# Nâng cấp model AI trong pipeline Voice — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Nâng chất lượng nhận dạng tiếng Việt của pipeline voice bằng cách dựng thang model PhoWhisper tiny/base/small rồi chọn bằng số đo, thêm domain biasing, hồi sinh tầng NLU T1 đang trả `null`, và sửa thước đo để mọi so sánh có nghĩa.

**Architecture:** Toàn bộ thay đổi nằm **sau** các interface đang có — `SpeechRecognitionEngine.transcribe(Flow<PcmFrame>)`, `SemanticIntentMatcher.bestIntent(String)`, và hợp đồng HTTP `POST /asr`. Không có interface mới, không đổi chữ ký nào. Model ASR đổi bằng Docker build-arg; model embedding đổi bằng URL trong Gradle. Đó là lý do việc này vừa 2 ngày.

**Tech Stack:** Python 3.11 + FastAPI + faster-whisper/CTranslate2 (container `viva-asr`); Kotlin + ONNX Runtime + Hilt (app AAOS); pytest; JUnit trên JVM thuần.

**Spec:** `docs/superpowers/specs/2026-08-06-nang-cap-model-voice-pipeline-design.md`

## Global Constraints

- **Code freeze 08/08/2026.** Sau mốc này chỉ sửa lỗi chặn.
- **`asr/scripts/*` chỉ dùng stdlib.** Không thêm numpy/scipy — script phải chạy cạnh container mà không cần venv. Ràng buộc này đã ghi trong docstring của cả `bench_tts_samples.py` lẫn `noise_mix.py`.
- **Hợp đồng wire không đổi:** `POST /asr` nhận PCM signed 16-bit little-endian, **16000 Hz**, mono; trả đúng ba trường `text` / `confidence` / `server_ms` (`vong2/03-contracts.md` §2).
- **Không truyền Android type vào `:voice-core`** (`android/voice`). Module đó phải test được trên JVM thuần.
- **Không bịa confidence.** Engine không cung cấp thì để `null`. Không đặt `1.0` cho "chưa đo được" (`28-PIPELINE` §2.4).
- **Error phải có mã máy đọc được**, không chỉ message (`28-PIPELINE` §4).
- **Mỗi task là một commit độc lập revert được riêng.** Không gộp task.
- **Ngân sách latency:** `e2e_ms` = `speech_end` → `tts_start`, p95 < 1500ms (`03-contracts.md` §1.3).
- **Lượt lỗi nằm trong mẫu.** Không lọc timeout/blank khỏi p50/p95.

---

## File Structure

| File | Trạng thái | Trách nhiệm |
|---|---|---|
| `asr/app/config.py` | Sửa | Thêm `initial_prompt`; đọc env |
| `asr/app/model.py` | Sửa | Hàm thuần `build_transcribe_kwargs()`; truyền vào faster-whisper |
| `asr/tests/test_model_settings.py` | Tạo | Test config + kwargs, không cần wheel faster-whisper |
| `asr/scripts/resample.py` | Tạo | Resampler windowed-sinc chống aliasing, stdlib-only |
| `asr/tests/test_resample.py` | Tạo | Test tần số giữ nguyên + tần số trên Nyquist bị chặn |
| `asr/scripts/bench_tts_samples.py` | Sửa | Dùng resampler mới; ghi CSV cho chấm intent |
| `asr/scripts/check_corpus.py` | Tạo | Kiểm định corpus giọng thật đúng 16 kHz mono |
| `asr/tests/test_check_corpus.py` | Tạo | Test bộ kiểm định corpus |
| `asr/scripts/corpus_prompts.tsv` | Tạo | 20 câu lệnh + ground truth |
| `android/voice/src/test/kotlin/com/viva/voice/intent/IntentAccuracyScorer.kt` | Tạo | Chấm intent qua router thật; parser CSV RFC4180 tối thiểu |
| `android/voice/src/test/kotlin/com/viva/voice/intent/IntentAccuracyScorerTest.kt` | Tạo | Test scorer bằng dữ liệu inline |
| `automotive/.../data/embedding/OnnxEmbeddingEncoder.kt` | Sửa | Chỉ truyền input mà model khai báo; bỏ giả định BERT |
| `automotive/.../data/embedding/OnnxEmbeddingIntentMatcher.kt` | Sửa | `MIN_COSINE` hiệu chỉnh lại |
| `automotive/feature/voice/build.gradle.kts` | Sửa | URL model embedding mới; `ASR_ENGINE` mặc định |
| `evidence/asr/v13/` | Tạo | Kết quả ma trận + manifest |

---

## Task 1: Domain biasing cho ASR service

Whisper nhận `initial_prompt` để ghim từ vựng domain vào decoder. Đây là thứ sửa lớp lỗi
`đặt`→`đác`, `độ C`→`độ xe` đã ghi trong `evidence/asr/asr-bench-manifest.txt`, và **tốn 0ms**.

`FasterWhisperTranscriber.__init__` import `faster_whisper` (wheel ~100MB, không có trong CI),
nên logic phải nằm trong một **hàm thuần** test được mà không cần wheel.

**Files:**
- Modify: `asr/app/config.py`
- Modify: `asr/app/model.py:89-114`
- Test: `asr/tests/test_model_settings.py` (tạo)

**Interfaces:**
- Consumes: `Settings` từ `asr/app/config.py` (đã có)
- Produces: `Settings.initial_prompt: str | None`; `build_transcribe_kwargs(settings: Settings) -> dict` trong `asr/app/model.py`

- [ ] **Step 1: Viết test thất bại**

Tạo `asr/tests/test_model_settings.py`:

```python
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.config import Settings  # noqa: E402
from app.model import build_transcribe_kwargs  # noqa: E402


def test_initial_prompt_defaults_to_none_when_env_absent(monkeypatch):
    monkeypatch.delenv("ASR_INITIAL_PROMPT", raising=False)
    assert Settings.from_env().initial_prompt is None


def test_initial_prompt_read_from_env(monkeypatch):
    monkeypatch.setenv("ASR_INITIAL_PROMPT", "điều hòa, quạt gió, độ C")
    assert Settings.from_env().initial_prompt == "điều hòa, quạt gió, độ C"


def test_blank_initial_prompt_is_none_not_empty_string(monkeypatch):
    # An empty prompt is not the same as no prompt: faster-whisper would
    # tokenize "" and prepend a useless empty context.
    monkeypatch.setenv("ASR_INITIAL_PROMPT", "   ")
    assert Settings.from_env().initial_prompt is None


def test_kwargs_omit_initial_prompt_when_unset():
    settings = Settings(initial_prompt=None)
    kwargs = build_transcribe_kwargs(settings)
    assert "initial_prompt" not in kwargs
    assert kwargs["language"] == "vi"
    assert kwargs["beam_size"] == 1
    # The app already ran Silero VAD before sending; a second pass would only
    # add latency and risk cutting the tail.
    assert kwargs["vad_filter"] is False


def test_kwargs_include_initial_prompt_when_set():
    settings = Settings(initial_prompt="điều hòa, quạt gió")
    kwargs = build_transcribe_kwargs(settings)
    assert kwargs["initial_prompt"] == "điều hòa, quạt gió"
```

- [ ] **Step 2: Chạy test cho thất bại**

Run: `cd asr && python -m pytest tests/test_model_settings.py -v`
Expected: FAIL — `ImportError: cannot import name 'build_transcribe_kwargs'`

- [ ] **Step 3: Thêm `initial_prompt` vào `Settings`**

Trong `asr/app/config.py`, thêm field vào `@dataclass(frozen=True) class Settings` ngay sau
`beam_size` (giữ nguyên comment giải thích hiện có của `beam_size`):

