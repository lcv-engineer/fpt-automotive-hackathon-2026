# 06 — `viva-asr`: service ASR tiếng Việt (+ Brain planner)

> Code: `asr/`. Thiết kế và so sánh model:
> [`docs/backend-docs/v6-viva-asr.md`](../backend-docs/v6-viva-asr.md).

---

## 1. Ba route

```
GET  /health          -> {"status":"ok","model":"phowhisper-tiny-int8", ...}
POST /asr             -> phien am mot cau noi
POST /v1/brain/plan   -> LLM planner cho VIVA Brain (tuy chon, mac dinh 503)
```

### `POST /asr`

```
Content-Type: application/octet-stream
X-Sample-Rate: 16000
X-Trace-Id: <traceId>
Body: raw PCM 16-bit LE mono

200 OK  { "text": "hạ điều hòa xuống 22 độ", "confidence": 0.94, "server_ms": 210 }
```

`server_ms` là **wall-clock quanh riêng bước transcribe**. Thời gian mạng =
`asr_sent → asr_done` **trừ** con số này — giữ hai thứ tách nhau là toàn bộ lý do
của trường đó.

### `POST /v1/brain/plan`

```
Content-Type: application/json
Authorization: Bearer <room-scoped deployment token>

{"text":"trong xe ngột ngạt quá","trace_id":"demo-1"}
```

Dùng `gpt-5.4-mini-2026-03-17` + **strict Structured Outputs**. Trả về **chỉ**: một
intent proposal trong allowlist, một clarification, hoặc `unsupported`. **Không có
vehicle tool, không thực thi được hành động.** Câu ghép có thể trả danh sách 2–3
action đã validate độc lập; Android chạy lần lượt qua gateway và SafetyGuard.

Chi tiết ràng buộc: [04 §6](04-VIVA-BRAIN.md) và
[`ADR-002`](../decisions/002-cloud-first-constrained-llm-planner.md).

---

## 2. Hành vi có chủ đích — mỗi dòng là một quyết định

| Trường hợp | Trả về | Vì sao |
|---|---|---|
| `X-Sample-Rate` ≠ 16000 | `400`, **không resample** | Contract cố định 16 kHz. Resample âm thầm biến bug của client thành "tự dưng kém chính xác" |
| Body không chia hết cho PCM16, hoặc rỗng | `400` | Upload cụt, không phải audio |
| Body > `ASR_MAX_BODY_BYTES` (30 s) | `413` | Đó là cả một phiên, không phải một câu — fail nhanh thay vì để app timeout |
| Audio < `ASR_MIN_AUDIO_MS` (200 ms) | `200` với `text: ""`, `confidence: 0.0` | Whisper **ảo giác ra cả câu** từ vài chục ms nhiễu. Kết quả rỗng là câu trả lời trung thực |
| Model đang nạp hoặc nạp lỗi | `503` + `Retry-After` | Health check xanh mà nói dối còn tệ hơn không có: CarSky sẽ route traffic vào pod không phục vụ được |
| Transcribe ném exception | `500`, **service vẫn sống** | Một câu hỏng không được làm chết container giữa demo |
| Thiếu `OPENAI_API_KEY` hoặc `VIVA_BRAIN_AUTH_TOKEN` | `/v1/brain/plan` → `503`, **ASR vẫn chạy** | Slow path là tuỳ chọn |
| Token sai/thiếu ở request | `401` **trước khi gọi provider** | Bảo vệ quota |

---

## 3. Cấu hình — mọi thứ là biến môi trường

`Settings.from_env()` trong `asr/app/config.py`. Cùng một image phục vụ nhiều cấu hình.

