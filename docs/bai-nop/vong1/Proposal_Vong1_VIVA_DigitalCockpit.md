# PROPOSAL VÒNG 1 · FPT AUTOMOTIVE HACKATHON 2026
**Vertical: Digital Cockpit · Core: Voice-Controlled Assistant (tích hợp Media Player + Climate/VHAL + DTC)**

> Tài liệu soạn theo đúng cấu trúc Template PPTX của BTC: *Thông tin đội chơi → Bài tập lựa chọn → Vấn đề & Cách giải quyết → Lộ trình Vòng 2*. Mỗi mục = 1 slide, copy nội dung vào template là dùng được. Chỗ cần điền đánh dấu `[...]`. Deadline nộp: **23:59 ngày 12/07/2026**.

---

## ⚡ Tóm tắt 30 giây (để cả đội align trước khi làm slide)

- **Sản phẩm: VIVA — Vietnamese In-Vehicle Assistant.** Wake word: *"Vivi ơi"*.
- **Một câu:** Trợ lý giọng nói **tiếng Việt** điều khiển **thật** toàn bộ cockpit — nhạc, điều hòa, cửa, chẩn đoán xe, quy trình giao hàng — phản hồi **dưới 1.5 giây**, chạy được **offline**, không cần chạm màn hình.
- **Insight ăn điểm:** 4 đề Digital Cockpit được ra như 4 app riêng, nhưng người lái không "mở app" — họ **nói**. Voice là lớp điều phối tự nhiên của cả cockpit → làm Voice là core, biến 3 đề còn lại thành skill **thật** (không mock) bằng chính starter pack của BTC.
- Tên đội gợi ý: đặt trùng tên sản phẩm — **Team VIVA** (nhất quán khi pitch). Phương án khác: *CabinTalk*, *Rảnh Tay*.

---

## SLIDE 3 — THÔNG TIN ĐỘI CHƠI

**Tên đội:** `[Team VIVA]`

| # | Thành viên | Vai trò | Mô tả (2 dòng, theo mẫu của BTC) |
|---|-----------|---------|----------------------------------|
| A | **Ngô Văn Long (Logan)** | Team Lead · Voice AI | Từng xây end-to-end VAD/ASR pipeline production xử lý ~47.000 file đa ngôn ngữ (Demucs, Silero VAD, Whisper large-v3 / PhoWhisper, ONNX, Docker, Triton), WER <5%. Kinh nghiệm Edge AI, quantize INT8/FP16 (TensorRT/ONNX). Lo voice pipeline, intent router và kiến trúc tổng. |
| B | `[Tên]` | Android / AAOS | *Profile cần:* biết Android, tốt nhất từng đụng AAOS/emulator. Lo HMI cockpit, MediaBrowserService/MediaSession, APK trên AVD. |
| C | `[Tên]` | System / VHAL | *Profile cần:* quen Java/Kotlin hoặc C++, chịu khó đọc doc platform. Lo CarPropertyManager ↔ VHAL (HVAC, DOOR), DTC/UDS simulator. |
| D | `[Tên]` | Backend / Agent | *Profile cần:* Python/Node, quen LLM function-calling. Lo Command Dispatcher, delivery simulator, cloud LLM fallback, benchmark harness. |
| E | `[Tên]` | Product & Demo | *Profile cần:* UX + kể chuyện. Lo kịch bản demo, video 5–7', write-up, pitch, giao diện ngày/đêm. |

> 💡 Barem "Năng lực đội" (15đ) chấm **sự khớp giữa skill và hướng đề xuất** — mô tả của mỗi người nên nhắc đúng công nghệ ở phần kiến trúc (VAD/ASR, AAOS, VHAL...). Nếu thành viên nào chưa có kinh nghiệm khớp, ghi kinh nghiệm gần nhất + phần việc cụ thể được giao.

## SLIDE 4 — HÌNH ẢNH ĐỘI CHƠI

- 1 ảnh nhóm thật + 1 avatar nhóm (theo template). Gợi ý avatar: icon sóng âm 🎙️ lồng trong vô-lăng, chữ VIVA.

---

## SLIDE 5 — BÀI TẬP LỰA CHỌN

**Core: Challenge #3 — Voice-Controlled Assistant (★★★ · Simulation-based)**

Tích hợp 3 challenge còn lại của Digital Cockpit làm "skill" cho trợ lý:

