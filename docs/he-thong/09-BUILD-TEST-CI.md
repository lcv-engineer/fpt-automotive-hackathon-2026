# 09 — Build, test và CI

---

## 1. Yêu cầu môi trường

| Phần | Cần gì |
|---|---|
| App AAOS | **Temurin JDK 21**, **Android SDK 37**, build-tools **37.0.0** |
| `viva-asr` | Python 3.11 (CI dùng bản này), hoặc Docker |
| `backend` | Go (chỉ standard library) |

**Không cần secret nào để build/test local.** Secret chỉ cần khi gọi CarSky/registry.

---

## 2. Build và test app

```powershell
cd automotive
./gradlew :voice-core:testDebugUnitTest :feature:voice:testDebugUnitTest :vehicle-service:api:testDebugUnitTest :vehicle-service:impl:testDebugUnitTest :core:common:testDebugUnitTest
```

```powershell
cd automotive
./gradlew :app:assembleMockDebug :app:assembleRealDebug
```

APK ra ở `automotive/app/build/outputs/apk/<flavor>/debug/`.

Báo cáo test nằm ở build dir của **từng module**, ví dụ
`automotive/feature/voice/build/test-results/`.

### Build cho Device CarSky

```bash
automotive/gradlew :app:clean :app:assembleRealDebug -PvivaAsrBaseUrl=http://10.99.0.3:8080
```

🔴 **Phải có `:app:clean`** — build incremental để lại dữ liệu chết trong archive
(305 MB thay vì 227 MB) và hash không tái lập được.
[Chi tiết](../carsky/07-APK-ARTIFACT-ADB.md).

### Cờ Gradle của module voice

| Cờ | `BuildConfig` | Mặc định |
|---|---|---|
| `-PvivaAsrBaseUrl` | `ASR_BASE_URL` | `http://127.0.0.1:8080` |
| `-PvivaBrainBaseUrl` | `BRAIN_BASE_URL` | rơi về `vivaAsrBaseUrl` → `http://127.0.0.1:8080` |
| `-PvivaBrainAgentEnabled` | `BRAIN_AGENT_ENABLED` | `false` |
| `-PvivaBrainAuthToken` | `BRAIN_AUTH_TOKEN` | `""` |

⚠️ Cờ `-PvivaAsrEngine` **không còn tồn tại** — engine chọn lúc chạy trong Settings.

⚠️ `preBuild` phụ thuộc `downloadVoskEnModel`, `downloadVoskViModel`,
`downloadEmbeddingModel` — build sạch lần đầu sẽ tải model.

---

## 3. Test `viva-asr`

```powershell
cd asr
py -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt
.\.venv\Scripts\python.exe -m pytest -q
```

20 test HTTP chạy với **transcriber giả** → không cần model hay wheel CTranslate2.

```powershell
docker build -t viva-asr:phowhisper-tiny-int8 asr/
```

---

## 4. Test `backend`

```powershell
cd backend
go test ./...
```

```powershell
cd backend
go run ./cmd/viva-tools harness report --input testdata/sample_trace.log --out report.csv
```

---

## 5. Workflow CI

| Workflow | Trigger | Làm gì |
|---|---|---|
| `android-ci` | `push` vào `main` + `pull_request` | Lint (`lintMockDebug`, `lintRealDebug`) → `./gradlew test` → build **cả hai** APK debug → **ghi artifact identity** (SHA-256, commit) → upload APK + manifest + báo cáo test/lint. JDK 21 Temurin, cache model giọng nói offline |
| `asr-ci` | `push` + `pull_request` | `pytest -q` (không cần model) · `compileall` toàn bộ `app scripts` · **build image và chờ `/health` trả 200** |
| `backend-ci` | `push` + `pull_request` | `gofmt` check · `go vet` · `go build` · `go test ./... -v` · chạy **golden benchmark fixture trên Windows PowerShell** · trên `push` vào `main` thì sinh binary chạy được |
| `carsky-push-asr-image` | `workflow_dispatch` | Build đa kiến trúc + push registry + in digest |
| `carsky-deploy-asr` | `workflow_dispatch` | `PATCH` digest + restart node + chờ `Running` + rollback |

