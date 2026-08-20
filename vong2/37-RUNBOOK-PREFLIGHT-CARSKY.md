# 37 — Runbook: kiểm trước phiên & khôi phục sau restart (CarSky)

> Lập 19/08/2026 sau phiên giọng nói thành công đầu tiên trên bản `real`.
> **Lý do tồn tại:** room đã hỏng âm thầm ít nhất hai lần (12/08, và sau mỗi lần
> reboot VM). Không chạy checklist này trước thì phiên demo sẽ vỡ giữa chừng mà
> không ai hiểu vì sao.
>
> Hằng số dùng trong mọi lệnh:
> ```
> ROOM = v37aa3knc6t1embelr5yi
> ASR  = b8eada00-d137-4fdc-a131-2197b1d1356b     (VIVA ASR, container)
> IVI  = cf7fe8d1-0a9c-48fe-9b59-573e3747f2cb     (IVI - Android, skycraft)
> VCU  = faa07ae4-8953-468a-a5b6-4304cb52a6c9     (script-node)
> IGW  = n-4e60c4fe-350e-4333-9e50-0bcd5596a609   (IVI Gateway, script-node)
> ```

---

## PHẦN 1 — Mức độ ảnh hưởng của từng loại restart

Đọc bảng này TRƯỚC khi bấm bất cứ nút restart nào.

| Thao tác | Guest reboot? | Mất IP `eth1`? | Mất app? | Mất log node ASR? | Node script mất subscription? |
|---|---|---|---|---|---|
| Restart **node ASR** | ❌ | ❌ | ❌ | ✅ **MẤT** | ❌ (nhưng phải flush ARP — xem 3.2b) |
| Restart **VCU / IVI Gateway** | ❌ | ❌ | ❌ | ❌ | (chính nó được sửa) |
| Restart **IVI - Android** (skycraft) | ✅ | ✅ **MẤT** | ❌ (`/data` sống) | ❌ | ❌ |
| **Redeploy cả room** | ✅ | ✅ MẤT | ⚠️ chưa kiểm | ✅ MẤT | ✅ MẤT |

⚠️ **Luật vàng:** log container **chỉ sống theo vòng đời pod** và Loki rỗng.
Trước MỌI thao tác restart, nếu phiên có dữ liệu cần giữ thì **kéo log node ASR trước**:

```bash
curl -s -H "x-api-key: $KEY" \
  "$B/deployments/$ROOM/logs/$ASR?container=user&tail=3000" -o asr-node-user.json
```

---

## PHẦN 2 — Checklist TRƯỚC mỗi phiên (~2 phút)

### 2.1 Nền tảng — chạy từ máy dev

```bash
cd backend
KEY=$(grep '^CARSKY_API_KEY=' .env | cut -d= -f2- | tr -d '\r')
B=$(grep '^CARSKY_BASE_URL=' .env | cut -d= -f2- | tr -d '\r')
ROOM=v37aa3knc6t1embelr5yi

# (a) tất cả node Running?
curl -s -H "x-api-key: $KEY" "$B/deployments/$ROOM/nodes" \
  | python -c "import json,sys; ns=json.load(sys.stdin); bad=[n for n in ns if n['phase']!='Running']; print(len(ns),'nodes,',len(bad),'not Running'); [print(' ',n['displayName'],n['phase']) for n in bad]"

# (b) chuỗi tín hiệu còn thông? (ba tầng phải cùng một số)
for pair in "drive-controls:vcu/Speed" "pwt-can:PWT_VehicleSpeed/Speed_kph" "central-broker-vss:Vehicle.Speed"; do
  node=${pair%%:*}; path=${pair#*:}
  printf "%-20s " "$node"
  curl -s -H "x-api-key: $KEY" -H "Content-Type: application/json" \
    -d "{\"paths\":[\"$path\"]}" "$B/signals/$ROOM/$node/values"; echo
done
```

**Ba tầng lệch nhau** ⇒ node script mất subscription → xem 3.1.

### 2.2 Guest — chạy trong web ADB shell

```
ip -o addr show eth1 ; ip route get 10.99.0.3 ; pidof com.sopa.viva_automotive ; id
```

