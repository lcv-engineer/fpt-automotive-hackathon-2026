# Đóng khoảng lệch kiến trúc voice pipeline — Kế hoạch thực thi

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Làm cho mọi tài liệu nộp bài mô tả đúng pipeline mà APK thực sự chạy, và biến chính việc phát hiện khoảng lệch thành nội dung ăn điểm cho mục *"AI sai ở đâu"*.

**Architecture:** Một file nội bộ `vong2/25-LECH-KIEN-TRUC-VOICE-PIPELINE.md` giữ toàn bộ lập luận và bằng chứng; tám điểm trích ngắn (2–5 dòng mỗi chỗ) đi vào các tài liệu nộp bài. Không copy lập luận sang bản nộp — bản nộp chỉ mang kết luận và đường trỏ về file 25.

**Tech Stack:** Markdown thuần. Một thay đổi trên `docs/bai-nop/vong2/VIVA_Pitch_Vong2.pptx` làm bằng tay trong PowerPoint. Không có mã sản phẩm nào bị sửa.

## Global Constraints

- **Không sửa file `.kt`** trong `android/voice/src` hoặc `automotive/`. Feature freeze là 05/08/2026 23:59. Đợt này chỉ đổi `.md` và `.pptx`; comment trong `.kt` cũng không đổi để chốt chặn Task 10 không mâu thuẫn.
- **Không hạ nhãn thứ vốn đã đúng.** `viva-asr` container đang là **Kế hoạch** ở N5 — chính xác, giữ nguyên. Claim `C-SAFETY` ở N1 đang là **ĐỎ — không có `SafetyGuard` trong repo** — chính xác, giữ nguyên.
- **Không nhận việc của Tùng.** `SafetyGuard` là T5/T6 của Tùng. Task 1 chỉ báo, không làm.
- **Ba nhãn integration giữ đúng định nghĩa của `24-N5`:** *Đã tích hợp* = đã chạy trên Device/nền tảng thật có log hoặc ảnh · *Mô phỏng* = chạy được nhưng đầu kia là mock/simulator/synthetic · *Kế hoạch* = contract đã có, code chưa chạy hoặc chưa nối.
- **Ngôn ngữ:** tiếng Việt, giọng văn khớp các file `vong2/` hiện có. Bảng dùng kiểu compact `|---|---|` như toàn bộ repo (linter IDE báo `MD060` là do cấu hình khác, bỏ qua).
- **Mọi đường dẫn mã trong tài liệu phải có dạng `file.kt:dòng`** để người khác kiểm chứng lại được.
- Spec nguồn: `docs/superpowers/specs/2026-08-04-lech-kien-truc-voice-pipeline-design.md`.

---

### Task 1: Báo Tùng về `SafetyGuard` — hạn 04/08 21:30

Đây là task duy nhất có deadline trước feature freeze. Nếu Tùng kịp đưa `SafetyGuard` vào trước 05/08 23:59 thì ablation A1 cứu được; sau freeze thì không, và N4 mất một trong ba trục.

**Files:**
- Create: `vong2/25-LECH-KIEN-TRUC-VOICE-PIPELINE.md` (chỉ phần §0 — phần còn lại ở Task 2)

**Interfaces:**
- Produces: file `vong2/25-*` tồn tại với heading `# 25 — Lệch giữa kiến trúc voice mô tả và luồng app thực chạy`, để Task 2 ghi tiếp.

- [ ] **Step 1: Xác minh lại phát hiện trước khi báo**

Chạy:

```bash
cd "e:/FPT Automative Hackathon 2026"
rg -n "(class|object|interface) SafetyGuard" automotive android -g '*.kt'
```

Kỳ vọng: **không có dòng nào**. Nếu có kết quả thì phát hiện F4 sai — dừng lại, báo người viết plan, không gửi tin nhắn.

```bash
rg -n "data class Final" automotive/feature/voice/src/main/java/com/sopa/viva_automotive/feature/voice/data/SpeechRecognitionEngine.kt
```

Kỳ vọng: `data class Final(val text: String) : TranscriptionEvent` — không có tham số `confidence`.

- [ ] **Step 2: Tạo file 25 với phần §0**

Tạo `vong2/25-LECH-KIEN-TRUC-VOICE-PIPELINE.md`:

```markdown
# 25 — Lệch giữa kiến trúc voice mô tả và luồng app thực chạy

> **Chủ sở hữu:** Long · lập 04/08/2026 · nguồn: rà soát pipeline voice/ASR.
> File này là **nguồn sự thật nội bộ**. Bản nộp chỉ mang kết luận trích từ đây, xem §6.
>
> Quy tắc của file: mọi phát hiện phải có đường dẫn `file:dòng` kiểm chứng lại được.
> Không có đường dẫn thì không được viết vào đây.

## 0. Hai việc phải chuyển cho người khác — 04/08

| # | Phát hiện | Ảnh hưởng tới ai | Hạn |
|---|---|---|---|
| F4 | `SafetyGuard` **không tồn tại trong mã sản phẩm ở snapshot 04/08** — chỉ có trong ngữ pháp verdict, README, `benchmark_v1.csv` và harness Go (`VoiceTurnReport.kt:46`) | **Tùng (T5/T6).** A1 của `23-N4` không có gì để tắt; B09 kỳ vọng `Confirm:G2_CONFIRM_DOOR`, B10 kỳ vọng `Deny:G1_SPEED_LOCK`, B20 kỳ vọng `Deny:G3_UNSUPPORTED`, nhưng build hiện tại không sinh được các verdict guard này | 🔴 trước freeze 05/08 23:59 |
| F5 | `TranscriptionEvent.Final(text)` **không mang confidence** (`SpeechRecognitionEngine.kt`) → luật `G3_LOW_CONFIDENCE` không kích hoạt được kể cả khi guard đã tồn tại | **Tùng.** Nếu làm guard mà không có confidence đi qua boundary thì G3 là luật chết | 🔴 cùng lúc với F4 |

Long **báo**, không nhận việc. Nếu sau freeze vẫn chưa có guard, `23-N4` giữ nguyên chữ
*chưa đo* ở A1 đúng theo luật số 1 của chính file đó, và write-up khai N4 chạy hai trục
thay vì ba.
```

- [ ] **Step 3: Soạn tin nhắn gửi Tùng**

Gửi trong nhóm trước standup 21:30, nội dung đúng như sau:

```text
Tùng ơi, mình rà lại mã trước freeze và thấy hai thứ ảnh hưởng thẳng tới phần của Tùng:

1. SafetyGuard hiện chưa có lớp hiện thực nào trong repo. Nó chỉ tồn tại trong ngữ pháp
   verdict, README, benchmark_v1.csv và harness Go. Chính comment trong mã đã ghi:
   VoiceTurnReport.kt:46 — "There is no SafetyGuard in this build".

2. TranscriptionEvent.Final chỉ mang text, không mang confidence. Nên kể cả khi guard có
   rồi, luật G3_LOW_CONFIDENCE vẫn không kích hoạt được vì không có số nào đi qua boundary.

Hệ quả: ablation A1 của N4 không có gì để tắt. B09, B10 và B20 trong benchmark
đều phụ thuộc guard: lần lượt kỳ vọng Confirm:G2_CONFIRM_DOOR, Deny:G1_SPEED_LOCK và
Deny:G3_UNSUPPORTED, trong khi build hiện tại không sinh được các verdict đó.

Mình không nhận việc này, chỉ báo để Tùng quyết trước 23:59 mai. Nếu kịp thì A1 cứu được;
sau freeze thì N4 còn hai trục thay vì ba, và mình sẽ ghi đúng như vậy vào write-up.
```

- [ ] **Step 4: Ghi nhận phản hồi**

Sau standup, thêm một dòng vào cuối §0 của file 25, chọn đúng một trong ba:

```markdown
**Phản hồi Tùng 04/08 21:30:** nhận việc, cam kết đưa `SafetyGuard` vào trước freeze.
```

```markdown
**Phản hồi Tùng 04/08 21:30:** không kịp trước freeze. A1 khai *chưa đo*; N4 chạy hai trục.
```

```markdown
**Phản hồi Tùng 04/08 21:30:** chưa trả lời. Nhắc lại sáng 05/08; nếu 12:00 chưa có thì coi như không kịp.
```

- [ ] **Step 5: Commit**

```bash
git add vong2/25-LECH-KIEN-TRUC-VOICE-PIPELINE.md
git commit -m "docs(vong2): mở file 25 và chuyển F4/F5 SafetyGuard cho Tùng"
```

---

### Task 2: Viết đủ file 25 — nguồn sự thật

**Files:**
- Modify: `vong2/25-LECH-KIEN-TRUC-VOICE-PIPELINE.md` (thêm §1–§6 sau §0)

**Interfaces:**
- Consumes: file 25 với §0 từ Task 1.
- Produces: các mục `§1` … `§6` mà bảy task sau trích về. Task 3–9 đều trỏ tới file này bằng chuỗi `vong2/25-LECH-KIEN-TRUC-VOICE-PIPELINE.md`.

- [ ] **Step 0: Chốt snapshot sau phản hồi/merge của Tùng**

Trước khi viết, cập nhật nhánh tài liệu với baseline dùng để nộp và chạy lại:

```bash
rg -n "(class|object|interface) SafetyGuard" automotive android -g '*.kt'
```

- Nếu vẫn rỗng: giữ F4/F5 và các đoạn *chưa có guard* như plan dưới đây.
- Nếu có kết quả: **không** chép nguyên văn các đoạn hard-code bên dưới. Cập nhật F4 thành
  *đã có mã, chờ evidence*, kiểm tra confidence boundary thật, rồi sửa S1/S2/S5/S6 và mục
  *AI sai ở đâu* theo snapshot mới. Ghi commit/hash chứa guard vào §0.

- [ ] **Step 1: Xác minh lại F1, F2, F3 trước khi viết**

```bash
cd "e:/FPT Automative Hackathon 2026"
rg -n "VadSegmenter|VadEndpointer|PushToTalkRecorder|AsrClient" automotive -g '*.kt'
```

Kỳ vọng: **không có dòng nào**. Đây là bằng chứng của F1.

```bash
rg -n -F "AudioRecord(" automotive/feature/voice/src/main/java/com/sopa/viva_automotive/feature/voice/data/vosk/VoskSpeechRecognitionEngine.kt
rg -n -F "no VAD endpointer of its own" automotive/feature/voice/src/main/java/com/sopa/viva_automotive/feature/voice/service/VoiceAssistantService.kt
```

Kỳ vọng: dòng 105 và dòng 93. Đây là bằng chứng của F2.

- [ ] **Step 2: Viết §1 — hai sơ đồ đặt cạnh nhau**

Thêm vào cuối file:

````markdown
## 1. Hai pipeline, không phải một

**Tài liệu của đội đang mô tả:**

```text
mic → push-to-talk → Silero VAD endpointer → AsrClient (Vosk | viva-asr)
    → grammar router → SafetyGuard → vehicle action → HMI + TTS
```

**APK thực sự chạy:**

```text
VoskSpeechRecognitionEngine tự mở AudioRecord → Vosk tự quyết endpoint
    → grammar router → (không có SafetyGuard) → vehicle action → HMI + TTS
```

Chỉ ba package của `android/voice` nằm trên đường chạy: `trace/`, `tts/`, `intent/`.
Hai package `audio/` và `asr/` có mã và unit test nhưng **không được tham chiếu ở bất kỳ
đâu** trong `automotive/`.

Đây là loại sai mà hai ô barem *Ranh giới và tính tương xứng* (2đ) và *Minh bạch phạm vi
demo* (2đ) trừ điểm trực tiếp, và BGK Vòng 2 là mentor có quyền đọc repo.
````

- [ ] **Step 3: Viết §2 — bảng tám phát hiện**

Thêm vào cuối file:

```markdown
## 2. Tám phát hiện

| # | Phát hiện | Bằng chứng |
|---|---|---|
| **F1** | Package `audio/` và `asr/` của `android/voice` không được tham chiếu ở bất kỳ đâu trong `automotive/`. Chỉ `trace/`, `tts/`, `intent/` được cắm vào app | `rg` 4 symbol trên `automotive/` không ra kết quả; `GrammarIntentRouter` được dùng tại `ProcessVoiceCommandUseCase.kt:19` |
| **F2** | App không có VAD endpointer riêng; điểm cuối câu do Vosk tự quyết; Vosk tự mở một `AudioRecord` độc lập | `VoiceAssistantService.kt:93-96` · `VoskSpeechRecognitionEngine.kt:105` |
| **F3** | `SpeechRecognitionEngine.transcribe()` không nhận PCM → không có cách nào đưa cùng một audio cho hai engine | `data/SpeechRecognitionEngine.kt` |
| **F4** | `SafetyGuard` không tồn tại trong mã sản phẩm | `VoiceTurnReport.kt:46` |
| **F5** | `TranscriptionEvent.Final(text)` không mang confidence → `G3_LOW_CONFIDENCE` không kích hoạt được | `data/SpeechRecognitionEngine.kt` |
| **F6** | `confidence` của `viva-asr` là `exp(avg_logprob)`, không phải xác suất đã hiệu chỉnh; ngưỡng 0.6 chưa validate lần nào | `asr/README.md:84` — chính README đã tự nhận |
| **F7** | `benchmark_v1.csv` (22 câu) đo `text → intent → verdict`. Không có cột SNR/điều kiện nhiễu, không đo chặng mic → VAD → ASR | `backend/suites/benchmark_v1.csv` · `24-N5` đã tự nhận "ba mức nhiễu — chưa tạo" |
| **F8** | Device lấy evidence là máy ảo Cuttlefish, không có micro cabin và không có xe | `evidence/c2/device-info.txt` — serial `CUTTLEFISHCVD01`, fingerprint `generic/aosp_trout_arm64/trout_arm64:14/...:userdebug/test-keys` |

F4 và F5 đã chuyển cho Tùng ở §0. Sáu phát hiện còn lại là việc của Long.
```