| Biến | Mặc định | Ghi chú |
|---|---|---|
| `ASR_MODEL_PATH` | `/models/phowhisper-tiny-ct2` | Thư mục CTranslate2, hoặc model id kiểu `tiny` |
| `ASR_MODEL_NAME` | `phowhisper-tiny-int8` | **Báo cáo verbatim ở `/health` và header `X-Asr-Model`** — giữ trung thực khi đổi model |
| `ASR_DEVICE` / `ASR_COMPUTE_TYPE` | `cpu` / `int8` | |
| `ASR_CPU_THREADS` / `ASR_NUM_WORKERS` | `0` / `1` | `0` = để CTranslate2 tự quyết theo CPU của pod |
| `ASR_LANGUAGE` | `vi` | |
| `ASR_BEAM_SIZE` | `1` (greedy) | Beam search mua thêm chút chính xác bằng latency mà ngân sách p95 1500 ms không có để chi |
| `ASR_INITIAL_PROMPT` | `None` | **Domain biasing** — xem §4 |
| `ASR_HOTWORDS`, `ASR_MAX_NEW_TOKENS` | `None`, `0` | |
| `ASR_MAX_BODY_BYTES` | `960000` (30 s) | |
| `ASR_MIN_AUDIO_MS` | `200` | |
| `OPENAI_API_KEY` | rỗng | **Chỉ phía server.** Không bao giờ vào Gradle/BuildConfig/APK/file tracked |
| `VIVA_BRAIN_MODEL` | `gpt-5.4-mini-2026-03-17` | |
| `VIVA_BRAIN_AUTH_TOKEN` | rỗng | Token triển khai xoay được, cấp riêng cho server và cho Android build |

⚠️ **Đổi env của node đang chạy trên CarSky là KHÔNG làm được** — deployment giữ
snapshot config lúc tạo. Xem [`docs/carsky/05 §3`](../carsky/05-VONG-DOI-DEPLOYMENT.md).

---

## 4. `ASR_INITIAL_PROMPT` — domain biasing

Whisper điều kiện hoá decoder trên đoạn text này, kéo lỗi cận-đồng-âm về phía từ
trong miền — đúng lớp lỗi `đặt → đặc`, `độ C → đỗ xê` trong
`evidence/asr/asr-bench-manifest.txt`. **Không tốn thêm thời gian decode** vì prompt
chỉ là context prepend.

`None`, không phải `""`: một prompt rỗng vẫn bị tokenize và prepend.

Giá trị đã dùng thật (xác minh 20/08, room `VIVA (Copy)`):

```
"Lệnh điều khiển xe: điều hòa, nhiệt độ, độ C, quạt, mức, cửa, khóa, nhạc, âm lượng…"
```

Kết quả A/B nằm ở `evidence/c2/voice-ab-prompt-20260820/` và
[`vong2/38-AB-DOMAIN-BIASING-CHO-DO-DANG.md`](../../vong2/38-AB-DOMAIN-BIASING-CHO-DO-DANG.md).

---

## 5. ⚠️ `confidence` là **xấp xỉ**, không phải xác suất của model

CTranslate2 báo `avg_logprob` (log-prob token trung bình), **không** phải điểm số
0–1 đã hiệu chuẩn. Service trả `exp(avg_logprob)` **có trọng số theo thời lượng**
qua các segment (`app/model.py: segments_to_confidence`).

`G3_LOW_CONFIDENCE` và `MIN_ACOUSTIC_CONFIDENCE` đều so với con số này ở ngưỡng
`0.6`. Hệ quả và cách xử lý: [03 §6](03-VIVA-VOICE.md).

**Trong write-up phải mô tả nó là một xấp xỉ**, không phải "độ tin cậy của model".

---

## 6. Model và Dockerfile

Build convert `vinai/PhoWhisper-tiny` sang **CTranslate2 INT8** trong một stage vứt
đi, nên **image runtime mang model nhưng không mang torch/transformers**.

### Đổi model không sửa code

| Muốn | Cách |
|---|---|
| Fine-tune Whisper khác trên HuggingFace | `--build-arg ASR_HF_MODEL=vinai/PhoWhisper-base --build-arg ASR_MODEL_NAME=phowhisper-base-int8` |
| Model tự convert | `docker run -v D:\models\my-ct2:/models/asr-ct2 -e ASR_MODEL_NAME=my-model …` |
| Đổi precision | `--build-arg ASR_QUANTIZATION=float16` + `-e ASR_COMPUTE_TYPE=float16` |

Convert tay:

```bash
ct2-transformers-converter --model vinai/PhoWhisper-tiny --output_dir phowhisper-tiny-ct2 --quantization int8 --copy_files tokenizer.json preprocessor_config.json
```

