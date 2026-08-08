# CarSky REST API — những gì đã gọi thật và kết quả

> Ghi ngày 02–03/08/2026, bổ sung 05/08 (mục 4c) và **08/08 (mục 4d)**. Mọi dòng dưới
> đây là **phản hồi thật của server**, không phải suy đoán từ tài liệu. Cái gì chưa gọi
> thì ghi rõ là chưa gọi.
>
> ⚠️ **Đọc mục 4d trước khi kết luận "không gửi được gì vào room".** Họ `/signals`
> không đi qua Conduit và đang chạy tốt; ba dòng mô tả nó ở mục 4b trước đây ghi sai.
>
> Không commit token/API key vào repo. Cấu hình đọc từ `backend/.env`
> (đã gitignore), mẫu ở `backend/.env.example`.

## 1. Xác thực — dùng API key, **không** dùng JWT của phiên web

Đây là chỗ mất nhiều thời gian nhất, nên ghi lại để không ai lặp lại:

| Thứ gửi đi | Kết quả |
|---|---|
| Không có header nào | `401 {"error":"UNAUTHORIZED","message":"Missing credentials"}` |
| JWT copy từ phiên đăng nhập web (`alg:HS256`, payload `sub/isAdmin/email`) | `401 ... "Invalid JWT"` — **kể cả token vừa phát, còn 59 phút mới hết hạn** |
| **API key** (`x-api-key: a8k_…`) | ✅ qua được auth |
| **API key** (`Authorization: Bearer a8k_…`) | ✅ qua được auth |
| API key đặt trong cookie | `401 ... "Missing credentials"` |

Vì sao JWT web không dùng được: gốc `https://hackathon-2.carsky.io/` là trang đăng
nhập **Keycloak** (`/auth/realms/hackathon02`, client `rework`). Token mà REST API
chấp nhận là API key phát riêng, không phải session token của Keycloak.

`viva-tools` đọc biến `CARSKY_API_TOKEN` và gửi dạng `Bearer` — **đặt API key vào
biến đó là chạy được**, không phải sửa code.

## 2. Base URL và spec

- Base: `https://hackathon-2.carsky.io/api/v1`
- Spec đầy đủ: **`GET /api/v1/openapi.json`** (71 endpoint). Lưu ý `/api/v1/openapi`
  **404** — đường dẫn đúng có đuôi `.json`. Swagger UI ở `/api/v1/docs`.
- Prefix khác (`/api/...`, `/v1/...`, `/deployments`) đều trả HTML của SPA → chỉ
  `/api/v1/*` mới được proxy sang API.
- Middleware auth chạy **trước** routing: đường dẫn không tồn tại mà thiếu key vẫn
  trả 401, nên đừng dùng 401 để kết luận "endpoint không có".

Spec 128 KB **không commit vào repo** (nội bộ nền tảng, thể lệ 3.6). Tải lại bằng:

```powershell
$k = ((Get-Content backend\.env | Select-String '^CARSKY_API_KEY=') -split '=',2)[1]
Invoke-WebRequest -Uri "https://hackathon-2.carsky.io/api/v1/openapi.json" `
  -Headers @{ "x-api-key" = $k } -OutFile openapi.json