- [ ] **Step 4: Viết §3 — hai chỗ bản review nói chưa chính xác**

Thêm vào cuối file:

```markdown
## 3. Hai chỗ bản review nói chưa chính xác

Ghi lại để roadmap Vòng 3 không ước lượng sai.

1. Review đề nghị *"chuyển `VadSegmenter` từ xử lý cả buffer sang streaming endpointer"*.
   Thực tế `VadEndpointer` **đã là** state machine streaming — `accept(probability,
   frameStartSample)` chạy từng frame 512 mẫu (`VadSegmenter.kt:55`). Thứ còn thiếu chỉ là
   **driver** đọc mic sống và phát segment ra dần. Công việc nhỏ hơn review ước lượng.
2. Review đề nghị thêm pre-roll 200–300 ms. `VadConfig.speechPadMs=30` và phép lùi
   `candidateStart` đã có trong state machine (`VadSegmenter.kt:10,65`), nhưng đó **chưa phải
   pre-roll hoàn chỉnh cho mic sống**: driver streaming còn phải giữ circular buffer để phát
   lại các frame trước trigger. Vì vậy cần cả driver/buffer lẫn tune độ dài bằng audio thật.
```

- [ ] **Step 5: Viết §4 — ba quyết định phạm vi**

Thêm vào cuối file:

```markdown
## 4. Ba quyết định phạm vi

| # | Quyết định | Lý do |
|---|---|---|
| Q1 | **Không viết một dòng mã sản phẩm nào** trước 10/08. Ngoại lệ: comment và README | Freeze 05/08 23:59. Hợp nhất capture/VAD là refactor xuyên biên `SpeechRecognitionEngine` — đúng loại việc mà mốc freeze tồn tại để chặn |
| Q2 | **Sửa claim ở mọi tài liệu đã "xong"**, kể cả write-up và slide; đồng thời dùng chính phát hiện này làm chất liệu cho mục bắt buộc *"AI sai ở đâu"* | Để lại mâu thuẫn giữa các file cùng nộp còn tệ hơn không viết gì |
| Q3 | **Phân tầng**: file này giữ lập luận đầy đủ, tám điểm trích ngắn vào bản nộp | Viết một lần, dùng nhiều chỗ |
```

- [ ] **Step 6: Viết §5 — roadmap Vòng 3**

Thêm vào cuối file:

```markdown
## 5. Roadmap Vòng 3

| # | Việc | Phụ thuộc | Ghi chú |
|---|---|---|---|
| **R1** | Một đường capture duy nhất, fan-out cho cả hai engine | — | Không dùng chữ ký `transcribe(pcm16, sampleRate)` kiểu one-shot mà review đề xuất: nó hợp với PhoWhisper (batch) nhưng **làm thoái hoá Vosk**, vốn là engine streaming có partial. Thiết kế đúng: *một mic → `Flow<PCM frame>` → fan-out*; Vosk tiêu thụ dòng liên tục, container nhận segment do VAD cắt |
| **R2** | Driver streaming cho `VadEndpointer`, circular pre-roll buffer; tune `speechPadMs` bằng audio thật | R1 | State machine đã có; live driver và buffer chưa có, xem §3 |
| **R3** | Bộ audio benchmark: 5 người nói × 22 câu × 3 điều kiện = 330 lượt, cộng 20–30 phút audio không lệnh để đo false trigger | R1 | **Khai đúng tên**: Device là Cuttlefish ảo (F8), nên đây là *audio thu ngoài rồi phát lại/inject*, **không phải "cabin thật"**. Gọi sai là tái phạm đúng lỗi F1–F3 |
| **R4** | Hiệu chỉnh confidence | R3, F4, F5 | Thứ tự đúng: `TranscriptionEvent.Final` mang được confidence → `SafetyGuard` tồn tại → lập bảng `confidence → transcript đúng/sai → intent đúng/sai` → chọn ngưỡng theo chi phí: lệnh vô hại chấp nhận thấp hơn, `door_lock` và `delivery_*` yêu cầu cao hơn hoặc hỏi xác nhận |
| **R5** | A/B ba cấu hình audio: raw `VOICE_RECOGNITION` / platform AEC-NS / enhancement bổ sung | R3 | Chọn theo WER và intent accuracy, không theo cảm nhận "nghe sạch hơn" |
| **R6** | Contextual bias cho từ khoá 10 intent; so sánh beam nhỏ với greedy hiện tại | R3 | Rẻ nhất trong nhóm cải thiện ASR. Chấm cả WER lẫn intent accuracy — transcript lệch mà intent đúng vẫn là lượt thành công |
| **R7** | Wake-word detector | R3 | Chỉ mở sau khi đo được false accepts/hour và false rejects. Push-to-talk giữ làm đường mặc định |

Metric tách theo tầng khi R3 có dữ liệu:

- VAD: false accept/hour · missed utterance · onset/offset error · endpoint latency.
- ASR: WER/CER · real-time factor · p50/p95.
- Sản phẩm: intent accuracy · command success rate · `speech_end → tts_start`.
- Lượt lỗi vẫn nằm trong mẫu với `Error:<stage>`, không lọc ra.

**Không làm, và đây là quyết định:** đổi `PhoWhisper-tiny` sang model lớn hơn trước khi có
R1, R3, R4. Không có bộ đo thì không chứng minh được model lớn hơn tốt hơn.
```

- [ ] **Step 7: Viết §6 — bảng trích đi đâu**

Thêm vào cuối file:

```markdown
## 6. Trích đi đâu

Bảng này để người khác kiểm tra được tính nhất quán giữa file 25 và bản nộp.

| # | File nhận | Trích gì | Trạng thái |
|---|---|---|---|
| S1 | `24-N5-TRANG-THAI-INTEGRATION.md` | Tách nhãn Voice core; thêm dòng `SafetyGuard`; ghi chú Device là Cuttlefish ảo | ⬜ |
| S2 | `18-CLAIM-EVIDENCE-MAP.md` | Cột team-owned và lý do VÀNG của claim C-VOICE | ⬜ |
| S3 | `20-WRITE-UP-AI-VONG-2.md` §1 · §3 · §6 | Mô tả pipeline đúng như APK chạy | ⬜ |
| S4 | `20-WRITE-UP-AI-VONG-2.md` mục mới | *"AI hỗ trợ tốt ở đâu và sai ở đâu"* | ⬜ |
| S5 | `15-QUYET-DINH-BENCHMARK-ASR.md` · `23-N4-ABLATION.md` | Tiền đề hợp lệ của trục so sánh; A1 chưa có guard để tắt | ⬜ |
| S6 | `android/voice/README.md` · `docs/bai-nop/vong2/VIVA_Pitch_Vong2.pptx` | Cột "nằm trên đường chạy app?"; một dòng trên slide kiến trúc | ⬜ |
| S7 | `12-PRODUCT-INTEGRATION-CARD.md` §5 | Gate thứ hai — Voice Pipeline Gate | ⬜ |

Đổi ⬜ thành ✅ khi task tương ứng đã commit.
```

