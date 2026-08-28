# 05 — VIVA Body: thực thi và tầng an toàn

> Code: `automotive/vehicle-service/api/`, `automotive/vehicle-service/impl/`,
> `automotive/feature/voice/…/domain/ExecuteVehicleControlUseCase.kt`.
> Contract: [`vong2/03-contracts.md`](../../vong2/03-contracts.md) §0.2, §4.

---

## 1. Bốn đường thực thi — chỉ MỘT đi tới CAN

| Nhóm intent | Đường đi thật | Qua VHAL? |
|---|---|---|
| `hvac_*`, `door_lock`, `cabin_lights` | App → `GuardedVehicleRepository` → `RealVehicleRepository` → `CarPropertyManager` → CarService → VHAL → (nền tảng) KUKSA → CAN → ECU | ✅ |
| `volume_adjust` | App → `AndroidVolumeController` / `CarAudioManager` | ❌ |
| `media_*` | App → `MediaBrowser`/`MediaController` → `VivaMediaBrowserService` → MediaSession/ExoPlayer | ❌ |
| `delivery_*` | App → `DeliverySkill` (in-memory, nội bộ app) | ❌ |
| `vehicle_status_*` | Đọc `VehicleStatus`, trả lời bằng giọng nói | (chỉ đọc) |

🔴 **Chỉ nhóm đầu tiên được claim "chạy full-stack tới CAN".** Khai gộp cả 16 intent
là sai — và đúng ô barem *Minh bạch phạm vi demo* / *Ranh giới và tính tương xứng*.

---

## 2. `SafetyGuard` — đặt ở đâu và vì sao

```kotlin
fun interface SafetyGuard {
    fun evaluate(request: VehicleWriteRequest, state: VehicleSafetyState): Verdict
}
```

Guard nằm ở module **`vehicle-service:api`**, được áp tại **biên `VehicleRepository`**
(`GuardedVehicleRepository` trong module `impl`), bọc **cả** `Mock` lẫn `Real` ở
DI boundary.

**Vì sao không đặt trên đường voice:** có **ba** nơi ghi property —
`ExecuteVehicleControlUseCase` (voice), `HvacViewModel` và `VehicleStatusViewModel`
(HMI). Guard chỉ chắn đường voice thì hai màn hình kia vẫn ghi thẳng, và câu *"không
lệnh nào tới xe mà không qua tầng an toàn"* trở thành lời khai sai — thứ chỉ cần mở
code ra là thấy.

⚠️ **Đây chưa phải tầng *service fw* mà mentor vẽ** (`03-contracts.md` §0.1). Nó là
chốt chặn một tầng **thấp hơn, trong app**. Khi `VivaCarService` (M1) có mặt thì
chuyển nguyên khối này vào trong đó — là việc **dời file**, không phải viết lại.
**Khai đúng như vậy** trong write-up.

⚠️ Guard này là **application guardrail**, **không phải** functional safety theo
ISO 26262.

### Kiểu dữ liệu

```kotlin
data class VehicleWriteRequest(
    val propertyId: Int,
    val areaId: Int,
    val value: Any,
    val confidence: Float? = null,                        // cua buoc nhan dang, neu tu giong noi
    val source: VehicleCommandSource = VehicleCommandSource.HMI,
    val isConfirmed: Boolean = false,
)

enum class VehicleCommandSource { HMI, VOICE, SYSTEM }

sealed class Verdict {
    object Allow
    data class Deny(val rule: String, val reasonVi: String, val suggestion: String?)
    data class Confirm(val rule: String, val questionVi: String)
}
```

⚠️ `VehicleSafetyState` khai `gear`, `parkingBrake`, `ignition` là **nullable** vì
`VehicleStatus` hiện **chưa đọc được** ba trường đó. Luật tương ứng **không kích
hoạt** khi thiếu dữ liệu — *"thà một luật im lặng vì thiếu dữ liệu, còn hơn một luật
chạy trên giá trị mặc định bịa ra"*.

---

## 3. Bộ luật — contract §4 vs. code thật

