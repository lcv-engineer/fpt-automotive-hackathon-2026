# KẾ HOẠCH VÒNG 3 — CHUNG KẾT 22–23/08/2026

> **Lập ngày:** 19/08/2026 · **Người lập:** Long · **Trạng thái:** bản chủ, các file 01–07 chi tiết hoá từ đây
>
> 🔴 **ĐỌC [`08-HOA-GIAI-VOI-PHIEN-19-08.md`](08-HOA-GIAI-VOI-PHIEN-19-08.md) TRƯỚC.** Phiên CarSky 18–19/08 của Vĩ
> đã **đóng G-A và G-B**. Khi mâu thuẫn, file 08 thắng.
>
> **Nguồn sự thật:** email BTC "Lịch trình & Quy chế Vòng Chung kết" (18/08, 14:18) ·
> `Phan_hoi_Vong2_viva.docx` (BTC khoá 17/08) · thể lệ chính thức mục II bảng C ·
> ghi chú hội thoại `09_08_2026.md` → `18_08_2026.md`

---

## 1. Xuất phát điểm: 70/100

BTC đã công bố điểm Vòng 2 kèm 29 tiểu mục có barem và locator.

| Tiêu chí | Điểm | Tối đa | Chưa ghi nhận |
|---|---|---|---|
| Demo end-to-end và chức năng cốt lõi | 17 | 25 | **8** |
| Chất lượng kỹ thuật và bằng chứng thực thi | 14 | 20 | 6 |
| Giá trị tăng thêm và phần đội trực tiếp thực hiện | 19 | 25 | 6 |
| **Mức tận dụng nền tảng / phù hợp hệ sinh thái** | **6** | **15** | **9** |
| Hiểu người dùng, khách hàng và khả năng triển khai | 9 | 10 | 1 |
| Trình bày và trả lời làm rõ | **5** | **5** | 0 |
| **Tổng** | **70** | **100** | 30 |

Hai điều phải đọc đúng từ bảng này:

- **Trình bày và trả lời làm rõ đạt trần tuyệt đối 5/5.** Cả 5 tiểu mục — claim nối evidence, dẫn demo,
  ưu tiên thời lượng, minh bạch giới hạn, làm rõ nhất quán — đều đạt trần. Đây là năng lực đã được BTC
  xác nhận bằng văn bản, và Vòng 3 có **20 điểm** cho đúng năng lực đó (Thuyết trình 10 + Trả lời BGK 10).
- **Nền tảng 6/15 là hố sâu nhất**, trong đó tiểu mục *"Độ sâu trong core flow"* nằm ở **0/4**.

> ⚠️ BTC ghi rõ: điểm Vòng 2 **không mở lại**, khuyến nghị **không tạo nghĩa vụ hoàn thành trước 23/08**,
> và Vòng 3 chấm bằng bảng 100 điểm riêng. Bản feedback này dùng làm **tình báo về cách giám khảo nhìn**,
> không phải danh sách việc phải làm cho xong.

---

## 2. Phát hiện quyết định hướng đi

BTC ghi điều kiện để ô *"Đường align với ecosystem"* lên 3–5/5:

> *"cần App gọi đúng ASR node **và** policy output đi tới VHAL/CAN **hoặc** Media consumer trong cùng run."*

**Đội đã chạy được đúng chuỗi đó từ 10/08.** Bằng chứng `evidence/c2/carsky-voice-e2e-20260810/`:

- APK `app-mock-debug.apk`, SHA-256 `6fede5ae…0706`, build với
  `-PvivaAsrEngine=remote -PvivaAsrBaseUrl=http://10.99.0.3:8080`
- Device `VIVA` (`v37aa3knc6t1embelr5yi`), deployment `RUNNING`, 22 node
- Chuỗi: mic → Silero VAD → **container `viva-asr` trong room CarSky** → NLU → SafetyGuard → thực thi
- 25 lượt: 13 `Allow` intent đúng phủ 6 nhóm chức năng, 2 lượt `Deny:G1_SPEED_LOCK` đúng luật
- Độ trễ thật: p50 = 1336 ms, p95 = 1664 ms

Cả hai vế BTC đòi — *App gọi đúng ASR node* và *policy output đi tới consumer* — đều nằm trong **cùng một run**.

