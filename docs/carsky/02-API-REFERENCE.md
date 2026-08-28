# 02 — CarSky REST API reference

> Mọi dòng dưới đây là **phản hồi thật của server**, không phải suy đoán từ tài liệu.
> Nhật ký khám phá theo thời gian: [`carsky-api.md`](../backend-docs/carsky-api.md).

---

## 1. Xác thực — API key, **không** phải JWT của phiên web

| Thứ gửi đi | Kết quả |
|---|---|
| Không header | `401 {"error":"UNAUTHORIZED","message":"Missing credentials"}` |
| JWT copy từ phiên đăng nhập web (`alg:HS256`) | `401 … "Invalid JWT"` — kể cả token vừa phát, còn 59 phút |
| `x-api-key: a8k_…` | ✅ |
| `Authorization: Bearer a8k_…` | ✅ |
| API key đặt trong cookie | `401 … "Missing credentials"` |

Gốc `https://hackathon-2.carsky.io/` là trang đăng nhập **Keycloak**
(`/auth/realms/hackathon02`, client `rework`). Token mà REST API chấp nhận là API
key phát riêng, **không** phải session token của Keycloak.

⚠️ **Middleware auth chạy trước routing.** Đường dẫn không tồn tại mà thiếu key
vẫn trả `401` → đừng dùng `401` để kết luận "endpoint không có".

---

## 2. Base URL và spec

- Base: `https://hackathon-2.carsky.io/api/v1`
- Spec: `GET /api/v1/openapi.json` (73 route). `/api/v1/openapi` **404** — phải có
  đuôi `.json`. Swagger UI ở `/api/v1/docs`.
- Prefix khác (`/api/…`, `/v1/…`, `/deployments`) trả HTML của SPA.

### 🔴 `openapi.json` GHI SAI ĐƯỜNG DẪN — bài học đắt nhất

```
PATCH /api/v1/nodes/{nodeId}              -> 404 Route not found     <- openapi.json, SAI
PATCH /api/v1/blueprints/nodes/{nodeId}   -> 200                     <- Car-Sky-Platform.html, DUNG
```

Spec máy sinh **khai thiếu tiền tố `/blueprints`** cho cả họ endpoint sửa topology.

> **Quy tắc:** gặp `404 Route not found` trên CarSky, **đừng kết luận nền tảng không
> hỗ trợ**. Mở `docs/platform/Car-Sky-Platform.html` (mục *API & MCP Tools*) đối
> chiếu trước. `openapi.json` vừa khai thừa endpoint, vừa khai sai địa chỉ.

**Cách dò một route có tồn tại hay không mà không ghi gì:** gửi `PATCH` với body
rỗng `{}`. `404` = không có route; `200`/`400`/`422` = có route.

---

## 3. Endpoint đã gọi thật — 200

### 3.1 Device, deployment, node

| Endpoint | Dùng cho | Kết quả thật |
|---|---|---|
| `GET /devices` | tra device của đội | 18 device (⚠️ gồm cả đội khác); VIVA = `v37aa3knc6t1embelr5yi` |
| `GET /deployments/find?device=<id>` | tìm room đang chạy | trả blueprint + namespace + trạng thái. ⚠️ có thể trả `[]` dù room đang chạy — xem §6 |
| `GET /deployments/{room}/status` | trạng thái | `RUNNING` |
| `GET /deployments/{room}/nodes` | danh sách node + `phase` | 22/22 `Running` |
| `GET /deployments/{room}/nodes/watch` | SSE theo dõi phase | ✅ 23 event |
| `POST /deployments` | dựng deployment | body `{blueprintId, roomId, name}` → `PENDING` |
| `DELETE /deployments/{roomId}` | xoá deployment | ⚠️ **huỷ cả VM Android** |
| `POST /deployments/{room}/restart/{node}` | restart một node | ⚠️ trả **500 body rỗng nhưng VẪN CHẠY** |
| `GET /deployments/{room}/adb-tunnel` | thông tin tunnel | trả `conduitUrl` + lệnh `nydus-reach tunnel adb …` |
| `GET /deployments/{room}/logs/{node}` | log pod | ⚠️ cần `?container=user` với container node — xem §5 |
| `GET /config/limits`, `/account-limits/effective/{acc}` | quota | xem [01 §6](01-KHAI-NIEM-VA-KIEN-TRUC.md) |

🚫 `GET /api/v1/deployments` (liệt kê tất cả) → **404**. Không có cách liệt kê mọi
deployment; chỉ tra được từng room.

### 3.2 Blueprint và topology

