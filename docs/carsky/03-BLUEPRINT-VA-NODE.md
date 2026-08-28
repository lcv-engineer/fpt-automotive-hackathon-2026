# 03 — Blueprint, node và chuỗi tín hiệu của room VIVA

> Nguồn: `backend/carsky/nodes.json` (bản export thật), `GATEWAY/README.md`,
> `docs/dbc/`, [`vong2/35-NHAT-KY-CARSKY-19-08.md`](../../vong2/35-NHAT-KY-CARSKY-19-08.md).

---

## 1. Blueprint đang dùng

| | |
|---|---|
| Blueprint | `6deadb05-c856-4dab-976b-432b0fac0658` (`VIVA-deploy-clone-0803`) |
| Nguồn gốc | clone server-side từ blueprint gốc do nền tảng cấp (`RMbeXxTF5ZvkmqzRK04gf`) |
| Số node | **22** (21 node gốc + `VIVA ASR` do đội thêm) |
| Deployment gần nhất được ghi lại | `VIVA-demo-0808` trên device `VIVA` |

⚠️ **Có HAI blueprint trùng tên `VIVA-deploy-clone-0803`.** Bản
`7175eb09-8d15-451e-a26f-aec1f60e667c` **không có node ASR**. Phân biệt bằng ngày
sửa trong danh sách UI, hoặc kiểm bằng `GET /blueprints/{id}` xem có node
`b8eada00-…` không.

---

## 2. Danh sách node

| `nodeType` | Số | Node đáng chú ý | Vì sao quan trọng |
|---|---|---|---|
| `script-node` | 8 | **IVI Gateway** (`n-4e60c4fe-…`), **PWT Gateway**, VCU (`faa07ae4-…`), BCM/Climate/BMS/TCU Gateway | Đây là chỗ logic Lua chạy. Hai node mentor bảo đọc trước khi viết Luau là IVI Gateway và PWT Gateway |
| `gpio-panel` | 4 | **Drive Controls** — slider tốc độ, cần số, chế độ lái | **Chỗ duy nhất đặt được tốc độ**; dùng cho ablation A1 và cho khoảnh khắc demo `G1_SPEED_LOCK` |
| | | TirePressure · SeatBelt · Battery | Cảm biến mô phỏng khác |
| `can-bus` | 2 | BCM CAN · PWT CAN | Định nghĩa bằng `docs/dbc/body_can.dbc` và `powertrain_can.dbc` |
| `kuksa-databroker` | 1 | **Central Broker VSS** | 1.268 tín hiệu VSS chuẩn COVESA |
| `skycraft` | 1 | **IVI - Android** (`cf7fe8d1-…`) | Máy ảo Android `trout_arm64`, Android 14 / SDK 34 — nơi cài APK |
| `container` | 3 | **VIVA ASR** (`b8eada00-…`) · TCU-NAD · SeatBelt ECU | `VIVA ASR` là image do đội đẩy lên registry |
| `eth-bridge` | 2 | IVI Switch · TCU Switch | Switch L2 ảo, kiêm DHCP server nhỏ |
| `device-proxy` | 1 | Device Proxy | Cầu USB từ trình duyệt vào VM |

---

## 3. Chuỗi tín hiệu — đường đi thật, đã đo

### 3.1 Chiều lên (cảm biến → app)

```text
GPIO Panel "Drive Controls"  (slider Speed 0..240 km/h)
   │  pins.sensor
   ▼
VCU.lua        RPM = Speed * 66; phát PWT_VehicleSpeed / PWT_MotorSpeed / PWT_DrivetrainStatus
   │  Powertrain CAN
   ▼
PWT_Gateway.lua   (read-only bridge CAN -> KUKSA)
   │  KUKSA
   ▼
Central Broker VSS     Vehicle.Speed
   │  KUKSA subscribe
   ▼
IVI_GATEWAY.lua        push_if_changed -> VHAL gRPC :9300
   │  pins.vhal
   ▼
Android VHAL  ->  CarService  ->  CarPropertyManager  ->  App
```

✅ **Đã chứng minh chạy đủ 19/08** (evidence:
`evidence/c2/vhal-local-fake-server-blocker-0819.txt`). Kéo slider = 60 km/h:

```
GPIO   vcu/Speed                  = 60   ts 03:41:51.161Z
CAN    PWT_VehicleSpeed/Speed_kph = 60   ts 03:49:14.613Z
KUKSA  Vehicle.Speed              = 60   ts 03:41:51.300Z
log    [igw] -> vhal 0x11600207 area=0x0 pushed = 16.6666   (= 60 km/h, quy doi dung)
```

