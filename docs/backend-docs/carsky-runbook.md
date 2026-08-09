# CarSky — Sổ tay vận hành & những cái bẫy đã dẫm phải

> **Mục đích:** ghi lại toàn bộ kiến thức vận hành CarSky rút ra từ thực nghiệm
> ngày 07–08/08/2026, để lần sau không ai phải dò lại từ đầu.
>
> **Nguyên tắc của file này:** mỗi khẳng định đều đến từ một lệnh đã gọi thật và
> một phản hồi đã đọc. Chỗ nào chưa kiểm thì ghi rõ **CHƯA THỬ**. Đừng thêm suy
> đoán vào đây — giá trị của nó nằm ở chỗ tin được.
>
> Bổ trợ cho `carsky-api.md` (nhật ký khám phá theo thời gian). File này là bản
> **đã chắt lọc, sắp theo việc cần làm**.

---

## 0. Bảng tra nhanh — ID và địa chỉ

| Thứ | Giá trị |
|---|---|
| API base | `https://hackathon-2.carsky.io/api/v1` |
| Xác thực | header `x-api-key: <CARSKY_API_KEY>` |
| Registry | `registry.hackathon-2.carsky.io` |
| Device `VIVA` (room demo) | `v37aa3knc6t1embelr5yi` |
| Device `VIVA (Copy)` | `wcmfnwigjse4hv9r8s0e3` |
| Blueprint đang dùng | `6deadb05-c856-4dab-976b-432b0fac0658` |
| Node `VIVA ASR` | `b8eada00-d137-4fdc-a131-2197b1d1356b` |
| Node `IVI - Android` | `cf7fe8d1-0a9c-48fe-9b59-573e3747f2cb` |
| Pin `eth` của ASR | `8pzTH3XYHO81KOqn3ygiD` |
| Part prefix của node Android | `face` → `face-adb`, `face-screen`, `face-audio`, `face-logcat` |
| Package app | `com.sopa.viva_automotive.mock` (mock) · `com.sopa.viva_automotive` (real) |

**Mạng ảo trong room** — subnet `10.99.0.0/24`, bridge dùng `.1`:

| Node | IP | Cách cấp |
|---|---|---|
| IVI - Android | `10.99.0.14` | tĩnh, khai trên pin |
| VIVA ASR | `10.99.0.3` | tĩnh (đã ghim 08/08; trước đó auto-assign) |
| TCU Gateway | `10.99.0.20` | tĩnh |
| TCU-NAD | `10.99.0.22` | tĩnh |
| Bridge (`IVI Switch`) | `10.99.0.1` | **không trả lời ICMP** — đừng dùng để test |

⚠️ **Có HAI blueprint trùng tên `VIVA-deploy-clone-0803`.** Bản `7175eb09-8d15-451e-a26f-aec1f60e667c` **không có node ASR** — sửa nhầm vào đó là sửa vào hư không mà không báo lỗi. Bản đúng là `6deadb05-…`. Phân biệt bằng ngày sửa trong danh sách UI.

Biến môi trường đọc từ `backend/.env` (đã gitignore):
`CARSKY_API_KEY`, `CARSKY_ROOM_ID`, `CARSKY_REGISTRY`, `CARSKY_REGISTRY_USER`, `CARSKY_REGISTRY_TOKEN`.

⚠️ `CARSKY_DEVICE_ID` trong `.env` là **VIVA 2**, không phải room demo. Đừng dùng.

---

## 1. 🔴 `openapi.json` GHI SAI ĐƯỜNG DẪN — bài học đắt nhất

Đây là thứ tốn nhiều thời gian nhất và dẫn tới hai kết luận sai liên tiếp.

```
PATCH /api/v1/nodes/{nodeId}              → 404 Route not found     ← openapi.json, SAI
PATCH /api/v1/blueprints/nodes/{nodeId}   → 200                     ← tài liệu người viết, ĐÚNG
```

Spec máy sinh **khai thiếu tiền tố `/blueprints`** cho cả họ endpoint sửa topology. Nguồn đúng là **`Car-Sky-Platform.html`** nằm sẵn trong repo (mục *"API & MCP Tools"*).

**Bộ đường dẫn đúng:**

```
PATCH  /api/v1/blueprints/nodes/:nodeId        Sửa node        ✅ đã gọi, 200
DELETE /api/v1/blueprints/nodes/:nodeId        Xoá node        CHƯA THỬ
POST   /api/v1/blueprints/nodes/:nodeId/pins   Thêm pin        ✅ đã gọi, 400/422 (route tồn tại)
PATCH  /api/v1/blueprints/pins/:pinId          Sửa pin         ✅ đã gọi, 200
DELETE /api/v1/blueprints/pins/:pinId          Xoá pin         CHƯA THỬ
```

