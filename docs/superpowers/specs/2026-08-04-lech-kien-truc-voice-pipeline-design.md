# Spec — Đóng khoảng lệch giữa kiến trúc voice mô tả và luồng app thực chạy

> **Ngày lập:** 04/08/2026 · **Chủ sở hữu:** Long · **Nguồn:** bản review pipeline voice/ASR
> **Ràng buộc thời gian:** feature freeze 05/08 23:59 · nộp bài 10/08 trước trưa

## 1. Vấn đề

Bản review đề xuất đổi model ASR và thêm denoiser/wake-word. Giá trị lớn nhất của nó
không nằm ở các đề xuất đó, mà ở chỗ nó làm lộ ra một khoảng lệch: **tài liệu của đội
mô tả một pipeline, còn APK chạy một pipeline khác.**

Kiến trúc được mô tả trong write-up và Claim–Evidence Map:

```text
mic → push-to-talk → Silero VAD endpointer → AsrClient (Vosk | viva-asr)
    → grammar router → SafetyGuard → vehicle action → HMI + TTS
```

Kiến trúc APK thực sự chạy:

```text
VoskSpeechRecognitionEngine tự mở AudioRecord → Vosk tự quyết endpoint
    → grammar router → (không có SafetyGuard) → vehicle action → HMI + TTS
```

Đây là loại sai mà hai ô barem *Ranh giới và tính tương xứng* (2đ) và *Minh bạch phạm
vi demo* (2đ) trừ điểm trực tiếp, và BGK Vòng 2 chính là mentor có quyền đọc repo.

## 2. Ba quyết định phạm vi đã chốt

| # | Quyết định | Lý do |
|---|---|---|
| Q1 | **Không viết một dòng mã sản phẩm nào** cho việc này trước 10/08 | Freeze 05/08 23:59. Hợp nhất capture/VAD là refactor xuyên biên `SpeechRecognitionEngine` — đúng loại việc mà mốc freeze tồn tại để chặn |
| Q2 | **Sửa claim ở mọi tài liệu đã "xong"**, kể cả write-up và slide; đồng thời dùng chính phát hiện này làm chất liệu cho mục bắt buộc *"AI sai ở đâu"* | Để lại mâu thuẫn giữa các file cùng nộp còn tệ hơn không viết gì. Và checklist nộp bài bắt buộc có mục đó — đây là ví dụ thật, có mã làm chứng |
| Q3 | **Phân tầng**: một file nội bộ giữ lập luận đầy đủ, bảy điểm trích ngắn vào bản nộp | Viết một lần, dùng nhiều chỗ. Bản nộp chỉ cần kết luận + đường trỏ về |

Ngoại lệ duy nhất của Q1: sửa tài liệu Markdown và slide; không đổi cả comment trong `.kt`.

## 3. Tám phát hiện

Mọi dòng dưới đây đã kiểm chứng bằng đọc mã hoặc `rg`, không phải suy đoán.

| # | Phát hiện | Bằng chứng |
|---|---|---|
| **F1** | Package `audio/` và `asr/` của `android/voice` **không được tham chiếu ở bất kỳ đâu** trong `automotive/`. Chỉ `trace/`, `tts/`, `intent/` được cắm vào app | `rg` các symbol trên `automotive/` chỉ trúng `GrammarIntentRouter` tại `ProcessVoiceCommandUseCase.kt:10,19` |
| **F2** | App không có VAD endpointer riêng; điểm cuối câu do Vosk tự quyết; Vosk tự mở một `AudioRecord` độc lập | `VoiceAssistantService.kt:93-96` (comment nói thẳng) · `VoskSpeechRecognitionEngine.kt:105` |
| **F3** | `SpeechRecognitionEngine.transcribe()` không nhận PCM → **không có cách nào** đưa cùng một audio cho hai engine | `data/SpeechRecognitionEngine.kt` |
| **F4** | `SafetyGuard` **không tồn tại trong mã sản phẩm**. Nó chỉ có trong ngữ pháp verdict, README, `benchmark_v1.csv` và harness Go | `VoiceTurnReport.kt:46` — *"There is no `SafetyGuard` in this build"* |
| **F5** | `TranscriptionEvent.Final(text)` **không mang confidence** → luật `G3_LOW_CONFIDENCE` không thể kích hoạt, kể cả khi guard đã tồn tại | `data/SpeechRecognitionEngine.kt` |
| **F6** | `confidence` của `viva-asr` là `exp(avg_logprob)`, không phải xác suất đã hiệu chỉnh; ngưỡng 0.6 chưa validate lần nào | `asr/README.md:84` — chính README đã tự nhận |
| **F7** | `benchmark_v1.csv` (22 câu) đo `text → intent → verdict`. Không có cột SNR/điều kiện nhiễu, không đo chặng mic → VAD → ASR | `backend/suites/benchmark_v1.csv` · `24-N5:51` đã tự nhận *"ba mức nhiễu — chưa tạo"* |
| **F8** | Device là **Cuttlefish ảo** (`CUTTLEFISHCVD01`, fingerprint `aosp_trout_arm64`), không có mic cabin | `evidence/c2/device-info.txt` |

