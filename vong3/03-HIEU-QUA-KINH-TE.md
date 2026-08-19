# HIỆU QUẢ KINH TẾ — MÔ HÌNH CÓ GIẢ ĐỊNH

> **Ô điểm:** `+10 — Giải pháp cần chứng minh tiềm năng cải thiện hiệu suất, giảm chi phí hoặc tăng
> doanh thu, bao gồm việc tối ưu hóa quy trình` (khối Sáng tạo/khả thi/hiệu quả 35đ)
>
> **Trạng thái Vòng 2:** ô này đang **bỏ trắng**. BTC cũng trừ 1đ ô *Outcome/adoption* với lý do
> *"H1–H4 chưa được đo trên speaker/noise/fleet thật"*.

---

## 0. Quy tắc của tài liệu này

**Đây là mô hình có giả định, không phải kết quả đã đo.** Câu này phải được nói ra miệng trên sân khấu
và phải in trên slide.

Lý do không phải khiêm tốn mà là chiến thuật: Vòng 2 đội ăn trần 5/5 ô minh bạch giới hạn, và Vòng 3
có 5đ cho *"cung cấp thông tin chính xác và đưa ra dẫn chứng cụ thể"*. Một mô hình khai đúng là mô hình
ăn điểm. Một con số bịa bị hỏi hai câu là sụp.

Ba loại đầu vào, phân biệt rõ:

| Nhãn | Nghĩa |
|---|---|
| 🟢 **ĐO ĐƯỢC** | Đội đã đo, có file bằng chứng trong repo |
| 🟡 **ĐO ĐƯỢC TRONG 2H** | Đội chưa đo nhưng đo được trước 21/08 bằng chính app của mình |
| 🔴 **NGUỒN NGOÀI** | Số từ nghiên cứu công bố — **bắt buộc dẫn nguồn cụ thể trước khi lên slide** |

---

## 1. Đơn vị phân tích

**Một tài xế giao vận, một ca 8 tiếng.** Chọn đơn vị này vì:

- Đây là user đội đã khai từ Vòng 2 và BTC chấm trần 2/2 ô *User/decision point*.
- Tài xế giao vận có tần suất thao tác cabin cao và có người trả tiền rõ ràng (fleet operations).
- Nó quy đổi lên đội xe được bằng phép nhân đơn giản, không cần giả định thêm.

---

## 2. Đầu vào

| # | Đại lượng | Nhãn | Giá trị | Nguồn |
|---|---|---|---|---|
| I1 | Độ trễ đầu-cuối lệnh giọng nói, p50 | 🟢 | **1336 ms** | `evidence/c2/carsky-voice-e2e-20260810/README.md` |
| I2 | Độ trễ p95 | 🟢 | **1664 ms** — chưa đạt ngân sách 1500 | như trên |
| I3 | Số thao tác chạm để đặt nhiệt độ trên HMI | 🟡 | **cần đếm** | Đếm trên chính app VIVA, quay màn hình |
| I4 | Số thao tác chạm để chuyển bài | 🟡 | **cần đếm** | như trên |
| I5 | Thời gian hoàn tất một thao tác chạm | 🟡 | **cần đo** | Bấm giờ 10 lần, lấy trung vị |
| I6 | Số lần thao tác cabin mỗi ca 8h | 🔴 | **cần dẫn nguồn** | Nghiên cứu hành vi tài xế, hoặc khảo sát nhỏ của đội |
| I7 | Ngưỡng thời gian mắt rời đường làm tăng rủi ro | 🔴 | **cần dẫn nguồn** | Nghiên cứu an toàn giao thông đã công bố |

> ⚠️ **I6 và I7 chưa được điền.** Trước 20/08 phải chọn một trong hai đường:
> **(a)** tìm nguồn công bố được và dẫn đúng tên nghiên cứu + năm; hoặc
> **(b)** bỏ hẳn hai biến này và chỉ trình bày phần dựng được từ I1–I5.
>
> **Tuyệt đối không điền số ước lượng rồi trình bày như số có nguồn.** Đó chính là lỗi 65% / 28,75%
> mà BTC đã ghi "chưa đủ thẩm quyền" ở Vòng 2 — lặp lại nó là mất điểm hai lần.