> **Quy tắc rút ra:** gặp `404 Route not found` trên CarSky, **đừng kết luận nền
> tảng không hỗ trợ**. Mở `Car-Sky-Platform.html` đối chiếu đường dẫn trước.
> `openapi.json` vừa khai thừa endpoint, vừa khai sai địa chỉ.

Cách dò một route có tồn tại hay không mà **không ghi gì**: gửi `PATCH` với body
rỗng `{}`. `404` = không có route. `200`/`400`/`422` = có route. Đã dùng cách này
trên node ASR, kiểm lại sau đó thấy config nguyên vẹn.

---

## 2. 🔴 Đổi image container = XOÁ deployment rồi DỰNG LẠI

Không có cách nhẹ hơn. Hai cách hiển nhiên đều **không** hoạt động:

| Cách | Kết quả thật |
|---|---|
| `POST /deployments/{room}/restart/{node}` | chỉ chạy lại pod theo **spec K8s hiện có**, không đọc lại blueprint → pod lên lại vẫn mang image **cũ** |
| Nút `Redeploy` trong Deployment Viewer | `Partial redeploy: 1 node(s) failed`, thử **3 lần** đều vậy, không lộ lý do |

**Phép thử đối chứng đã chứng minh không phải lỗi image:** cùng blueprint, cùng
image `0.2.1`, dựng deployment **mới** trên device thứ hai → ASR `Running` sau
**9 giây**. Khác duy nhất là đường đi.

### Quy trình đúng

```
1. build + push image                                   (CI, workflow_dispatch)
2. PATCH /api/v1/blueprints/nodes/{nodeId}  đổi image   (API)
3. DELETE /api/v1/deployments/{roomId}                  (API)
4. POST   /api/v1/deployments {blueprintId, roomId, name}
5. chờ ~3 phút → 22/22 Running
6. ⚠️ CÀI LẠI APK + chạy lại khối lệnh mạng eth1        (xem §5, §6)
```

### ⚠️ Bước 3 xoá sạch máy ảo Android

Đây là điều dễ quên nhất và đắt nhất. `DELETE /deployments` không chỉ tắt
container — nó **huỷ cả VM Android**, node skycraft boot lại từ artifact gốc.
Mất hết:

- APK đã cài
- Cấu hình mạng `eth1` (chỉ nằm trong RAM)
- Mọi thiết lập trong app (ngôn ngữ giọng nói, DataStore)

**Không gián đoạn:** dựng trước một deployment thứ hai trên device khác
(`VIVA (Copy)`), xác nhận 22/22, rồi mới xoá cái cũ. Quota cho phép 2.

### Thời gian thực đo

```
0s     POST /deployments → status PENDING
20s    22/22 Provisioning
60s    21/22 Running   (chỉ còn IVI - Android)
180s   22/22 Running   ← node skycraft boot Android chậm nhất
```

---

## 3. 🟡 `restart/{node}` trả 500 nhưng VẪN CHẠY

```
POST /api/v1/deployments/{roomId}/restart/{node}  →  500, body RỖNG
```

Không có `error`, không có `message`. **Nhưng lệnh vẫn thực thi:** node chuyển
`Provisioning` ngay sau đó rồi `Running` sau ~50 giây.

**Hậu quả đã xảy ra thật:** workflow dùng `curl -f` coi 500 là lỗi → step fail →
rollback chạy → PATCH đè image cũ lên bản mới vừa ghi thành công. **Deploy tự
huỷ chính nó**, và log báo `failure` nên trông như nền tảng hỏng.

> **Quy tắc:** với CarSky, phán xét bằng **hệ quả quan sát được** (phase của node
> đổi), không bằng mã HTTP.

---

## 4. 🟡 Khi Redeploy hỏng, API KHÔNG cho biết vì sao

Đã thử hết, không đường nào lộ pod đang fail:

| Cách | Kết quả |
|---|---|
| `GET /deployments/{room}/nodes` | `phase: Running`, `message: null` — 22/22 |
| `GET .../nodes/watch` (SSE, 23 event) | node **chưa từng rời** `Running` |
| `GET .../logs/{node}?container=user` | vẫn trỏ **pod cũ**, log dừng ở lần restart trước |
| `?container=sidecar` | như trên |
| `GET .../logs/{node}/search` | `result: []` |
| `container-exec` | 502 Conduit |

**Cơ chế:** K8s giữ ReplicaSet cũ chạy tiếp khi pod mới không lên được. Nên node
vẫn `Running` (đúng — *có* một pod đang chạy), dịch vụ vẫn phục vụ, còn pod hỏng
biến mất khỏi mọi endpoint. **Không có endpoint liệt kê pod** trong 73 route;
`list_pods(roomId)` chỉ tồn tại ở tầng MCP.

