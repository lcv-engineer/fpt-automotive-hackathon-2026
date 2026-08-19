# PHÂN CÔNG VÒNG 3 — 18/08 → 23/08

> 🔴 **ĐỌC [`08-HOA-GIAI-VOI-PHIEN-19-08.md`](08-HOA-GIAI-VOI-PHIEN-19-08.md) TRƯỚC** — V2/T2/T3/T4 đã bị huỷ hoặc đã xong.
>
> **Ngân sách thật (tính từ 19/08 23:00):** **2 buổi tối** (20 và 21/08) + **3,5 giờ code tại chỗ**
> ngày 22/08. Không hơn.
>
> Mọi ước lượng dưới đây giả định ~4–5h/người/tối và full ngày 21/08.

---

## 1. Bốn người, bốn làn

| Người | Làn | Chịu trách nhiệm cuối cùng cho |
|---|---|---|
| **Long** | 1 — Sân khấu | Kịch bản, Q&A, slide, hiệu quả kinh tế, decision table. **Người nói 20 phút** |
| **Dương** | 2 — Độ tin cậy | Chốt phủ định, TTS, bộ câu ổn định, **video dự phòng (G-C)** |
| **Vĩ** | 3a — Nền tảng | Room sống, spike quyền (G-A), correlated trace, khoảnh khắc ② |
| **Tùng** | 3b — VHAL | `setProperty` + readback (G-B), gateway, luồng ngược |

**Long không nhận việc code.** 20 phút trên sân khấu là của anh ấy; thời gian anh ấy phải được bảo vệ.

---

## 2. Sơ đồ ai chờ ai

```
Vĩ: Room sống (18/08 tối)
      │
      ├──→ Vĩ: G-A spike quyền (19/08) ──→ Tùng: G-B readback (20/08)
      │                                          │
      ├──→ Vĩ: correlated trace (20/08) ─────────┤
      │                                          │
      └──→ Vĩ: khoảnh khắc ② (20/08) ────────────┤
                                                 ↓
Dương: phủ định (19/08) ──→ Dương: Y1–Y3 trên Device (20/08) ──→ G-C VIDEO (21/08)
Dương: TTS (20/08) ───────────────────────────────────────────────┘
                                                                   ↓
Long: kịch bản (19/08) → Q&A + kinh tế + bảng (20/08) → slide + tập (21/08)
```

**Đường găng đi qua Vĩ.** Nếu Room chết tối 18/08 thì cả ba làn đổi hình. Đó là lý do việc đầu tiên
trong tuần là đánh thức Room.

---

## 3. Long — Làn 1 · ~18,5h *(L1–L3 đã xong 19/08)*

| Mã | Việc | Hạn | Xong nghĩa là | Giờ |
|---|---|---|---|---|
| ~~L1~~ | ~~Xác nhận thông tin thành viên với BTC~~ | ✅ **XONG 19/08** | Đã nhắn LinhNT169 | — |
| ~~L2~~ | ~~Gửi link mời khán giả~~ | ✅ **XONG 19/08** | Audience Choice 10tr — theo dõi số người đăng ký | — |
| ~~L3~~ | ~~Chốt di chuyển ra HN~~ | ✅ **XONG 19/08** | Có vé/lịch, kịp đón 08:00 ngày 22/08 | — |
| L4 | Kịch bản 10 phút, **đổi ② → ②′** | 20/08 | `01-KICH-BAN` đã rà lại theo thực tế | 3 |
| L5 | Bộ Q&A v1, **viết lại A1** theo file 08 §9 | 20/08 | 14 câu có locator, đã chia vai 4 người | 3 |
| L6 | Quyết I6/I7: dẫn nguồn được hay cắt | 20/08 | Không còn ô 🔴 nào chưa quyết | 1,5 |
| L7 | Mô hình hiệu quả kinh tế, 3 kịch bản | 21/08 | 1 slide, mọi số có nhãn 🟢/🟡/🔴 | 2,5 |
| L8 | Rà nhãn decision table theo mã thật | 20/08 | 🟢/🟡/🔵 đúng với code | 1 |
| L9 | **Slide hoàn chỉnh** | 21/08 | ≤12 slide, không lỗi chính tả | 4 |
| L10 | Tập nói bấm giờ ×3 | 21/08 | Cả 3 lần đều ≤ 10:00 | 2 |
| L11 | Điều phối tập hỏi chéo | 22/08 tối | Cả 4 người thuộc phần mình | 1,5 |