- [ ] **Step 8: Kiểm tra file đọc được và đủ mục**

```bash
rg -n "^## " vong2/25-LECH-KIEN-TRUC-VOICE-PIPELINE.md
```

Kỳ vọng: đúng bảy heading — `## 0.` … `## 6.`

- [ ] **Step 9: Commit**

```bash
git add vong2/25-LECH-KIEN-TRUC-VOICE-PIPELINE.md
git commit -m "docs(vong2): viết đủ file 25 — nguồn sự thật về lệch kiến trúc voice"
```

---

### Task 3 (S1): Tách nhãn integration ở `24-N5`

**Files:**
- Modify: `vong2/24-N5-TRANG-THAI-INTEGRATION.md:25` và phần sau bảng trạng thái
- Modify: `vong2/25-LECH-KIEN-TRUC-VOICE-PIPELINE.md` §6 — đổi S1 thành ✅

**Interfaces:**
- Consumes: §1 và §2 của file 25.
- Produces: bốn dòng bảng thay cho một dòng cũ; một khối `>` ghi chú về Cuttlefish.

- [ ] **Step 1: Thay dòng "Voice core" bằng ba dòng**

Trong `vong2/24-N5-TRANG-THAI-INTEGRATION.md`, tìm dòng 25:

```markdown
| Voice core (VAD · grammar 10 intent · TTS · audio focus · trace) | **Mô phỏng** | Unit test JVM xanh; APK `mock`/`real` build xanh | Chạy trên Device AAOS, nghe/thu trong cabin |
```

Thay bằng:

```markdown
| Voice core **trên đường chạy APK** (grammar 10 intent · TTS · audio focus · trace) | **Mô phỏng** | Unit test JVM xanh; APK `mock`/`real` build xanh | Chạy trên Device AAOS, nghe/thu trong cabin |
| Push-to-talk · Silero VAD — **module `android/voice`, chưa nằm trên đường chạy** | **Mô phỏng** | Unit test JVM xanh; baseline synthetic `threshold=0.50` | Cắm vào `VoiceAssistantService`. App hiện dùng `VoskSpeechRecognitionEngine` tự mở `AudioRecord` và **không có VAD riêng** (`VoiceAssistantService.kt:93`) |
| `AsrClient` → container `viva-asr` — **chưa nằm trên đường chạy** | **Kế hoạch** | Contract + fake client; 20 test HTTP dùng fake transcriber | Cắm client thật, build/chạy model thật và đo trên cùng PCM với Vosk |
| `SafetyGuard` (luật G1 · G3) | **Kế hoạch** | Contract §4, ngữ pháp verdict, harness Go đọc được `Deny:<rule>` | Chưa có lớp hiện thực nào trong snapshot 04/08 (`VoiceTurnReport.kt:46`). B09, B10 và B20 lần lượt phụ thuộc `G2_CONFIRM_DOOR`, `G1_SPEED_LOCK`, `G3_UNSUPPORTED` |
```

- [ ] **Step 2: Thêm ghi chú Cuttlefish sau bảng trạng thái**

Chèn ngay trước heading `## Dữ liệu synthetic — tạo thế nào`:

```markdown
> **Device dùng để lấy evidence là máy ảo Cuttlefish.** `evidence/c2/device-info.txt` ghi
> serial `CUTTLEFISHCVD01`, fingerprint
> `generic/aosp_trout_arm64/trout_arm64:14/UP1A.231005.007.A1/...:userdebug/test-keys`.
> Nó chạy AAOS thật nên đủ để đóng các gate về quyền, property và cài đặt app. Nhưng nó
> **không có micro cabin và không có xe**, nên mọi số liệu audio đo qua đường này phải khai
> là *audio thu ngoài rồi phát lại*, không được gọi là "đo trong cabin".
```

- [ ] **Step 3: Kiểm tra không còn dòng gộp**

```bash
rg -n -F "Voice core (VAD" vong2/24-N5-TRANG-THAI-INTEGRATION.md
```

Kỳ vọng: **không có kết quả**.

```bash
rg -c "chưa nằm trên đường chạy" vong2/24-N5-TRANG-THAI-INTEGRATION.md
rg -c "SafetyGuard" vong2/24-N5-TRANG-THAI-INTEGRATION.md
rg -c "CUTTLEFISHCVD01" vong2/24-N5-TRANG-THAI-INTEGRATION.md
```

Kỳ vọng: `1`, `1`, `1`.

- [ ] **Step 4: Đổi S1 thành ✅ ở §6 file 25**

Trong `vong2/25-LECH-KIEN-TRUC-VOICE-PIPELINE.md`, dòng S1: đổi `⬜` thành `✅`.

- [ ] **Step 5: Commit**

```bash
git add vong2/24-N5-TRANG-THAI-INTEGRATION.md vong2/25-LECH-KIEN-TRUC-VOICE-PIPELINE.md
git commit -m "docs(vong2): tách nhãn N5 cho thứ chưa nằm trên đường chạy APK"
```

---

### Task 4 (S2): Sửa claim C-VOICE ở `18-N1`

**Files:**
- Modify: `vong2/18-CLAIM-EVIDENCE-MAP.md` — dòng quy tắc (khoảng dòng 20–22) và dòng claim `C-VOICE`
- Modify: `vong2/25-LECH-KIEN-TRUC-VOICE-PIPELINE.md` §6 — đổi S2 thành ✅

**Interfaces:**
- Consumes: §1 và §2 của file 25.
- Produces: claim `C-VOICE` với cột team-owned tách hai phần và lý do trạng thái đã sửa.

Claim `C-SAFETY` ở file này **đã ghi đúng** *"**ĐỎ** — không có `SafetyGuard` trong repo"*. Không đụng vào nó.

- [ ] **Step 1: Thêm quy tắc số 4**

Sau dòng quy tắc số 3 (*"Chỉ `hvac_*` và `door_lock` có thể đi qua Vehicle Property…"*), thêm:

```markdown
4. Thành phần chỉ có unit test mà **chưa nằm trên đường chạy của APK** không được ghi vào cột team-owned như thể đang chạy. Phải nêu rõ, xem `25-LECH-KIEN-TRUC-VOICE-PIPELINE.md`.
```

- [ ] **Step 2: Sửa cột team-owned của C-VOICE**

Trong dòng `| **C-VOICE** | …`, thay ô thứ tư:

```markdown
Push-to-talk, Silero VAD, `AsrClient`, grammar router, TTS và audio focus
```

bằng:

```markdown
**Trên đường chạy:** grammar router 10 intent, TTS + 36 câu pre-render, audio focus, `LatencyTrace`. **Ngoài đường chạy** (có mã và unit test, chưa cắm): push-to-talk, Silero VAD, `AsrClient` — app dùng `VoskSpeechRecognitionEngine` tự mở `AudioRecord`
```

- [ ] **Step 3: Sửa ô trạng thái của C-VOICE**

Thay ô cuối:

```markdown
**VÀNG** — chờ M6/Device
```

bằng:

```markdown
**VÀNG** — phần trên đường chạy chờ M6/Device; phần ngoài đường chạy **Device mở cũng không xanh**, xem `25-LECH-KIEN-TRUC-VOICE-PIPELINE.md`
```

- [ ] **Step 4: Kiểm tra**

```bash
rg -n "Ngoài đường chạy" vong2/18-CLAIM-EVIDENCE-MAP.md
rg -n -F 'không có `SafetyGuard` trong repo' vong2/18-CLAIM-EVIDENCE-MAP.md
```

Kỳ vọng: lệnh đầu ra đúng 1 dòng (C-VOICE); lệnh sau vẫn ra 1 dòng (C-SAFETY giữ nguyên).

- [ ] **Step 5: Đổi S2 thành ✅ ở §6 file 25, rồi commit**

```bash
git add vong2/18-CLAIM-EVIDENCE-MAP.md vong2/25-LECH-KIEN-TRUC-VOICE-PIPELINE.md
git commit -m "docs(vong2): tách team-owned của C-VOICE theo đường chạy thật"
```

---

### Task 5 (S5): Thêm tiền đề hợp lệ vào `15` và `23-N4`

**Files:**
- Modify: `vong2/15-QUYET-DINH-BENCHMARK-ASR.md` — thêm mục trước `## Trạng thái`
- Modify: `vong2/23-N4-ABLATION.md` — thêm ghi chú vào A1 và A2
- Modify: `vong2/25-LECH-KIEN-TRUC-VOICE-PIPELINE.md` §6 — đổi S5 thành ✅

**Interfaces:**
- Consumes: F3, F4 ở §2 file 25.
- Produces: không ai đọc `15` rồi tưởng số benchmark chỉ còn chờ chạy.

- [ ] **Step 1: Thêm mục tiền đề vào `15`**

Chèn ngay trước heading `## Trạng thái`:

```markdown
## Tiền đề hợp lệ — chưa thoả ở snapshot 04/08

Trục so sánh trên chỉ có nghĩa khi hai engine nhận **cùng một audio** và **cùng một định
nghĩa điểm cuối câu**. Hiện chưa thoả:

| Tiền đề | Trạng thái | Bằng chứng |
|---|---|---|
| Hai engine nhận cùng PCM | ❌ | `SpeechRecognitionEngine.transcribe()` không nhận tham số PCM; Vosk tự mở `AudioRecord` riêng (`VoskSpeechRecognitionEngine.kt:105`) |
| Cùng định nghĩa điểm cuối câu | ❌ | APK không có VAD endpointer riêng; điểm cuối do Vosk tự quyết (`VoiceAssistantService.kt:93`) |
| Container `viva-asr` đã cắm | ❌ | `AsrClient` chưa được tham chiếu trong `automotive/` |

Vì vậy **không có số benchmark nào của trục này được công bố ở Vòng 2**, và ô tương ứng ở
`23-N4` giữ nguyên chữ *chưa đo*. Việc hợp nhất đường audio là R1 trong roadmap Vòng 3 tại
`25-LECH-KIEN-TRUC-VOICE-PIPELINE.md`.
```

- [ ] **Step 2: Thêm ghi chú vào A1 của `23-N4`**

Ngay dưới heading `## A1 — Tắt \`SafetyGuard\` (N4b, Tùng)`, chèn:

```markdown
> ⚠️ **Tiền đề chưa thoả tính đến 04/08:** `SafetyGuard` chưa có lớp hiện thực nào trong mã
> (`VoiceTurnReport.kt:46`), nên **không có gì để tắt**. B09 không thể sinh
> `Confirm:G2_CONFIRM_DOOR`, B10 không thể sinh `Deny:G1_SPEED_LOCK`, và B20 không thể sinh
> `Deny:G3_UNSUPPORTED` ở build hiện tại. Nếu tới freeze vẫn vậy, mọi ô dưới đây giữ
> nguyên chữ *chưa đo* theo luật số 1 của chính file này.
```

- [ ] **Step 3: Thêm ghi chú vào A2 của `23-N4`**

Ngay dưới heading `## A2 — Thay \`viva-asr\` container bằng đường cloud (N4a, Vĩ)`, chèn:

```markdown
> ⚠️ **Tiền đề chưa thoả tính đến 04/08:** `AsrClient` chưa được cắm vào app, và hai engine
> chưa nhận được cùng một PCM. Xem mục *Tiền đề hợp lệ* ở `15-QUYET-DINH-BENCHMARK-ASR.md`.
```

- [ ] **Step 4: Kiểm tra**

```bash
rg -n "Tiền đề chưa thoả" vong2/23-N4-ABLATION.md
rg -n "Tiền đề hợp lệ" vong2/15-QUYET-DINH-BENCHMARK-ASR.md
```

Kỳ vọng: lệnh đầu ra 2 dòng; lệnh sau ra 2 dòng (heading và dòng trỏ về trong `23`).

- [ ] **Step 5: Đổi S5 thành ✅ ở §6 file 25, rồi commit**

```bash
git add vong2/15-QUYET-DINH-BENCHMARK-ASR.md vong2/23-N4-ABLATION.md vong2/25-LECH-KIEN-TRUC-VOICE-PIPELINE.md
git commit -m "docs(vong2): ghi tiền đề chưa thoả của trục benchmark ASR và A1/A2"
```

---

### Task 6 (S7): Thêm Voice Pipeline Gate vào `12-N2`

**Files:**
- Modify: `vong2/12-PRODUCT-INTEGRATION-CARD.md` §5 — thêm gate thứ hai sau đoạn *Tiêu chí qua gate*
- Modify: `vong2/25-LECH-KIEN-TRUC-VOICE-PIPELINE.md` §6 — đổi S7 thành ✅

**Interfaces:**
- Consumes: R1, R3, R4 ở §5 file 25.
- Produces: ô "bước kiểm chứng tiếp theo" nêu cả rào cản platform lẫn rào cản voice.

- [ ] **Step 1: Thêm gate thứ hai**

Chèn vào cuối §5 của `vong2/12-PRODUCT-INTEGRATION-CARD.md`, sau đoạn bắt đầu bằng `**Tiêu chí qua gate:**`:

```markdown
**Validation gate thứ hai — Voice Pipeline Gate:**

Gate trên đóng câu hỏi *"lệnh có xuống được xe không"*. Gate này đóng câu hỏi *"câu nói có
lên được đúng intent trong tiếng ồn cabin không"* — hiện chưa có dữ liệu nào trả lời.

1. Hợp nhất đường thu âm: một micro, một dòng PCM, fan-out cho cả Vosk on-device lẫn
   container `viva-asr`. Hiện mỗi engine tự thu riêng nên không so sánh được (R1).
2. Thu bộ audio đánh giá: 5 người nói × 22 câu × 3 điều kiện, cộng 20–30 phút audio không
   chứa lệnh để đo false trigger. Device là máy ảo Cuttlefish nên đây là **audio thu ngoài
   rồi phát lại**, không phải thu trong cabin (R3).
3. Hiệu chỉnh `confidence` trên chính bộ audio đó rồi mới chọn ngưỡng cho `SafetyGuard`:
   lệnh vô hại chấp nhận thấp hơn, `door_lock` và `delivery_*` yêu cầu cao hơn hoặc hỏi xác
   nhận. Ngưỡng 0.6 hiện tại chưa validate lần nào (R4).

**Tiêu chí qua gate:** có WER và intent accuracy đo trên cùng một bộ audio cho cả hai đường
ASR, có false accept/hour của VAD, và ngưỡng confidence được chọn bằng số chứ không bằng mặc
định. Chi tiết và thứ tự phụ thuộc ở `vong2/25-LECH-KIEN-TRUC-VOICE-PIPELINE.md` §5.
```

- [ ] **Step 2: Kiểm tra**

```bash
rg -n "Voice Pipeline Gate" vong2/12-PRODUCT-INTEGRATION-CARD.md
```

Kỳ vọng: 1 dòng.

- [ ] **Step 3: Đổi S7 thành ✅ ở §6 file 25, rồi commit**

```bash
git add vong2/12-PRODUCT-INTEGRATION-CARD.md vong2/25-LECH-KIEN-TRUC-VOICE-PIPELINE.md
git commit -m "docs(vong2): thêm Voice Pipeline Gate vào Product & Integration Card"
```

---

### Task 7 (S3): Sửa mô tả pipeline trong write-up

**Files:**
- Modify: `vong2/20-WRITE-UP-AI-VONG-2.md` §1 (khoảng dòng 9), §3 (mục 1–2, khoảng dòng 30–32), §6 (bảng, khoảng dòng 75–76)
- Modify: `vong2/25-LECH-KIEN-TRUC-VOICE-PIPELINE.md` §6 — đổi S3 thành ✅

**Interfaces:**
- Consumes: §1 file 25.
- Produces: người đọc write-up rồi cài APK không thấy khác nhau.

Đoạn §3 nói *"Ở snapshot hiện tại, SafetyGuard và service boundary hoàn chỉnh chưa có evidence"* **đã đúng** — không đụng.

- [ ] **Step 1: Sửa §1 Tóm tắt**

Tìm câu bắt đầu bằng `Phần đội tự xây đã có bằng chứng ở mức source/JVM:` và thay cả câu bằng:

```markdown
Phần đội tự xây đã có bằng chứng ở mức source/JVM: push-to-talk, Silero VAD ONNX, hai biên ASR có thể thay thế,
router grammar cho 10 intent lõi, TTS tiếng Việt có 36 câu dự phòng, audio focus và trace latency theo từng chặng.
Ba thành phần đầu hiện là **module có unit test nhưng chưa nằm trên đường chạy của APK** — xem §3 và
`vong2/25-LECH-KIEN-TRUC-VOICE-PIPELINE.md`.
```

- [ ] **Step 2: Sửa mục 1–2 của §3**

Thay hai mục:

```markdown
1. `PushToTalkRecorder` thu PCM 16 kHz mono; `VadEndpointer` dùng Silero VAD ONNX để chốt cuối câu.
2. `AsrClient` là boundary có thể thay thế. Đường local hiện dùng Vosk on-device; đường container `viva-asr` được
   giữ làm vế so sánh khi endpoint của đội sẵn sàng.
```

bằng:

```markdown
1. **Đường chạy hiện tại:** `VoskSpeechRecognitionEngine` mở `AudioRecord` với audio source `VOICE_RECOGNITION`
   và để Vosk tự quyết điểm cuối câu. APK **không có VAD endpointer riêng** — comment tại
   `VoiceAssistantService.kt:93` ghi rõ điều này, nên `speech_start` là thời điểm bắt đầu nghe chứ không phải
   onset được phát hiện, và đội không back-date mốc đó để làm đẹp số.
2. **Kiến trúc đích, đã có mã và unit test nhưng chưa cắm:** `PushToTalkRecorder` thu PCM 16 kHz mono,
   `VadEndpointer` dùng Silero VAD ONNX chốt cuối câu, `AsrClient` là boundary thay thế được giữa Vosk on-device
   và container `viva-asr`. Khoảng lệch giữa hai đường này, lý do không vá trước freeze và kế hoạch đóng nó nằm ở
   `vong2/25-LECH-KIEN-TRUC-VOICE-PIPELINE.md`.
```

- [ ] **Step 3: Sửa hai dòng bảng §6**

Thay:

```markdown
| Push-to-talk + WAV + Silero VAD ONNX | Team-owned | Unit test JVM |
| ASR boundary + Vosk engine | Team-owned/tích hợp thư viện | Unit test/build; Device pending |
```

bằng:

```markdown
| Push-to-talk + WAV + Silero VAD ONNX | Team-owned — **module, chưa nằm trên đường chạy APK** | Unit test JVM |
| ASR: `VoskSpeechRecognitionEngine` trên đường chạy; boundary `AsrClient` chưa cắm | Team-owned/tích hợp thư viện | Unit test/build; Device pending |
```

- [ ] **Step 4: Kiểm tra không còn mô tả sai**

```bash
rg -n -F 'PushToTalkRecorder thu PCM 16 kHz mono; `VadEndpointer`' vong2/20-WRITE-UP-AI-VONG-2.md
```

Kỳ vọng: **không có kết quả**.

```bash
rg -c "25-LECH-KIEN-TRUC-VOICE-PIPELINE" vong2/20-WRITE-UP-AI-VONG-2.md
```

Kỳ vọng: `2`.

- [ ] **Step 5: Đổi S3 thành ✅ ở §6 file 25, rồi commit**

```bash
git add vong2/20-WRITE-UP-AI-VONG-2.md vong2/25-LECH-KIEN-TRUC-VOICE-PIPELINE.md
git commit -m "docs(vong2): write-up mô tả đúng pipeline APK đang chạy"
```

---

### Task 8 (S4): Viết mục "AI hỗ trợ tốt ở đâu và sai ở đâu"

Checklist nộp bài bắt buộc write-up có đủ bốn ý: *prompt đã dùng · AI hỗ trợ tốt ở đâu · **AI sai ở đâu** · MCP-driven testing*. §9 hiện chỉ có ý thứ nhất và thứ tư. Task này bù hai ý còn thiếu.

