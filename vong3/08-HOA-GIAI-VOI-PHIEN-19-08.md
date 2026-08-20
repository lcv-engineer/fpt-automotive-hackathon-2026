# HOÀ GIẢI — KẾT QUẢ PHIÊN 18–19/08 CỦA VĨ

> **Lập:** 19/08/2026 23:30 · **Ưu tiên:** file này **thắng** khi mâu thuẫn với
> `00-KE-HOACH-VONG-3.md` §6 (gate) và `05-SPIKE-ROUND-TRIP.md`.
>
> **Nguồn:** `evidence/c2/vhal-local-fake-server-blocker-0819.txt` ·
> `evidence/c2/real-apk-local-build-0819.txt` · `evidence/c2/car-speed-permission-probe-0818.txt` ·
> `vong2/34-PLAN-CAI-THIEN-CARSKY-SAU-VONG-2.md` · `vong2/35-NHAT-KY-CARSKY-19-08.md`

---

## 0. Vì sao có file này

Bộ `vong3/00–07` viết chiều 19/08 dựa trên trạng thái **10/08**. Nhưng Vĩ đã chạy hai phiên CarSky ngày
18 và 19/08 và kết quả **lật ngược hai gate**. Không sửa thì đội sẽ đi làm lại việc đã xong và đâm vào
tường đã có người dò ra.

---

## 1. G-B đã có câu trả lời: **BỊ CHẶN BỞI IMAGE, KHÔNG PHẢI LỖI ĐỘI**

**Đóng gate G-B. Không ai được tốn thêm một giờ nào cho `setProperty` readback.**

Image AAOS bật:

```
ro.vendor.vehiclehal.server.use_local_fake_server = true
ro.boot.vendor.vehiclehal.server.cid  = 1      (vsock loopback)
ro.boot.vendor.vehiclehal.server.port = 9210
```

`ps -ef` xác nhận **hai** tiến trình VHAL cùng chạy: fake-hardware-grpc-server (PID 477) và
trout-service client (PID 478). Client nối vào **fake server nội bộ**, không bao giờ nối tới IVI Gateway
của room. `ro.vendor.*` nướng trong vendor partition — **không `setprop` đè lúc chạy được**.

⇒ Root, priv-app, M1a, allowlist — **tất cả đều vô ích** cho vế readback. Đây là lời giải vì sao
E06/E07/E08 chưa từng đạt suốt Vòng 2.

### Nhưng đây là TIN TỐT, không phải tin xấu

Cùng phiên đó Vĩ chứng minh **chuỗi nền tảng chạy tới tận hop cuối**, có readback ba tầng và log node:

```
GPIO   vcu/Speed                  = 60   ts 03:41:51.161Z   ✅
CAN    PWT_VehicleSpeed/Speed_kph = 60   ts 03:49:14.613Z   ✅
KUKSA  Vehicle.Speed              = 60   ts 03:41:51.300Z   ✅
[vcu] speed=60 km/h
[igw] → vhal 0x11600207 area=0x0 pushed = 16.6666        (= 60 km/h, quy đổi đúng)
```

**GPIO → VCU → PWT-CAN → PWT Gateway → KUKSA → IVI Gateway → VHAL push: chạy hết.**
Chỉ hop cuối vào guest bị fake server nuốt.

> Đây là thứ mạnh hơn nhiều so với "đội chưa làm được VHAL". Nó là:
> *"Đội đã đi hết chuỗi nền tảng, đo được ở từng tầng, và chỉ ra chính xác dòng cấu hình trong image
> làm đứt hop cuối."*

---

## 2. G-A đã có câu trả lời một phần

| Câu hỏi | Trạng thái 19/08 |
|---|---|
| Có shell root trên Device không? | ✅ **Có** — qua CarSky web ADB shell |
| Cài priv-app được chưa? | ❌ Chưa làm — **và giờ không còn lý do làm**, vì §1 |
| Đọc được `CAR_SPEED` không? | ✅ **Được, không cần M1a.** `prot=dangerous` trên image FAuto Trout → `pm install` thường + `pm grant` là đủ |
| Ghi climate/doors? | ❌ Vẫn `signature\|privileged`, **và vẫn vô ích vì §1** |

⇒ **Đóng gate G-A.** Vĩ không cần dùng hộp thời gian 3 giờ cho `adb root`/`remount` nữa.

---

## 3. Bản `real` đã lên Device — lần đầu tiên

| Trường | Giá trị |
|---|---|
| APK | `app-real-debug.apk`, SHA-256 `48f9830f…23677ea7e`, 227.759.181 bytes |
| Commit | `0c67010` |
| Hash khớp | **ba chặng** — máy dev = file trên USB = `base.apk` đã cài |
| Quyền | `CAR_SPEED granted=true` |
| App chạy | Màn hình VIVA đủ, *"Hotword armed — say Vi-Vi ơi"* |

