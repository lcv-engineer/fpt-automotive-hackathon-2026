# Thiết kế — Nâng cấp model AI trong pipeline Voice AI Agent

> **Chủ sở hữu:** Long · **Ngày:** 06/08/2026 · **Trạng thái:** DESIGN — chờ duyệt
>
> **Ràng buộc lịch:** code freeze 08/08, nộp 10/08. Tức còn **2 ngày code**.
>
> **Nguồn đối chiếu:** source tại `6046c4e`; `28-PIPELINE-VOICE-AI-AGENT-HOAN-CHINH.md`;
> `15-QUYET-DINH-BENCHMARK-ASR.md`; `03-contracts.md`; `evidence/asr/asr-bench-manifest.txt`;
> notebook research speech AI `cd947b95` (truy vấn 06/08 — xem cảnh báo trích dẫn ở §4.1).

---

## 1. Vấn đề

Voice là phần ăn điểm chính của sản phẩm. Nếu trợ lý nghe sai thì mọi tầng phía sau —
grammar router, SafetyGuard, VHAL — đều không cứu được, vì chúng nhận đầu vào đã hỏng.

Số đo duy nhất đang có nói rằng đầu vào **đang** hỏng:

| Chỉ số | Giá trị | Nguồn |
|---|---|---|
| WER trung bình | 0.411 | `evidence/asr/asr-bench-manifest.txt` |
| WER = 0 tuyệt đối | 3/36 clip | cùng nguồn |
| Clip có WER > 0.5 | 10/36 | cùng nguồn |

Ba nguyên nhân, đã tách bạch được:

1. **Model quá nhỏ.** `PhoWhisper-tiny` là bậc nhỏ nhất của họ PhoWhisper (39M tham số).
2. **Không có domain biasing.** Lỗi phổ biến là phụ âm/dấu gần giống — `đặt`→`đác`,
   `độ C`→`độ xe`. Đây đúng là lớp lỗi mà một prompt từ vựng sửa được.
3. **Thước đo đang tự bóp méo chính nó.** Clip 22.05 kHz bị resample bằng nội suy tuyến
   tính; và WER phạt `đác`/`đặt` như sai hoàn toàn trong khi router làm việc trên từ khoá.
   Không sửa cái này thì không phân biệt được "model tốt lên" với "nhiễu đo".

Ngoài ASR còn một lỗi model nằm sẵn trong source: tầng NLU T1 dùng
`Xenova/all-MiniLM-L6-v2` — **model train trên tiếng Anh** — để so khớp ngữ nghĩa câu
tiếng Việt ở ngưỡng cosine 0.48.

## 2. Kết quả mong muốn

Khi xong, đội phải có đủ ba thứ:

1. Đường ASR mặc định của build demo chạy model tiếng Việt lớn nhất mà ngân sách
   latency còn chịu được — **chọn theo số đo, không theo phỏng đoán**.
2. Một bảng so sánh tái lập được giữa các bậc model và giữa hai deployment
   (on-device Vosk vs container), lấp đúng ô đang ghi *chưa đo* ở `23-N4` và ba tiền đề ❌
   ở `15-QUYET-DINH-BENCHMARK-ASR.md` §"Tiền đề hợp lệ".
3. Tầng NLU T1 dùng embedding thật sự biết tiếng Việt.

**Không** nằm trong kết quả mong muốn: một con số WER đẹp không tái lập được, hoặc một
model lớn hơn nhưng làm lượt thoại chậm tới mức demo mất nhịp.

## 3. Ràng buộc phần cứng — thứ quyết định trần của thiết kế

Container `viva-asr` chạy **CPU, không GPU**. Quota GPU trên CarSky vẫn để ngỏ
(`04-KE-HOACH-CAP-NHAT-28-07.md`) và không thể mở kịp trong 2 ngày.

Whisper pad mel lên **30 giây bất kể câu dài bao nhiêu**, nên chi phí encoder gần như cố
định và tỉ lệ với `layers × d_model²`:

| Bậc | enc/dec layers | d_model | Chi phí encoder tương đối | `server_ms` p50 ước tính |
|---|---|---|---|---|
| tiny | 4 / 4 | 384 | 1.0× | 439 (**đo thật**) |
| base | 6 / 6 | 512 | ~2.7× | ~1200 |
| small | 12 / 12 | 768 | ~12× | ~1500–2500 |
| medium | 24 / 24 | 1024 | ~43× | RTF > 1 — loại |

Ngân sách là **`e2e_ms` = `speech_end` → `tts_start`, p95 < 1500ms** (`03-contracts.md`
§1.3, định nghĩa ở dòng 187). ASR chỉ là một chặng trong đó; còn VAD, mạng, NLU, guard,
execute, TTS.

Con số ở cột cuối là **ước tính từ kiến trúc, chưa đo** — nên thiết kế này không đóng đinh
bậc nào thắng. Nó dựng cả ba bậc rồi chọn bậc lớn nhất còn lọt ngân sách.

## 4. Quyết định

### 4.1 Trục 1 — Thang model ASR: dựng tiny/base/small, chọn bằng số

Model swap đã là một build-arg, không phải đổi code (`asr/Dockerfile` dòng 9–13):

```bash
docker build --build-arg ASR_HF_MODEL=vinai/PhoWhisper-small \
             --build-arg ASR_MODEL_NAME=phowhisper-small-int8 \
             -t viva-asr:phowhisper-small-int8 asr/
```

Ba image, cùng corpus, cùng cách chấm. **Luật chọn, chốt trước khi thấy số** để không tự
điều chỉnh tiêu chí theo kết quả:

> Chọn bậc model **lớn nhất** có `e2e_ms` p95 < 1500ms trên corpus giọng thật.
>
> Nếu **không bậc nào** lọt — kể cả tiny — thì đây không còn là quyết định kỹ thuật mà là
> đánh đổi sản phẩm giữa "nghe đúng" và "trả lời kịp". Khi đó: dừng, báo số thật, để đội
> chọn. **Không** tự sửa ngân sách 1500ms cho vừa kết quả, và không tự hạ tiêu chí.

**Đòn bẩy latency được phép dùng trước khi hạ bậc model** — theo thứ tự:

1. `ASR_CPU_THREADS` — hiện là `0` (để CTranslate2 tự quyết). Đặt tay theo số core thật.
2. `ASR_NUM_WORKERS` — giữ 1 cho lượt đơn; chỉ tăng nếu đo thấy có lợi.
3. `beam_size` giữ nguyên 1. Beam search đổi latency lấy chất lượng — sai hướng ở đây.

#### Ứng viên từ research đã cân nhắc và loại

Đối chiếu với notebook research ngày 06/08 (`cd947b95`). **Cảnh báo trích dẫn: mọi con số
WER trong nguồn đó là leaderboard TIẾNG ANH (`en_shortform` / Open ASR), không phải hiệu
năng tiếng Việt.** Không được trích chúng như số tiếng Việt của VIVA.

| Ứng viên | Cỡ | Vì sao loại |
|---|---|---|
| Cohere Transcribe | 2.0B | Conformer 2B; "Running on CPU" là badge của Demo Space, không phải cam kết latency. Không có số CPU tự host |
| Qwen3-ASR | 1.7B | Nguồn ghi thẳng **không hỗ trợ suy luận CPU-only**. Container là CPU → loại dứt điểm |
| Whisper large-v3 | 1.55B | Chính nguồn đánh giá chạy CPU là *"barely usable"*, RTF 0.3–0.8 |
| Whisper large-v3-turbo | 809M | Vẫn quá lớn cho CPU trong ngân sách 1500ms |
| FireRedASR2 | — | Tích hợp LLM, cần GPU |
| **Moonshine** | 27M–331M | **Kiến trúc đúng nhất cho bài này** — streaming encoder, xử lý audio độ dài thay đổi thay vì pad 30s, chạy CPU tốt, có đường sherpa-onnx. Nhưng nguồn ghi: **gốc tiếng Anh, tiếng Việt mới chỉ có biến thể cộng đồng**. Không đủ chín cho sản phẩm tiếng Việt trong 2 ngày |
| Meta Omnilingual ASR | 300M–7.8B | 1600+ ngôn ngữ, có biến thể giải mã **CTC**, bản 300M hứa hẹn cho edge. Nguồn không xác nhận riêng tiếng Việt và không có số CPU. **Ứng viên đáng theo sau hackathon** |