```python
    # Domain biasing. Whisper conditions the decoder on this text, which pulls
    # near-homophone errors back toward in-domain words — the "dat"->"dac" and
    # "do C"->"do xe" class in evidence/asr/asr-bench-manifest.txt. It costs no
    # extra decoding time because the prompt is just prepended context.
    #
    # None, not "": an empty prompt still gets tokenized and prepended.
    initial_prompt: str | None = None
```

Thêm helper đọc env (đặt cạnh `_env_int`):

```python
def _env_text(name: str, default: str | None) -> str | None:
    raw = os.getenv(name)
    if raw is None or raw.strip() == "":
        return default
    return raw.strip()
```

Và trong `Settings.from_env()`, thêm dòng vào lời gọi `Settings(...)`:

```python
            initial_prompt=_env_text("ASR_INITIAL_PROMPT", Settings.initial_prompt),
```

- [ ] **Step 4: Thêm `build_transcribe_kwargs` vào `model.py`**

Trong `asr/app/model.py`, thêm hàm ngay trước `class FasterWhisperTranscriber`:

```python
def build_transcribe_kwargs(settings: Settings) -> dict:
    """Decoding options for one utterance.

    A plain function, not a method, so the tests can check it without the
    ~100 MB faster-whisper wheel on the machine.
    """
    kwargs = {
        "language": settings.language,
        "beam_size": settings.beam_size,
        # The app already ran Silero VAD before sending (L3), so a second VAD
        # pass here would only add latency and risk cutting the tail.
        "vad_filter": False,
    }
    if settings.initial_prompt:
        kwargs["initial_prompt"] = settings.initial_prompt
    return kwargs
```

Sửa `FasterWhisperTranscriber.transcribe` — thay lời gọi hiện tại (dòng 93-100):

```python
        segments, info = self._model.transcribe(
            audio,
            **build_transcribe_kwargs(self._settings),
        )
```

- [ ] **Step 5: Chạy test cho pass**

Run: `cd asr && python -m pytest tests/test_model_settings.py -v`
Expected: PASS — 5 passed

- [ ] **Step 6: Chạy toàn bộ test ASR để chắc không vỡ gì**

Run: `cd asr && python -m pytest -q`
Expected: PASS toàn bộ, không có test nào đỏ thêm so với trước

- [ ] **Step 7: Đặt prompt mặc định trong Dockerfile**

Trong `asr/Dockerfile`, thêm vào khối `ENV` của runtime stage (sau `ASR_BEAM_SIZE=1`):

```dockerfile
    ASR_INITIAL_PROMPT="Lệnh điều khiển xe: điều hòa, nhiệt độ, độ C, quạt gió, mức quạt, mở khóa cửa, khóa cửa, ghế sưởi, âm lượng, phát nhạc, chuyển bài, bật, tắt, tăng, giảm." \
```

- [ ] **Step 8: Commit**

```bash
git add asr/app/config.py asr/app/model.py asr/tests/test_model_settings.py asr/Dockerfile
git commit -m "feat(asr): domain biasing qua ASR_INITIAL_PROMPT

Whisper nhan initial_prompt de ghim tu vung domain vao decoder — sua dung
lop loi dat->dac, do C->do xe da ghi trong asr-bench-manifest, va ton 0ms
vi prompt chi la context duoc prepend.

Logic nam trong build_transcribe_kwargs() la ham thuan nen test duoc ma
khong can wheel faster-whisper."
```

---

## Task 2: Resampler chống aliasing

`resample_linear` trong `bench_tts_samples.py:69-83` là nội suy tuyến tính. Nó **không có
low-pass**, nên mọi năng lượng trên 8 kHz của nguồn 22.05 kHz gập ngược xuống dải thoại
dưới dạng aliasing — làm bẩn đúng dải chứa phụ âm tiếng Việt. Chính docstring của script
đã thừa nhận *"cheaper and worse than a proper filter"*.

`noise_mix.py:43` import lại hàm này, nên sửa một chỗ là cả hai script cùng sạch.

**Files:**
- Create: `asr/scripts/resample.py`
- Create: `asr/tests/test_resample.py`
- Modify: `asr/scripts/bench_tts_samples.py:69-83`

**Interfaces:**
- Produces: `resample_sinc(samples: array.array, src_rate: int, dst_rate: int) -> array.array` trong `asr/scripts/resample.py`
- `bench_tts_samples.resample_linear` giữ nguyên tên và chữ ký (`noise_mix.py` đang import) nhưng gọi vào `resample_sinc`

- [ ] **Step 1: Viết test thất bại**

Tạo `asr/tests/test_resample.py`:

```python
"""Resampling correctness, stated as the two properties that matter.

A resampler is only useful here if it (a) keeps in-band content intact and
(b) removes content above the destination Nyquist instead of folding it back
into the speech band. Linear interpolation passes (a) and fails (b).
"""

from __future__ import annotations

import array
import cmath
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))

from resample import resample_sinc  # noqa: E402


def tone(freq_hz: float, rate: int, seconds: float, amplitude: int = 12000) -> array.array:
    n = int(rate * seconds)
    return array.array(
        "h",
        (int(amplitude * math.sin(2.0 * math.pi * freq_hz * i / rate)) for i in range(n)),
    )


def magnitude_at(samples: array.array, rate: int, freq_hz: float) -> float:
    """One DFT bin, computed directly. Stdlib only — no numpy in this tree."""
    n = len(samples)
    acc = sum(s * cmath.exp(-2j * math.pi * freq_hz * i / rate) for i, s in enumerate(samples))
    return abs(acc) / n


def test_in_band_tone_survives():
    src = tone(1000.0, 22050, 0.5)
    out = resample_sinc(src, 22050, 16000)

    assert abs(len(out) - int(0.5 * 16000)) <= 2
    # Amplitude 12000 sine -> single-sided bin magnitude ~6000.
    assert magnitude_at(out, 16000, 1000.0) > 4000.0


def test_tone_above_destination_nyquist_is_rejected():
    """9 kHz cannot exist in a 16 kHz signal (Nyquist 8 kHz).

    Linear interpolation folds it down to 7 kHz at near-full amplitude. A
    filtered resampler must leave almost nothing behind.
    """
    src = tone(9000.0, 22050, 0.5)
    out = resample_sinc(src, 22050, 16000)

    folded = magnitude_at(out, 16000, 7000.0)
    assert folded < 600.0, f"aliased image too strong: {folded}"


def test_same_rate_is_identity():
    src = tone(1000.0, 16000, 0.1)
    assert resample_sinc(src, 16000, 16000) is src


def test_output_stays_in_int16_range():
    src = tone(1000.0, 22050, 0.2, amplitude=32700)
    out = resample_sinc(src, 22050, 16000)
    assert all(-32768 <= s <= 32767 for s in out)
```

- [ ] **Step 2: Chạy test cho thất bại**

Run: `cd asr && python -m pytest tests/test_resample.py -v`
Expected: FAIL — `ModuleNotFoundError: No module named 'resample'`

- [ ] **Step 3: Viết resampler**

Tạo `asr/scripts/resample.py`:

```python
#!/usr/bin/env python3
"""Windowed-sinc resampling, stdlib only.

Why this exists: the bench used to resample 22.05 kHz -> 16 kHz by linear
interpolation. That has no anti-alias filter, so everything above 8 kHz in the
source folds back down into the speech band — corrupting exactly the region
Vietnamese consonants live in, and making every WER number downstream a
measurement of the resampler as much as of the model.

No numpy: `bench_tts_samples.py` and `noise_mix.py` are documented as runnable
next to the container without a venv, and that property is worth more than the
speed a vectorised filter would buy on 36 short clips.
"""

from __future__ import annotations

import array
import math

# Taps either side of the centre. 16 puts the first sidelobe far enough down
# for a benchmark; more taps buy accuracy nobody here can measure.
HALF_WIDTH = 16


def _blackman(x: float) -> float:
    """Blackman window over x in [-1, 1]."""
    t = (x + 1.0) * 0.5
    return 0.42 - 0.5 * math.cos(2.0 * math.pi * t) + 0.08 * math.cos(4.0 * math.pi * t)


def _sinc(x: float) -> float:
    if x == 0.0:
        return 1.0
    pix = math.pi * x
    return math.sin(pix) / pix


def resample_sinc(samples: array.array, src_rate: int, dst_rate: int) -> array.array:
    """Resample mono int16 `samples` from `src_rate` to `dst_rate`.

    When downsampling, the sinc is stretched to cut at the destination Nyquist
    so the filter both interpolates and anti-aliases in one pass.
    """
    if src_rate == dst_rate:
        return samples

    ratio = src_rate / dst_rate
    # Downsampling -> lower the cutoff to the destination Nyquist.
    cutoff = min(1.0, dst_rate / src_rate)
    n_in = len(samples)
    n_out = int(n_in / ratio)
    # Widen the kernel by the same factor the cutoff narrowed, so the number of
    # non-negligible taps stays constant.
    half = max(1, int(HALF_WIDTH / cutoff))

    out = array.array("h")
    for i in range(n_out):
        centre = i * ratio
        left = int(math.floor(centre)) - half + 1
        right = int(math.floor(centre)) + half

        acc = 0.0
        norm = 0.0
        for k in range(left, right + 1):
            if k < 0 or k >= n_in:
                continue
            offset = centre - k
            window = _blackman(offset / half)
            weight = cutoff * _sinc(cutoff * offset) * window
            acc += samples[k] * weight
            norm += weight
        value = acc / norm if norm != 0.0 else 0.0
        out.append(int(max(-32768, min(32767, round(value)))))
    return out
```

- [ ] **Step 4: Chạy test cho pass**

Run: `cd asr && python -m pytest tests/test_resample.py -v`
Expected: PASS — 4 passed

- [ ] **Step 5: Nối vào bench script**

Trong `asr/scripts/bench_tts_samples.py`, thay toàn bộ thân `resample_linear` (dòng 69-83)
bằng phần uỷ quyền, giữ nguyên tên hàm vì `noise_mix.py:43` đang import nó:

```python
def resample_linear(samples: array.array, src_rate: int, dst_rate: int) -> array.array:
    """Kept under the old name because `noise_mix.py` imports it.

    No longer linear: it delegates to the windowed-sinc filter. The old
    interpolation had no anti-alias stage and folded everything above 8 kHz
    back into the speech band.
    """
    return resample_sinc(samples, src_rate, dst_rate)
```

Thêm import ở đầu file, sau các import stdlib:

```python
sys.path.insert(0, str(Path(__file__).resolve().parent))

from resample import resample_sinc  # noqa: E402
```

- [ ] **Step 6: Sửa cảnh báo trong docstring**

Trong `bench_tts_samples.py`, thay gạch đầu dòng đang ghi giới hạn resample (dòng 15-17):

```
  * They are 22.05 kHz and the service accepts only 16 kHz, so this script
    resamples with a windowed-sinc filter (`resample.py`). That removes the
    aliasing the old linear interpolation introduced — but the clips are still
    synthesised speech, so accuracy here remains a floor, not a prediction.
```

Và ở cuối `main()`, sửa dòng in ra (dòng 195-196):

```python
    print("Nho: clip la giong TTS tong hop, resample 22.05k->16k bang windowed-sinc,")
    print("va RTF do tren CPU may nay — khong phai so cua container node CarSky.")
```

- [ ] **Step 7: Xác minh `noise_mix.py` vẫn chạy**

Run: `cd asr && python -c "import sys; sys.path.insert(0,'scripts'); import noise_mix; print('import OK')"`
Expected: `import OK` — không `ImportError`

- [ ] **Step 8: Commit**

```bash
git add asr/scripts/resample.py asr/tests/test_resample.py asr/scripts/bench_tts_samples.py
git commit -m "fix(bench): resample windowed-sinc thay noi suy tuyen tinh

Noi suy tuyen tinh khong co low-pass, nen moi thu tren 8 kHz cua nguon
22.05 kHz gap nguoc xuong dai thoai — lam ban dung dai chua phu am tieng
Viet, va bien moi so WER thanh phep do ca resampler lan model.

Test bat dung tinh chat do: tone 9 kHz phai bi chan, khong duoc hien ra
o 7 kHz. Giu ten resample_linear vi noise_mix.py dang import."
```

---

## Task 3: Chấm intent accuracy qua router thật

`asr-bench-manifest.txt` tự ghi: *"WER dem la sai hoan toan, nhung intent router lam viec
tren tu khoa nen anh huong that su can do bang intent accuracy, khong phai WER."*

Chỉ số cần đo là: **lỗi ASR có làm hệ thống hành động khác đi không.** Nên phép chấm là
cho cả `reference` và `hypothesis` chạy qua `GrammarIntentRouter` **thật** rồi so kết quả.
Port router sang Python sẽ tạo một bản sao trôi khỏi bản Kotlin đúng lúc con số bắt đầu
quan trọng — không làm.

Slots nằm trong phép so: `hvac_set_temp(24)` và `hvac_set_temp(20)` là hai hành động khác
nhau, không phải cùng một "đúng".

**Files:**
- Create: `android/voice/src/test/kotlin/com/viva/voice/intent/IntentAccuracyScorer.kt`
- Create: `android/voice/src/test/kotlin/com/viva/voice/intent/IntentAccuracyScorerTest.kt`
- Modify: `asr/scripts/bench_tts_samples.py` (CSV đã có sẵn `clip`/`reference`/`hypothesis` — không cần đổi)

**Interfaces:**
- Consumes: `GrammarIntentRouter`, `IntentRouter`, `RouteResult`, `Intent` từ `com.viva.voice.intent`
- Produces: `IntentAccuracyScorer(router: IntentRouter)` với `parseCsv(text: String): List<Row>`, `score(rows: List<Row>): Score`, `outcomeKey(result: RouteResult): String`

- [ ] **Step 1: Viết test thất bại**

Tạo `android/voice/src/test/kotlin/com/viva/voice/intent/IntentAccuracyScorerTest.kt`:

> **Lưu ý framework:** module này dùng **JUnit 4** (`junit:junit:4.13.2`), không phải
> `kotlin.test`. `org.junit.Assert.assertTrue` nhận **message trước, điều kiện sau** —
> ngược với `kotlin.test`. Viết nhầm thứ tự thì test vẫn compile nhưng báo lỗi vô nghĩa.

