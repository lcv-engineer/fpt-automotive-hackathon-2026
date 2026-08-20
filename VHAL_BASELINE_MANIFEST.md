# VHAL & CAN BASELINE MANIFEST (TASK N3b)

### Dự án: VIVA (Vietnamese In-Vehicle Assistant) · Phân nhóm Platform & Embedded

**Phụ trách:** Lê Đức Tùng (Embedded / System Engineer)
**Rà soát lại:** 08/08/2026 — xem mục 5 để biết bản trước sai ở đâu.
**Mục tiêu:** minh bạch `provided` / `configured` / `modified` / `new` để chấm ô
*Platform utilization (15đ)* và *Ranh giới & Tính tương xứng (2đ)*.

> **Quy tắc của bản này:** mọi đường dẫn phải mở được từ gốc repo này. Nghi ngờ
> thì hạ một bậc (`new` → `modified` → `configured` → `provided`), không nâng.
> Thành phần chưa từng chạy trên CarSky thì ghi **KẾ HOẠCH**, không ghi nhãn
> ownership như thể nó đang chạy.

---

## 1. Nền tảng cấp sẵn — đọc từ chính blueprint đã export

Nguồn: [`backend/carsky/blueprint-VIVA-deploy-backup.json`](backend/carsky/blueprint-VIVA-deploy-backup.json)
(do `GET /blueprints/{id}/export` sinh ra) và [`evidence/carsky/signals-rest-0808/00-nodes-live.json`](evidence/carsky/signals-rest-0808/00-nodes-live.json)
(22/22 node `Running`, đọc ngày 08/08).

Room VIVA có **8 Script Node, tất cả do nền tảng viết**:

| Node | Script | Nhãn | Nó làm gì |
|---|---|---|---|
| IVI Gateway | `infotainment_gateway.lua` (22 KB) | `provided` | **Cầu KUKSA ↔ VHAL hai chiều đầy đủ.** `pins.vhal:on_change → actuate_kuksa` (app ghi VHAL → VSS), `vehicle_kuksa:subscribe → pins.vhal:push` (VSS → VHAL callback), `pins.vhal:on_get` (getProperty). Có sẵn bảng mapping VSS ↔ (prop, area), quy đổi quạt `fan_vhal_to_vss`, quy đổi tốc độ m/s ↔ km/h, và dedup change-only |
| Body Gateway | `body_gateway.lua` | `provided` | CAN body ↔ KUKSA |
| PWT Gateway | `powertrain_gateway.lua` | `provided` | CAN powertrain → KUKSA (read-only) |
| Climate ECU | `climate_ecu.lua` | `provided` | **"virtual HVAC ECU. Echoes HvacCommand into HvacStatus"** — chính là "CCU mô phỏng M5" |
| BCM ECU | `bcm_ecu.lua` | `provided` | **"door echo + TPMS sensor"** |
| VCU / BMS / TCU Gateway | `vcu.lua`, `bms.lua`, `tcu_gateway.lua` | `provided` | Powertrain coordinator, pin, TCU ↔ SOME/IP |

| Thành phần khác | Nhãn | Căn cứ |
|---|---|---|
| Device AAOS 14 (node skycraft `IVI - Android`) | `provided` | CarSky cấp sẵn |
| KUKSA Data Broker (`Central Broker (VSS)`) | `provided` | 1.268 tín hiệu VSS — [`evidence/carsky/signals-rest-0808/`](evidence/carsky/signals-rest-0808/) |
| CAN bus (`BCM CAN`, `PWT CAN`) | `provided` | 33 tín hiệu trên `bcm-can` |
| GPIO panel (`Drive Controls`, `SeatBelt`, `Battery`, `TirePressure`) | `provided` | `vcu/Speed` `[0,180] kmh` là `actuator` |
| DBC [`docs/dbc/body_can.dbc`](docs/dbc/body_can.dbc), [`powertrain_can.dbc`](docs/dbc/powertrain_can.dbc) | `provided` | Tải từ panel Artifacts, đội không tự viết |
| Catalog VSS [`docs/dbc/vss_full_demo.json`](docs/dbc/vss_full_demo.json) | `provided` | Cùng nguồn |
| Blueprint bản clone của đội | `configured` | Clone server-side, đội chỉ **thêm node** `VIVA ASR`, không dựng từ số 0 |

