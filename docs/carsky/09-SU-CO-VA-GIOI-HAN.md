# 09 — Tra sự cố, giới hạn nền tảng và câu hỏi còn treo

---

## 1. Tra theo triệu chứng

| Triệu chứng | Nguyên nhân thật đã gặp | Đi đâu |
|---|---|---|
| `401` trên mọi endpoint | Dùng JWT của phiên web thay vì API key; hoặc key trong cookie | [02 §1](02-API-REFERENCE.md) |
| `404 Route not found` cho endpoint spec có ghi | `openapi.json` khai thiếu tiền tố `/blueprints` | [02 §2](02-API-REFERENCE.md) |
| `404` khi gọi `GET /signals/.../values` | Route đó là **POST** | [02 §3.3](02-API-REFERENCE.md) |
| `502 Conduit service not configured` | Cả họ điều khiển VM qua REST chưa được bật | [02 §4](02-API-REFERENCE.md) |
| `502 UPSTREAM_ERROR … choose one of: [user sidecar]` | Thiếu `?container=user` | [02 §5](02-API-REFERENCE.md) |
| `500` body rỗng khi restart node | Bình thường — **lệnh vẫn chạy** | [05 §5](05-VONG-DOI-DEPLOYMENT.md) |
| `find?device=` trả `[]` dù room chạy | Bản ghi deployment lệch thực tế | [02 §6](02-API-REFERENCE.md) |
| Redeploy báo `N node(s) failed` mà 22/22 `Running` | ReplicaSet cũ vẫn phục vụ; pod hỏng vô hình | [05 §6](05-VONG-DOI-DEPLOYMENT.md) |
| Đổi image xong mà pod vẫn image cũ | `restart` không đọc lại blueprint | [05 §4](05-VONG-DOI-DEPLOYMENT.md) |
| Đổi `env` xong mà `/health` vẫn giá trị cũ | Deployment giữ snapshot config lúc tạo | [05 §3](05-VONG-DOI-DEPLOYMENT.md) |
| `curl` từ guest tới node ASR **timeout** (exit 28) | ARP cache trỏ MAC cũ sau restart container | [04 §3](04-MANG-TRONG-ROOM.md) |
| `curl` từ guest **refused** | Tiến trình/cổng trong container, không phải mạng | log node `?container=user` |
| `eth1` UP nhưng không có `inet` | Android không chạy DHCP client trên interface lạ | [04 §2](04-MANG-TRONG-ROOM.md) |
| `ping -I eth1` thông, `ping` thường không | Policy routing Android, **không** phải lỗi L2 | [04 §2](04-MANG-TRONG-ROOM.md) |
| `Cleartext HTTP traffic to 10.99.0.3 not permitted` | Thiếu địa chỉ trong `network_security_config.xml` | [04 §4](04-MANG-TRONG-ROOM.md) |
| App chết ngay lúc mở, **crash buffer rỗng** | `HttpRemoteAsrTransport.validatedEndpoint` ném trong `Application.onCreate` | [04 §4](04-MANG-TRONG-ROOM.md) |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Cùng package, khác khoá debug | [07 §3](07-APK-ARTIFACT-ADB.md) |
| APK 305 MB thay vì 227 MB, hash không tái lập | Build incremental không compact archive | [07 §4](07-APK-ARTIFACT-ADB.md) |
| Nói tiếng Việt mà nhận sai hết | App mặc định `ENGLISH` | [07 §5](07-APK-ARTIFACT-ADB.md) |
| Kéo slider mà CAN/KUKSA không đổi | Script node mất subscription | [08 §2.1](08-PREFLIGHT-VA-KHOI-PHUC.md) |
| App đọc `Vehicle.Speed = 0.0` dù gateway push đúng | 🔴 `use_local_fake_server=true` trong image AAOS | [§2 dưới đây](#2-giới-hạn-đã-xác-nhận-của-nền-tảng) |
| Web ADB `Connection closed (code 1006)` | `adbd` trong guest lỗi | [07 §7](07-APK-ARTIFACT-ADB.md) |
| App biến mất khỏi màn hình | RAM thấp, Activity bị huỷ, process còn | [07 §8](07-APK-ARTIFACT-ADB.md) |
| Widget IVI Screen *"Input is not available"* | Touch part mất sau reboot | [07 §8](07-APK-ARTIFACT-ADB.md) |

---

## 2. Giới hạn đã xác nhận của nền tảng

### 🔴 VHAL của image AAOS nối vào fake server nội bộ

```
ro.vendor.vehiclehal.server.use_local_fake_server = true
ro.boot.vendor.vehiclehal.server.cid  = 1     (loopback)
ro.boot.vendor.vehiclehal.server.port = 9210
```

Hai tiến trình cùng chạy: `…-fake-hardware-grpc-server` (PID 477) và
`…-trout-service` (PID 478, client). Client nối vào fake server nội bộ, **không bao
giờ nối tới IVI Gateway**. `ro.vendor.*` nướng trong vendor partition — **không
`setprop` đè được**.

⇒ Mọi mốc readback qua `CarPropertyManager` bị chặn ở đây. **Không phải lỗi app,
không phải thiếu quyền.** Evidence: `evidence/c2/vhal-local-fake-server-blocker-0819.txt`.

### 🚫 Pin `ETHERNET` không tạo được bằng API

```
pinType hop le toan cuc            : VHAL | KUKSA | CAN | LIN | VIDEO | GPIO | GENERIC
pinType hop le tren container node : CAN, KUKSA, VIDEO
```

Đây là **giới hạn thiết kế, không phải route chưa bật**. Khác biệt quan trọng khi đi
hỏi BTC: câu hỏi đúng là *"thêm container node có mạng bằng cách nào nếu không dùng
UI?"*, và câu trả lời có thể là *"không có, dùng UI"*.

🔴 **Đừng `DELETE` node rồi tạo lại** để né — đường một chiều.

### 🚫 `import` không nhận lại bản `export` của chính nền tảng

Lấy chính `backend/carsky/blueprint-VIVA-deploy-backup.json` (do
`GET /blueprints/{id}/export` sinh ra) đẩy vào `POST /blueprints/import` → **400**,
vì chứa pin `ETHERNET`.

| Thứ | Vai trò thật |
|---|---|
| `POST /blueprints/{id}/clone` | **Đây mới là đường rollback** |
| File JSON export | **Tài liệu và bằng chứng**, không phải ảnh chụp khôi phục được |

### 🚫 Họ endpoint điều khiển VM qua REST (Conduit) — 502 từ 02/08

### 🚫 Không đổi được config node của deployment đang chạy

Đã kiểm cạn 4 đường 20/08. Xem [05 §3](05-VONG-DOI-DEPLOYMENT.md).

### 🚫 Việc khác không làm được (kiểm 19–20/08)

| Muốn làm | Trạng thái |
|---|---|
| `vm_tunnel_open` → local ADB | ❌ port trả về là `localhost` của **máy chủ MCP** |
| Đặt tốc độ bằng REST `actuate` trên GPIO | ❌ không sinh sự kiện cho VCU — **phải kéo slider** |
| `periodic/start` trên GPIO | ❌ `"not supported by this signal source"` (CAN/KUKSA thì được) |
| Tải artifact qua REST | ❌ 5 đường thử đều 404 — phải copy link từ UI |
| Guest ra internet | ❌ từ 19/08 không còn route |

### Quota

```
MAX_DEVICES = 5 · MAX_NODES_PER_BLUEPRINT = 30
MAX_CONCURRENT_DEPLOYMENTS = 2 · MAX_SKYCRAFT_PER_BLUEPRINT = 2
```

---

## 3. Còn treo — chưa thử hoặc chưa giải quyết

| Việc | Trạng thái |
|---|---|
| Cấu hình `eth1` bền qua reboot (EthernetService trong artifact AAOS) | ❌ CHƯA THỬ — không sửa được từ shell |
| Biến thể `real` chạy VHAL **thật** qua pin `vhal` | 🔴 Bị chặn bởi `use_local_fake_server` — cần BTC can thiệp vào image |
| `DELETE /blueprints/nodes/{id}`, `DELETE /blueprints/pins/{id}` | ❌ CHƯA THỬ |
| Vì sao `Redeploy` không áp được image | Không rõ cơ chế — chỉ biết hiện tượng |
| Conduit (`adb-exec`, `container-exec`, `vms/*`) | 502 từ 02/08, chưa được bật |
| Hai blueprint trùng tên `VIVA-deploy-clone-0803` | Nên đổi tên bản `7175eb09-…` |
| Tự động hoá bước cài APK | Vẫn phải bấm tay qua widget — không có adb host thật |
| URL tải artifact qua REST | KHÔNG TÌM ĐƯỢC |

---

## 4. Câu hỏi cho BTC / mentor

Ghi nguyên văn để gửi được ngay:

> **1. Conduit.** Cả họ `POST /vms/{room}/{node}/*`, `/deployments/{room}/adb-exec/*`
> và `/container-exec/*` trả `502 {"error":"SERVICE_UNAVAILABLE","message":"Conduit
> service not configured"}` từ 02/08, kể cả khi room vừa deploy sạch và 22/22 node
> `Running`. Mọi endpoint không cần conduit đều 200 với cùng API key. Conduit có
> được bật cho tài khoản đội không ạ?

> **2. Pin ETHERNET.** `POST /blueprints/nodes/{id}/pins` và `/blueprints/import`
> chỉ nhận `pinType` trong `VHAL|KUKSA|CAN|LIN|VIDEO|GPIO|GENERIC`, nhưng blueprint
> do nền tảng sinh ra lại có pin `ETHERNET`. Đội thêm Container Node có mạng bằng
> API kiểu gì, hay bắt buộc phải qua UI? Và có chủ đích để `import` không nhận lại
> được bản `export` không ạ?

> **3. VHAL fake server.** Image AAOS của node skycraft khai
> `ro.vendor.vehiclehal.server.use_local_fake_server = true`, nên VHAL client nối
> vào fake server nội bộ (`cid=1`, port 9210) thay vì tới IVI Gateway. Gateway push
> đúng (`[igw] → vhal 0x11600207 pushed = 16.6666`) nhưng app đọc `0.0`. Có image
> hoặc cờ boot nào để VHAL nối ra gateway thật không ạ?

> **4. Redeploy.** `Redeploy` báo `Partial redeploy: N node(s) failed` trong khi
> `GET /deployments/{room}/nodes` trả 22/22 `Running` và log pod không đổi. Có
> endpoint nào liệt kê pod hoặc lấy lý do fail không ạ?

> **5. Artifact.** Có đường REST nào tải nội dung artifact về không ạ? 5 đường thử
> (`/download`, `/files/…`, `?path=`) đều 404, hiện phải copy link từ UI.

---

## 5. Nguồn

| Nguồn | Vai trò |
|---|---|
| `docs/platform/Car-Sky-Platform.html` | **Tài liệu vận hành chính thức**, 39 mục, nội dung trong biến JS `EMBEDDED_CONTENT`. Là nguồn đúng khi `openapi.json` mâu thuẫn. Mục hữu ích nhất: *API & MCP Tools*, *Container Node*, *Ethernet Bridge Node*, *Triển Khai Blueprint*, *Thiết Bị & Widget*, *Cẩm Nang Sửa Lỗi FAQ* |
| `GET /api/v1/openapi.json` | 73 endpoint. ⚠️ **Đường dẫn có thể sai** |
| `docs/backend-docs/carsky-api.md` | Nhật ký khám phá theo thời gian |
| `docs/backend-docs/carsky-runbook.md` | Sổ tay vận hành 07–08/08 |
| `vong2/35-NHAT-KY-CARSKY-19-08.md` | Phiên 19/08 — bản `real`, USB image, root cause VHAL |
| `vong2/37-RUNBOOK-PREFLIGHT-CARSKY.md` | Checklist trước phiên |
| `evidence/carsky/`, `evidence/c2/` | Bằng chứng chạy thật |
