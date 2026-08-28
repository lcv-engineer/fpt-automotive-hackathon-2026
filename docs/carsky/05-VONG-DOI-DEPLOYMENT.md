# 05 — Vòng đời deployment: dựng, restart, đổi image

> Đây là phần đắt nhất khi làm sai: một thao tác sai xoá sạch APK, cấu hình mạng và
> mọi thiết lập trong app, mất ~30 phút dựng lại.

---

## 1. Mức độ ảnh hưởng của từng thao tác — ĐỌC TRƯỚC KHI BẤM

| Thao tác | Guest reboot? | Mất IP `eth1`? | Mất app? | Mất log node ASR? | Script node mất subscription? |
|---|---|---|---|---|---|
| Restart **node ASR** (container) | ❌ | ❌ | ❌ | ✅ **MẤT** | ❌ (nhưng phải flush ARP — [04 §3](04-MANG-TRONG-ROOM.md)) |
| Restart **VCU / IVI Gateway** (script) | ❌ | ❌ | ❌ | ❌ | (chính nó được sửa) |
| Restart **IVI - Android** (skycraft) | ✅ | ✅ **MẤT** | ❌ (`/data` sống) | ❌ | ❌ |
| **Redeploy** cả room | ✅ | ✅ MẤT | ⚠️ chưa kiểm | ✅ MẤT | ✅ MẤT |
| **DELETE + POST /deployments** | ✅ | ✅ MẤT | ✅ **MẤT** | ✅ MẤT | ✅ MẤT |

⚠️ **Luật vàng:** log container **chỉ sống theo vòng đời pod** và Loki rỗng. Trước
MỌI thao tác restart, nếu phiên có dữ liệu cần giữ thì **kéo log node ASR trước**:

```bash
curl -s -H "x-api-key: $KEY" "$B/deployments/$ROOM/logs/$ASR?container=user&tail=3000" -o asr-node-user.json
```

---

## 2. Dựng một deployment

```
POST /api/v1/deployments   {"blueprintId": "...", "roomId": "...", "name": "..."}
-> 201, status PENDING, namespace room-xxxxxxxx
```

**Thời gian thực đo:**

```
0s     POST /deployments -> status PENDING
20s    22/22 Provisioning
60s    21/22 Running   (chi con IVI - Android)
180s   22/22 Running   <- node skycraft boot Android cham nhat
```

Gỡ: `DELETE /api/v1/deployments/{roomId}`.

### ⚠️ `DELETE /deployments` xoá sạch máy ảo Android

Đây là điều dễ quên nhất và đắt nhất. Nó **không chỉ tắt container** — nó **huỷ cả
VM Android**, node skycraft boot lại từ artifact gốc. Mất hết:

- APK đã cài
- Cấu hình mạng `eth1` (chỉ nằm trong RAM)
- Mọi thiết lập trong app (ngôn ngữ giọng nói, engine ASR, DataStore)

**Không gián đoạn:** dựng trước một deployment thứ hai trên device khác
(`VIVA (Copy)`), xác nhận 22/22, rồi mới xoá cái cũ. Quota cho phép 2.

---

## 3. 🔴 Deployment giữ SNAPSHOT config từ lúc tạo (xác minh 20/08)

Đây là kết luận quan trọng nhất của cả mục này. Mục tiêu thử: thêm
`ASR_INITIAL_PROMPT` (domain biasing) cho node `VIVA ASR` đang chạy.

| Cách | Kết quả |
|---|---|
| UI → view deployment → click node | Inspector chỉ có `View Logs` / `Restart Node` — **không sửa được env** |
| UI → **blueprint editor** → click node | ✅ CÓ form đầy đủ (Image/Command/Args/Environment/Pins) |
| `PATCH /api/v1/blueprints/nodes/{nodeId}` | ✅ **200** — env vào blueprint, UI hiện `Environment (4)` |
| Nút `Redeploy` (chuột phải deployment) | ⚠️ báo *"Partial redeploy: 4 node(s) failed"* nhưng API cho thấy 22/22 `Running`. **Không chạm node ASR** — log pod không đổi |
| `Restart Node` sau Redeploy | Pod mới lên (`model ready` giờ mới) nhưng `/health` **vẫn** `initial_prompt: null` |

⇒ **Sửa blueprint chỉ có tác dụng cho deployment tạo MỚI sau đó.**

### ✅ Cách đúng để áp dụng config node mới

Deploy blueprint (đã sửa) lên **device khác** — quota cho 2 deployment đồng thời:

```bash
curl -X POST -H "x-api-key: $KEY" -H "Content-Type: application/json" \
  -d '{"blueprintId":"6deadb05-...","roomId":"wcmfnwigjse4hv9r8s0e3","name":"VIVA-asr-prompt-0820"}' \
  "$B/deployments"
```

Xác minh trong room mới (guest cũng thiếu IPv4 như mọi room — vá theo [04 §2](04-MANG-TRONG-ROOM.md)):

```
curl -sm 8 http://10.99.0.3:8080/health
-> "initial_prompt":"Lenh dieu khien xe: dieu hoa, nhiet do, do C, ..."   OK
```

**Quy trình đúng:** gom mọi thay đổi config vào blueprint, rồi áp dụng **một lần**
khi buộc phải dựng lại room — thay vì sửa lắt nhắt giữa chừng. Đội có 4 device
(`VIVA`, `VIVA (Copy)`, `Gemini`, `Gemini 2`) nên luôn có chỗ dựng room thử nghiệm
song song mà không đụng room demo.

---

