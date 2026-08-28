# 03 — VIVA Voice: nghe và nói

> Code: `android/voice/src/main/kotlin/com/viva/voice/` (JVM thuần + adapter Android)
> và `automotive/feature/voice/…/data/`.

---

## 1. Chuỗi một lượt nói

```text
Wake word "Viva oi" | Push-to-talk
   -> HotwordGate / HotwordController          (via/*)
   -> VoiceAssistantService                    (foreground service mic)
   -> VadUtteranceCapture
        -> PcmSourceAudioCapture  (AudioRecord, 16 kHz mono PCM16)
        -> VadStreamDriver -> SileroVadOnnxScorer (ONNX, mot session lazy dung lai)
        -> speech_start / speech_end
   -> RoutingAsrClient   [VIVA | GOOGLE | VOSK]
   -> VoiceAgent (Brain)
   ...
   -> AndroidTtsSpeaker + AndroidAudioFocusController
```

`VoiceAssistantStateManager` giữ vòng đời hiển thị:
`IDLE → LISTENING → PROCESSING → EXECUTING → SUCCESS/ERROR`.

---

## 2. Wake word và push-to-talk

| Thành phần | File |
|---|---|
| Từ khoá | `HotwordConstants.KEYPHRASE = "Viva ơi"`, `LOCALE_TAG = "vi-VN"` |
| Cổng lọc | `HotwordGate`, `SoftwareHotwordDetector`, `HotwordMetrics` |
| Tích hợp hệ điều hành | `via/VivaVoiceInteractionService`, `VivaRecognitionService`, `VivaVoiceInteractionSession*` |
| DSP / mô hình keyphrase | `via/DspHotwordDetector`, `KeyphraseSoundModelSupport`, `HotwordTemplateStore` |
| Push-to-talk | `voice/audio/PushToTalkRecorder.kt` |

Trên Device CarSky, màn hình app hiện **"Hotword armed — say Vi-Vi ơi"** khi đã sẵn sàng.

---

## 3. Thu âm và cắt câu (VAD)

- Nguồn PCM: **một** nguồn duy nhất (`PcmSource` → `AndroidPcmSource`), 16 kHz mono PCM16.
- VAD: **Silero VAD v6.2.1** chạy qua **ONNX Runtime Android 1.20.0**.
- `SileroVadOnnxScorer` giữ **một session lazy dùng lại giữa các lượt** — không nạp
  lại model mỗi lần nói.
- `VadSegmenter` / `VadStreamDriver` sinh hai mốc trace `speech_start` và `speech_end`.

`speech_end` là mốc quan trọng nhất của cả hệ: **`e2e_ms` được định nghĩa là
`speech_end → tts_start`** — xem [07 §2](07-BACKEND-HARNESS.md).

---

## 4. ASR — ba engine, chọn lúc chạy

`RoutingAsrClient` đọc `SettingsDataStore.asrEngine` mỗi lượt:

| `AsrEngine` | Client | Đặc điểm |
|---|---|---|
| `VIVA` (**mặc định**) | `HttpAsrClient` | Gọi container `viva-asr` qua HTTP. Đường dùng cho demo trên CarSky |
| `GOOGLE` | `GoogleCloudSpeechAsrClient` | Cần internet + service-account json |
| `VOSK` | `VoskAsrClient` | Hoàn toàn on-device, chạy được khi **không có mạng** (khôi phục 20/08) |

⚠️ **Không còn cờ build `-PvivaAsrEngine`.** Các runbook cũ ghi cờ này; nó đã bị gỡ.
Engine chọn trong **Settings của app**, và **gỡ cài đặt app sẽ xoá DataStore** → phải
chọn lại.

Contract HTTP với `viva-asr` (`vong2/03-contracts.md` §2):

```
POST /asr
Content-Type: application/octet-stream
X-Sample-Rate: 16000
X-Trace-Id: <traceId>
Body: raw PCM 16-bit LE mono

200 OK  { "text": "...", "confidence": 0.94, "server_ms": 210 }
```

