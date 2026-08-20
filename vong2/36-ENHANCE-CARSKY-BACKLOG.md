# 36 — Backlog enhance CarSky (theo năng lực nền tảng chưa khai thác)

> Lập 19/08/2026 · dùng để **tracking**, cập nhật trạng thái tại chỗ.
> Góc nhìn: barem ô Align đo *"mức hiểu và **sử dụng thực chất** năng lực nền tảng"*.
> Đội mới dùng ~4 năng lực (artifact · signals REST · logs · USB proxy).
> Mỗi mục dưới đây gắn với một ô điểm cụ thể.
>
> Trạng thái: ⬜ chưa làm · 🔄 đang làm · ✅ xong · ❌ đã thử, không dùng được · ⚠️ chưa xác minh

---

## Nhóm A — Gỡ nút cổ chai mic (chặn tất cả)

| # | Việc | Ăn ô | Trạng thái |
|---|---|---|---|
| A1 | Widget **Text-to-speech** — nếu bơm được audio vào room/VM thì vừa gỡ mic, vừa cho phát lại **cùng một câu y hệt N lần** (thứ giọng người không làm được) | ① tính đúng −2 · ① ổn định −2 | ⬜ ⚠️ chưa thử |
| A2 | Nếu A1 hỏng: chẩn đoán mic trong guest `dumpsys media.audio_flinger \| grep -A5 -i "input\|record"` — phân biệt "widget không truyền" với "app không mở được mic" | (chẩn đoán) | ⬜ |

---

## Nhóm B — Nâng chất evidence bằng chính công cụ nền tảng

| # | Việc | Ăn ô | Trạng thái |
|---|---|---|---|
| B1 | **Nút `Record`** trên khung IVI Screen. Barem đòi *"output **từ CarSky**"*: video OBS là output của máy mình, video Record là output của nền tảng — khác hạng, chi phí một cú click | ④ Evidence 2/4 | ⬜ 🔴 giá trị cao / công gần 0 |
| B2 | **SSE `/signals/{room}/{node}/subscribe`** — stream tín hiệu suốt phiên, có timeline đóng dấu bởi nền tảng, ghép với logcat theo mốc giờ | ② observability −1 | ⬜ (tôi viết script được) |
| B3 | `POST /container-file/{node}` để đọc file log trong container | ④ Evidence | ❌ **502 Conduit** — xem mục dưới |

---

## Nhóm C — Tự động hoá để có "repeated run" 🔄 ĐANG LÀM

| # | Việc | Ăn ô | Trạng thái |
|---|---|---|---|
| C1 | `/signals/.../periodic/start` cho `vcu/Speed` | ① ổn định −2 | ❌ **KHÔNG DÙNG ĐƯỢC** |
| C2 | Widget **Road Simulator** — mô phỏng hành trình (tăng tốc/phanh/dừng) thay vì hai điểm rời 0/60 | ① biên −1 · ① ổn định −2 | ⬜ ⚠️ chưa thử — **hy vọng còn lại của nhóm C** |

### C1 — kết quả thử 19/08 (đã chạy thật)

```
POST /signals/{room}/drive-controls/periodic/start
  {"path":"vcu/Speed","value":30,"intervalMs":1000}
→ HTTP 400
  {"error":"VALIDATION_ERROR",
   "message":"Signal source: Periodic (cyclic) send is not supported by this signal source."}
```

Kiểm nguồn nào hỗ trợ (`GET /signals/{room}/{node}/periodic`):

| Nguồn | Kết quả | Kết luận |
|---|---|---|
| `drive-controls` (GPIO) | 400 VALIDATION_ERROR khi start | ❌ không hỗ trợ |
| `pwt-can` | `{"activePaths":[]}` | ✅ hỗ trợ |
| `bcm-can` | `{"activePaths":[]}` | ✅ hỗ trợ |
| `central-broker-vss` | `{"activePaths":[]}` | ✅ hỗ trợ |

**Vì sao vẫn không dùng được cho ablation tốc độ:** `periodic/start` chỉ lặp **một giá
trị cố định**, không phải kịch bản biến thiên. Và dù bơm thẳng `PWT_VehicleSpeed` lên
CAN được, **VCU vẫn publish cùng signal đó mỗi 100 ms** theo cycle time của DBC → hai
nguồn tranh chấp, giá trị nhấp nháy. ⇒ **Slider panel Drive Controls vẫn là đường duy
nhất** để đặt tốc độ (khớp phát hiện cũ: REST `actuate` trên GPIO cũng không sinh sự
kiện cho VCU; cờ `actuate` chỉ dành cho KUKSA).

Periodic vẫn có thể hữu ích cho việc **khác**: mô phỏng tín hiệu mà không ECU nào phát
(tire pressure, nhiễu…). Không phải ưu tiên hiện tại.

