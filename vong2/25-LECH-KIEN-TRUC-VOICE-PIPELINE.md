# 25 — Lệch giữa kiến trúc voice mô tả và luồng app thực chạy

> **Chủ sở hữu:** Long · lập 04/08/2026 · nguồn: rà soát pipeline voice/ASR.
> File này là **nguồn sự thật nội bộ**. Bản nộp chỉ mang kết luận trích từ đây, xem §6.
>
> Quy tắc: mọi phát hiện phải có đường dẫn `file:dòng` kiểm chứng lại được. Trạng thái
> `SafetyGuard` phải được chốt lại sau phản hồi/merge của Tùng và trước lần quét cuối.

## 0. Hai việc phải chuyển cho người khác — 04/08

| # | Phát hiện | Ảnh hưởng tới ai | Hạn |
|---|---|---|---|
| F4 | `SafetyGuard` **không tồn tại trong mã sản phẩm ở snapshot 04/08** — chỉ có contract, ngữ pháp verdict, fixture và harness (`VoiceTurnReport.kt:46`) | **Tùng (T5/T6).** A1 không có gì để tắt; B09, B10 và B20 không sinh được verdict guard tương ứng | 🔴 trước freeze 05/08 23:59 |
| F5 | `TranscriptionEvent.Final(text)` không mang confidence (`SpeechRecognitionEngine.kt:7`) | **Tùng.** `G3_LOW_CONFIDENCE` không kích hoạt được kể cả khi guard đã tồn tại | 🔴 cùng lúc với F4 |

Long **báo**, không nhận việc. Nếu sau freeze vẫn chưa có guard, `23-N4` giữ nguyên chữ
*chưa đo* ở A1 và write-up khai đúng giới hạn này.

**Tin nhắn đã chuẩn bị, chưa gửi vì phiên này không có kênh nhắn nhóm:**

> Tùng ơi, mình rà lại mã trước freeze và thấy hai thứ ảnh hưởng thẳng tới phần của Tùng:
> (1) SafetyGuard chưa có lớp hiện thực trong repo; `VoiceTurnReport.kt:46` ghi rõ không có
> guard trong build. (2) `TranscriptionEvent.Final` chỉ mang text, không mang confidence,
> nên `G3_LOW_CONFIDENCE` không có dữ liệu đầu vào. Hệ quả: A1 không có gì để tắt; B09,
> B10 và B20 lần lượt chưa thể sinh `Confirm:G2_CONFIRM_DOOR`, `Deny:G1_SPEED_LOCK`,
> `Deny:G3_UNSUPPORTED`. Mình không nhận việc này, chỉ báo để Tùng quyết trước freeze.

**Phản hồi Tùng:** chưa ghi nhận trong repo. Long cần gửi tin nhắn trên và cập nhật một trong
ba trạng thái: *nhận việc* · *không kịp* · *chưa trả lời*.

## 1. Hai pipeline, không phải một

**Tài liệu của đội đang mô tả:**

```text
mic → push-to-talk → Silero VAD endpointer → AsrClient (Vosk | viva-asr)
    → grammar router → SafetyGuard → vehicle action → HMI + TTS
```

**APK thực sự chạy ở snapshot 04/08:**

```text
VoskSpeechRecognitionEngine tự mở AudioRecord → Vosk tự quyết endpoint
    → grammar router → (không có SafetyGuard) → vehicle action → HMI + TTS
```

Trong `android/voice`, các package `trace/`, `tts/`, `intent/` được app tham chiếu. Hai
package `audio/` và `asr/` có mã và unit test nhưng không được tham chiếu trong `automotive/`.

## 2. Tám phát hiện