| Quan sát | Nghĩa là | Xử lý |
|---|---|---|
| `eth1` **không có `inet`** | guest cô lập với mạng room, app **không gọi được ASR** | → 3.2 |
| `ip route get` ra `dev buried_eth0` | route sai bảng | → 3.2 |
| `pidof` trống | app chưa chạy | `am start -n com.sopa.viva_automotive/.MainActivity` |
| `uid=2000(shell)` | không root (bình thường sau reboot) | dùng `su 0 <lệnh>` khi cần |

### 2.3 Đường app → ASR node (phép thử quyết định)

```
run-as com.sopa.viva_automotive sh -c 'curl -sm 5 http://10.99.0.3:8080/health'
```

Phải ra `{"status":"ok","model":"phowhisper-tiny-int8",…}`.
Đây là kiểm bằng **đúng danh tính app** (uid 10125), không phải shell — Android định
tuyến theo UID nên shell chạy được **không** đảm bảo app chạy được.

---

## PHẦN 3 — Khôi phục

### 3.1 Node script mất subscription (VCU / IVI Gateway)

Triệu chứng: kéo slider Speed nhưng CAN/KUKSA không đổi; log node VCU im lặng.

```bash
curl -s -X POST -H "x-api-key: $KEY" "$B/deployments/$ROOM/restart/$VCU"
curl -s -X POST -H "x-api-key: $KEY" "$B/deployments/$ROOM/restart/$IGW"
```

⚠️ **API trả HTTP 500 nhưng lệnh VẪN có tác dụng.** Đừng thử lại nhiều lần.
Chờ `phase` đi `Provisioning → Running` (~50–60 s):

```bash
for i in $(seq 12); do sleep 10
  curl -s -H "x-api-key: $KEY" "$B/deployments/$ROOM/nodes" \
   | python -c "import json,sys;[print(n['phase']) for n in json.load(sys.stdin) if n['name']=='$VCU']"
done
```

Xác nhận: kéo slider Speed **đổi giá trị** (đổi thật, không đặt lại số cũ — sự kiện chỉ
sinh khi giá trị thay đổi), rồi đọc lại ba tầng ở 2.1(b).

### 3.2 Guest mất IPv4 trên `eth1` (sau mỗi lần reboot VM)

```
su 0 ip addr add 10.99.0.14/24 dev eth1
su 0 ip route add 10.99.0.0/24 dev eth1 table legacy_system
```

Chờ ~3 giây (route cache cũ còn hiệu lực) rồi kiểm:

```
ip route get 10.99.0.3
```
→ phải ra `dev eth1 table legacy_system src 10.99.0.14`

Rồi kiểm lại 2.3.

**Vì sao là `table legacy_system`:** `ip rule` của Android cho traffic không đánh dấu
(và cả `mark 0x64` của app) khớp rule 18000 → `lookup legacy_system`. Thêm route vào
`table main` hay `table wlan0` **không có tác dụng**. Nếu nghi ngờ, hỏi kernel:
`ip route get 10.99.0.3` và `ip route get 10.99.0.3 mark 0x64`.

### 3.2b 🔴 Sau khi restart node CONTAINER: guest timeout dù node đã Running

**Triệu chứng:** `curl … :8080/health` trả **exit 28 (timeout)** — chú ý: *timeout*,
không phải *refused*. Node phase = `Running`, log node cho thấy uvicorn đã lên
(`model ready in …`), route trong guest vẫn đúng.

**Nguyên nhân:** container mới có **MAC mới**, nhưng **ARP cache trong guest còn trỏ
tới MAC cũ** → gói gửi vào hư không → timeout (nếu là lỗi cổng/tiến trình thì sẽ là
*refused*, không phải *timeout* — dùng chính điểm này để phân biệt).

**Xử lý (trong guest):**
```
su 0 ip neigh flush all
sleep 2
ping -c 2 10.99.0.3
run-as com.sopa.viva_automotive sh -c 'curl -sm 8 http://10.99.0.3:8080/health'
```

Đã xác minh 19/08: sau flush, ping 0.6 ms và `/health` trả lời ngay. **Luôn chạy bước
này sau mỗi lần restart node ASR**, nếu không sẽ tưởng nhầm là node hỏng.

### 3.3 Web ADB shell đứt (`Connection closed (code 1006)`)

