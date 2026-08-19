# BỔ SUNG EVIDENCE — ba khối BTC yêu cầu làm rõ

> **Ngày:** 12/08/2026 · **Người lập:** Long · **Gửi:** BTC + mentor ThuyDH
> **Phạm vi:** đúng ba khối ③ ④ ⑤ của barem Vòng 2. Không mở claim mới, không
> thêm tính năng. Mỗi dòng chỉ trỏ tới thứ `git ls-files` xác nhận có thật.
>
> **Quy tắc của bản này:** nghi ngờ thì hạ một bậc. Thành phần chưa có log chạy
> trên nền tảng thì ghi rõ "chưa xác nhận runtime", không viết như đã chạy.
> Ba nhãn trạng thái dùng theo `24-N5-TRANG-THAI-INTEGRATION.md`.

---

## 0. Đội xác nhận trước phần khoảng trống

Nhận xét gốc của mentor — *"car framework, vhal và climate control unit đội vẫn
chưa phát triển được"* — là **đúng** với ba hạng mục sau. Đội không tranh luận
phần này, và nó trùng với chính sổ bằng chứng nội bộ
([`18-CLAIM-EVIDENCE-MAP.md`](18-CLAIM-EVIDENCE-MAP.md) — claim **C-PLATFORM đang ĐỎ**):

| Hạng mục | Trạng thái thật | Căn cứ |
|---|---|---|
| `VivaCarService` — service framework riêng + AIDL | **CHƯA XÂY** | [`VHAL_BASELINE_MANIFEST.md`](../VHAL_BASELINE_MANIFEST.md) §5. Lớp vehicle-property hiện là module thư viện trong app, không phải system service |
| `setProperty` → readback trên Device CarSky | **CHƯA CÓ BẰNG CHỨNG** | E06/E07/E08 trong evidence register vẫn trống |
| CCU (climate control unit) | **KHÔNG DO ĐỘI PHÁT TRIỂN** | `Climate ECU` đang chạy là `climate_ecu.lua` của nền tảng; bản trong [`GATEWAY/`](../GATEWAY/) giống hệt từng byte |
| `vhal_server.luau` | **CHƯA TỪNG CHẠY** trên CarSky | Đã ghi nhãn KẾ HOẠCH từ 08/08, không tính là tính năng |

Vì vậy đội **không** claim luồng `App → VHAL → CAN → CCU`.

Phần dưới đây là những gì đội **có** và cho rằng chưa hiện ra trong bài nộp.

---

## ③ GIÁ TRỊ TĂNG THÊM VÀ PHẦN TEAM-OWNED — 25đ

### ③.1 Xác lập baseline đúng (3đ)

Baseline Manifest: [`VHAL_BASELINE_MANIFEST.md`](../VHAL_BASELINE_MANIFEST.md), đọc trực tiếp từ
bản export blueprint [`backend/carsky/blueprint-VIVA-deploy-backup.json`](../backend/carsky/blueprint-VIVA-deploy-backup.json)
chứ không viết theo trí nhớ.

Nền tảng cấp sẵn và đội **không** nhận là của mình: 8 Script Node Lua, Device
AAOS 14, KUKSA Data Broker (1.268 tín hiệu VSS), 2 CAN bus (33 tín hiệu trên
`bcm-can`), GPIO panel, DBC và catalog VSS.

Manifest có riêng §5 liệt kê **8 hạng mục bản trước khai sai** và đã bị hạ nhãn —
đội tự công bố lỗi của mình thay vì để giám khảo phát hiện.

### ③.2 Tách phần team-owned — `provided / configured / modified / new` (5đ)

**Đây là ô đội khai thiếu trong bài nộp.** Cột `modified` bị bỏ trống, dẫn tới
nhận xét "chỉ có ứng dụng tầng trên".