```

## 3. Endpoint đã gọi thật — đều 200

| Endpoint | Dùng cho | Kết quả thật |
|---|---|---|
| `GET /devices` | tra device của đội | 18 device; **VIVA = `v37aa3knc6t1embelr5yi`** |
| `GET /deployments/find?device=<id>` | tìm room đang chạy | VIVA → room `v37aa3knc6t1embelr5yi`, blueprint `RMbeXxTF5ZvkmqzRK04gf`, namespace `room-lgpuafex`, `RUNNING`. **"VIVA 2" (`og4erd2wzaxe5xod8otuj`) không có deployment nào** |
| `GET /deployments/{room}/status` | trạng thái | `RUNNING` |
| `GET /deployments/{room}/nodes` | **V3** | 21 node, 21/21 `Running` — xem `backend/carsky/nodes.json` |
| `GET /deployments/{room}/adb-tunnel` | **V5** | trả `conduitUrl` + lệnh `nydus-reach tunnel adb …` |
| `GET /blueprints/{id}/export` | **V2** backup | 66 KB JSON, đã lưu `backend/carsky/blueprint-VIVA-deploy-backup.json` |
| `GET /signals/{room}` | nguồn tín hiệu | 7 nguồn: 2 CAN (`bcm-can`, `pwt-can`), 1 KUKSA (`central-broker-vss`), 4 GPIO |
| `GET /deployments/{room}/logs/{node}` | log pod | 200 — nhưng xem cảnh báo mục 5 |
| `GET /config/limits`, `/account-limits/effective/{acc}` | quota | `MAX_DEVICES=5`, `MAX_NODES_PER_BLUEPRINT=30`, `MAX_CONCURRENT_DEPLOYMENTS=2`, `MAX_SKYCRAFT_PER_BLUEPRINT=2` |

**`POST /blueprints/{id}/clone` — đã chạy 03/08.** Kết quả ở
`backend/carsky/blueprint-clone-response.json`: blueprint mới
`6deadb05-c856-4dab-976b-432b0fac0658`, tên `VIVA-deploy-clone-0803`,
`parentBlueprintId` trỏ đúng bản gốc.

⚠️ **`name` là field bắt buộc**, và client cũ POST body rỗng nên lệnh clone
chưa từng chạy được — xem PR `fix/carsky-clone-name`. Bài học chung: mọi
endpoint POST trong client này đều viết từ tài liệu HTML chứ chưa gọi thật,
nên **đối chiếu lại với `openapi.json` trước khi tin**.

**Chưa gọi:** `POST /deployments` (deploy bản clone, phần cuối của V2) — xem
mục 8.

## 4. 🚫 Cả họ endpoint điều khiển VM đang chết

`screenshot` · `accessibility` · `shell` · `tap` · `text` · `key` · `swipe` ·
`adb-exec` · `container-exec` — tất cả trả:

```
502 {"error":"SERVICE_UNAVAILABLE","message":"Conduit service not configured"}
```

Bốn phép thử loại trừ nguyên nhân từ phía đội:

1. Mọi endpoint **không** cần conduit đều 200 với cùng API key → không phải quyền.
2. Lỗi y hệt trên **script-node** (`container-exec` ở IVI Gateway), không riêng node
   Android → không phải do node skycraft.
3. `/account-limits/effective/…` **không có cờ nào** bật/tắt conduit → không phải quota.
4. 🆕 **05/08 21:35 — thử lại với room vừa deploy sạch, 22/22 node `Running`:** vẫn
   `502`. Xem mục 4b.

→ Thiếu cấu hình phía nền tảng. **Câu hỏi cho BTC/mentor**, kèm nguyên văn lỗi trên.

### 4b. Đường dẫn đúng của họ endpoint này — bản trước ghi thiếu

Mục 4 liệt kê tên endpoint nhưng không ghi route, và đoán route theo kiểu
`/deployments/{room}/nodes/{node}/shell` sẽ nhận `404 Route not found` — **404 đó
không phải Conduit chết**, chỉ là gõ sai địa chỉ. Route thật, lấy từ
`/api/v1/openapi.json` (73 route):

```
POST /api/v1/vms/{roomId}/{nodeKey}/adb-shell     ← cai APK, chay lenh
POST /api/v1/vms/{roomId}/{nodeKey}/shell
POST /api/v1/vms/{roomId}/{nodeKey}/screenshot · /tap · /text · /key · /swipe
POST /api/v1/deployments/{roomId}/adb-exec/{nodeKey}
POST /api/v1/deployments/{roomId}/container-exec/{nodeKey}
```

⚠️ **Hai dòng `/signals` trong bản trước của mục này ghi sai và đã được gỡ khỏi
danh sách trên.** Chúng không thuộc họ Conduit, không trả 502, và `values` là
`POST` chứ không phải `GET`. Xem mục **4d** — họ `/signals` đang chạy tốt.

Gọi đúng `POST /vms/{room}/{node}/adb-shell` với node `IVI - Android` lúc room
đang chạy đủ 22 node vẫn trả:

```
502 {"error":"SERVICE_UNAVAILABLE","message":"Conduit service not configured"}
```

**Đây là bằng chứng mạnh nhất để leo thang.** Trước đây còn có thể nghi "tại room
không chạy"; nay room chạy đủ, `adb-tunnel` trả cấu hình hợp lệ, mọi endpoint khác
200, riêng họ Conduit 502. Không còn giả thuyết nào từ phía đội.

### 4c. Room từng biến mất — 05/08

Chiều 05/08, giao diện web không kết nối được: `Data WebSocket closed (code 4000)`
và mọi part (`Device Proxy`, `face-screen`, `face-audio`, `face-touch-panel`) đều
hết giờ. Nguyên nhân **không phải Conduit**:

```
GET /deployments/v37aa3knc6t1embelr5yi/nodes
404 {"error":"NOT_FOUND","message":"No deployment found for this room in current profile"}
```

Cả 4 device (`Gemini`, `Gemini 2`, `VIVA`, `VIVA (Copy)`) đều `operational: IDLE`,
`lastSeenAt: null` — **không có deployment nào tồn tại**. Room `og4erd2wzaxe5xod8otuj`
từng chạy 22/22 node ở V7 cũng không còn trong danh sách device: device đó đã bị xoá.

Khắc phục: deploy lại blueprint `6deadb05-…` (chính bản đã dựng ra room V7) lên
device `VIVA`:

```
POST /deployments {blueprintId, roomId: v37aa3knc6t1embelr5yi, name: "VIVA-demo-0805"}
→ id 933cf72a-0a81-4b48-a401-725850eca3d8, namespace room-5s98rj6x, status PENDING
→ sau ~3 phut: 22/22 node Running, device VIVA chuyen IDLE → BUSY
```

**Bài học vận hành:** deployment CarSky không sống mãi. Trước mỗi buổi làm việc
hoặc trước khi quay demo, kiểm một dòng:

```powershell
go run ./cmd/viva-tools carsky nodes --room $env:CARSKY_ROOM_ID
```

404 nghĩa là phải deploy lại, không phải nền tảng hỏng.

**Hệ quả:** kế hoạch V11 (`send_signals → screenshot → find_text`) chưa chạy được
qua HTTPS. Đường thay thế là tunnel ở mục 5.

### 4d. ✅ Họ `/signals` KHÔNG đi qua Conduit — và nó đang chạy tốt (08/08)

Đây là đính chính quan trọng nhất của mục 4. Kết luận *"không gửi được gì vào room"*
chỉ đúng với họ endpoint điều khiển VM. Họ `/signals` nói chuyện thẳng với KUKSA
broker / CAN bus / GPIO panel và **hoạt động bình thường qua HTTPS, không cần
`nydus-reach`**.

Ba chỗ bản trước ghi sai:

| Bản trước | Thực tế |
|---|---|
| `GET .../signals/{room}/{node}/values` | **`POST`**. Gọi bằng `GET` trả `404 Route not found` — và đó là lý do đội tưởng route không tồn tại |
| `nodeKey` = UUID node trong `/deployments/{room}/nodes` | `nodeKey` là trường **`key`** do `GET /signals/{roomId}` trả về: `central-broker-vss`, `bcm-can`, `pwt-can`, `drive-controls`, `battery-sensor`, `seatbelt-sensor`, `tirepressure-sensor` |
| Họ này có 2 route | Có **9** route |

Danh sách đủ:

```
GET  /api/v1/signals/{roomId}                          — liet ke nguon tin hieu
GET  /api/v1/signals/{roomId}/{nodeKey}                — liet ke tin hieu + metadata
POST /api/v1/signals/{roomId}/{nodeKey}/values         — doc gia tri hien tai  {"paths":[...]}
POST /api/v1/signals/{roomId}/{nodeKey}/actuate        — ghi  {"path":..,"value":..,"actuate":bool}
GET  /api/v1/signals/{roomId}/{nodeKey}/subscribe      — SSE, BAT BUOC query param ?paths=
GET  /api/v1/signals/{roomId}/{nodeKey}/periodic
POST /api/v1/signals/{roomId}/{nodeKey}/periodic/start · /periodic/stop
GET  /api/v1/signals/{roomId}/{nodeKey}/periodic/subscribe
```

**Đã chạy thật 07/08 19:30–19:37 UTC, tất cả 200** — bằng chứng đầy đủ ở
`evidence/carsky/signals-rest-0808/`:

- `central-broker-vss` trả **1.268 tín hiệu VSS** kèm metadata. Bốn tín hiệu của
  bảng M2 khớp chính xác `03-contracts.md §0.2`: `Driver.Temperature` (float,
  actuator, °C), `Driver.FanSpeed` (uint8, actuator, **percent 0–100** — đúng lý do
  contract bắt quy đổi `level × 20`), `Door.Row1.DriverSide.IsLocked` (bool,
  **True = Locked**), `Vehicle.Speed` (float, sensor, km/h).
- `bcm-can` trả 33 tín hiệu khớp `docs/dbc/README.md`.
- `drive-controls` có **`vcu/Speed` [0,180] kmh, `entryType=actuator`** → đặt được
  tốc độ cho ablation A1 bằng REST.
- **Vòng ghi → đọc lại trên KUKSA đã chứng minh:**
  `Driver.Temperature` = `null` (ts 05/08 14:30:56Z) → `POST /actuate {value:24.0}`
  → `200 {"ok":true,"sent":1}` → `POST /values` = **`24`** (ts 07/08 19:34:36Z).

**Giới hạn — phải khai khi trích:**

1. Đây là REST gọi thẳng KUKSA. **Không có APK, không có VHAL, không có SafetyGuard
   trong đường này.** Nó là công cụ **đo**, không phải core flow của sản phẩm.
2. **Ghi VSS từ ngoài không lan xuống CAN.** `POST /values` trên `bcm-can` trả
   `{"values":[]}`; SSE `bcm-can/subscribe` trong 20 giây chỉ nhận một sự kiện
   `ping`. Thử `{"actuate":true}` (provider-based) → `200 ok` nhưng giá trị hiện tại
   không đổi, tức không có provider nào nhận. Điều này khớp với
   `infotainment_gateway.lua` trong blueprint: chuỗi VSS → CAN được kích hoạt từ
   phía VHAL (`pins.vhal:on_change → actuate_kuksa`), nên **vẫn phải có APK chạy
   trên node skycraft** mới đóng được chuỗi đầy đủ.
3. Không dùng thay cho E03/E04 (trace + p50/p95) hay E06–E08 (readback property qua
   `CarPropertyManager`).

**Dùng được ngay vào ba việc:** (a) ô barem *"Evidence từ platform"* đòi
*"log/trace/output từ CarSky"* — đây đúng là thứ đó; (b) xác nhận bảng M2 bằng
metadata của chính nền tảng thay vì bằng tài liệu đội tự viết; (c) khi APK đã cài,
đây là **cách đọc lại từ ngoài** để chứng minh lệnh của app thật sự chạm KUKSA.

## 5. Hai cái bẫy khi đọc số

- **`/logs/{node}` không phải logcat.** Nó trả log của pod; với node Android các dòng
  lấy về là log WebRTC (`rtc_source_native … UDP throughput`). `VIVA_TRACE` **không**
  nằm ở đây — muốn có trace phải qua `adb logcat` bằng tunnel.
- **`/devices` liệt kê cả device của đội khác** (18 cái, phần lớn `PUBLISHED`). Chỉ
  thao tác trên `VIVA`; đừng gọi lệnh ghi lên id lạ.

## 6. Dev loop hiện dùng được (V5)

API trả sẵn lệnh mở tunnel cho node Android:

```
nydus-reach tunnel adb --conduit https://hackathon-2.carsky.io \
  --namespace room-lgpuafex --node rmbexxtf5zvkmqzrk04gf-n1