### Việc phải làm để điền I3–I5 (Dương, ~2h, hạn 20/08)

1. Quay màn hình app VIVA, thao tác đặt nhiệt độ 24 °C bằng tay từ màn hình chính. Đếm số chạm.
2. Lặp lại 10 lần, bấm giờ từ lúc chạm đầu tới lúc giá trị đổi. Lấy trung vị.
3. Làm tương tự với "chuyển bài".
4. Ghi ra `evidence/ux/touch-count-baseline.csv` kèm commit và ngày.

Đây là **số của chính đội, đo trên chính sản phẩm của đội** — không cần nguồn ngoài, không ai bác được.

---

## 3. Công thức

Cho một thao tác cabin:

```
Tiết kiệm thời gian mỗi thao tác  =  (I5 × I3)  −  I1
Tiết kiệm mỗi ca                  =  Tiết kiệm mỗi thao tác  ×  I6
Tiết kiệm mỗi đội xe N xe, năm    =  Tiết kiệm mỗi ca × số ca/năm × N
```

Cho phần an toàn — chỉ trình bày nếu điền được I7:

```
Thời gian mắt rời đường tránh được  =  (I5 × I3) × I6      (giọng nói ≈ mắt không rời đường)
```

> 💡 **Điểm mạnh của cấu trúc này:** phần lõi (tiết kiệm thời gian thao tác) dựng hoàn toàn từ số đội
> tự đo. Phần an toàn là phần thêm, và nếu không dẫn được nguồn thì cắt đi mà mô hình vẫn đứng.

---

## 4. Ba kịch bản

Trình bày cả ba, không trình bày một. Trình bày một con số duy nhất là dấu hiệu chưa hiểu độ bất định.

| Kịch bản | Giả định | Dùng để |
|---|---|---|
| **Thận trọng** | I6 ở cận dưới; chỉ tính thao tác HVAC | Con số đội dám bảo vệ trước mọi câu hỏi |
| **Cơ sở** | I6 ở giá trị trung vị; tính cả HVAC và media | Con số đưa lên slide |
| **Lạc quan** | I6 cận trên; tính thêm phần giảm thao tác sai | Chỉ nói khi được hỏi "trần là bao nhiêu" |

---

## 5. Ai trả tiền, và vì sao họ trả

Ba vai, đã được BTC chấm trần 2/2 ở Vòng 2 — giữ nguyên cách khai.

| Vai | Là ai | Họ được gì |
|---|---|---|
| **User** | Tài xế, ưu tiên tài xế giao vận | Thao tác cabin không rời tay khỏi vô-lăng |
| **Buyer** | OEM / Tier-1 | Một module tiếng Việt cắm được vào cockpit, có tầng chính sách sẵn — rẻ hơn tự xây |
| **Process owner** | Fleet operations / an toàn đội xe | Giảm thao tác gây phân tâm; có log quyết định để truy vết sự cố |

**Offering:** module + integration kit AAOS, mô hình **B2B2C**. Không phải app bán thẳng cho tài xế.

**Vì sao mô hình này chứ không phải B2C:** các permission `CONTROL_CAR_*` là privileged. Ứng dụng phải
được OEM ký và cấp allowlist mới ghi được property. Đây không phải lựa chọn kinh doanh mà là **ràng buộc
kỹ thuật quyết định mô hình kinh doanh** — nói ý này ra, nó cho thấy đội hiểu hệ sinh thái.

---

## 6. Tối ưu hóa quy trình

Barem ghi rõ *"bao gồm việc tối ưu hóa quy trình"*. Đây là phần dễ bị bỏ quên và nó nằm ở chỗ khác:

**Quy trình tích hợp của OEM.** Thêm một chức năng vào trợ lý hiện tại tốn bao nhiêu?

> Với VIVA: thêm một luật ngữ pháp, một dòng ánh xạ `intent → (propertyId, areaId, value)`, và một test.
> **Không sửa VHAL, không build lại image, không cho AI sinh ra PropertyID.**

Đây là claim `C-MODULAR` đội đã có sẵn từ Vòng 2 và có test chứng minh. Nó là lập luận "giảm chi phí"
mạnh hơn phần tiết kiệm thời gian tài xế, vì nó nói vào túi tiền của **người mua**, không phải người dùng.

**Locator:** `vong2/18-CLAIM-EVIDENCE-MAP.md` claim `C-MODULAR` · `CoreIntentMapperTest`

### 6.1. Chi phí biên của lệnh lõi và lý do tách Voice–Brain–Body

Nguồn chính thức cho thấy Trợ lý ảo VinFast 3.0 có GenAI nằm trong các gói VF Connect nâng cao trả phí,
trong khi gói Basic giữ các tính năng thiết yếu. Điều đó chứng minh **thị trường chấp nhận phân tầng
capability**, nhưng nguồn không nói chi phí GPU là nguyên nhân duy nhất của việc định giá; không được
suy diễn quan hệ nhân quả đó trên slide.

Lập luận VIVA có thể bảo vệ:

> "VIVA giữ định tuyến và thực thi lệnh xe lõi ở local, nên mỗi lệnh lõi không cần một lượt suy luận LLM
> trả phí. Offering mục tiêu cho phép OEM đóng gói phần lõi trong cấu hình cơ bản; hội thoại GenAI là capability mở rộng của
> Brain, không phải điều kiện để HVAC, media hay SafetyGuard hoạt động."

Đây **không phải** claim “chi phí mỗi câu bằng 0”: bản hiện tại vẫn dùng ASR HTTP/Google tùy Settings,
và mọi xử lý đều có chi phí compute/tích hợp. Claim hẹp, đúng là **không phát sinh LLM inference cho
quyết định lệnh lõi**.

**Nguồn:** [VinFast công bố VF Connect và Trợ lý ảo 3.0](https://vinfastauto.com/vn_vi/vinfast-ra-mat-goi-dich-vu-thong-minh-vf-connect-nang-tam-trai-nghiem-ca-nhan-hoa-tren-xe-dien) ·
[ViVi 2.0 — VinBigdata](https://vivi.vinbigdata.com/)

---

## 7. Những gì tài liệu này KHÔNG claim

Đọc to phần này khi trình bày. Nó là thứ ăn điểm ở Q&A.

- ❌ Không claim đã đo trên tài xế thật, cabin thật, hay đội xe thật.
- ❌ Không claim TAM, pricing, hay có khách hàng cam kết. Thể lệ nói rõ không bắt buộc.
- ❌ Không claim tỉ lệ giảm tai nạn. Đội không có dữ liệu đó và không ai nên claim nó từ một prototype.
- ❌ Không dùng lại con số 65% / 28,75%.

**Bước kiểm chứng tiếp theo:** pilot với một đội xe vận tải, đo bốn chỉ số — tỉ lệ hoàn thành lệnh,
độ trễ, số lần chạm, tỉ lệ thực thi sai. **Rào cản lớn nhất là quyền privileged**, cần OEM ký ứng dụng.

---

## 8. Việc còn lại

| Việc | Ai | Hạn |
|---|---|---|
| Đo I3, I4, I5 → `evidence/ux/touch-count-baseline.csv` | Dương | 20/08 |
| Quyết I6, I7: dẫn được nguồn hay cắt | Long | 20/08 |
| Tính ba kịch bản, lên 1 slide | Long | 21/08 |
| Rà lại: mọi con số trên slide đều có nhãn 🟢/🟡/🔴 | Long | 21/08 |