| Luật | Trong `03-contracts.md` §4 | Trong `DefaultSafetyGuard` |
|---|---|---|
| `G1_SPEED_LOCK` | `speedKph > 5` → Deny `door_lock(unlock)` | ✅ `MAX_UNLOCK_SPEED_KMH = 5f` |
| `G1_GEAR_LOCK` | `gear != "P"` → Deny | ✅ nhưng **chỉ chạy khi đọc được số** |
| `G2_CONFIRM_DOOR` | mở khoá luôn phải hỏi | ✅ (chỉ khi `source == VOICE` và chưa `isConfirmed`) |
| `G3_LOW_CONFIDENCE` | `confidence < 0.6` | ✅ `MIN_CONFIDENCE = 0.6f`, chỉ áp cho property nhạy cảm và lệnh từ giọng nói |
| `G3_VALUE_RANGE` | *(không có trong §4)* | ✅ **thêm** — `MIN_TEMPERATURE_C = 16.0`, `MAX_TEMPERATURE_C = 32.0`, chặn cả `NaN` |
| `G1_STALE_STATE` | `now - timestampNanos > 500ms` | 🟡 **chưa hiện thực đúng nghĩa** — hiện chỉ fail-closed khi `speedKmh` là `null`/không hữu hạn/âm |
| `G2_CONFIRM_DELIVERY` | luôn hỏi với `delivery_confirm` | ➡️ đã có ở `DeliverySkill`, không đi qua đường property |
| `G3_LLM_WHITELIST` | intent T2 ngoài whitelist → Deny | ➡️ thực thi bằng schema allowlist + `CoreIntentMapper` ở tầng planner ([04 §6](04-VIVA-BRAIN.md)) |
| `G3_MISSING_SLOT`, `G3_UNSUPPORTED` | | ➡️ đã có ở `GrammarIntentRouter` (tầng NLU) |

### Vì sao `G1_STALE_STATE` chưa làm

Cần so `timestampNanos` của snapshot với đồng hồ hiện tại. Dùng
`SystemClock.elapsedRealtimeNanos` thì test JVM không chạy được; dùng
`System.nanoTime` thì **không cùng gốc thời gian** với snapshot. Một phép kiểm "cũ hay
mới" chạy trên hai đồng hồ khác gốc sẽ **luôn** sai ở một phía — **và sai kiểu đó tệ
hơn là không kiểm**. Để nguyên cho tới khi `VivaCarService` cấp snapshot kèm mốc thời
gian cùng gốc.

### Vì sao thêm `G3_VALUE_RANGE`

Ablation A4 (04/08) cho thấy khi bỏ tầng grammar thì câu *"đặt nhiệt độ 40 độ"* đi
thẳng thành `SetTemperature(40.0)`. **Miền giá trị hợp lệ không nên chỉ được giữ bởi
một tầng** — đây là lớp chắn thứ hai ngay trước khi ghi xuống xe.

### Hai chi tiết dễ bỏ sót

1. **Chỉ chặn MỞ khoá** (`value == false`). Khoá cửa lúc đang chạy là hành động an
   toàn — chặn nó thì vô lý.
2. **Câu hỏi xác nhận phải nói luôn cách trả lời** — xem [04 §7](04-VIVA-BRAIN.md).

---

## 4. Bảng dịch intent → PropertyID → VSS → CAN (M2)

Nguồn hằng số: `vehicle-service/api/…/VehicleProperties.kt`.
VSS/CAN: `docs/dbc/`.

| Intent + slots | PropertyID · areaId · kiểu | Đường VSS | CAN command ↔ status |
|---|---|---|---|
| `hvac_set_temp` · `value: Float` (`zone` thiếu ⇒ `DRIVER`) | `HVAC_TEMPERATURE_SET = 358614275` · `SEAT_ZONE_DRIVER = 0x1` · `Float` °C · chỉ nhận `16.0..32.0` | `Vehicle.Cabin.HVAC.Station.Row1.Driver.Temperature` — float, °C, truyền thẳng | `HvacCommand.Driver_Temperature` (`BO_ 256`, bit 0, 16-bit, scale 0.1, 16–32 °C) ↔ `HvacStatus.Driver_Temperature` (`BO_ 257`, nhiệt độ **thực** −40..80 °C) |
| `hvac_set_fan` · `level: Int` | `HVAC_FAN_SPEED = 356517120` · `GLOBAL = 0` · `Int` `0..5` | `…Row1.Driver.FanSpeed` — uint8, **percent 0..100** | `HvacCommand.Driver_FanSpeed` (`BO_ 256`, bit 32, 8-bit, 0–5) ↔ `HvacStatus.Driver_FanSpeed` (`BO_ 257`) |
| `door_lock` · `lock: Boolean` | `DOOR_LOCK = 371198722` · `DOOR_ROW_1_LEFT = 0x1` · `Boolean`, `true = lock` | `Vehicle.Cabin.Door.Row1.DriverSide.IsLocked` — bool, truyền thẳng | `DoorCommand.Row1Driver_IsLocked` (`BO_ 528`) ↔ `DoorStatus.Row1Driver_IsLocked` (`BO_ 769`) |
| `cabin_lights` · `on: Boolean` | `CABIN_LIGHTS_SWITCH = 289410818` (ghi) / `CABIN_LIGHTS_STATE = 289410817` (chỉ đọc) | — | — |

🔴 **Gateway bắt buộc quy đổi FanSpeed:** `percent = level × 20`, chiều về
`level = round(percent / 20)`. Đây là lý do contract bắt quy đổi — VSS dùng percent,
CAN dùng mức 0–5.

### `areaId` — bẫy `0x31` vs `0x1`

