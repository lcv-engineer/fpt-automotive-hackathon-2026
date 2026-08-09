# KỊCH BẢN VIDEO THUYẾT MINH + DEMO — 07/08/2026

> **Thay thế `27-KICH-BAN-VIDEO-06-08.md`.** Bản 27 viết theo snapshot 05/08 và giờ đã sai ở bốn
> chỗ: nó ghi *"Silero VAD chưa cắm"*, *"SafetyGuard chưa chạy trên Device"*, *"181 test"* và
> *"MiniLM"*. Nếu đọc theo bản 27, người thuyết minh sẽ **tự khai thấp hơn** thứ đội thật sự có.
>
> **Loại video:** báo cáo tiến độ theo yêu cầu mentor/BGK. Được phép cắt cảnh và có lời dẫn.
> **Thời lượng đích:** 5–6 phút. **Không** phải video 3 phút không cắt ghép của C3.
> **Nhãn bắt buộc trên màn hình suốt phần demo:** `MÔ PHỎNG — máy ảo AAOS 14, chưa phải Device CarSky`.

---

## 0. Phần demo đã quay sẵn — chỉ cần lồng tiếng

**File:** `evidence/c2/demo-20260807/viva-demo-07-08.mp4` · **47,8 giây** · 1408×942 · không tiếng.

Đã có sẵn trong video: phụ đề tiếng Việt cho từng lượt, và **nhãn MÔ PHỎNG chạy suốt** ở chân
khung. Bằng chứng đi kèm: `trace-summary.log` (log thật của đúng lượt quay) và `run-manifest.txt`
(commit, APK, thiết bị, giới hạn).

Năm lượt trong video, **tất cả đều là kết quả thật, không dàn dựng**:

| Mốc | Câu | Kết quả thật trong log |
|---|---|---|
| 0:04 | "Viva ơi, hạ điều hòa xuống 24 độ" | `hvac_set_temp` · `Allow` · HMI về 24.0°C |
| 0:12 | "Quạt mạnh lên" | `clarify` · `Confirm:CLARIFY_SLOT` — **hỏi lại, không đoán** |
| 0:20 | "Quạt mức 3" | `hvac_set_fan` · `Allow` · HMI về mức 3 |
| 0:31 | "Mở cửa" *(xe đang chạy 60 km/h)* | `door_lock` · **`Deny:G1_SPEED_LOCK`** — cửa không bị ghi |
| 0:39 | "Đặt bàn ăn tối" | `unknown` · **`Deny:G3_UNSUPPORTED`** — từ chối có lý do |

> ⭐ **Lượt 0:31 là điểm nhấn của cả video, và nó mới có từ hôm nay.** Log ghi
> `source=VOICE`. Bằng chứng cũ (`evidence/emulator/safety-speed60.log`) chỉ có `source=HMI`,
> tức mới chỉ chứng minh guard chặn đường **chạm**. Lượt này chứng minh guard chặn cả đường
> **giọng nói**, trong cùng một lớp biên `VehicleRepository`.

---

## 1. Lời dẫn cho phần demo — đọc đè lên file trên

