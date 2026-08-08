# CarSky AAOS device gate — build → identify → install → prove

Mục tiêu của runbook này là biến một APK đã build thành evidence có thể dùng để
claim trên CarSky. `Build successful`, container `Running` và APK chạy trên AAOS
là ba gate khác nhau; không dùng bằng chứng của gate này để thay cho gate khác.

Runbook này **không deploy/redeploy blueprint**. Deploy dùng quota, có thể làm
gián đoạn room demo và chỉ được thực hiện sau khi cả đội xác nhận đúng blueprint,
device và credential.

## 0. Trạng thái đã biết ngày 04/08/2026

- `viva-asr:0.1.0` đã được CarSky pull theo digest; room thử nghiệm có 22/22 node
  `Running`. Đây là evidence image/platform, chưa phải evidence app gọi ASR.
- APK hiện tại nhận dạng giọng nói **on-device bằng Vosk**. Source chưa có
  `BuildConfig.ASR_BASE_URL` hoặc client gọi container `viva-asr`; nội dung đó
  trong contract/README cũ là hướng thiết kế, không phải integration đã tồn tại.
- Device `VIVA` đã được đọc fingerprint qua web ADB shell, nhưng E01 còn
  `PARTIAL` vì chưa có local `adb devices -l` qua `nydus-reach`.
- REST `screenshot`, `shell`, `adb-exec` và `container-exec` đang trả
  `Conduit service not configured`. Đường khả dụng là ADB tunnel.

Nguồn hiện trạng:

- `evidence/carsky/v7-manifest.txt`
- `evidence/carsky/v7-asr-node-phases.json`
- `evidence/c2/device-info.txt`
- `docs/backend-docs/carsky-api.md`

## 1. Stop rules

Dừng lượt chạy, không “thử tiếp cho may”, khi gặp một trong các điều sau:

- chưa chốt đúng Room, namespace và node AAOS;
- APK không có commit/flavor/SHA-256 đi kèm;
- fingerprint hiện tại khác E01 mà chưa giải thích được;
- `real` flavor thiếu privileged permission;
- app crash/ANR hoặc package/activity không khớp;
- team đang dùng room demo nhưng thao tác yêu cầu deploy, delete hoặc remount.

Không ghi token, API key, keystore, ADB credential hoặc nội dung `backend/.env`
vào log/evidence.

## 2. Gate A — CI và artifact identity

Ưu tiên artifact từ workflow `android-ci`, không lấy APK được gửi qua chat hoặc
từ một thư mục build không rõ commit. Workflow chỉ upload APK sau khi:

1. `lintMockDebug` và `lintRealDebug` pass;
2. toàn bộ JVM unit test pass;
3. `assembleMockDebug` và `assembleRealDebug` pass;
4. `artifact-manifest.txt` đã ghi commit, run ID, kích thước và SHA-256.

Sau khi tải artifact, kiểm lại hash trên máy cài:

```powershell
Get-Content .\artifact-manifest.txt
Get-FileHash .\app-mock-debug.apk -Algorithm SHA256
Get-FileHash .\app-real-debug.apk -Algorithm SHA256
```

Pass khi hash trùng manifest. Chép manifest vào evidence của đúng lượt chạy;
không đổi APK sau bước này.

Nếu buộc phải build local:

```powershell
Set-Location automotive
.\gradlew.bat lintMockDebug lintRealDebug test `
  :app:assembleMockDebug :app:assembleRealDebug `
  --no-daemon --no-configuration-cache --console=plain
Get-FileHash .\app\build\outputs\apk\mock\debug\app-mock-debug.apk -Algorithm SHA256
Get-FileHash .\app\build\outputs\apk\real\debug\app-real-debug.apk -Algorithm SHA256
```

Ghi rõ đây là local artifact; không gọi nó là CI artifact.

## 3. Gate B — pin đúng Room và mở ADB tunnel

Không hard-code Room cũ. Đọc lại node list và tunnel instruction ngay trước khi
cài APK:

```powershell
Set-Location backend
go run ./cmd/viva-tools carsky nodes --room <roomId> --out carsky\nodes-live.json
go run ./cmd/viva-tools carsky adb-tunnel --room <roomId>
```

Kiểm tra:

- node `IVI - Android` có `phase=Running`;
- tunnel instruction trả đúng namespace và node key của node đó;
- `nydus-reach` lấy từ UI CarSky, không lấy binary không rõ nguồn.

Chạy lệnh `nydus-reach tunnel adb ...` mà helper trả về trong một terminal giữ
mở. Trong terminal khác, kết nối endpoint tunnel rồi xác nhận identity:

```powershell
adb connect <host:port>
adb devices -l
adb shell getprop ro.serialno
adb shell getprop ro.build.fingerprint
adb shell getprop ro.build.version.release
adb shell cmd activity get-current-user
```

Pass khi serial/fingerprint khớp target và E01 được nâng từ `PARTIAL` bằng output
local ADB thật.

## 4. Gate C — install và launch sạch

### Smoke trước bằng mock flavor

Mock flavor chứng minh APK/HMI/voice pipeline chạy trên AAOS mà không phụ thuộc
privileged VHAL write. Nó vẫn phải được ghi nhãn **MÔ PHỎNG**.

```powershell
adb install -r -d .\app-mock-debug.apk
adb shell am force-stop com.sopa.viva_automotive.mock
adb logcat -c
adb shell am start --user <userId> `
  -n com.sopa.viva_automotive.mock/com.sopa.viva_automotive.MainActivity
