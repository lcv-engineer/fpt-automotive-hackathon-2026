# `:voice-core` — voice pipeline module (Long)

Android **library** module. VAD · push-to-talk · ASR client · intent router · TTS ·
latency trace. Không phải app — app shell là D1 của Dương, module này cắm vào đó.

## Vì sao là library riêng, không viết thẳng vào app

`06-PHAN-CONG-4-NGUOI.md` PHẦN 5: *"App shell — nếu trễ, mỗi người chạy module bằng unit
test, ghép sau."* Tách library là cách biến câu đó thành sự thật thay vì lời hứa: toàn bộ
logic lõi ở đây **không import `android.*`**, nên unit test chạy trên JVM, không
cần Device, không cần emulator, không cần Robolectric.

Các adapter dưới đây được phép chạm framework/runtime; logic còn lại vẫn JVM thuần:

| File | Chạm gì |
|---|---|
| `trace/AndroidTrace.kt` | `SystemClock.elapsedRealtimeNanos`, `Log.i` |
| `audio/AndroidPcmSource.kt` | `AudioRecord` |
| `audio/SileroVadOnnxScorer.kt` | Android assets + ONNX Runtime |
| `tts/AndroidTtsSpeaker.kt` | `TextToSpeech` + `MediaPlayer` |
| `tts/AndroidAudioFocusController.kt` | `AudioManager` + `AudioFocusRequest` |

Thêm framework vào bất kỳ file nào khác là **làm hỏng tính chất này** — test sẽ cần emulator,
và test cần emulator là test không ai chạy.

## Ghép vào app shell (Dương)

Project thật ở `automotive/settings.gradle.kts` đã có:

```kotlin
include(":voice-core")
project(":voice-core").projectDir = file("../android/voice")
```

`automotive/feature/voice/build.gradle.kts` đã có:

```kotlin
dependencies { implementation(project(":voice-core")) }
```

Rồi:

```bash
cd automotive
./gradlew :voice-core:testDebugUnitTest :feature:voice:testDebugUnitTest
```

> Project Gradle và wrapper đã có sau khi merge app shell của Dương. Build local ngày 01/08 dùng
> Temurin JDK 21 (`automotive/gradle/gradle-daemon-jvm.properties`) và Android SDK 37.

## Đang có gì

| Package | Task | Trạng thái | Nằm trên đường chạy APK? |
|---|---|---|---|
| `trace/` | **L2** `LatencyTrace` + log format `VIVA_TRACE\|` | ✅ code + test + log mẫu | ✅ có |
| `audio/` | **L3a** push-to-talk `AudioRecord` + WAV | ✅ code + test | ❌ **chưa** — app dùng `VoskSpeechRecognitionEngine` |
| `audio/` | **L3b/L3c** Silero VAD ONNX + endpointer | ✅ code/model/unit test + synthetic baseline; cabin/Device là integration gate riêng | ❌ **chưa** — app không có VAD riêng (`VoiceAssistantService.kt:93`) |
| `asr/` | **L4** `AsrClient` + `FakeAsrClient` | 🟡 contract + fake; endpoint thật chưa cắm | ❌ **chưa** |
| `intent/` | **L5a/L5b** grammar T0 — đủ 10 intent lõi | ✅ code + test router; 5 biến thể đã cắt được từ chối rõ ràng | ✅ có — `ProcessVoiceCommandUseCase.kt:19` |
| `agent/` | Voice ↔ app/service boundary | 🟡 code + test viết; chờ Dương cắm adapter | ❌ **chưa** |
| `tts/` | **L6** Android vi-VN TTS + 36 pre-rendered clips + final cue | ✅ code/assets/unit test + APK build; nghe Device là integration gate riêng | ✅ có |
| `tts/` | **L7** transient audio focus cho TTS | 🟡 code/unit test/APK build xanh; ducking với media thật còn chờ Device | ✅ có |

> **Cột thứ tư quan trọng hơn cột thứ ba.** Dấu ✅ ở *Trạng thái* nghĩa là mã/test đã có,
> không có nghĩa thành phần đang chạy trong APK. `audio/`, `asr/` và `agent/` hiện là module
> độc lập; đường chạy thật đi qua `VoskSpeechRecognitionEngine`. Chi tiết ở
> `vong2/25-LECH-KIEN-TRUC-VOICE-PIPELINE.md`.

### Build evidence — kiểm lại 02/08/2026

- JDK 21 + Android SDK 37: full Gradle `test` ngày 02/08 chạy **139 test**
  *(65 `voice-core` + 74 ở các module automotive)*, **0 failure/error/skipped**.
- `:app:assembleMockDebug :app:assembleRealDebug`: `BUILD SUCCESSFUL in 6m 51s`, 303 actionable tasks.
- APK sinh tại `app/build/outputs/apk/mock/debug/` và `app/build/outputs/apk/real/debug/`.
- Evidence này xác nhận code/build; không thay thế kiểm thử nghe TTS, VAD cabin hoặc quyền VHAL trên Device.

