# V6 — `viva-asr`: container ASR cho VIVA

> Task nguồn: `vong2/06-PHAN-CONG-4-NGUOI.md` V6/V7 (Vĩ). Interface bắt buộc:
> `vong2/03-contracts.md` §2 (`AsrClient`). File này là **plan + design + research**.
>
> ⚙️ **02/08 — code đã có ở `asr/`**, viết đúng theo design bên dưới. Chốt tạm câu #1
> bằng cách **mặc định `PhoWhisper-tiny` INT8 nhưng đổi được bằng build-arg/env** —
> không chờ câu trả lời nữa vì V6 đã quá hạn 3 ngày và Long đang dùng stub. Câu #2
> (kiến trúc CPU node) và #3 (công thức `confidence`) **vẫn treo**, đã ghi rõ trong
> `asr/README.md` mục "Status" thay vì giấu. Xem mục "Trạng thái triển khai" cuối file.

## Problem statement

Long (`L4 — AsrClient`) đang dùng `FakeAsrClient` (stub trả text cố định) để không
bị chặn, nhưng pipeline thoại thật (push-to-talk → VAD → ASR → intent) không thể
demo được cho tới khi có một service ASR thật, tiếng Việt, chạy được trong Room
CarSky, đáp ứng ngân sách latency (~500ms cho chặng ASR, tổng đường edge trần
1.5s — theo `CLAUDE.md` gốc và `03-contracts.md` §1 bảng latency budget).

## Current baseline

`03-contracts.md` §2 khoá cứng interface, không được tự đổi:

```
POST /asr
Content-Type: application/octet-stream
Header: X-Sample-Rate: 16000
Header: X-Trace-Id: <traceId>
Body: raw PCM 16-bit LE mono

200 OK
{ "text": "hạ điều hòa xuống 22 độ", "confidence": 0.94, "server_ms": 210 }

GET /health -> {"status":"ok","model":"phowhisper-small-int8"}
```

Kotlin phía app gọi qua interface `AsrClient.transcribe(pcm16, sampleRate, trace) -> AsrResult`
(`text`, `confidence: Float`, `serverMs: Int`, `isPartial: Boolean = false`).

## Current situation

Chưa có gì. Không có Dockerfile, không có code Python, chưa build/test lần nào.
`06-PHAN-CONG-4-NGUOI.md` V6/V7 hạn đã qua (kế hoạch gốc đặt 29/07), và đây là
việc Long đang chờ (`V7 → L4` trong bảng "ai chờ ai" §5 file phân công) — mức độ
khẩn cấp cao thứ 2 sau V1 (DBC), nhưng khác V1 ở chỗ **việc này tôi làm được
trọn vẹn ngay bây giờ**, không cần tài khoản CarSky (chỉ cần lúc push image lên
Zot registry mới cần).

## Research

### Chọn model: PhoWhisper, không phải whisper-tiny gốc

`03-contracts.md` §2 ví dụ minh hoạ dùng tên `"phowhisper-small-int8"`, nhưng
`06-PHAN-CONG-4-NGUOI.md` V6 ghi "whisper-tiny INT8" và `04-KE-HOACH-CAP-NHAT-28-07.md`
liệt "Hạ xuống whisper-tiny INT8" như phương án khi RTF quá chậm — hai tài liệu
không khớp tên model. Đã tra cứu thật (không đoán):

| Model | Tham số | WER VIVOS (tiếng Việt) |
|---|---|---|
| `vinai/PhoWhisper-tiny` | 39M | 10.41% |
| `vinai/PhoWhisper-base` | 74M | 8.46% |
| `vinai/PhoWhisper-small` | 244M | 6.33% |