```
GET    /api/v1/blueprints/{id}                blueprint chi tiet     OK 200
GET    /api/v1/blueprints/{id}/export         export JSON (66 KB)    OK 200
POST   /api/v1/blueprints/{id}/clone          clone server-side      OK 200  (name BAT BUOC)
POST   /api/v1/blueprints/{id}/nodes          them node              OK 201
PATCH  /api/v1/blueprints/nodes/{nodeId}      sua node (doi image)   OK 200
DELETE /api/v1/blueprints/nodes/{nodeId}      xoa node               CHUA THU
POST   /api/v1/blueprints/nodes/{nodeId}/pins them pin               route co, enum tu choi ETHERNET
PATCH  /api/v1/blueprints/pins/{pinId}        sua pin (dat IP tinh)  OK 200
DELETE /api/v1/blueprints/pins/{pinId}        xoa pin                CHUA THU
POST   /api/v1/blueprints/import              import JSON            400 - tu choi pin ETHERNET
```

⚠️ **`PATCH /blueprints/nodes/{id}` ghi đè `config`.** Phải **đọc config cũ rồi
TRỘN** — gửi mỗi `image` có thể xoá sạch biến môi trường của node.

⚠️ **`import` không nhận lại được bản `export` của chính nền tảng.** Nghĩa là file
JSON backup là **tài liệu và bằng chứng**, không phải ảnh chụp khôi phục được.
Đường rollback đúng là `clone`.

### 3.3 Họ `/signals` — ✅ đang chạy tốt, KHÔNG đi qua Conduit

Đây là đính chính quan trọng nhất so với các bản tài liệu đầu. Họ `/signals` nói
chuyện thẳng với KUKSA broker / CAN bus / GPIO panel qua HTTPS, không cần
`nydus-reach`.

```
GET  /api/v1/signals/{roomId}                      liet ke nguon tin hieu
GET  /api/v1/signals/{roomId}/{nodeKey}            liet ke tin hieu + metadata
POST /api/v1/signals/{roomId}/{nodeKey}/values     doc gia tri hien tai  {"paths":[...]}
POST /api/v1/signals/{roomId}/{nodeKey}/actuate    ghi {"path":..,"value":..,"actuate":bool}
GET  /api/v1/signals/{roomId}/{nodeKey}/subscribe  SSE, BAT BUOC ?paths=
GET  /api/v1/signals/{roomId}/{nodeKey}/periodic
POST /api/v1/signals/{roomId}/{nodeKey}/periodic/start | /periodic/stop
GET  /api/v1/signals/{roomId}/{nodeKey}/periodic/subscribe
```

Hai cái bẫy đã làm đội tưởng route không tồn tại:

1. **`values` là `POST`, không phải `GET`.** Gọi `GET` trả `404 Route not found`.
2. **`nodeKey` là trường `key`** do `GET /signals/{roomId}` trả về —
   `central-broker-vss`, `bcm-can`, `pwt-can`, `drive-controls`, `battery-sensor`,
   `seatbelt-sensor`, `tirepressure-sensor` — **không phải** UUID node.

**Đã chạy thật 07/08, tất cả 200.** Evidence: `evidence/carsky/signals-rest-0808/`.

- `central-broker-vss` trả **1.268 tín hiệu VSS** kèm metadata.
- Vòng ghi → đọc lại đã chứng minh: `Driver.Temperature` = `null` →
  `POST /actuate {value:24.0}` → `200 {"ok":true,"sent":1}` → `POST /values` = `24`.

**Giới hạn phải khai khi trích:**

| Giới hạn | Chi tiết |
|---|---|
| Đây là REST gọi thẳng KUKSA | **Không có APK, không VHAL, không SafetyGuard** trong đường này. Nó là công cụ **đo**, không phải core flow của sản phẩm |
| Ghi VSS từ ngoài **không lan xuống CAN** | `POST /values` trên `bcm-can` trả `{"values":[]}`; SSE 20 giây chỉ nhận `ping`. Chuỗi VSS→CAN được kích hoạt từ phía VHAL, nên **vẫn phải có APK chạy** mới đóng được chuỗi |
| ⚠️ `actuate` trên `drive-controls`/`vcu/Speed` **không sinh sự kiện cho script VCU** | Cờ `actuate` chỉ dành cho KUKSA. **Đặt tốc độ phải qua slider GPIO Panel**; REST chỉ để **đọc lại** (phát hiện 19/08) |

---

## 4. 🚫 Cả họ endpoint điều khiển VM qua REST đang chết (Conduit)

```
POST /api/v1/vms/{roomId}/{nodeKey}/adb-shell | /shell | /screenshot | /tap | /text | /key | /swipe
POST /api/v1/deployments/{roomId}/adb-exec/{nodeKey}
POST /api/v1/deployments/{roomId}/container-exec/{nodeKey}

-> 502 {"error":"SERVICE_UNAVAILABLE","message":"Conduit service not configured"}
```

