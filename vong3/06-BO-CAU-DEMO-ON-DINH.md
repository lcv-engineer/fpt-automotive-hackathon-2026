# BỘ CÂU DEMO — CHỈ DÙNG CÂU ĐÃ CHỨNG MINH

> **Chủ trì:** Dương · **Nguồn:** `evidence/c2/carsky-voice-e2e-20260810/README.md` — 25 lượt thật
> trên Device CarSky
>
> **Luật của file này:** trên sân khấu **chỉ nói câu nằm trong bảng XANH**. Không ứng biến. Không đổi
> cách nói. Không thêm chữ.

---

## 0. Vì sao cần file này

Phiên 10/08 có **10/25 lượt không nhận ra** — 40%. Nhưng đọc kỹ log thì nguyên nhân không phải đường
ống hỏng:

> *"Cách nói đã chứng minh chạy được thì lặp lại **ổn định**; các biến thể chưa nằm trong grammar hoặc
> bộ câu mẫu thì trượt."*

Nghĩa là tỉ lệ 40% là **tỉ lệ của một phiên thử tay ngẫu hứng**, không phải tỉ lệ của một demo có tập
dượt. Chọn đúng câu thì con số đó gần như biến mất.

Đây là chiến thuật hợp lệ, không phải giấu giếm: BTC khuyến nghị nguyên văn *"Dùng corpus hiện có để
trình bày intent/slot/OOD/false-execution; thêm vài biến thể khó có chủ đích thay vì cố xây NLU tổng quát."*

---

## 1. 🟢 BẢNG XANH — đã chạy thật trên Device 10/08

Từ gọi: **"Vi-Vi ơi"** (cũng nhận *Vivi ơi*, *Viva ơi*).

| # | Câu nói | Intent | Dùng ở khoảnh khắc |
|---|---|---|---|
| X1 | Vi-Vi ơi, **nhiệt độ lên hai tư độ** | `hvac_set_temp` | ① lệnh cơ bản |
| X2 | Vi-Vi ơi, **nhiệt độ hai hai độ** | `hvac_set_temp` | ② nền tảng · ④ suy diễn |
| X3 | Vi-Vi ơi, **tăng quạt lên mức năm** | `hvac_set_fan` | dự phòng |
| X4 | Vi-Vi ơi, **giảm quạt xuống mức một** | `hvac_set_fan` | dự phòng |
| X5 | Vi-Vi ơi, **mở cửa** | `door_lock` → `Deny:G1_SPEED_LOCK` | ③ an toàn |
| X6 | Vi-Vi ơi, **hãy mở cửa** | `door_lock` → `Deny` | ③ dự phòng |
| X7 | Vi-Vi ơi, **phát nhạc lên** | `media_play` | ⑥ media |
| X8 | Vi-Vi ơi, **phát nhạc đi** | `media_play` | ⑥ dự phòng |
| X9 | Vi-Vi ơi, **chuyển bài tiếp theo** | `media_next` | ⑥ media |
| X10 | Vi-Vi ơi, **cho tôi biết tốc độ hiện tại** | `vehicle_status_speed` | dự phòng |
| X11 | Vi-Vi ơi, **cho tôi biết nhiên liệu hiện tại** | `vehicle_status_fuel` | dự phòng |

**Sáu nhóm chức năng này đều đã đi trọn tuyến tới `Allow` trên Device thật.**

---

## 2. 🔴 BẢNG ĐỎ — CẤM nói trên sân khấu

Đây là những câu đã trượt thật, có ghi trong log 10/08. Nói chúng là tự chuốc lấy rủi ro.

| Câu cấm | ASR nghe thành | Dùng thay bằng |
|---|---|---|
| ~~chuyển bài~~ (cụt) | **chuyển bay** | X9 — *chuyển bài **tiếp theo*** |
| ~~tốc độ bao nhiêu~~ (mở đầu bằng "tốc độ") | **tóc độ** | X10 — *cho tôi biết **tốc độ hiện tại*** |
| ~~còn bao nhiêu xăng~~ | **xen** / **săn** | X11 — *cho tôi biết **nhiên liệu hiện tại*** |
| ~~mức xăng~~ | **mức binh** / **mức tinh** | X11 |

**Quy luật rút ra:** câu **dài hơn và có ngữ cảnh** thì nhận đúng hơn câu ngắn cụt. Từ đứng đầu câu dễ
bị nghe nhầm nhất. Đừng rút gọn cho "tự nhiên" — trên sân khấu, đúng quan trọng hơn tự nhiên.

---

## 3. 🟡 BẢNG VÀNG — câu mới, phải kiểm trên Device trước khi lên bảng xanh

Đây là hai khoảnh khắc mới của kịch bản. Chúng **chưa từng chạy trên Device**.

| # | Câu | Kỳ vọng | Khoảnh khắc | Trạng thái |
|---|---|---|---|---|
| Y1 | Vi-Vi ơi, **nóng quá** | Hỏi lại: *"Bạn muốn giảm nhiệt độ điều hoà xuống bao nhiêu độ?"* | ④ | Có mã ở 3 tầng, **chưa test trên Device** |
| Y2 | **hai hai độ** (trả lời Y1) | Thực hiện đặt 22 °C | ④ | chưa test |
| Y3 | Vi-Vi ơi, **tôi không muốn tăng nhiệt độ** | **Không** tăng nhiệt độ | ⑤ | Đang vá — xem §5 |