**Đây là thứ đóng ô Artifact identity mà Vòng 2 chỉ được 1/2.**

### Hai bài học vận hành bắt buộc nhớ

1. ⚠️ **Mọi APK làm evidence phải build sau `clean`.** Build incremental để lại ~78 MB byte chết
   trong archive (AGP gỡ entry khỏi central directory nhưng không compact) → hash không tái lập được.
2. ⚠️ **Chuyển file vào guest không dùng `curl`** — guest không có route ra ngoài. Đường sống: tạo
   USB image FAT32 → artifact → widget USB Device → Plug → file ở `/sdcard/Music/usb_1/`.

---

## 4. ⚠️ Khoảnh khắc ② của tôi có rủi ro tôi chưa biết

`01-KICH-BAN` §4② dựa trên giả định: dừng node `viva-asr` thì lệnh chết.

**Phiên 19/08 phát hiện guest bị cô lập L3 với mạng room:**
```
curl 10.99.0.3:8080/health   → (7) connection refused
ping 8.8.8.8                 → unreachable
guest chỉ có route 10.0.2.0/24
```

Vĩ ghi rõ: *"Đường app gọi ASR 10/08 đi qua network_security_config + cơ chế khác, KHÔNG phải TCP thẳng
từ guest — cần xác minh lại riêng."*

Và: sau pod restart 12/08, **guest mất IPv4 trên `eth1`** nên không gọi được ASR node; đã khôi phục
bằng tay 19/08, **phải kiểm lại trước mỗi phiên**.

### Việc bắt buộc — Vĩ, tối 20/08, trước mọi việc khác

- [ ] Xác minh app **thật sự** gọi được ASR node từ guest ở trạng thái hiện tại
- [ ] Xác minh dừng node → lệnh **thật sự** chết (không có fallback nào cứu)
- [ ] Đo thời gian node `Running` trở lại sau `POST /deployments/{room}/restart/{node}`
- [ ] Ghi lại **cách khôi phục `eth1`** thành runbook — nếu sáng 22/08 nó hỏng lại thì phải sửa được trong 10 phút

**Nếu không xác minh được:** cắt khoảnh khắc ② khỏi kịch bản live, giữ nó **trong video** (quay lúc
mạng đang lành), và thay bằng khoảnh khắc ②′ ở §5.

---

## 5. Khoảnh khắc ②′ — mạnh hơn bản gốc, và không phụ thuộc mạng lúc diễn

Thay vì chứng minh "bỏ CarSky thì đứt" bằng cách rút node, **chứng minh bằng chính chuỗi nền tảng**:

> Kéo slider **Drive Controls** trên panel CarSky từ 0 lên 60 km/h.
> Chiếu song song ba giá trị đọc lại — GPIO `vcu/Speed`, CAN `PWT_VehicleSpeed`, KUKSA `Vehicle.Speed`
> — cùng đổi theo, kèm log node `[vcu] speed=60 km/h` và `[igw] → vhal … pushed = 16.6666`.
> Rồi nói câu **"Vi-Vi ơi, mở cửa"** → `Deny:G1_SPEED_LOCK`.
>
> *"Tốc độ này không phải biến trong app chúng em. Nó xuất phát từ panel phần cứng của nền tảng, đi qua
> VCU, qua CAN, qua KUKSA, qua gateway. Bốn tầng các thầy cô vừa thấy đổi theo. Và chính nó làm tầng an
> toàn của chúng em đổi quyết định. Bỏ CarSky ra thì không có tốc độ nào cả."*

Rồi nói tiếp phần trung thực — **đây mới là chỗ ăn điểm nặng nhất:**

> *"Hop cuối cùng, từ gateway vào VHAL của máy ảo, thì đứt. Chúng em đã tìm ra vì sao: image bật
> `ro.vendor.vehiclehal.server.use_local_fake_server=true`, nên VHAL client trong guest nối vào một
> fake server nội bộ qua vsock loopback thay vì nối tới gateway của room. Nó nằm trong `ro.vendor`,
> nướng trong vendor partition, không đè lúc chạy được. Đây là ràng buộc của image, và chúng em báo
> hạ tầng thay vì tiếp tục debug phía app."*

**Vì sao ②′ mạnh hơn ②:**

| | ② rút node | ②′ chuỗi tốc độ |
|---|---|---|
| Phụ thuộc mạng lúc diễn | ✅ có — rủi ro | ❌ không |
| Chứng minh nền tảng là điều kiện cần | có | **có, mạnh hơn** — 4 tầng đổi theo trước mắt |
| Thể hiện hiểu sâu nền tảng | vừa | **rất cao** — chỉ ra đúng dòng cấu hình chặn |
| Trả lời "chưa làm được VHAL" | không | **có** — đổi từ "chưa làm" thành "đã dò ra tại sao" |

⇒ **Đề xuất: ②′ thay ② làm khoảnh khắc chính.** Giữ ② trong video nếu Vĩ xác minh được.