> ⭐ **Đây là câu trả lời cho dòng 🟠 CHỜ N3b trong [`vong2/22-N3-BASELINE-MANIFEST.md`](vong2/22-N3-BASELINE-MANIFEST.md)** —
> *"Script Node IVI Gateway / PWT Gateway: sửa mapping (`modified`) hay viết mới (`new`)?"*
> **Trả lời: `provided`.** Đội không sửa một dòng nào trong 8 script đó. Kiểm được:
> tìm `process_vhal_set_property`, `check_safety_guard`, `"VHAL SERVER"` trong
> blueprint export → không có kết quả.

> 🔴 **ĐÍNH CHÍNH 12/08/2026 — câu trên chỉ còn đúng tới hết ngày 08/08.**
> Commit `c255ccc` (**09/08**, có trên `origin/main`) đưa 8 script Lua vào [`GATEWAY/`](GATEWAY/),
> trong đó **2 file đã bị sửa thật**. So từng byte với blueprint export:
>
> | File | Khác | Nội dung |
> |---|---|---|
> | [`GATEWAY/IVI_GATEWAY.lua`](GATEWAY/IVI_GATEWAY.lua) | **+19 thêm / 4 thay** | Khối guard 17 dòng: **G1.1** (từ chối mở khoá cửa khi `Vehicle.Speed > 0`) và **G1.2** (chặn nhiệt độ ngoài 16–32 °C) đặt tại đường `pins.vhal → actuate_kuksa`; sửa property ID sai của bản gốc: `EV_BATTERY_LEVEL` `0x11600204` → `0x11600600` |
> | [`GATEWAY/BCM_GATEWAY.lua`](GATEWAY/BCM_GATEWAY.lua) | **+33 thêm / 4 thay** | **G1.1/G1.2/G1.3** ở lớp CAN; thêm `current_vehicle_speed` và `bind_door_actuate` (thay `bind_bool_actuate`) để chặn mở cửa **trước khi** publish khung `DoorCommand` |
> | `VCU.lua` | 16 dòng | Chỉ khác dấu tiếng Việt trong comment/log — **không có thay đổi chức năng**, không khai là đóng góp |
> | 5 file còn lại | 0 dòng | Giữ nguyên của nền tảng |
>
> Nhãn đúng cho 2 file đầu là **`modified` — chưa xác nhận runtime**: mới xác nhận
> được phần source nằm trong commit đã nộp, **chưa** có log từ nền tảng in ra
> `[SAFETY GUARD G1.1 BLOCKED]`. Không được viết là "đã chạy trên CarSky".
>
> Bản 08/08 khai thiếu cột `modified` có thể đã góp phần khiến phần sửa gateway
> không hiện rõ trong feedback BTC. Việc này không thay đổi khoảng trống runtime
> mà mentor nêu. Xem
> [`vong2/31-BO-SUNG-EVIDENCE-3-TIEU-CHI.md`](vong2/31-BO-SUNG-EVIDENCE-3-TIEU-CHI.md) §③.2a.

---

## 2. Phần đội tự xây — chỉ liệt kê thứ mở được trong repo này