| Nhãn | Thành phần | Bằng chứng |
|---|---|---|
| `provided` | 8 Script Node, KUKSA, CAN, DBC, VSS, Device | Blueprint export |
| `configured` | Blueprint bản clone của đội — thêm node `VIVA ASR` | [`evidence/carsky/v7-manifest.txt`](../evidence/carsky/v7-manifest.txt) |
| **`modified`** | **`IVI_GATEWAY.lua` và `BCM_GATEWAY.lua`** — xem ③.2a | Commit `c255ccc`, diff bên dưới |
| `new` | `SafetyGuard`, `GuardedVehicleRepository`, `AreaIdResolver`, `RealVehicleRepository`, bảng M2, allowlist privapp, toàn bộ voice pipeline tiếng Việt | Xem ③.3 và ③.5 |

#### ③.2a Phần `modified` — đội có sửa lớp gateway của nền tảng

Commit `c255ccc` (09/08, **có trên `origin/main`** và là tổ tiên của snapshot
`feature/automotive@5cb9b31` ghi trong báo cáo) đưa 8
script Lua vào [`GATEWAY/`](../GATEWAY/). So từng byte với bản trong blueprint export:

| File | Khác biệt | Nội dung sửa |
|---|---|---|
| [`GATEWAY/IVI_GATEWAY.lua`](../GATEWAY/IVI_GATEWAY.lua) | **+19 dòng thêm / 4 dòng thay** | Khối chặn an toàn **17 dòng** đặt ngay tại đường `pins.vhal → actuate_kuksa`: **G1.1** từ chối mở khoá cửa khi `Vehicle.Speed > 0`; **G1.2** chặn nhiệt độ ngoài dải 16–32 °C. Hai dòng còn lại **sửa một property ID sai của bản gốc**: `EV_BATTERY_LEVEL` `0x11600204` → `0x11600600` theo `VehiclePropertyIds` của AOSP |
| [`GATEWAY/BCM_GATEWAY.lua`](../GATEWAY/BCM_GATEWAY.lua) | **+33 dòng thêm / 4 dòng thay** | **G1.1/G1.2/G1.3** ở lớp CAN; thêm biến theo dõi `current_vehicle_speed` và hàm `bind_door_actuate` thay `bind_bool_actuate` (4 dòng thay) để chặn lệnh mở cửa **trước khi** publish khung `DoorCommand` xuống Body CAN |
| `VCU.lua` | 16 dòng, **không có thay đổi chức năng** | Chỉ khác dấu tiếng Việt và ký tự đặc biệt trong comment/log. Đội **không** khai là đóng góp |
| 5 file còn lại | **0 dòng** | Giữ nguyên của nền tảng |

Ý nghĩa kỹ thuật: đây là **phòng thủ nhiều lớp có chủ đích**. Cùng một luật G1
tồn tại ở hai tầng độc lập — `DefaultSafetyGuard` trong app (ngưỡng `speed > 5`
theo [`03-contracts.md`](03-contracts.md) §4) và gateway Lua (ngưỡng `speed > 0`, chặt hơn).
Theo thiết kế, lớp gateway có thể chặn cả lệnh **không đi qua app của đội**;
khả năng này mới được xác nhận ở source, chưa được xác nhận bằng runtime log.

> ⚠️ **Giới hạn phải nói kèm:** đội mới xác nhận được phần **source có trong
> commit đã nộp**. Chưa có log runtime từ nền tảng in ra dòng
> `[SAFETY GUARD G1.1 BLOCKED]`. Nhãn đúng là `modified — chưa xác nhận runtime`,
> **không** phải "đã chạy trên CarSky".

> 📌 **Vì sao mentor không thấy phần này:** [`VHAL_BASELINE_MANIFEST.md`](../VHAL_BASELINE_MANIFEST.md)
> lập ngày **08/08** ghi cả 8 script là `provided` kèm câu *"đội không sửa một
> dòng nào"* — đúng tại thời điểm lập, nhưng phần sửa đến ngày **09/08** và tài
> liệu không được cập nhật lại. Tài liệu của đội đã tự làm mờ đóng góp của chính
> mình. Đã bổ sung đính chính có ghi ngày vào manifest.