| # | Phát hiện | Bằng chứng |
|---|---|---|
| **F1** | `audio/` và `asr/` của `android/voice` không được tham chiếu trong `automotive/` | `rg` bốn symbol không có kết quả; grammar được dùng tại `ProcessVoiceCommandUseCase.kt:10,19` |
| **F2** | App không có VAD endpointer riêng; Vosk tự quyết endpoint và tự mở `AudioRecord` | `VoiceAssistantService.kt:93-96` · `VoskSpeechRecognitionEngine.kt:105` |
| **F3** | `SpeechRecognitionEngine.transcribe()` không nhận PCM, nên chưa thể cấp cùng audio cho hai engine | `SpeechRecognitionEngine.kt:11-14` |
| **F4** | `SafetyGuard` chưa có trong mã sản phẩm ở snapshot 04/08 | `VoiceTurnReport.kt:46-47` |
| **F5** | `TranscriptionEvent.Final(text)` không mang confidence | `SpeechRecognitionEngine.kt:7` |
| **F6** | Confidence của `viva-asr` là `exp(avg_logprob)`, chưa được hiệu chỉnh; ngưỡng 0.6 chưa validate | `asr/README.md:84` · `asr/app/model.py:36-58` |
| **F7** | `benchmark_v1.csv` đo text → intent → verdict, không đo mic → VAD → ASR hay điều kiện nhiễu | `backend/suites/benchmark_v1.csv` · `24-N5` mục dữ liệu synthetic |
| **F8** | Device evidence là Cuttlefish ảo, không phải cabin/xe thật | `evidence/c2/device-info.txt` — serial `CUTTLEFISHCVD01`, fingerprint `aosp_trout_arm64` |

F4 và F5 đã chuẩn bị nội dung chuyển cho Tùng ở §0. Sáu phát hiện còn lại là phần Long
phải phản ánh đúng trong tài liệu và roadmap.

## 3. Hai chỗ bản review cần diễn đạt chính xác hơn

1. `VadEndpointer` **đã là** state machine nhận từng frame qua
   `accept(probability, frameStartSample)` (`VadSegmenter.kt:55`). Thứ còn thiếu là driver
   đọc mic sống, giữ buffer và phát segment ra dần; không phải viết lại state machine.
2. `VadConfig.speechPadMs=30` và phép lùi `candidateStart` đã có
   (`VadSegmenter.kt:10,65`), nhưng đó chưa phải pre-roll hoàn chỉnh cho mic sống. Driver
   streaming vẫn cần circular buffer để giữ các frame trước trigger, rồi mới tune 30 ms hay
   200–300 ms bằng audio thật.

## 4. Ba quyết định phạm vi

| # | Quyết định | Lý do |
|---|---|---|
| Q1 | Không sửa mã sản phẩm trong đợt tài liệu này; chỉ `.md` và `.pptx` | Hợp nhất capture/VAD là refactor xuyên biên và chưa có audio/device evidence |
| Q2 | Sửa claim ở mọi tài liệu nộp; dùng chính khoảng lệch làm ví dụ cho mục *AI sai ở đâu* | Mâu thuẫn giữa repo, write-up và slide gây mất tính minh bạch |
| Q3 | File này giữ lập luận; các file nộp chỉ trích kết luận và trỏ về đây | Một nguồn sự thật, giảm drift |

## 5. Roadmap Vòng 3

| # | Việc | Phụ thuộc | Ghi chú |
|---|---|---|---|
| **R1** | Một đường capture duy nhất: mic → `Flow<PCM frame>` → fan-out | — | Vosk giữ streaming/partial; container nhận segment do VAD cắt |
| **R2** | Driver streaming cho `VadEndpointer`, circular pre-roll buffer; tune padding | R1 | State machine đã có; live driver/buffer chưa có |
| **R3** | Audio benchmark: 5 người × 22 câu × 3 điều kiện = 330 lượt, cộng 20–30 phút audio không lệnh | R1 | Với Cuttlefish, gọi đúng là audio thu ngoài rồi phát lại/inject, không phải cabin thật |
| **R4** | Truyền và hiệu chỉnh confidence | R3, F4, F5 | Boundary có confidence → guard tồn tại → đo correctness → chọn threshold theo rủi ro |
| **R5** | A/B raw `VOICE_RECOGNITION` / platform AEC-NS / enhancement bổ sung | R3 | Chọn theo WER và intent accuracy, không theo cảm giác “nghe sạch” |
| **R6** | Contextual bias cho miền 10 intent; so beam nhỏ với greedy | R3 | Chấm cả WER và intent accuracy |
| **R7** | Wake-word detector | R3 | Skeleton AOSP VIA + DSP bridge + software KWS “Vi-Vi ơi” đã vào mã; hotword vẫn **tắt mặc định** đến khi đo FA/hour + FRR; PTT/TTT giữ làm đường chính |

