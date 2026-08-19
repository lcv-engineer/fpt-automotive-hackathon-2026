# 32 — Enhance khối ④ Platform utilization: phân tích sâu từng ý phản hồi

> **Chủ sở hữu:** Vĩ · viết 18/08/2026 · nguồn phản hồi: bảng chấm khối ④ (6/15).
> Tài liệu này KHÔNG lặp lại `24-N5`. Nó trả lời đúng ba khoản trừ, bằng những
> thứ đọc được từ blueprint đang chạy và từ API nền tảng ngày 18/08.
>
> Mọi phát hiện trong PHẦN 0 đều lấy từ lệnh thật, có file evidence kèm theo.
> Chỗ nào là **suy luận chưa chạy thử** thì ghi rõ `⚠️ SUY LUẬN`.

Điểm hiện tại: align 2/5 · độ sâu 0/4 · evidence 2/4 · ranh giới 2/2 = **6/15**.

---

## PHẦN 0 — BẢY PHÁT HIỆN NGÀY 18/08, TRƯỚC KHI BÀN ĐIỂM

Tất cả lấy từ room demo `v37aa3knc6t1embelr5yi`, blueprint đang deploy
`6deadb05-c856-4dab-976b-432b0fac0658` (`VIVA-deploy-clone-0803`, deployment
`VIVA-demo-0808`). Toàn bộ là lệnh GET đọc-only, không đổi trạng thái room.

### F1 — `/logs/{node}` CHẠY ĐƯỢC, thiếu đúng một tham số không có trong spec

`docs/backend-docs/carsky-api.md` §5 ghi *"/logs/{node} không phải logcat… lấy về
là log WebRTC"*. Nhận xét đó đúng cho node **skycraft**, và đã bị suy rộng sai ra
cả họ endpoint.

```
GET /deployments/{room}/logs/{node}                   -> 502
GET /deployments/{room}/logs/{node}?container=user    -> 200  <- log ứng dụng
GET /deployments/{room}/logs/{node}?container=sidecar -> 200
```

502 không phải Conduit. Upstream nói thẳng: *"a container name must be specified
for pod …, choose one of: [user sidecar]"*. `openapi.json` chỉ khai `tail` và
`since`; `container` là tham số **không được tài liệu hoá**.

- Node **container** (VIVA ASR): có cả `user` và `sidecar`. `user` = stdout tiến trình.
- Node **script-node** (IVI Gateway): **không có** `user`, chỉ `sidecar`.

Log lấy được từ node VIVA ASR:

```
INFO:     Uvicorn running on http://0.0.0.0:8080
2026-08-12 06:34:48,285 INFO viva.asr VIVA_ASR model ready in 1424 ms: phowhisper-tiny-int8
```

Đúng logger sinh ra `VIVA_ASR|<trace_id>|ok|server_ms=…` (`asr/app/main.py:162`).

**Ba giới hạn:** (a) log chỉ sống theo vòng đời pod — pod hiện tại khởi động
2026-08-12 06:34:48 UTC, log phiên 10/08 đã mất; (b) Loki `/logs/{node}/search`
trả 200 nhưng `result: []` với **mọi** node đã thử, mọi khoảng thời gian — không
có log lịch sử; (c) log lua của script-node **không** nằm trong stream `sidecar`
(sidecar chỉ khai `registered log-source part 'ivi-gateway-main'`), tức dòng
`vhal->vss:` của gateway chỉ xem được trong UI CarSky.

Evidence: `evidence/carsky/asr-node-logs-0818/`.

### F2 🔴 — VHAL pin CHỈ BẮC CẦU 15 PROPERTY, và HAI TRONG BA INTENT M2 KHÔNG CÓ TRONG ĐÓ

Node `IVI - Android` (skycraft) có pin `vhal` khai danh sách property được bắc cầu.
Bản backup và bản live **giống hệt nhau**, 15 mục:

| propId | hex | nhóm | areaIds | là gì |
|---|---|---|---|---|
| 557843456 | `0x21400400` | VENDOR | [0] | `HU_HVAC_FAN_SPEED` |
| 559940617 | `0x21600409` | VENDOR | [0] | `HU_HVAC_TEMPERATURE_DRIVER` |
| 559940618 | `0x2160040A` | VENDOR | [0] | `HU_HVAC_TEMPERATURE_PASSENGER` |
| 555746305-309 | `0x212004xx` | VENDOR | [0] | AC / recirc / auto / defrost |
| 557843463-464 | `0x2140040x` | VENDOR | [0] | seat warmer |
| 392168201 | `0x17600309` | SYSTEM | [1,2,4,8] | `TIRE_PRESSURE` |
| 354421634 | `0x15200B82` | SYSTEM | [1] | `SEAT_BELT_BUCKLED` |
| 291504647 | `0x11600207` | SYSTEM | [0] | `PERF_VEHICLE_SPEED` |
| 291504905 | `0x11600309` | SYSTEM | [0] | (app gọi là `EV_BATTERY_LEVEL` — xem F4) |
| **371198722** | `0x16200B02` | SYSTEM | **[1]** | **`DOOR_LOCK`** |

Đối chiếu với bảng M2 (`03-contracts.md` §0.2):

| Intent | propId app ghi | Có trong pin? | areaId app ghi | Gateway chờ area | Kết luận |
|---|---|---|---|---|---|
| `door_lock` | `371198722` | ✅ | `1` | `1` | **khớp hoàn toàn** |
| `hvac_set_temp` | `358614275` | ❌ **vắng** | `1` | `1` | propId không được bắc cầu |
| `hvac_set_fan` | `356517120` | ❌ **vắng** | `0` (GLOBAL) | `1` (ROW_1_LEFT) | **sai cả hai** |

Nguồn phía app: `ExecuteVehicleControlUseCase.kt:96,105` (fan, `VehicleAreas.GLOBAL`),
`:184-185` (door, `DOOR_ROW_1_LEFT`), `:213` (temp).

⚠️ **SUY LUẬN** (chưa chạy trên Device): `actuate_kuksa` trong gateway bắt đầu bằng
`local areas = entry_by_prop[prop_id]; if not areas then return end` — property
không có mapping thì hàm **im lặng thoát**. Cộng với pin không bắc cầu, hai lệnh
HVAC gần như chắc chắn không tới KUKSA. Chính `carsky-analysis/03` §4 đã trích
guideline CDC: *"Property trả null/lỗi → khả năng cao chưa được wire trong
blueprint. Báo team hạ tầng, đừng debug tiếp phía app."* Đội đã có câu cảnh báo
này trong tay từ đầu nhưng chưa ai đối chiếu pin với bảng M2.

### F3 🟢 — Gateway CÓ SẴN đường vendor cho HVAC, dùng được ngay không cần sửa blueprint

`infotainment_gateway.lua` khai mỗi tín hiệu VSS với **nhiều consumer** — một
propId AOSP chuẩn và một vendor mirror trỏ cùng giá trị vật lý:

```lua
{ sig = vss_hvac_driver.Temperature,
  consumers = { { prop = prop.HVAC_TEMPERATURE_SET,       area = seat.ROW_1_LEFT },
                { prop = prop.HU_HVAC_TEMPERATURE_DRIVER, area = 0 } } },
{ sig = vss_hvac_driver.FanSpeed,
  to_vss = fan_vhal_to_vss, to_vhal = fan_vss_to_vhal,
  consumers = { { prop = prop.HVAC_FAN_SPEED,    area = seat.ROW_1_LEFT },
                { prop = prop.HU_HVAC_FAN_SPEED, area = 0 } } },
```

Vendor mirror **nằm trong pin allowlist ở area 0**. Nghĩa là app ghi
`0x21600409 area 0` (nhiệt độ) và `0x21400400 area 0` (quạt) là chạm được KUKSA
**ngay hôm nay**, không phải sửa blueprint, không phải xin quota deploy.