🔴 **Hai workflow CarSky cố ý KHÔNG chạy theo `push`** — bốn lý do ở
[`docs/carsky/06 §6`](../carsky/06-REGISTRY-VA-CI.md), nặng nhất là: đổi image bắt
buộc xoá-dựng-lại deployment, mà việc đó **xoá luôn APK và cấu hình mạng** trên VM
Android. Một PR sửa docs mà tự động làm việc đó là thảm hoạ.

### Artifact identity

`android-ci` ghi SHA-256 của APK cùng commit vào manifest và upload kèm. Đây là thứ
cho phép đối chiếu **ba chặng** (máy build = file trên Device = `base.apk` đã cài).
Evidence: `evidence/artifact-identity-ci.txt`.

---

## 6. Chạy flavor `real` trên emulator AAOS

`mock` **không bao giờ đồng bộ với system bar HVAC** — có chủ đích. Muốn app và system
bar cùng đổi thì cài `real` dưới dạng privileged app.

**Cài privileged một lần** (image userdebug / Google APIs, không phải Play Store):

```bash
adb root && adb remount
```

```bash
adb shell mkdir -p /system/priv-app/VivaAutomotive
```

```bash
adb push automotive/app/build/outputs/apk/real/debug/app-real-debug.apk /system/priv-app/VivaAutomotive/VivaAutomotive.apk
```

```bash
adb push automotive/app/privapp-permissions-com.sopa.viva_automotive.xml /system/etc/permissions/
```

```bash
adb reboot
```

**Cập nhật hằng ngày** (giữ quyền privileged, không reboot, tránh NPE):

```bash
adb install -r -d automotive/app/build/outputs/apk/real/debug/app-real-debug.apk
```

```bash
adb shell am start --user 10 -n com.sopa.viva_automotive/.MainActivity
```

🔴 **Đừng `adb push` APK mới đè `/system/priv-app/…` rồi mở app mà không reboot** —
PackageManager giữ mapping cũ và crash lúc khởi tạo process:
`NullPointerException: Resources.getConfiguration()` trong
`ConfigurationController.updateLocaleListFromAppContext`.

Nếu đã dính: `am force-stop` → `adb install -r -d` → `adb reboot`.

**Kiểm app và VHAL có đồng ý nhau không:**

```bash
adb shell cmd car_service get-property-value 358614275 1
```

Thiếu quyền privileged thì app **không im lặng**: write hiện snackbar nêu tên quyền,
và `RealVehicleRepo` ghi log (`adb logcat -s RealVehicleRepo`).

---

## 7. Đo benchmark một lần chạy đầy đủ

```powershell
cd backend
.\scripts\run_benchmark.ps1 -Variant quiet -Log <duong-dan-log-da-bat>
```

```powershell
cd backend
.\scripts\run_benchmark.ps1 -Variant quiet -Adb
```

Sinh kèm `run_manifest.txt` (commit + hash suite + worktree dirty?). Chi tiết:
[07](07-BACKEND-HARNESS.md).

---

## 8. Bí mật — quy tắc cứng

| Không bao giờ commit | Ở đâu thì đúng |
|---|---|
| `backend/.env`, API key CarSky, token registry | `.env` (gitignored), GitHub Secrets |
| `OPENAI_API_KEY` | **Chỉ** biến môi trường phía server `viva-asr` |
| `VIVA_BRAIN_AUTH_TOKEN` | Cấp riêng cho server và cho Android build qua `-PvivaBrainAuthToken` |
| Keystore, APK | Artifact CI hoặc CarSky Artifacts |
| `openapi.json` của CarSky (128 KB) | Không commit — nội bộ nền tảng, thể lệ 3.6 |

⚠️ `VIVA_BRAIN_AUTH_TOKEN` **nằm trong APK** nên không phải bí mật bền vững. Nó bảo
vệ biên triển khai và quota; production vẫn cần HTTPS, rotation, rate limiting.
