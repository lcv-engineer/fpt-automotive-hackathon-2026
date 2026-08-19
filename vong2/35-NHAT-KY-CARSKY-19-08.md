# 35 — Nhật ký phiên CarSky 19/08/2026

> **Người chạy:** Vĩ · Room `v37aa3knc6t1embelr5yi` (VIVA-demo-0808) · toàn phiên qua
> CarSky web ADB shell + REST + widget UI, không có local ADB.
> **Phạm vi:** chỉ phần CarSky. Mục tiêu vào phiên: kiểm tiền đề Block B của
> `34-PLAN` (nền tảng đổi tốc độ → app đọc được → SafetyGuard đổi verdict).
>
> File này để **tracking**. Ba phần: đã làm gì · đang ở đâu · làm gì tiếp.

---

## PHẦN A — ĐÃ LÀM GÌ HÔM NAY

### A1. Đưa được bản `real` lên Device (lần đầu tiên)

Trước hôm nay Device chỉ có `com.sopa.viva_automotive.mock`. Bản `mock` đọc tốc độ
từ **sóng sin trong app** (`MockVehicleRepository.kt:143`), không chạm VHAL — nên
mọi bằng chứng cũ không thể dùng cho claim "nền tảng là điều kiện cần".

| Bước | Kết quả |
|---|---|
| Xác minh quyền `CAR_SPEED` trên image | `prot=dangerous` → `pm grant` được, **không cần M1a** (`evidence/c2/car-speed-permission-probe-0818.txt`) |
| Build `:app:clean :app:assembleRealDebug -PvivaAsrBaseUrl=http://10.99.0.3:8080` | commit `0c67010`, SHA-256 `48f9830f…23677ea7e`, 227.759.181 bytes |
| Gỡ `assets/model-vi` + `model-en-us` (Vosk, untracked, không code nào tham chiếu) | APK từ 305 MB → 227 MB, truy nguyên sạch về commit |
| Upload artifact `viva-apk` v0.0.3 | UI Artifacts (ANDROID_IMAGE cần **cả hai** ô Image + Host Package mới bấm Upload được → dùng zip giả 245 B) |
| Chuyển file vào guest | ⚠️ `curl` từ guest **chết** (không có route ra ngoài). Đường sống: tạo **USB image FAT32** (`viva-usb.img`, 256 MB) → artifact `viva-usb` → widget USB Device → **Plug** → file hiện ở `/sdcard/Music/usb_1/` |
| `pm install` + đối chiếu hash | `Success`; hash khớp **ba chặng** (máy dev = file trên USB = `base.apk` đã cài) |
| `pm grant … CAR_SPEED` + xác minh | `granted=true` |
| Mở app | Màn hình VIVA lên đủ (Weather/Radio/Now playing), **"Hotword armed — say Vi-Vi ơi"**; `ss -tlnp` thấy PID 17190 LISTEN :7788 |

Evidence: `evidence/c2/real-apk-local-build-0819.txt`.

> 🧠 **Bài học artifact:** hai lần build incremental trước đó cho APK 305 MB mà
> listing **không** còn entry model — AGP gỡ entry khỏi central directory nhưng
> không compact file, ~78 MB dữ liệu chết vẫn nằm trong archive. ⇒ **Mọi APK dùng
> làm evidence phải build sau `clean`**, nếu không hash sẽ không tái lập được.

### A2. Chứng minh chuỗi nền tảng chạy — thứ đội chưa từng có bằng chứng

Đặt tốc độ bằng slider panel **Drive Controls** (widget GPIO Panel → source
`drive-controls-signal`), đọc lại từng tầng bằng REST:

```
GPIO   vcu/Speed                  = 60   ts 03:41:51.161Z   ✅
CAN    PWT_VehicleSpeed/Speed_kph = 60   ts 03:49:14.613Z   ✅
KUKSA  Vehicle.Speed              = 60   ts 03:41:51.300Z   ✅
```

Log node (widget Log):
```
[vcu] speed=60 km/h
[igw] → vhal 0x11600207 area=0x0 pushed = 16.6666      (= 60 km/h, quy đổi đúng)
```

⇒ **GPIO → VCU → PWT-CAN → PWT Gateway → KUKSA → IVI Gateway → VHAL push: TẤT CẢ CHẠY.**

> ⚠️ REST `actuate` trên `vcu/Speed` **không** sinh sự kiện cho script VCU (cờ
> `actuate` chỉ dành cho KUKSA). Đặt tốc độ **phải qua slider panel**. REST chỉ dùng
> để **đọc lại**. Sửa lại kỳ vọng sai trong `34-PLAN` Block B.

### A3. Tìm ra root cause chặn VHAL readback — thuộc image, không thuộc đội