### ③.3 Mức quyết định của phần team-owned — ô 6đ, L3 đòi ablation

Đây là ô nặng nhất của khối, và là chỗ đội có bằng chứng mạnh nhất.

**Ablation A1 — bỏ `SafetyGuard`:**
[`evidence/ablation/a1-safety-guard-ablation.csv`](../evidence/ablation/a1-safety-guard-ablation.csv) ·
[`a1-run-manifest.txt`](../evidence/ablation/a1-run-manifest.txt)

| Ca | Tình huống | Có guard | Bỏ guard |
|---|---|---|---|
| A1-01 | Mở khoá cửa khi xe chạy 60 km/h (**giọng nói**) | `Deny:G1_SPEED_LOCK` | `Allow` → **ghi vào `MockVehicleRepository`** |
| A1-02 | Mở khoá cửa khi xe chạy 60 km/h (**chạm HMI**) | `Deny:G1_SPEED_LOCK` | `Allow` → **ghi vào `MockVehicleRepository`** |
| A1-03 | Mở khoá cửa khi đứng yên, chưa xác nhận | `Confirm:G2_CONFIRM_DOOR` | `Allow` → **ghi vào `MockVehicleRepository`** |
| A1-04 | Mở khoá cửa khi **không đọc được tốc độ** | `Deny:G1_STALE_STATE` | `Allow` → **ghi vào `MockVehicleRepository`** |
| A1-05 / A1-06 | Đặt nhiệt độ 40 °C / 5 °C (ngoài dải 16–32) | `Deny:G3_VALUE_RANGE` | `Allow` → **ghi vào `MockVehicleRepository`** |
| A1-OK-07/08/09 | Ba lệnh hợp lệ | `Allow` | `Allow` — **không đổi** |

**Kết quả: trong bộ 9 ca, cả 6 ca nguy hiểm đều ghi được vào repository mô
phỏng khi bỏ guard; 3 ca đối chứng hợp lệ không đổi.** Đây đúng dạng
counterfactual mà L3 đòi:
bỏ phần mới thì claim an toàn sụp, chứ không phải suy giảm mơ hồ.

Điểm kiến trúc làm A1-02 có ý nghĩa: guard được cưỡng chế tại **biên
repository** ([`GuardedVehicleRepository.kt`](../automotive/vehicle-service/impl/src/main/java/com/sopa/viva_automotive/vehicleservice/impl/GuardedVehicleRepository.kt)),
không phải trong UI. Nên nó chặn cả đường chạm màn hình, không chỉ đường giọng nói.

**Ablation A4 — bỏ grammar router:**
[`evidence/ablation/a4-grammar-ablation.csv`](../evidence/ablation/a4-grammar-ablation.csv)

**Tái lập không cần phần cứng:**
```powershell
cd automotive
.\gradlew :vehicle-service:impl:testDebugUnitTest --tests "*SafetyGuardAblationTest*"
```

### ③.4 Lợi ích so với baseline + trade-off (7đ)

Baseline CarSky **không có** lớp nào nằm giữa câu nói và Vehicle Property. Cụ thể
starter pack không có ASR tiếng Việt, không có voice pipeline, không có TTS tiếng
Việt, và không có bảng chính sách intent → `(propertyId, areaId, value)`.

| So sánh | Baseline | Có phần team-owned |
|---|---|---|
| Sáu ca nguy hiểm trong bộ A1 | 6/6 ghi vào repository mô phỏng | 0/6 ghi vào repository mô phỏng |
| Ba ca đối chứng hợp lệ | 3/3 được thực thi | 3/3 được thực thi |
| Kiểm chứng tự động | không có | **258 test JVM, 0 fail / 0 error / 0 skip** — [`evidence/c2/artifact-identity-ci.txt`](../evidence/c2/artifact-identity-ci.txt), sinh từ CI có commit identity |

