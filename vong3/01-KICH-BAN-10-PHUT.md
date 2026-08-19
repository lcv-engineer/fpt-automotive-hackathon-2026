# KỊCH BẢN SÂN KHẤU — 10 PHÚT TRÌNH BÀY/DEMO

> **Khung BTC:** tối đa 20 phút = **10 phút trình bày/demo + 10 phút Q&A**.
> Đồng hồ đếm ngược chiếu trực tiếp trên màn hình sự kiện.
> Ô *"Trình bày đúng thời gian quy định"* = **2 điểm**, nhìn thấy được.
>
> **Người dẫn:** Long · **Người vận hành demo:** Vĩ · **Người thay thế:** Dương

---

## 1. Phân bổ 600 giây

| Mốc | Thời lượng | Nội dung | Ai |
|---|---|---|---|
| 0:00–0:40 | 40s | Vấn đề và người dùng | Long |
| 0:40–1:20 | 40s | Sản phẩm là gì + 1 slide kiến trúc | Long |
| **1:20–6:00** | **4:40** | **DEMO — 6 khoảnh khắc** | Long dẫn, Vĩ bấm |
| 6:00–7:10 | 70s | Khác biệt và số đo | Long |
| 7:10–8:20 | 70s | Hiệu quả kinh tế và khả thi triển khai | Long |
| 8:20–9:10 | 50s | Giới hạn nói thẳng + roadmap | Long |
| 9:10–9:40 | 30s | Chốt | Long |
| 9:40–10:00 | 20s | **Đệm** — không lên kế hoạch dùng | — |

Quy tắc cứng: **đến 6:00 mà demo chưa xong thì cắt khoảnh khắc còn lại, không kéo dài.** Nội dung
sau demo là chỗ ăn 20 điểm sáng tạo/hiệu quả — không được hy sinh cho demo.

---

## 2. Mở đầu — 0:00–0:40

> "Tài xế giao vận Việt Nam mỗi ca chạy 8 đến 10 tiếng. Mỗi lần chỉnh điều hoà hay đổi bài nhạc là một
> lần rời tay khỏi vô-lăng và rời mắt khỏi đường.
>
> VIVA là trợ lý giọng nói tiếng Việt chạy native trên Android Automotive OS — không phải app điện thoại
> chiếu lên xe. Tài xế nói, xe làm, và có một tầng an toàn đứng giữa quyết định lệnh nào được phép xuống xe.
>
> Trong 10 phút tới, phần lớn thời gian sẽ là demo chạy thật trên nền tảng CarSky."

**Slide:** 1 ảnh cabin + 1 dòng chữ. Không bullet.

---

## 3. Sản phẩm và kiến trúc — 0:40–1:20

Một slide duy nhất, ba khối lớn; bên dưới Body mới tô màu phân biệt phần đội sở hữu và nền tảng cấp:

```text
VIVA VOICE                 VIVA BRAIN                    VIVA BODY
wake · mic · VAD · ASR  →  context · intent · route  →  skills · SafetyGuard · adapters
TTS · audio            ←  response                  ←  media · VHAL/CarProperty ↔ KUKSA/CAN/CCU
```

> "VIVA có ba phần. Voice nghe và nói. Brain hiểu câu nói và tạo yêu cầu hành động có kiểu. Body thực
> thi media hoặc giao tiếp với xe. Điểm quan trọng là Brain không được ghi thẳng xuống VHAL: mọi lệnh
> xe đều bị SafetyGuard kiểm tra ở biên Body. Vì vậy sau này Brain có thể thông minh hơn bằng GenAI mà
> không trao cho AI quyền bỏ qua tầng an toàn."

> "Phần nền tảng CarSky cấp là Device AAOS, KUKSA, CAN, tám Script Node và ECU mô phỏng. Phần đội làm
> là voice pipeline tiếng Việt, Brain điều phối tất định hiện tại, tầng an toàn, adapter và đo đạc."