| Thành phần | Đường dẫn | Nhãn | Bằng chứng |
|---|---|---|---|
| **SafetyGuard** — bộ luật G1/G2/G3 | [`vehicle-service/api/.../SafetyGuard.kt`](automotive/vehicle-service/api/src/main/java/com/sopa/viva_automotive/vehicleservice/api/SafetyGuard.kt) + [`impl/.../DefaultSafetyGuard.kt`](automotive/vehicle-service/impl/src/main/java/com/sopa/viva_automotive/vehicleservice/impl/DefaultSafetyGuard.kt) | `new` ⭐ | `SafetyGuardTest.kt`, `GuardedVehicleRepositoryTest.kt`, và ablation A1 |
| **Cưỡng chế guard ở biên repository** | [`GuardedVehicleRepository.kt`](automotive/vehicle-service/impl/src/main/java/com/sopa/viva_automotive/vehicleservice/impl/GuardedVehicleRepository.kt) | `new` ⭐ | Chặn **cả** đường giọng nói lẫn đường chạm — A1-02 |
| **Ablation A1** — counterfactual bỏ guard | [`SafetyGuardAblationTest.kt`](automotive/vehicle-service/impl/src/test/java/com/sopa/viva_automotive/vehicleservice/impl/ablation/SafetyGuardAblationTest.kt) | `new` ⭐ | [`evidence/ablation/a1-safety-guard-ablation.csv`](evidence/ablation/a1-safety-guard-ablation.csv) — trong bộ 9 ca, **6/6 ca nguy hiểm ghi được vào `MockVehicleRepository` khi bỏ guard; 3/3 ca đối chứng hợp lệ không đổi** |
| Lớp truy cập Vehicle Property | [`RealVehicleRepository.kt`](automotive/vehicle-service/impl/src/main/java/com/sopa/viva_automotive/vehicleservice/impl/RealVehicleRepository.kt), [`AreaIdResolver.kt`](automotive/vehicle-service/impl/src/main/java/com/sopa/viva_automotive/vehicleservice/impl/AreaIdResolver.kt) | `new` | `AreaIdResolver` xử lý mask area chồng nhau — lý do app khớp được với `seat.ROW_1_LEFT` của nền tảng |
| Allowlist quyền privileged | [`privapp-permissions-com.sopa.viva_automotive.xml`](automotive/app/privapp-permissions-com.sopa.viva_automotive.xml) | `new` | ⚠️ mới có 4/7 quyền manifest xin — xem mục 4 |
| Bảng đối chiếu property ↔ signal | [`docs/dbc/README.md`](docs/dbc/README.md) | `new` | Dữ liệu đầu vào là `provided`, bảng là sản phẩm của đội |
| UDS DTC Simulator | [`uds_dtc_simulator.py`](embedded/uds_dtc_simulator.py) | `new` | ⚠️ **Không dùng ở Vòng 2** (cắt 29/07, T10). Giữ cho Vòng 3 — barem Vòng 3 có dòng `(+05) Tích hợp đa dạng bài tập` |
| Safety scenario pack (Python) | [`test_safety_scenario_pack.py`](embedded/test_safety_scenario_pack.py) | `new` | 8 kịch bản. ⚠️ 4/8 (S5 quạt, S6 âm lượng, S7/S8 số) kiểm luật ở tầng Luau **ngoài đường sản phẩm** — xem mục 4 |

### VHAL Script Node của đội — trạng thái thật

| Thành phần | Đường dẫn | Nhãn | Ghi chú |
|---|---|---|---|
| `vhal_server.luau` | [`vhal_server.luau`](embedded/vhal_server.luau) | **KẾ HOẠCH — chưa từng chạy trên CarSky** | Không có trong blueprint; 8 script-node đang chạy đều là của nền tảng. Chức năng VHAL ↔ CAN mà nó nhắm tới **đã được `infotainment_gateway.lua` + `body_gateway.lua` cung cấp sẵn** |
| Guard G1 tại lớp gateway VHAL↔KUKSA | [`GATEWAY/IVI_GATEWAY.lua`](GATEWAY/IVI_GATEWAY.lua) | **`modified` — chưa xác nhận runtime** | +17 dòng trên bản `provided`: G1.1 chặn mở khoá cửa khi `Speed > 0`, G1.2 chặn nhiệt độ ngoài 16–32 °C, sửa `EV_BATTERY_LEVEL` `0x11600204` → `0x11600600`. Commit `c255ccc` 09/08. Chưa có pod log chứng minh đã chạy |
| Guard G1 tại lớp gateway CAN | [`GATEWAY/BCM_GATEWAY.lua`](GATEWAY/BCM_GATEWAY.lua) | **`modified` — chưa xác nhận runtime** | +33 dòng thêm / 4 dòng thay trên bản `provided`: G1.1/G1.2/G1.3 và `bind_door_actuate` chặn trước khi publish `DoorCommand`. Cùng commit |

> Thể lệ ghi rõ: *"Chạy lại hoặc đóng gói lại capability sẵn có không tự tạo
> Added Value cao"* và *"tự xây lại những gì đã có sẵn trong starter pack… không
> được cộng thêm điểm"*. Nên **không khai `vhal_server.luau` như phần team-owned
> quyết định core flow.** Phần quyết định thật của đội nằm ở tầng trên VHAL:
> voice → intent → SafetyGuard → (PropertyID, areaId, value). Nền tảng **không**
> có tầng đó.