Nguồn: [VinAIResearch/PhoWhisper GitHub](https://github.com/VinAIResearch/PhoWhisper),
fine-tune Whisper trên 844 giờ audio tiếng Việt đa vùng miền
([arXiv:2406.02555](https://arxiv.org/pdf/2406.02555)).

**Khuyến nghị: `PhoWhisper-tiny`.** Cùng cỡ tham số với `whisper-tiny` gốc (39M)
nên tốc độ/latency tương đương những gì team đã tính trong ngân sách (~500ms),
nhưng WER tiếng Việt tốt hơn hẳn whisper-tiny gốc (vốn không fine-tune tiếng
Việt, sẽ tệ hơn nhiều trên giọng Việt so với 10.41% này — chưa đo số cụ thể của
bản gốc nên không so sánh bằng số, chỉ nói định tính). Nếu sau benchmark thật
thấy dư latency budget, có thể nâng lên `PhoWhisper-base` (74M, WER 8.46%) —
thiết kế "swap được model" bên dưới cho phép đổi bằng biến môi trường, không
sửa code.

### Engine phục vụ: faster-whisper (CTranslate2), không phải whisper.cpp

- `faster-whisper` (CTranslate2) hỗ trợ **convert model fine-tune tuỳ ý** từ
  HuggingFace (không chỉ model gốc OpenAI) qua công cụ `ct2-transformers-converter`,
  kèm quantize INT8 ngay lúc convert:
  ```
  ct2-transformers-converter --model vinai/PhoWhisper-tiny \
    --output_dir phowhisper-tiny-ct2 --quantization int8
  ```
  Xác nhận qua tài liệu chính thức SYSTRAN/faster-whisper và ví dụ convert model
  fine-tune tuỳ ý (nguồn: [faster-whisper GitHub](https://github.com/SYSTRAN/faster-whisper),
  [hướng dẫn convert model fine-tune](https://medium.com/@balaragavesh/converting-your-fine-tuned-whisper-model-to-faster-whisper-using-ctranslate2-b272063d3204)).
  → Giải toả mâu thuẫn "PhoWhisper hay faster-whisper": đây không phải 2 lựa chọn
  loại trừ nhau — PhoWhisper là **model**, faster-whisper là **engine chạy** nó.
- `whisper.cpp` là lựa chọn khác (nhanh trên CPU/Metal) nhưng dùng định dạng
  `ggml`, việc convert một fine-tune tiếng Việt ít tài liệu/rủi ro hơn so với
  đường CTranslate2 vốn có sẵn script convert chính thức cho mọi model HF-compatible.
  → Không chọn whisper.cpp cho v1.
- Số RTF/latency cụ thể tôi tổng hợp được từ vài bài viết 2025-2026 (không tự đo)
  nói INT8 CPU có thể đạt RTF <0.5 (tạm ổn cho pipeline nhạy latency) nhưng cũng có
  nguồn báo RTF ~2.5 trên CPU cho model **large** — số liệu không đồng nhất giữa
  các nguồn và phụ thuộc mạnh vào CPU cụ thể của pod CarSky. **Không tin số này,
  phải tự đo RTF thật của `PhoWhisper-tiny` INT8 trên chính container node CarSky
  ngay khi container chạy được** — đây là việc benchmark harness (`viva-tools`)
  đã sẵn sàng hỗ trợ đo (chặng `asr_processing`).

### Điểm chưa có nguồn xác nhận — ghi rõ để không lẫn với việc đã kiểm chứng

- **`confidence` score:** contract yêu cầu field `confidence: Float 0.0-1.0`,
  nhưng CTranslate2/faster-whisper trả `avg_logprob` (log-probability trung bình,
  số âm) và `no_speech_prob`, không có sẵn một số "confidence" chuẩn hoá 0–1.
  Cách quy đổi phổ biến là `exp(avg_logprob)` — đây là một **xấp xỉ**, không phải
  công thức chính thức được xác nhận, cần validate bằng tay trên vài câu mẫu
  trước khi tin số này cho ngưỡng `G3_LOW_CONFIDENCE` (`03-contracts.md` §4,
  Safety Guard dùng `confidence < 0.6` để quyết định hỏi lại).
- **Kiến trúc CPU của container node CarSky:** giả định x86_64 Linux (phổ biến
  cho hạ tầng container hoá) để build wheel `ctranslate2`/`faster-whisper` đúng
  kiến trúc — **chưa xác nhận với tài liệu CarSky**, cần kiểm khi có quyền truy
  cập container node thật (V3/V7).
- **GPU quota:** `vong2/04-KE-HOACH-CAP-NHAT-28-07.md` liệt đây là câu hỏi #2
  còn treo gửi mentor, chưa có câu trả lời tại thời điểm viết file này — thiết kế
  dưới đây mặc định **CPU-only** (an toàn hơn), có thể bật GPU sau nếu quota có.

## Design proposal

### Cấu trúc project (Python, tách biệt khỏi `backend/` Go)

```
asr/                          # sibling của backend/, không phải subfolder
  app/
    main.py                    # FastAPI app, 2 route: POST /asr, GET /health
    model.py                    # load WhisperModel 1 lần lúc startup, giữ singleton
    schemas.py                   # request/response Pydantic models khớp §2
  Dockerfile
  requirements.txt
  .env.example                  # ASR_MODEL_PATH, ASR_DEVICE, ASR_COMPUTE_TYPE
  tests/
    test_health.py
    test_asr_endpoint.py         # dùng audio mẫu ngắn, assert response shape
```

Vì sao tách khỏi `backend/`: `backend/CLAUDE.md` đã ghi rõ project Go đó cố tình
0 dependency ngoài stdlib và không có runtime Python — trộn 2 stack vào 1 thư
mục sẽ phá vỡ giả định đó và làm Dockerfile/CI của `backend/` phức tạp không
cần thiết. `asr/` có Dockerfile + CI riêng.

### Endpoint

- `POST /asr` — đọc header `X-Sample-Rate` (validate phải là `16000`, nếu khác
  trả `400`, không âm thầm resample sai chỗ), đọc `X-Trace-Id` (dùng để log,
  echo lại trong response header để dễ nối với `VIVA_TRACE` phía app nếu cần
  debug); đọc body raw PCM16LE, decode → numpy `float32` chuẩn hoá `[-1, 1]`,
  đưa vào `WhisperModel.transcribe()`. Đo `server_ms` bằng đồng hồ wall-clock
  quanh lệnh transcribe, không tính thời gian decode HTTP.
- `GET /health` — trả `{"status": "ok", "model": "<tên model + quantization>"}`
  đúng field đã có trong ví dụ hợp đồng; nếu model chưa load xong (đang khởi
  động) trả `503`, không trả `200` giả.

### Swap model không sửa code

Biến môi trường `ASR_MODEL_PATH` trỏ tới thư mục model CTranslate2 đã convert
(mount vào image hoặc bake sẵn), `ASR_COMPUTE_TYPE` (`int8`/`float16`/`float32`),
`ASR_DEVICE` (`cpu`/`cuda`). Đổi model = đổi biến môi trường + rebuild image với
model khác đã convert sẵn, không đổi `app/model.py`.

### Fault tolerance cần có (theo đúng tinh thần `backend/CLAUDE.md` đã áp dụng cho `viva-tools`)

| Edge case | Xử lý dự kiến |
|---|---|
| Model chưa load xong mà có request tới | `/health` trả 503; `/asr` trả 503 kèm `Retry-After`, không để FastAPI worker treo chờ |
| `X-Sample-Rate` khác 16000 | `400`, thông báo rõ — không resample ngầm (dễ che giấu bug phía client) |
| Audio quá ngắn (VAD cắt lỗi, gửi vài trăm ms) | Whisper vẫn chạy được nhưng kết quả rác — trả kèm `confidence` thấp, không cố "sửa" kết quả |
| Audio quá dài (client gửi nhầm nguyên phiên thay vì 1 utterance) | Giới hạn kích thước body ở tầng FastAPI (vd 30s audio ~ 1MB PCM16 mono 16kHz) — quá thì `413`, không cố xử lý rồi timeout phía app |
| Nhiều request đồng thời (2 người test cùng lúc) | Đã tra lại: CTranslate2's `Whisper` model có cơ chế worker-queue tích hợp (`inter_threads`, `max_queued_batches`) — 1 instance model **được thiết kế để dùng chung an toàn** cho nhiều request đồng thời khi cấu hình đúng, không cần tự khoá thủ công ([nguồn: CTranslate2 docs](https://opennmt.net/CTranslate2/python/ctranslate2.models.Whisper.html)). Việc cần làm khi code: đọc kỹ 2 tham số này, chọn giá trị hợp lý cho tải thấp của hackathon (vài request/lần demo), không cần tự viết lock |
| Container restart giữa demo | `/health` phải reflect đúng trạng thái ngay, để CarSky/monitoring biết mà không tưởng service còn sống |

## Câu hỏi cần chốt trước khi code (raise cho team, không tự đoán)

1. **Long xác nhận `PhoWhisper-tiny` hay `PhoWhisper-base`?** Ảnh hưởng trực
   tiếp `AsrClient`/`LatencyTrace` phía Long đang giữ. Tôi đề xuất tiny (an
   toàn latency), nhưng đây là quyết định ảnh hưởng UX nhận diện giọng nói,
   Long nên có tiếng nói cuối.
2. **Kiến trúc CPU thật của container node CarSky** — cần Vĩ (tôi) hoặc ai đó
   có quyền xem chi tiết node (`V3` đã có tool `viva-tools carsky nodes`,
   chỉ chưa chạy được vì thiếu token) xác nhận trước khi build image.
3. **Công thức `confidence` từ `avg_logprob`** — cần validate bằng tay trên vài
   câu mẫu tiếng Việt thật trước khi Safety Guard (Tùng) dựa vào ngưỡng 0.6.

## Tradeoff

Chọn CTranslate2/faster-whisper + PhoWhisper thay vì tự triển khai whisper.cpp
hay dùng thẳng `transformers` pipeline (chậm hơn, không có INT8 tối ưu sẵn):
đổi lấy tốc độ dev nhanh (script convert có sẵn, API Python đơn giản) và hiệu
năng CPU tốt, nhưng phụ thuộc một binary C++ (`ctranslate2`) phải build đúng
kiến trúc container node — rủi ro duy nhất nếu node CarSky là kiến trúc lạ
(ARM, ví dụ) thì phải build lại wheel, không có sẵn binary prebuilt.

## Kế hoạch triển khai (sau khi câu hỏi #1–#3 có câu trả lời tối thiểu #1)

1. Convert `PhoWhisper-tiny` sang CTranslate2 INT8 (local, 1 lần, output_dir
   commit vào repo hoặc lưu ngoài git nếu quá nặng — file model .bin thường
   vài chục MB với tiny, cần kiểm kích thước thật trước khi quyết định commit
   hay tải runtime).
2. Viết `app/model.py`, `app/schemas.py`, `app/main.py` theo đúng contract.
3. Test local bằng vài câu audio mẫu tiếng Việt tự thu hoặc lấy từ VIVOS
   (dataset công khai) — so khớp `text` trả về bằng mắt, đo `server_ms` thật.
4. Viết `tests/` (pytest + `TestClient` của FastAPI, không cần model thật load
   trong unit test nếu có thể mock — cần thiết kế để `model.py` mock được).
5. Dockerfile, build local trước, xác nhận image chạy + `/health` OK trước khi
   nghĩ tới push Zot (V7).
6. Chỉ tới bước push lên Zot / thêm Container Node vào blueprint (V7) mới cần
   CarSky credential thật.

---

## Trạng thái triển khai (cập nhật 02/08/2026)

| Bước trong kế hoạch | Trạng thái |
|---|---|
| 1. Convert PhoWhisper-tiny sang CTranslate2 INT8 | ⚙️ Đã tự động hoá trong `asr/Dockerfile` (stage `model-builder`), **chưa chạy thật** |
| 2. `app/model.py`, `app/schemas.py`, `app/main.py` | ✅ Xong, đúng contract §2 |
| 3. Test bằng audio tiếng Việt thật, đo `server_ms` | ❌ Chưa — cần model đã convert |
| 4. `tests/` (pytest + TestClient, mock model) | ✅ 20 test xanh, chạy được **không cần** faster-whisper |
| 5. Dockerfile, build local, `/health` OK | ⚠️ Dockerfile đã viết, **chưa build** — Docker daemon không chạy trên máy dev lúc viết |
| 6. Push Zot + Container Node (V7) | ❌ Cần credential CarSky |

**Ba thứ tuyệt đối không được khai là "đã xong"** khi viết write-up: chất lượng nhận
dạng (WER), latency/RTF thật, và việc image chạy được trên node CarSky. Cả ba đều cần
bước 3/5/6 ở trên.