> ⚠️ **L9 có ô "+01 nội dung không có lỗi chính tả".** Cho người khác đọc soát, đừng tự soát.

---

## 4. Dương — Làn 2 · ~14h

| Mã | Việc | Hạn | Xong nghĩa là | Giờ |
|---|---|---|---|---|
| D1 | **Chốt phủ định** theo token | 20/08 | N1–N4 xanh **và N5–N7 hồi quy xanh** | 4 |
| D2 | Đo I3/I4/I5 cho mô hình kinh tế | 20/08 | `evidence/ux/touch-count-baseline.csv` | 2 |
| D3 | TTS không bao giờ im | 20/08 | 11/11 câu trả lời có tiếng | 2,5 |
| D4 | Kiểm Y1–Y3 trên Device (bảng vàng, file 06) | 20/08 | Lên bảng xanh, hoặc báo Long cắt khoảnh khắc | 1,5 |
| D5 | **G-C — quay video dự phòng** | **21/08 20:00** | Uncut, phát được, một artifact identity | 4 |

> ⛔ **D5 là gate cứng của cả đội.** Nếu D1–D4 trễ, **cắt việc chứ không trễ D5**. Video ăn +10 và
> che rủi ro cho +15; không có nó thì cả hai ô đều treo.

### Yêu cầu của video G-C

- Quay **liên tục, không cắt ghép** — dựng lại niềm tin sau nhận xét *"video chỉ khóa emulator/mock slice"*
- Quay **màn hình Device thật**, không phải emulator
- Có **âm thanh thật** — nghe được câu nói và TTS
- Mở đầu bằng 5 giây hiện: commit, APK SHA-256, Device serial, ngày giờ
- Phủ đủ 6 khoảnh khắc, riêng khoảnh khắc ② (dừng/bật node) là **bắt buộc**
- Dài 3–4 phút, để chèn được vào 10 phút mà vẫn còn chỗ nói

---

## 5. Vĩ — Làn 3a · ~14h

| Mã | Việc | Hạn | Xong nghĩa là | Giờ |
|---|---|---|---|---|
| ~~V1~~ | ~~Đánh thức Room~~ | ✅ **XONG 19/08** | Room sống, 2 script-node đã restart, mạng đã khôi phục | — |
| ~~V2~~ | ~~G-A spike root~~ | ✅ **XONG** — có root; `CAR_SPEED` là `dangerous`, không cần M1a | — |
| **V2′** | **G-A′** — xác minh đường ASR từ guest + runbook khôi phục `eth1` | **20/08 22:00** | Biết chắc dừng node thì lệnh có chết không | 2,5 |
| **V2″** | Chuẩn bị khoảnh khắc ②′ trên slider Drive Controls | 20/08 | Kéo slider → 4 tầng đọc lại đổi theo | 1,5 |
| V3 | Build **`real` sau `clean`**, remote-ASR-only, xác nhận tắt fallback | 20/08 | Dừng node → lệnh **thật sự** chết | 2 |
| V4 | **Correlated trace một `traceId`** | 20/08 | `evidence/vong3/correlated-trace-*/` đủ 6 mục identity | 3,5 |
| V5 | Tập thao tác dừng/bật node cho khoảnh khắc ② | 21/08 | Biết mất bao lâu để `Running` trở lại | 1 |
| V6 | Đóng artifact identity | 21/08 | Một commit đã push, SHA-256 khớp 3 nơi | 2 |
| V7 | Vận hành demo trên sân khấu | 23/08 | — | 1 |

> ⏱ **V2 có hộp thời gian cứng 3 giờ.** Quá thì tuyên bố fail, chuyển sang V4. Ba giờ này không được
> ăn vào G-C.

---

## 6. Tùng — Làn 3b · ~14h

| Mã | Việc | Hạn | Xong nghĩa là | Giờ |
|---|---|---|---|---|
| T1 | Chuẩn bị runbook priv-app, chờ G-A | 19/08 | Lệnh đã soạn sẵn, chạy được ngay khi V2 pass | 1,5 |
| ~~T2~~ | ~~G-B setProperty~~ | ❌ **HUỶ** — bị chặn bởi image (file 08 §1) | — |
| ~~T3~~ | ~~Xác nhận gateway nhận lệnh~~ | ✅ **XONG 19/08** — có log `[igw] → vhal … pushed` | — |
| **T4′** | **Soạn phần nói về root cause fake server** — Tùng nói câu A1 trên sân khấu | 20/08 | Thuộc file 08 §9 | 3 |
| T5 | Soạn phần trả lời Q&A về VHAL/property/gateway | 21/08 | Thuộc mục A1, B1, B3 của `02-QA` | 2 |
| T6 | Hỗ trợ quay video G-C | 21/08 | — | 2 |