**Trade-off đội tự nêu** (L3 yêu cầu nêu trade-off, không chỉ nêu lợi ích):
ca **A1-04** cho thấy khi thiếu quyền `CAR_SPEED`, guard từ chối **mọi** lệnh mở
cửa với `G1_STALE_STATE` — kể cả lệnh hợp lệ lúc xe đứng yên. An toàn được ưu
tiên hơn tính sẵn sàng. Đây là lựa chọn có chủ đích, nhưng nó làm kịch bản demo
*"xe dừng → xác nhận → mở cửa"* không chạy được trên flavor `real` nếu allowlist
chưa đủ quyền.

### ③.5 Khác biệt có ý nghĩa đối với use case (4đ)

L0 của ô này là *"chủ yếu reskin, packaging hoặc tái triển khai hành vi đã có"*.
Đội đối chiếu thẳng:

- Nền tảng **không** có ASR/TTS tiếng Việt và **không** có voice pipeline → phần
  này không thể là reskin.
- Nền tảng **có sẵn** cầu VHAL ↔ KUKSA ↔ CAN. Đội **không** xây lại — và đã ghi
  rõ trong manifest rằng `vhal_server.luau` không được khai là team-owned, đúng
  điều khoản *"tự xây lại những gì đã có sẵn trong starter pack không được cộng
  điểm"*. Đóng góp của đội ở lớp này là **sửa có mục đích** (③.2a), không phải
  viết lại.
- Phần quyết định của đội nằm ở tầng nền tảng không có: câu nói → intent →
  chính sách an toàn tất định → `(PropertyID, areaId, value)`.

---

## ④ PLATFORM UTILIZATION — 15đ

### ④.1 Đường align hệ sinh thái theo track (5đ) — cổng cứng CarSky

Thể lệ: *"để đạt từ L2, đội cần chứng minh core flow của implementation chạy
trên CarSky"*. Đội khai đúng mức đạt được, không khai vượt:

| Phần core flow | Chạy trên CarSky? | Bằng chứng |
|---|---|---|
| NLU → media (MediaSession/ExoPlayer) | **CÓ** | [`evidence/c2/carsky-runtime-20260809/`](../evidence/c2/carsky-runtime-20260809/) |
| Container ASR của đội chạy như node trong room | **CÓ** | [`evidence/carsky/v7-manifest.txt`](../evidence/carsky/v7-manifest.txt) |
| mic → VAD → ASR trên Device | chưa | — |
| `setProperty` → VHAL → CAN | chưa | E06/E07/E08 trống |

### ④.2 Độ sâu tích hợp vào core flow (4đ)

L3 = *"bỏ đường tích hợp thì workflow chính thất bại"*.

- **Đạt** cho đường media: voice Agent điều khiển qua `MediaBrowserCompat` /
  `MediaControllerCompat` → `VivaMediaBrowserService` / MediaSession của AAOS.
  Bỏ MediaSession thì lệnh media không còn tác dụng.
- **Chưa đạt** cho đường vehicle-control: `RealVehicleRepository` có mã gọi
  `CarPropertyManager.getProperty`/`setProperty` và có xử lý `SecurityException`,
  nhưng chưa chạy trên Device.

### ④.3 Evidence từ platform (4đ) — *"log/trace/output từ CarSky"*

**(a) Vòng ghi → đọc lại thật trên KUKSA broker qua REST** —
[`evidence/carsky/signals-rest-0808/`](../evidence/carsky/signals-rest-0808/)

```
T0  19:34:34Z  POST /values   → Driver.Temperature = null  (ts 2026-08-05T14:30:56Z)
T1  19:34:36Z  POST /actuate  {"value":24.0} → 200 {"ok":true,"sent":1}
T2  19:34:54Z  POST /values   → Driver.Temperature = 24    (ts 2026-08-07T19:34:36Z)
```