Quy đổi quạt của gateway khớp contract: `fan_vhal_to_vss(v) = round(v*100/5)`
đúng bằng luật `percent = level × 20` của M2. (Comment trong lua ghi *"VSS 1..5 ↔
VHAL 0..4"* là **sai so với chính code của nó** — đừng đọc comment, đọc hàm.)

### F4 🟠 — Ba property đọc trạng thái đang lệch số hiệu

| App đọc | Giá trị trong app | Trong pin? | Gateway map tới propId nào |
|---|---|---|---|
| `PERF_VEHICLE_SPEED` | `291504647` | ✅ | `prop.PERF_VEHICLE_SPEED`, `read_only` |
| `EV_BATTERY_LEVEL` | `291504905` | ✅ | lua **ghi đè** `prop.EV_BATTERY_LEVEL = 0x11600204` = `291504644` ❌ |
| `FUEL_LEVEL` | `291504903` | ❌ **vắng** | **không có mapping nào** |

⚠️ **SUY LUẬN**: `vehicle_status_battery` hỏng ở cả hai đầu — gateway đẩy SoC lên
`291504644` (pin không bắc cầu), còn app nghe `291504905` (pin bắc cầu nhưng
không mapping nào nhắm tới). `vehicle_status_fuel` không có đường nào cả.
Trong phiên 10/08 hai câu này trả lời được vì chạy flavor `mock`. Trên `real`
chúng sẽ trả null. **Phải kiểm bằng `getProperty` thật trước khi lên kịch bản demo.**

🟢 **Bẫy đơn vị thì KHÔNG dính.** `VehicleStatus.kt:17` có
`speedKmh = speedMetersPerSecond * 3.6f` và `VehicleControlResponses.kt:37` cũng
nhân 3.6 — đúng với cảnh báo "sai gấp 3,6 lần" ở `carsky-analysis/03` §4.

### F5 🟢 — Nền tảng ĐIỀU KHIỂN ĐƯỢC đầu vào của SafetyGuard

Ba mảnh ghép đã có sẵn, chưa ai ghép:

1. `evidence/carsky/signals-rest-0808/` chứng minh `POST /signals/{room}/drive-controls/actuate`
   ghi được `vcu/Speed` (GPIO, **actuator**, range 0–180 kmh) — không cần Conduit.
2. Gateway map `vss_root.Speed → PERF_VEHICLE_SPEED` (`read_only`, có
   `speed_kph_to_mps`) và `pins.vhal:on_get` phục vụ đọc thẳng từ KUKSA.
3. `PERF_VEHICLE_SPEED` nằm trong pin allowlist.

⇒ Người ngoài đặt tốc độ bằng REST → app trên Device thấy đổi → `SafetyGuard`
đổi verdict. Đây chính là ablation **A1** trong `08-BAREM` §③, nhưng chạy qua
nền tảng thay vì qua mock.

⚠️ Lưu ý contract của gateway: *"gateway publishes CHANGES only — no cold-start
re-seed. App side MUST call getProperty() on subscribe-init"*. Nếu app chỉ
`observeProperty` mà không `getProperty` lúc khởi động thì lần đọc đầu là null.

### F6 🟠 — Ba chỗ lệch giữa tài liệu đội và blueprint đang chạy

| Nơi | Tài liệu đội ghi | Blueprint live |
|---|---|---|
| Địa chỉ node ASR | `03-contracts.md` §2 PA-1 = `10.99.0.2:8080` | pin eth = **`10.99.0.3`** |
| `10.99.0.2` thực ra là của ai | (không ghi) | TAP của **IVI Gateway** (`[eth_tunnel] TAP 'e-eth' configured: 10.99.0.2/24`) |
| Digest image ASR | `v7-manifest.txt`: index `sha256:63c2c56a…` | `viva-asr@sha256:6ca09c24…` |

Và một va chạm địa chỉ: lua gọi
`agl_kuksa = nydus.kuksa.connect("http://10.99.0.3:55555")` — blueprint gốc dành
`10.99.0.3` cho **AGL guest databroker**, còn đội đã đặt node ASR vào đúng địa chỉ đó.
Room hiện không có node AGL nên chưa gây hại, và lua nối AGL **sau cùng có chủ ý**
để đường Android↔CAN không bị chặn. Nhưng đây là một quyết định phải khai, không
phải một sự trùng hợp im lặng.

### F7 — `/deployments/find` báo `status: PENDING` trong khi `/deployments/{room}/status` báo `RUNNING`

22/22 node `Running`. Dùng `/status`, đừng tin trường `status` của `find`.

---

## PHẦN 1 — Khoản trừ −3: "Đường align với ecosystem"

**Giám khảo nói:** *"APK và ASR node là hai lát cắt rời; cần App gọi đúng ASR node
và policy output đi tới VHAL/CAN hoặc Media consumer trong cùng run."*

**Thực tế:** vế đầu **đã làm được từ 10/08** nhưng không chứng minh được bằng file
đang nộp. `evidence/c2/carsky-voice-e2e-20260810/capture.log` chỉ có 25 dòng
`VIVA_TRACE_SUMMARY`, không có `asr_sent`/`asr_done`, không có log phía node.
Build flag `-PvivaAsrBaseUrl=http://10.99.0.3:8080` là một câu văn xuôi trong
README. Vế sau — policy output tới consumer thật trong **cùng** run — thiếu thật:
readback media nằm ở phiên 09/08 (bơm text, không mic), số liệu giọng nói nằm ở
phiên 10/08 (không readback).

### Đề xuất

| # | Việc | Vì sao ăn điểm | Chi phí |
|---|---|---|---|
| **A1** | Chạy lại một phiên, lấy logcat **đủ mốc** (`speech_start`/`asr_sent`/`asr_done`/`nlu_done`), không chỉ dòng SUMMARY | Biến "build flag trong README" thành khoảng thời gian đo được của chặng mạng | 0 code |
| **A2** | Ngay sau phiên, kéo `?container=user&tail=2000` của node ASR (F1) | Bên thứ hai xác nhận: nền tảng ghi nhận đúng UUID app gửi | 1 lệnh curl |
| **A3** | Trong **cùng** phiên đó, thêm ít nhất một lượt `door_lock` **thành công** và đọc lại `Vehicle.Cabin.Door.Row1.DriverSide.IsLocked` bằng REST | Nối vế "policy output đi tới VHAL/CAN" vào đúng run có ASR | phụ thuộc M1a |
| **A4** | Nếu M1a chưa xong: thêm lượt `media_*` và capture `dumpsys media_session` trong cùng phiên | Vế "Media consumer trong cùng run" — đủ để bỏ chữ "rời" | 0 code |
| **A5** | Sửa `03-contracts.md` §2: PA-1 là `10.99.0.3`, ghi rõ `10.99.0.2` là TAP của IVI Gateway và `10.99.0.3:55555` vốn dành cho AGL guest (F6) | Ô "Ranh giới" chấm đúng chỗ này; sai địa chỉ trong contract là điểm trừ tiềm ẩn | 15 phút |

**A4 là lối thoát nếu M1a trượt.** Giám khảo cho hai lựa chọn — *"VHAL/CAN **hoặc**
Media consumer"*. Media không cần privileged permission.

---

## PHẦN 2 — Khoản trừ −4: "Độ sâu trong core flow"

**Giám khảo nói:** *"bỏ CarSky vẫn giữ được flow HVAC/media quan sát hiện nay;
platform chỉ qua ngưỡng khi capability/contract của nó là điều kiện cần cho core outcome."*

**Thực tế:** đúng vế sau, **sai vế trước theo code hiện tại**.
`RoutingAsrClient.kt:28` chỉ còn hai nhánh — `AsrEngine.VIVA` (container CarSky,
**mặc định**) và `AsrEngine.GOOGLE` (cloud). `find -name "Vosk*.kt"` không còn kết
quả: engine on-device đã bị gỡ khỏi code. Bỏ node ASR = mất luôn đường offline.

Cái thật sự thiếu là **vế ra**: ở flavor `mock`, NLU + SafetyGuard + HVAC + media
chạy trong APK, đầu kia là `MockVehicleRepository`.

### Bốn đề xuất, xếp theo "điều kiện cần" mạnh dần

**B1 — Vòng ghi→đọc lại của `door_lock` (đường ngắn nhất tới điểm)**

Đây là intent duy nhất **khớp hoàn toàn** ba tầng (F2): app ghi `371198722` area `1`,
pin bắc cầu đúng propId đúng area, gateway có mapping bidirectional với coercion
bool đã xử lý sẵn. Kịch bản:

```
1. REST: POST /signals/{room}/drive-controls/actuate  vcu/Speed = 0
2. Nói "khoá cửa"  -> Allow -> app setBooleanProperty(DOOR_LOCK, area 1, true)
3. REST: POST /signals/{room}/central-broker-vss/values
         Vehicle.Cabin.Door.Row1.DriverSide.IsLocked  -> true, timestamp nhảy
```

Cùng hình dạng bằng chứng T0/T1/T2 đã làm 07/08, khác đúng một điểm: **lệnh đến
từ app thay vì từ curl**. Chặn bởi M1a.

**B2 — Ablation A1 chạy qua nền tảng (F5) 🔴 đây là đề xuất mạnh nhất**

```
Lượt 1:  REST đặt vcu/Speed = 0   -> nói "mở cửa" -> Confirm:G2_CONFIRM_DOOR
Lượt 2:  REST đặt vcu/Speed = 60  -> nói "mở cửa" -> Deny:G1_SPEED_LOCK
```

Hai lượt, cùng phiên, cùng APK, cùng câu nói. **Verdict đổi chỉ vì trạng thái
trên nền tảng đổi.** Đây đúng nghĩa *"capability của platform là điều kiện cần
cho core outcome"* — và nó ăn thẳng vào claim khác biệt số 1 của đội (SafetyGuard),
chứ không phải một tính năng phụ.

Ưu điểm lớn: **B2 không cần quyền ghi property**, chỉ cần app **đọc** được
`PERF_VEHICLE_SPEED` — nhẹ hơn M1a rất nhiều. ⚠️ SUY LUẬN: cần kiểm app có gọi
`getProperty` lúc subscribe-init không (contract "changes only, no re-seed" ở F5).

**B3 — Chuyển hai lệnh HVAC sang vendor propId (F3)**

Thêm vào `ExecuteVehicleControlUseCase` đường ghi `0x21600409 area 0` (nhiệt độ)
và `0x21400400 area 0` (quạt) song song với propId chuẩn. Không sửa blueprint,
không tốn quota deploy. Sau đó đọc lại
`Vehicle.Cabin.HVAC.Station.Row1.Driver.Temperature` bằng REST — đúng path mà
`signals-rest-0808` đã chứng minh ghi/đọc được.

Nếu muốn giữ propId chuẩn thì phải `PATCH /pins/{pinId}` thêm
`358614275 area 1` + `356517120 area 1` vào allowlist **và** sửa
`ExecuteVehicleControlUseCase.kt:96,105` từ `VehicleAreas.GLOBAL` sang
`SEAT_ZONE_DRIVER` — sửa một chỗ mà quên chỗ kia thì vẫn im lặng không chạy.

**B4 — Kiểm ba property đọc trước khi lên kịch bản demo (F4)**

`vehicle_status_fuel` và `vehicle_status_battery` nhiều khả năng trả null trên
`real`. Trong phiên 10/08 cả hai đều `Allow` — nhưng đó là `mock`. Nếu demo có hai
câu này thì phải kiểm trước, hoặc đổi sang `vehicle_status_speed` (property duy
nhất trong nhóm đã khớp cả ba tầng).

---

## PHẦN 3 — Khoản trừ −2: "Evidence platform"

**Giám khảo nói:** *"receipt Device/node còn phân mảnh; cần một biên nhận có cùng
identity cho app, ASR node, invocation, policy verdict và vehicle/media readback."*

**Thực tế:** toàn bộ ống dẫn đã tồn tại, chưa ai bấm. Không cần viết code.

| Mảnh | Đã có ở đâu | Trạng thái |
|---|---|---|
| app gắn identity | `HttpAsrClient.kt:74`, `HttpRemoteAsrTransport.kt:37` — `X-Trace-Id` | ✅ |
| node ASR ghi lại đúng identity | `asr/app/main.py:127,162` — `VIVA_ASR\|<trace_id>\|ok\|server_ms=…` | ✅ |
| lấy log node từ nền tảng | `?container=user` (F1) | ✅ mới xác minh 18/08 |
| policy verdict | `VIVA_TRACE_SUMMARY\|<uuid>\|…\|<verdict>` | ✅ |
| vehicle readback | `POST /signals/{room}/central-broker-vss/values` | ✅ đã chứng minh 07/08 |

### C1 — Runbook "biên nhận hợp nhất" (một trang, đưa vào `25-CARSKY-AAOS-DEVICE-GATE.md`)

```
0. Ghi lại: commit, SHA-256 APK, digest image ASR đang chạy, roomId, nodeId
1. REST đặt vcu/Speed = 0
2. Chạy phiên nói. Giữ nguyên phiên, KHÔNG restart gì.
3. adb logcat -d -s VIVA_TRACE:I VIVA_VOICE:I   -> ĐỦ MỐC, không lọc còn SUMMARY
4. curl .../logs/<asr-node>?container=user&tail=2000
5. REST đọc lại VSS path tương ứng
6. REST đặt vcu/Speed = 60, lặp lại một câu -> verdict đổi
7. Đối chiếu: một UUID phải xuất hiện ở bước 3, 4; bước 5 có timestamp nhảy
```

**Ràng buộc cứng:** bước 4 phải chạy **trong cùng phiên**. Log chỉ sống theo vòng
đời pod và Loki rỗng (F1) — pod restart là mất sạch, không lấy lại được.

### C2 — Sửa `docs/backend-docs/carsky-api.md` §5

Ghi rõ: `container=user` cho container node, `container=sidecar` cho script-node,
tham số không có trong `openapi.json`; Loki `/search` rỗng ở instance này; log lua
của script-node chỉ xem được trong UI. Ô "Evidence từ platform" đòi *"log/trace/
output từ CarSky"* — một tài liệu ghi đúng cách lấy chính là một phần của ô đó.

### C3 — Đóng cảnh báo commit của bộ 10/08

README bộ đó tự ghi: APK = `main@214914e` **cộng** PR #42 chưa merge, nên bộ bằng
chứng trỏ về một trạng thái cây làm việc. PR #42 nay đã ở `main` (`59d94e4`).
Build lại từ commit merge, đối chiếu SHA-256, hoặc khai rõ giới hạn truy nguyên.

---

## PHẦN 4 — Ô 2/2 "Ranh giới và tính tương xứng": đừng làm hỏng

Ô này đầy điểm nhờ kỷ luật ba nhãn ở `24-N5`. Khi chạy đua ba khoản trên,
**nới nhãn để lấy 2đ không chắc mà mất 2đ chắc chắn là lỗ.**

Ba dòng phải cập nhật **sau khi** có bằng chứng, không phải trước:

- `viva-asr` container: bổ sung digest đang chạy `sha256:6ca09c24…` (F6) — hiện
  manifest ghi một digest khác.
- `VivaCarService → PropertyID → VHAL`: hiện là **Kế hoạch**. Sau B1 chỉ được nâng
  cho **`door_lock`**, không nâng cho `hvac_*` (F2).
- Thêm một dòng mới: *"Bắc cầu VHAL của blueprint — 15 property, xem F2"*, nhãn
  **Đã tích hợp (ở mức đọc cấu hình)**.

---

## PHẦN 5 — Thứ tự chạy, theo tỉ lệ điểm trên công

| Ưu tiên | Việc | Gỡ được | Chặn bởi |
|---|---|---|---|
| 1 | **B2** ablation tốc độ qua REST | −4 (mạnh nhất) + −3 | chỉ cần app đọc được property |
| 2 | **A1+A2+C1** biên nhận hợp nhất | −2 gần trọn | không gì |
| 3 | **B4** kiểm 3 property đọc | tránh vỡ demo | không gì |
| 4 | **A5+C2** sửa contract §2 và carsky-api §5 | giữ 2/2 ranh giới | không gì |
| 5 | **B3** vendor propId cho HVAC | −4 | ~1 buổi code |
| 6 | **B1** door_lock ghi→đọc lại | −4 trọn vẹn | **M1a** |
| 7 | **A4** media readback cùng run | −3 nếu M1a trượt | không gì |

Việc 2, 3, 4, 7 **không phụ thuộc M1a**. Nếu M1a không xong đúng hạn thì vẫn
gỡ được phần lớn −3 và −2; chỉ −4 là phải khai thẳng chưa đạt.

---

## PHẦN 6 — Chưa xác minh, phải kiểm trước khi trích

1. Ghi property ngoài allowlist có thật sự bị bỏ im lặng không — mới đọc code lua
   và cấu hình pin, **chưa chạy trên Device** (F2, F3).
2. `EV_BATTERY_LEVEL` lệch số hiệu có thật sự làm câu trả lời null không (F4).
3. App có gọi `getProperty` lúc subscribe-init không — nếu không thì lần đọc đầu
   là null theo contract của gateway (F5).
4. `PATCH /pins/{pinId}` có sửa được allowlist không. `v7-manifest.txt` ghi 404
   khi thử trên pin `eth`; route có trong `openapi.json` và địa chỉ eth **hiện đã
   được đặt**, nên nhiều khả năng đường UI làm được — chưa thử lại bằng API.
5. Toàn bộ PHẦN 0 đọc từ blueprint `6deadb05` bản export ngày 18/08. Nếu ai đó
   redeploy hoặc sửa blueprint thì phải export lại rồi đối chiếu.