Metric tách theo tầng:

- VAD: false accept/hour · missed utterance · onset/offset error · endpoint latency.
- ASR: WER/CER · real-time factor · p50/p95.
- Sản phẩm: intent accuracy · command success rate · `speech_end → tts_start`.
- Lượt lỗi vẫn nằm trong mẫu với `Error:<stage>`, không lọc ra.

Không đổi `PhoWhisper-tiny` sang model lớn hơn trước khi có R1, R3 và R4.

## 6. Trích đi đâu

| # | File nhận | Trích gì | Trạng thái |
|---|---|---|---|
| S1 | `24-N5-TRANG-THAI-INTEGRATION.md` | Tách đường chạy, module mô phỏng, ASR kế hoạch; thêm guard và Cuttlefish | ✅ |
| S2 | `18-CLAIM-EVIDENCE-MAP.md` | Tách phần team-owned đang chạy và ngoài đường chạy | ✅ |
| S3 | `20-WRITE-UP-AI-VONG-2.md` §1 · §3 · §6 | Mô tả pipeline đúng như APK chạy | ✅ |
| S4 | `20-WRITE-UP-AI-VONG-2.md` mục mới | AI hỗ trợ tốt ở đâu và sai ở đâu | ✅ |
| S5 | `15-QUYET-DINH-BENCHMARK-ASR.md` · `23-N4-ABLATION.md` | Tiền đề benchmark; A1 chưa có guard | ✅ |
| S6 | `android/voice/README.md` · `docs/bai-nop/vong2/VIVA_Pitch_Vong2.pptx` | Phân biệt đường chạy và kiến trúc đích | ✅ |
| S7 | `12-PRODUCT-INTEGRATION-CARD.md` §5 | Voice Pipeline Gate | ✅ |

## 7. Kết quả quét nhất quán

Chạy ngày 04/08/2026 sau khi S1–S7 đã commit. Snapshot mã vẫn khớp §2: không có
`SafetyGuard`, `TranscriptionEvent.Final` chỉ mang `text`, và `automotive/` không tham chiếu
`PushToTalkRecorder`, `VadEndpointer` hay `AsrClient`.

| Kiểm tra | Kết quả |
|---|---|
| `git diff --name-only main...HEAD -- '*.kt'` | rỗng — không mã sản phẩm nào bị sửa trong nhánh tài liệu |
| Claim `PushToTalkRecorder` / `VadEndpointer` / `AsrClient` trong tài liệu hiện hành | đều được tách thành *chưa cắm* / *kiến trúc đích*; bản ghi 01/08 có cảnh báo đã lỗi thời |
| Claim `SafetyGuard` | không tài liệu hiện hành nào mô tả như thứ đang chạy; trạng thái vẫn là **Kế hoạch/Đỏ** |
| Bảy điểm trích S1–S7 | ✅ đủ 7/7 |
| Slide 3–4 | đã trích text, render toàn bộ 10 slide và chạy kiểm tra overflow; không phát hiện tràn nội dung |

Ngoại lệ vận hành còn mở: tin nhắn §0 đã được soạn nguyên văn nhưng chưa gửi, vì phiên này
không có kênh nhắn nhóm. Sau khi gửi, ghi phản hồi của Tùng ngay dưới tin nhắn và chạy lại
ba kiểm tra snapshot trước khi thay đổi bất kỳ trạng thái nào.