> T2 huỷ, T3 đã xong ⇒ Tùng dôi ra ~8 giờ. Đổ vào T4′, hỗ trợ quay video G-C và tập Q&A.

---

## 7. Ba mốc cân bằng lại

Không đợi đến hạn mới biết trễ. Ba thời điểm cả đội báo cáo trạng thái, mỗi lần 15 phút:

| Mốc | Câu hỏi phải trả lời được |
|---|---|
| ~~19/08~~ | ✅ Đã xong — Room sống, G-A đóng, G-B đóng có root cause |
| **20/08 22:00** | **G-A′ pass?** Correlated trace có chưa? Y1–Y3 lên xanh hay bị cắt? Phủ định xanh chưa? |
| **21/08 20:00** | ⛔ **Video có chưa?** Slide xong chưa? Long đã tập ≤10:00 chưa? |

Mốc 21/08 là mốc duy nhất **không được phép trượt**.

---

## 8. Ngày 22/08 — 3,5 giờ, dùng thế nào

| Giờ | Việc | Ai |
|---|---|---|
| 09:30–10:00 | Minigame chọn slot — nhắm **slot 4 hoặc 5** | Long |
| 10:30–11:15 | Cắm Device, chạy lại **toàn bộ 6 khoảnh khắc** trên hạ tầng sự kiện | Vĩ + Dương |
| 11:15–12:00 | Ghi lại đúng thứ hỏng. **Chỉ ghi, chưa sửa** | cả đội |
| 14:00–15:30 | Vá đúng thứ đã ghi. **Không thêm tính năng** | Dương + Vĩ |
| 15:30–16:00 | Chạy lại lần cuối, `./gradlew test`, cập nhật con số cho Q&A B2 | Vĩ |
| **16:00** | ⛔ **G-D CODE FREEZE** | — |
| 17:00–18:00 | Tổng duyệt 10 phút + **nộp file cho BTC** | Long dẫn |
| 20:30–21:30 | Tập hỏi chéo — Vĩ và Dương đóng vai BGK hỏi khó | cả đội |

> **Luật của ngày 22/08: không ai được viết tính năng mới.** 3,5 giờ chỉ đủ để làm cho thứ đã có
> chạy được trên hạ tầng lạ. Đây là lỗi kinh điển của ngày cuối và nó đã suýt xảy ra ở Vòng 2.

---

## 9. Nếu trễ thì cắt gì — thứ tự cắt

Khi hết giờ, cắt theo thứ tự này. Đã quyết trước để lúc cuống không phải cãi.

| Thứ tự cắt | Hạng mục | Vì sao cắt được |
|---|---|---|
| 1 | Khoảnh khắc ④ suy diễn ngữ cảnh | Nói bằng miệng ở phần "khác biệt" vẫn được |
| 2 | Khoảnh khắc ⑤ phủ định | Tiếc, nhưng nếu chưa xanh thì diễn là tự sát |
| 3 | Vế readback trong khoảnh khắc ① | Đã có câu thay ở `01-KICH-BAN` §4① |
| 4 | Mô hình hiệu quả kinh tế phần 🔴 | Giữ phần dựng từ số đội tự đo |
| 5 | Khoảnh khắc ⑥ media | Media là thứ chắc nhất, cắt cuối cùng |

**Không bao giờ cắt:** video G-C · khoảnh khắc ② nền tảng · khoảnh khắc ③ SafetyGuard · bộ Q&A.
Bốn thứ này là toàn bộ khác biệt của đội.

---

## 10. Việc hành chính — Long chịu trách nhiệm

**✅ Xong 19/08:** phản hồi BTC xác nhận thông tin 4 thành viên · gửi link mời khán giả ·
chốt vé ra Hà Nội.

**Còn lại:**

- [ ] 4/4 người có **CCCD hoặc VNeID Mức 2 đã kích hoạt**, tên và năm sinh trùng danh sách đăng ký —
      xác minh danh tính tại check-in **ngày 22/08**, không phải 23/08
- [ ] Nhắc cả đội: **áo Automotive Hackathon do BTC cấp** — không mặc áo khác
- [ ] Mang: laptop + sạc, dây HDMI dự phòng, chuột, **video dự phòng lưu offline trên ≥2 máy**