```

### Real flavor cho Vehicle Property

```powershell
adb install -r -d .\app-real-debug.apk
adb shell am force-stop com.sopa.viva_automotive
adb logcat -c
adb shell am start --user <userId> `
  -n com.sopa.viva_automotive/.MainActivity
```

`real` flavor có thể cài và mở nhưng VHAL write vẫn bị từ chối nếu app chưa được
platform-sign/allowlist privileged permission. Nếu thiếu permission, ghi **G3
FAIL — privileged integration missing**. Không `adb remount`, sửa `/system` hoặc
reboot room demo trong lượt evidence nếu chưa được team cho phép và chưa có
rollback đã thử.

Thu crash scan:

```powershell
adb shell pidof com.sopa.viva_automotive
adb logcat -d | Select-String -Pattern `
  "FATAL EXCEPTION|AndroidRuntime|ANR|SecurityException"
```

Pass khi activity mở, process sống và crash scan sạch.

## 5. Gate D — prove đúng đường chạy

Không gộp các proof sau thành một câu “end-to-end pass”:

| Proof | Điều kiện pass | Nhãn được phép dùng |
|---|---|---|
| Voice/HMI trên mock APK | Nói câu thật, có `VIVA_TRACE`, HMI/TTS phản hồi | Device + mô phỏng vehicle |
| Voice/HMI trên real APK | Nói câu thật, app chạy trên AAOS | Device; chưa được claim VHAL write |
| HVAC/door write | Có PropertyID/area/value, setter thành công và readback khớp | Device core flow |
| Safety deny | `Deny:G1_SPEED_LOCK` và không có setter sau deny | SafetyGuard trên Device |
| `viva-asr` container | Node Running + image digest | Container/platform only |
| App → `viva-asr` | Request/response correlated bằng timestamp/trace ID | **Chưa thể chạy với source hiện tại** |

Các readback tối thiểu của real flavor:

```powershell
adb shell cmd car_service get-property-value 358614275 49
```

Với door/speed/fan, dùng đúng PropertyID/area đã khóa trong
`vong2/03-contracts.md`; không tự đoán ID lúc demo.

## 6. Gate E — evidence bundle

Mỗi lần chạy tạo một thư mục riêng, ví dụ
`evidence/c2/run-20260804-2230/`, chứa tối thiểu:

- `artifact-manifest.txt` từ CI hoặc manifest local có nhãn rõ;
- `device-info.txt`: serial, fingerprint, Android version, Room/namespace/node;
- `nodes.json`: snapshot phase trước lượt chạy;
- `install-launch-crash.log`;
- `viva-trace-device.log` từ `adb logcat`, không lấy fixture;
- `harness-report.csv` và run manifest do benchmark runner sinh;
- ảnh/video có cùng lượt chạy nếu claim cần visual proof.

Lệnh thu trace và chạy harness:

```powershell
adb logcat -d -s VIVA_TRACE:I > evidence\c2\run-<timestamp>\viva-trace-device.log
adb logcat -d | Select-String -Pattern `
  "FATAL EXCEPTION|AndroidRuntime|ANR|SecurityException" `
  > evidence\c2\run-<timestamp>\install-launch-crash.log

Set-Location backend
go run ./cmd/viva-tools harness report `
  --input ..\evidence\c2\run-<timestamp>\viva-trace-device.log `
  --out ..\evidence\c2\run-<timestamp>\harness-report.csv
```

## 7. CarSky diagnostics ladder

Khi node không chạy, chẩn đoán theo thứ tự; không redeploy mù:

1. Room status.
2. Per-node phase và message.
3. Container log đúng container (`user` hoặc `sidecar`).
4. Registry image/digest nếu node kẹt `Provisioning`.
5. App logcat qua ADB nếu node AAOS đã `Running`.

Các bẫy đã xác nhận:

- `/logs/{node}` của Skycraft là pod/WebRTC log, **không phải logcat**.
- `waiting to start: trying and failing to pull image` là image-pull finding;
  trích nguyên upstream message thay vì ghi “không có log”.
- `registry.hackathon-2.carsky.io` là registry đã dùng thành công.
- Image multi-arch hiện tại của đội đã được pull thành công theo digest; không
  rebuild chỉ để ép single-platform trước demo. Với image mới, arm64-only là
  lựa chọn giảm biến số, không phải quy luật tuyệt đối.
- Pin `ETHERNET` và IP tĩnh phải thao tác qua UI; REST/import hiện không làm được.
- Server-side clone là rollback. File export JSON là evidence/topology reference,
  không import ngược lại được khi có pin `ETHERNET`.

## 8. Bảng đóng gate

| Gate hiện hữu | Pass khi | Evidence quyết định |
|---|---|---|
| G1 Artifact | APK hash khớp manifest; Device identity cùng lượt | E01, E02 |
| G2 Service framework | Chỉ ra service/repository/guard nằm trong APK đang chạy | E05 + source commit |
| G3 Device core flow | HVAC/fan/door có write và readback thật | E03, E06–E09 |
| G4 Benchmark | Trace Device thật; cỡ mẫu và p50/p95 tái lập được | E03, E04 |
| G5 Media/audio | Track đổi, TTS focus/duck/release quan sát được | E10, E11 |

Gate chỉ chuyển PASS khi evidence tồn tại và mở được. Không dùng unit test, fixture
hoặc node `Running` để thay cho Device behavior.
