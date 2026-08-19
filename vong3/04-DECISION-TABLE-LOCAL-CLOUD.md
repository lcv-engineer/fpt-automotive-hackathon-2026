# BẢNG QUYẾT ĐỊNH — LOCAL RULE ↔ CLOUD FALLBACK

> **Phạm vi thuật ngữ:** trong bảng này, “local” nghĩa là **định tuyến intent, policy và thực thi sau
> khi đã có transcript**. ASR active của bản build hiện tại là viva-asr HTTP hoặc Google theo Settings;
> vì vậy không được suy rộng thành “toàn bộ voice pipeline offline”. R1–R3 mô tả đường đang chạy;
> R4–R7 là thiết kế mục tiêu, chưa có cloud conversation fallback trong runtime.

> **Vì sao có tài liệu này:** BTC khuyến nghị bằng văn bản trong `Phan_hoi_Vong2_viva.docx`:
> *"Chuẩn bị decision table local rule vs cloud fallback theo confidence, capability, latency, privacy
> và network availability."*
>
> Đây là tài liệu thuần — không cần viết code — nhưng ăn vào ô `+10 khả thi cao, dễ triển khai` và là
> câu trả lời sẵn cho Q&A mục A5.

---

## 0. Trạng thái triển khai — đọc trước

Bảng này là **chính sách thiết kế**. Không phải mọi dòng đã chạy trong bản demo. Phân biệt rõ:

| Nhãn | Nghĩa |
|---|---|
| 🟢 **ĐANG CHẠY** | Có trong mã và chạy trên đường APK hiện tại |
| 🟡 **CÓ MÃ, CHƯA TỰ ĐỘNG** | Cơ chế tồn tại nhưng còn do người/cấu hình chọn, chưa tự định tuyến |
| 🔵 **THIẾT KẾ** | Chính sách đã chốt, chưa triển khai |

Nói đúng nhãn khi bị hỏi. **Không mô tả 🔵 như thể đang chạy.**

---

## 1. Nguyên tắc gốc

> **Lệnh điều khiển xe không rời khỏi xe.**

Đây là ràng buộc cứng, không phải tối ưu hoá. Mọi dòng dưới đây là hệ quả của nó.

Ba lý do, theo thứ tự quan trọng:

1. **An toàn** — lệnh xe phụ thuộc mạng là lệnh có thể treo giữa chừng. Fail-closed cần quyết định cục bộ.
2. **Độ trễ** — ngân sách p95 của đội là 1500 ms cho toàn chuỗi; một vòng ra cloud ăn hết phần lớn.
3. **Riêng tư** — audio cabin chứa hội thoại của người trong xe, không chỉ lệnh.

---

## 2. Bảng quyết định theo 5 trục

| # | Trục | Điều kiện | Quyết định | Trạng thái |
|---|---|---|---|---|
| R1 | **Capability** | Câu khớp một trong các luật lệnh xe (`hvac_*`, `door_lock`, `cabin_lights`, `volume_*`, `media_*`) | **LOCAL, luôn luôn.** Kể cả khi mạng tốt | 🟢 |
| R2 | **Confidence** | Điểm tin cậy âm học `< 0.6` | **Hỏi lại người dùng.** Không thực thi, không gọi cloud | 🟢 |
| R3 | **Confidence** | `0.6 ≤ điểm < ngưỡng ngữ nghĩa`, câu vẫn khớp luật | Thực thi local, nhưng **hỏi xác nhận** nếu lệnh thuộc nhóm nguy hiểm | 🟢 |
| R4 | **Capability** | Câu **không** khớp luật nào, và không thuộc nhóm lệnh xe | Ứng viên cho cloud — xét tiếp R5, R6 | 🔵 |
| R5 | **Network** | Không có mạng, hoặc RTT tới endpoint `> 800 ms` | **Không gọi cloud.** Trả lời "chưa hỗ trợ" và gợi ý tập lệnh lõi | 🔵 |
| R6 | **Privacy** | Đoạn audio chưa được người dùng bật cờ cho phép gửi ra ngoài | **Không gửi.** Mặc định là không | 🔵 |
| R7 | **Latency** | Cloud không trả lời trong 2000 ms | Huỷ, suy biến về câu trả lời local | 🔵 |
| R8 | **Chọn engine ASR** | Người dùng chọn `VIVA` (container trong room) hay `GOOGLE` trong Settings | Định tuyến theo lựa chọn | 🟡 — `RoutingAsrClient` chuyển theo Setting, chưa tự chọn theo mạng/tải |

**Locator:** `VoiceTurnReport.kt` (R2, R3 — `MIN_ACOUSTIC_CONFIDENCE = 0.6f`, ghi đè runtime qua
`Settings.Global/viva_min_conf`) · `RoutingAsrClient.kt` (R8) · `GrammarIntentRouter.kt` (R1, R4)

---

## 3. Hai ví dụ nói trên sân khấu

Đừng đọc bảng. Nói hai ví dụ này.

**Ví dụ 1 — lệnh xe, có mạng tốt:**
> *"Nhiệt độ hai tư độ"* → khớp luật `hvac_set_temp` → **chạy local**, không hỏi cloud, dù mạng đang tốt.
> Vì đây là lệnh điều khiển xe.

**Ví dụ 2 — câu ngoài phạm vi, mất mạng:**
> *"Kể cho tôi nghe một câu chuyện"* → không khớp luật nào → ứng viên cloud → nhưng mất mạng →
> hệ thống **nói ra là chưa hỗ trợ** và gợi ý tập lệnh lõi. Nó không im lặng, và nó không đoán bừa.

---

## 4. Suy biến khi mất mạng

Điều quan trọng nhất về kiến trúc này: **mất mạng không làm chết sản phẩm, chỉ làm hẹp nó lại.**

| Thành phần | Mất mạng ngoài xe | Mất node ASR trong room |
|---|---|---|
| Luật ngữ pháp 10 intent lõi | ✅ chạy | ✅ chạy (không cần ASR để chạy luật) |
| Nhận dạng giọng nói | ✅ chạy — container ở trong room, không ra internet | ❌ **đứt** |
| SafetyGuard | ✅ chạy | ✅ chạy |
| Câu hội thoại tự do | ❌ từ chối có thông báo | ❌ từ chối có thông báo |

> ⚠️ **Cột thứ hai là chỗ phải nói chính xác.** Container `viva-asr` chạy **trong room CarSky**, không
> phải trên internet. Nên "mất mạng internet" và "mất node ASR" là hai sự cố khác nhau. Bản demo hôm nay
> build với remote ASR là đường duy nhất — đó là lý do khoảnh khắc ② trong kịch bản diễn được.

---

## 5. Vì sao không đẩy hết lên cloud

Câu hỏi này gần như chắc chắn bị hỏi. Trả lời ngắn:

> "Một trợ lý cloud nói hay hơn của chúng em. Nhưng nó không nhận lệnh xe, không có tầng chính sách
> quyết định lệnh nào được xuống property, và nó cần mạng để mở cửa xe cho bạn. Ba điều đó là lý do
> phần lõi phải ở local. Cloud là phần mở rộng, không phải phần thay thế."

---

## 6. Việc còn lại

| Việc | Ai | Hạn | Ghi chú |
|---|---|---|---|
| Rà lại nhãn 🟢/🟡/🔵 cho đúng mã hiện tại | Long + Dương | 20/08 | Sai nhãn ở đây là mất điểm ở Q&A |
| Đưa bảng lên 1 slide phụ (chỉ mở khi BGK hỏi) | Long | 21/08 | Không đưa vào 10 phút chính |