### 3.1. Hai chỗ bản review nói chưa chính xác

Ghi lại để plan Vòng 3 không ước lượng sai:

1. Review đề nghị *"chuyển `VadSegmenter` từ xử lý cả buffer sang streaming endpointer"*.
   Thực tế `VadEndpointer` **đã là** state machine streaming — `accept(probability,
   frameStartSample)` chạy từng frame 512 mẫu (`VadSegmenter.kt:55`). Thứ còn thiếu chỉ
   là **driver** đọc mic sống và phát segment ra dần. Công việc nhỏ hơn review ước lượng.
2. Review đề nghị thêm pre-roll 200–300 ms. `VadConfig.speechPadMs=30` và phép lùi
   `candidateStart` đã có (`VadSegmenter.kt:10,65`), nhưng live driver vẫn cần circular
   buffer để giữ các frame trước trigger. Đây là cả việc tích hợp buffer lẫn tune tham số.

### 3.2. Hệ quả ngoài phạm vi voice — chuyển cho Tùng

F4 và F5 làm hai thứ của người khác mất nền:

- **Ablation A1** của `23-N4` (*"Tắt `SafetyGuard`"*) không có gì để tắt.
- B09, B10 và B20 trong `benchmark_v1.csv` phụ thuộc guard: lần lượt kỳ vọng
  `Confirm:G2_CONFIRM_DOOR`, `Deny:G1_SPEED_LOCK`, `Deny:G3_UNSUPPORTED`, mà build hiện tại
  không sinh ra được.

Long **báo**, không nhận việc. Việc báo phải xảy ra ở standup 21:30 ngày 04/08: nếu Tùng
còn kịp đưa `SafetyGuard` vào trước 23:59 ngày 05/08 thì A1 cứu được; sau freeze thì
không, và N4 mất một trong ba trục.

## 4. Kiến trúc tài liệu

```text
vong2/25-LECH-KIEN-TRUC-VOICE-PIPELINE.md   ← nguồn sự thật, lập luận đầy đủ
        │
        ├─► vong2/24-N5-TRANG-THAI-INTEGRATION.md   nhãn integration
        ├─► vong2/18-CLAIM-EVIDENCE-MAP.md          claim C-VOICE
        ├─► vong2/20-WRITE-UP-AI-VONG-2.md          mô tả pipeline + "AI sai ở đâu"
        ├─► vong2/12-PRODUCT-INTEGRATION-CARD.md    ô "bước kiểm chứng tiếp theo"
        ├─► vong2/15-QUYET-DINH-BENCHMARK-ASR.md    tiền đề hợp lệ của trục so sánh
        ├─► vong2/23-N4-ABLATION.md                 tiền đề chưa thoả của A1, A2
        ├─► android/voice/README.md                 cột "nằm trên đường chạy app?"
        └─► docs/bai-nop/vong2/VIVA_Pitch_Vong2.pptx              1 dòng trên slide kiến trúc
```

File 25 viết một lần và dài. Bảy chỗ còn lại mỗi chỗ 2–5 dòng, chỉ mang kết luận và
đường trỏ về — không copy lập luận sang bản nộp.

### 4.1. Nội dung file 25

| Mục | Nội dung |
|---|---|
| §1 | Hai sơ đồ pipeline đặt cạnh nhau: *mô tả* và *thực chạy* |
| §2 | Bảng 8 phát hiện F1–F8 kèm đường dẫn file:dòng |
| §3 | Hai chỗ review nói chưa chính xác (mục 3.1 ở trên) |
| §4 | Ba quyết định phạm vi Q1–Q3 và lý do |
| §5 | Roadmap Vòng 3 R1–R7 (mục 6 ở dưới) |
| §6 | Bảng "trích đi đâu" — ánh xạ sang 7 điểm trích, để người khác kiểm tra được tính nhất quán |

## 5. Sáu sửa đổi vào bản nộp