| Challenge | Vai trò trong sản phẩm | Mức độ triển khai |
|-----------|------------------------|-------------------|
| #3 Voice-Controlled Assistant | **Core** — pipeline giọng nói, intent, dispatcher, delivery flow | Đầy đủ theo Output Requests của đề |
| #1 Media Player | Skill: "phát nhạc / chuyển bài / playlist" điều khiển media **thật** | MediaSession + nguồn Virtual USB (starter pack) |
| #2 Climate Control / VHAL | Skill: "hạ 22 độ / quạt mức 2 / bật A/C" set property **thật** qua VHAL | 4–5 property chuẩn, đồng bộ UI ↔ xe real-time |
| #4 DTC Monitor | Skill: "xe có lỗi gì không?" — đọc & tóm tắt mã lỗi từ simulator | Query + phân loại + đọc kết quả bằng giọng nói |

**Lý do chọn cụm này:** bản thân đề Voice đã yêu cầu điều khiển *"volume, media control, climate settings, door lock/unlock"* — đa số đội sẽ **mock** các hành động đó bằng UI giả. Chúng tôi dùng đúng tài nguyên starter pack của 3 đề còn lại để làm **thật**, nên một demo end-to-end chạm cả 4 bài toán của vertical.

---

## SLIDE 6 — VẤN ĐỀ CẦN GIẢI QUYẾT

**Headline:** *Trong buồng lái, mỗi cú chạm là 2–3 giây rời mắt khỏi đường*

**Số to (ô bên trái, theo layout mẫu):**
> **×2** — rời mắt khỏi đường quá 2 giây làm nguy cơ va chạm tăng ~gấp đôi
> *(VTTI — 100-Car Naturalistic Driving Study · ⚠️ verify lại citation trước khi nộp)*

**3 pain points (cột phải):**

1. **Chạm = mất tập trung** — Chỉnh điều hòa, đổi nhạc, xem thông tin xe đều phải qua màn hình cảm ứng nhiều bước, đúng lúc đang lái.
2. **Trợ lý hiện tại "nói được, không làm được"** — Yếu tiếng Việt trong cabin ồn; phần lớn chỉ trả lời thông tin chứ không điều khiển được phần cứng xe; phụ thuộc cloud nên độ trễ cao và mất mạng là tê liệt.
3. **Tài xế giao hàng — nhóm rủi ro nhất** — Quy trình check đơn, tìm chặng kế tiếp, xác nhận giao đều cần tay và mắt trên điện thoại trong lúc xe đang chạy; chưa có workflow nào thiết kế rảnh tay cho họ.

---

## SLIDE 7 — Ý TƯỞNG TRIỂN KHAI

**Headline:** *Cockpit không chờ được chạm — nó nghe, hiểu tiếng Việt, và làm ngay*

**Elevator pitch:**
> "Vivi ơi, hạ điều hòa xuống 22 độ rồi bật playlist đi làm" — nhiệt độ đổi **thật** qua VHAL, nhạc phát **thật** từ USB ảo, HMI cập nhật tức thì, tất cả dưới **1.5 giây** và **không cần mạng**. Chuyển sang chế độ giao hàng: Vivi đọc chặng kế tiếp, báo trạng thái đơn, xác nhận giao thành công — hoàn toàn rảnh tay, hoàn toàn tiếng Việt.

**💡 Insight chính (ô vàng, theo layout mẫu):**
> Bốn đề Digital Cockpit được ra như bốn ứng dụng riêng: Media là "bài toán phát nhạc", HVAC là "bài toán full-stack", DTC là "bài toán chẩn đoán", Voice là "bài toán NLP". **Nhưng người lái không mở app — họ nói một câu và mong chiếc xe phản hồi.** Giá trị thật nằm ở lớp điều phối: chúng tôi xây Voice Assistant thành **bộ não định tuyến** của cockpit, còn Media – Climate – DTC là **cơ bắp thật** lấy từ chính starter pack. Lệnh nói ra được thực thi qua VHAL và MediaSession thật, không phải mock UI. Một đề làm core, ba đề làm skill — một demo, bốn bài toán.

**3 điểm khác biệt:**
1. **Điều khiển thật, không mock** — hành động đi xuống tận VHAL property / MediaSession, HMI phản chiếu trạng thái xe real-time.
2. **Tiếng Việt-first, noise-robust** — PhoWhisper + Silero VAD, kế thừa kinh nghiệm production pipeline đa ngôn ngữ WER <5% của team.
3. **Edge-first latency** — kiến trúc 3 tầng: lệnh phổ biến xử lý ngay trên máy (<1s), cloud LLM chỉ là fallback cho câu tự do; mất mạng vẫn chạy đủ kịch bản chính.

---

## SLIDE 8 — KIẾN TRÚC & NGUYÊN LIỆU STARTER PACK

**Pipeline (vẽ dạng 6 khối ngang như slide mẫu, gắn nhãn nguồn dưới mỗi khối):**

```
🎙️ Mic/Speaker      →  VAD + ASR              →  Intent Router 3 tầng   →  Command Dispatcher  →  Skills (4)             →  TTS + HMI AAOS
[Starter pack:          [Tự xây: Silero VAD +      [Tự xây]                  [Tự xây]               [Starter pack làm nền]     [Tự xây]
audio pipeline]         PhoWhisper ONNX INT8]                                                       Media · VHAL · DTC · Ship
```