Giá trị đổi và **timestamp nhảy đúng thời điểm ghi**. Room `22/22 node
phase=Running`. Kèm theo: bảng M2 được đối chiếu bằng **chính metadata của nền
tảng** (1.268 tín hiệu VSS, 33 tín hiệu CAN trên `bcm-can`, 3 GPIO), xác nhận
được ba chi tiết mà tài liệu đội tự viết không thể tự xác nhận: `FanSpeed` trên
VSS là **percent 0–100** (nên contract phải quy đổi `level × 20`),
`Door.IsLocked` polarity **True = Locked**, và `vcu/Speed` là **actuator** nên
đặt được tốc độ cho ablation qua REST.

Bản thân file manifest cũng liệt kê **3 chỗ tài liệu của đội ghi sai** về API nền
tảng, đã kiểm lại bằng `openapi.json` và sửa (route là `POST` không phải `GET`;
`nodeKey` không phải UUID; họ `/signals` có 9 route không phải 2).

**(b) Runtime trên Device CarSky** —
[`evidence/c2/carsky-runtime-20260809/`](../evidence/c2/carsky-runtime-20260809/)

APK do đội build, **SHA-256 khớp cả ba đầu** (bản local, artifact `viva-apk`,
`base.apk` đã cài) trên Device `VIVA` (`trout_arm64`, Android 14 / SDK 34):

```
VIVA_TRACE_SUMMARY|3fa9c6df-…|phát nhạc |media_play |Allow  → MediaSession PLAYING
VIVA_TRACE_SUMMARY|a92f5f4c-…|dừng nhạc |media_pause|Allow  → PAUSED @ 3124 ms
VIVA_TRACE_SUMMARY|3cf25f75-…|chuyển bài|media_next |Allow  → activeItemId 0 → 1
```

### ④.4 Ranh giới và tính tương xứng (2đ)

Đội tự nêu giới hạn của chính bằng chứng mình nộp:

1. Vòng ghi/đọc KUKSA ở ④.3(a) là REST gọi **thẳng vào broker** — không có APK,
   không có VHAL, không có SafetyGuard trên đường đó. Nó là **công cụ đo**,
   không phải sản phẩm. Ghi VSS từ ngoài **không** lan xuống CAN (đã thử: SSE
   `bcm-can` 20 giây chỉ nhận `ping`, không frame nào).
2. Bằng chứng ④.3(b) là **flavor mock**, không chứng minh VHAL/CAN/CCU hay quyền
   privileged.
3. Device dùng lấy evidence là **máy ảo Cuttlefish** (`CUTTLEFISHCVD01`,
   `aosp_trout_arm64`), không phải cabin hay xe thật. Mọi audio đi đường này được
   khai là *thu ngoài rồi phát lại*, không gọi là "đo trong cabin".
4. Không đánh đồng Script Node / Container Node với ECU hay vECU thật.

Bảng ba trạng thái đầy đủ: [`24-N5-TRANG-THAI-INTEGRATION.md`](24-N5-TRANG-THAI-INTEGRATION.md).

---

## ⑤ NGƯỜI DÙNG, KHÁCH HÀNG VÀ KHẢ NĂNG TRIỂN KHAI — 10đ

Nguồn: [`12-PRODUCT-INTEGRATION-CARD.md`](12-PRODUCT-INTEGRATION-CARD.md). Năm ô, 2đ đều nhau.