```kotlin
package com.viva.voice.intent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentAccuracyScorerTest {

    private val scorer = IntentAccuracyScorer(GrammarIntentRouter())

    @Test
    fun `identical transcript scores as correct`() {
        val rows = listOf(
            IntentAccuracyScorer.Row("c1", "đặt nhiệt độ 24 độ", "đặt nhiệt độ 24 độ"),
        )
        val score = scorer.score(rows)
        assertEquals(1, score.total)
        assertEquals(1, score.correct)
    }

    @Test
    fun `harmless misspelling that still routes the same is correct`() {
        // "dat"->"dac" is the exact error class the ASR manifest recorded. The
        // router keys on "nhiệt độ" + the number, so the action is unchanged.
        val rows = listOf(
            IntentAccuracyScorer.Row("c1", "đặt nhiệt độ 24 độ", "đạc nhiệt độ 24 độ"),
        )
        assertEquals(1, scorer.score(rows).correct)
    }

    @Test
    fun `wrong slot value counts as incorrect`() {
        val rows = listOf(
            IntentAccuracyScorer.Row("c1", "đặt nhiệt độ 24 độ", "đặt nhiệt độ 20 độ"),
        )
        val score = scorer.score(rows)
        assertEquals(1, score.total)
        assertEquals(0, score.correct)
    }

    @Test
    fun `blank hypothesis counts as incorrect not as skipped`() {
        // tts_volume_up returned "" in the last run. A blank must land in the
        // sample as a failure, never be filtered out.
        val rows = listOf(
            IntentAccuracyScorer.Row("c1", "tăng âm lượng", ""),
        )
        val score = scorer.score(rows)
        assertEquals(1, score.total)
        assertEquals(0, score.correct)
    }

    @Test
    fun `outcome key includes intent name and slots`() {
        val matched = GrammarIntentRouter().route("đặt nhiệt độ 24 độ")
        val key = scorer.outcomeKey(matched)
        assertTrue("unexpected key: $key", key.startsWith("matched:hvac_set_temp"))
        assertTrue("slots missing from key: $key", key.contains("24"))
    }

    @Test
    fun `csv parser handles quoted fields containing commas`() {
        val csv = """
            clip,reference,hypothesis
            c1,"hạ điều hòa, xuống 24 độ","hạ điều hòa xuống 24 độ"
        """.trimIndent()
        val rows = IntentAccuracyScorer.parseCsv(csv)
        assertEquals(1, rows.size)
        assertEquals("hạ điều hòa, xuống 24 độ", rows[0].reference)
        assertEquals("hạ điều hòa xuống 24 độ", rows[0].hypothesis)
    }

    @Test
    fun `csv parser handles escaped double quotes`() {
        val csv = "clip,reference,hypothesis\nc1,\"nói \"\"viva ơi\"\"\",\"nói viva ơi\""
        val rows = IntentAccuracyScorer.parseCsv(csv)
        assertEquals("nói \"viva ơi\"", rows[0].reference)
    }
}
```

- [ ] **Step 2: Chạy test cho thất bại**

Run: `cd automotive && ./gradlew :voice-core:testDebugUnitTest --tests "com.viva.voice.intent.IntentAccuracyScorerTest"`
Expected: FAIL — compile error, `IntentAccuracyScorer` chưa tồn tại

- [ ] **Step 3: Viết scorer**

Tạo `android/voice/src/test/kotlin/com/viva/voice/intent/IntentAccuracyScorer.kt`:

```kotlin
package com.viva.voice.intent

/**
 * Scores an ASR benchmark by what the system would DO, not by how the words look.
 *
 * WER punishes "đặt"->"đạc" as a total miss while the grammar router, which keys
 * on "nhiệt độ" plus the number, is unaffected. It also under-punishes a clean
 * transcript with the wrong number, which changes the action. So both the
 * reference and the hypothesis are routed through the real [IntentRouter] and
 * the outcomes compared.
 *
 * Lives in test sources on purpose: this is measurement tooling, not product
 * code, and it must never end up on the APK path.
 */
class IntentAccuracyScorer(private val router: IntentRouter) {

    data class Row(val clip: String, val reference: String, val hypothesis: String)

    data class Score(
        val total: Int,
        val correct: Int,
        /** Rows whose reference itself does not route — a corpus problem, not a model one. */
        val referenceUnroutable: Int,
    ) {
        val accuracy: Double get() = if (total == 0) 0.0 else correct.toDouble() / total
    }

    fun score(rows: List<Row>): Score {
        var correct = 0
        var referenceUnroutable = 0
        for (row in rows) {
            val referenceKey = outcomeKey(router.route(row.reference))
            val hypothesisKey = outcomeKey(router.route(row.hypothesis))
            if (referenceKey.startsWith("unsupported")) referenceUnroutable++
            if (referenceKey == hypothesisKey) correct++
        }
        return Score(total = rows.size, correct = correct, referenceUnroutable = referenceUnroutable)
    }

    /**
     * Collapses a route into the identity of the action taken.
     *
     * Slots are part of the key: `hvac_set_temp(24)` and `hvac_set_temp(20)` are
     * two different actions, not two spellings of one.
     */
    fun outcomeKey(result: RouteResult): String = when (result) {
        is RouteResult.Matched -> {
            val slots = result.intent.slots.entries
                .sortedBy { it.key }
                .joinToString(",") { "${it.key}=${it.value}" }
            "matched:${result.intent.name}($slots)"
        }
        is RouteResult.NeedsClarification -> "clarify:${result.rule}"
        is RouteResult.Unsupported -> "unsupported:${result.rule}"
    }

    companion object {
        /**
         * Minimal RFC 4180 reader. Vietnamese prompts contain commas, so the
         * Python writer quotes them; splitting on ',' would shred those rows.
         */
        fun parseCsv(text: String): List<Row> {
            val records = splitRecords(text)
            if (records.isEmpty()) return emptyList()
            val header = records.first().map { it.trim() }
            val clipAt = header.indexOf("clip")
            val referenceAt = header.indexOf("reference")
            val hypothesisAt = header.indexOf("hypothesis")
            require(clipAt >= 0 && referenceAt >= 0 && hypothesisAt >= 0) {
                "CSV must have clip/reference/hypothesis columns, got $header"
            }
            return records.drop(1)
                .filter { it.size > maxOf(clipAt, referenceAt, hypothesisAt) }
                .map { Row(it[clipAt], it[referenceAt], it[hypothesisAt]) }
        }

        private fun splitRecords(text: String): List<List<String>> {
            val records = mutableListOf<List<String>>()
            var fields = mutableListOf<String>()
            val field = StringBuilder()
            var quoted = false
            var index = 0
            while (index < text.length) {
                val ch = text[index]
                when {
                    quoted && ch == '"' && index + 1 < text.length && text[index + 1] == '"' -> {
                        field.append('"')
                        index++
                    }
                    ch == '"' -> quoted = !quoted
                    !quoted && ch == ',' -> {
                        fields.add(field.toString()); field.setLength(0)
                    }
                    !quoted && (ch == '\n' || ch == '\r') -> {
                        if (field.isNotEmpty() || fields.isNotEmpty()) {
                            fields.add(field.toString()); field.setLength(0)
                            records.add(fields); fields = mutableListOf()
                        }
                        if (ch == '\r' && index + 1 < text.length && text[index + 1] == '\n') index++
                    }
                    else -> field.append(ch)
                }
                index++
            }
            if (field.isNotEmpty() || fields.isNotEmpty()) {
                fields.add(field.toString())
                records.add(fields)
            }
            return records
        }
    }
}
```

- [ ] **Step 4: Chạy test cho pass**

Run: `cd automotive && ./gradlew :voice-core:testDebugUnitTest --tests "com.viva.voice.intent.IntentAccuracyScorerTest"`
Expected: PASS — 7 test xanh

- [ ] **Step 5: Thêm test chấm file CSV thật, bỏ qua khi không có file**

Thêm vào cuối `IntentAccuracyScorerTest.kt` (trong cùng class):