| # | File | Sửa gì | Xong khi |
|---|---|---|---|
| **S1** | `vong2/24-N5` | Tách dòng "Voice core": *(a)* nằm trong APK — grammar, TTS, audio focus, trace; *(b)* push-to-talk/Silero VAD **Mô phỏng**, có unit test nhưng chưa nằm trên đường chạy; *(c)* `AsrClient` container **Kế hoạch**; thêm `SafetyGuard` theo snapshot sau phản hồi Tùng. Ghi Device là Cuttlefish ảo | Không còn dòng nào gộp thứ đang chạy với thứ chưa cắm hoặc gộp hai nhãn khác nhau |
| **S2** | `vong2/18-N1` | Cột team-owned của claim **C-VOICE** đang gộp cả ba thứ chưa cắm. Tách ra. Sửa lý do trạng thái VÀNG: không phải *"chờ M6/Device"* mà là *"chưa nằm trên đường chạy"* — Device mở cũng không làm ba thứ đó xanh | Mỗi mảnh team-owned trỏ đúng loại evidence của nó (unit test / build / Device) |
| **S3** | `vong2/20-WRITE-UP` dòng 28–31 và 75–76 | Mô tả lại pipeline **đúng như APK chạy**. Giữ mô tả module như *kiến trúc đích*, nhưng gọi tên rõ đó là đích, không phải hiện trạng | Người đọc write-up rồi cài APK không thấy khác nhau |
| **S4** | `vong2/20-WRITE-UP` — **mục mới** | ⚠️ Write-up hiện **không có** mục *"AI sai ở đâu"*: §9 chỉ có prompt và MCP-driven testing, thiếu hai trong bốn ý mà checklist nộp bài bắt buộc. Thêm mục mới sau §9: AI sinh nhanh **hai nhánh song song** khớp nhau trên giấy nhưng lệch nhau trong mã; đội bắt được bằng rà soát chéo trước hạn; chọn **khai đúng thay vì vá vội** trước freeze, và ghi phần vá vào roadmap Vòng 3 | Mục mới nêu được: sai gì · phát hiện bằng cách nào · xử lý ra sao · vì sao không vá ngay |
| **S5** | `vong2/15` và `vong2/23-N4` | Thêm **tiền đề hợp lệ** vào trục Vosk vs container: chỉ có nghĩa khi hai engine nhận cùng PCM và cùng định nghĩa endpoint — hiện chưa (F3). A1 ghi rõ chưa có guard để tắt (F4) | Không ai đọc `15` rồi tưởng số benchmark chỉ còn chờ chạy |
| **S6** | `android/voice/README.md` và slide | README: thêm cột *"nằm trên đường chạy app?"* vào bảng trạng thái. Slide kiến trúc: thêm 1 dòng phân biệt *đang chạy* / *đích* | Bảng README không còn dấu ✅ đứng một mình cho thứ chưa cắm |
| **S7** | `vong2/12-N2` §5 | Thêm gate thứ hai — *Voice Pipeline Gate* — vào ô "bước kiểm chứng tiếp theo", lấy nội dung từ R1/R3/R4. Hiện ô này chỉ có Device Integration Gate về quyền VHAL | Ô "bước kiểm chứng tiếp theo" nêu được cả rào cản platform lẫn rào cản voice |

## 6. Roadmap Vòng 3

| # | Việc | Phụ thuộc | Ghi chú |
|---|---|---|---|
| **R1** | Một đường capture duy nhất, fan-out cho cả hai engine | — | Không dùng chữ ký `transcribe(pcm16, sampleRate)` kiểu one-shot mà review đề xuất: nó hợp với PhoWhisper (batch) nhưng **làm thoái hoá Vosk**, vốn là engine streaming có partial. Thiết kế đúng: *một mic → `Flow<PCM frame>` → fan-out*; Vosk tiêu thụ dòng liên tục, container nhận segment do VAD cắt |
| **R2** | Driver streaming cho `VadEndpointer`, circular pre-roll buffer; tune `speechPadMs` bằng audio thật | R1 | State machine đã có; live driver/buffer chưa có (mục 3.1) |
| **R3** | Bộ audio benchmark: 5 người nói × 22 câu × 3 điều kiện = 330 lượt, cộng 20–30 phút audio không lệnh để đo false trigger | R1 | **Khai đúng tên**: Device là Cuttlefish ảo (F8), nên đây là *audio thu ngoài rồi phát lại/inject*, **không phải "cabin thật"**. Gọi sai là tái phạm đúng lỗi F1–F3 |
| **R4** | Hiệu chỉnh confidence | R3, F4, F5 | Review bỏ sót hai tiền đề. Thứ tự đúng: `TranscriptionEvent.Final` mang được confidence → `SafetyGuard` tồn tại → lập bảng `confidence → transcript đúng/sai → intent đúng/sai` → chọn ngưỡng theo chi phí: lệnh vô hại chấp nhận thấp hơn, `door_lock` và `delivery_*` yêu cầu cao hơn hoặc hỏi xác nhận |
| **R5** | A/B ba cấu hình audio: raw `VOICE_RECOGNITION` / platform AEC-NS / enhancement bổ sung | R3 | Chọn theo WER và intent accuracy, không theo cảm nhận "nghe sạch hơn" |
| **R6** | Contextual bias cho từ khoá 10 intent; so sánh beam nhỏ với greedy hiện tại | R3 | Rẻ nhất trong nhóm cải thiện ASR. Chấm cả WER lẫn intent accuracy — transcript lệch mà intent đúng vẫn là lượt thành công |
| **R7** | Wake-word detector | R3 | Chỉ mở sau khi đo được false accepts/hour và false rejects. Push-to-talk giữ làm đường mặc định |