| Ô | Nội dung đội đã trả lời |
|---|---|
| **Người dùng và người quyết định** | **User** = tài xế (ưu tiên tài xế Việt Nam và tài xế giao vận). **Buyer** = OEM/Tier-1 sở hữu roadmap Digital Cockpit — người cấp quyền privileged VHAL, duyệt an toàn và chịu trách nhiệm phát hành. **Process owner** = quản lý vận hành/an toàn đội xe cho pilot giao vận. Ranh giới nêu rõ: tài xế dùng nhưng **không** phải người cấp quyền VHAL hay quyết định tích hợp |
| **Offering và quan hệ tiếp nhận** | Gói phần mềm tích hợp AAOS (VIVA Agent + `GuardedVehicleRepository` + media boundary chuẩn AAOS + contract M2 và bộ test/trace). Quan hệ **B2B2C**: đội cấp module + integration kit; OEM/Tier-1 tích hợp, platform-sign, kiểm thử, phát hành |
| **Outcome và giả thuyết áp dụng** | 4 giả thuyết H1–H4 cho 3 nhóm đối tượng, **được ghi nhãn rõ là giả thuyết chưa đo**, không phải kết quả. Ví dụ H3: một intent vehicle-control mới thêm được bằng mapping + policy + test, không sửa VHAL và không để LLM sinh trực tiếp PropertyID |
| **Tích hợp và phụ thuộc bên ngoài** | Bảng 12 điểm nối, mỗi dòng gắn nhãn **THẬT / MÔ PHỎNG / KẾ HOẠCH** kèm giới hạn. `VivaCarService` và VHAL trên CarSky được khai đúng nhãn **KẾ HOẠCH**; CCU khai **MÔ PHỎNG** |
| **Bước kiểm chứng tiếp theo** | Xem mục 6 dưới đây |

Thể lệ ghi rõ khối này *"không bắt buộc có TAM, pricing, LOI hoặc tích hợp đối
tác thật"* — đội không dựng business case, chỉ trả lời đúng 5 ô.

---

## 6. Bước kiểm chứng tiếp theo và rào cản lớn nhất

**Rào cản lớn nhất — nêu thẳng:** quyền `android.car.permission.CONTROL_CAR_*`
là privileged. APK thường bị từ chối `setProperty`. Không vượt được rào này thì
không có readback property, và cả ba khoảng trống ở mục 0 đều không đóng được.

Thứ tự đóng, đúng theo thứ tự mentor chỉ ở kick-off 30/07:

| # | Việc | Đóng được gì | Đầu ra kiểm chứng |
|---|---|---|---|
| 1 | Hoàn thiện allowlist (hiện còn thiếu `CAR_SPEED`, `CAR_ENERGY`, `CAR_INFO` so với manifest), cài APK vào `/system/priv-app` và platform-sign theo quy trình OEM | Rào cản trên | Quyền được cấp thật; `setProperty` trả thành công thay vì `SecurityException` |
| 2 | Dựng `VivaCarService` — Service + AIDL, giữ một kết nối `Car`/`CarPropertyManager`, fan-out callback | "chưa có car framework" | Service chạy, app HVAC/DOOR nối qua AIDL |
| 3 | Readback 3 property | "chưa có vhal" | E06 `358614275/49`, E07 `356517120/0`, E08 `371198722/1` |
| 4 | Chạy bản gateway đã sửa và bắt log | Nâng ③.2a lên "đã chạy" | Pod log có `[SAFETY GUARD G1.1 BLOCKED]` |
| 5 | Một lượt mic thật trên Device | Đóng ASR/latency | Trace có `speech_start → asr_done → nlu_done → tts_start` |

Cách đọc lại để chứng minh lệnh của app thật sự chạm KUKSA đã có sẵn: ghi từ app
→ `POST /signals/{room}/central-broker-vss/values` đọc lại từ ngoài, đúng cách đã
làm ở ④.3(a).

> **Ranh giới snapshot:** commit `5cb9b31` chỉ có trên `feature/automotive`,
> không có trên `origin/main`. Nếu source locator của bài nộp trỏ tới `main`,
> mọi thay đổi chỉ có ở `5cb9b31` phải được loại khỏi claim hoặc bài nộp phải
> trỏ chính xác tới commit/branch đó. Riêng gateway commit `c255ccc` đã có trên
> `origin/main`.