> **Hệ quả thực dụng:** redeploy fail thì **đừng cố quan sát**. Liệt kê khác biệt
> giữa bản chạy được và bản hỏng rồi **đổi từng biến**. Hoặc tốt hơn: dựng một
> môi trường thứ hai để so sánh — đó là thứ cho câu trả lời trong 9 giây sau khi
> ba vòng quan sát đều mù.

### Endpoint không tồn tại

`GET /api/v1/deployments` → **404**. Không có cách liệt kê mọi deployment. Chỉ
tra được từng room qua `find?device=` hoặc `/deployments/{room}/status`.

---

## 5. 🔵 Đọc log bên trong container — cần `?container=user`

Gọi thẳng endpoint log với container node trả **502**, trông y hệt họ lỗi Conduit
nhưng **không phải**:

```
502 {"error":"UPSTREAM_ERROR","message":"Blueprint service error: 500",
     "details":{"upstream":"ApiError: a container name must be specified for pod
     b8eada00-…-6cc865b44-gltn9, choose one of: [user sidecar] …"}}
```

Pod của container node có **hai container**: `user` (image của mình) và `sidecar`
(`nydus_sidecar_native`, lo eth-tunnel/silkit). API không chọn giúp, và
`openapi.json` **chỉ khai `tail` và `since`** — không có tham số container.

**Nhưng server vẫn nhận:**

```bash
GET /api/v1/deployments/{room}/logs/{node}?container=user&tail=200
→ 200 {"lines":[…,"2026-08-08 09:11:22,646 INFO viva.asr VIVA_ASR model ready in 455 ms: phowhisper-tiny-int8"]}
```

`?container=sidecar` cho xem mạng của node (`TAP 'e-eth' configured: 10.99.0.3/24`,
bridge silkit, upstream tunnel) — đây là cách **lấy IP thật** của một container node.

> Đây là **đường duy nhất nhìn được vào trong container** khi `container-exec`
> còn chết vì Conduit. Giới hạn: chỉ đọc được **pod đang chạy**; pod chết lúc
> redeploy không để lại dòng nào.

---

## 6. 🔵 Mạng trong room — Android KHÔNG tự lấy IP

### Cách bridge cấp IP

Theo tài liệu Ethernet Bridge: container/script-node để trống `properties` thì
**auto-assign** `.2`, `.3`, …; **Skycraft phải khai IP tĩnh trên pin** vì bridge
chạy một DHCP server nhỏ gắn MAC của guest với IP đã khai.

**Nên ghim IP tĩnh cho mọi node mình cần gọi tới.** Auto-assign đổi giữa các lần
dựng, và một địa chỉ đổi giữa buổi demo là thứ không ai muốn dò lại:

```bash
PATCH /api/v1/blueprints/pins/{pinId}
{"properties":{"address":"10.99.0.3"}}
```

### Vấn đề thật: Android bỏ mặc `eth1`

Node `IVI - Android` khai `10.99.0.14` trên pin, dây nối đúng vào `IVI Switch`,
nhưng **bên trong VM thì `eth1` không có IPv4**:

```
15: eth1: <BROADCAST,MULTICAST,UP,LOWER_UP> ... state UP      ← UP, có carrier, KHÔNG có inet
17: wlan0: inet 10.0.2.96/24                                   ← mạng NAT, đường ra internet
ip route: 10.0.2.0/24 dev wlan0                                ← chỉ một route
```

Android **không chạy DHCP client trên interface lạ** — nó chỉ quản mạng nào được
khai trong cấu hình EthernetService. `dhcptool` cũng **không có** trên image này.

### Khối lệnh khôi phục mạng

Chạy qua widget **IVI ADB**, **sau mỗi lần VM reboot hoặc room dựng lại**:

```sh
su 0 sh
ip addr add 10.99.0.14/24 dev eth1
ip link set eth1 up
ip rule add to 10.99.0.0/24 lookup main priority 100
exit
curl -s -m 5 http://10.99.0.3:8080/health
```

Bốn điều cần hiểu về khối này:

1. **`ip route add` không cần** — kernel tự thêm route cho subnet trực tiếp khi
   gán địa chỉ. Chạy vào chỉ báo `File exists`.
2. **Dòng `ip rule` mới là mấu chốt.** Không có nó, chỉ `ping -I eth1` và
   `curl --interface eth1` chạy được, còn lệnh thường thì timeout — vì Android
   chọn bảng định tuyến theo `fwmark` của network mà tiến trình gắn vào, và bảng
   đó không có route tới `10.99.0.0/24`. Rule ưu tiên 100 chèn **trước** mọi rule
   fwmark, buộc mọi gói tới dải đó tra bảng `main`.
3. **Phải kiểm bằng user thường** (`exit` về prompt `$`). Root gọi được không
   chứng minh app gọi được — Android định tuyến theo UID.