**Intent Router 3 tầng (điểm nhấn kỹ thuật):**
- **T0 — Grammar/keyword on-device:** ~20 lệnh phổ biến nhất (volume, nhiệt độ, next bài, khóa cửa) → khớp tức thì, <300ms.
- **T1 — Intent classifier edge:** model nhẹ (distilled, ONNX INT8) cho câu nói tự nhiên có cấu trúc → <800ms.
- **T2 — Cloud LLM function-calling:** câu tự do/phức hợp ("tìm quán cà phê gần điểm giao kế tiếp") → ≤2.5s, **degrade có kiểm soát khi mất mạng** (T0/T1 vẫn chạy đủ demo).

**Map tài nguyên BTC → thành phần (chốt điểm "Hiểu đề & starter pack" 20đ):**

| Tài nguyên starter pack | Dùng cho |
|---|---|
| AAOS14 (google trout) trên nền tảng ảo hóa FPT | Môi trường chạy toàn bộ demo + APK |
| Mic/speaker + minimal audio pipeline | Đầu vào/ra giọng nói, nền để thay ASR riêng |
| Default VHAL trên AAOS | Climate skill: get/set HVAC_POWER_ON, HVAC_TEMPERATURE_SET, HVAC_FAN_SPEED; DOOR_LOCK |
| Virtual USB device kết nối sẵn AAOS | Nguồn media thật cho Media skill |
| Default AAOS UDS / DTC | Nguồn dữ liệu cho DTC skill |
| Mentor CDC (office hours T3 & T5) + repo cdc-starter | Checkpoint cuối mỗi tuần Vòng 2 |

**Ngân sách độ trễ (mục tiêu, khớp tiêu chí Latency <1.5s của đề):**

| Chặng | Mục tiêu |
|---|---|
| Wake word + VAD | ~150ms |
| ASR streaming (edge) | ~500ms |
| Intent T0/T1 | 50–150ms |
| Action + HMI update | ~100ms |
| **Tổng (đường edge)** | **~0.8–1.0s** (trần 1.5s) |
| Đường T2 (cloud, câu tự do) | ≤2.5s |

---

## SLIDE 9 — CAM KẾT ĐẦU RA (khớp 1-1 với Output Requests của đề)

- ✅ **Voice demo ≥5 lệnh car control** — cửa, volume, media, điều hòa; HMI đổi trạng thái ngay khi thực thi.
- ✅ **Delivery voice flow end-to-end** — kích hoạt → chặng kế tiếp → trạng thái đơn → xác nhận giao, hoàn toàn rảnh tay.
- ✅ **Latency benchmark report** — ≥20 utterances, đo end-to-end (wake → intent → action), so sánh **edge-only vs hybrid**.
- ✅ **Sơ đồ kiến trúc** — voice → ASR → NLU → dispatcher → vehicle integration, kèm extension point thêm intent/tín hiệu xe mới.
- ✅ **APK chạy trên AAOS AVD** — cả kịch bản car control và delivery, giọng nói thật, HMI phản hồi thấy được.

---

## SLIDE 10 — LỘ TRÌNH VÒNG 2 (21/07 – 10/08 · 3 tuần)

**Tuần 1 — Dựng xương sống** *(must-have)*
- Audio pipeline chạy trên AVD: mic → VAD → ASR ra text tiếng Việt.
- 5 lệnh T0 hoạt động; **2 hành động thật** end-to-end: volume + nhiệt độ qua VHAL.
- HMI skeleton + benchmark harness đo latency từng chặng ngay từ đầu.

**Tuần 2 — Đủ skill + router** *(must-have)*
- Hoàn thiện 4 skill: Media (USB ảo), Climate (đủ property), DTC (query + đọc kết quả), Delivery flow.
- Router 3 tầng + cloud fallback; TTS phản hồi; **confirm lệnh nhạy cảm** (mở cửa khi xe đang chạy → hỏi lại).

**Tuần 3 — Đo đạc & demo** *(must-have)*
- Benchmark ≥20 utterances (edge vs hybrid) + tinh chỉnh ngưỡng VAD/ASR trong nhiễu.
- Kịch bản demo mượt, video 5–7 phút, write-up (kể rõ **cách dùng AI trong quá trình build** — tiêu chí BTC cộng điểm).
- *Nice-to-have (chỉ làm nếu kịp):* barge-in (ngắt lời trợ lý), noise augmentation, cá nhân hóa theo giọng, theme ngày/đêm.

Chú thích màu như slide mẫu: ■ Must-have ■ Nice-to-have. Nhịp làm việc: checkpoint mentor CDC vào office hours Thứ 3 & Thứ 5 hằng tuần.