```kotlin
interface AsrClient {
    suspend fun transcribe(pcm16: ShortArray, sampleRate: Int, trace: LatencyTrace): AsrResult
}

data class AsrResult(
    val text: String,
    val confidence: Float,   // 0.0 .. 1.0
    val serverMs: Int,       // tach thoi gian server khoi thoi gian mang
    val isPartial: Boolean = false,
)
```

`serverMs` tồn tại để tách được **thời gian mạng** = `asr_sent → asr_done` trừ đi
`server_ms`. Giữ hai số này tách nhau là toàn bộ lý do của trường đó.

### Địa chỉ service

| Môi trường | `ASR_BASE_URL` |
|---|---|
| Room CarSky | `http://10.99.0.3:8080` (`-PvivaAsrBaseUrl`) |
| Máy dev | `http://127.0.0.1:8080` + `adb reverse tcp:8080 tcp:8080` |
| Emulator | `http://10.0.2.2:8080` |

⚠️ Đổi địa chỉ là phải sửa **cả hai cổng cleartext** —
[`docs/carsky/04 §4`](../carsky/04-MANG-TRONG-ROOM.md).

---

## 5. TTS và audio focus

| Thành phần | File |
|---|---|
| Phát tiếng | `voice/tts/AndroidTtsSpeaker.kt` |
| Focus | `voice/tts/AndroidAudioFocusController.kt`, `AudioFocus.kt` |
| Câu dựng sẵn | `voice/tts/PrerenderedPrompts.kt` |
| Ducking media khi nói | `feature/voice/data/audio/VoiceSessionDucker.kt` |

Hành vi: xin **transient focus trước khi nói**, trả focus sau `success`/`failure`.

🟡 **Giới hạn đã đo:** trên Device CarSky **không có giọng TTS tiếng Việt** — lệnh
chạy đúng nhưng câu trả lời không phát ra tiếng. `PrerenderedPrompts` (WAV dựng sẵn
offline) là đường vá cho các câu hay dùng. **Không được claim phản hồi TTS hoàn
chỉnh trên Device.**

⚠️ Câu tiếng Việt cho tài xế **không bao giờ là mã lỗi**:
`VoiceTurnReport.speechErrorSpeech(code)` dịch mã máy sang câu người nghe. Bản trước
từng đọc thẳng *"Microphone is unavailable"* lên màn hình xe tiếng Việt.

---

## 6. ⚠️ Cổng `MIN_ACOUSTIC_CONFIDENCE` — gắn với MODEL, không phải với hệ thống

Đây là bẫy chẩn đoán đắt nhất phía app: ASR phiên âm **chính xác tuyệt đối** mà hệ
thống vẫn từ chối.

```
transcript = "phat nhac"           <- dung 100%
ket qua    = unknown | Confirm:G3_LOW_CONFIDENCE
spoken     = "Minh chua nghe ro. Ban noi lai giup minh nhe."
```

**Nguyên nhân:** `confidence` từ ASR là `exp(avg_logprob)` — *xác suất token trung
bình của chính model đó*. Model càng được huấn luyện kém cho ngôn ngữ đang nói thì
số càng thấp, **kể cả khi phiên âm ra đúng**. Ngưỡng `0.6` được chọn từ corpus giọng
thật chạy trên **PhoWhisper**; nó đúng **cho model đó**.

Chạy thử với `Systran/faster-whisper-tiny`: **8/8 lượt bị chặn**, gồm cả lượt phiên
âm hoàn hảo.

### Đã có: override lúc chạy (không phải build lại)

```sh
adb shell settings put global viva_min_conf 40    # = 0.40
adb shell settings delete global viva_min_conf    # ve mac dinh 0.6
```

Đơn vị là **phần trăm nguyên 0..100** — `Settings.Global` chỉ có getter `Int`/`String`,
và parse float tay thì sai locale là hỏng (`"0,4"` vs `"0.4"`). Giá trị ngoài dải
(gồm `-1` = chưa đặt) rơi về mặc định: **giá trị rác không được nới lỏng cổng an toàn
một cách âm thầm**.

Cùng khuôn mẫu với công tắc `viva_asr_grammar` — chính công tắc đó cho phép đo A/B
trên **cùng một giọng nói** và ra **WER 0,841 → 0,566**.

### Còn treo