1. Bấm **Reconnect** 2–3 lần, cách nhau ~10 s (`adbd` là service của init, thường tự lên)
2. Kiểm phía nền tảng — nếu sidecar spam
   `hybrid-vsock CONNECT 5555 response failed: Connection reset by peer` thì `adbd` trong
   guest đang lỗi:
   ```bash
   curl -s -H "x-api-key: $KEY" "$B/deployments/$ROOM/logs/$IVI?container=sidecar&tail=15"
   ```
3. Cùng đường: restart node `IVI - Android` → **guest reboot** → phải làm lại 3.2

**Đường lấy log không phụ thuộc ADB:** widget **Log** với source **`face-logcat`**
(sidecar tail `/logcat/logcat.txt`). Nên mở sẵn widget này trước mọi phiên — mất shell
giữa phiên vẫn còn `VIVA_TRACE`.

### 3.4 App biến mất khỏi màn hình

Không phải crash. RAM guest ~3.8 G, thường chỉ còn ~330 M → Android hủy Activity nhưng
**giữ process**. Kiểm rồi gọi lại:

```
pidof com.sopa.viva_automotive
am start -n com.sopa.viva_automotive/.MainActivity
```

⚠️ App **không có foreground service** (`dumpsys activity services … → (nothing)`) —
voice pipeline sống trong scope Activity. **Giữ app ở foreground suốt phiên.**

### 3.5 Widget IVI Screen báo "Input is not available"

Touch part chưa đăng ký lại sau reboot. Click widget **IVI Screen** → Inspector →
kiểm **Touch Part** còn `face-touch-panel` không; trống thì chọn lại, có rồi thì đóng/mở
widget. Không chặn việc gõ lệnh ADB hay nói.

---

## PHẦN 4 — Sau khi cài lại APK (nếu có)

Quyền **sống qua reboot** nhưng **mất khi gỡ cài đặt**. Sau mỗi lần `pm install` bản mới:

```
pm grant com.sopa.viva_automotive android.car.permission.CAR_SPEED
pm grant com.sopa.viva_automotive android.car.permission.CAR_ENERGY
dumpsys package com.sopa.viva_automotive | grep -i CAR_SPEED     # phải ra granted=true
dumpsys package com.sopa.viva_automotive | grep -i RECORD_AUDIO   # phải ra granted=true
```

Và đối chiếu hash ba chặng (máy dev = file trong guest = `base.apk`):

```
sha256sum $(pm path com.sopa.viva_automotive | cut -d: -f2)
```

**Chuyển file vào guest phải qua USB image** — guest không có route ra internet, `curl`
từ trong guest sẽ chết. Quy trình: tạo `.img` FAT32 chứa APK → upload artifact category
`USB` → widget **USB Device** → **Plug** → file hiện ở `/sdcard/Music/usb_1/`.

---

## PHẦN 5 — Trước khi kết thúc phiên

1. **Kéo log node ASR** (mất theo pod, không lấy lại được):
   `GET /deployments/{room}/logs/{ASR}?container=user&tail=3000`
2. **Tải logcat** từ widget `face-logcat` (icon mũi tên xuống)
3. `dumpsys media_session | grep -iA3 "state=PlaybackState"` — readback consumer
4. Trả trạng thái room: slider Speed về giá trị ban đầu; **Unplug** USB image nếu không dùng
5. Ghi vào evidence: commit, sha256 APK, digest image node, roomId, giờ chạy

---

## PHỤ LỤC — Việc KHÔNG làm được qua API (đã kiểm 19/08)

| Muốn làm | Trạng thái |
|---|---|
| Đổi `env`/config của node trong một **deployment đang chạy** | ❌ **KHÔNG LÀM ĐƯỢC** — đã thử đủ 4 đường 20/08, xem mục dưới bảng | `PATCH /blueprints/{id}` chỉ sửa name/description; `update_blueprint` (MCP) chỉ `addNode/addPin/addEdge`. Chỉ còn **UI**, hoặc `import_blueprint` + redeploy (thổi bay trạng thái — không nên) |
| `adb_shell`, `container_shell`, `ui_tree`, `find_text` | ❌ 502 — Conduit chết, kể cả gọi qua MCP |
| `vm_tunnel_open` → local ADB | ❌ port trả về là `localhost` **của máy chủ MCP**, không tới được từ máy dev |
| Đặt tốc độ bằng REST `actuate` trên GPIO | ❌ không sinh sự kiện cho VCU — **phải kéo slider** trong widget GPIO Panel |
| `periodic/start` trên GPIO | ❌ `"not supported by this signal source"` (CAN/KUKSA thì hỗ trợ) |

