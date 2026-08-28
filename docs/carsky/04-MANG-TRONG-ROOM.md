# 04 — Mạng trong room CarSky

> Đây là chỗ tốn nhiều giờ nhất của cả dự án. Triệu chứng luôn giống nhau —
> *"app nói mà không ra gì"* — trong khi nguyên nhân nằm ở bốn tầng khác nhau.

---

## 1. Bản đồ mạng ảo

Subnet `10.99.0.0/24`, bridge (`IVI Switch`) dùng `.1`.

| Node | IP | Cách cấp |
|---|---|---|
| IVI - Android (skycraft) | `10.99.0.14` | **tĩnh, khai trên pin** |
| VIVA ASR (container) | `10.99.0.3` | tĩnh (ghim 08/08; trước đó auto-assign) |
| TCU Gateway | `10.99.0.20` | tĩnh |
| TCU-NAD | `10.99.0.22` | tĩnh |
| Bridge (`IVI Switch`) | `10.99.0.1` | ⚠️ **không trả lời ICMP** — switch L2 trong suốt. `ip neigh` báo `FAILED` là bình thường, **đừng dùng để test** |

**Quy tắc cấp IP của bridge:** container/script-node để trống `properties` thì
auto-assign `.2`, `.3`, …; **skycraft phải khai IP tĩnh trên pin** vì bridge chạy
một DHCP server nhỏ gắn MAC guest với IP đã khai.

**Nên ghim IP tĩnh cho mọi node mình cần gọi tới** — auto-assign đổi giữa các lần
dựng, và một địa chỉ đổi giữa buổi demo là thứ không ai muốn dò lại:

```
PATCH /api/v1/blueprints/pins/{pinId}   {"properties":{"address":"10.99.0.3"}}
```

Guest **không có route ra internet** (`ip route` chỉ có `10.0.2.0/24` qua `wlan0`
NAT). Chuyển file vào Device phải qua **USB image** — xem [07](07-APK-ARTIFACT-ADB.md).

---

## 2. 🔴 Android bỏ mặc `eth1` — và cách vá

Node `IVI - Android` khai `10.99.0.14` trên pin, dây nối đúng vào `IVI Switch`,
nhưng **bên trong VM thì `eth1` không có IPv4**:

```
15: eth1: <BROADCAST,MULTICAST,UP,LOWER_UP> ... state UP      <- UP, co carrier, KHONG co inet
17: wlan0: inet 10.0.2.96/24                                   <- mang NAT, duong ra internet
ip route: 10.0.2.0/24 dev wlan0                                <- chi mot route
```

Android **không chạy DHCP client trên interface lạ** — nó chỉ quản mạng nào được
khai trong cấu hình `EthernetService`. `dhcptool` cũng **không có** trên image này.

### 2.1 Khối lệnh khôi phục — bản dùng 19/08 (đã xác minh trên flavor `real`)

Chạy trong **web ADB shell**, **sau mỗi lần VM reboot hoặc room dựng lại**:

```
su 0 ip addr add 10.99.0.14/24 dev eth1
su 0 ip route add 10.99.0.0/24 dev eth1 table legacy_system
```

Chờ **~3 giây** (route cache cũ còn hiệu lực vài giây → lần thử đầu vẫn fail) rồi:

```
ip route get 10.99.0.3
```

→ phải ra `dev eth1 table legacy_system src 10.99.0.14`.

**Vì sao là `table legacy_system`:** `ip rule` của Android cho traffic không đánh
dấu (và cả `mark 0x64` của app) khớp rule 18000 → `lookup legacy_system`. Thêm
route vào `table main` hay `table wlan0` **không có tác dụng**. Nghi ngờ thì hỏi
kernel: `ip route get 10.99.0.3` và `ip route get 10.99.0.3 mark 0x64`, rồi thêm
route vào **đúng bảng nó trả về**.

### 2.2 Bản cũ (08/08, flavor `mock`) — dùng `ip rule`

```sh
su 0 sh
ip addr add 10.99.0.14/24 dev eth1
ip link set eth1 up
ip rule add to 10.99.0.0/24 lookup main priority 100
exit
curl -s -m 5 http://10.99.0.3:8080/health
```

Rule ưu tiên 100 chèn **trước** mọi rule fwmark, buộc mọi gói tới dải đó tra bảng
`main`. Hai bản đều đã chạy được; **bản 2.1 là bản mới hơn và tổng quát hơn** vì nó
hỏi kernel thay vì đoán bảng.

### 2.3 Bốn điều phải hiểu

1. **`ip route add` cho subnet trực tiếp thường không cần** — kernel tự thêm khi
   gán địa chỉ. Chạy vào chỉ báo `File exists`.