**Nhưng video nộp lại quay lát cắt emulator/mock.** BTC chấm đúng thứ có trong video:
*"video chỉ khóa emulator/mock slice"*, *"hành vi quan sát được là một lát cắt trên emulator/mock"*.

### Hệ quả

Phần lớn 9 điểm nền tảng và 8 điểm demo mất vì **bằng chứng mạnh nhất không được quay**, không phải vì
chưa làm được. Đây là lỗi đóng gói, sửa được trong 3 ngày. Việc đắt nhất của Vòng 3 không phải viết thêm
code mà là **quay đúng thứ đã chạy**.

### Đòn phản công cho ô "Độ sâu trong core flow"

BTC cho 0/4 với lý do: *"bỏ CarSky vẫn giữ được flow HVAC/media quan sát hiện nay"*.

Câu trả lời không phải tranh luận mà là **diễn ngay trên sân khấu**.

> 🔴 **CẬP NHẬT 19/08 — cách diễn đã đổi.** Bản gốc là "dừng node ASR cho lệnh chết". Phiên 19/08 phát
> hiện guest bị cô lập L3 với mạng room, nên cách đó có rủi ro. **Dùng khoảnh khắc ②′ ở
> [`08-HOA-GIAI`](08-HOA-GIAI-VOI-PHIEN-19-08.md) §5** — kéo slider tốc độ trên panel CarSky, chiếu
> 4 tầng đọc lại cùng đổi (GPIO → CAN → KUKSA → gateway push), rồi `Deny:G1_SPEED_LOCK`. Mạnh hơn,
> và không phụ thuộc mạng lúc diễn.

Đây là chỗ ăn trực tiếp ô **+10 "Tận dụng tối đa nền tảng & starter pack"** của Vòng 3.

---

## 3. Barem Vòng 3 (bảng C, thể lệ mục II)

| Khối | Điểm | Chi tiết |
|---|---|---|
| **Demo** | **40** | +15 demo hoạt động đúng như mô tả · +10 demo bằng video hoặc mô hình thật · **+10 tận dụng tối đa nền tảng & starter pack** · +05 câu chuyện AI ấn tượng |
| **Tính sáng tạo, khả thi, hiệu quả** | **35** | +10 đổi mới · +10 khả thi cao, dễ triển khai · **+10 chứng minh tiềm năng cải thiện hiệu suất / giảm chi phí / tăng doanh thu** · **+05 kết hợp nguyên liệu từ ≥2 domain** |
| Trả lời câu hỏi từ BGK | 10 | +05 rõ ràng, logic, hiểu sâu · +05 thông tin chính xác và dẫn chứng cụ thể |
| Kỹ năng thuyết trình | 10 | 5 ô × 2đ: phong thái · mạch lạc · ngôn ngữ nói/hình thể/âm lượng · **đúng thời gian** · tương tác |
| Tài liệu, slide thuyết trình | 5 | +01 đủ nội dung · +01 phương pháp + kết luận + đề xuất · +01 không lỗi chính tả · +02 source code + tài liệu mô tả |

**95/100 quyết trong 20 phút trên sân khấu.** Tài liệu chỉ còn 5đ.

Ba ô đang bỏ trắng, cộng lại **25đ**:

| Ô | Điểm | Vì sao đang trắng | File xử lý |
|---|---|---|---|
| Chứng minh hiệu quả kinh tế | +10 | Đội chưa có con số ROI nào; BTC cũng trừ 1đ ô Outcome/adoption vì H1–H4 chưa đo | [`03-HIEU-QUA-KINH-TE.md`](03-HIEU-QUA-KINH-TE.md) |
| Kết hợp ≥2 domain | +05 | DTC/UDS cố ý để dành Vòng 3 nhưng chưa nối | §7 file này — giải bằng lập luận, không bằng code |
| Tận dụng tối đa nền tảng | +10 | Vòng 2 ô này 6/15; đội cố ý không tối ưu vì Vòng 2 không tính starter pack | §2 file này + [`05-SPIKE-ROUND-TRIP.md`](05-SPIKE-ROUND-TRIP.md) |

---