```

Sau đó `adb connect <host:port>` → cài APK, `adb logcat`, `screencap`,
`uiautomator dump` đều chạy bản địa, và harness dùng được `--adb` như thiết kế.
**Còn thiếu:** binary `nydus-reach` (tải trong UI CarSky) — chưa ai trong đội xác
nhận đã chạy được lệnh này.

## 7. Node của room VIVA (V3)

21/21 `Running`. Bản đầy đủ ở `backend/carsky/nodes.json`.

| nodeType | Số lượng | Đáng chú ý |
|---|---|---|
| `script-node` | 8 | **IVI Gateway** (`…-n4`) và **PWT Gateway** (`…-n13`) — đúng hai node mentor bảo đọc trước khi viết Luau (M4). Thêm BCM/Climate/VCU/BMS/TCU Gateway |
| `gpio-panel` | 4 | TirePressure, Drive Controls, SeatBelt, Battery — **Drive Controls (`…-n12`) là chỗ đặt tốc độ cho ablation A1** |
| `can-bus` | 2 | BCM CAN (`…-n7`), PWT CAN (`…-n19`) |
| `kuksa-databroker` | 1 | Central Broker VSS (`…-n18`) |
| `skycraft` | 1 | **IVI - Android (`…-n1`)** — nơi cài APK |
| `container` | 2 | TCU-NAD, SeatBelt ECU |
| `eth-bridge` | 2 | IVI Switch, TCU Switch |
| `device-proxy` | 1 | Device Proxy (`…-n14`) |

## 8. Deploy bản clone — lệnh cụ thể, và vì sao chưa bấm

Phần cuối của V2 (*"Clone Running"*) là một lệnh:

```powershell
$k = ((Get-Content backend\.env | Select-String '^CARSKY_API_KEY=') -split '=',2)[1]
$body = @{
  blueprintId = "6deadb05-c856-4dab-976b-432b0fac0658"   # bản clone
  roomId      = "og4erd2wzaxe5xod8otuj"                  # device VIVA 2, dang khong co deployment
  name        = "VIVA-deploy-clone-0803"
} | ConvertTo-Json -Compress

