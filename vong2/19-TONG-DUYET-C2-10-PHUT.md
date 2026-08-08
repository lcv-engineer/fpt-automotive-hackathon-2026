# Runbook tổng duyệt C2 — 10 phút

> Chuẩn bị kỹ thuật build/CI/APK identity/ADB/install/diagnostics trước khi bấm
> giờ nằm ở [CarSky AAOS device gate](25-CARSKY-AAOS-DEVICE-GATE.md). Không dùng
> 10 phút tổng duyệt để sửa tunnel, tìm APK hoặc đoán privileged permission.

Ngày dự kiến chạy: **02/08/2026**
Mục tiêu: hoàn thành **một lượt liên tục**, thu evidence thật và chốt claim nào được phép dùng cho C2 ngày 03/08.

**Cập nhật 03/08 23:13 (Asia/Saigon):** runbook **CHƯA CHẠY**. E01 đã có bản
**PARTIAL** từ CarSky web ADB shell (`VIVA` connected, serial/fingerprint/model
đã đọc trực tiếp), nhưng local `adb devices -l`, E02–E11 và toàn bộ lượt demo
vẫn chưa có. Vì vậy các gate Device/benchmark vẫn giữ đỏ cho tới khi cả đội
chạy trọn runbook trên CarSky.

> Đây không phải buổi “đọc slide”. Nếu không có Device hoặc service framework, vẫn chạy mock để kiểm tra kịch bản nhưng phải gắn nhãn **MÔ PHỎNG** và giữ các gate tương ứng màu đỏ.

## 1. Vai trò

| Người | Trách nhiệm trong lượt chạy |
|---|---|
| **Long** | Dẫn chuyện, nói lệnh, giữ thời gian, gọi đúng Claim ID và không vượt phạm vi evidence |
| **Vĩ** | Thu `VIVA_TRACE`, chạy harness, công bố cỡ mẫu + p50/p95 và giữ failure trong mẫu |
| **Tùng** | Xác nhận service/AIDL/safety boundary; đọc lại PropertyID/area/value và VHAL callback |
| **Dương** | Build/cài APK, vận hành HMI/media/volume, lưu artifact identity và crash scan |

Nếu một người vắng, ghi tên người thay vào biên bản; không để một đầu việc evidence vô chủ.

## 2. Preflight — làm trước khi bấm giờ

- [ ] `git status --short` sạch; ghi `git rev-parse HEAD` vào E02.
- [ ] Build đúng flavor; tính SHA-256 APK; không đổi APK sau khi ghi E02.
- [ ] Ghi Device serial/fingerprint vào E01.
- [ ] Cài và launch APK sạch; cấp/kiểm tra permission; xóa logcat.
- [ ] Tùng chỉ ra file service/AIDL/SafetyGuard đang được build. Không tìm thấy thì đánh **G2 FAIL** ngay.
- [ ] Vĩ chuẩn bị lệnh harness `report --adb`; không dùng fixture.
- [ ] Trạng thái đầu: xe đứng yên/P, HVAC 26°C, fan 1, cửa tài xế unlocked, một track đang phát.
- [ ] Camera quay thấy HMI/Device và thu được TTS/media; đồng hồ đếm 10 phút sẵn sàng.

Lệnh tham chiếu, điều chỉnh serial/path theo máy chạy:

```powershell
git status --short
git rev-parse HEAD
Get-FileHash automotive\app\build\outputs\apk\real\debug\app-real-debug.apk -Algorithm SHA256
adb devices -l
adb shell getprop ro.build.fingerprint
adb logcat -c
```

## 3. Timeline 10 phút

| Thời gian | Nội dung | Pass condition | Evidence |
|---|---|---|---|
| **0:00–0:45** | Artifact + phạm vi: commit/flavor/Device; nói rõ CCU nào mô phỏng | Identity khớp, không claim 10 intent tới CAN | E01, E02 |
| **0:45–1:30** | Kiến trúc: voice → guard/service → PropertyID; media/volume rẽ adapter riêng | Tùng chỉ ra được service boundary đang chạy | E05 hoặc G2 FAIL |
| **1:30–2:30** | “Hạ điều hòa xuống 24 độ” | HMI + readback 24°C; TTS sau `Applied` | E03, E06, E10 |
| **2:30–3:20** | “Quạt mạnh lên” → trả lời “mức 3” | Có clarification rồi fan/readback mức 3 | E03, E07, E10 |
| **3:20–4:10** | “Khóa cửa” | Cửa tài xế locked; đúng polarity/area | E03, E08, E10 |
| **4:10–5:00** | “Đặt bàn ăn tối” | Từ chối; không có property/media action | E03, E10 |
| **5:00–5:50** | “Chuyển bài” | Track đổi thật; TTS duck/release focus | E10, E11 |
| **5:50–6:40** | Safety: “Mở cửa” ở speed 60 chỉ khi fixture/snapshot thật sẵn sàng | `Deny:G1_SPEED_LOCK`, không setter | E09, E10 |
| **6:40–8:00** | Mở trace/harness summary | Nêu cỡ mẫu, failure count, p50/p95 và định nghĩa latency | E03, E04 |
| **8:00–9:00** | Team-owned/baseline: bỏ voice/guard/callback thì claim nào sụp | Trả lời bằng C-VOICE/C-SAFETY/C-PLATFORM, không nói chung chung | N1 + N3 |
| **9:00–10:00** | Giới hạn + kết luận | Đọc đúng gate đỏ/vàng; chốt việc trước C2 | Biên bản |

Không dành thời gian live để sửa code. Một lệnh timeout quá 3 giây: gọi đúng `Error:<stage>`, tiếp tục dòng sau, không thử quá một lần.

## 4. Thu evidence ngay sau lượt chạy

```powershell
adb logcat -d -s VIVA_TRACE:I > evidence\c2\viva-trace-device.log
adb logcat -d | Select-String -Pattern "FATAL EXCEPTION|AndroidRuntime|ANR" > evidence\c2\install-launch-crash.log
```

Từ thư mục `backend/`:

```powershell
go run ./cmd/viva-tools harness report --input ..\evidence\c2\viva-trace-device.log --out ..\evidence\c2\harness-report.csv
```

Hai lệnh trên là protocol ghi evidence; chỉ chạy khi đúng Device đã kết nối. Không copy `android/voice/fixtures/golden_trace.log` sang tên E03.

## 5. Biên bản pass/fail

| Gate | Kết quả | Evidence / lý do fail | Quyết định claim C2 |
|---|---|---|---|
| G1 Artifact | `PASS / FAIL` | | |
| G2 Service framework | `PASS / FAIL` | | |
| G3 Device core flow | `PASS / FAIL` | | |
| G4 Benchmark | `PASS / FAIL` | | |
| G5 Media/audio | `PASS / FAIL` | | |

Chốt sau buổi chạy:

- [ ] Video E10 mở được và đúng một lượt liên tục.
- [ ] E03 có nguồn Device thật; E04 sinh trực tiếp từ E03.
- [ ] Không có câu “Đã…” trước `Applied`.
- [ ] Mọi claim đỏ đã bị bỏ khỏi C2 hoặc đổi nhãn `Kế hoạch`/`Mô phỏng`.
- [ ] Gửi danh sách blocker theo **file/commit/evidence**, không gửi trạng thái “em làm xong ở máy em”.