> ⚠️ Ràng buộc vận hành: **đặt tốc độ phải qua slider panel.** REST `actuate` trên `vcu/Speed`
> **không** sinh sự kiện cho script VCU — cờ `actuate` chỉ dành cho KUKSA. REST chỉ để đọc lại.

---

## 6. Bắt buộc dùng flavor `real`

`mock` đọc tốc độ từ **sóng sin trong app** (`MockVehicleRepository.kt:143`), không chạm VHAL.

⇒ Mọi bằng chứng "nền tảng là điều kiện cần" **phải chạy trên `real`**, nếu không claim sụp ngay câu
hỏi đầu tiên. Bản `real` đã có trên Device từ 19/08 — dùng đúng nó.

---

## 7. Gate cập nhật

| Gate | Trạng thái mới |
|---|---|
| ~~G-A `adb root`/remount~~ | ✅ **ĐÓNG** — có root, và không còn cần cho mục tiêu này |
| ~~G-B `setProperty` readback~~ | ✅ **ĐÓNG = BỊ CHẶN BỞI IMAGE**, có root cause, đã thành evidence |
| **G-A′** | 🆕 **20/08** — Vĩ xác minh đường ASR từ guest + runbook khôi phục `eth1` (§4) |
| **G-C video** | ⛔ **21/08 20:00 — giữ nguyên, vẫn là gate cứng duy nhất** |
| **G-D code freeze** | 22/08 16:00 — giữ nguyên |

---

## 8. Phân công cập nhật

| Người | Bỏ | Thêm |
|---|---|---|
| **Vĩ** | V2 spike `adb root` (3h) — đã xong | **G-A′** xác minh đường ASR + runbook `eth1` · chuẩn bị ②′ trên panel |
| **Tùng** | T2, T3, T4 (`setProperty`/gateway/luồng ngược) — bị chặn bởi image | **Soạn phần nói về root cause fake server** — đây là phần của Tùng trên sân khấu, và là câu trả lời A1 mới · hỗ trợ Vĩ phiên vàng |
| **Dương** | không đổi | không đổi — G-C vẫn là ưu tiên tuyệt đối |
| **Long** | không đổi | Viết lại A1 trong `02-QA` theo §1 · đổi ② thành ②′ trong `01-KICH-BAN` |

Tùng được giải phóng ~8 giờ. Đổ vào: hỗ trợ quay video G-C và tập trả lời Q&A.

---

## 9. Câu trả lời A1 viết lại — thay bản trong `02-QA-BGK-CHUNG-KET.md`

> **Chưa, và chúng em biết chính xác vì sao.**
>
> Chuỗi nền tảng thì chạy hết: em kéo tốc độ trên panel, nó đi qua VCU, qua CAN, qua KUKSA, tới gateway,
> và gateway push vào VHAL — chúng em đọc lại được ở cả bốn tầng, có log từng node.
>
> Hop cuối, từ gateway vào VHAL của máy ảo, thì đứt. Image bật
> `ro.vendor.vehiclehal.server.use_local_fake_server=true`, nên VHAL client trong guest nối vào một
> fake server nội bộ qua vsock loopback, không bao giờ nối tới gateway của room. Nó nằm trong
> `ro.vendor`, nướng trong vendor partition, không setprop đè được.
>
> Nên không phải chúng em chưa làm — mà là trên image này, `setProperty` rồi đọc lại qua
> `CarPropertyManager` **không thể** cho số thật. Guideline CDC cũng viết đúng tình huống này:
> *"property null thì báo team hạ tầng, đừng debug tiếp phía app."* Chúng em dừng đúng ở đó và mang
> biên nhận đi báo.
>
> Còn service framework riêng với AIDL thì đúng là chúng em chưa xây, và CCU là của nền tảng.

**Locator:** `evidence/c2/vhal-local-fake-server-blocker-0819.txt`

> 💡 Đây là câu trả lời biến khoản trừ nặng nhất của Vòng 2 thành minh chứng hiểu nền tảng sâu.
> **Tùng nói câu này**, không phải Long — nó cho thấy chuyên môn phân bố thật.

---

## 10. Việc hành chính — ✅ đã xong 19/08

| Việc | Trạng thái |
|---|---|
| Xác nhận thông tin 4 thành viên với BTC (đã nhắn LinhNT169) | ✅ xong |
| Gửi link mời khán giả — Audience Choice 10tr | ✅ xong |
| Chốt vé ra Hà Nội, kịp đón 08:00 ngày 22/08 tại TT HN | ✅ xong |

**Còn lại một mục duy nhất:** 4/4 người có **CCCD hoặc VNeID Mức 2 đã kích hoạt**, tên và năm sinh
trùng danh sách đăng ký. Xác minh danh tính diễn ra tại check-in **ngày 22/08**.

⇒ Từ đây trở đi **không còn việc hành chính nào chặn**. Toàn bộ thời gian còn lại là kỹ thuật và tập nói.