```kotlin
    /**
     * Scores a real bench CSV when one is handed in:
     *
     *   ./gradlew :voice-core:testDebugUnitTest -Dviva.bench.csv=/abs/path/asr-bench.csv
     *
     * Skipped otherwise so CI stays green without the corpus.
     */
    @Test
    fun `score a bench csv when the path is supplied`() {
        val path = System.getProperty("viva.bench.csv") ?: return
        val file = java.io.File(path)
        assertTrue("bench CSV not found: $path", file.exists())

        val rows = IntentAccuracyScorer.parseCsv(file.readText(Charsets.UTF_8))
        val score = scorer.score(rows)
        println("VIVA_INTENT_ACCURACY rows=${score.total} correct=${score.correct} " +
            "accuracy=${"%.4f".format(score.accuracy)} reference_unroutable=${score.referenceUnroutable}")
        assertTrue("bench CSV had no scorable rows", score.total > 0)
    }
```

- [ ] **Step 6: Chạy lại toàn bộ test module voice**

Run: `cd automotive && ./gradlew :voice-core:testDebugUnitTest`
Expected: PASS toàn bộ

- [ ] **Step 7: Commit**

```bash
git add android/voice/src/test/kotlin/com/viva/voice/intent/IntentAccuracyScorer.kt android/voice/src/test/kotlin/com/viva/voice/intent/IntentAccuracyScorerTest.kt
git commit -m "test(bench): cham intent accuracy qua GrammarIntentRouter that

asr-bench-manifest tu ghi rang WER phat dat->dac nhu sai hoan toan trong
khi router lam viec tren tu khoa. Chi so dung la: loi ASR co lam he thong
hanh dong khac di khong.

Cho ca reference lan hypothesis chay qua router THAT roi so ket qua. Slots
nam trong phep so — hvac_set_temp(24) va (20) la hai hanh dong khac nhau.
Khong port router sang Python: ban sao se troi khoi ban Kotlin dung luc con
so bat dau quan trong."
```

---

## Task 4: Corpus giọng thật 16 kHz

Giới hạn #1 và #2 của `asr-bench-manifest.txt` là "giọng TTS tổng hợp" và "resample 22.05k".
Thu thẳng ở 16 kHz gỡ cả hai cùng lúc — không resample thì không có gì để hỏng.

**Files:**
- Create: `asr/scripts/corpus_prompts.tsv`
- Create: `asr/scripts/check_corpus.py`
- Create: `asr/tests/test_check_corpus.py`

**Interfaces:**
- Produces: `check_corpus.validate_wav(path: Path) -> tuple[bool, str]`; file WAV trong `evidence/asr/corpus-human/raw/<clip>.wav`

- [ ] **Step 1: Viết bộ câu lệnh**

Tạo `asr/scripts/corpus_prompts.tsv` (tab-separated, đúng cột như `tts_prompts.tsv`):

```tsv
raw_name	text_vi
cmd_temp_24	đặt nhiệt độ 24 độ
cmd_temp_22	hạ điều hòa xuống 22 độ
cmd_temp_26	tăng nhiệt độ lên 26 độ
cmd_temp_wake	viva ơi đặt nhiệt độ 25 độ
cmd_temp_noslot	giảm nhiệt độ
cmd_fan_3	đặt quạt mức 3
cmd_fan_0	tắt quạt gió về mức 0
cmd_fan_5	quạt gió mức 5
cmd_door_unlock	mở khóa cửa
cmd_door_lock	khóa cửa lại
cmd_vol_up	tăng âm lượng
cmd_vol_down	giảm âm lượng
cmd_media_next	chuyển bài
cmd_media_pause	dừng nhạc
cmd_media_play	phát nhạc
cmd_delivery_next	chặng tiếp theo
cmd_delivery_status	đơn a123 thế nào
cmd_cold	lạnh quá
cmd_out_of_scope	đặt bàn ăn tối lúc bảy giờ
cmd_removed	bật điều hòa
```

Hai dòng cuối là **cố ý**: `cmd_out_of_scope` phải ra `Unsupported`, `cmd_removed` phải ra
`Unsupported` theo `REMOVED_COMMANDS`. Bộ đo không được chỉ toàn câu dễ.

- [ ] **Step 2: Viết test thất bại cho bộ kiểm định**

Tạo `asr/tests/test_check_corpus.py`:

```python
from __future__ import annotations

import sys
import wave
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))

from check_corpus import validate_wav  # noqa: E402


def write_wav(path: Path, rate: int, channels: int, sampwidth: int, frames: int = 16000):
    with wave.open(str(path), "wb") as wav:
        wav.setnchannels(channels)
        wav.setsampwidth(sampwidth)
        wav.setframerate(rate)
        wav.writeframes(b"\x00\x01" * (frames * channels * (sampwidth // 2)))


def test_accepts_16k_mono_16bit(tmp_path):
    path = tmp_path / "ok.wav"
    write_wav(path, 16000, 1, 2)
    ok, reason = validate_wav(path)
    assert ok, reason


def test_rejects_wrong_sample_rate(tmp_path):
    path = tmp_path / "bad_rate.wav"
    write_wav(path, 44100, 1, 2)
    ok, reason = validate_wav(path)
    assert not ok
    assert "44100" in reason


def test_rejects_stereo(tmp_path):
    path = tmp_path / "stereo.wav"
    write_wav(path, 16000, 2, 2)
    ok, reason = validate_wav(path)
    assert not ok
    assert "mono" in reason.lower()


def test_rejects_clip_shorter_than_min(tmp_path):
    path = tmp_path / "tiny.wav"
    write_wav(path, 16000, 1, 2, frames=800)  # 50 ms
    ok, reason = validate_wav(path)
    assert not ok
    assert "ngan" in reason.lower() or "short" in reason.lower()
```

- [ ] **Step 3: Chạy test cho thất bại**

Run: `cd asr && python -m pytest tests/test_check_corpus.py -v`
Expected: FAIL — `ModuleNotFoundError: No module named 'check_corpus'`

- [ ] **Step 4: Viết bộ kiểm định**

Tạo `asr/scripts/check_corpus.py`:

```python
#!/usr/bin/env python3
"""Validate a hand-recorded corpus before it is allowed near a benchmark.

Recording at 16 kHz directly is the whole point: it removes the resampling
step, and with it the second limitation printed on every number the old TTS
corpus produced. A file that sneaks in at 44.1 kHz would quietly put that
limitation back.

    python scripts/check_corpus.py --dir ../evidence/asr/corpus-human/raw \\
                                   --prompts scripts/corpus_prompts.tsv

Stdlib only, matching the rest of asr/scripts.
"""

from __future__ import annotations

import argparse
import csv
import sys
import wave
from pathlib import Path

REQUIRED_RATE = 16000
REQUIRED_CHANNELS = 1
REQUIRED_SAMPWIDTH = 2  # bytes, i.e. PCM16
MIN_DURATION_MS = 300


def validate_wav(path: Path) -> tuple[bool, str]:
    """Return (ok, reason). `reason` is empty when ok."""
    try:
        with wave.open(str(path), "rb") as wav:
            rate = wav.getframerate()
            channels = wav.getnchannels()
            sampwidth = wav.getsampwidth()
            frames = wav.getnframes()
    except wave.Error as exc:
        return False, f"khong doc duoc WAV: {exc}"

    if rate != REQUIRED_RATE:
        return False, f"sample rate {rate}, can {REQUIRED_RATE}"
    if channels != REQUIRED_CHANNELS:
        return False, f"co {channels} kenh, can mono"
    if sampwidth != REQUIRED_SAMPWIDTH:
        return False, f"sampwidth {sampwidth * 8} bit, can 16 bit"
    duration_ms = frames / rate * 1000.0
    if duration_ms < MIN_DURATION_MS:
        return False, f"clip qua ngan: {duration_ms:.0f} ms < {MIN_DURATION_MS} ms"
    return True, ""


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dir", required=True, help="thu muc chua WAV da thu")
    parser.add_argument("--prompts", default="scripts/corpus_prompts.tsv")
    args = parser.parse_args()

    prompts: dict[str, str] = {}
    with open(args.prompts, encoding="utf-8") as handle:
        for row in csv.DictReader(handle, delimiter="\t"):
            name = (row.get("raw_name") or "").strip()
            text = (row.get("text_vi") or "").strip()
            if name and text:
                prompts[name] = text

    directory = Path(args.dir)
    failures = 0
    missing = []
    for name in sorted(prompts):
        path = directory / f"{name}.wav"
        if not path.exists():
            missing.append(name)
            continue
        ok, reason = validate_wav(path)
        status = "OK  " if ok else "LOI "
        if not ok:
            failures += 1
        print(f"  {status}{name:<24} {reason}")

    print()
    print(f"cau trong prompts : {len(prompts)}")
    print(f"file da thu       : {len(prompts) - len(missing)}")
    print(f"file loi dinh dang: {failures}")
    if missing:
        print(f"CHUA THU          : {', '.join(missing)}")
    if failures or missing:
        print()
        print("Corpus CHUA dung duoc. Thu lai o 16 kHz mono 16-bit roi chay lai.")
        return 1
    print()
    print("Corpus hop le. Buoc tiep: sinh 3 muc nhieu bang noise_mix.py")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
```

