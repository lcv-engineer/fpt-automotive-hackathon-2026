# VIVA Digital Cockpit

VIVA là prototype buồng lái số cho Android Automotive OS (AAOS), tập trung vào trợ lý giọng nói offline, điều khiển HVAC/cửa, media và quan sát độ trễ đầu-cuối. Mục tiêu của Vòng 2 là chứng minh luồng do đội sở hữu có ranh giới rõ ràng, đo được và không bỏ qua tầng an toàn/service khi thực thi lệnh xe.

## Trạng thái hiện tại

| Hạng mục | Trạng thái | Bằng chứng / giới hạn |
|---|---|---|
| App AAOS, UI HVAC/vehicle status, mock repository | **Đã tích hợp** | Hai flavor `mock` và `real` build được; mock dùng simulator trong bộ nhớ |
| Voice core: trace, thu âm, VAD, grammar 10 intent, TTS | **Đã tích hợp ở mức code/build** | Unit test và APK build xanh; nghe/thu trong cabin vẫn là Device Integration Gate |
| Audio focus cho TTS | **Đã tích hợp ở mức code/build** | Xin transient focus trước khi nói, trả focus sau success/failure; kiểm chứng ducking với media thật trên Device còn chờ |
| Vosk/MiniLM assets và pipeline hiện hữu của app | **Đã tích hợp** | Chạy offline; đường nối cuối từ pipeline service tới grammar core đang được hoàn thiện theo từng adapter |
| `FakeAsrClient`, dữ liệu TTS/noise synthetic, mock vehicle | **Mô phỏng** | Dùng cho test tái lập, không được xem là bằng chứng cabin/xe thật |
| `DeliverySkill` — 3 intent giao hàng | **Mô phỏng** | Lộ trình in-memory do đội tạo; unit test JVM. ⚠️ Chưa biên dịch trên máy có Android SDK |
| Benchmark harness + bộ 22 câu + runner PASS/FAIL | **Đã tích hợp** *(công cụ)* | `go test ./...` xanh; chạy ra CSV thật trên fixture. Số đo **trên Device** thì chưa có |
| `VivaCarService` → VHAL → gateway → CAN/CCU | **Kế hoạch / tích hợp đội** | Contract đã chốt; quyền privileged và luồng Device phải được chứng minh riêng |
| ASR container `viva-asr` | **Kế hoạch / phụ thuộc đội** | Trục benchmark đã chốt, còn chờ số đo từ harness |

Không claim toàn bộ 10 intent đi tới CAN. Chỉ `hvac_*` và `door_lock` thuộc đường Vehicle Property; media, volume và delivery đi qua adapter riêng. Xem [contract tích hợp](vong2/03-contracts.md).

## Kiến trúc

```text
Microphone / push-to-talk
  → VAD / endpoint
  → ASR (audio → text)
  → normalize + grammar router
  → CommandGateway + SafetyGuard
      ├─ HVAC / door → VivaCarService → PropertyID → VHAL → gateway → CAN/CCU
      ├─ media       → MediaSession
      ├─ volume      → Android car audio adapter
      └─ delivery    → in-app skill
  → Applied / Denied / ConfirmationRequired / Failed
  → HMI + TTS (audio focus)
```

`Intent` dừng ở biên app/service; VHAL chỉ nhận `(propertyId, areaId, value)`. TTS chỉ được nói câu xác nhận dạng “Đã…” sau khi tầng thực thi trả `Applied`.

Các module chính:

```text
automotive/                 app AAOS, feature modules, vehicle-service API/impl
android/voice/              voice-core JVM + Android adapters
asr/                        service ASR tiếng Việt `viva-asr` (Python) + Dockerfile
backend/                    Go benchmark harness, bộ câu benchmark và CarSky devops helper
vong2/03-contracts.md       interface và mapping intent → PropertyID → VSS → CAN
vong2/13-M7A-*.md           tình huống phức tạp và hành vi mong đợi
vong2/14-KICH-BAN-*.md      kịch bản demo 3 phút và đường thoát lỗi
vong2/15-QUYET-DINH-*.md    quyết định trục benchmark ASR
vong2/18-N3-*.md            baseline manifest: nền tảng cấp gì, đội xây gì
vong2/19-N4-*.md            quy trình ablation A1/A2/A3
vong2/20-N5-*.md            bảng ba trạng thái integration + dữ liệu synthetic
```

## Build và kiểm thử

Yêu cầu: Temurin JDK 21, Android SDK 37 và Android build-tools 37.0.0. Không cần secret để build/test local.

```powershell
cd automotive
./gradlew :voice-core:testDebugUnitTest `
  :feature:voice:testDebugUnitTest `
  :vehicle-service:api:testDebugUnitTest `
  :vehicle-service:impl:testDebugUnitTest `
  :core:common:testDebugUnitTest

./gradlew :app:assembleMockDebug :app:assembleRealDebug
```

APK debug được sinh dưới `automotive/app/build/outputs/apk/<flavor>/debug/`. Flavor `real` cần app privileged/platform-signed và permission allowlist của OEM để ghi các Vehicle Property được bảo vệ; chi tiết cài đặt nằm trong [README Android](automotive/README.md).

Backend harness không có dependency ngoài Go standard library:

```powershell
cd backend
go test ./...
go run ./cmd/viva-tools harness report --input testdata/sample_trace.log --out report.csv
```

Nếu dùng CarSky helper, sao chép `backend/.env.example` thành `backend/.env` và tự điền credential. `.env`, token, API key, keystore và APK không được commit.

Đo một lần chạy benchmark đầy đủ (báo cáo latency + PASS/FAIL từng câu + manifest identity):