**Locator:** [`docs/architecture/VIVA-VOICE-BRAIN-BODY.md`](../docs/architecture/VIVA-VOICE-BRAIN-BODY.md)

> 💡 Câu này phục vụ ô *"khả thi cao, dễ triển khai"* (+10) — giám khảo cần thấy ranh giới tích hợp rõ
> thì mới tin sản phẩm cắm được vào xe thật.

---

## 4. DEMO — 1:20–6:00

**Thứ tự có chủ đích:** chạy được → nền tảng là điều kiện cần → an toàn → thông minh → cẩn thận → nhanh.

### ① Lệnh cơ bản có readback — 60s

Câu nói: **"Vi-Vi ơi, nhiệt độ hai tư độ"**

Cần thấy trên màn hình: transcript hiện ra → intent `hvac_set_temp` → verdict `Allow` →
`(propertyId, areaId, value)` → **giá trị đọc lại từ property** → HMI đổi → TTS đọc "Đã…".

> "Đây là một lượt đầy đủ. Câu nói được nhận ở đây, hiểu thành lệnh có kiểu ở đây, đi qua tầng an toàn,
> xuống property của xe — và con số các bạn thấy là **giá trị đọc ngược lại từ xe**, không phải giá trị
> chúng em vừa gửi đi."

⚠️ Nếu **G-B fail**, bỏ vế readback, đổi câu thành: *"…xuống adapter của xe, và trạng thái trên HMI phản
chiếu đúng giá trị."* **Không được nói "readback" khi chưa có.**

### ② Nền tảng là điều kiện cần — 45s ★ ô +10

Vĩ dừng node `viva-asr` trong room CarSky.

Câu nói: **"Vi-Vi ơi, nhiệt độ hai hai độ"** → không có gì xảy ra, HMI báo lỗi ASR.

Vĩ bật lại node. Nói lại cùng câu → chạy bình thường.

> "Trong cấu hình demo này, app gọi viva-asr trên node CarSky. Rút node đó ra thì luồng đứt — các bạn
> vừa nhìn thấy. Nền tảng ở đây không phải chỗ chứa app; nó là một thành phần của luồng."

> 💡 Đây là câu trả lời trực tiếp cho nhận xét Vòng 2 *"bỏ CarSky vẫn giữ được flow"* (0/4), và là đòn
> chính vào ô **+10 "tận dụng tối đa nền tảng & starter pack"**. Diễn nó, đừng tranh luận nó.

⚠️ Chỉ diễn khi G-A′ đã có log chứng minh app gọi đúng endpoint của node CarSky và Settings đang chọn
`VIVA`; build hiện tại còn cho phép chuyển sang Google. Nếu chưa có log hai đầu, bỏ khoảnh khắc này.

### ③ SafetyGuard chặn lệnh nguy hiểm — 45s

Đặt tốc độ xe > 5 km/h. Câu nói: **"Vi-Vi ơi, mở cửa"**

Cần thấy: `Deny:G1_SPEED_LOCK`, cửa **không** đổi trạng thái, TTS nói rõ lý do.

> "Xe đang chạy. Lệnh mở cửa bị chặn trước khi chạm tới property, và tài xế được nghe lý do.
> Chúng em đã đo phản chứng cho việc này: bỏ tầng an toàn ra thì cả 6 lệnh nguy hiểm trong bộ thử
> đều ghi được xuống xe. Đó là ablation, có trong repo."

### ④ Suy diễn ngữ cảnh — 40s

Câu nói: **"Vi-Vi ơi, nóng quá"**

Cần thấy: hệ thống **không** đoán bừa mà hỏi lại → *"Bạn muốn giảm nhiệt độ điều hoà xuống bao nhiêu độ?"*
→ trả lời **"hai hai độ"** → thực hiện.