---

## SLIDE 11 — TẦM NHÌN CỦA ĐỘI

**Phục vụ ai**
- OEM / Tier-1 muốn trợ lý giọng nói tiếng Việt **thực sự điều khiển xe** mà không thêm phần cứng.
- Doanh nghiệp giao vận (fleet last-mile) muốn quy trình rảnh tay an toàn cho tài xế.

**Vì sao đáng làm tiếp**
- Intent framework mở: thêm skill mới (đỗ xe, OTA, cảnh báo DMS…) không đụng core — đúng tiêu chí Extensibility của đề.
- Lợi thế dữ liệu giọng tiếng Việt trong môi trường cabin ồn — rào cản mà trợ lý ngoại khó sao chép.
- Hướng cross-vertical (để mở, cộng điểm nếu kịp): DTC skill nối sang chuẩn SOVD (Vehicle Middleware), hoặc thêm KMS agent trả lời câu hỏi từ manual xe (Agentic AI).

**Bước tiếp theo**
- Test với noise cabin thật, mở rộng bộ lệnh, đo task-completion-time so với thao tác chạm truyền thống.

*Vòng 1 không bắt buộc business model chi tiết — để mở là được (đúng ghi chú trong slide mẫu của BTC).*

---

## PHỤ LỤC A — Rủi ro & phương án (giữ làm slide backup / trả lời Q&A)

| Rủi ro | Phương án |
|---|---|
| ASR nặng, emulator yếu | Dùng whisper-tiny/small INT8; kiến trúc cho phép swap ASR chạy trên host qua gRPC mà không đổi các khối khác |
| Mất mạng lúc demo | Toàn bộ kịch bản demo chính chạy T0/T1 offline; cloud chỉ minh họa câu tự do |
| VHAL phức tạp hơn dự kiến | Chốt trước 4 property chuẩn: HVAC_POWER_ON, HVAC_TEMPERATURE_SET, HVAC_FAN_SPEED, DOOR_LOCK; mở rộng sau |
| Không đạt <1.5s | Streaming ASR chạy song song intent; benchmark harness từ tuần 1 để biết chặng nào chậm mà tối ưu đúng chỗ |
| Nhận nhầm intent lệnh nhạy cảm | Confirmation bắt buộc cho door/unlock khi xe chạy; ngưỡng confidence + fallback hỏi lại |

## PHỤ LỤC B — Kịch bản demo 90 giây (dùng cho video Vòng 2, kể được trong pitch)

1. *"Vivi ơi"* → wake. *"Hạ điều hòa 22 độ, quạt mức 2"* → HMI HVAC đổi, log VHAL hiện property thay đổi thật.
2. *"Phát playlist đi làm"* → media player phát từ USB ảo, album art hiện, *"chuyển bài"* hoạt động.
3. *"Xe có lỗi gì không?"* → DTC skill: "Có 1 mã P0301 đang pending — misfire xy-lanh 1, mức trung bình, nên kiểm tra khi bảo dưỡng."
4. Chế độ giao hàng: *"Chặng tiếp theo là gì?"* → "Đơn A12, 25 Duy Tân, khách hẹn trước 10h." → *"Xác nhận giao thành công"* → trạng thái đơn cập nhật trên HMI.
5. **Rút mạng** — các lệnh trên vẫn chạy (edge). Cắm lại — câu tự do *"tìm quán cà phê gần điểm giao kế tiếp"* → cloud LLM trả lời.

## PHỤ LỤC C — Checklist khớp barem Vòng 1 (nội bộ, không đưa vào slide)

- **Ý tưởng (35đ)** → Slide 6–7: vấn đề có số liệu, insight nối 4 đề, giá trị thực (an toàn + tài xế giao hàng), tiềm năng mở rộng rõ.
- **Tính khả thi (30đ)** → Slide 8–10: kiến trúc cụ thể từng khối, ngân sách latency, roadmap must/nice, bảng rủi ro có phương án.
- **Hiểu đề & starter pack (20đ)** → Slide 5, 8, 9: map từng tài nguyên BTC vào từng khối + cam kết đầu ra khớp 1-1 Output Requests.
- **Năng lực đội (15đ)** → Slide 3: kinh nghiệm production VAD/ASR + phân vai theo đúng work stream của kiến trúc.

**Việc cần làm trước 12/07:** ① điền tên đội + 4 thành viên và chỉnh mô tả cho khớp kinh nghiệm thật · ② verify citation số liệu VTTI (hoặc thay bằng số liệu khác có nguồn) · ③ chèn ảnh đội + avatar · ④ đăng ký chọn bài tập qua link BTC gửi email cho đội trưởng · ⑤ nộp trước 23:59 ngày 12/07/2026.