### 🔴 Đã kiểm cạn 20/08: không đổi được config node của deployment đang chạy

Mục tiêu thử: thêm `ASR_INITIAL_PROMPT` (domain biasing) cho node `VIVA ASR`.

| Cách | Kết quả |
|---|---|
| UI, view deployment → click node | Inspector chỉ có `View Logs` / `Restart Node` — không sửa được env |
| UI, **blueprint editor** → click node | ✅ CÓ form đầy đủ (Image/Command/Args/Environment/Pins) |
| `PATCH /api/v1/blueprints/nodes/{nodeId}` | ✅ **200** — env vào blueprint, UI hiện `Environment (4)`. ⚠️ Đường trong `openapi.json` (`/api/v1/nodes/{id}`) là **SAI**, trả 404; đường đúng có tiền tố `/blueprints` |
| `Redeploy` (chuột phải deployment ở sidebar) | ⚠️ báo *"Partial redeploy: 4 node(s) failed"* nhưng API cho thấy 22/22 `Running`. **Không chạm node ASR** — log pod không đổi |
| `Restart Node` sau Redeploy | Pod mới lên (`model ready` giờ mới) nhưng `/health` **vẫn** `initial_prompt: null` |

⇒ **Deployment giữ snapshot config từ lúc tạo.** Sửa blueprint chỉ có tác dụng cho
**deployment tạo mới sau đó**. Muốn áp dụng vào room đang chạy thì phải undeploy +
deploy lại — đổi lấy toàn bộ trạng thái đã dựng (IP `eth1`, log node, subscription
script node), thường **không đáng**.

**Cách làm đúng:** gom mọi thay đổi config vào blueprint, rồi áp dụng **một lần** khi
buộc phải dựng lại room, thay vì sửa lắt nhắt giữa chừng.

**Bài học chung — đừng tin mã trả về, hãy tin hệ quả quan sát được:**
`restart` trả `500` mà node vẫn restart; `Redeploy` báo `4 node failed` mà 22/22 vẫn
`Running`; `PATCH` đường trong openapi trả 404 còn đường trong tài liệu trả 200.
Luôn xác minh bằng trạng thái thật (`phase`, log pod, `/health`).

### ✅ Cách ĐÚNG để áp dụng config node mới: tạo deployment mới (xác minh 20/08)

Sau khi 4 đường trên đều thất bại, thử deploy blueprint (đã có `ASR_INITIAL_PROMPT`)
lên **device khác** — quota cho 2 deployment đồng thời, lúc đó mới dùng 1:

```bash
curl -X POST -H "x-api-key: $KEY" -H "Content-Type: application/json"   -d '{"blueprintId":"6deadb05-...","roomId":"wcmfnwigjse4hv9r8s0e3","name":"VIVA-asr-prompt-0820"}'   "$B/deployments"
# -> 201, namespace room-9pjsm4pz
```

Thời gian lên: node container ~1 phút, **21/22 trong ~1 phút**, riêng `IVI - Android`
(skycraft) mất thêm ~2 phút → tổng ~3 phút cho 22/22.

Xác minh trong room mới (guest cũng thiếu IPv4 như mọi room — vá theo 3.2):
```
curl -sm 8 http://10.99.0.3:8080/health
-> "initial_prompt":"Lệnh điều khiển xe: điều hòa, nhiệt độ, độ C, …"   ✅
```

**Kết luận:** config trong blueprint chỉ đi vào container **khi deployment được tạo**.
⇒ Quy trình đúng: gom thay đổi vào blueprint → **deploy sang device rảnh** để kiểm,
giữ room demo nguyên vẹn. Không bao giờ undeploy room đang có dữ liệu/app.

**Mẹo dùng quota:** `MAX_CONCURRENT_DEPLOYMENTS_PER_ACCOUNT=2`, `MAX_DEVICES=5`.
Đội có 4 device (`VIVA`, `VIVA (Copy)`, `Gemini`, `Gemini 2`) → luôn có chỗ dựng một
room thử nghiệm song song mà không đụng room demo.