### ⚠️ Phát hiện phụ khi test C1 — ĐÃ RÚT LẠI (kết luận ban đầu SAI)

**Kết luận ban đầu (SAI):** "đồng hồ node CAN lệch +5h32m".

**Kiểm chứng lại 19/08 09:57Z** — đọc CAN kèm giờ thực ngay tại thời điểm gọi:

```
NOW(UTC)                        = 2026-08-19T09:57:32Z
PWT_VehicleSpeed/Speed_kph ts   = 2026-08-19T09:57:38.214Z   → lệch 6 giây
```

⇒ **Node CAN KHÔNG lệch đồng hồ.**

**Nguyên nhân thật của con số gây hiểu nhầm:** GPIO chỉ cập nhật `timestamp` khi
giá trị **đổi** (kéo slider), còn CAN phát theo chu kỳ DBC 100 ms nên `timestamp`
**luôn mới**. So một mốc cũ (GPIO, lần cuối đổi) với một mốc mới (CAN, vừa phát)
sinh ra khoảng chênh trông như lệch đồng hồ.

**Luật rút ra cho evidence:** khi trích readback nhiều tầng, phải ghi **giờ thực lúc
gọi** bên cạnh timestamp của từng tầng, và hiểu rằng `timestamp` của GPIO/KUKSA là
*thời điểm giá trị đổi lần cuối*, còn của CAN là *thời điểm phát gần nhất*. Hai loại
mốc khác nhau về ngữ nghĩa — không so trực tiếp để suy ra nhân quả.

## Nhóm D — Đường chưa ai đi: MCP server (42 tool)

| # | Việc | Ăn ô | Trạng thái |
|---|---|---|---|
| D0 | Kết nối MCP server | — | ✅ **XONG** — không cần tải package |
| D1 | `vm_tunnel_open` → local ADB | ② identity | ❌ **KHÔNG DÙNG ĐƯỢC qua HTTP** |
| D2 | `container_shell` / `container_file_read` vào container ASR | ④ Evidence | ❌ 502 (Conduit) |
| D3 | Các tool KHÔNG qua Conduit: `list_pods`, `pod_logs`, `search_logs`, `get_signal_values`, `send_signals`, `subscribe_signals`, blueprint/artifact CRUD | tiện ích | ⬜ dùng được, chưa khai thác |

### D0 — MCP server chạy được qua HTTP (phát hiện 19/08) ✅

**Không cần tải package node.** CarSky host sẵn MCP tại `POST https://hackathon-2.carsky.io/mcp`,
xác thực bằng `x-api-key`, transport Streamable HTTP:

```
Header bắt buộc: Accept: application/json, text/event-stream
initialize -> {"serverInfo":{"name":"a8-mcp","version":"0.1.0"}}
tools/list -> 44 tool
```

Script gọi nhanh đã lưu ở scratchpad (`mcp.sh <tool> <json-args>`).
`GET /mcp` trả **406** khi thiếu header Accept — đừng đọc nhầm là "không có endpoint".

### D1/D2 — vì sao không dùng được

- **`vm_tunnel_open` trả `{"port":15556,"host":"localhost"}` với `success:true`**, nhưng đó là
  `localhost` **của máy chủ MCP** (tunnel là "built-in TCP↔WebSocket bridge" chạy phía server).
  `adb connect localhost:15556` từ máy dev → `actively refused`. Muốn dùng phải chạy MCP server
  dạng **stdio ngay trên máy mình** — package `mcp/dist/index.js` không có trên npm
  (`@carsky/mcp`, `carsky-mcp` đều 404), phải xin từ BTC/UI.
- **`adb_shell`, `container_shell`, `find_text`/`ui_tree` đều trả 502** — cùng đi qua Conduit
  đang chết. Đúng cảnh báo ở `carsky-analysis/04` §4. MCP **không** vòng qua được Conduit.
- `screenshot` trả 404 *"not a skycraft VM with a configured part prefix"* (thử lúc node vừa
  restart; chưa thử lại sau khi part đăng ký xong).

---

## Nhóm E — Blueprint

| # | Việc | Ăn ô | Trạng thái |
|---|---|---|---|
| E1 | **Deploy room thứ hai** để thử nghiệm an toàn | ④ Align | ✅ **XONG 20/08** — `VIVA-asr-prompt-0820` trên device `VIVA (Copy)` (room `wcmfnwigjse4hv9r8s0e3`), 22/22 Running trong ~3 phút. Đội nay đã **deploy** bản clone chứ không chỉ clone |
| E2 | Sửa `nydus.kuksa.connect("http://10.99.0.3:55555")` trong IVI Gateway — dọn rác log ERROR vô tận + sửa va chạm địa chỉ (F6 của doc 32). Làm trên room test trước | ④ Ranh giới | ⬜ |
| E4 | **Domain biasing `ASR_INITIAL_PROMPT`** | ③ lợi ích baseline | ✅ **ĐANG CHẠY** ở room mới — `/health` trả prompt đầy đủ. Room demo cũ vẫn `null` ⇒ có sẵn **cặp A/B song song** |
| E3 | PATCH pin thêm propId HVAC chuẩn (`358614275`, `356517120`) | ④ Độ sâu | ⏸️ **HOÃN** — fake server còn bật thì thêm vào cũng vô nghĩa. Chờ mentor trả lời image |