4. **Tất cả nằm trong RAM.** Mất sạch khi VM reboot. Muốn bền phải khai `eth1` ở
   tầng cấu hình AAOS trong artifact image — **CHƯA THỬ**, và không sửa được từ
   shell.

### Chẩn đoán khi mạng không thông

```sh
ip addr show | grep -E "^[0-9]+:|inet "   # eth1 có IPv4 chưa
ip route                                   # có route 10.99.0.0/24 chưa
ip neigh show dev eth1                     # ARP: REACHABLE hay FAILED
grep -E "eth1|face" /proc/net/dev          # TX tăng mà RX = 0 → gói ra được, không có gì về
ping -I eth1 -c2 -W2 10.99.0.3             # ép đi đúng cửa
```

⚠️ Đừng dùng `10.99.0.1` (bridge) để test — nó là switch L2 trong suốt, **không
trả lời ICMP**, `ip neigh` sẽ báo `FAILED`. Điều đó bình thường.

---

## 7. 🟢 Cài APK lên node Android — Conduit chết nhưng widget ADB sống

`carsky-api.md` §4 kết luận *"ADB không dùng được"* — đúng với **REST API**
(`/vms/{room}/{node}/adb-shell`, `/deployments/{room}/adb-exec/{node}` đều 502
`Conduit service not configured`).

**Nhưng widget `IVI ADB` trong UI thì chạy.** Nó cho terminal xterm với shell
thật trên VM, prompt `trout_arm64:/ $`. Không qua REST, không cần Conduit.

### Cách dùng

1. Cột trái: **Devices → VIVA → Widgets → `IVI ADB`**
2. Inspector cột phải: dropdown `ADB` → chọn **`face-adb`**
3. Khung giữa hiện `ADB SHELL` + badge `connected`

### Thông tin về VM (đã đo)

| | |
|---|---|
| Kiến trúc | **`arm64-v8a`** (`ro.product.cpu.abi`), artifact khai `arch: aarch64` |
| Current user | **`0`** — *không* phải `10` như emulator AAOS trên máy dev |
| `su` | **có**, cú pháp `su 0 sh` (**không** nhận `-c`) |
| `curl` | có ở `/system/bin/curl`; **không** có `wget`, **không** có `dhcptool` |
| Ra internet | ✅ `curl -sI https://github.com` → `HTTP/1.1 200 OK` |
| Dung lượng `/data` | 5.4 GB trống |

### Quy trình cài (đã chạy thành công)

APK phải có `lib/arm64-v8a/` — kiểm trước khi phí công:

```bash
unzip -l app-mock-debug.apk | grep -c "lib/arm64-v8a/"   # phải > 0
```

**Đưa file lên Device — hai đường, ưu tiên đường 1:**

**① CarSky Artifacts (nên dùng).** Upload APK lên panel **Artifacts** thành một
artifact **private** (đã làm 09/08: `viva-apk` / `0.0.1`), rồi tải xuống Device.
Ưu điểm: file **không ra công khai**, và nằm cùng nền tảng nên không phụ thuộc
việc VM có ra được internet hay không.

**② GitHub Release.** Chỉ dùng khi repo public và chấp nhận file ra URL công
khai. Nhanh hơn nhưng **đăng cả bản build ra ngoài** — cân nhắc trước khi làm.

```powershell
gh release create <tag> --repo <owner/repo> --title "..." --notes "..." "<đường-dẫn-apk>"
gh api repos/<owner/repo>/releases/tags/<tag> --jq '.assets[].browser_download_url'
```

Rồi trong ADB shell — **dán từng dòng một**, terminal trong trình duyệt hay dính
lệnh vào nhau khi dán nhiều dòng:

```sh
cd /data/local/tmp
curl -L -o viva.apk <URL>
ls -l viva.apk
pm install -r viva.apk
pm grant com.sopa.viva_automotive.mock android.permission.RECORD_AUDIO
am start -n com.sopa.viva_automotive.mock/com.sopa.viva_automotive.MainActivity
```

Phiên ADB hay tự `Connecting...` và **giết `curl` đang chạy**. Tải tiếp chỗ dở
thay vì làm lại:

```sh
curl -L -C - -o viva.apk <URL>
```

### ⚠️ `INSTALL_FAILED_UPDATE_INCOMPATIBLE` — chữ ký khác nhau

Gặp 09/08. Nếu trên Device đã có package cùng tên nhưng **ký bằng khoá debug
khác** (ví dụ người khác build trên máy khác, hoặc bản trước cài từ nguồn khác),
`pm install -r` sẽ trả:

```
Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: existing package signature mismatch]
```

Không có cách ép. Phải gỡ rồi cài sạch:

```sh
pm uninstall com.sopa.viva_automotive.mock
pm install /data/local/tmp/viva.apk
```