Invoke-WebRequest -Uri "https://hackathon-2.carsky.io/api/v1/deployments" -Method POST `
  -Headers @{ "x-api-key" = $k; "Content-Type" = "application/json" } -Body $body
```

Hai điều phải biết trước khi bấm:

- **Quota `MAX_CONCURRENT_DEPLOYMENTS_PER_ACCOUNT = 2`**, đội đang dùng 1 (room VIVA
  đang chạy). Deploy bản clone là lấy nốt slot thứ hai — sau đó không deploy thêm
  được gì cho tới khi xoá một cái.
- Deploy vào **VIVA 2** thì không đụng gì tới room demo. Đừng deploy đè lên
  `v37aa3knc6t1embelr5yi`.

Gỡ khi cần: `DELETE /api/v1/deployments/{roomId}`.

## 9. Tự động deploy khi merge vào `main` — làm được tới đâu

Làm được, nhưng **không phải cho thứ quan trọng nhất**:

| Việc | Tự động được? | Vì sao |
|---|---|---|
| Deploy/redeploy blueprint | ✅ | `POST /deployments` + `DELETE /deployments/{roomId}`, chỉ cần API key trong GitHub Secrets |
| Cập nhật container `viva-asr` | ⚠️ một phần | Phải push image lên Zot trước; CI chưa có bước đó |
| **Cài APK lên node Android** | ❌ | Cần `adb`, mà `adb-exec`/`shell` đang chết vì Conduit (mục 4). Không có đường HTTPS nào thay thế |