> "Người ta không nói chuyện với xe bằng lệnh. Người ta than. Một trợ lý thương mại có thể chọn tự hạ
> nhiệt độ; VIVA chọn hỏi lại vì đây là lệnh ghi xuống xe và câu nói còn thiếu giá trị đích. Làm sai đắt
> hơn hỏi thêm một câu. Mentor giúp chúng em phát hiện chính khoảng trống này, còn cách xử lý fail-safe
> là lựa chọn thiết kế của đội."

⚠️ Active runtime đã hỏi lại ở lượt đầu nhưng chưa có pending clarification cho câu trả lời “hai hai
độ”. Chỉ diễn đủ hai lượt khi test Y2/Y3 đã pass; nếu chưa, dừng demo sau câu hỏi và khai đúng giới hạn.

### ⑤ Phủ định — 30s ★

Câu nói: **"Vi-Vi ơi, tôi không muốn tăng nhiệt độ"**

Cần thấy: hệ thống **không** tăng nhiệt độ.

> "Câu này là một lỗi mentor tìm ra cho chúng em ngày 15 tháng 8: người dùng nói câu phủ định mà hệ
> thống vẫn thực hiện. Nó củng cố nguyên tắc thiết kế của đội: với lệnh ghi xuống xe, làm sai đắt hơn
> không làm gì. Chỉ khi negation gate chặn được câu này chúng em mới đưa nó vào demo."

⚠️ Tại baseline 20/08, negation gate N1–N4 chưa được implement. **Không diễn và không nói “đã vá”**
cho tới khi test đỏ→xanh và smoke test trên Device đều pass.

> 💡 Khoảnh khắc đáng giá nhất kịch bản: biến một lỗi đã bị ghi nhận thành bằng chứng đội phản hồi
> nhanh. Rẻ hơn và thuyết phục hơn bất kỳ tính năng mới nào.

### ⑥ Media và độ trễ — 40s

Câu nói: **"Vi-Vi ơi, phát nhạc lên"** rồi **"chuyển bài tiếp theo"**

Cần thấy: nhạc phát, bài đổi, và **số độ trễ hiện trên HMI**.

> "Độ trễ đầu-cuối đo được trên Device thật: trung vị 1336 mili-giây. Con số này là đo, không phải ước
> lượng — và p95 của chúng em là 1664, tức **chưa đạt** ngân sách 1500 mà chúng em tự đặt. Chúng em
> nói luôn con số chưa đạt."

> 💡 Chủ động công bố số chưa đạt là chỗ Vòng 2 ăn trần 5/5 ô minh bạch. Ở Vòng 3 nó nuôi ô
> *"thông tin chính xác và dẫn chứng cụ thể"* (5đ) trong Q&A.

---

## 5. Khác biệt và số đo — 6:00–7:10

Một slide, ba dòng:

| | Baseline gần nhất | VIVA |
|---|---|---|
| 6 lệnh nguy hiểm trong bộ thử | 6/6 ghi xuống xe | **0/6** |
| 3 lệnh hợp lệ đối chứng | 3/3 chạy | 3/3 chạy — không đổi |
| Kiểm chứng tự động | không có | **291 test JVM, 0 fail** |

> "Cái khác biệt không nằm ở số lượng câu lệnh. Nó nằm ở chỗ có một tầng quyết định lệnh nào được
> phép xuống xe, và tầng đó được cưỡng chế ở biên repository nên nó chặn cả lệnh chạm tay trên màn hình,
> không riêng lệnh giọng nói."

**Trade-off phải tự nêu:** khi không đọc được tốc độ, guard từ chối **mọi** lệnh mở cửa — kể cả lệnh
hợp lệ lúc xe đứng yên. An toàn được ưu tiên hơn tính sẵn sàng. Đó là lựa chọn có chủ đích.

---

## 6. Hiệu quả kinh tế và triển khai — 7:10–8:20

Số liệu và giả định lấy từ [`03-HIEU-QUA-KINH-TE.md`](03-HIEU-QUA-KINH-TE.md). Nói đúng ba ý:

1. **Đơn vị tiết kiệm** — mỗi thao tác chỉnh cabin bằng giọng nói thay cho chạm màn hình tiết kiệm bao
   nhiêu giây mắt rời đường, nhân với tần suất ca chạy.
2. **Ai trả tiền** — offering là module + integration kit theo mô hình B2B2C; OEM/Tier-1 là người mua,
   fleet operations là process owner.
3. **Đây là mô hình có giả định, không phải kết quả đã đo.** Nói câu này ra miệng.

> "Chúng em chưa chạy pilot nên đây là mô hình, không phải số đã đo. Giả định nằm hết trong tài liệu,
> và bước kiểm chứng tiếp theo là pilot đội xe với bốn chỉ số: tỉ lệ hoàn thành lệnh, độ trễ,
> số lần chạm, và tỉ lệ thực thi sai."

---

## 7. Giới hạn và roadmap — 8:20–9:10

> "Ba điều chúng em chưa làm được, nói trước khi các thầy cô hỏi.
>
> Một — chúng em chưa xây service framework riêng với AIDL; lớp vehicle hiện là module thư viện trong app.
> Hai — CCU điều hoà đang chạy là thành phần mô phỏng của nền tảng, không phải của đội.
> Ba — NLU của chúng em chứng minh trên một tập câu giới hạn, chưa phải NLU tiếng Việt tổng quát.
>
> Bù lại, ranh giới để đi tiếp đã rõ: intent dừng ở biên app, VHAL chỉ nhận `(propertyId, areaId, value)`.
> Thêm một chức năng là thêm một luật và một dòng ánh xạ, không phải sửa VHAL."

---

## 8. Chốt — 9:10–9:40

> "VIVA là trợ lý giọng nói tiếng Việt cho buồng lái, chạy trên nền tảng CarSky, với một tầng an toàn
> mà chúng em chứng minh được bằng phản chứng chứ không bằng lời.
>
> Chúng em xin cảm ơn Ban Giám khảo và các anh chị mentor."

---

## 9. Câu thoát khi demo trượt

Học thuộc. Nói bình thản, **không xin lỗi dài, không đổ tại thiết bị**.

| Tình huống | Câu nói | Hành động |
|---|---|---|
| ASR nghe trượt lần 1 | *"Câu đó chưa vào. Em nói lại."* | Nói lại **đúng câu đã tập**, không đổi cách nói |
| ASR trượt lần 2 cùng câu | *"Em chuyển sang bản ghi để giữ đúng thời gian, phần này chạy thật trên Device hôm hai mươi mốt."* | **Chuyển video ngay.** Không thử lần 3 |
| Mạng/node chết ngoài ý muốn | *"Đúng như phần vừa rồi — không có nền tảng thì luồng đứt. Em dùng bản ghi."* | Biến sự cố thành minh hoạ cho khoảnh khắc ② |
| TTS im | *"Máy chưa đọc được câu này, nhưng lệnh đã chạy — các bạn thấy trạng thái trên màn hình."* | Đi tiếp, chỉ tay vào HMI |
| Quá 6:00 mà chưa xong demo | (không nói gì) | **Cắt khoảnh khắc còn lại**, sang mục 5 |

**Nguyên tắc: không bao giờ thử lại lần thứ ba.** Đồng hồ đang chiếu công khai, và ô "đúng thời gian"
đáng 2 điểm.

---

## 10. Chuẩn bị trước slot 30 phút

- [ ] Cắm thử máy chiếu, kiểm tra tỉ lệ màn hình
- [ ] Thử âm thanh — TTS phải nghe rõ trong hội trường
- [ ] **Mở sẵn video dự phòng ở tab riêng, đã tua đến 0:00**
- [ ] Device đã kết nối, deployment `RUNNING`, node `viva-asr` sống
- [ ] Đặt tốc độ xe > 5 km/h sẵn cho khoảnh khắc ③
- [ ] Tắt thông báo trên máy trình chiếu
- [ ] Slide ở chế độ trình chiếu, không phải chế độ soạn
- [ ] Long uống nước, thử micro