## 4. Lịch trình cứng từ BTC

### Thứ Bảy 22/08 — Hola Park

| Giờ | Nội dung |
|---|---|
| 08:00–09:00 | Đón thí sinh & mentor từ TT HN về Hola Park |
| 09:00–09:30 | Tập trung, thăm quan Campus |
| **09:30–10:00** | **Minigame: lựa chọn thứ tự thuyết trình Chung kết** |
| 10:00–10:30 | Tea Break Welcome |
| **10:30–12:00** | **Lập trình Code — Tích hợp phần mềm (1h30)** |
| 12:00–14:00 | Ăn trưa + nghỉ |
| **14:00–16:00** | **Lập trình Code — Tích hợp phần mềm (2h)** |
| 16:00–17:00 | Di chuyển lên FPT Tower tổng duyệt Demo |
| **17:00–18:00** | Tổng duyệt 10 phút/đội · **GỬI BTC FILE BÀI TẬP & DEMO** |
| 18:00 trở đi | Ăn tối, về khách sạn |

> **Tổng thời gian code thật tại chỗ chỉ có 3 giờ 30 phút.** Mọi thứ cần build phải xong trước khi lên đường.
> 17:00 ngày 22/08 là hạn nộp thật — không phải 23/08.

### Chủ Nhật 23/08 — FPT Tower, Phạm Văn Bạch

| Giờ | Nội dung |
|---|---|
| 06:30–07:30 | Ăn sáng, thu xếp đồ |
| 07:30–08:15 | Di chuyển đến FPT PVB |
| 08:52–08:55 | Kick-off sự kiện |
| 08:55 / 09:15 / 09:35 / 09:55 / 10:15 / 10:35 | **6 slot thuyết trình, mỗi đội 20 phút** |
| 10:55–11:10 | BGK chấm điểm cuối cùng |
| 11:20–11:35 | Công bố & trao giải chính |

**Quy chế phần thi:** tối đa 20 phút — **10 phút trình bày/demo + 10 phút Q&A**. Đồng hồ đếm ngược
chiếu trực tiếp trên màn hình sự kiện. Trang phục: áo Automotive Hackathon do BTC cấp.
Xác minh danh tính bằng **CCCD hoặc VNeID Mức 2** tại check-in ngày 22/08.

### Giải thưởng — 6 đội, 6 giải

| Giải | Tiền |
|---|---|
| Winner Prize | 100.000.000 |
| Runner-up Prize | 80.000.000 |
| **4 × Promising Talent Awards** | **20.000.000** |
| Audience Choice Awards | 10.000.000 |

Cộng hỗ trợ 5.000.000/đội chi trả cùng giải thưởng.

**Sàn 20 triệu được bảo đảm** — 6 giải cho 6 đội. Nghĩa là không có kịch bản "trắng tay", và điều đó
cho phép đội chọn thế đánh hướng lên trần thay vì thủ. Khoảng cách 20tr → 100tr mới là phần đang chơi.

---

## 5. Nguyên tắc

1. **Quay thứ đã chạy trước khi xây thứ chưa chạy.** Bài học đắt nhất của Vòng 2.
2. **Một run, một identity.** BTC lặp lại yêu cầu này ở 5 tiểu mục khác nhau: cùng một `traceId`, cùng
   một APK SHA-256, cùng một Device, cùng một lần chạy — nối từ audio qua intent, policy đến readback.
3. **Giữ nguyên phong cách minh bạch đã ăn 5/5.** Vòng 3 không có ô riêng cho minh bạch, nhưng nó là
   nguyên liệu của ô *"cung cấp thông tin chính xác và đưa ra dẫn chứng cụ thể"* (5đ) trong Q&A.
4. **Không mở claim mới.** Mọi câu nói trên sân khấu phải có locator trong repo.
5. **Đúng giờ là 2 điểm.** Ô *"Trình bày đúng thời gian quy định"* cho 2đ và có đồng hồ đếm ngược
   công khai. Vượt giờ là mất điểm nhìn thấy được.

---

## 6. Bốn gate