### 🔴 Bốn ràng buộc phiên bản tìm ra bằng cách build, không phải bằng đọc docs

Mỗi cái tốn một lần build hỏng. **Đừng "dọn dẹp" chúng mà không build lại.**

| Ràng buộc | Triệu chứng nếu vi phạm |
|---|---|
| `ctranslate2 >= 4.6` | `ImportError: libctranslate2-*.so: cannot enable executable stack` — wheel 4.4/4.5 mang cờ exec-stack mà kernel WSL2 từ chối. Đã kiểm: 4.4.0 ✗, 4.6.0 ✓, 4.8.1 ✓ |
| `transformers` 5.x | `TypeError: WhisperForConditionalGeneration.__init__() got an unexpected keyword argument 'dtype'` |
| `torch >= 2.6` | `ValueError: … vulnerability issue in torch.load … upgrade torch to at least v2.6` (CVE-2025-32434). ⚠️ Đây là thuộc tính **của model**: `vinai/PhoWhisper-tiny` ship `pytorch_model.bin`, không phải safetensors |
| `huggingface_hub < 1.0` | Container start rồi `/health` kẹt `503` với `ModuleNotFoundError: No module named 'requests'` — hub 1.x bỏ `requests`, faster-whisper 1.1.1 vẫn import |

---

## 7. Chạy và test

```powershell
py -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
$env:ASR_MODEL_PATH = "tiny"
.\.venv\Scripts\python.exe -m uvicorn app.main:app --port 8080
```

```powershell
docker build -t viva-asr:phowhisper-tiny-int8 asr/
docker run --rm -p 8080:8080 viva-asr:phowhisper-tiny-int8
```

```powershell
.\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt
.\.venv\Scripts\python.exe -m pytest -q
```

Test HTTP chạy với **transcriber giả** → không cần wheel CTranslate2 hay model trên
đĩa. Đó là lý do `requirements-http.txt` được tách khỏi `requirements.txt`.

Test Brain provider dùng `httpx.MockTransport` — chứng minh contract request tới
OpenAI và parse fail-closed **mà không tiêu token, không cần key thật**. Smoke test
live vẫn là một bước kiểm tra triển khai riêng.

---

## 8. Trạng thái đã kiểm chứng

| Hạng mục | Trạng thái |
|---|---|
| Contract HTTP (route, header, status code, body) | ✅ 20 test xanh |
| Decode PCM16, tổng hợp confidence | ✅ unit test |
| Dockerfile | ✅ build và chạy được (04/08) |
| Phiên âm tiếng Việt thật | ✅ 36 clip qua container: RTF median **0.167**, `server_ms` p50 **439** / p95 **667**, WER **0.411** trên giọng tổng hợp. Evidence: `evidence/asr/` |
| Latency **trên CarSky** | ⚠️ Số 439/667 ms là **CPU máy dev**, không phải trong room |
| Phục vụ thật trong room CarSky | ✅ log node: `VIVA_ASR model ready in 455 ms: phowhisper-tiny-int8`; đường app → node đã chạy 10/08 |

### ⚠️ Cách đọc con số WER

WER 0.411 nghe tệ; transcript nói điều cụ thể hơn — lỗi chủ yếu là **cận đồng âm**
(`đặt → đặc`, `độ C → đỗ xê`) và **rớt từ đầu câu**. Hai hệ quả:

- Clip là **giọng tổng hợp**, hạ mẫu 22.05 → 16 kHz bằng nội suy tuyến tính — thô,
  và chỉ có thể làm xấu đi. Audio 16 kHz thật nên tốt hơn.
- Intent router khớp theo từ khoá, nên **WER phóng đại thiệt hại**. Con số quan
  trọng với sản phẩm là **độ chính xác intent end-to-end**, và nó đến từ harness
  ([07](07-BACKEND-HARNESS.md)), không phải từ đây.

---

## 9. Vì sao là Python, không nằm trong `backend/`

`backend/` là CLI Go với quy tắc **zero-dependency** có chủ đích. Phục vụ INT8
Whisper được hỗ trợ tốt hơn nhiều trong Python (`faster-whisper`/CTranslate2) so với
ONNX binding của Go ⇒ đây là **service riêng, Dockerfile riêng**.