> **Luật cứng: câu nào chưa chạy được trên Device thì không lên sân khấu.**
> Hạn kiểm: **20/08**. Đến 21/08 mà Y1–Y3 chưa xanh thì **cắt khoảnh khắc ④ và ⑤ khỏi kịch bản**, và
> chỉ nói bằng miệng ở phần "khác biệt" mà không diễn.

Y1 có cơ sở tốt: `nóng quá` / `lạnh quá` đã có ở cả ba tầng —
`GrammarIntentRouter.kt:43,48`, `CommandMappingRepository.kt:59,66`, `IntentExemplarCatalog.kt:33,40`.
Việc còn lại chỉ là xác nhận ASR nghe đúng hai chữ đó trên Device.

---

## 4. Cách nói trên sân khấu

Người nói: **Long**. Tập ít nhất 20 lần mỗi câu.

- **Ngắt rõ sau từ gọi.** "Vi-Vi ơi" → nghỉ nửa nhịp → câu lệnh. VAD cần khoảng lặng để cắt câu đúng.
- **Nói đều, không lên giọng cuối câu.** Đây là lệnh, không phải câu hỏi.
- **Không nói nhanh khi hồi hộp.** Đây là lỗi phổ biến nhất trên sân khấu và nó phá VAD.
- **Micro cách miệng cố định.** Đổi khoảng cách giữa chừng làm đổi mức tín hiệu.
- **Nói xong thì im và đợi.** Đừng nói chèn lên lúc hệ thống đang xử lý — 1,3 giây là dài trên sân khấu
  nhưng phải để nó trôi qua.

> 💡 1336 ms trung vị nghĩa là **có một khoảng lặng thật sự** sau mỗi câu. Tập chịu khoảng lặng đó.
> Lấp nó bằng một câu dẫn đã soạn: *"Trong lúc nó xử lý — con số các bạn sắp thấy là độ trễ đo thật."*

---

## 5. Việc của Dương — chốt phủ định

**Vấn đề:** toàn bộ matching là `contains()` không có chốt phủ định.
`GrammarIntentRouter.kt:48` — `command.contains("nong qua")`; dòng 71 — `contains("tang am luong")`.
Nên *"tôi **không** muốn tăng nhiệt độ"* chứa `tang nhiet do` → fire `TEMPERATURE_UP`.

Mentor báo lỗi này ngày 15/08.

### ⚠️ Bẫy bắt buộc tránh

**Tiếng Việt dùng "không" làm cả số 0.** Corpus có `evidence/asr/corpus-human/*/cmd_fan_0.wav` =
*"quạt mức **không**"* nghĩa là quạt mức 0.

Vá bằng `contains("khong")` sẽ **giết luôn lệnh quạt mức 0**.

### Yêu cầu của chốt

1. **Theo token**, không theo chuỗi con.
2. **Nhận biết "không" ở vị trí slot số là số 0**, không phải phủ định. Dấu hiệu: đứng sau `mức`, `độ`,
   hoặc đứng ở vị trí giá trị.
3. Đặt **trước** cả ba tầng (grammar, keyword, embedding) để không tầng nào bypass được.
4. Hành vi khi phát hiện phủ định: **hỏi lại**, không im lặng từ chối. Vừa an toàn hơn vừa diễn tốt hơn.

### Test bắt buộc

| Ca | Đầu vào | Kỳ vọng |
|---|---|---|
| N1 | tôi không muốn tăng nhiệt độ | không thực hiện |
| N2 | tôi không muốn giảm nhiệt độ | không thực hiện |
| N3 | đừng bật đèn | không thực hiện |
| N4 | khỏi cần chuyển bài tiếp theo | không thực hiện |
| **N5 hồi quy** | **quạt mức không** | ✅ **VẪN đặt quạt mức 0** |
| **N6 hồi quy** | **nhiệt độ hai tư độ** | ✅ **VẪN đặt 24 °C** |
| **N7 hồi quy** | **nóng quá** | ✅ **VẪN hỏi lại** |

N5–N7 là ca hồi quy. **Chúng quan trọng hơn N1–N4** — vá phủ định mà làm hỏng lệnh đang chạy là đổi
một điểm lấy năm điểm.

---

## 6. Việc của Dương — TTS không bao giờ im

**Vấn đề đã ghi trong log 10/08:**
```
W VIVA_VOICE: TTS failed for "Đã gửi lệnh phát nhạc tới trình phát.":
              No Vietnamese TTS voice or pre-rendered prompt for: ...
```

Lệnh chạy đúng nhưng máy im. **Trên sân khấu, im lặng bị đọc là hỏng.**

**Yêu cầu:** chuỗi dự phòng sao cho không bao giờ có kết cục im lặng —
giọng tiếng Việt → câu đã render sẵn → câu ngắn tương đương đã render sẵn → âm báo ngắn.

Kiểm bằng cách chạy **mọi câu trả lời của 11 câu bảng xanh** và xác nhận có tiếng ra ở cả 11.

---

## 7. Checklist trước G-C (21/08 20:00)

- [ ] 11 câu bảng xanh chạy lại trên Device, ghi kết quả
- [ ] Y1, Y2, Y3 đã kiểm trên Device → lên xanh hoặc bị cắt khỏi kịch bản
- [ ] N1–N7 xanh trong unit test
- [ ] 11 câu trả lời đều có tiếng TTS
- [ ] Long đã tập đủ 20 lượt mỗi câu
- [ ] Bảng đỏ đã được cả đội đọc — không ai lỡ mồm nói câu cấm