---

## 3. Bảng M2 — intent ↔ Vehicle Property ↔ VSS ↔ CAN

Nguồn có thẩm quyền là [`vong2/03-contracts.md §0.2`](vong2/03-contracts.md). Bảng dưới
đã được **đối chiếu với metadata sống của nền tảng** ngày 08/08
([`evidence/carsky/signals-rest-0808/02-vss-m2-signals.json`](evidence/carsky/signals-rest-0808/02-vss-m2-signals.json)).

| Intent | Property ID | Area ID | Kiểu | Đường VSS (đã xác nhận trên broker) | CAN (đã xác nhận trên `bcm-can`) |
|---|---|---|---|---|---|
| `hvac_set_temp` | `0x15600503` = `358614275` | **`49` (0x31, SEAT_ZONE_DRIVER)** | `Float` 16.0–32.0 °C | `Vehicle.Cabin.HVAC.Station.Row1.Driver.Temperature` — float, actuator, Celsius | `HvacCommand/Driver_Temperature` [16,32] ↔ `HvacStatus/Driver_Temperature` [-40,80] |
| `hvac_set_fan` | `0x15400500` = `356517120` | `0` (GLOBAL) | `Int` 0–5 | `...Row1.Driver.FanSpeed` — uint8, actuator, **percent 0–100** | `HvacCommand/Driver_FanSpeed` [0,5] ↔ `HvacStatus/Driver_FanSpeed` |
| `door_lock` | `0x16200b02` = `371198722` | `1` (0x01, ROW_1_LEFT) | `Boolean` `true`=Lock | `Vehicle.Cabin.Door.Row1.DriverSide.IsLocked` — bool, **True = Locked** | `DoorCommand/Row1Driver_IsLocked` ↔ `DoorStatus/Row1Driver_IsLocked` |

**Quy đổi quạt** `percent = level × 20`: **nền tảng tự làm** trong
`infotainment_gateway.lua` (`fan_vhal_to_vss` / `fan_vss_to_vhal`). Đội không cần
hiện thực bước này — mục *"Cần xử lý"* #2 của `docs/dbc/README.md` coi như đã có
lời giải.

**Không thuộc bảng M2, không đi qua VHAL** (`03-contracts.md §0.1`):
`volume_adjust` → `CarAudioManager` · `media_*` → `MediaSession` · `delivery_*` → nội bộ app.

---

## 4. Ba việc còn mở — không được khai là đã xong

1. **Quyền privileged (M1a).** [`privapp-permissions-com.sopa.viva_automotive.xml`](automotive/app/privapp-permissions-com.sopa.viva_automotive.xml)
   khai 4 quyền; `AndroidManifest.xml` xin 7. Thiếu **`CAR_SPEED`** (SafetyGuard
   cần để đọc tốc độ), `CAR_ENERGY`, `CAR_INFO`; ngoài ra `RealVehicleRepository`
   còn cần `CAR_POWERTRAIN` cho `IGNITION_STATE` mà chưa khai ở đâu.
   Hệ quả đo được: ca **A1-04** trong ablation — không đọc được tốc độ thì guard
   từ chối mọi lệnh mở cửa với `G1_STALE_STATE`. An toàn, nhưng kịch bản demo
   *"xe dừng → xác nhận → mở cửa"* sẽ không chạy trên flavor `real`.

2. **Ngưỡng G1 khác nhau ở hai tầng.** `DefaultSafetyGuard` dùng `speed > 5 km/h`
   (đúng `03-contracts.md §4`); `vhal_server.luau` G1.1 dùng `speed > 0`. Không
   sai về an toàn (VHAL chặt hơn) nhưng phải khai là **phòng thủ nhiều lớp có
   chủ đích**, không để lộ ra như bất nhất.

