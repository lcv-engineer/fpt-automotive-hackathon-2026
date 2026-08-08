# VHAL & CAN BASELINE MANIFEST (TASK N3b)
### Dự án: VIVA (Vietnamese In-Vehicle Assistant) · Phân nhóm Platform & Embedded
**Phụ trách:** Lê Đức Tùng (Embedded / System Engineer - Team VIVA)  
**Mục tiêu:** Minh bạch hóa các thành phần `provided` (nền tảng CarSky cấp sẵn) vs `configured` vs `modified` vs `new` (team VIVA tự xây dựng) để ghi điểm tối đa ô điểm *Platform utilization (15đ)* và *Ranh giới & Tính tương xứng (2đ)*.

---

## 📊 1. BẢNG PHÂN ĐỊNH THÀNH PHẦN (RANH GIỚI BẢN NỀN & TEAM-OWNED)

| Thành phần | Đường dẫn / Tệp nguồn | Nhãn Phân loại | Mô tả chi tiết & Phạm vi đóng góp |
|---|---|---|---|
| **Android AAOS 14 VM** | Skycraft Node (CarSky Platform) | `provided` | Emulator Android Automotive OS 14 gốc do FPT CarSky cấp sẵn |
| **CAN Bus Broker & DBC** | [`car_signals.dbc`](file:///E:/FPT%20Automotive%20-%20VIVA%20Project/car_signals.dbc) | `configured` | Cấu hình các khung CAN Message 0x200 (HVAC) & 0x300 (Door) |
| **VHAL Native Server** | [`vhal_server.luau`](file:///E:/FPT%20Automotive%20-%20VIVA%20Project/vhal_server.luau) | `modified` | **Refactor toàn bộ script Luau**: Chuyển từ nhận tên intent thô sang tiếp nhận trực tiếp `(propertyId, areaId, value)` hex AAOS chuẩn (`0x15600503`, `0x15400500`, `0x16200b02`) |
| **Vendor System Service** | [`VivaVendorCarService.kt`](file:///E:/FPT%20Automotive%20-%20VIVA%20Project/android_cockpit/app/src/main/java/com/viva/cockpit/VivaVendorCarService.kt) | `new` ⭐ | **100% Team-owned**: Privileged System Service chạy trong `/system/priv-app/` quản lý kết nối CarPropertyManager & fan-out real-time |
| **AI Safety Guard** | [`SafetyGuard.kt`](file:///E:/FPT%20Automotive%20-%20VIVA%20Project/android_cockpit/app/src/main/java/com/viva/cockpit/SafetyGuard.kt) | `new` ⭐ | **100% Team-owned**: Rào chắn an toàn tất định 7 quy tắc G1-G3 tầng Framework + Cờ toggle `isEnabled` cho Ablation A1 |
| **Privileged Allowlist** | [`privapp_permissions_viva.xml`](file:///E:/FPT%20Automotive%20-%20VIVA%20Project/android_cockpit/app/src/main/res/raw/privapp_permissions_viva.xml) | `new` ⭐ | File XML cho phép bypass SecurityException với các quyền `CONTROL_CAR_CLIMATE`, `CONTROL_CAR_DOORS` |
| **Binder AIDL IPC** | [`IVivaVendorCarService.aidl`](file:///E:/FPT%20Automotive%20-%20VIVA%20Project/android_cockpit/app/src/main/aidl/com/viva/cockpit/IVivaVendorCarService.aidl) | `new` ⭐ | Giao diện Binder IPC đa tiến trình giữa AI Agent, System Service và HMI Apps |
| **UDS DTC Simulator** | [`uds_dtc_simulator.py`](file:///E:/FPT%20Automotive%20-%20VIVA%20Project/uds_dtc_simulator.py) | `new` ⭐ | Mô phỏng đọc chẩn đoán lỗi xe ISO-TP `19 02 FF` và phân tích 3 trục (Tần suất, Xu hướng, Tương quan) |

---

## 🔀 2. BẢNG ÁNH XẠ CHÍNH THỨC INTENT ↔ VEHICLE PROPERTY ↔ CAN SIGNAL

| Ý định giọng nói (Intent) | Property ID (Hex / AAOS) | Area ID | Kiểu dữ liệu | Tín hiệu CAN thật (DBC) | Hành vi SafetyGuard G1/G2 |
|---|---|---|---|---|---|
| `SET_HVAC_TEMP` | `0x15600503` (`HVAC_TEMPERATURE_SET`) | `0` (Global) | `Float` (°C) | `TargetTemperature` (0x200) | G1.2: Giới hạn 16.0°C – 32.0°C |
| `SET_FAN_SPEED` | `0x15400500` (`HVAC_FAN_SPEED`) | `0` (Global) | `Int` (0-5) | `FanSpeed` (0x200) | G1.3: Giới hạn mức 0 – 5 |
| `LOCK_DOOR` | `0x16200b02` (`DOOR_LOCK`) | `0x1` (Driver) | `Int` (`1` = Lock) | `DoorLockStatus` (0x300) | Luôn CHO PHÉP khóa cửa khi xe chạy |
| `UNLOCK_DOOR` / `OPEN_DOOR` | `0x16200b02` (`DOOR_LOCK`) | `0x1` (Driver) | `Int` (`0` = Unlock) | `DoorLockStatus` (0x300) | G1.1: CHẶN TỪ CHỐI nếu `Speed > 0` |
| `SET_VOLUME` | `0x11400901` (`AUDIO_VOLUME`) | `0` (Global) | `Int` (0-100) | `VolumeLevel` | G1.4: Giới hạn mức 0 – 100 |
| `GEAR_SHIFT` | `0x11400400` (`GEAR_SELECTION`) | `0` (Global) | `Int` (-1: R, 126: P) | `GearStatus` | G2.1: CHẶN cài số R/P khi `Speed > 10 km/h` |

---

## 🧪 3. BẰNG CHỨNG KIỂM THỬ KHÔNG CẦN HARDWARE (TESTABILITY PROOF)

Hệ thống cung cấp 2 bộ test suite độc lập cho phép kiểm chứng logic mà không cần tới ECU thực tế:
1. **Python / Luau Standalone Suite**: Run `python embedded/test_safety_scenario_pack.py`
2. **Android Framework Unit Test**: Run `./gradlew test` (Thực thi [`SafetyGuardTest.kt`](file:///E:/FPT%20Automotive%20-%20VIVA%20Project/android_cockpit/app/src/test/java/com/viva/cockpit/SafetyGuardTest.kt))