Bốn phép thử loại trừ nguyên nhân từ phía đội:

1. Mọi endpoint **không** cần conduit đều 200 với cùng API key → không phải quyền.
2. Lỗi y hệt trên **script-node** (`container-exec`), không riêng node Android.
3. `/account-limits/effective/…` **không có cờ nào** bật/tắt conduit → không phải quota.
4. Thử lại với room vừa deploy sạch, 22/22 `Running` → vẫn `502`.

⚠️ Đoán route kiểu `/deployments/{room}/nodes/{node}/shell` sẽ nhận `404 Route not
found` — **404 đó không phải Conduit chết**, chỉ là gõ sai địa chỉ.

✅ **Nhưng widget `IVI ADB` và widget Shell trong web UI vẫn chạy** — xem
[07 — APK, Artifact & ADB](07-APK-ARTIFACT-ADB.md).

---

## 5. Đọc log — cần `?container=user`

Gọi thẳng endpoint log với container node trả **502 trông y hệt lỗi Conduit nhưng
không phải**:

```
502 {"error":"UPSTREAM_ERROR","message":"Blueprint service error: 500",
     "details":{"upstream":"ApiError: a container name must be specified for pod
     b8eada00-..., choose one of: [user sidecar] ..."}}
```

Pod của container node có **hai container**: `user` (image của mình) và `sidecar`
(`nydus_sidecar_native`, lo eth-tunnel/silkit). `openapi.json` **chỉ khai `tail` và
`since`** — không có tham số container, nhưng **server vẫn nhận**:

```bash
curl -H "x-api-key: $KEY" "$B/deployments/$ROOM/logs/$NODE?container=user&tail=200"
```

`?container=sidecar` cho xem mạng của node (`TAP 'e-eth' configured: 10.99.0.3/24`)
— đây là cách **lấy IP thật** của một container node.

⚠️ **Log chỉ sống theo vòng đời pod, Loki rỗng.** Trước MỌI thao tác restart, nếu
phiên có dữ liệu cần giữ thì kéo log trước.

⚠️ **`/logs/{node}` không phải logcat.** Với node Android nó trả log của pod
(WebRTC `rtc_source_native … UDP throughput`). `VIVA_TRACE` **không** nằm ở đây.

---

## 6. Ba bất thường phải biết trước khi tin mã HTTP

| Bất thường | Chi tiết |
|---|---|
| `restart/{node}` trả **500 body rỗng nhưng vẫn thực thi** | Node đi `Provisioning → Running` sau ~50–60s. Workflow dùng `curl -f` coi 500 là lỗi → rollback → **deploy tự huỷ chính nó**, và log báo `failure` nên trông như nền tảng hỏng |
| `find?device=` trả `[]` dù room đang chạy | `/status` báo `RUNNING`, `/nodes` trả 22/22, nhưng `find` rỗng và `/devices` báo `IDLE`. Bản ghi deployment lệch khỏi thực tế. Sửa: xoá deployment rồi dựng lại |
| Redeploy fail mà API **không cho biết vì sao** | K8s giữ ReplicaSet cũ chạy tiếp khi pod mới không lên được → node vẫn `Running`, pod hỏng biến mất khỏi mọi endpoint. **Không có endpoint liệt kê pod** trong 73 route; `list_pods(roomId)` chỉ tồn tại ở tầng MCP |

> **Quy tắc:** với CarSky, phán xét bằng **hệ quả quan sát được** (phase của node
> đổi, giá trị tín hiệu đổi), không bằng mã HTTP.

---

## 7. Wrapper Go có sẵn trong repo

`backend/internal/infrastructure/carsky/client.go` bọc bốn endpoint đã xác nhận,
dùng qua CLI `viva-tools`:

```bash
go run ./cmd/viva-tools carsky blueprint export --id <blueprintId> --out backup.json
```

```bash
go run ./cmd/viva-tools carsky blueprint clone --id <blueprintId> --backup-out backup.json --clone-out clone.json
```

```bash
go run ./cmd/viva-tools carsky nodes --room <roomId> --out nodes.json
```

```bash
go run ./cmd/viva-tools carsky adb-tunnel --room <roomId>
```

`blueprint clone` **luôn export backup trước và từ chối clone nếu backup lỗi**.

⚠️ Client đọc credential từ `backend/.env`. Các endpoint `POST` trong client này ban
đầu viết từ tài liệu HTML chứ chưa gọi thật — đối chiếu lại với `openapi.json`
**và** `Car-Sky-Platform.html` trước khi tin.