## 4. Đổi image container = XOÁ deployment rồi DỰNG LẠI

Hai cách hiển nhiên đều **không** hoạt động:

| Cách | Kết quả thật |
|---|---|
| `POST /deployments/{room}/restart/{node}` | Chỉ chạy lại pod theo **spec K8s hiện có**, không đọc lại blueprint → pod lên lại vẫn mang image **cũ** |
| Nút `Redeploy` trong Deployment Viewer | `Partial redeploy: 1 node(s) failed`, thử **3 lần** đều vậy, không lộ lý do |

**Phép thử đối chứng đã chứng minh không phải lỗi image:** cùng blueprint, cùng
image `0.2.1`, dựng deployment **mới** trên device thứ hai → ASR `Running` sau
**9 giây**. Khác duy nhất là đường đi.

### Quy trình đầy đủ

```
1. build + push image                                   (CI, workflow_dispatch)
2. PATCH /api/v1/blueprints/nodes/{nodeId}  doi image   (API)
   -> DOC config cu roi TRON; gui moi `image` co the xoa sach env
3. DELETE /api/v1/deployments/{roomId}
4. POST   /api/v1/deployments {blueprintId, roomId, name}
5. cho ~3 phut -> 22/22 Running
6. CAI LAI APK + chay lai khoi lenh mang eth1
```

🔴 **Làm ngoài giờ tổng duyệt.** Bước 3 huỷ mọi thứ trên VM Android.

---

## 5. `restart/{node}` trả 500 nhưng VẪN CHẠY

```
POST /api/v1/deployments/{roomId}/restart/{node}  ->  500, body RONG
```

Không có `error`, không có `message`. **Nhưng lệnh vẫn thực thi:** node chuyển
`Provisioning` ngay sau đó rồi `Running` sau ~50–60 giây. Script-node stateless,
không tốn quota.

**Hậu quả đã xảy ra thật:** workflow dùng `curl -f` coi 500 là lỗi → step fail →
rollback chạy → PATCH đè image cũ lên bản mới vừa ghi thành công. **Deploy tự huỷ
chính nó**, và log báo `failure` nên trông như nền tảng hỏng.

**Đừng thử lại nhiều lần.** Chờ `phase` đi `Provisioning → Running`.

---

## 6. Khi Redeploy hỏng, API KHÔNG cho biết vì sao

Đã thử hết, không đường nào lộ pod đang fail:

| Cách | Kết quả |
|---|---|
| `GET /deployments/{room}/nodes` | `phase: Running`, `message: null` — 22/22 |
| `GET .../nodes/watch` (SSE, 23 event) | node **chưa từng rời** `Running` |
| `GET .../logs/{node}?container=user` | vẫn trỏ **pod cũ**, log dừng ở lần restart trước |
| `?container=sidecar` | như trên |
| `GET .../logs/{node}/search` | `result: []` |
| `container-exec` | 502 Conduit |

**Cơ chế:** K8s giữ ReplicaSet cũ chạy tiếp khi pod mới không lên được. Node vẫn
`Running` (đúng — *có* một pod đang chạy), dịch vụ vẫn phục vụ, còn pod hỏng biến
mất khỏi mọi endpoint. **Không có endpoint liệt kê pod** trong 73 route.

> **Hệ quả thực dụng:** redeploy fail thì **đừng cố quan sát**. Liệt kê khác biệt
> giữa bản chạy được và bản hỏng rồi **đổi từng biến**. Hoặc tốt hơn: **dựng một
> môi trường thứ hai để so sánh** — đó là thứ cho câu trả lời trong 9 giây sau khi
> ba vòng quan sát đều mù.

---

## 7. Deployment không sống mãi

Đã biến mất một lần (05/08). Triệu chứng: giao diện web báo
`Data WebSocket closed (code 4000)`, mọi part hết giờ, và:

```
GET /deployments/v37aa3knc6t1embelr5yi/nodes
404 {"error":"NOT_FOUND","message":"No deployment found for this room in current profile"}
```

Cả 4 device đều `operational: IDLE`, `lastSeenAt: null`.

**Kiểm một dòng trước mỗi buổi làm việc:**

```bash
go run ./cmd/viva-tools carsky nodes --room $env:CARSKY_ROOM_ID
```

`404` nghĩa là **phải deploy lại**, không phải nền tảng hỏng.

---

## 8. Ba lần chẩn đoán sai — ghi lại vì phương pháp quan trọng hơn kết luận

| Kết luận sai | Sai ở đâu | Cái gì bác bỏ nó |
|---|---|---|
| *"`PATCH /nodes/{id}` 404 → CarSky không cho đổi image bằng API"* | Lấy đường dẫn từ `openapi.json`, không đối chiếu `Car-Sky-Platform.html` — tài liệu nằm sẵn trong repo từ 31/07 | Gọi đúng path → 200 |
| *"image amd64-only không hợp cluster"* | `platforms: linux/amd64` là do chính mình thu hẹp dựa trên một comment chưa ai kiểm | Build đa kiến trúc, Redeploy **vẫn** fail |
| *"cluster không kéo được image mới"* | Suy luận từ việc quan sát bị mù, không phải từ bằng chứng | Dựng deployment mới cùng image → ASR `Running` sau **9 giây** |

**Hai bài học:**

1. Khi ba vòng quan sát đều mù, thứ cho câu trả lời không phải vòng quan sát thứ tư,
   mà là **dựng một môi trường thứ hai để so sánh**.
2. **Đọc tài liệu có sẵn trong repo trước** khi suy luận từ spec máy sinh.