`LEGACY_SEAT_ZONE_DRIVER = 0x31` còn tồn tại vì một số tài liệu CarSky dùng mask cũ.
App **tự chuẩn hoá `0x31` → `0x1`** qua `AreaIdResolver`. Trên emulator AAOS gốc, ưu
tiên area `1` (`ROW_1_LEFT`):

```bash
adb shell cmd car_service get-property-value 358614275 1
```

### Ba luật tích hợp bắt buộc

1. **`CoreIntentMapper` là ranh giới dịch kiểu duy nhất.** Slot thiếu, sai kiểu,
   `NaN` hoặc ngoài range phải **dừng ở đây**; không tự gán một lệnh xe mặc định.
2. **`door_lock` v1 chỉ tác động cửa tài xế.** Muốn cả bốn cửa phải tạo quyết định
   mới và fan-out bốn write; **không được đổi ngầm ý nghĩa** của intent hiện tại.
3. **Chỉ phát TTS "Đã…" sau khi service trả `Applied`.** Với nhiệt độ, nói *"Đã đặt
   nhiệt độ mục tiêu 24°C"* — **không** nói cabin *đã đạt* 24°C, vì signal status là
   nhiệt độ thực và cần thời gian thay đổi.

---

## 5. Hai flavor

| Flavor | Backend xe | Dùng cho |
|---|---|---|
| `mock` (mặc định) | Simulator trong bộ nhớ (sóng tốc độ, hội tụ nhiệt độ, hao năng lượng) | Emulator, unit test, làm UI |
| `real` | `CarPropertyManager` qua VHAL | AAOS Device / Automotive emulator image |

⚠️ **Flavor `mock` không bao giờ đồng bộ với system bar HVAC của AAOS** — có chủ đích:
mock nói chuyện với simulator trong bộ nhớ, system bar nói chuyện với VHAL thật.

### Quyền privileged của flavor `real`

`CONTROL_CAR_CLIMATE`, `CONTROL_CAR_DOORS`, `CONTROL_CAR_INTERIOR_LIGHTS`,
`CAR_SPEED`, `CAR_ENERGY`, `CAR_INFO`, `CAR_POWERTRAIN`.

Trên **emulator AAOS**: cần cài privileged/platform-signed + allowlist
`app/privapp-permissions-com.sopa.viva_automotive.xml` trong `/system/etc/permissions/`.

```bash
adb push app/build/outputs/apk/real/debug/app-real-debug.apk /system/priv-app/VivaAutomotive/VivaAutomotive.apk
```

🔴 **Đừng `adb push` APK mới đè `/system/priv-app/…` rồi mở app mà không reboot** —
PackageManager giữ mapping cũ và crash ngay lúc khởi tạo process:
`NullPointerException: Resources.getConfiguration()`.

✅ Trên **Device CarSky** thì khác: `CAR_SPEED` là `prot=dangerous` → `pm grant` được,
**không cần privileged install** (evidence:
`evidence/c2/car-speed-permission-probe-0818.txt`).

Thiếu `CAR_SPEED` thì SafetyGuard **không đọc được tốc độ** và mở cửa **fail closed**
với `G1_STALE_STATE`.

---

## 6. 🔴 Readback qua `CarPropertyManager` đang bị chặn

Không phải lỗi app. Image AAOS của node skycraft khai:

```
ro.vendor.vehiclehal.server.use_local_fake_server = true
```

VHAL client nối vào fake server nội bộ (`cid=1`, port 9210), **không bao giờ nối tới
IVI Gateway** — trong khi gateway push đúng
(`[igw] → vhal 0x11600207 area=0x0 pushed = 16.6666`).

⇒ Mọi mốc E06–E08 (readback property) chưa đạt được **vì lý do ngoài đội**.
Chi tiết và câu hỏi gửi BTC: [`docs/carsky/09`](../carsky/09-SU-CO-VA-GIOI-HAN.md).

---

## 7. Ablation — cách chứng minh guard có tác dụng

`vong2/23-N4-ABLATION.md` định nghĩa A1/A2/A3; test
`vehicle-service/impl/src/test/…/ablation/SafetyGuardAblationTest.kt` hiện thực.

**A1 — bỏ guard:** 6/9 lệnh nguy hiểm ghi được xuống repository.

Bảng before/after ra được bằng **một câu group-by trên CSV** của harness, vì verdict
có mã luật trong log (`Deny:G1_SPEED_LOCK`) chứ không phải `Deny` trơn — xem
[07 §3](07-BACKEND-HARNESS.md).

---

## 8. Nền tảng CarSky cũng có guard riêng — đừng lẫn

`GATEWAY/README.md` §3: script Lua của nền tảng có sẵn G1.1 (chặn mở khoá khi
`Vehicle.Speed > 0`), G1.2 (giới hạn 16–32 °C), G1.3 (quạt 0–5).

🔴 Ngưỡng khác của đội (`> 5 km/h` vs `> 0`), và **đó là code của nền tảng, không phải
của VIVA**. Khi trình bày phải tách hai thứ.