- [ ] **Step 5: Chạy test cho pass**

Run: `cd asr && python -m pytest tests/test_check_corpus.py -v`
Expected: PASS — 4 passed

- [ ] **Step 6: Thu 20 câu**

Thu bằng Audacity hoặc bất kỳ công cụ nào, đặt **Project Rate = 16000 Hz, mono, 16-bit PCM**.
Xuất từng câu ra `evidence/asr/corpus-human/raw/<raw_name>.wav` đúng tên ở cột `raw_name`.

Cách nói: giọng bình thường, tốc độ tự nhiên, để **~300ms im lặng ở đầu** — manifest cũ ghi
nhiều clip *"mất từ đầu câu"* vì thiếu khoảng lặng đầu.

- [ ] **Step 7: Kiểm định corpus**

Run: `cd asr && python scripts/check_corpus.py --dir ../evidence/asr/corpus-human/raw`
Expected: `Corpus hop le.` và exit code 0. Nếu đỏ thì thu lại, **không** sửa script cho vừa file.

- [ ] **Step 8: Sinh ba mức nhiễu**

Run:
```bash
cd asr && python scripts/noise_mix.py \
  --source ../evidence/asr/corpus-human/raw \
  --out-dir ../evidence/asr/corpus-human
```
Expected: in ra `clip goc: 20`, `file da ghi: 80` (clean + quiet + cabin + highway), `mau bi clip: 0`

- [ ] **Step 9: Commit**

```bash
git add asr/scripts/corpus_prompts.tsv asr/scripts/check_corpus.py asr/tests/test_check_corpus.py evidence/asr/corpus-human
git commit -m "test(corpus): bo 20 cau giong that thu thang o 16 kHz

Thu o 16 kHz go cung luc hai gioi han cua manifest cu: giong tong hop va
resample 22.05k. Khong resample thi khong co gi de hong.

check_corpus.py chan file lot vao o 44.1 kHz — mot file nhu vay se lang le
dat lai gioi han vua go. Hai cau cuoi cua bo la co y ngoai pham vi, de bo
do khong chi toan cau de."
```

---

## Task 5: Chạy ma trận model và chọn

Đây là task cho ra **quyết định** của cả kế hoạch. Không có test đơn vị; nghiệm thu là
evidence tái lập được.

**Files:**
- Create: `evidence/asr/v13/` (CSV kết quả + manifest)

**Interfaces:**
- Consumes: `resample_sinc` (Task 2), `IntentAccuracyScorer` (Task 3), corpus (Task 4), `ASR_INITIAL_PROMPT` (Task 1)
- Produces: `evidence/asr/v13/v13-manifest.txt` ghi model được chọn + lý do

- [ ] **Step 1: Build ba image, chạy nền**

```bash
cd "e:/FPT Automative Hackathon 2026"
docker build --build-arg ASR_HF_MODEL=vinai/PhoWhisper-tiny  --build-arg ASR_MODEL_NAME=phowhisper-tiny-int8  -t viva-asr:tiny  asr/
docker build --build-arg ASR_HF_MODEL=vinai/PhoWhisper-base  --build-arg ASR_MODEL_NAME=phowhisper-base-int8  -t viva-asr:base  asr/
docker build --build-arg ASR_HF_MODEL=vinai/PhoWhisper-small --build-arg ASR_MODEL_NAME=phowhisper-small-int8 -t viva-asr:small asr/
```

Mỗi build ~15-20 phút. Build lần lượt, không song song — stage `model-builder` kéo torch
và ngốn cả đĩa lẫn RAM.

- [ ] **Step 2: Xác minh từng image khai đúng tên model**

```bash
docker run -d --rm -p 8080:8080 --name viva-asr-check viva-asr:small
curl -s http://127.0.0.1:8080/health
docker stop viva-asr-check
```
Expected: JSON có `"model_name":"phowhisper-small-int8"`. Sai tên nghĩa là build-arg không
tới nơi — dừng, sửa, đừng đo tiếp.

- [ ] **Step 3: Chạy ma trận**

Với mỗi `(model, biasing)` trong `{tiny, base, small} × {có, không}`:

```bash
# không biasing
docker run -d --rm -p 8080:8080 -e ASR_INITIAL_PROMPT="" --name viva-asr-run viva-asr:small
cd asr && python scripts/bench_tts_samples.py --url http://127.0.0.1:8080 \
  --raw-dir ../evidence/asr/corpus-human/clean \
  --prompts scripts/corpus_prompts.tsv \
  --out ../evidence/asr/v13/small-nobias-clean.csv
docker stop viva-asr-run
```

Lặp cho `--raw-dir` = `quiet`, `cabin`, `highway`, và cho biến thể có biasing (bỏ
`-e ASR_INITIAL_PROMPT=""` để dùng mặc định của image).

Tổng: 3 model × 2 biasing × 4 mức nhiễu = **24 lần chạy**. Nếu hết giờ, cắt theo thứ tự này:
bỏ mức `quiet` trước, rồi bỏ biến thể không-biasing của `tiny`.

- [ ] **Step 4: Chấm intent accuracy cho từng CSV**

```bash
./gradlew :voice-core:testDebugUnitTest --tests "*IntentAccuracyScorerTest*" \
  -Dviva.bench.csv="e:/FPT Automative Hackathon 2026/evidence/asr/v13/small-nobias-clean.csv" --info
```
Expected: dòng `VIVA_INTENT_ACCURACY rows=20 correct=… accuracy=…`

- [ ] **Step 5: Áp luật chọn của spec §4.1**

> Chọn bậc model **lớn nhất** có `e2e_ms` p95 < 1500ms trên corpus giọng thật.
> Nếu **không bậc nào** lọt — kể cả tiny — thì dừng, báo số thật, để đội chọn.
> Không tự sửa ngân sách 1500ms cho vừa kết quả.

Vì Task 5 chưa có `e2e_ms` end-to-end (chưa chạy emulator), dùng **`server_ms` p95 làm chặn
trên tạm thời**: `server_ms` p95 phải < 1100ms để còn chỗ cho VAD + mạng + NLU + guard + TTS.
Ghi rõ đây là chặn xấp xỉ, và Task 6 mới xác nhận `e2e_ms` thật.

Trước khi hạ bậc model, thử `ASR_CPU_THREADS` đặt tay theo số core thật:
```bash
docker run -d --rm -p 8080:8080 -e ASR_CPU_THREADS=8 --name viva-asr-run viva-asr:small
```