| Gate | Hạn | Điều kiện pass | Nếu fail |
|---|---|---|---|
| ~~**G-A**~~ | ~~19/08~~ | ✅ **ĐÓNG 19/08** — đã có shell root qua CarSky web ADB; `CAR_SPEED` là `prot=dangerous` nên `pm grant` đủ, không cần M1a | — |
| ~~**G-B**~~ | ~~20/08~~ | ✅ **ĐÓNG = BỊ CHẶN BỞI IMAGE.** `ro.vendor.vehiclehal.server.use_local_fake_server=true` → VHAL client nối fake server nội bộ. Root/priv-app vô ích | Đã thành **evidence mạnh**, xem file 08 §1 |
| **G-A′** | **20/08 22:00** | Vĩ xác minh app **thật sự** gọi được ASR node từ guest + viết runbook khôi phục `eth1` | Cắt khoảnh khắc ② khỏi live, giữ trong video, dùng ②′ thay thế |
| **G-C** | **21/08 20:00** | **Video dự phòng uncut đã quay xong, phát được, đúng một artifact identity** | ⛔ **Gate cứng.** Chưa có video thì không ai lên đường. Đây là ô +10 "demo bằng video" và là bảo hiểm cho ô +15 |
| **G-D** | 22/08 16:00 | Code freeze khi rời Hola Park | Sau giờ này chỉ tập nói, không chạm code. 17:00 nộp file cho BTC |

G-C là gate quan trọng nhất. Barem cho **+10 cho "demo bằng video hoặc mô hình thật"** — video được chấp
nhận rõ ràng. Nó vừa ăn 10đ vừa che rủi ro ASR trượt trên sân khấu cho ô +15.

---

## 7. Ba làn và phân công

| Làn | Chủ trì | Nội dung | Giờ |
|---|---|---|---|
| **1 — Sân khấu** | **Long** | Kịch bản 10 phút, bộ Q&A, slide, mô hình hiệu quả kinh tế, decision table local↔cloud | ~20h |
| **2 — Độ tin cậy demo** | **Dương** | Chốt phủ định + test, TTS không bao giờ im, bộ câu đã chứng minh ổn định, **quay video dự phòng** | ~14h |
| **3a — Nền tảng** | **Vĩ** | Đánh thức Room, tái lập chuỗi ASR-node, **correlated trace một `traceId`**, spike quyền priv-app | ~14h |
| **3b — VHAL** | **Tùng** | `setProperty` → readback → gateway Lua → KUKSA/CAN → CCU, và luồng ngược về callback/UI | ~14h |

Long không nhận việc code. 20 phút trên sân khấu là của anh ấy, thời gian anh ấy phải được bảo vệ.

Chi tiết theo người, có giờ và định nghĩa "xong": [`07-PHAN-CONG-VONG-3.md`](07-PHAN-CONG-VONG-3.md).

### Ô "+05 kết hợp ≥2 domain" — giải bằng lập luận, không bằng code

**Quyết định: KHÔNG nối UDS/DTC thật.** Với 3,5 ngày và ba làn đang chạy, đây là việc thứ tư và nó sẽ
ăn vào G-C.

Thay vào đó khai đúng cái đang có: sản phẩm đã bắc qua **Digital Cockpit** (voice, HMI, media) và
**Vehicle Middleware** (VHAL/CarPropertyManager, KUKSA/VSS, CAN, gateway Lua đội đã sửa). Nếu G-B pass
thì đây là hai domain có bằng chứng chạy trong cùng một run, không phải hai domain kể miệng.

> ⚠️ **Màn Diagnostics hub không được đưa vào demo.** `HardwareDiagnosticsRepository.kt` lấy dữ liệu từ
> `defaultDtcs()` / `defaultPredictive()` / `defaultTwinHotspots()` hardcode. Diễn nó mà BGK hỏi
> *"số này ở đâu ra"* là mất điểm đúng ô *"thông tin chính xác và dẫn chứng cụ thể"* (5đ) — chỗ đội mạnh nhất.
> Nếu vẫn muốn hiện màn này thì phải có nhãn **MÔ PHỎNG** trên chính màn hình.

---

## 8. Lịch ngày-theo-ngày