Nghĩa là "CD lên CarSky" **không** giao được APK — tức là không giao được thứ cả
demo phụ thuộc vào. Và ba lý do nữa để **không** bật auto-deploy theo push `main`:

1. **Quota 2 deployment.** Một workflow chạy mỗi lần merge sẽ đụng trần rất nhanh.
2. **Redeploy là phá room đang chạy.** Merge một PR docs lúc đang tổng duyệt mà
   room bị dựng lại thì hỏng buổi duyệt.
3. **Freeze 05/08 rồi demo.** Đúng giai đoạn cần môi trường đứng yên nhất.

**Đề xuất:** workflow chạy tay (`workflow_dispatch`) có ô nhập `roomId` + `blueprintId`,
chứ không phải `on: push`. Vẫn được tính là tự động hoá, mà không có nguy cơ một
commit docs làm sập room lúc 21:00. Khi Conduit được bật thì thêm bước cài APK và
lúc đó mới đáng bàn tới `on: push` cho nhánh release.

## 10. 🚫 API công khai KHÔNG tạo được pin `ETHERNET` — và hệ quả cho V2

Phát hiện 04/08 khi làm V7 (thêm Container Node `viva-asr`).

**Tạo node thì được.** `POST /blueprints/{id}/nodes` trả **201**, và chấp nhận cả
image dạng **digest**:

```
registry.hackathon-2.carsky.io/viva/viva-asr@sha256:63c2c56a...
```