---

## Ưu tiên nếu chỉ chọn ba

1. **A1 / A2** — không gỡ mic thì mọi thứ khác đứng yên
2. **D1 (MCP `vm_tunnel_open`)** — nếu chạy, nhân đôi tốc độ mọi phiên còn lại
3. **B1 (nút Record)** — một cú click, nâng hạng video từ "output của đội" thành "output của nền tảng"

Ba cái độc lập, làm song song được.

---

## Nhật ký thay đổi

| Ngày | Thay đổi |
|---|---|
| 19/08 | Lập backlog. Test C1 → ❌ GPIO không hỗ trợ periodic |
| 19/08 | ⚠️ **Rút lại** claim "đồng hồ CAN lệch 5h32m" — kiểm lại thấy lệch 6 giây. Nguyên nhân thật: ngữ nghĩa timestamp GPIO (đổi lần cuối) khác CAN (phát gần nhất) |
| 19/08 | D0 ✅ MCP server chạy qua HTTP, 44 tool. D1 ❌ tunnel là localhost của server. D2 ❌ 502 Conduit |
| 19/08 | Sau reboot node skycraft: `eth1` **lại mất IPv4** (nền tảng không tự cấp) · shell **mất root** · app còn cài + `CAR_SPEED` vẫn granted · chuỗi GPIO→CAN→KUKSA vẫn thông |
| 20/08 | E4: PATCH `ASR_INITIAL_PROMPT` vào blueprint ✅ (đường đúng `/blueprints/nodes/{id}`, openapi ghi sai). Nhưng Redeploy + Restart Node **không** áp dụng được vào deployment đang chạy — deployment giữ snapshot lúc tạo. Kết luận: đổi config node của room đang chạy là không làm được |
| 20/08 | E1 ✅ deploy blueprint (đã có prompt) sang device `VIVA (Copy)` → room `wcmfnwigjse4hv9r8s0e3`, 22/22 Running ~3 phút. `/health` trả `initial_prompt` đầy đủ ⇒ **xác minh: config chỉ áp dụng khi TẠO deployment mới**. Giờ có hai room song song: cũ (không prompt, có app+evidence) và mới (có prompt, chưa có app) — sẵn cho A/B |

### B3 — đã dò cạn 20/08: KHÔNG có đường API nào đọc được `face-logcat`

`face-logcat` là **log-source part**: sidecar tail `/logcat/logcat.txt` rồi đẩy qua
**WebSocket của room** tới widget. Không có REST tương ứng.

| Đường thử | Kết quả |
|---|---|
| `/deployments/{room}/logs/{node}?container=sidecar` | chỉ log nydus sidecar, **không có logcat** |
| `?container=user` trên node skycraft | node này không có container `user` |
| `POST /container-file/{node}?direction=pull&path=/logcat/logcat.txt` | **502 Conduit** (cú pháp đúng là query param `direction=pull\|push` + `path`, không phải body — openapi để requestBody rỗng) |
| `GET /vms/{room}/{node}/logs` | **502 Conduit** |
| Loki `/logs/{node}/search?q=VIVA_` | 0 stream |
| route riêng cho "part" / "log-source" | không tồn tại trong openapi |

**Cách duy nhất: copy tay từ widget.** ⚠️ Icon mũi tên xuống trên widget là
**scroll-to-bottom**, KHÔNG phải download — widget không có chức năng xuất file.

Quy trình đo thực tế:
1. **🗑** xoá buffer trước khi nói (log sạch, dễ đọc)
2. Ô **Filter**: gõ `VIVA_` → chỉ còn dòng của app
3. Nói các câu cần đo
4. Bấm icon **↗ (mở cửa sổ riêng)** hoặc **⤢ (phóng to)** → bôi đen → Ctrl+C

⇒ Ràng buộc "log chết theo pod" **vẫn còn** với log container; logcat của guest thì
**phải copy tay**, không tự động hoá được. Đây là chi phí cố định của mỗi lượt đo —
tính vào thời gian khi lên kịch bản phiên.
| 20/08 | B3 ❌ đã dò cạn: không có API nào đọc `face-logcat` (container-file/vms-logs đều 502 Conduit, Loki rỗng, không có route "part"). Chỉ còn nút download trên widget |
