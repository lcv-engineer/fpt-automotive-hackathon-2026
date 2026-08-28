# 08 — Checklist trước phiên & khôi phục

> Lý do tồn tại: room đã **hỏng âm thầm** ít nhất hai lần (12/08, và sau mỗi lần
> reboot VM). Không chạy checklist này trước thì phiên demo sẽ vỡ giữa chừng mà
> không ai hiểu vì sao.
>
> Bản gốc: [`vong2/37-RUNBOOK-PREFLIGHT-CARSKY.md`](../../vong2/37-RUNBOOK-PREFLIGHT-CARSKY.md).

Hằng số dùng trong mọi lệnh:

```
ROOM = v37aa3knc6t1embelr5yi
ASR  = b8eada00-d137-4fdc-a131-2197b1d1356b     (VIVA ASR, container)
IVI  = cf7fe8d1-0a9c-48fe-9b59-573e3747f2cb     (IVI - Android, skycraft)
VCU  = faa07ae4-8953-468a-a5b6-4304cb52a6c9     (script-node)
IGW  = n-4e60c4fe-350e-4333-9e50-0bcd5596a609   (IVI Gateway, script-node)
```

---

## PHẦN 1 — Trước mỗi phiên (~2 phút)

### 1.1 Nền tảng — chạy từ máy dev

```bash
cd backend
KEY=$(grep '^CARSKY_API_KEY=' .env | cut -d= -f2- | tr -d '\r')
B=$(grep '^CARSKY_BASE_URL=' .env | cut -d= -f2- | tr -d '\r')
ROOM=v37aa3knc6t1embelr5yi
```

**(a) Tất cả node `Running`?**

```bash
curl -s -H "x-api-key: $KEY" "$B/deployments/$ROOM/nodes" | python -c "import json,sys; ns=json.load(sys.stdin); bad=[n for n in ns if n['phase']!='Running']; print(len(ns),'nodes,',len(bad),'not Running'); [print(' ',n['displayName'],n['phase']) for n in bad]"
```

`404` ⇒ deployment đã biến mất, phải deploy lại ([05 §7](05-VONG-DOI-DEPLOYMENT.md)).

**(b) Chuỗi tín hiệu còn thông? — ba tầng phải cùng một số**

```bash
for pair in "drive-controls:vcu/Speed" "pwt-can:PWT_VehicleSpeed/Speed_kph" "central-broker-vss:Vehicle.Speed"; do node=${pair%%:*}; path=${pair#*:}; printf "%-20s " "$node"; curl -s -H "x-api-key: $KEY" -H "Content-Type: application/json" -d "{\"paths\":[\"$path\"]}" "$B/signals/$ROOM/$node/values"; echo; done
```