**Metric tách theo tầng** khi R3 có dữ liệu:

- VAD: false accept/hour · missed utterance · onset/offset error · endpoint latency.
- ASR: WER/CER · real-time factor · p50/p95.
- Sản phẩm: intent accuracy · command success rate · `speech_end → tts_start`.
- Lượt lỗi vẫn nằm trong mẫu với `Error:<stage>`, không lọc ra.

**Không làm, và ghi rõ đây là quyết định:** đổi `PhoWhisper-tiny` sang model lớn hơn
trước khi có R1, R3, R4. Không có bộ đo thì không chứng minh được model lớn hơn tốt hơn.

## 7. Ranh giới

Không làm:

- Không viết mã sản phẩm trước 10/08. Ngoại lệ: comment và README.
- Không hạ nhãn thứ vốn đã đúng. `viva-asr` container đang là **Kế hoạch** ở N5 — chính xác, giữ nguyên.
- Không nhận việc của Tùng. F4 và F5 là báo, không phải làm.
- Không mở lại phạm vi nào khác của review (denoiser, model lớn hơn, wake-word) ở Vòng 2.

## 8. Lịch

Ghép vào slot đã có trong `07-PLAN-CA-NHAN-LONG.md`, không xin thêm giờ.

| Ngày | Slot sẵn có | Nhét vào | Giờ |
|---|---|---|---|
| **04/08** | standup 21:30 | 🚨 Báo Tùng F4 + F5 | 0.25h |
| 05/08 | L9b + L10 + tuyên bố freeze | *không đụng* | 0 |
| 06/08 | L11 README (3h) | Viết file **25** trước, rồi mới viết README — README phải khớp nó | 1.5h |
| 07/08 | N1 Claim–Evidence Map (3.5h) | **S1, S2, S5** — đúng lúc đóng map | 1h |
| 08/08 | L12 write-up + L15 slide | **S3, S4, S6** | 1.5h |

Tổng ~4.25h, nằm gọn trong quỹ hiện có.

## 9. Rủi ro

| Rủi ro | Đánh giá |
|---|---|
| Hạ nhãn làm mất điểm | Hai ô *Ranh giới và tính tương xứng* (2đ) và *Minh bạch phạm vi demo* (2đ) chấm đúng chuyện này, và BGK đọc được repo. Khai đúng ăn 4đ; khai gộp mất 4đ **và** kéo theo nghi ngờ lên mọi claim còn lại |
| Sửa write-up/slide đã đánh dấu xong làm vỡ tiến độ | S3+S4+S6 tổng 1.5h, và L12/L15 vốn được xây theo lối claim-gated nên chỉ thay đoạn, không viết lại narrative |
| Tùng không kịp `SafetyGuard` trước freeze | A1 mất trục. Khi đó `23-N4` ghi *chưa đo* đúng như luật số 1 của chính file đó, và write-up khai N4 chạy 2 trục thay vì 3 |
| Người đọc hiểu file 25 là "đội thừa nhận làm sai" | Cách viết quyết định điều này. Khung đúng: đội **tự phát hiện bằng rà soát trước hạn** và chọn khai đúng — đó là nội dung của S4 |

## 10. Tiêu chí hoàn thành

- [ ] `vong2/25-LECH-KIEN-TRUC-VOICE-PIPELINE.md` tồn tại, có đủ 6 mục §1–§6.
- [ ] S1–S7 đã sửa xong, mỗi cái đạt cột "Xong khi" của nó.
- [ ] Không còn tài liệu nào mô tả `PushToTalkRecorder`, `VadEndpointer`, `AsrClient` hay
      `SafetyGuard` như thứ đang chạy trong APK.
- [ ] Write-up có mục *"AI sai ở đâu"* dùng chính phát hiện này, nêu đủ bốn ý: sai gì ·
      phát hiện bằng cách nào · xử lý ra sao · vì sao không vá ngay.
- [ ] Tùng đã xác nhận đã nhận F4 + F5 ở standup 04/08.
- [ ] Không có commit nào của đợt này sửa file `.kt` trong `android/voice/src` hoặc
      `automotive/`. Chỉ `.md`, `.pptx` và comment được phép đổi.