🔴 **Nhưng app vẫn đọc `0.0`.** Root cause thuộc **image AAOS**, không thuộc đội:

```
ro.vendor.vehiclehal.server.use_local_fake_server = true
ro.boot.vendor.vehiclehal.server.cid  = 1     (loopback)
ro.boot.vendor.vehiclehal.server.port = 9210
```

`ps -ef` xác nhận hai tiến trình cùng chạy: `…-fake-hardware-grpc-server` (PID 477)
và `…-trout-service` (PID 478, client). VHAL client nối vào **fake server nội bộ**,
không bao giờ nối tới IVI Gateway. Property nằm trong `ro.vendor.*` → nướng trong
vendor partition, **không `setprop` đè được**.

⇒ Đây là lời giải cho việc mọi mốc readback qua `CarPropertyManager` chưa từng đạt:
không phải lỗi app, không phải thiếu quyền, không phải thiếu privileged install.

### 3.2 Chiều xuống (lệnh app → xe)

```text
App -> CarPropertyManager -> VHAL (pin `vhal` cua skycraft)
   -> IVI_GATEWAY.lua  (VHAL Property ID  ->  VSS path)
   -> Central Broker VSS
   -> BCM_GATEWAY.lua  (VSS  ->  CAN command frame)
   -> Body CAN  ->  Climate ECU.lua / BCM ECU.lua
   -> phat lai CAN status  ->  BCM_GATEWAY  ->  VSS current  ->  nguoc len app
```

⚠️ Ghi VSS **từ ngoài** bằng REST không lan xuống CAN — chuỗi VSS→CAN được kích
hoạt từ phía VHAL (`pins.vhal:on_change → actuate_kuksa`). Xem
[02 §3.3](02-API-REFERENCE.md).

---

## 4. Script Lua do nền tảng cấp — thư mục `GATEWAY/`

⚠️ **Không phải code của đội.** Giữ trong repo để đối chiếu.

### Gateway (cầu nối miền)

| File | Vai trò | Interface |
|---|---|---|
| `IVI_GATEWAY.lua` | Cầu KUKSA ↔ gRPC VHAL server (`:9300`) của AAOS Trout. Dịch VSS ↔ VHAL Property ID (AOSP chuẩn + vendor `0x21XXXXXX`). Forward VSS sang AGL Cluster. Khử trùng lặp bằng `push_if_changed` | `pins.vhal`, `pins.kuksa`, `pins.eth` |
| `BCM_GATEWAY.lua` | Dịch lệnh ghi KUKSA (HVAC, khoá cửa) thành khung CAN command; phản chiếu CAN status về VSS. Chuyển tiếp `Vehicle.Speed` sang Body CAN (`0x460`) cho vECU dây an toàn | `pins.can`, `pins.kuksa` |
| `PWT_Gateway.lua` | Cầu **read-only** CAN → KUKSA: tốc độ, vòng tua motor, odometer, SoC, điện áp, dòng, nhiệt pin, tầm hoạt động, PRNDL, chế độ lái | `pins.can`, `pins.kuksa` |
| `TCU_GATEWAY.lua` | SOME/IP provider (`Service 0x2000` / `Instance 0x0001`); telemetry qua Event `0x8001`; nhận lệnh từ xa qua Method `0x9001` (`SetDoorLock`) | `e-eth`, `pins.kuksa` |

### ECU ảo

| File | Mô phỏng |
|---|---|
| `VCU.lua` | Master điều khiển chuyển động EV. Đọc GPIO (Speed 0..240, PRNDL, Normal/Sport/Eco/Snow/Rain), `RPM = Speed * 66` |
| `BMS ECU.lua` | Pin EV: SoC, `Voltage = 320 + soc * 0.6`, dòng xả −15A, 28°C, `Range = SoC% * 450 km` |
| `BCM ECU.lua` | Thân xe: nhận `DoorCommand`, trả `DoorStatus`; TPMS 4 bánh từ GPIO |
| `Climate ECU.lua` | HVAC: nhận `HvacCommand` (nhiệt độ ghế lái/phụ, quạt, hướng gió, AC, lấy gió trong, sấy kính), trả `HvacStatus` |

### ⚠️ Nền tảng CŨNG có safety guard riêng

`GATEWAY/README.md` §3 mô tả ba luật đã có sẵn trong script Lua:

| Luật nền tảng | Nội dung | Áp tại |
|---|---|---|
| G1.1 | `Vehicle.Speed > 0` → chặn mọi yêu cầu `IsLocked = false` | `BCM_GATEWAY.lua`, `IVI_GATEWAY.lua` |
| G1.2 | Giới hạn nhiệt độ đặt trong `16.0–32.0 °C` | `BCM_GATEWAY.lua`, `IVI_GATEWAY.lua` |
| G1.3 | Giới hạn quạt trong thang DBC `0..5` | `BCM_GATEWAY.lua` |