**Ba tầng lệch nhau** ⇒ script node mất subscription → [§2.1](#21-script-node-mất-subscription-vcu--ivi-gateway).

### 1.2 Guest — chạy trong web ADB shell

```
ip -o addr show eth1 ; ip route get 10.99.0.3 ; pidof com.sopa.viva_automotive ; id
```

| Quan sát | Nghĩa là | Xử lý |
|---|---|---|
| `eth1` **không có `inet`** | guest cô lập với mạng room, app **không gọi được ASR** | [§2.2](#22-guest-mất-ipv4-trên-eth1) |
| `ip route get` ra `dev buried_eth0` | route sai bảng | [§2.2](#22-guest-mất-ipv4-trên-eth1) |
| `pidof` trống | app chưa chạy | `am start -n com.sopa.viva_automotive/.MainActivity` |
| `uid=2000(shell)` | không root (bình thường sau reboot) | dùng `su 0 <lệnh>` khi cần |

### 1.3 Đường app → ASR node — phép thử quyết định

```
run-as com.sopa.viva_automotive sh -c 'curl -sm 5 http://10.99.0.3:8080/health'
```

Phải ra `{"status":"ok","model":"phowhisper-tiny-int8",…}`.

Đây là kiểm bằng **đúng danh tính app**, không phải shell — Android định tuyến theo
UID nên shell chạy được **không** đảm bảo app chạy được.

### 1.4 Chuẩn bị trước để không mất dữ liệu

- Mở sẵn widget **Log** với source `face-logcat` (không phụ thuộc phiên ADB)
- Mở sẵn widget **IVI Screen** với `Video/Audio Part` đã chọn, kiểm **Touch Part**
- Xác nhận app đang **foreground** và Settings → Ngôn ngữ giọng nói = **Tiếng Việt**

---

## PHẦN 2 — Khôi phục

### 2.1 Script node mất subscription (VCU / IVI Gateway)

**Triệu chứng:** kéo slider Speed nhưng CAN/KUKSA không đổi; log node VCU im lặng.

```bash
curl -s -X POST -H "x-api-key: $KEY" "$B/deployments/$ROOM/restart/$VCU"
curl -s -X POST -H "x-api-key: $KEY" "$B/deployments/$ROOM/restart/$IGW"
```

⚠️ **API trả HTTP 500 nhưng lệnh VẪN có tác dụng. Đừng thử lại nhiều lần.**
Chờ `phase` đi `Provisioning → Running` (~50–60 s).

**Xác nhận:** kéo slider Speed **đổi giá trị** (đổi thật — sự kiện chỉ sinh khi giá
trị thay đổi), rồi đọc lại ba tầng ở §1.1(b).

### 2.2 Guest mất IPv4 trên `eth1`

Xảy ra **sau mỗi lần reboot VM**.

```
su 0 ip addr add 10.99.0.14/24 dev eth1
su 0 ip route add 10.99.0.0/24 dev eth1 table legacy_system
```

Chờ **~3 giây** (route cache) rồi `ip route get 10.99.0.3` → phải ra
`dev eth1 table legacy_system src 10.99.0.14`. Chi tiết: [04 §2](04-MANG-TRONG-ROOM.md).

### 2.3 Sau khi restart node container: timeout dù node `Running`

ARP cache trong guest còn trỏ MAC cũ.

```
su 0 ip neigh flush all
sleep 2
ping -c 2 10.99.0.3
run-as com.sopa.viva_automotive sh -c 'curl -sm 8 http://10.99.0.3:8080/health'
```

**Luôn chạy sau mỗi lần restart node ASR.**

### 2.4 Web ADB shell đứt · App biến mất · Touch part mất

Xem [07 §7–§8](07-APK-ARTIFACT-ADB.md).

---

## PHẦN 3 — Sau khi cài lại APK

```sh
pm grant com.sopa.viva_automotive android.car.permission.CAR_SPEED
pm grant com.sopa.viva_automotive android.car.permission.CAR_ENERGY
pm grant com.sopa.viva_automotive android.permission.RECORD_AUDIO
dumpsys package com.sopa.viva_automotive | grep -iE "CAR_SPEED|RECORD_AUDIO"
sha256sum $(pm path com.sopa.viva_automotive | cut -d: -f2)
```

Rồi đặt lại **Ngôn ngữ giọng nói = Tiếng Việt** và **engine ASR** trong Settings —
gỡ package xoá luôn DataStore.

---

## PHẦN 4 — Trước khi kết thúc phiên

1. **Kéo log node ASR** (mất theo pod, không lấy lại được):
   `GET /deployments/{room}/logs/{ASR}?container=user&tail=3000`
2. **Tải logcat** từ widget `face-logcat` (icon mũi tên xuống)
3. `dumpsys media_session | grep -iA3 "state=PlaybackState"` — readback consumer
4. Trả trạng thái room: slider Speed về giá trị ban đầu; **Unplug** USB image
5. Ghi vào evidence: **commit · sha256 APK · digest image node · roomId · giờ chạy**

---

## PHẦN 5 — Thứ tự loại trừ khi "nói mà không ra gì"

```text
1. Node ASR con song?         logs/{ASR}?container=user&tail=20  -> co "model ready"?
2. Ba tang tin hieu khop?     §1.1(b)
3. Guest co IPv4 tren eth1?   ip -o addr show eth1
4. Route dung bang?           ip route get 10.99.0.3
5. ARP con tro MAC cu?        ip neigh flush all
6. App goi duoc chua?         run-as <pkg> sh -c 'curl ... /health'
7. Cleartext da khai chua?    network_security_config.xml
8. Engine ASR dang chon?      Settings trong app (VIVA | GOOGLE | VOSK)
9. Ngon ngu giong noi?        phai la Tieng Viet
10. Quyen RECORD_AUDIO?       dumpsys package ... | grep RECORD_AUDIO
```

Mỗi bước có một lệnh cho câu trả lời dứt khoát. **Đừng nhảy bước** — sáu trong mười
nguyên nhân trên đều cho cùng một triệu chứng bên ngoài.