2. **Phải kiểm bằng đúng danh tính app**, không phải bằng root:
   ```
   run-as com.sopa.viva_automotive sh -c 'curl -sm 5 http://10.99.0.3:8080/health'
   ```
   Android định tuyến theo **UID**; shell chạy được **không** đảm bảo app chạy được.
3. **`ping -I eth1` thông trong khi `ping` thường unreachable ⇒ KHÔNG phải lỗi L2**,
   mà là policy routing.
4. **Tất cả nằm trong RAM.** Mất sạch khi VM reboot. Muốn bền phải khai `eth1` ở
   tầng cấu hình AAOS trong artifact image — ❌ **CHƯA THỬ**, và không sửa được từ shell.

---

## 3. 🔴 Sau khi restart node container: guest timeout dù node đã Running

**Triệu chứng:** `curl … :8080/health` trả **exit 28 (timeout)** — *timeout*, không
phải *refused*. Node `phase = Running`, log node cho thấy uvicorn đã lên, route
trong guest vẫn đúng.

**Nguyên nhân:** container mới có **MAC mới**, nhưng **ARP cache trong guest còn trỏ
tới MAC cũ** → gói gửi vào hư không.

> Dùng chính điểm này để phân biệt: lỗi cổng/tiến trình cho *refused*, lỗi ARP cho
> *timeout*.

**Xử lý (trong guest):**

```
su 0 ip neigh flush all
sleep 2
ping -c 2 10.99.0.3
run-as com.sopa.viva_automotive sh -c 'curl -sm 8 http://10.99.0.3:8080/health'
```

✅ Xác minh 19/08: sau flush, ping 0.6 ms và `/health` trả lời ngay. **Luôn chạy
bước này sau mỗi lần restart node ASR.**

---

## 4. ⚠️ HAI cổng cleartext khác nhau — cả hai đều phải mở

Đây là cặp bẫy làm mất nửa buổi vì log **không chỉ thẳng vào nguyên nhân**.

| Cổng | Là gì | Sai thì thấy gì |
|---|---|---|
| ① **Cổng của app** — `HttpRemoteAsrTransport.validatedEndpoint` | Chỉ cho HTTP với loopback và dải private RFC 1918 | Ném `IllegalArgumentException` **trong `Application.onCreate`** → app chết trước khi vẽ, **crash buffer RỖNG**, nhìn từ ngoài y hệt "app không mở được" |
| ② **Cổng của Android** — `automotive/app/src/main/res/xml/network_security_config.xml` | Chính sách hệ điều hành, chặn cleartext mặc định từ API 28 | `W VIVA_VOICE: ASR loi asr_model_unavailable: cannot reach viva-asr at http://10.99.0.3:8080/asr: Cleartext HTTP traffic to 10.99.0.3 not permitted` |

⚠️ **Android network security config KHÔNG nhận dải CIDR** — phải liệt kê từng địa
chỉ. Thêm node mới vào room là **phải thêm vào file đó**. Hiện đang khai:

```
127.0.0.1 · localhost      adb reverse tren may dev
10.0.2.2                   host loopback nhin tu emulator Android
10.99.0.3                  node VIVA ASR tren bridge L2 ao cua room
```

Rất dễ truy nhầm sang mạng room hoặc container — trong khi `curl` từ chính VM đó
vẫn trả `200`.

---

## 5. Bộ lệnh chẩn đoán khi mạng không thông

```sh
ip -o addr show eth1                       # eth1 co IPv4 chua
ip route get 10.99.0.3                     # kernel chon bang nao
ip route get 10.99.0.3 mark 0x64           # mo phong traffic cua app
ip neigh show dev eth1                     # ARP: REACHABLE hay FAILED
grep -E "eth1|face" /proc/net/dev          # TX tang ma RX = 0 -> goi ra duoc, khong co gi ve
ping -I eth1 -c2 -W2 10.99.0.3             # ep di dung cua
ss -tlnp                                   # netstat KHONG ton tai tren Android 14
```

Thứ tự loại trừ nên theo:

```text
1. Node ASR con song?      GET /deployments/{room}/logs/{ASR}?container=user&tail=20
2. Guest co IPv4 tren eth1? ip -o addr show eth1
3. Route dung bang chua?    ip route get 10.99.0.3
4. ARP con tro MAC cu?      ip neigh flush all  (sau moi lan restart node)
5. App goi duoc chua?       run-as <pkg> sh -c 'curl -sm 5 .../health'
6. Cleartext da mo chua?    network_security_config.xml co dia chi do chua
```