**Notebook không chứa model ASR nào fine-tune riêng cho tiếng Việt** — không PhoWhisper,
không wav2vec2-vietnamese. Đó là lý do thang model ở trên vẫn dựa trên PhoWhisper: nó là
model tiếng Việt tốt nhất *đang có đường chạy trong repo này*, không phải vì nó thắng một
cuộc so sánh nào.

Bài học kiến trúc rút từ Moonshine được giữ lại và áp vào đường lùi ngay dưới: **thứ giết
latency không phải số tham số, mà là việc Whisper pad mọi câu lên 30 giây.**

**Phương án dự phòng nếu cả ba bậc Whisper đều vỡ ngân sách:** chuyển sang model **CTC**
(`nguyenvulebinh/wav2vec2-base-vietnamese-250h`). CTC là encoder-only, một lượt, **không
pad 30 giây** — nhanh hơn hẳn trên câu ngắn, và lệnh xe thì luôn ngắn. Đánh đổi: không có
dấu câu/viết hoa, và phải thay stack phục vụ (transformers thay cho faster-whisper), tức
`model.py` có thêm một `Transcriber` thứ hai. **Chỉ chạm tới nếu trục 1 thất bại**, vì nó
là ngày công thật chứ không phải một build-arg.

### 4.2 Trục 2 — Domain biasing bằng `initial_prompt`

`asr/app/model.py:93-100` hiện chỉ truyền `language`, `beam_size`, `vad_filter`. Thêm:

| Env mới | Mặc định | Vai trò |
|---|---|---|
| `ASR_INITIAL_PROMPT` | từ vựng lệnh xe | Ghim domain vocabulary vào decoder |
| `ASR_CPU_THREADS` | (đã có) | Đặt tay thay vì để CT2 đoán |

Prompt chứa đúng từ vựng 10 intent lõi: *điều hoà, nhiệt độ, độ C, quạt gió, mức, mở khoá
cửa, khoá cửa, ghế sưởi, âm lượng, bật, tắt, tăng, giảm*.

Đây là thay đổi **rẻ nhất và tốn 0ms latency** trong toàn bộ thiết kế, và nó đánh trúng
lớp lỗi đã quan sát được. Nó được đo như một biến độc lập: mỗi bậc model chạy hai lần,
có prompt và không, để biết phần cải thiện đến từ đâu.

### 4.3 Trục 3 — Đổi engine mặc định sang remote, Vosk thành fallback

Hiện `-PvivaAsrEngine=vosk` là mặc định (`VoiceModule.kt:80`). Đổi mặc định sang `remote`.

Đường đã thông sẵn: `adb reverse tcp:8080 tcp:8080` (`automotive/README.md:199`),
`BuildConfig.ASR_BASE_URL` mặc định `http://127.0.0.1:8080`, emulator gate đã mở, và
`network_security_config.xml` đã cho phép cleartext loopback.

Cái này đổi câu chuyện sản phẩm từ *offline-only* thành **hybrid: container khi có mạng,
on-device khi mất mạng** — mạnh hơn, và dùng đúng nền tảng CarSky đang chạy 22/22 node.

**Phạm vi:** chỉ đổi giá trị mặc định của build property. **Không** thêm logic tự động
dò `/health` rồi chuyển đường lúc chạy — đó là hành vi runtime mới cần test mới, hai ngày
trước freeze thì không đáng.

### 4.4 Trục 4 — Thay embedding NLU T1 bằng model multilingual

`Xenova/all-MiniLM-L6-v2` → `Xenova/distiluse-base-multilingual-cased-v2`.

