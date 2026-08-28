# 08 — Embedded: VHAL ↔ CAN, DBC/VSS và mô phỏng chẩn đoán

> Code của đội: `embedded/`. Script của **nền tảng**: `GATEWAY/`.
> Tín hiệu thật: `docs/dbc/`.

---

## 1. Ranh giới: cái gì của đội, cái gì của nền tảng

| Thư mục | Chủ sở hữu | Vai trò |
|---|---|---|
| `embedded/` | **Đội VIVA** (Tùng) | `vhal_server.luau` — script VHAL ↔ CAN; `uds_dtc_simulator.py` — mô phỏng UDS/DTC; 4 script kiểm thử Python |
| `GATEWAY/` | **Nền tảng CarSky** | 4 gateway Lua + 4 ECU ảo Lua. Giữ trong repo **chỉ để đối chiếu** |
| `docs/dbc/` | Nền tảng (tải từ Artifacts) | `body_can.dbc`, `powertrain_can.dbc`, `vss_full_demo.json`, `vss-m1-custom-signals.json` |

🔴 Khi trình bày phải tách rõ. `GATEWAY/README.md` mô tả cả "AI Safety Guard G1.1–G1.3"
— **đó là guard của nền tảng**, không phải `DefaultSafetyGuard` của VIVA.

---

## 2. `embedded/vhal_server.luau`

Script phía đội, dịch giữa VHAL property và tín hiệu CAN. Nội dung chính:

- **Bảng `VHAL_PROP`** — property ID dạng hex kèm giá trị thập phân và chú thích:

  ```
  HVAC_TEMPERATURE_SET = 0x15600503  -- 358614275
  HVAC_FAN_SPEED       = 0x15400500  -- 356517120
  DOOR_LOCK            = 0x16200b02  -- 371198722
  VEHICLE_SPEED        = 0x11600207
  GEAR_SELECTION       = 0x11400400  -- -1: R, 0: N, 126: P, 127: D
  EV_BATTERY_LEVEL     = 0x11600600
  DRIVER_SEATBELT      = 0x16400b02
  AUDIO_VOLUME         = 0x11400901
  ```

- `normalize_prop_id()` — nhận cả number, `"0x…"` và chuỗi thập phân.
- `VehicleState` — trạng thái xe thời gian thực trong script (speed, motor_rpm,
  hvac_temp_driver/pass, fan_speed, ac_on, defroster_on, …).

⚠️ `0x11600207` xuất hiện đúng trong log nền tảng khi kéo slider tốc độ:
`[igw] → vhal 0x11600207 area=0x0 pushed = 16.6666` — tức property id này khớp với
thứ IVI Gateway thật đang push.

### 🔴 Ba điểm lệch đã phát hiện khi đối chiếu DBC thật

`docs/dbc/README.md` ghi lại. Ba việc phải xử lý ở phía script:

| Vấn đề | Chi tiết |
|---|---|
| **`EngineData` không tồn tại** | `powertrain_can.dbc` dòng 15: *"EV framing (no combustion engine)"*. Powertrain thật chỉ có `PWT_VehicleSpeed`, `PWT_MotorSpeed`, `PWT_BatteryStatus`, `PWT_Odometer`, `PWT_Range`, `PWT_DrivetrainStatus` |
| **`HVAC_FAN_SPEED` cần quy đổi đơn vị** | VSS dùng **percent 0–100**, CAN dùng **mức 0–5**. Forward thẳng là ghi sai giá trị |
| **`HVAC_POWER_ON` chưa rõ map vào signal nào** | CAN chỉ có `IsAirConditioningActive` (bật/tắt máy nén), không có "bật nguồn HVAC" riêng. VSS chuẩn cũng vậy |
| **`DOOR_LOCK` là 4 cửa riêng** | `Row1Driver`, `Row1Passenger`, `Row2Driver`, `Row2Passenger` — không phải một property |

---

## 3. `embedded/uds_dtc_simulator.py` — mô phỏng chẩn đoán

Task T10. Theo **ISO 14229 (UDS)** / **ISO 15031 · SAE J2012 (DTC)**.

- ISO-TP, service `0x19 0x02 FF` (đọc DTC theo status mask).
- Phân nhóm mã lỗi theo **P / C / B / U** (Powertrain, Chassis, Body, Network).
- Mỗi mã có: `hex_code`, `severity`, `status` (`ACTIVE` 0x09 / `Pending` 0x04 /
  `Stored` 0x08), `frequency` (số lần trong 100 km gần nhất), `trend`
  (`ESCALATING`/`STABLE`/`INTERMITTENT`), `action_vn`, và `correlated_with`.
- Phân tích ba trục: mức độ · tần suất · tương quan giữa các mã.

Ví dụ `P0301` (bỏ lửa xy-lanh 1) tương quan với `P0171` và `U0100`.

⚠️ Đây là **mô phỏng** phục vụ hướng cross-vertical DTC ↔ SOVD; intent `dtc_query`
**đã bị cắt** khỏi danh mục intent (29/07). Không claim đây là chẩn đoán trên xe thật.