- [ ] **Step 6: Viết manifest evidence**

Tạo `evidence/asr/v13/v13-manifest.txt` theo đúng khuôn của `asr-bench-manifest.txt` — gồm
nhãn nguồn, lệnh chạy, kết quả, **và phần giới hạn**:

```
GIOI HAN — bat buoc khai kem khi trich bat ky so nao o tren:
  1. Corpus giong that nhung chi MOT nguoi noi, nam, khong phai trong cabin xe.
  2. Nhieu la TONG HOP (noise_mix.py), khong phai thu trong cabin. So chi noi len
     muc SUY GIAM giua cac muc SNR, khong noi len do chinh xac tren duong that.
  3. server_ms/RTF do tren CPU may dev, KHONG phai CPU container node CarSky.
  4. Bo TTS 36 clip la regression set, khong tron vao so cong bo.
```

- [ ] **Step 7: Commit**

```bash
git add evidence/asr/v13
git commit -m "evidence(asr): ma tran 3 bac model x 2 che do biasing tren corpus giong that

Dong truc ablation ma 15-QUYET-DINH cam ket: ba tien de tung danh dau ❌
(hai engine cung PCM, cung dinh nghia endpoint, AsrClient da cam) deu da
thoa tu dd58b2b.

Model duoc chon theo luat spec §4.1, ghi kem ly do va gioi han."
```

---

## Task 6: Đổi engine mặc định sang remote

`BuildConfig.ASR_ENGINE` mặc định `"vosk"` tại `automotive/feature/voice/build.gradle.kts`.
`VoiceModule.provideSpeechRecognitionEngine` đã có sẵn nhánh `"remote"`. Đổi mặc định là
đổi **một giá trị**, không thêm logic runtime nào.

Không thêm tự động dò `/health` rồi chuyển đường — đó là hành vi mới cần test mới, hai
ngày trước freeze thì không đáng (spec §4.3).

**Files:**
- Modify: `automotive/feature/voice/build.gradle.kts:137` (dòng `ASR_ENGINE`)
- Modify: `automotive/README.md` (mục hướng dẫn chạy)

**Interfaces:**
- Consumes: `evidence/asr/v13/v13-manifest.txt` — model đã chọn ở Task 5

- [ ] **Step 1: Đổi mặc định**

Trong `automotive/feature/voice/build.gradle.kts`, sửa `buildConfigField` của `ASR_ENGINE`:

```kotlin
        buildConfigField(
            "String",
            "ASR_ENGINE",
            // `remote` is the default because the container model measurably beats
            // Vosk on the corpus in evidence/asr/v13. Vosk stays as the offline
            // fallback and is one flag away: -PvivaAsrEngine=vosk
            "\"" + (project.findProperty("vivaAsrEngine") ?: "remote") + "\"",
        )
```

- [ ] **Step 2: Xác minh cả hai variant vẫn build**

```bash
cd automotive
./gradlew :feature:voice:assembleMockDebug
./gradlew :feature:voice:assembleMockDebug -PvivaAsrEngine=vosk
```
Expected: cả hai `BUILD SUCCESSFUL`

- [ ] **Step 3: Chạy toàn bộ unit test**

Run: `cd automotive && ./gradlew test`
Expected: PASS toàn bộ, không giảm số test so với trước

- [ ] **Step 4: Xác minh trên emulator**

```bash
docker run -d --rm -p 8080:8080 --name viva-asr viva-asr:<bac-da-chon>
adb reverse tcp:8080 tcp:8080
cd automotive && ./gradlew installMockDebug
adb shell am start -n com.sopa.viva_automotive/.MainActivity
adb logcat -c && adb logcat | grep -E "VIVA_TRACE|VIVA_VOICE|VIVA_ASR"
```

Nói một câu tiếng Việt vào mic. Expected: trace đủ chín stage
`speech_start → speech_end → asr_sent → asr_done → nlu_done → guard_done → exec_done → render_done → tts_start`,
và `asr_done` mang text tiếng Việt đọc được.

- [ ] **Step 5: Ghi `e2e_ms` thật và đối chiếu ngân sách**

Từ trace, tính `speech_end → tts_start`. Nếu p95 vượt 1500ms trên bậc đã chọn: hạ một bậc
model, chạy lại bước 4. Nếu tiny cũng vượt — **dừng và báo đội**, đúng luật spec §4.1.

- [ ] **Step 6: Cập nhật README**

Trong `automotive/README.md`, sửa mục nói về `adb reverse` để nói rõ remote là mặc định và
Vosk là fallback offline, kèm lệnh đổi lại.

- [ ] **Step 7: Commit**

```bash
git add automotive/feature/voice/build.gradle.kts automotive/README.md
git commit -m "feat(voice): remote PhoWhisper lam engine mac dinh, Vosk lam fallback

Doi cau chuyen tu offline-only sang hybrid: container khi co mang, on-device
khi mat mang. Manh hon, va dung dung nen tang CarSky dang chay 22/22 node.

Chi doi mot gia tri mac dinh — khong them logic do /health luc chay. Vosk
van build nguyen ven bang -PvivaAsrEngine=vosk."
```

---

## Task 7: Hồi sinh tầng NLU T1 bằng embedding đa ngữ

`OnnxEmbeddingEncoder.kt:72-83` ghi lại một lỗi đội đã tự đo được: vocab WordPiece tiếng
Anh của MiniLM tách câu tiếng Việt có dấu thành toàn `[UNK]`, khiến `"đặt bàn ăn tối"` và
`"tốc độ hiện tại"` cùng ra cosine 1.0. Đội đã chặn bằng `isAllUnknown → return null`.

Chặn đó **đúng và phải giữ**. Nhưng hệ quả là tầng T1 trả `null` với hầu hết câu tiếng Việt
có dấu — nó không sai, nó không tồn tại. Task này làm nó bắt đầu tồn tại.

`distiluse-base-multilingual-cased-v2` là **DistilBERT**, không nhận `token_type_ids`.
`infer()` hiện luôn truyền cả ba input, nên sẽ ném lỗi. Sửa để chỉ truyền input mà session
khai báo — đó cũng là thiết kế đúng hơn, không phải chỗ chắp vá.

**Files:**
- Modify: `automotive/.../data/embedding/OnnxEmbeddingEncoder.kt:92-108`
- Modify: `automotive/.../data/embedding/OnnxEmbeddingIntentMatcher.kt:30,81`
- Modify: `automotive/feature/voice/build.gradle.kts:91-94`

**Interfaces:**
- Consumes: `BertWordPieceTokenizer.Encoding` (đã có: `inputIds`, `attentionMask`, `tokenTypeIds`, `isAllUnknown`)
- Produces: không đổi API công khai — `OnnxEmbeddingEncoder.embed(text): FloatArray?` giữ nguyên

- [ ] **Step 1: Đổi URL model trong Gradle**

Trong `automotive/feature/voice/build.gradle.kts`, sửa hai URL (dòng 91-94):

```kotlin
// distiluse-base-multilingual-cased-v2, not all-MiniLM-L6-v2: MiniLM's vocab is
// English WordPiece and tokenises accented Vietnamese into pure [UNK]. The
// encoder already refuses to embed an all-[UNK] sentence, which made tier T1
// return null for most real input — safe, but absent. See the comment in
// OnnxEmbeddingEncoder.embed().
val embeddingModelUrl =
    "https://huggingface.co/Xenova/distiluse-base-multilingual-cased-v2/resolve/main/onnx/model_quantized.onnx"
val embeddingVocabUrl =
    "https://huggingface.co/Xenova/distiluse-base-multilingual-cased-v2/resolve/main/vocab.txt"
```