| Mốc video | Lời dẫn |
|---|---|
| **0:00–0:04** | "Đây là VIVA chạy trên máy ảo Android Automotive 14. Lớp xe ở bản này là mô phỏng — em sẽ nói rõ ranh giới đó ở cuối." |
| **0:04–0:12** | "Câu thứ nhất: *hạ điều hòa xuống 24 độ*. Nhiệt độ tài xế về đúng 24. Hệ thống chỉ nói 'đã đặt' sau khi tầng thực thi trả kết quả, không nói trước." |
| **0:12–0:20** | "Câu thứ hai là chỗ đội muốn BGK chú ý: *quạt mạnh lên* — thiếu mức. Hệ thống **không đoán**. Nó hỏi lại 'mức mấy, từ 0 đến 5'. Một trợ lý đoán giá trị cho lệnh xe là lỗi an toàn, không phải tính năng." |
| **0:20–0:31** | "Trả lời *quạt mức 3* — quạt về mức 3." |
| **0:31–0:39** | "Bây giờ em đặt tốc độ xe lên 60 km/h rồi nói *mở cửa*. Tầng SafetyGuard từ chối với mã luật `G1_SPEED_LOCK`, và property khóa cửa **không hề được ghi**. Điểm quan trọng: guard nằm ở biên repository, nên nó chặn cả lệnh giọng nói lẫn thao tác chạm — không chỉ đường voice." |
| **0:39–0:47** | "Câu cuối *đặt bàn ăn tối* nằm ngoài phạm vi. Hệ thống từ chối và **nói rõ phạm vi hỗ trợ**, không im lặng và không gọi bừa xuống xe." |

---

## 2. Bốn cảnh còn lại cần quay thêm

### 2.1 Mở đầu — 0:00–0:40

Slide tiêu đề + sơ đồ kiến trúc tô ba màu: **xanh = đang chạy**, **vàng = có code chưa chạy trên
Device**, **đỏ = chưa nối**.

> "VIVA là trợ lý giọng nói tiếng Việt trên Android Automotive, cho tài xế giao hàng. Nguyên tắc
> của bản báo cáo này: cái gì đã chạy thì demo, cái gì chưa chạy thì nói là chưa. Luồng đích là
> app → service framework → PropertyID → VHAL → CAN → CCU. Hôm nay đội đóng được phần xanh."

### 2.2 Nền tảng CarSky — 0:40–1:10

Quay màn hình trình duyệt ở trang node list.

> "Đây là nền tảng thật. Container nhận dạng tiếng Việt của đội — `viva-asr` — đã được CarSky pull
> theo digest từ registry của đội, 22 trên 22 node đang `Running`. Đây là bằng chứng **nền tảng**,
> **không phải** bằng chứng độ trễ: đội chưa gửi được request nào vào nó từ ngoài room."

### 2.3 Số đo — sau phần demo

Mở terminal và hai file.

> "255 trên 256 unit test JVM xanh. Một test đỏ là do kỳ vọng trong chính test viết sai, không phải
> lỗi sản phẩm — đội để nguyên và báo ra đây thay vì lặng lẽ vá.
>
> Con số đội tự hào nhất là bảng ablation: **bỏ tầng grammar do đội tự viết đi, 12 trên 22 câu mất
> lệnh hoàn toàn, và 2 câu đáng lẽ phải bị từ chối lại trở thành lệnh xe thật.** Đây là cách đội đo
> phần mình đóng góp bao nhiêu — không phải đếm số tính năng.
>
> Về nhận dạng tiếng Việt: trên bộ 20 câu **giọng người thật** thu ở 16 kHz, cấu hình PhoWhisper-tiny
> đạt **65% intent accuracy** với `server_ms` p95 **244 mili giây**. Con số này chấm bằng chính
> router của sản phẩm, không phải bằng một bản sao."

### 2.4 Ranh giới và blocker — 1 phút cuối

Mở bảng ba trạng thái.

> "Đội phân biệt ba nhãn: Đã tích hợp, Mô phỏng, Kế hoạch. Hôm nay voice pipeline, HMI và SafetyGuard
> đã chạy thật nhưng ở nhãn **Mô phỏng**, vì lớp xe là mock. VHAL và service framework ở nhãn
> **Kế hoạch**. Đội chưa dùng và sẽ không dùng cụm *full-stack tới CAN* cho tới khi có readback thật.
>
> Ba việc đang chặn: quyền privileged VHAL trên Device, service framework, và `nydus-reach` để đo
> được trên nền tảng. Việc thứ tư là của đội: hợp nhất bốn nhánh đang treo — trong đó có module media
> và bản sửa Property ID — trước code freeze ngày 08."

---