Gỡ **đúng package mock**; bản `real` (`com.sopa.viva_automotive`) và các package
khác không bị ảnh hưởng vì khác `applicationId`.

> Hệ quả vận hành: **thống nhất một máy build** cho mọi bản đưa lên Device, hoặc
> chấp nhận phải gỡ-cài lại mỗi khi đổi người build. Gỡ package cũng xoá luôn
> DataStore của app — tức mất thiết lập ngôn ngữ giọng nói, phải chọn lại.

### Đối chiếu SHA-256 sau khi cài

Nên làm, để chắc thứ đang chạy đúng là thứ mình build:

```sh
sha256sum /data/local/tmp/viva.apk
pm path com.sopa.viva_automotive.mock      # → /data/app/…/base.apk
sha256sum <đường-dẫn-base.apk>
```

Ba giá trị (local, tải xuống, đã cài) phải khớp nhau.

### Đường thay thế nếu VM không ra được internet — **CHƯA THỬ**

Đóng APK vào ảnh đĩa FAT32 `.img`, upload lên panel **Artifacts**, dùng widget
**USB Device** (Device Proxy) mount vào VM, rồi `pm install` từ đường dẫn USB.
Tài liệu `31-usb-image` có script `truncate` + `mkfs.vfat` + `mcopy`, và bản
PowerShell + `diskpart` cho Windows không WSL. Node `IVI - Android` **có** pin
`usb` nên đường này khả thi.

### ⚠️ App mặc định chạy tiếng Anh

`VoiceLanguage.fromStorageKey` trả `ENGLISH`, Vosk nạp `model-en-us`, và **mọi
câu tiếng Việt đều sai bét** — nhìn y hệt "mic không hoạt động". Phải vào Cài đặt
trong app đổi **Ngôn ngữ giọng nói → Tiếng Việt** (bấm tay qua widget IVI Screen).

Đặt luôn ngôn ngữ giao diện cho khớp:

```sh
cmd locale set-app-locales com.sopa.viva_automotive.mock --user 0 --locales vi-VN
```

---

### Đã chứng minh chạy trên Device — 09/08

Bằng chứng đầy đủ ở `evidence/c2/carsky-runtime-20260809/`.

| Môi trường | Giá trị |
|---|---|
| Deployment | `VIVA-demo-0808`, 22/22 Running |
| Android target | `trout_arm64`, `arm64-v8a`, **Android 14 / SDK 34** |
| Package | `com.sopa.viva_automotive.mock`, `versionCode=1`, `minSdk=32`, `targetSdk=36` |
| Artifact | private `viva-apk` / `0.0.1`, 387.904.742 bytes |

Ba câu bơm văn bản đi hết chuỗi **receiver debug → `VoiceAssistantService` → NLU
→ `MediaBrowserCompat`/`MediaControllerCompat` → MediaSession/ExoPlayer**, và
MediaSession **đổi trạng thái thật**:

```
phát nhạc   → media_play  |Allow   → state=PLAYING(3), position=0
dừng nhạc   → media_pause |Allow   → state=PAUSED(2), position=3124
chuyển bài  → media_next  |Allow   → activeItemId 0 → 1, rồi PLAYING
```

Đây chính là **3 trong 5 ca `known_gap` D7 MediaSession** mà benchmark trên
emulator luôn FAIL — trên Device thật thì chạy.

Lệnh bơm câu (chỉ có ở build **mock/debuggable**):

```sh
am broadcast -a com.sopa.viva_automotive.mock.UTTERANCE \
  --es text_b64 cGjDoXQgbmjhuqFj \
  -n com.sopa.viva_automotive.mock/com.sopa.viva_automotive.debug.SimulatedUtteranceReceiver
```

```
cGjDoXQgbmjhuqFj       = phát nhạc
ZOG7q25nIG5o4bqhYw==   = dừng nhạc
Y2h1eeG7g24gYsOgaQ==   = chuyển bài
```

⚠️ **Phạm vi của bằng chứng này — đừng nói quá:** nó bỏ qua **mic, VAD, ASR**
(dùng hook bơm text), nên `e2e_ms=0` **không phải** độ trễ giọng nói. Nó cũng
không chứng minh VHAL/CAN/CCU, vì là flavor `mock`.

**Degrade đã biết trên Device:** không có giọng TTS tiếng Việt, nên câu trả lời
`"Đã gửi lệnh phát nhạc tới trình phát."` không phát ra tiếng. Media vẫn chạy
đúng — nhưng **không được claim phản hồi TTS hoàn chỉnh**.

---

## 8. 🟢 Mic — nằm trong widget Screen, không phải widget riêng

Theo tài liệu mục Devices/Widget Screen:

> *"**Microphone**: chọn micro vật lý của máy client và bấm **Enable microphone**
> để nói chuyện hai chiều với thiết bị."*

Mở **`IVI Screen`** → Inspector → đặt `Video Part` = `face-screen`,
`Audio Part` = `face-audio`, chọn `Microphone`, bấm **Enable microphone**
(trình duyệt sẽ hỏi quyền, phải Allow).

Mic tắc được ở **ba tầng**, và cách sửa khác hẳn nhau:

| Tầng | Kiểm bằng |
|---|---|
| CarSky có gửi tiếng vào VM không | đã bấm `Enable microphone` chưa, `Audio Part` đã chọn chưa |
| Android có thấy thiết bị thu không | `dumpsys media.audio_flinger \| grep -iA3 input` |
| App có mở được mic không | `logcat -c` → bấm mic → `logcat -d \| grep -iE "viva\|audiorecord\|permission denial\|vad"` |

| Thấy trong log | Nghĩa là |
|---|---|
| `Permission denial ... RECORD_AUDIO` | quyền chưa ăn — cấp lại rồi **khởi động lại app** |
| Mở `AudioRecord` OK nhưng không có `speech_end` | mic mở được nhưng toàn mẫu 0 → tắc ở tầng 1 |
| Không có dòng nào của app | app chưa chạy tới đoạn thu |

---

## 9. Registry — build và push image

### Xác thực

Registry đòi credential **riêng**; API key CarSky **không** dùng được (thử 06/08:
`Basic`, `Bearer`, ẩn danh đều 401). Cặp đúng nằm ở `backend/.env`
(`CARSKY_REGISTRY_USER` / `CARSKY_REGISTRY_TOKEN`) và đã đặt lên GitHub Secrets
thành `CARSKY_REGISTRY_USERNAME` / `CARSKY_REGISTRY_PASSWORD`.

⚠️ Khi đặt secret bằng CLI, nhớ `.Trim()` — token dính ký tự xuống dòng làm
`docker login` fail 401 với thông báo chẳng liên quan.

Tra registry bằng API v2 chuẩn:

```bash
curl -u "$USER:$TOKEN" https://registry.hackathon-2.carsky.io/v2/viva/viva-asr/tags/list
curl -u "$USER:$TOKEN" -H "Accept: application/vnd.oci.image.index.v1+json" \
  https://registry.hackathon-2.carsky.io/v2/viva/viva-asr/manifests/<tag>
```

Lấy ngày build và biến env nướng trong image: đi từ index → manifest amd64 →
config blob. Hữu ích để biết một image thật sự cũ đến đâu.

### Kiến trúc

**Cluster chạy arm64.** Tài liệu tutorial ghi: *"image x86_64 chạy qua QEMU có thể
lỗi ngẫu nhiên"* và khuyên build `--platform linux/arm64`. Android VM cũng
aarch64.

Luôn build **đa kiến trúc** (`linux/amd64,linux/arm64`) và thêm
`docker/setup-qemu-action` — thiếu nó thì stage `runtime` (có `pip install`)
không build được dưới arm64 trên runner x86. Build cả hai kiến trúc mất ~5 phút.

> ⚠️ Kiến trúc **không** phải nguyên nhân của lỗi Redeploy (đã loại trừ, xem §2).
> Nhưng cứ giữ đa kiến trúc — bản duy nhất từng chạy được là đa kiến trúc, và thu
> hẹp lại là đánh cược không có lợi.

### Digest, không phải tag

Node ghim image bằng **digest**. Push đè lên một tag rồi restart sẽ kéo lại đúng
image cũ — pipeline chạy mà không đổi gì. Luôn dùng dạng `@sha256:…`.

### Hai cái bẫy trong build-args

1. **`ASR_MODEL_NAME` không được lấy từ `tag`.** Bản đầu của workflow viết
   `ASR_MODEL_NAME=${{ inputs.tag }}` — gộp "tên bản phát hành" với "nhãn model"
   làm một. Nó chỉ đúng khi tag tình cờ trùng tên model. Push với `tag=0.2.0` làm
   image mang `ASR_MODEL_NAME=0.2.0`, khiến `/health` và header `X-Asr-Model`
   khai một tên model không tồn tại. Phải tách thành input riêng.
2. **Truyền `VIVA_GIT_COMMIT=${{ github.sha }}`.** `Dockerfile` khai
   `ARG VIVA_GIT_COMMIT=unknown` rồi nướng vào ENV; không truyền thì mọi image
   đẩy lên đều mang `unknown` và không truy ngược về commit được — đúng thứ mà kỷ
   luật ghim-digest sinh ra để bảo vệ.

---

## 10. Giới hạn đã xác nhận của nền tảng

### Pin `ETHERNET` không tạo được bằng API

Đã kiểm trên một blueprint clone tạm (không đụng room demo, xoá sạch sau đó):