---

## 4. Bốn script kiểm thử Python

| File | Kiểm |
|---|---|
| `test_vhal_embedded.py` | Logic VHAL phía embedded |
| `test_vhal_server_luau.py` | Script Luau |
| `test_safety_scenario_pack.py` | Bộ kịch bản an toàn |
| `test_compatibility_checker.py` | **Đối chiếu DBC/VSS thật ở `docs/dbc/`** — trước 20/08 đọc bản sao ở thư mục gốc, nay đã bỏ |

---

## 5. Script Lua của nền tảng — bảng tra nhanh

### Gateway

| File | Vai trò | Pin |
|---|---|---|
| `IVI_GATEWAY.lua` | KUKSA ↔ gRPC VHAL server (`:9300`) của AAOS Trout. Dịch VSS ↔ VHAL Property ID (AOSP chuẩn + vendor `0x21XXXXXX`). Forward VSS sang AGL Cluster (`10.99.0.3:55555`). `push_if_changed` để khử trùng lặp, tối ưu Binder | `pins.vhal`, `pins.kuksa`, `pins.eth` |
| `BCM_GATEWAY.lua` | KUKSA → khung CAN command (HVAC, khoá cửa); CAN status → VSS current. Cross-domain: đọc `Vehicle.Speed` từ Powertrain, phát lại lên Body CAN thành `PWT_VehicleSpeed` (`0x460`) cho vECU dây an toàn | `pins.can`, `pins.kuksa` |
| `PWT_Gateway.lua` | **Read-only** CAN → KUKSA: tốc độ, vòng tua motor, odometer, SoC, điện áp, dòng, nhiệt pin, tầm hoạt động, PRNDL, chế độ lái | `pins.can`, `pins.kuksa` |
| `TCU_GATEWAY.lua` | SOME/IP provider (`Service 0x2000`/`Instance 0x0001`) trên `e-eth`; telemetry JSON qua Event `0x8001` tới TCU-NAD; nhận lệnh từ xa qua Method `0x9001` (`SetDoorLock`) | `e-eth`, `pins.kuksa` |

### ECU ảo

| File | Mô phỏng |
|---|---|
| `VCU.lua` | Master chuyển động EV: đọc GPIO (Speed 0..240 km/h, PRNDL, Normal/Sport/Eco/Snow/Rain), `RPM = Speed × 66`, phát `PWT_VehicleSpeed` / `PWT_MotorSpeed` / `PWT_DrivetrainStatus` |
| `BMS ECU.lua` | Pin EV: SoC, `Voltage = 320 + soc × 0.6`, dòng xả `−15 A`, `28 °C`, `Range = SoC% × 450 km`. Đọc slider SoC từ GPIO |
| `BCM ECU.lua` | Nhận `DoorCommand` → trả `DoorStatus`; đọc TPMS 4 bánh từ GPIO |
| `Climate ECU.lua` | Nhận `HvacCommand` (nhiệt độ ghế lái/phụ, quạt, hướng gió, AC, lấy gió trong, sấy kính trước/sau) → trả `HvacStatus` |

---

## 6. Kiến trúc tín hiệu tổng thể (theo `GATEWAY/README.md`)

```text
Android IVI (AAOS/Trout) <--VHAL gRPC :9300--> IVI_GATEWAY.lua
AGL Cluster (Linux)      <--KUKSA gRPC :55555--> IVI_GATEWAY.lua
                                   |
                          VSS Subscribe/Actuate
                                   v
                     Vehicle KUKSA Databroker (COVESA VSS 6.0)
                       ^              ^                 ^
                       |              |                 |
             BCM_GATEWAY.lua   PWT_Gateway.lua   TCU_GATEWAY.lua
                       |              ^                 |
                  Body CAN       Powertrain CAN     SOME/IP UDP
                       |              ^              224.0.0.1:30490
              BCM ECU / Climate ECU   |                 |
                                 VCU / BMS           TCU-NAD -> Cloud
```

Chuẩn dữ liệu: **COVESA VSS 6.0**. Kiến trúc: **Zonal & Domain Gateway**.

---

## 7. Việc còn treo ở tầng embedded

| Việc | Trạng thái |
|---|---|
| `vhal_server.luau` chạy thật trên script node CarSky | ❌ CHƯA THỬ — hiện script node đang chạy bản Lua của nền tảng |
| Quy đổi FanSpeed percent ↔ level trong script của đội | ❌ chưa thêm |
| `HVAC_POWER_ON` map vào signal nào | ❌ chưa chốt |
| `door_lock` fan-out 4 cửa | ❌ cần quyết định mới, không đổi ngầm intent hiện tại |
| Readback VHAL → app | 🔴 bị chặn bởi `use_local_fake_server` trong image AAOS — [chi tiết](../carsky/09-SU-CO-VA-GIOI-HAN.md) |