| Hướng | Trạng thái |
|---|---|
| ① Chạy NLU trước, chỉ áp ngưỡng âm học khi NLU **không** khớp chắc chắn | ❌ chưa làm — đây là cách sửa đúng gốc |
| ② Ngưỡng tra theo model (`/health` và header `X-Asr-Model` **đã trả tên model**) | ❌ hạ tầng có sẵn, chưa ai dùng |
| ③ Chuẩn hoá `confidence` phía server | ❌ đắt hơn, phải hiệu chuẩn từng model |
| ④ Override lúc chạy | ✅ **đã có** |

**Điều tốt cần giữ:** trong 8 lượt đầu vào tệ nhất — gồm ảo giác lặp token kiểu
*"Quý, quý, quý"* — **không một lệnh sai nào lọt xuống xe**. Cổng này đang làm đúng
việc của nó; vấn đề là nó quá chặt với model yếu và quyết định **mà không hỏi NLU**.
Đừng gỡ nó — hãy cho nó thêm thông tin.

**Lỗ hổng chẩn đoán nên vá:** trace chỉ ghi `Confirm:G3_LOW_CONFIDENCE`, **không ghi
con số** → không phân biệt được `0.59` (suýt qua) với `0.12` (rác thật).

---

## 7. Cụm từ đã chứng minh chạy được trên Device (10/08)

Dùng đúng những cụm này khi demo — biến thể khác là rủi ro chưa đo.

```
phat nhac len · phat nhac di          -> media_play
chuyen bai tiep theo · bai tiep theo  -> media_next
tang|giam nhiet do ... <so> do        -> hvac_set_temp
tang|giam quat ... muc <so>           -> hvac_set_fan
cho toi biet toc do hien tai          -> vehicle_status_speed
cho toi biet nhien lieu hien tai      -> vehicle_status_fuel
```

**Ba cách nói luôn trượt**, đã đo nhiều lần:

| Câu | Vì sao |
|---|---|
| `phát nhạc` trơn | quá ngắn |
| `chuyển bài` trơn | ASR nghe thành `chuyến bay` |
| `tốc độ …` mở đầu câu | thành `tóc độ` — cụm `cho tôi biết` phía trước là thứ cứu nó |

> **Quy luật chung: âm tiết đầu câu hay bị nuốt, và câu càng dài càng đúng.**

---

## 8. Ba tầng có thể làm mic "không hoạt động"

| Tầng | Kiểm bằng |
|---|---|
| Nền tảng có gửi tiếng vào VM không | widget IVI Screen: đã bấm `Enable microphone`? `Audio Part` đã chọn? |
| Android có thấy thiết bị thu không | `dumpsys media.audio_flinger \| grep -iA3 input` |
| App có mở được mic không | `logcat -c` → bấm mic → `logcat -d \| grep -iE "viva\|audiorecord\|permission denial\|vad"` |

Chi tiết: [`docs/carsky/07 §6`](../carsky/07-APK-ARTIFACT-ADB.md).

---

## 9. Tài sản mô hình và license

| Thành phần | Phiên bản / license |
|---|---|
| Silero VAD | `v6.2.1` — MIT (`android/voice/third_party/silero-vad-LICENSE`) |
| ONNX Runtime Android | `1.20.0` — MIT |
| Vosk Android | `0.3.75` (`com.alphacephei:vosk-android`) + model `model-vi` (51 MB) / `model-en-us` (68 MB) |
| PhoWhisper (VinAI) qua faster-whisper/CTranslate2 | xem [06](06-VIVA-ASR-SERVICE.md) |
| WAV fallback tiếng Việt | tạo offline cho demo; không chứa credential |

⚠️ **License của từng gói và của model PhoWhisper phải được đối chiếu lại tại repo
upstream trước khi phát hành** — mục này ghi tên và nguồn, chưa phải kết luận pháp lý.

⚠️ Model Vosk được tải bằng task Gradle (`downloadVoskEnModel` / `downloadVoskViModel`,
`preBuild dependsOn`), nên **thay đổi ở `VoiceLanguage.voskAssetDir` là thay đổi có
ảnh hưởng biên dịch** — copy file thôi không đủ, phải build mới biết còn thiếu gì.