App/shell đọc property vẫn `0.0` dù gateway push liên tục. `getprop` khai:

```
ro.vendor.vehiclehal.server.use_local_fake_server = true
ro.boot.vendor.vehiclehal.server.cid  = 1     (loopback)
ro.boot.vendor.vehiclehal.server.port = 9210
```

`ps -ef` xác nhận hai tiến trình cùng chạy: `…-fake-hardware-grpc-server` (PID 477)
và `…-trout-service` (PID 478, client). VHAL client nối vào **fake server nội bộ**,
không bao giờ nối tới IVI Gateway. `use_local_fake_server` nằm trong `ro.vendor.*`
→ nướng trong vendor partition, không `setprop` đè được.

⇒ Đây là lời giải cho việc **mọi mốc readback qua `CarPropertyManager` chưa từng đạt**:
không phải lỗi app, không phải thiếu quyền, không phải M1a. Đúng kịch bản guideline
CDC mà `carsky-analysis/03` §4 đã trích: *"property null → báo team hạ tầng, đừng
debug tiếp phía app"*.

### A4. Phát hiện room đã âm thầm hỏng từ 12/08 — và sửa được

| Hỏng | Triệu chứng | Đã sửa bằng |
|---|---|---|
| VCU mất subscription GPIO | kéo slider, log VCU im lặng, CAN vẫn 0 | `POST /deployments/{room}/restart/{node}` |
| IVI Gateway mất kết nối | không push VHAL | restart node |
| Guest mất IPv4 trên `eth1` | `curl 10.99.0.3:8080` → (7); không gọi được ASR node | `ip addr add 10.99.0.14/24 dev eth1` + `ip route add 10.99.0.0/24 dev eth1 table legacy_system` |

Sau khi vá: `curl http://10.99.0.3:8080/health` (không cần `--interface`) trả
`{"status":"ok","model":"phowhisper-tiny-int8",…}` ⇒ **đường app → ASR node sống lại.**

Evidence: `evidence/c2/vhal-local-fake-server-blocker-0819.txt` (gồm cả A3 và A4).

### A5. Bảy điều mới biết về nền tảng (ghi để khỏi mất giờ lần sau)

1. `POST /deployments/{room}/restart/{node}` trả **HTTP 500 nhưng vẫn có tác dụng**;
   node đi `Provisioning → Running` trong ~50–60s. Script-node stateless, không tốn quota.
2. Guest **không có route ra internet** (`ip route` chỉ có `10.0.2.0/24`). Chuyển file
   vào Device phải qua **USB image**, không `curl` được.
3. Widget **USB Device**: ô "Mirror base" ghi `/sdcard/Movies/ota_` nhưng đường mount
   thật là **`/sdcard/Music/usb_1`** (panel hiển thị đúng sau khi Plug). Tin panel.
4. `ping -I eth1` thông trong khi `ping` thường unreachable ⇒ **không phải lỗi L2**, mà
   là policy routing Android. Phải hỏi kernel bằng `ip route get <ip>` (và
   `ip route get <ip> mark 0x64` để mô phỏng traffic app) rồi thêm route vào **đúng
   bảng nó trả về** (ở đây là `legacy_system`, không phải `wlan0`).
5. Sau khi thêm route, **route cache cũ còn hiệu lực vài giây** → lần thử đầu vẫn fail.
6. `netstat` không tồn tại trên Android 14; dùng `ss -tlnp`.
7. Widget **IVI Screen** có `Recorder Part: Client Microphone` → **mic laptop truyền
   thẳng vào máy ảo**. Nghĩa là tự chạy phiên giọng nói được tại máy mình.

---

## PHẦN B — ĐANG Ở ĐÂU (đối chiếu bảng chấm Vòng 2)

Trung thực: **chưa dòng feedback nào được đáp ứng trọn vẹn**, vì tất cả đều đòi một
lượt chạy có bằng chứng, mà hôm nay chưa có lượt nói nào.

| Dòng BGK | Trạng thái | Còn thiếu |
|---|---|---|
| ② Artifact identity −1 | 🟢 **Gần đủ** — source build được từ `0c67010`, SHA-256 khớp ba chặng | Manifest hợp nhất thêm node image digest, video, logs, test |
| ④ Evidence platform −2 | 🟡 **Một phần** — có log node ASR (`?container=user`), log VCU/IGW, readback ba tầng | "Biên nhận cùng identity": một UUID ở logcat + log node + verdict + readback |
| ④ Align −3 | 🟡 **Tiền đề xong** — app `real` đúng hash, ASR `/health` OK từ guest, mic bật | Một lượt nói đi hết chuỗi trong cùng run |
| ① Đầy đủ / ổn định / đúng / biên (−8) | 🔴 Chưa | Phiên chạy có kịch bản, lặp lại, edge case |
| ② Observability −1 | 🔴 Chưa | Correlated trace trong cùng run |
| ③ Counterfactual −2 | 🔴 Chưa | Ablation trên Device |
| ④ Độ sâu −4 | ⛔ **Bị chặn** — fake server trong image | Hạ tầng đổi image. Ngả khác: ablation `AsrEngine.VIVA → GOOGLE` để chứng minh ASR node là điều kiện cần |