3. **4/8 kịch bản của safety scenario pack nằm ngoài đường sản phẩm.**
   S6 (âm lượng) và S7/S8 (số) kiểm luật G1.4/G2.1 của `vhal_server.luau`, nhưng
   `volume_adjust` không đi qua VHAL và router không có intent nào về số. Ngoài ra
   `AUDIO_VOLUME = 0x11400901` không phải property AAOS chuẩn, và comment trong
   Luau ghi `289429249` trong khi giá trị thật của hex đó là `289409281`.
   **"8/8 PASS" phải kèm nhãn**: 4 ca là phòng thủ tầng VHAL, không phải kịch bản
   của core flow.

---

## 5. Bản 04/08 sai ở đâu — ghi lại để không lặp

Bản trước khai 4 hạng mục nhãn `new ⭐ 100% Team-owned` mà **không file nào tồn
tại trong repo này**, và mọi đường dẫn trỏ sang một thư mục local khác
(`E:\FPT Automotive - VIVA Project\android_cockpit\...`):

| Bản 04/08 khai | Thực tế |
|---|---|
| `VivaVendorCarService.kt` | ❌ không tồn tại — `VivaCarService` vẫn là **KẾ HOẠCH (M1)** |
| `IVivaVendorCarService.aidl` | ❌ không tồn tại |
| `privapp_permissions_viva.xml` | ❌ không tồn tại (file thật tên khác, package khác) |
| `SafetyGuard.kt` @ `com/viva/cockpit/` | ❌ sai đường — file thật ở `automotive/vehicle-service/api/` |
| `embedded/test_safety_scenario_pack.py` | ✅ đúng từ 20/08 — bốn script Python đã được gom về `embedded/`. Trước đó chúng nằm ở thư mục gốc nên đường dẫn này sai |
| DBC = `car_signals.dbc` | ⚠️ file placeholder đã chết (giả định `EngineData` cho xe EV). DBC thật là `body_can.dbc` / `powertrain_can.dbc` |
| `HVAC_TEMPERATURE_SET` Area ID = `0` | ❌ contract và code dùng `49` |
| VHAL Native Server = `modified` | ❌ chưa từng chạy trên CarSky; nền tảng đã có cầu VHAL↔KUKSA riêng |

Đây là loại lỗi mà ô *Minh bạch phạm vi demo* và *Nhận diện artifact* trừ điểm
nặng nhất: một giám khảo mở link theo manifest sẽ không thấy file, rồi đặt câu
hỏi về mọi con số khác trong bài. Bản này chỉ liệt kê thứ `git ls-files` xác
nhận có thật.

Hai artifact mà nhật ký 04/08 khai là bằng chứng —
`viva_safety_scenario_report.csv` và `viva_ablation_a1_report.csv` — là **output
sinh ra khi chạy test**. Chúng **không có trong bài nộp Vòng 2** vì thời điểm đó
chưa từng được commit. Từ 20/08 chúng được lưu lại dưới
[`evidence/ablation/safety-scenario-pack-report.csv`](evidence/ablation/safety-scenario-pack-report.csv)
và [`evidence/ablation/safety-scenario-pack-a1-python.csv`](evidence/ablation/safety-scenario-pack-a1-python.csv),
tái lập byte-for-byte bằng lệnh ở mục 6.

Bản A1 **chính thức** của đội vẫn là
[`evidence/ablation/a1-safety-guard-ablation.csv`](evidence/ablation/a1-safety-guard-ablation.csv)
— sinh từ đường sản phẩm Kotlin, không phải từ bộ Python này. Hai nguồn đó
**không được trộn vào nhau** khi trích dẫn số liệu.

---

## 6. Kiểm chứng không cần phần cứng

```powershell
# Ablation A1 — bo SafetyGuard
cd automotive
.\gradlew :vehicle-service:impl:testDebugUnitTest --tests "*SafetyGuardAblationTest*"

# Toan bo unit test JVM
.\gradlew test

# Safety scenario pack (Python) — CAN ep UTF-8 tren Windows
cd ..\embedded    # dang o automotive/ tu buoc tren
$env:PYTHONIOENCODING="utf-8"; python test_safety_scenario_pack.py
```

⚠️ Bốn script Python trong [`embedded/`](embedded/) **vỡ trên console Windows mặc định** (cp1258)
vì in emoji/dấu; phải đặt `PYTHONIOENCODING=utf-8`. Chúng cũng **chưa nằm trong
workflow CI nào** — CI hiện chỉ có `android-ci`, `asr-ci`, `backend-ci`.