> ⏰ **Đã là 19/08 23:00.** Chỉ còn **2 buổi tối làm việc: 20/08 và 21/08.** Phần lớn Làn 3 đã được
> Vĩ hoàn thành trong hai phiên 18–19/08 — xem [`08-HOA-GIAI`](08-HOA-GIAI-VOI-PHIEN-19-08.md).

### ✅ Đã xong 18–19/08

**Hành chính (Long):** xác nhận thông tin 4 thành viên với BTC · gửi link mời khán giả
(Audience Choice 10tr) · chốt vé ra Hà Nội. **Không còn việc hành chính nào chặn.**

**Kỹ thuật (Vĩ):** bản `real` lên Device lần đầu (SHA-256 `48f9830f…`, hash khớp 3 chặng, `CAR_SPEED granted`) ·
chuỗi nền tảng GPIO→VCU→CAN→KUKSA→gateway-push chứng minh chạy với readback 3 tầng ·
**root cause VHAL readback** · khôi phục mạng room và 2 script-node mất subscription.

### 20/08 (T5) — tối

| Ai | Việc |
|---|---|
| **Vĩ** | ⚠️ **G-A′ trước mọi việc khác**: xác minh đường ASR từ guest + runbook khôi phục `eth1` |
| Vĩ | Chuẩn bị khoảnh khắc ②′ trên slider Drive Controls (file 08 §5) |
| Dương | Chốt phủ định + test hồi quy N5–N7 · TTS không bao giờ im |
| Dương | Kiểm Y1–Y3 trên Device |
| Tùng | Soạn phần nói về root cause fake server — **đây là phần của Tùng trên sân khấu** |
| Long | Kịch bản (đổi ② → ②′) · bộ Q&A (viết lại A1 theo file 08 §9) · mô hình kinh tế · decision table |

### 21/08 (T6) — ngày đóng gói

| Ai | Việc |
|---|---|
| Dương + cả đội | **G-C: quay video dự phòng uncut.** Việc quan trọng nhất trong tuần |
| Vĩ | Đóng artifact identity: một commit, một APK SHA-256, một Device, một bộ log |
| Long | Slide xong + tập nói lần 1 bấm giờ |
| Cả đội | Di chuyển ra Hà Nội nếu cần |

### 22/08 (T7) — tại chỗ, 3,5 giờ code

| Giờ | Việc |
|---|---|
| 09:30–10:00 | Minigame chọn slot — **xem §10 để biết chọn slot nào** |
| 10:30–12:00 | Cắm Device thật, chạy lại toàn bộ kịch bản trên hạ tầng sự kiện |
| 14:00–16:00 | Vá đúng thứ hỏng tại chỗ. **Không thêm tính năng** |
| 16:00 | **G-D code freeze** |
| 17:00–18:00 | Tổng duyệt 10 phút + nộp file cho BTC |
| Tối | Tập nói lần cuối, ngủ sớm |

### 23/08 (CN)

Dậy 06:30, có mặt 08:15. Trước slot 30 phút: cắm thử máy chiếu, thử âm thanh, mở sẵn video dự phòng
ở tab riêng.

---

## 9. Rủi ro và cách chặn

| Rủi ro | Mức | Cách chặn |
|---|---|---|
| ASR nghe trượt trên sân khấu | 🔴 Cao | Chỉ dùng bộ câu đã chứng minh ([`06-BO-CAU-DEMO-ON-DINH.md`](06-BO-CAU-DEMO-ON-DINH.md)); video dự phòng đã bật sẵn; người dẫn có câu thoát chuẩn bị trước |
| Room CarSky đã bị thu hồi sau Vòng 2 | 🔴 Cao | Kiểm tra tối nay 18/08. Nếu mất, báo mentor ngay và chuyển sang demo bằng video + emulator |
| Vá phủ định giết luôn lệnh "quạt mức **không**" | 🟠 Vừa | Tiếng Việt dùng "không" làm cả số 0. Chốt phủ định phải theo token và nhận biết "không" ở vị trí slot số là số 0. Có test hồi quy cho `cmd_fan_0` |
| Vượt 10 phút trên sân khấu | 🟠 Vừa | Ô "đúng thời gian" 2đ, có đồng hồ công khai. Tập bấm giờ tối thiểu 3 lần, cắt nội dung chứ không nói nhanh |
| Mạng/hạ tầng sự kiện khác môi trường đã test | 🟠 Vừa | 10:30–12:00 ngày 22/08 dành riêng để chạy lại trên hạ tầng thật |
| TTS im tiếng giữa demo | 🟡 Thấp | Làn 2 vá; trên sân khấu im lặng bị đọc là hỏng |
| Long ốm/mất giọng | 🟡 Thấp | Dương học thuộc kịch bản làm người thay |