```
POST /api/v1/nodes/{id}/pins                        → 404 Route not found
POST /api/v1/blueprints/nodes/{id}/pins  ETHERNET   → 400 enum từ chối
cùng route,                              GENERIC    → 422 "Pin type GENERIC is not
                                                       allowed on container nodes.
                                                       Allowed: CAN, KUKSA, VIDEO"
```

```
pinType hợp lệ toàn cục            : VHAL | KUKSA | CAN | LIN | VIDEO | GPIO | GENERIC
pinType hợp lệ trên container node : CAN, KUKSA, VIDEO
```

**Đây là giới hạn thiết kế, không phải route chưa bật.** Khác biệt quan trọng khi
đi hỏi BTC: câu hỏi đúng là *"thêm container node có mạng bằng cách nào nếu không
dùng UI?"*, và câu trả lời có thể là *"không có, dùng UI"*.

🔴 **Đừng `DELETE` node rồi tạo lại** để né — node cần pin `ETHERNET` nối vào
`IVI Switch`, mà pin đó chỉ tạo được trong web UI. Đường một chiều.

### Họ endpoint điều khiển VM qua REST

`screenshot` · `accessibility` · `shell` · `tap` · `text` · `key` · `swipe` ·
`adb-exec` · `container-exec` — tất cả `502 Conduit service not configured`.
Nhưng **widget ADB và widget Shell trong UI vẫn chạy** (§7).

### `find?device=` có thể trả `[]` dù room đang chạy

Gặp 08/08: `/status` báo `RUNNING`, `/nodes` trả 22/22, nhưng `find?device=` và
`find?blueprint=` đều `[]`, và `/devices` báo `VIVA: IDLE`. Bản ghi deployment
lệch khỏi thực tế.

Hậu quả: workflow lấy `blueprintId` từ `find` sẽ chết. **Cách sửa: xoá deployment
rồi dựng lại** — sau đó `find` trả 1 bản ghi và device về `BUSY`.

### Deployment không sống mãi

Đã biến mất một lần (05/08, §4c của `carsky-api.md`). Kiểm trước mỗi buổi làm việc:

```powershell
go run ./cmd/viva-tools carsky nodes --room $env:CARSKY_ROOM_ID
```

`404` nghĩa là phải deploy lại, không phải nền tảng hỏng.

### Quota

`MAX_DEVICES=5` · `MAX_NODES_PER_BLUEPRINT=30` · **`MAX_CONCURRENT_DEPLOYMENTS=2`**
· `MAX_SKYCRAFT_PER_BLUEPRINT=2`

Hai deployment là trần. Dựng bản dự phòng để không gián đoạn thì hết slot — nhớ
xoá sau khi xong.

---

## 11. Vì sao workflow CarSky là `workflow_dispatch`, đừng bật `on: push`

| Workflow | Trigger |
|---|---|
| `carsky-push-asr-image` | `workflow_dispatch` |
| `carsky-deploy-asr` | `workflow_dispatch` |
| `android-ci` / `asr-ci` / `backend-ci` | `push` + `pull_request` (chỉ kiểm thử, **không** đụng CarSky) |

Bốn lý do, lý do cuối là nặng nhất:

1. Quota 2 deployment — chạy mỗi lần merge là đụng trần ngay.
2. Đụng room demo giữa lúc tổng duyệt.
3. Giai đoạn freeze trước demo.
4. **Đổi image bắt buộc xoá-dựng-lại, mà việc đó xoá luôn APK và cấu hình mạng
   trên VM Android.** Một PR sửa docs mà tự động làm việc đó là thảm hoạ.

---

## 12. Runbook — các việc hay làm

### A. Kiểm tình trạng room trước buổi làm việc

```bash
KEY=$(grep '^CARSKY_API_KEY=' backend/.env | cut -d= -f2-)
B=https://hackathon-2.carsky.io/api/v1
curl -fsS -H "x-api-key: $KEY" "$B/deployments/v37aa3knc6t1embelr5yi/status"
curl -fsS -H "x-api-key: $KEY" "$B/deployments/v37aa3knc6t1embelr5yi/nodes" | jq '[.[].phase] | group_by(.) | map({(.[0]): length}) | add'
```

### B. Xem image nào đang khai trong blueprint

```bash
curl -fsS -H "x-api-key: $KEY" "$B/blueprints/6deadb05-c856-4dab-976b-432b0fac0658" \
  | jq '.nodes[] | select(.id=="b8eada00-d137-4fdc-a131-2197b1d1356b") | .config'
```

### C. Xem container ASR có phục vụ được không

Từ ADB shell của VM Android (sau khi đã chạy khối lệnh mạng §6):

```sh
curl -s -m 5 http://10.99.0.3:8080/health
```