Đổi luôn dòng `description` của task cho khớp:

```kotlin
    description = "Download multilingual sentence-embedding ONNX + vocab into assets if missing"
```

- [ ] **Step 2: Xoá asset cũ để buộc tải lại**

```bash
rm -rf automotive/feature/voice/src/main/assets/embeddings
cd automotive && ./gradlew :feature:voice:downloadEmbeddingModel
```
Expected: in ra `Embedding assets ready in …`. Kiểm `vocab.txt` có chứa token tiếng Việt:

```bash
grep -c "^##ệ\|^đ\|^ệ" automotive/feature/voice/src/main/assets/embeddings/vocab.txt
```
Expected: > 0. Nếu bằng 0 thì vocab **không** đa ngữ — dừng, chọn model khác, đừng đi tiếp.

- [ ] **Step 3: Chỉ truyền input mà model khai báo**

Trong `OnnxEmbeddingEncoder.kt`, thay `infer()` (dòng 92-108):

```kotlin
    private fun infer(ort: OrtSession, encoding: BertWordPieceTokenizer.Encoding): FloatArray {
        val shape = longArrayOf(1, encoding.inputIds.size.toLong())
        // Build only what this graph declares. BERT takes token_type_ids;
        // DistilBERT does not, and handing it one is a hard ORT failure. Reading
        // inputNames keeps the encoder working across both families instead of
        // encoding one model's signature as a law.
        val declared = ort.inputNames
        val tensors = LinkedHashMap<String, OnnxTensor>()
        try {
            tensors["input_ids"] =
                OnnxTensor.createTensor(env, LongBuffer.wrap(encoding.inputIds), shape)
            if ("attention_mask" in declared) {
                tensors["attention_mask"] =
                    OnnxTensor.createTensor(env, LongBuffer.wrap(encoding.attentionMask), shape)
            }
            if ("token_type_ids" in declared) {
                tensors["token_type_ids"] =
                    OnnxTensor.createTensor(env, LongBuffer.wrap(encoding.tokenTypeIds), shape)
            }
            ort.run(tensors).use { result ->
                return meanPoolAndNormalize(result[0].value, encoding.attentionMask)
            }
        } finally {
            tensors.values.forEach { it.close() }
        }
    }
```

- [ ] **Step 4: Bỏ hạ chữ thường cho model cased**

Trong `OnnxEmbeddingIntentMatcher.kt`, sửa dòng 30:

```kotlin
        // No lowercasing: the model is `-cased`, and folding case throws away a
        // signal it was trained to use. The tokenizer still normalises whitespace.
        val query = utterance.trim()
```

- [ ] **Step 5: Cập nhật log cho khỏi nói sai tên model**

Trong `OnnxEmbeddingEncoder.kt` dòng 55, sửa:

```kotlin
                Log.i(TAG, "Multilingual embedding encoder ready (${modelFile.length() / 1024} KB)")
```

- [ ] **Step 6: Build và chạy test**

```bash
cd automotive && ./gradlew :feature:voice:assembleMockDebug && ./gradlew test
```
Expected: `BUILD SUCCESSFUL`, toàn bộ test xanh

- [ ] **Step 7: Hiệu chỉnh lại `MIN_COSINE` bằng số**

Cài APK lên emulator, bật log rồi đọc câu thử:

```bash
adb logcat -c && adb logcat | grep "EmbedIntent"
```

Dòng `Semantic match "…" → … (cos=…)` cho phân bố cosine. Thu hai nhóm:
- **cặp đúng**: câu trong `corpus_prompts.tsv` khớp intent của nó
- **cặp sai**: `cmd_out_of_scope` ("đặt bàn ăn tối lúc bảy giờ") và các câu ngoài phạm vi

Chọn ngưỡng tách hai nhóm, **ưu tiên giảm false-accept** — một câu ngoài phạm vi bị nhận
nhầm thành lệnh xe nguy hiểm hơn một câu bị hỏi lại.

Sửa `OnnxEmbeddingIntentMatcher.kt:81` kèm lý do đo được:

```kotlin
        // Recalibrated for distiluse-base-multilingual-cased-v2 on <ngay>: correct
        // pairs sat at cos >= <x>, out-of-scope ("đặt bàn ăn tối lúc bảy giờ") peaked
        // at <y>. Threshold sits above <y> because a false accept becomes a vehicle
        // command, while a false reject only becomes a clarifying question.
        const val MIN_COSINE = <nguong>f
```

Thay `<ngay>`, `<x>`, `<y>`, `<nguong>` bằng số thật đọc được từ log. **Không bê nguyên
0.48f** — nó được hiệu chỉnh cho model cũ.

- [ ] **Step 8: Xác minh hồi quy trên bộ 22 câu**

Run: `cd automotive && ./gradlew test`
Expected: PASS. Nếu intent accuracy tụt so với baseline → revert task này. Nó là commit
độc lập, revert được riêng mà không đụng Task 1-6.

- [ ] **Step 9: Commit**

```bash
git add automotive/feature/voice/build.gradle.kts automotive/feature/voice/src/main/java/com/sopa/viva_automotive/feature/voice/data/embedding/
git commit -m "fix(nlu): embedding da ngu thay MiniLM tieng Anh o tang T1

OnnxEmbeddingEncoder da ghi lai loi doi tu do duoc: vocab WordPiece tieng
Anh tach cau tieng Viet co dau thanh toan [UNK], khien 'dat ban an toi' va
'toc do hien tai' cung ra cosine 1.0. Chan isAllUnknown la dung va duoc giu
— nhung he qua la T1 tra null voi hau het cau that. Tang do khong sai, no
khong ton tai.

infer() gio chi truyen input ma graph khai bao: DistilBERT khong nhan
token_type_ids. Bo lowercase vi model la -cased. MIN_COSINE hieu chinh lai
bang so do, khong be nguyen 0.48 cua model cu."
```

---

## Self-Review

**Spec coverage:**

| Spec | Task |
|---|---|
| §4.1 Thang model tiny/base/small + luật chọn | Task 5 |
| §4.1 Đòn bẩy `ASR_CPU_THREADS` | Task 5 bước 5 |
| §4.2 Domain biasing `initial_prompt` | Task 1 |
| §4.3 Đổi engine mặc định sang remote | Task 6 |
| §4.4 Embedding đa ngữ + hiệu chỉnh `MIN_COSINE` | Task 7 |
| §4.5a Corpus giọng thật 16 kHz | Task 4 |
| §4.5b Resample có lọc | Task 2 |
| §4.5c Intent accuracy qua router thật | Task 3 |
| §5.3 Ghi lại phân bố confidence sau đổi model | **Task 5 bước 6** — ghi vào manifest |
| §6.3 Tách nhãn nguồn số | Task 5 bước 6 |
| §7 Tiêu chí chấp nhận | rải trong bước xác minh của từng task |

**Khoảng trống đã bịt:** §5.3 ban đầu không có task nào — đã gộp vào Task 5 bước 6 (manifest
phải ghi phân bố confidence để đánh giá ngưỡng `G3_LOW_CONFIDENCE = 0.6` còn tách được không).

**Ghi chú phụ thuộc:** Task 5 cần xong Task 1-4. Task 6 cần Task 5. Task 7 độc lập hoàn toàn
— chạy song song được, và revert riêng được nếu hết giờ.

**Đường lùi nếu hết giờ trước 08/08:** giữ Task 1 + 2 + 3 (biasing + thước đo đúng). Riêng
Task 1 đã đứng độc lập và đủ giá trị.