🔴 **Đây KHÔNG phải SafetyGuard của đội.** Guard của VIVA nằm trong app
(`DefaultSafetyGuard`, biên `VehicleRepository`) và có ngưỡng khác
(`MAX_UNLOCK_SPEED_KMH = 5f`, không phải `> 0`). Khi trình bày phải tách hai thứ,
nếu không sẽ bị hỏi *"vậy phần nào là của đội"*. Xem
[`docs/he-thong/05-VIVA-BODY.md`](../he-thong/05-VIVA-BODY.md).

---

## 5. DBC / VSS — bản thật, tải từ CarSky Artifacts

Nằm ở `docs/dbc/` và là **bản duy nhất trong repo**
(`embedded/test_compatibility_checker.py` đọc trực tiếp từ đây).

| File | Nội dung |
|---|---|
| `body_can.dbc` | Body CAN: Gateway ↔ HVAC/BCM (cửa, lốp, nguồn, dây an toàn) ↔ VCU |
| `powertrain_can.dbc` | Powertrain CAN: VCU/BMS (tốc độ, motor, pin, số, odometer) |
| `vss_full_demo.json` | Catalog VSS COVESA đầy đủ — dùng đối chiếu unit/scale |
| `vss-m1-custom-signals.json` | Overlay VSS custom cho V2X/M1 |

### ⚠️ Đây là xe EV — không có `EngineData`

`powertrain_can.dbc` dòng 15 ghi rõ *"EV framing (no combustion engine)"*.
Powertrain thật chỉ có `PWT_VehicleSpeed`, `PWT_MotorSpeed`, `PWT_BatteryStatus`,
`PWT_Odometer`, `PWT_Range`, `PWT_DrivetrainStatus`.

### Ba chỗ lệch đơn vị/ngữ nghĩa phải nhớ

| Chỗ lệch | Chi tiết |
|---|---|
| **FanSpeed** | VSS `…Driver.FanSpeed` là **percent 0–100**; CAN `HvacCommand.Driver_FanSpeed` là **mức 0–5**. Gateway bắt buộc quy đổi `percent = level × 20`, chiều về `level = round(percent / 20)` |
| **Temperature** | `HvacCommand.Driver_Temperature` range **[16,32] °C** (setpoint); `HvacStatus.Driver_Temperature` range **[−40,80] °C** (nhiệt độ **thực**). Đừng lẫn khi validate input, và đừng nói TTS *"cabin đã đạt 24 độ"* |
| **DOOR_LOCK** | Không phải một property — DBC có **4 cửa riêng** (`Row1Driver`, `Row1Passenger`, `Row2Driver`, `Row2Passenger`). V1 của app chỉ tác động `Row1Driver` |

Danh sách signal có sẵn nhưng chưa dùng: `TirePressure` (BO_ 784),
`PowerState.LowVoltageSystemState` (BO_ 770), `VCU_TX_SEATBELT.VCU_Seatbelt_Sts`,
`PWT_BatteryStatus`, `PWT_DrivetrainStatus.SelectedGear/PerformanceMode`.

---

## 6. Bảy nguồn tín hiệu từ `GET /signals/{roomId}`

| `key` | Loại |
|---|---|
| `central-broker-vss` | KUKSA — 1.268 tín hiệu VSS |
| `bcm-can` | CAN — 33 tín hiệu, khớp `docs/dbc/README.md` |
| `pwt-can` | CAN |
| `drive-controls` | GPIO — `vcu/Speed` `[0,180]` kmh, `entryType=actuator` ⚠️ |
| `battery-sensor` · `seatbelt-sensor` · `tirepressure-sensor` | GPIO |

⚠️ **Hai nguồn ghi dải tốc độ khác nhau:** metadata `GET /signals/{room}/drive-controls`
trả `[0,180]` kmh (đọc 07/08), còn `GATEWAY/README.md` mô tả slider `0..240` km/h.
Chưa đối chiếu lại sau đó — **kiểm bằng metadata trước khi dựa vào con số trần** nếu
kịch bản cần tốc độ trên 180.

Bốn tín hiệu của bảng mapping M2 đã được xác nhận bằng **metadata của chính nền
tảng** (không phải bằng tài liệu đội tự viết):

```
Driver.Temperature                 float, actuator, °C
Driver.FanSpeed                    uint8, actuator, percent 0-100
Door.Row1.DriverSide.IsLocked      bool,  True = Locked
Vehicle.Speed                      float, sensor, km/h
```
