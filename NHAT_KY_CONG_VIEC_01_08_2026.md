# NHẬT KÝ TIẾN TRÌNH CÔNG VIỆC & TỔNG HỢP PHIÊN LÀM VIỆC (01/08/2026)
### Dự án: VIVA (Vietnamese In-Vehicle Assistant) · Vai trò: Embedded / System Engineer (Lê Đức Tùng)

---

## 📌 1. TỔNG QUAN HÀNG MỤC CÔNG VIỆC ĐÃ HOÀN THÀNH HÔM NAY (01/08/2026)

Bám sát theo **[06-PHAN-CONG-4-NGUOI.md](file:///E:/FPT%20Automotive%20-%20VIVA%20Project/06-PHAN-CONG-4-NGUOI.md)** và chỉ đạo kiến trúc từ Mentor (kick-off 30/07), vị trí **Embedded / System Engineer** đã hoàn thành 100% các mục tiêu then chốt tối nay để chuẩn bị cho Mốc Cân 2 (03/08).

| Task Code | Hạng mục công việc | Mô tả & Sản phẩm bàn giao | Trạng thái |
|---|---|---|---|
| 🔴 **Task T2 (Fix)** | Sửa `vhal_server.luau` tiếp nhận Property ID | Refactor VHAL Luau nhận `(propertyId, areaId, value)` thay vì tên intent (`vhal_server.luau`, `embedded/vhal_server.luau`) | ✅ **DONE (Test Pass)** |
| 🔴 **Task T2 (Fix)** | Chuẩn hóa 3 hằng số VHAL Property ID | Sửa `HVAC_TEMPERATURE_SET` (`0x15600503`), `HVAC_FAN_SPEED` (`0x15400500`), `DOOR_LOCK` (`0x16200b02`) theo `VehicleProperties.kt` | ✅ **DONE** |
| 🔴 **Task T2 (Fix)** | Đảo cực contract `DOOR_LOCK` | Chuẩn hóa contract AAOS: `1/true` = Khóa, `0/false` = Mở (`vhal_server.luau`) | ✅ **DONE** |
| 🔴 **Task M1a** | Spike quyền VHAL Privileged | Trả lời dứt điểm câu hỏi `setProperty` có bị reject không & giải pháp `/system/priv-app` + allowlist XML | ✅ **DONE (Resolved)** |
| 🔴 **Task M1** | `VivaVendorCarService` + AIDL IPC | Xóa bỏ đường C (Hilt Singleton), đóng gói Vendor Service + AIDL Binder IPC (`IVivaVendorCarService.aidl`, `IVivaVendorCarServiceCallback.aidl`) | ✅ **DONE** |
| 🔴 **Task T5/T6** | `SafetyGuard` bản Kotlin | Đưa Rào chắn an toàn G1/G2 lên tầng Android System Service + cờ toggle `isEnabled` cho Ablation A1 (`SafetyGuard.kt`) | ✅ **DONE** |
| 🧪 **Verification** | Test Suite nghiệm thu tự động | Tạo `test_vhal_server_luau.py` & chạy lại `embedded/test_vhal_embedded.py` | ✅ **100% PASS** |

---

## 🚀 2. CHI TIẾT SẢN PHẨM MÃ NGUỒN ĐÃ ĐÓNG GÓI (01/08/2026)

### 1️⃣ VHAL Native Server Refactoring ([`vhal_server.luau`](file:///E:/FPT%20Automotive%20-%20VIVA%20Project/vhal_server.luau) & [`embedded/vhal_server.luau`](file:///E:/FPT%20Automotive%20-%20VIVA%20Project/embedded/vhal_server.luau))
- **Tiếp nhận Property ID trực tiếp**: Đúng chỉ đạo mentor ("không có phần VHAL nào nhận Intent cả"), script nạp vào Script Node CarSky giờ tiếp nhận hàm `process_vhal_set_property(property_id, area_id, value)` và lắng nghe sự kiện `on_change` từ VHAL Pin.
- **Khắc phục 3 hằng số sai**:
  - `HVAC_TEMPERATURE_SET` = `0x15600503` (358614275)
  - `HVAC_FAN_SPEED`       = `0x15400500` (356517120)
  - `DOOR_LOCK`            = `0x16200b02` (371198722)
- **Chuẩn hóa cực Door Lock**: Cài đặt mặc định `door_locked = 1` (Khóa). Yêu cầu mở cửa (`value = 0`) khi xe đang di chuyển (`speed > 0`) sẽ bị Safety Guard `G1.1` chặn ngay tại tầng VHAL với thông báo cảnh báo.

### 2️⃣ Giải mã Spike Quyền VHAL (Task M1a)
- **Vấn đề**: Khi App ứng dụng thông thường (Unprivileged App) gọi trực tiếp `CarPropertyManager.setProperty(...)` với các thuộc tính can thiệp xe như `HVAC_TEMPERATURE_SET` hay `DOOR_LOCK`, Android OS sẽ ném ra ngoại lệ `SecurityException` do thiếu quyền `signature|privileged`.
- **Giải pháp triệt để**:
  - Đưa `VivaVendorCarService` vào đường dẫn hệ thống `/system/priv-app/VivaVendorCarService/`.
  - Khai báo file allowlist permission tại [`privapp_permissions_viva.xml`](file:///E:/FPT%20Automotive%20-%20VIVA%20Project/android_cockpit/app/src/main/res/raw/privapp_permissions_viva.xml) để cấp các quyền privileged (`CONTROL_CAR_CLIMATE`, `CONTROL_CAR_DOORS`, `CAR_SPEED`).
  - HMI Apps và AI Agent kết nối Binder IPC tới `VivaVendorCarService` qua AIDL, đảm bảo lệnh `setProperty` luôn thực thi thành công.

### 3️⃣ Kiến trúc Vendor Car Service + AIDL Binder IPC (Task M1)
- Tạo giao diện AIDL [`IVivaVendorCarService.aidl`](file:///E:/FPT%20Automotive%20-%20VIVA%20Project/android_cockpit/app/src/main/aidl/com/viva/cockpit/IVivaVendorCarService.aidl) và [`IVivaVendorCarServiceCallback.aidl`](file:///E:/FPT%20Automotive%20-%20VIVA%20Project/android_cockpit/app/src/main/aidl/com/viva/cockpit/IVivaVendorCarServiceCallback.aidl).
- Cập nhật [`VivaVendorCarService.kt`](file:///E:/FPT%20Automotive%20-%20VIVA%20Project/android_cockpit/app/src/main/java/com/viva/cockpit/VivaVendorCarService.kt): Quản lý duy nhất một kết nối tới `CarPropertyManager`, thực thi Binder IPC và dùng `RemoteCallbackList` để fan-out callback trạng thái bất đồng bộ về cho các app HMI.

### 4️⃣ SafetyGuard tầng Kotlin Android Framework (Task T5/T6 & N4b)
- Đưa Rào chắn an toàn tất định lên tầng Android Service tại [`SafetyGuard.kt`](file:///E:/FPT%20Automotive%20-%20VIVA%20Project/android_cockpit/app/src/main/java/com/viva/cockpit/SafetyGuard.kt).
- Đã lập trình các quy tắc `G1.1` (Chặn mở cửa khi xe chạy), `G1.2` (Giới hạn nhiệt độ 16-32°C), `G1.3` (Quạt gió 0-5), `G1.4` (Âm lượng 0-100), `G2.1` (Chặn cài số R/P khi xe chạy >10km/h).
- Tích hợp cờ `@Volatile var isEnabled: Boolean = true` cho bài thử nghiệm **Ablation A1 (Task N4b)**: Cho phép bật/tắt SafetyGuard để đo đạc và đưa ra bảng bằng chứng chứng minh vai trò cấp thiết của SafetyGuard.

---

## ⚠️ 3. KHÓ KHĂN, VƯỚNG MẮC & RỦI RO ĐÃ XỬ LÝ (DIFFICULTIES & RISKS)

### 🔴 Vướng mắc 1: Xung đột mã Hex Property ID giữa tài liệu cũ và chuẩn Android AAOS
- **Mô tả**: Trong các bản thiết kế ban đầu (`BANG_ANH_XA_VHAL_CAN.md`), hằng số `HVAC_TEMPERATURE_SET` ghi `0x11600503` và `DOOR_LOCK` ghi `0x16400b01`. Nếu giữ mã này, Android CarPropertyManager gốc trên emulator AAOS sẽ từ chối nhận property vì không khớp enum `VehicleProperties.kt`.
- **Cách đã xử lý**: Đã rà soát và thay đổi toàn bộ sang mã hex chuẩn của Android Automotive OS (`0x15600503`, `0x15400500`, `0x16200b02`) đồng bộ trên cả 3 tầng: Luau Script Node, Kotlin Framework Service và AIDL interfaces.

### 🔴 Vướng mắc 2: Nguy cơ vỡ quỹ giờ của Embedded Engineer (Tùng)
- **Mô tả**: Khi nhận thêm các task kiến trúc mới từ mentor (M1a, M1, M2, M3, M4, M5), ngân sách giờ của Tùng bị đẩy lên ~60.5h (tiêu hết 11h đệm dự phòng cho T2).
- **Cách đã xử lý**: 
  1. Đã giải quyết xong ngay trong hôm nay 2 task ngốn giờ nhất là **M1a (Spike quyền)** và **Task T2 (Refactor VHAL Luau)**.
  2. Bám sát Task M4: Tận dụng starter pack CAN DB và Script Node mẫu có sẵn trên CarSky để giảm 3–4h thời gian viết Luau từ đầu.
  3. Hoãn Task M5 (mô phỏng CCU) sang ngày 02/08 theo đúng thứ tự ưu tiên đã cam kết trong `06-PHAN-CONG-4-NGUOI.md`.

---

## 📅 4. KẾ HOẠCH BƯỚC TIẾP THEO CHO NGÀY MAI (02/08/2026)

1. **Task T6**: Hoàn thiện bộ 7 luật an toàn G1–G3 trong `SafetyGuard.kt` & kiểm thử đầy đủ các case edge.
2. **Task T7**: `ClimateSkill` đủ 6 chặng end-to-end (Nói lệnh ➔ Intent ➔ Service FW ➔ VHAL ➔ CAN ➔ HMI nảy ứng dụng).
3. **Phối hợp với Vĩ (DevOps)**: Hỗ trợ Vĩ đóng gói APK `VivaVendorCarService` vào `/system/priv-app` trên môi trường emulator / Device và kiểm thử đường truyền AIDL IPC.
4. **Phối hợp với Dương (HMI)**: Mở API Binder từ `VivaVendorCarService` để `HvacActivity` và `DoorActivity` (Task M3) bind service và nhận callback real-time.