## 3. Danh sách câu CẤM nói — cập nhật 07/08

- ❌ "Đã chạy trên CarSky" cho luồng điều khiển xe *(chỉ container ASR mới được nói câu này)*
- ❌ "Full-stack tới CAN" / "tới CCU"
- ❌ Bất kỳ con số **p95 end-to-end** nào — chưa có E03/E04. *(`server_ms` của riêng chặng ASR thì
  được, kèm nhãn "đo trên CPU máy dev")*
- ❌ "SafetyGuard đã chặn lệnh trên **xe thật**" — câu đúng là *"chặn trên emulator AAOS, property là
  mock"*
- ❌ Trích 65% intent accuracy mà không kèm ngay: *"trên corpus 20 câu, một người nói, nam, không phải
  trong cabin, đo trên CPU máy dev"*
- ❌ "Media đã hoạt động" — module media **có thật** nhưng **chưa merge vào `main`**; câu đúng là
  *"đã viết xong, đang chờ hợp nhất"*

### Ba câu MỚI được phép nói từ hôm nay

- ✅ "Silero VAD đã nằm trên đường chạy APK; toàn repo chỉ còn **một** chỗ mở microphone."
- ✅ "SafetyGuard chặn cả đường giọng nói lẫn đường chạm, kiểm chứng trên emulator AAOS."
- ✅ "Bộ ngữ liệu benchmark là **giọng người thật** thu thẳng ở 16 kHz, không còn là giọng tổng hợp."

---

## 4. Checklist trước khi gửi video

- [ ] Nhãn `MÔ PHỎNG` hiện suốt phần demo *(file đã quay có sẵn — đừng crop mất chân khung)*
- [ ] Có nói commit `f93751e` và ngày quay 07/08
- [ ] Không có câu nào trong §3
- [ ] Có nói rõ media "đã viết, chưa merge" chứ không phải "chưa có"
- [ ] Thời lượng ≤ 6:00
- [ ] Ba blocker nêu đúng tên, có ngày đóng
- [ ] File cuối lưu vào `evidence/c2/demo-20260807/`, **không đè** lên `viva-demo-07-08.mp4`

---

## 5. Nếu muốn quay lại phần demo bằng mic thật

Đội **chưa từng** có lượt nào đi qua micro thật — cả ba phiên benchmark đều là bơm text. Nếu còn
thời gian, đây là thứ nâng giá trị video nhiều nhất, và runbook bật mic ảo cho emulator đã có sẵn
trên nhánh `feat/router-chiu-loi-thanh-dieu` của Vĩ (commit `2cfe594` — *"ghi lại bước bật mic ảo,
thử đã ngốn vài giờ vì không ai ghi"*).

Lệnh quay lại đúng như lượt 07/08 (giữ nguyên để tái lập được):

```powershell
$env:PATH += ";E:\Android\Sdk\platform-tools"
$P = "com.sopa.viva_automotive.mock"
adb install -r -d automotive\app\build\outputs\apk\mock\debug\app-mock-debug.apk
adb shell pm grant --user 10 $P android.permission.RECORD_AUDIO
adb shell pm grant --user 10 $P android.permission.POST_NOTIFICATIONS
adb shell am start --user 10 -n "$P/com.sopa.viva_automotive.MainActivity"
adb shell screenrecord --size 1408x792 --bit-rate 8000000 /sdcard/viva.mp4
```

⚠️ **Một cảnh báo từ lượt quay 07/08:** câu thứ sáu (*"chuyển bài"*) được receiver nhận nhưng
**không sinh lượt nào** — không có `VIVA_BENCH_INJECT`, không có `VIVA_TRACE_SUMMARY`. Xem
`run-manifest.txt` mục *LOI QUAN SAT DUOC*. Khi quay, **để cách nhau ít nhất 10 giây giữa hai câu**
và kiểm lại số dòng `VIVA_TRACE_SUMMARY` phải khớp số câu đã nói.