**Thứ hôm nay thật sự đạt được mà không nằm trong bảng:** ô "Ranh giới" (2/2) và
"Minh bạch giới hạn" (1/1) được củng cố. Trước đây "VHAL readback chưa có" là khoảng
trống không giải thích được; giờ là blocker có nguyên nhân, có bằng chứng, thuộc hạ
tầng. Đó là khác biệt giữa *"đội chưa làm"* và *"đội biết chính xác cái gì chặn"*.

---

## PHẦN C — LÀM GÌ TIẾP

### C1. Ngay lượt tới (chưa test được hôm nay): MỘT LƯỢT NÓI

App đang ở trạng thái "Hotword armed". Nói **"Vi-Vi ơi"** → **"phát nhạc lên"**, rồi:

```
logcat -d -s VIVA_TRACE:I VIVA_VOICE:I | tail -25
```

Cần thấy `asr_sent` → `asr_done` → `VIVA_TRACE_SUMMARY|<uuid>|…|media_play|Allow`.
Một lượt này bật cùng lúc: ④ align · ② observability · ④ evidence (vế app+node) ·
② identity (cùng APK đã khóa hash).

⚠️ Nếu mic không ăn → đó là mắt xích cuối chưa kiểm; ghi lại triệu chứng, đừng đoán.

### C2. Script kiểm 30 giây trước mỗi phiên

Ba thứ ở A4 **sẽ hỏng lại** sau mỗi lần platform restart. Cần một script chạy trước
mỗi buổi:

```
1. REST đọc vcu/Speed, PWT_VehicleSpeed, Vehicle.Speed → ba giá trị có khớp nhau không
2. Kéo slider một nhịp → log VCU có phản ứng không (nếu không: restart node VCU)
3. Trong guest: ip -o addr show eth1 → có IPv4 không (nếu không: gán lại + route)
4. Trong guest: curl -m 5 http://10.99.0.3:8080/health
```

### C3. Báo mentor/hạ tầng

Mang `evidence/c2/vhal-local-fake-server-blocker-0819.txt` đi hỏi hai việc:
- Xin image trout **không** bật `use_local_fake_server`, hoặc tham số để VHAL client
  trỏ tới IVI Gateway thay vì fake server nội bộ.
- Vì sao `eth1` mất IPv4 và node script mất subscription sau 12/08; có cách nào để
  nền tảng tự cấu hình lại thay vì vá tay.

### C4. Phiên vàng — đổi trọng tâm sang Media consumer

BGK cho phép *"VHAL/CAN **hoặc** Media consumer"*. Đường media **không** dính blocker
fake server. Chi tiết kịch bản ở `34-PLAN` Phase 1; điều chỉnh: bỏ Block B
(counterfactual tốc độ) cho tới khi có image mới, giữ nguyên các block còn lại.

### C5. Việc dọn còn treo

- `vcu/Speed` đang để **60**; trạng thái gốc trước phiên là **0** → trả về bằng slider.
- USB image `viva-usb` vẫn đang **attach** → Unplug khi không cần.
- `ip addr`/`ip route` vá tay sẽ mất sau reboot — không dùng làm nền cho claim
  "chạy được" nếu không kèm runbook C2.

---

## PHỤ LỤC — FILE SINH RA HÔM NAY

| File | Nội dung |
|---|---|
| `evidence/c2/car-speed-permission-probe-0818.txt` | `CAR_SPEED = prot=dangerous`, không cần M1a để đọc |
| `evidence/c2/real-apk-local-build-0819.txt` | Identity APK `real`, bài học `clean` build |
| `evidence/c2/vhal-local-fake-server-blocker-0819.txt` | Root cause VHAL + khôi phục mạng room |
| `vong2/32-ENHANCE-KHOI-4-PLATFORM-CARSKY.md` | 7 phát hiện blueprint/pin/allowlist (18/08) |
| `vong2/34-PLAN-CAI-THIEN-CARSKY-SAU-VONG-2.md` | Plan 3 phase, đã cập nhật cảnh báo Block B |
| `vong2/35-NHAT-KY-CARSKY-19-08.md` | File này |