---

## 10. Chọn slot thuyết trình (minigame 09:30 ngày 22/08)

6 slot: 08:55 · 09:15 · 09:35 · 09:55 · 10:15 · 10:35.

**Ưu tiên: slot 4 (09:55) hoặc slot 5 (10:15).** Lý do:

- **Tránh slot 1 (08:55)** — BGK chưa có mốc so sánh, thường chấm chặt hơn; đội đi đầu cũng gánh rủi ro
  trục trặc kỹ thuật của hội trường.
- **Tránh slot 6 (10:35)** — BGK đã nghe 5 đội, và 10:55 là hạn chấm cuối nên áp lực thời gian dồn vào đội cuối.
- Slot 4–5 vừa đủ muộn để hưởng hiệu ứng gần cuối, vừa chưa chạm vùng mệt.

Nếu minigame không cho chọn tự do thì thứ tự ưu tiên là: **4 → 5 → 3 → 2 → 6 → 1**.

---

## 11. Danh sách cố ý KHÔNG làm

Ghi ra để không ai lặng lẽ làm rồi ăn mất thời gian của G-C.

| Không làm | Vì sao |
|---|---|
| Nối UDS/DTC thật | Việc thứ tư trong 3,5 ngày; ô +05 giải được bằng lập luận (§7) |
| Xây `VivaCarService` + AIDL đầy đủ | Không đủ thời gian, và bảng C Vòng 3 không có ô riêng cho service framework |
| Thêm intent mới | Barem Vòng 3 không cộng điểm theo số lượng chức năng |
| Refactor, dọn kiến trúc | Không ăn điểm, có rủi ro làm vỡ thứ đang chạy |
| Chạy benchmark corpus mới quy mô lớn | BTC nói rõ: dùng corpus hiện có, thêm vài biến thể khó có chủ đích |
| Đưa Diagnostics hub vào demo | Dữ liệu hardcode — rủi ro Q&A lớn hơn lợi ích (§7) |
| Sửa số 65% / 28,75% thành số mới | BTC đã ghi "chưa đủ thẩm quyền". Bỏ hẳn khỏi bài nói, đừng thay bằng số khác cũng chưa đo |

---

## 12. Các file trong bộ này

| File | Nội dung |
|---|---|
| [`01-KICH-BAN-10-PHUT.md`](01-KICH-BAN-10-PHUT.md) | Kịch bản sân khấu tính theo giây, 6 khoảnh khắc demo, câu thoát khi trượt |
| [`02-QA-BGK-CHUNG-KET.md`](02-QA-BGK-CHUNG-KET.md) | Bộ Q&A dựng từ chính feedback BTC + mentor, mỗi câu có locator |
| [`03-HIEU-QUA-KINH-TE.md`](03-HIEU-QUA-KINH-TE.md) | Mô hình hiệu quả kinh tế có giả định ghi rõ — ô +10 đang bỏ trắng |
| [`04-DECISION-TABLE-LOCAL-CLOUD.md`](04-DECISION-TABLE-LOCAL-CLOUD.md) | Bảng quyết định local rule ↔ cloud fallback — BTC khuyến nghị bằng văn bản |
| [`05-SPIKE-ROUND-TRIP.md`](05-SPIKE-ROUND-TRIP.md) | Runbook G-A/G-B: `adb root` → priv-app → `setProperty` → readback → correlated trace |
| [`06-BO-CAU-DEMO-ON-DINH.md`](06-BO-CAU-DEMO-ON-DINH.md) | Bộ câu đã chứng minh chạy trên Device, câu cấm dùng, câu thoát |
| [`07-PHAN-CONG-VONG-3.md`](07-PHAN-CONG-VONG-3.md) | Phân công theo người: giờ, hạn, định nghĩa "xong", ai chờ ai |