## `trace/` — dùng thế nào

```kotlin
// VAD (hoặc nút push-to-talk) mở lượt:
val trace = startVoiceTrace(nanos = utterance.startNanos)
trace.markAt(Stage.SPEECH_END, utterance.endNanos)

// Mỗi module đánh mốc của mình — chỉ 1 dòng:
trace.mark(Stage.GUARD_DONE)     // Tùng, trong SafetyGuard
trace.mark(Stage.EXEC_DONE)      // bất kỳ Skill nào

// Đóng lượt, đúng 1 lần:
trace.summary("hạ điều hòa xuống 22 độ", "hvac_set_temp", TraceVerdict.Allow)
trace.summary("mở cửa", "door_lock", TraceVerdict.Deny("G1_SPEED_LOCK"))
trace.summary("", "unknown", TraceVerdict.Error(Stage.ASR_DONE))   // lượt chết giữa chừng
```

Format dây và lý do từng luật: `vong2/03-contracts.md` §1. Log mẫu bàn giao cho harness:
[`fixtures/`](fixtures/README.md).

## `audio/` — dùng thế nào

```kotlin
val recorder = PushToTalkRecorder(AndroidPcmSource(), SystemNanoClock)
val utterance = recorder.record(isHeld = { talkButton.isPressed })   // chạy ở background thread
if (!utterance.isUsable) return                                      // chạm nhầm, đừng gọi ASR
val wav = WavWriter.toWav(utterance.pcm, utterance.sampleRate)       // để nghe lại khi debug
```

`record()` **block** — gọi từ main thread là ANR.

### Silero VAD và baseline L3c

`SileroVadOnnxScorer` nạp model v6.2.1 từ assets, giữ recurrent state + 64 mẫu context và chấm
từng frame cố định 512 mẫu/16 kHz. `VadEndpointer` dùng hysteresis `0.50/0.35`, tối thiểu 250 ms
speech, 100 ms silence và pad 30 ms; toàn bộ số này nằm trong `VadConfig` để đổi bằng evidence,
không hard-code rải rác.

Model: `silero_vad_v6_2_1.onnx` · SHA-256
`1a153a22f4509e292a94e67d6f9b85e8deb25b4988682b7e174c65279d8788e3`.

Baseline mô phỏng tái lập:

```bash
python scripts/evaluate_vad_thresholds.py > fixtures/vad-threshold-baseline.csv
```

Trên 36 câu TTS Việt, threshold `0.50` trigger 36/36 từ clean đến white-noise 0 dB, median coverage
99.0% ở 0 dB và 0/36 trigger trên noise-only. Đây chỉ là **MÔ PHỎNG**: task L3c đã hoàn tất ở mức
chọn baseline tái lập; audio cabin/Device vẫn là integration gate trước khi claim hiệu năng thực tế.
Giữ `0.50` làm baseline vì đó cũng là mặc định upstream, không hạ xuống `0.35` chỉ để tối ưu synthetic set.

Nguồn upstream chính chủ:

- Silero VAD v6.2.1 (MIT): https://github.com/snakers4/silero-vad/releases/tag/v6.2.1
- Wrapper/endpoint parameters: https://github.com/snakers4/silero-vad/blob/master/src/silero_vad/utils_vad.py
- ONNX Runtime Java: https://onnxruntime.ai/docs/get-started/with-java.html
- ONNX Runtime Android/R8: https://onnxruntime.ai/docs/build/android.html

## Flow MVP đúng sau kick-off 30/07

```text
microphone front-end
  ├─ push-to-talk trigger
  └─ always-on wake-word detector ("Viva ơi"; aliases "Vivi ơi" / "Vi-Vi ơi")
  → command capture → VAD/endpointer → ASR (audio → text) → normalize
  → grammar T0 (LLM chỉ là fallback đề xuất intent)
  → CommandGateway
      ├─ media/volume → code của Dương
      └─ hvac/door   → VivaCarService → PropertyID → VHAL
  → Applied / Denied / ConfirmationRequired / Failed
  → HMI + TTS
```

Ba luật tích hợp:

1. ASR chính là STT; “lọc tiếng ồn” thuộc audio front-end/VAD, không phải một ASR thứ hai.
2. LLM không trả `passed/fail` và không được gọi thẳng Skill. Nó chỉ đề xuất `Intent` rồi vẫn đi qua
   safety/service gateway.
3. Chỉ `CommandResult.Applied` — tức tầng dưới đã xác minh trạng thái mới — mới được nói câu “Đã…”.
   Request vừa được nhận chưa phải là thành công.