Lý do chọn đúng model này thay vì model multilingual mạnh hơn: nó dùng **WordPiece**, nên
`BertWordPieceTokenizer` hiện có chạy nguyên. Họ e5/paraphrase-multilingual dùng
SentencePiece → phải viết tokenizer mới, không nằm trong 2 ngày.

Hệ quả phải xử lý, không được bỏ qua:

- **Chiều vector đổi** 384 → 512. `CosineSimilarity` không phụ thuộc chiều nên không sao,
  nhưng bất kỳ chỗ nào hard-code 384 đều phải sửa.
- **Ngưỡng `MIN_COSINE = 0.48f`** (`OnnxEmbeddingIntentMatcher.kt:81`) được hiệu chỉnh cho
  model cũ. Model mới có phân bố cosine khác → **phải hiệu chỉnh lại bằng số**, không được
  bê nguyên. Cách hiệu chỉnh ở §5.4.
- Kích thước model tăng (~135M vs 22M tham số) → APK to hơn. Chấp nhận được.

### 4.5 Trục 5 — Sửa thước đo (điều kiện cần của cả bốn trục trên)

Không sửa thì mọi so sánh ở trên đều vô nghĩa. Ba việc:

**a. Corpus giọng thật.** Thu ~20 câu lệnh, **thẳng ở 16 kHz mono** — không resample, gỡ
luôn giới hạn #2 của manifest. Một speaker (Long). Chạy qua `asr/scripts/noise_mix.py`
có sẵn để ra 3 mức nhiễu = ~60 clip. Đáp ứng đúng ma trận "20 utterance × 3 mức nhiễu"
mà `15-QUYET-DINH` cam kết.

> Giới hạn phải khai kèm mọi lần trích số: **một speaker, nam, không phải trong cabin xe.**
> Nó gỡ được giới hạn "giọng tổng hợp" nhưng không gỡ được "chưa phải điều kiện thật".

**b. Sửa resample cho 36 clip TTS.** Thay `resample_linear` bằng **polyphase windowed-sinc
22050→16000 (tỉ lệ 160/220.5 → dùng L=320, M=441)**, có low-pass chống aliasing ở Nyquist
đích. Giữ ràng buộc *stdlib-only* của bench hiện tại — bộ lọc viết tay, không thêm
scipy/numpy vào script chạy cạnh container. Bộ TTS giữ lại làm **regression set** — chạy
được không cần ai thu, hợp cho CI.

**c. Chấm intent accuracy qua router thật.** Đây là chỉ số quyết định, không phải WER —
chính manifest đã ghi *"ảnh hưởng thật sự cần đo bằng intent accuracy, không phải WER"*.

Cách làm: bench Python ghi CSV `(clip, reference, hypothesis)`; một **JVM test/CLI trong
`android/voice`** đọc CSV rồi cho hypothesis chạy qua `GrammarIntentRouter` thật.

> **Không port router sang Python.** Router chính là thứ đang được kiểm; một bản sao
> Python sẽ trôi khỏi bản Kotlin và biến con số thành vô nghĩa đúng lúc nó bắt đầu quan
> trọng.

## 5. Kiến trúc — cái gì đổi, cái gì không

### 5.1 Sáu quyết định của `28-PIPELINE` không bị phá

Thiết kế này **không** chạm vào bất kỳ ràng buộc kiến trúc nào ở `28-PIPELINE` §0. Cụ thể:

| Ràng buộc | Ảnh hưởng |
|---|---|
| Một chủ sở hữu microphone | Không đổi — vẫn `AndroidPcmSource` duy nhất |
| ASR chỉ là STT | Không đổi — biasing là tham số decoder, không phải trách nhiệm mới |
| Hai trigger, một đường sau trigger | Không đổi |
| LLM chỉ đề xuất `IntentProposal` | Không liên quan — không thêm LLM |
| SafetyGuard ở biên mọi lệnh ghi | Không đổi |
| Chỉ nói "Đã…" sau readback | Không đổi |

### 5.2 Ranh giới interface — không có interface mới

Toàn bộ thay đổi nằm **sau** các interface đang có:

- `SpeechRecognitionEngine.transcribe(Flow<PcmFrame>)` — không đổi chữ ký. Đổi model chỉ
  đổi thứ chạy phía sau `RemoteAsrTransport`.
- `SemanticIntentMatcher.bestIntent(String)` — không đổi. Đổi file ONNX + ngưỡng.
- Hợp đồng HTTP `POST /asr` (`03-contracts.md` §2) — **không đổi**: vẫn PCM16 16 kHz vào,
  vẫn ba trường `text` / `confidence` / `server_ms` ra.

Đây là lý do việc này vừa 2 ngày: hạ tầng V6 "swap được model" đã được thiết kế từ trước,
thiết kế này chỉ đi tiêu nó.

### 5.3 `confidence` sau khi đổi model

`segments_to_confidence()` (`asr/app/model.py:43`) là xấp xỉ từ `avg_logprob`, **không phải
xác suất đã hiệu chỉnh** — chính docstring đã ghi thế. Ngưỡng `G3_LOW_CONFIDENCE = 0.6`
(`03-contracts.md` §4) cưỡi lên số này.

Đổi model làm phân bố `avg_logprob` đổi theo. Vì vậy:

- Bench phải **ghi lại phân bố confidence** của model được chọn.
- Nếu phân bố lệch đủ để 0.6 không còn tách được đúng/sai, hiệu chỉnh lại ngưỡng bằng số
  **hoặc** ghi rõ trong write-up rằng nó chưa hiệu chỉnh.
- Tuyệt đối không đặt confidence giả để luật guard trông như đang chạy — cùng nguyên tắc
  đã áp cho `acousticConfidence = null` của Vosk (`28-PIPELINE` §2.4).

### 5.4 Hiệu chỉnh lại `MIN_COSINE` cho embedding mới

Dùng chính `IntentExemplarCatalog` làm dữ liệu hiệu chỉnh:

1. Với mỗi cặp (câu thử, intent đúng), tính cosine tới mọi exemplar.
2. Lấy phân bố cosine của **cặp đúng** và của **cặp sai**.
3. Chọn ngưỡng tách hai phân bố, ưu tiên **giảm false-accept** — một câu ngoài phạm vi bị
   nhận nhầm thành lệnh xe nguy hiểm hơn một câu bị hỏi lại.
4. Ghi ngưỡng + lý do vào comment ngay cạnh hằng số, như dòng hiện tại.

Bộ câu thử lấy từ 22 câu regression đã có của harness Vĩ (gồm cả câu **đáng lẽ bị từ
chối**) — ablation A4 đã dùng đúng bộ này.

## 6. Đo lường

### 6.1 Ma trận chạy

| Biến | Các mức |
|---|---|
| Model | phowhisper-tiny / base / small (INT8) |
| Biasing | có `initial_prompt` / không |
| Corpus | giọng thật 16 kHz × 3 mức nhiễu · TTS regression set |
| Engine đối chứng | Vosk vn-0.4 trên **cùng** file PCM |

Giữ cố định: cùng file PCM, cùng định nghĩa endpoint, cùng cách tính p50/p95.

### 6.2 Chỉ số

| Chỉ số | Vì sao |
|---|---|
| **Intent accuracy** | Chỉ số quyết định — đo cái người dùng thật sự nhận được |
| WER / CER | Giữ để so với baseline cũ; **không** dùng làm tiêu chí chọn |
| `server_ms` p50/p95 | Chi phí ASR tách khỏi lượt |
| `e2e_ms` p95 | Ngân sách 1500ms — tiêu chí chọn model |
| Phân bố confidence | Đầu vào cho §5.3 |
| Blank rate | `tts_volume_up` từng trả chuỗi rỗng — theo dõi hồi quy |

Lượt lỗi **nằm trong mẫu**. Timeout/blank ghi `Error:<stage>`, không lọc khỏi p95
(`03-contracts.md` dòng 191: *"càng hỏng càng đẹp số là hỏng cách đo"*).

### 6.3 Tách nhãn nguồn số