**Files:**
- Modify: `vong2/20-WRITE-UP-AI-VONG-2.md` — chèn mục mới sau §9, đánh số lại §10 và §11 cũ thành §11 và §12
- Modify: `vong2/25-LECH-KIEN-TRUC-VOICE-PIPELINE.md` §6 — đổi S4 thành ✅

**Interfaces:**
- Consumes: §1, §2, §3, §4 file 25.
- Produces: mục `## 10. AI hỗ trợ tốt ở đâu và sai ở đâu`.

- [ ] **Step 1: Đánh số lại hai mục cuối**

Trong `vong2/20-WRITE-UP-AI-VONG-2.md`:

- `## 10. Hướng sau Vòng 2` → `## 11. Hướng sau Vòng 2`
- `## 11. Tài liệu truy vết` → `## 12. Tài liệu truy vết`

- [ ] **Step 2: Chèn mục mới**

Chèn ngay trước `## 11. Hướng sau Vòng 2`:

```markdown
## 10. AI hỗ trợ tốt ở đâu và sai ở đâu

**Tốt ở đâu.** AI rút ngắn nhất ở ba loại việc có ranh giới rõ và có oracle để kiểm: sinh
`GrammarRule` cho 10 intent lõi cùng bộ test biến thể cách nói; dựng state machine của
`VadEndpointer` theo tham số Silero; và viết parser/aggregator Go cho `VIVA_TRACE` khớp
đúng format do đội tự định nghĩa. Ở cả ba, đội có sẵn cách phủ định kết quả — một bộ test,
một fixture, một định dạng log — nên nhận sai là thấy ngay.

**Sai ở đâu.** Sai lớn nhất của vòng này không phải một hàm hỏng, mà là **hai nhánh song
song khớp nhau trên giấy nhưng lệch nhau trong mã**. Module `android/voice` được xây cho
phần đầu của kiến trúc đích: push-to-talk → Silero VAD → `AsrClient` → grammar;
`SafetyGuard` mới chỉ có trong contract/verdict, chưa có lớp hiện thực. App
`automotive/` được xây theo đường ngắn nhất chạy được: `VoskSpeechRecognitionEngine` tự mở
`AudioRecord`, Vosk tự quyết điểm cuối câu, không VAD, không guard. Mỗi nhánh đều có unit
test xanh của riêng nó, nên không có test nào đỏ để báo động. Tài liệu thì mô tả nhánh thứ
nhất, còn APK chạy nhánh thứ hai.

**Phát hiện bằng cách nào.** Không phải bằng test, mà bằng một lượt rà soát chéo có chủ đích
trước hạn: tìm ngược từng lớp của module `android/voice` xem có ai gọi nó trong `automotive/`
không. Kết quả: `audio/` và `asr/` không được tham chiếu ở bất kỳ đâu. Bài học là **unit test
xanh chứng minh một thành phần đúng, không chứng minh nó được dùng** — đó cũng chính là ranh
giới mà bảng ba trạng thái ở `vong2/24-N5` gọi là *Mô phỏng* chứ không phải *Đã tích hợp*.

**Xử lý ra sao, và vì sao không vá ngay.** Vá đúng là hợp nhất đường thu âm để cả hai engine
nhận cùng một PCM — một refactor xuyên biên `SpeechRecognitionEngine`, đúng loại thay đổi mà
mốc feature freeze 05/08 tồn tại để chặn. Đội chọn **khai đúng thay vì vá vội**: sửa nhãn ở
`24-N5`, sửa cột team-owned của claim `C-VOICE` ở `18-N1`, sửa mô tả pipeline ở §3 trên, ghi
tiền đề chưa thoả vào trục benchmark ASR, và đưa phần vá vào roadmap Vòng 3 (R1–R4) tại
`vong2/25-LECH-KIEN-TRUC-VOICE-PIPELINE.md`. Một pipeline vá lúc 23:00 đêm trước freeze,
chưa từng chạy trên Device, sẽ tạo ra một claim mới không có bằng chứng — đúng thứ mà cả
bảng ba trạng thái lẫn Claim–Evidence Map được lập ra để ngăn.
```

- [ ] **Step 3: Kiểm tra**

```bash
rg -n "^## " vong2/20-WRITE-UP-AI-VONG-2.md
```

Kỳ vọng: 12 heading, đánh số liên tục `## 1.` … `## 12.`, không trùng số.

```bash
rg -c "AI hỗ trợ tốt ở đâu và sai ở đâu" vong2/20-WRITE-UP-AI-VONG-2.md
```

Kỳ vọng: `1`.

- [ ] **Step 4: Đổi S4 thành ✅ ở §6 file 25, rồi commit**

```bash
git add vong2/20-WRITE-UP-AI-VONG-2.md vong2/25-LECH-KIEN-TRUC-VOICE-PIPELINE.md
git commit -m "docs(vong2): thêm mục AI hỗ trợ tốt ở đâu và sai ở đâu vào write-up"
```

---

### Task 9 (S6): README module và slide

**Files:**
- Modify: `android/voice/README.md` — bảng `## Đang có gì`
- Modify: `docs/bai-nop/vong2/VIVA_Pitch_Vong2.pptx` — slide kiến trúc, sửa tay
- Modify: `vong2/25-LECH-KIEN-TRUC-VOICE-PIPELINE.md` §6 — đổi S6 thành ✅

**Interfaces:**
- Consumes: §1 file 25.
- Produces: bảng README không còn dấu ✅ đứng một mình cho thứ chưa cắm.

- [ ] **Step 1: Thêm cột vào bảng README**

Trong `android/voice/README.md`, bảng dưới heading `## Đang có gì`, đổi hàng tiêu đề:

```markdown
| Package | Task | Trạng thái |
|---|---|---|
```

thành:

```markdown
| Package | Task | Trạng thái | Nằm trên đường chạy APK? |
|---|---|---|---|
```

Rồi thêm ô thứ tư cho từng dòng, đúng như sau:

| Dòng | Ô thứ tư |
|---|---|
| `trace/` L2 | ✅ có |
| `audio/` L3a push-to-talk | ❌ **chưa** — app dùng `VoskSpeechRecognitionEngine` |
| `audio/` L3b/L3c Silero VAD | ❌ **chưa** — app không có VAD riêng (`VoiceAssistantService.kt:93`) |
| `asr/` L4 `AsrClient` | ❌ **chưa** |
| `intent/` L5a/L5b grammar | ✅ có — `ProcessVoiceCommandUseCase.kt:19` |
| `agent/` boundary | ❌ **chưa** |
| `tts/` L6 | ✅ có |
| `tts/` L7 audio focus | ✅ có |

- [ ] **Step 2: Thêm ghi chú dưới bảng README**

Chèn ngay sau bảng, trước heading `### Build evidence — kiểm lại 02/08/2026`:

```markdown
> **Cột thứ tư quan trọng hơn cột thứ ba.** Dấu ✅ ở cột *Trạng thái* nghĩa là mã và unit
> test đã có, **không** nghĩa là thành phần đang chạy trong APK. Ba mục `audio/` và `asr/`
> hiện là module độc lập; đường chạy thật của app đi qua `VoskSpeechRecognitionEngine`.
> Lý do, hệ quả và kế hoạch đóng khoảng lệch ở `vong2/25-LECH-KIEN-TRUC-VOICE-PIPELINE.md`.
```

- [ ] **Step 3: Kiểm tra README**

```bash
rg -n "Nằm trên đường chạy APK" android/voice/README.md
rg -c "❌ \*\*chưa\*\*" android/voice/README.md
```

Kỳ vọng: lệnh đầu ra 1 dòng; lệnh sau ra `4`.

- [ ] **Step 4: Sửa slide kiến trúc**

Mở `docs/bai-nop/vong2/VIVA_Pitch_Vong2.pptx` trong PowerPoint, tìm slide có sơ đồ voice pipeline, thêm một dòng chú thích nhỏ dưới sơ đồ:

```text
Đang chạy trong APK: mic → Vosk (tự endpoint) → grammar 10 intent → action → HMI/TTS.
Silero VAD, AsrClient và SafetyGuard là kiến trúc đích, đã có mã + test, chưa cắm.
```

Lưu file, giữ nguyên tên.

- [ ] **Step 5: Kiểm tra slide đã đổi**

Kiểm tra đủ ba lớp, không chỉ trạng thái file:

1. Extract text từ PPTX và xác nhận có chuỗi `Đang chạy trong APK`.
2. Render toàn bộ deck; xem riêng slide kiến trúc ở kích thước đầy đủ.
3. Chạy kiểm tra overflow/overlap; không chấp nhận chữ bị tràn, wrap sai hoặc che sơ đồ.

Cuối cùng `git status --short docs/bai-nop/vong2/VIVA_Pitch_Vong2.pptx` phải ra
`M docs/bai-nop/vong2/VIVA_Pitch_Vong2.pptx`.

- [ ] **Step 6: Đổi S6 thành ✅ ở §6 file 25, rồi commit**

```bash
git add android/voice/README.md docs/bai-nop/vong2/VIVA_Pitch_Vong2.pptx vong2/25-LECH-KIEN-TRUC-VOICE-PIPELINE.md
git commit -m "docs: README module voice và slide phân biệt đường chạy với kiến trúc đích"
```

---

### Task 10: Quét nhất quán lần cuối

**Files:**
- Modify: `vong2/25-LECH-KIEN-TRUC-VOICE-PIPELINE.md` — thêm §7 kết quả quét
- Verify: toàn repo

**Interfaces:**
- Consumes: kết quả của Task 3–9.
- Produces: bằng chứng rằng không còn tài liệu nào mô tả thành phần chưa cắm như thứ đang chạy.

- [ ] **Step 0: Chốt lại snapshot sau freeze**

Trước quét, cập nhật baseline dùng để nộp và chạy lại kiểm tra `SafetyGuard`, `AsrClient`
cùng các reference runtime. Nếu trạng thái khác snapshot 04/08, sửa file 25 và S1–S7 theo
code thật trước khi ghi §7; không giữ claim cũ chỉ để lệnh tìm kiếm xanh.

- [ ] **Step 1: Quét mã sản phẩm không bị đụng**

```bash
git diff --name-only main...HEAD -- '*.kt'
```

Kỳ vọng: **không có kết quả do chính đợt tài liệu này tạo ra**. Nếu có, dừng và xác định
commit/chủ sở hữu; không tự động hoàn tác thay đổi của Tùng hoặc thay đổi đã có trên baseline.

- [ ] **Step 2: Quét claim còn sót**

```bash
rg -n "PushToTalkRecorder|VadEndpointer|Silero VAD|AsrClient" vong2 android/voice/README.md -g '*.md'
```

Đọc từng dòng kết quả. Mỗi dòng phải thoả một trong ba:
- nằm trong file 25, hoặc
- có kèm chữ *"chưa nằm trên đường chạy"* / *"kiến trúc đích"* / *"chưa cắm"* / *"Ngoài đường chạy"*, hoặc
- là mục lịch sử trong `07-PLAN-CA-NHAN-LONG.md` hay `10-BAN-GIAO-L2-29-07.md` — ghi lại việc đã làm ngày nào, không phải claim về hiện trạng.

Dòng nào không thoả cả ba thì sửa ngay trong bước này.

- [ ] **Step 3: Quét `SafetyGuard`**

```bash
rg -n "SafetyGuard" vong2 android/voice/README.md backend/README.md -g '*.md'
```

Kỳ vọng: không dòng nào mô tả `SafetyGuard` như thứ đang chạy. `18-N1` giữ *"ĐỎ — không có `SafetyGuard` trong repo"*, `24-N5` giữ nhãn **Kế hoạch**, `23-N4` giữ ghi chú tiền đề chưa thoả.

- [ ] **Step 4: Kiểm §6 file 25 đã đủ bảy dấu ✅**

```bash
rg -c -F "| ✅ |" vong2/25-LECH-KIEN-TRUC-VOICE-PIPELINE.md
```

Kỳ vọng: `7`.

- [ ] **Step 5: Ghi §7 vào file 25**

Thêm vào cuối file:

```markdown
## 7. Kết quả quét nhất quán

Chạy ngày 08/08/2026, sau khi S1–S7 đã commit.

| Kiểm tra | Kết quả |
|---|---|
| `git diff --name-only main...HEAD -- '*.kt'` | rỗng — không mã sản phẩm nào bị sửa |
| Mọi lần nhắc `PushToTalkRecorder` / `VadEndpointer` / `AsrClient` trong `.md` | đều kèm nhãn *chưa cắm* hoặc *kiến trúc đích*, hoặc là mục lịch sử |
| Mọi lần nhắc `SafetyGuard` | không chỗ nào mô tả như thứ đang chạy |
| Bảy điểm trích S1–S7 | ✅ đủ |
```

- [ ] **Step 6: Commit**

```bash
git add vong2/25-LECH-KIEN-TRUC-VOICE-PIPELINE.md
git commit -m "docs(vong2): đóng file 25 bằng kết quả quét nhất quán"
```

---

## Lịch ghép vào `07-PLAN-CA-NHAN-LONG.md`

| Ngày | Slot sẵn có | Task | Giờ |
|---|---|---|---|
| **04/08** | standup 21:30 | Task 1 | 0.25h |
| 05/08 | L9b + L10 + tuyên bố freeze | *không đụng* | 0 |
| 06/08 | L11 README (3h) | Task 2 — viết file 25 **trước** rồi mới viết README | 1.5h |
| 07/08 | N1 Claim–Evidence Map (3.5h) | Task 3, 4, 5, 6 | 1h |
| 08/08 | L12 write-up + L15 slide | Task 7, 8, 9, 10 | 1.5h |

Tổng ~4.25h, nằm gọn trong quỹ hiện có. Không xin thêm giờ.