**Nhưng không nối được vào mạng.** Container node cần một pin `ETHERNET` (đúng như
`TCU-NAD` đang có: `pinType=ETHERNET, direction=OUTPUT, properties.address=10.99.0.22`)
rồi nối vào pin của `IVI Switch`. Cả ba đường đều bị chặn:

| Đường | Kết quả |
|---|---|
| `POST /nodes/{nodeId}/pins` | **404 Route not found** — spec có ghi, server không có route |
| `POST /blueprints/{id}/batch` với `addPin` | **400** `Invalid option: expected one of "VHAL"\|"KUKSA"\|"CAN"\|"LIN"\|"VIDEO"\|"GPIO"\|"GENERIC"` |
| `POST /blueprints/import` | **400**, cùng lỗi enum |

Server **tự** kiểm enum, không phải spec cũ: cả ba nơi đều trả về đúng danh sách 7
loại, và `ETHERNET` không nằm trong đó — dù blueprint do chính nền tảng sinh ra thì
đang dùng nó.

→ **Pin ETHERNET chỉ tạo được trong UI CarSky.** Muốn thêm container node có mạng
thì: tạo node bằng API (hoặc UI), rồi **vào UI thêm pin + nối dây**.

`PATCH /pins/{pinId}` cũng **404** — nên sau khi pin được tạo trong UI, cũng không
sửa được `properties.address` bằng API. Địa chỉ IP tĩnh phải đặt luôn trong UI.

### ✅ Đã chạy được: tên đầy đủ + digest, xác nhận 04/08

Sau khi thêm pin trong UI và deploy bản clone, **22/22 node `Running`**, trong đó
node `VIVA ASR` khai image dạng:

```
registry.hackathon-2.carsky.io/viva/viva-asr@sha256:63c2c56a...
```

Nên câu hỏi *"khai `localhost:5000/...` hay tên đầy đủ"* **đã tự trả lời: tên đầy đủ
pull được**, không cần hỏi BTC. Và nền tảng **chấp nhận dạng digest**, nên khoá
artifact theo digest (thay vì `:latest`) là làm được thật — với `viva-asr` thì digest
khoá luôn cả model đã convert nằm trong image.

Bằng chứng: `evidence/carsky/v7-manifest.txt` + `v7-asr-node-phases.json`.

⚠️ Điều này **không** chứng minh gì về latency: container nằm trên mạng `10.99.0.x`
trong room, và không có đường gửi request vào từ ngoài (Conduit chết, chưa có
`nydus-reach`). Số 439/667 ms vẫn là CPU máy dev.

### ⚠️ Hệ quả cho V2: file backup JSON KHÔNG phải đường khôi phục

Đã kiểm bằng thực nghiệm: lấy **chính** `backend/carsky/blueprint-VIVA-deploy-backup.json`
(do `GET /blueprints/{id}/export` sinh ra) đẩy ngược vào `POST /blueprints/import`
→ **400**, vì chứa pin `ETHERNET` mà import từ chối. Nền tảng **không import lại được
bản export của chính nó**.

`04-KE-HOACH-CAP-NHAT-28-07.md` mô tả quy trình an toàn *"export backup → clone →
chỉ sửa clone"*. Quy trình đó vẫn đúng, nhưng phải hiểu lại vai trò từng thứ:

| Thứ | Vai trò thật |
|---|---|
| `POST /blueprints/{id}/clone` | **Đây mới là đường rollback.** Bản sao server-side, giữ nguyên mọi thứ kể cả pin ETHERNET |
| File JSON export | **Tài liệu và bằng chứng**, không phải ảnh chụp khôi phục được. Vẫn đáng commit (nó ghi lại topology, image, địa chỉ IP), nhưng đừng dựa vào nó để phục hồi lúc gấp |

Nói cách khác: trước khi sửa blueprint, thứ phải làm là **clone**, không phải tải JSON về.

### Câu hỏi gửi BTC

> `POST /blueprints/{id}/batch` (và `/import`) chỉ nhận `pinType` trong
> `VHAL|KUKSA|CAN|LIN|VIDEO|GPIO|GENERIC`, nhưng blueprint do nền tảng sinh ra lại có
> pin `ETHERNET`. Vậy đội thêm Container Node có mạng bằng API kiểu gì, hay bắt buộc
> phải qua UI? Và có chủ đích để `import` không nhận lại được bản `export` không ạ?