Báo cáo tách bốn nhóm, không trộn: `synthetic (TTS)` · `giọng thật, CPU máy dev` ·
`local emulator` · `CarSky Device`. Mọi số trong thiết kế này thuộc hai nhóm đầu cho tới
khi Device gate mở.

## 7. Tiêu chí chấp nhận

- [ ] Ba image tiny/base/small build được, `/health` trả đúng `model_name` của từng bậc.
- [ ] Corpus giọng thật ~20 câu × 3 mức nhiễu, 16 kHz mono, có ground truth TSV.
- [ ] Bench sinh CSV có cả WER **và** intent accuracy chấm qua `GrammarIntentRouter` thật.
- [ ] Có bảng so sánh ≥ 3 bậc model × 2 chế độ biasing, cộng đối chứng Vosk cùng PCM.
- [ ] Model mặc định được chọn theo luật §4.1, và luật đó ghi trong evidence manifest.
- [ ] Intent accuracy của cấu hình được chọn **cao hơn** baseline tiny-không-biasing.
- [ ] `MIN_COSINE` mới có số hiệu chỉnh, không phải giá trị bê nguyên.
- [ ] Toàn bộ test JVM + pytest `asr/` vẫn xanh.
- [ ] Manifest evidence khai đủ giới hạn: một speaker, không phải cabin, CPU máy dev.

## 8. Rủi ro và đường lùi

| Rủi ro | Dấu hiệu sớm | Đường lùi |
|---|---|---|
| small vỡ ngân sách 1500ms | `server_ms` p95 > 1100 | Hạ về base; nếu base cũng vỡ, giữ tiny + biasing |
| Cả ba bậc Whisper đều vỡ | RTF > 0.6 ở base | Chuyển hướng CTC (§4.1) — chỉ nếu còn ≥ 1 ngày |
| Build convert model quá lâu/hết đĩa | stage `model-builder` > 30 phút | Build lần lượt, xoá image trung gian; small ưu tiên cuối |
| Embedding mới làm tụt intent accuracy | Regression 22 câu tụt so với baseline | Revert về MiniLM — đây là commit độc lập, revert được riêng |
| Đổi mặc định sang remote làm demo phụ thuộc container | Emulator không gọi được `127.0.0.1:8080` | `-PvivaAsrEngine=vosk` vẫn build được nguyên vẹn |
| Hết thời gian trước freeze | Hết 07/08 mà chưa chọn xong | Giữ tiny + biasing (trục 2 đứng độc lập, đã đủ giá trị) |

Mỗi trục là **một commit độc lập revert được riêng**. Không gộp.

## 9. Thứ tự thực hiện

Build convert model tốn ~15–20 phút wall-clock, nên nó chạy nền còn người làm việc khác.

**06/08 — còn lại trong ngày**

1. Khởi động build `base` và `small` chạy nền.
2. Trong lúc chờ: thêm `ASR_INITIAL_PROMPT` + `ASR_CPU_THREADS` vào `config.py`/`model.py`
   + test pytest.
3. Sửa `resample_linear` → resample có lọc.
4. Thêm chấm intent accuracy (bench ghi CSV → JVM test đọc CSV chạy router thật).
5. Thu corpus ~20 câu ở 16 kHz; sinh 3 mức nhiễu.

**07/08**

6. Chạy ma trận §6.1, chọn model theo luật §4.1.
7. Đổi mặc định `VoiceModule` sang `remote`; xác minh trên emulator.
8. Thay embedding + hiệu chỉnh `MIN_COSINE` + chạy regression 22 câu.
9. Viết evidence manifest; cập nhật `15-QUYET-DINH`, `23-N4`, `28-PIPELINE` §8 P3.

**08/08** — freeze. Chỉ sửa lỗi chặn.

## 10. Ngoài phạm vi — và vì sao

Bức tranh speech AI có bảy lĩnh vực. Đây là vị trí từng lĩnh vực trong VIVA, để write-up
trả lời được câu *"vì sao không làm X"* bằng lý do kỹ thuật thay vì im lặng:

| Lĩnh vực | Quyết định | Lý do |
|---|---|---|
| **ASR** | ✅ Trục chính | Đúng chỗ lỗi đang nằm |
| **VAD** | ⏸️ Giữ nguyên | Silero VAD đã là chuẩn công nghiệp và đã trên đường chạy APK. Đổi = rủi ro không đổi lấy gì |
| **NLU embedding** | ✅ Trục phụ | Đang dùng model tiếng Anh cho tiếng Việt — lỗi thật |
| **TTS** | ❌ Không đụng | Android TTS đã chạy, có audio focus và fallback. TTS neural thêm hàng trăm MB và rủi ro focus/latency ngay trước freeze, đổi lấy "nghe hay hơn" — sai ưu tiên khi đầu vào vẫn đang sai |
| **Wake-word (KWS)** | ❌ Hoãn (P2) | Cần corpus positive/near-miss/noise mới chọn được ngưỡng. Không có số false-accept/hour đáng tin trong 2 ngày |
| **Speech enhancement** | ❌ Không thêm | `28-PIPELINE` §2.2 đã chốt: không thêm model enhancement chỉ vì "nghe sạch hơn"; phải A/B bằng WER/clipping/latency. Chưa có ngân sách đo đó |
| **Speaker verification/ID** | ❌ Ngoài MVP | Chưa có use case, chưa có consent, chưa có anti-spoofing |
| **Diarization** | ❌ Không cần | Trợ lý một lệnh một lượt; "ai nói khi nào" không giải bài toán nào đang có |
| **Paralinguistic** | ❌ Subsystem riêng | Không được dùng cảm xúc/buồn ngủ để tự động thực thi lệnh xe |
| **LLM T2** | ❌ Đã loại từ trước | `15-QUYET-DINH`: trái mục tiêu offline và rủi ro vượt ngân sách 1.5s |

### 10.1 Đáng theo sau hackathon

Hai ứng viên bị loại vì lịch chứ không vì kỹ thuật — ghi lại để không phải research lại:

- **Moonshine** (`usefulsensors/moonshine`, 27M–331M): streaming encoder xử lý audio độ dài
  thay đổi, không pad 30 giây — đúng kiến trúc cho lệnh ngắn 1–3 giây trong xe, chạy CPU
  tốt, có sherpa-onnx nên hợp cả đường on-device (trục 5). **Chặn bởi:** tiếng Việt mới chỉ
  có biến thể cộng đồng. Việc cần làm: đánh giá các biến thể đó, hoặc fine-tune tiếng Việt.
- **Meta Omnilingual ASR 300M, biến thể giải mã CTC**: CTC không tự hồi quy nên rẻ hơn hẳn
  trên câu ngắn; phủ 1600+ ngôn ngữ. **Chặn bởi:** nguồn chưa xác nhận riêng tiếng Việt và
  chưa có số CPU. Việc cần làm: kiểm tiếng Việt có trong tập được hỗ trợ không, đo CER.

---

## Phụ lục — file sẽ chạm

| File | Thay đổi |
|---|---|
| `asr/app/config.py` | Thêm `initial_prompt`; `cpu_threads` đặt tay |
| `asr/app/model.py` | Truyền `initial_prompt` vào `transcribe()` |
| `asr/tests/` | Test cho tham số mới |
| `asr/scripts/bench_tts_samples.py` | Resample có lọc; ghi CSV cho chấm intent |
| `asr/scripts/` | Script thu/chuẩn hoá corpus giọng thật |
| `automotive/feature/voice/build.gradle.kts` | URL + tên file embedding mới |
| `.../di/VoiceModule.kt` | Mặc định `remote` |
| `.../embedding/OnnxEmbeddingIntentMatcher.kt` | `MIN_COSINE` hiệu chỉnh lại |
| `android/voice/src/test/` | JVM scorer đọc CSV → `GrammarIntentRouter` |
| `evidence/asr/` | Manifest + CSV kết quả |
| `vong2/15`, `23`, `28` | Cập nhật trạng thái trục ablation |