Hoặc đọc log từ ngoài:

```bash
curl -fsS -H "x-api-key: $KEY" \
  "$B/deployments/v37aa3knc6t1embelr5yi/logs/b8eada00-d137-4fdc-a131-2197b1d1356b?container=user&tail=20" | jq -r '.lines[]'
```

### D. Đưa một bản ASR mới lên (toàn bộ chuỗi)

```
1. gh workflow run carsky-push-asr-image -f tag=<mới> -f hf_model=vinai/PhoWhisper-tiny \
       -f model_name=phowhisper-tiny-int8
2. Lấy digest từ registry hoặc step summary
3. PATCH /api/v1/blueprints/nodes/{nodeId}  {"config": {…env cũ…, "image": "…@sha256:…"}}
   ⚠️ ĐỌC config cũ rồi TRỘN — gửi mỗi `image` có thể xoá sạch env
4. DELETE /api/v1/deployments/{roomId}
5. POST   /api/v1/deployments {blueprintId, roomId, name: "…"}
6. Chờ 22/22 (~3 phút)
7. Cài lại APK (§7) + chạy lại khối lệnh mạng (§6)
```

**Làm ngoài giờ duyệt.** Bước 4 huỷ mọi thứ trên VM Android.

---

## 13. Ba lần chẩn đoán sai trong ngày — và vì sao

Ghi lại vì phương pháp quan trọng hơn kết luận.

| Kết luận sai | Sai ở đâu | Cái gì bác bỏ nó |
|---|---|---|
| *"`PATCH /nodes/{id}` 404 → CarSky không cho đổi image bằng API"* | Lấy đường dẫn từ `openapi.json`, không đối chiếu `Car-Sky-Platform.html` — tài liệu nằm sẵn trong repo từ 31/07, chỉ mở khi được nhắc | Gọi đúng path → 200 |
| *"image amd64-only không hợp cluster"* | `platforms: linux/amd64` là do chính mình thu hẹp dựa trên một comment chưa ai kiểm; đã nêu lo ngại hai lần rồi vẫn để nguyên | Build đa kiến trúc, Redeploy **vẫn** fail |
| *"cluster không kéo được image mới"* | Suy luận từ việc quan sát bị mù, không phải từ bằng chứng | Dựng deployment mới cùng image → ASR `Running` sau **9 giây** |

**Bài học lớn nhất:** khi ba vòng quan sát đều mù, thứ cho câu trả lời không phải
vòng quan sát thứ tư, mà là **dựng một môi trường thứ hai để so sánh**. Rẻ hơn,
nhanh hơn, và không đụng vào bản đang chạy.

**Bài học thứ hai:** đọc tài liệu có sẵn trong repo **trước** khi suy luận từ
spec máy sinh.

---

## 14. Còn treo — chưa thử hoặc chưa giải quyết

| Việc | Trạng thái |
|---|---|
| Cấu hình `eth1` bền qua reboot (EthernetService trong artifact AAOS) | **CHƯA THỬ** — không sửa được từ shell |
| Cài APK qua USB image + Device Proxy | **CHƯA THỬ** — đường dự phòng nếu VM mất internet |
| Biến thể `real` (VHAL thật qua pin `vhal`) | **CHƯA THỬ** trên CarSky; đã build APK |
| `DELETE /blueprints/nodes/{id}`, `DELETE /blueprints/pins/{id}` | **CHƯA THỬ** |
| Vì sao `Redeploy` không áp được image | Không rõ cơ chế — chỉ biết hiện tượng. Câu hỏi cho BTC |
| Conduit (`adb-exec`, `container-exec`, `vms/*`) | 502 từ 02/08, chưa được bật. Câu hỏi cho BTC |
| Hai blueprint trùng tên `VIVA-deploy-clone-0803` | Nên đổi tên bản `7175eb09-…` để không ai sửa nhầm |

---

## 15. Nguồn

- `Car-Sky-Platform.html` (repo root và `docs/`) — **tài liệu vận hành chính
  thức**, 39 mục, nội dung nằm trong biến JS `EMBEDDED_CONTENT`. Trích ra bằng
  cách strip tag từ khối `<script>`. Đây là nguồn đúng khi `openapi.json` mâu
  thuẫn.
  Mục hữu ích nhất: *API & MCP Tools*, *Container Node*, *Ethernet Bridge Node*,
  *Triển Khai Blueprint*, *Thiết Bị & Widget*, *Cẩm Nang Sửa Lỗi FAQ*.
- `GET /api/v1/openapi.json` — 73 endpoint. **Đường dẫn có thể sai**, đối chiếu
  với trên trước khi tin.
- `docs/backend-docs/carsky-api.md` — nhật ký khám phá theo thời gian, có bối
  cảnh vì sao từng kết luận được rút ra.
