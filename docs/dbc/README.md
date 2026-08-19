# DBC / VSS thật — Task 1

File tải từ CarSky Artifacts (panel Artifacts, `hackathon-2.carsky.io`, category DBC/VSS), thay cho
tên tín hiệu tự đặt (`EngineData`/`HVACStatus`/`DoorStatus`) mà Tùng đang dùng tạm trong
[`embedded/vhal_server.luau`](../../embedded/vhal_server.luau).

- `body_can.dbc` — Body CAN bus: Gateway ↔ HVAC / BCM (cửa, lốp, nguồn, dây an toàn) ↔ VCU
- `powertrain_can.dbc` — Powertrain CAN bus: VCU / BMS (tốc độ, động cơ điện, pin, số, odometer)
- `vss_full_demo.json` — Catalog VSS chuẩn COVESA đầy đủ (dùng để đối chiếu unit/scale)
- `vss-m1-custom-signals.json` — Overlay VSS custom cho V2X/M1 (không liên quan Task 1, để dành cho
  phần cross-vertical DTC↔SOVD nếu làm sau)

> Ba file DBC/VSS trong thư mục này là **bản duy nhất** trong repo. [`embedded/test_compatibility_checker.py`](../../embedded/test_compatibility_checker.py) đọc trực tiếp từ đây — trước 20/08 nó đọc bản sao ở thư mục gốc, nay đã bỏ.

## Checklist

- [x] `body_can.dbc`, `powertrain_can.dbc`, `vss_full_demo.json`, `vss-m1-custom-signals.json` đã tải về
- [x] Bảng đối chiếu điền xong (bên dưới)
- [ ] Báo Tùng để đối chiếu lại `vhal_server.luau` — **có 2 điểm lệch cần anh ấy xử lý, xem "Cần xử lý" bên dưới**

## ⚠️ Phát hiện quan trọng nhất: đây là xe EV, không có "EngineData"

`powertrain_can.dbc` dòng 15 ghi rõ **"EV framing (no combustion engine)"**. Tên giả định
`EngineData` của Tùng **không có signal tương ứng** vì xe không có động cơ đốt trong. Powertrain
thật chỉ có: `PWT_VehicleSpeed`, `PWT_MotorSpeed` (động cơ điện), `PWT_BatteryStatus`,
`PWT_Odometer`, `PWT_Range`, `PWT_DrivetrainStatus` (số + chế độ lái).

## Bảng đối chiếu tên signal thật ↔ VHAL property

| VHAL property (đã chốt) | Tên giả định trước đó | Message/Signal thật | File nguồn | Ghi chú |
|---|---|---|---|---|
| `HVAC_POWER_ON` | `HVACStatus.*` | `HvacCommand.IsAirConditioningActive` (BO_ 256, bit 40, bool) ↔ status `HvacStatus.IsAirConditioningActive` (BO_ 257) | `body_can.dbc` | ⚠️ **Không khớp 1-1**: signal thật chỉ bật/tắt máy nén AC (`IsAirConditioningActive`), không có tín hiệu "bật nguồn HVAC/quạt" riêng. VSS chuẩn cũng chỉ có `Cabin.HVAC.IsAirConditioningActive`, không có `PowerOn` generic. **Cần hỏi Tùng/mentor**: `HVAC_POWER_ON` của app nên map thẳng vào `IsAirConditioningActive`, hay tắt máy = `FanSpeed=0` + `IsAirConditioningActive=false`? |
| `HVAC_TEMPERATURE_SET` | `HvacCommand.Driver_Temperature` | `HvacCommand.Driver_Temperature` (BO_ 256, bit 0, 16-bit, scale 0.1, **range [16,32] °C**) ↔ status `HvacStatus.Driver_Temperature` (BO_ 257, **range [-40,80] °C** — đo nhiệt độ thực, không phải setpoint) | `body_can.dbc` | Khớp tốt, tên giống hệt giả định. **Lưu ý**: command và status dùng range khác nhau — đừng lẫn khi validate input từ app (app chỉ được set 16–32°C). |
| `HVAC_FAN_SPEED` | (chưa có tên giả định) | `HvacCommand.Driver_FanSpeed` (BO_ 256, bit 32, 8-bit, scale 1, **range [0,5]** — mức số) | `body_can.dbc` | ⚠️ **Lệch đơn vị với VSS chuẩn**: `Cabin.HVAC.Station.Row1.Driver.FanSpeed` trong `vss_full_demo.json` dùng **percent 0–100**. Nếu app gửi theo % (0-100) mà Script Node forward thẳng vào CAN (range 0-5) → lỗi ghi giá trị. Cần quy đổi (vd `level = round(percent / 20)`) — Tùng cần thêm bước này vào `vhal_server.luau`. |
| `DOOR_LOCK` | `DoorStatus.*` | `DoorCommand.Row1Driver_IsLocked` (BO_ 528, bit 0, 8-bit bool) ↔ status `DoorStatus.Row1Driver_IsLocked` (BO_ 769) — có **4 biến thể**: `Row1Driver`, `Row1Passenger`, `Row2Driver`, `Row2Passenger` | `body_can.dbc` | Tên giống giả định. ⚠️ **Không phải 1 property duy nhất** — có 4 cửa riêng biệt. CLAUDE.md chỉ ghi chung `DOOR_LOCK` — **cần hỏi Tùng**: app hiện chỉ điều khiển cửa tài xế (`Row1Driver`) hay cả 4 cửa cùng lúc theo lệnh thoại "khóa cửa"? |

## Signal khác có sẵn (chưa dùng, ghi chú để biết là có)

Không thuộc 4 property đã chốt, nhưng có thể hữu ích sau này (DTC/mở rộng):
`TirePressure` (4 bánh, BO_ 784), `PowerState.LowVoltageSystemState` (BO_ 770, trạng thái ON/ACC/START...),
`VCU_TX_SEATBELT.VCU_Seatbelt_Sts` (dây an toàn), `PWT_BatteryStatus` (SoC/Voltage/Current/Temperature —
hữu ích nếu làm DTC pin), `PWT_DrivetrainStatus.SelectedGear`/`PerformanceMode`.

## Cần xử lý (báo Tùng)

1. **`EngineData` không tồn tại** — xe là EV, dùng `PWT_*` thay thế nếu skill nào đang giả định có engine.
2. **`HVAC_FAN_SPEED` cần quy đổi đơn vị** trước khi ghi CAN (percent app → level 0-5 CAN).
3. **`HVAC_POWER_ON` chưa rõ map vào signal nào** — cần quyết định trước khi code Script Node.
4. **`DOOR_LOCK` là 4 cửa riêng, không phải 1** — cần chốt app điều khiển cửa nào theo lệnh thoại.