```powershell
cd backend
.\scripts\run_benchmark.ps1 -Variant quiet -Log <đường dẫn log đã bắt>
# hoặc bắt trực tiếp từ Device sau khi mở adb tunnel:
.\scripts\run_benchmark.ps1 -Variant quiet -Adb
```

Service ASR chạy độc lập, không cần Android SDK:

```powershell
cd asr
py -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt
.\.venv\Scripts\python.exe -m pytest -q          # 20 test HTTP, không cần model
docker build -t viva-asr:phowhisper-tiny-int8 .  # build image kèm model đã convert
```

## Triển khai lên CarSky

> ⚠️ Quy trình dưới đây viết theo endpoint đã xác nhận trong `docs/Car-Sky-Platform.html`
> và runbook nội bộ; **chưa chạy đủ đầu-cuối tại thời điểm viết** vì cần credential và
> Room thật. Ai chạy được trước thì sửa lại mục này theo đúng thứ tự lệnh thực tế.

1. **Backup rồi mới clone blueprint** — không sửa blueprint gốc:
   ```powershell
   cd backend
   go run ./cmd/viva-tools carsky blueprint clone --id <blueprintId> `
     --backup-out backup.json --clone-out clone.json
   ```
   Lệnh này luôn export backup trước và từ chối clone nếu backup lỗi.
2. **Tra node và pin** của Room để biết CCU/CAN/VHAL nằm ở đâu:
   `go run ./cmd/viva-tools carsky nodes --room <roomId> --out nodes.json`
3. **Push image `viva-asr`** lên Zot registry của nền tảng, rồi thêm Container Node vào
   blueprint clone; app đọc địa chỉ qua `BuildConfig.ASR_BASE_URL` (`03-contracts.md` §2),
   không hard-code.
4. **Mở adb tunnel** rồi cài APK:
   `go run ./cmd/viva-tools carsky adb-tunnel --room <roomId>` → `adb connect <host:port>` → `adb install`.
5. **Bắt log và đo**: `.\scripts\run_benchmark.ps1 -Variant <mức nhiễu> -Adb`.

## Thêm intent mà không sửa grammar core

`GrammarIntentRouter` nhận các `GrammarRule` bổ sung ở composition root. Rule chỉ phân tích câu đã lowercase, chuẩn hóa dấu câu và bỏ wake phrase; nó đề xuất `RouteResult`, không được tự thực thi lệnh.

```kotlin
val trunkRule = GrammarRule { command ->
    if (command == "mở cốp") {
        RouteResult.Matched(
            Intent(
                name = "trunk_open",
                slots = emptyMap(),
                confidence = 1.0f,
                tier = Intent.Tier.T0,
            ),
        )
    } else {
        null
    }
}

val router = GrammarIntentRouter(extensionRules = listOf(trunkRule))
```

Sau khi đăng ký rule, cần bổ sung mapper/action ở module sở hữu domain, đưa action qua `CommandGateway` và `SafetyGuard`, rồi thêm test cho parse, slot, deny/confirm và kết quả thực thi. Extension chạy sau toàn bộ core rule và safety pre-filter, nên không thể ghi đè intent core hoặc khôi phục biến thể đã chủ động loại bỏ.

## Tài liệu Vòng 2

- [Product & Integration Card](vong2/12-PRODUCT-INTEGRATION-CARD.md)
- [5 tình huống phức tạp M7a](vong2/13-M7A-TINH-HUONG-PHUC-TAP.md)
- [Kịch bản demo 3 phút L8](vong2/14-KICH-BAN-DEMO-3-PHUT.md)
- [Quyết định benchmark ASR L10](vong2/15-QUYET-DINH-BENCHMARK-ASR.md)
- [Plan cá nhân và các Device Integration Gate](vong2/07-PLAN-CA-NHAN-LONG.md)
- [Baseline Manifest N3 — nền tảng cấp gì, đội xây gì](vong2/18-N3-BASELINE-MANIFEST.md)
- [Ablation N4 — quy trình A1/A2/A3](vong2/19-N4-ABLATION.md)
- [Bảng ba trạng thái integration N5](vong2/20-N5-TRANG-THAI-INTEGRATION.md)

## Mã nguồn mở và tài sản mô hình

- Vosk Android `0.3.75` — Apache-2.0; model EN/VI theo license của từng model upstream.
- ONNX Runtime Android `1.20.0` — MIT.
- Silero VAD `v6.2.1` — MIT; bản license được giữ tại `android/voice/third_party/silero-vad-LICENSE`.
- AndroidX, Kotlin, Coroutines, Hilt và Room — xem version catalog tại `automotive/gradle/libs.versions.toml` và license upstream tương ứng.
- Các WAV fallback tiếng Việt được tạo offline cho demo; không chứa credential hay dữ liệu người dùng.
- `viva-asr` dùng **faster-whisper / CTranslate2** làm engine và **PhoWhisper** (VinAI) làm model
  mặc định, phục vụ qua **FastAPI + uvicorn + pydantic**. Phiên bản pin trong `asr/requirements.txt`.
  ⚠️ **License của từng gói và của model PhoWhisper phải được đối chiếu lại tại repo upstream
  trước khi nộp** — mục này ghi tên và nguồn, chưa phải kết luận pháp lý.
- `backend/` (Go) **không có dependency ngoài standard library** — cố ý, để không phải vendor gì cả.

Trước khi phát hành, đội phải đối chiếu lại license của từng model được đóng gói và hoàn tất Device Integration Gate; kết quả unit test/synthetic không thay thế bằng chứng chạy trên AAOS Device.