`"lạnh quá"` không tự đổi nhiệt độ: câu này có nghĩa cần **ấm hơn**, nhưng thiếu mức đích. Router hỏi
`"Bạn muốn tăng nhiệt độ điều hòa lên bao nhiêu độ?"` để không làm ngược ý người dùng.

### Điểm cắm thật với app của Dương

Project `automotive/` đã include module này dưới tên `:voice-core`; `:feature:voice` phụ thuộc trực
tiếp vào nó. Dương không cần phụ thuộc vào microphone/ASR để bắt đầu, có thể cắm text giả trước:

```kotlin
val result = voiceAgent.handleText("Viva ơi, chuyển bài", trace)

when (result.status) {
    VoiceTurnStatus.APPLIED -> renderPatch(result.hmiPatch)
    else -> showVoiceMessage(result.spokenVi)
}
// Gọi sau frame đầu tiên thực sự phản ánh trạng thái mới:
trace.mark(Stage.RENDER_DONE)
```

Ranh giới kiểu dữ liệu đã có sẵn ở
`automotive/feature/voice/.../integration/CoreIntentMapper.kt`:

```kotlin
when (val action = CoreIntentMapper.map(coreIntent)) {
    is AutomotiveVoiceAction.VehicleControl -> executeVehicleControl(action.intent)
    is AutomotiveVoiceAction.VolumeAdjust -> adjustVolume(action.delta)
    AutomotiveVoiceAction.MediaNext -> skipToNext()
    null -> showVoiceMessage("Lệnh chưa có adapter hoặc thiếu slot")
}
```

Mapper này là chỗ duy nhất biết cả `com.viva.voice.intent.Intent` và `VehicleIntent` của app. Phần kế
tiếp chỉ cần nối ba nhánh action vào `ExecuteVehicleControlUseCase`, MediaSession và CarAudioManager;
`VoiceAgent` vẫn JVM thuần và không import Activity/ViewModel.

## `tts/` — Android voice trước, file đóng gói sau

`AndroidTtsSpeaker` khởi tạo engine bất đồng bộ, kiểm tra `vi-VN`, gọi API `speak(CharSequence, …,
utteranceId)` và chỉ đánh dấu `tts_start` trong `UtteranceProgressListener.onStart`. Nếu Device không
có dữ liệu tiếng Việt hoặc engine báo lỗi, speaker tra `PrerenderedPrompts` và phát clip WAV tương ứng
trong `res/raw/`. Text động chưa có clip dùng `viva_notification_ping.wav` làm cue cuối, còn HMI giữ
nguyên text và trạng thái thực thi. Owner của Service phải gọi `close()` để `shutdown()` engine và
giải phóng player.

Mỗi lượt phát xin `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` với `USAGE_ASSISTANT` và
`CONTENT_TYPE_SPEECH`; cùng `AudioAttributes` được dùng cho focus request, TTS và WAV fallback.
Focus được trả trong cả nhánh thành công lẫn lỗi. Khi nhận focus loss, speaker dừng playback và lượt nói
không được báo thành công. Đây là bằng chứng code/unit test; việc nhạc trên AAOS duck đúng và không chồng
tiếng vẫn phải kiểm chứng trên Device.

Nguồn API chính thức:

- `TextToSpeech`: https://developer.android.com/reference/android/speech/tts/TextToSpeech
- `AudioFocusRequest`: https://developer.android.com/reference/android/media/AudioFocusRequest
- AAOS audio focus: https://source.android.com/docs/automotive/audio/audio-focus
- `UtteranceProgressListener`: https://developer.android.com/reference/android/speech/tts/UtteranceProgressListener
- `MediaPlayer.create(Context, R.raw.*)` và yêu cầu `release()`:
  https://developer.android.com/reference/android/media/MediaPlayer
- Keep file cho resource được tra bằng `getIdentifier()`:
  https://developer.android.com/topic/performance/app-optimization/customize-which-resources-to-keep

Tạo lại 36 clip fallback bằng giọng Windows `Microsoft An - Vietnamese (Vietnam)`:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\generate_tts_assets.ps1
```

Catalog cố ý dùng câu **“Đã đặt nhiệt độ mục tiêu 24°C”**: đó là setpoint đã được service xác nhận,
không phải claim cabin đã đạt 24°C. Fan chỉ có mức `0..5`.

## Thư viện ngoài

- L6 chỉ dùng API trong Android SDK; các WAV được tạo offline và đóng gói, không thêm runtime library.
- L3b dùng `com.microsoft.onnxruntime:onnxruntime-android:1.20.0` và model Silero VAD v6.2.1.
  License Silero được giữ tại `third_party/silero-vad-LICENSE`; cả hai dự án dùng MIT.

Checklist nộp bài 10/08 bắt buộc ghi lại hai nguồn L3b ở README gốc khi lắp ghép tài liệu cuối.
