# KỊCH BẢN VIDEO THUYẾT MINH + DEMO — hạn 06/08/2026

> **Loại video:** báo cáo tiến độ giữa kỳ theo yêu cầu BGK, **không phải** video 3 phút không cắt ghép của C3
> và **không phải** video nộp chính thức 5–7 phút. Video này được phép cắt cảnh và có lời dẫn.
> **Thời lượng đích:** 5–6 phút.
> **Nhãn bắt buộc hiện trên màn hình suốt phần demo app:** `MÔ PHỎNG — máy ảo AAOS, chưa phải Device CarSky`.

---

## 0. Vì sao quay được

Ngày 05/08 đã kiểm chứng: APK `mock` cài lên máy ảo Android Automotive 14 mất 3,1 giây, mở được, không crash, HMI hiển thị. Bằng chứng: `evidence/c2/local-emulator-20260805/`.

Nghĩa là đội **không cần chờ Device CarSky** mới có video. Nhưng cũng nghĩa là mọi câu nói trong video phải nằm trong đúng ranh giới đó.

---

## 1. Preflight — làm trước, ngoài thời lượng quay

### 1.1 Màn hình AVD — ✅ ĐÃ SỬA XONG 05/08

AVD trước đó là 320×640 nên chữ bị cắt (`Dri ve r`), không quay được. Đã sửa `AAOS_AVD.avd/config.ini` sang **1408×792, density 160, RAM 4G** và khởi động lại kiểm chứng: `adb shell wm size` → `Physical size: 1408x792`.

Layout sau khi sửa — xem `evidence/c2/local-emulator-20260805/hmi-landscape-1408x792.png`: Climate với card Driver / Passenger, Airflow Fan speed, thanh mic. **Đủ chất lượng để quay.**

Config gốc đã backup ở `config.ini.bak-20260805`. Chỉ sửa file này khi emulator **đã tắt**.

Khởi động:

```powershell
E:\Android\Sdk\emulator\emulator.exe -avd AAOS_AVD -no-snapshot-load -gpu swiftshader_indirect
```

### 1.1b Việt hoá HMI — việc còn lại trước khi quay

Text đang là tiếng Anh: `Climate`, `Airflow`, `Fan speed`, `Tap the mic and speak a command`. Sản phẩm bán câu chuyện trợ lý **tiếng Việt** nên đây là chỗ BGK nhìn thấy đầu tiên. Nếu kịp thì Việt hoá; nếu không kịp thì phải chủ động nói trong video, đừng để BGK hỏi.

### 1.2 Cài app và cấp quyền

```powershell
$env:PATH += ";E:\Android\Sdk\platform-tools"
$P = "com.sopa.viva_automotive.mock"
adb install -r -d automotive\app\build\outputs\apk\mock\debug\app-mock-debug.apk
adb shell pm grant --user 10 $P android.permission.RECORD_AUDIO
adb shell pm grant --user 10 $P android.permission.POST_NOTIFICATIONS
adb logcat -c
adb shell am start --user 10 -n "$P/com.sopa.viva_automotive.MainActivity"
```

### 1.3 Kiểm mic trước khi bấm quay — **làm việc này ĐẦU TIÊN, không để cuối**

`hw.audioInput=yes` nên emulator *có thể* nhận mic của host, nhưng **chưa ai xác nhận là nó thật sự vào**. Log tắt máy ngày 05/08 có dòng `warning: Voice is not capturing` — có thể chỉ là thông báo giải phóng thiết bị âm thanh lúc shutdown, nhưng cũng có thể là mic chưa từng mở. Chưa kết luận được.

Vì cả §3 phụ thuộc vào việc này, hãy kiểm ngay khi emulator vừa lên:

1. Mở app, bấm nút mic, nói "quạt mức 3"
2. Xem transcript có hiện trên HMI không
3. Song song mở `adb logcat -s VIVA_TRACE:I` xem có chặng nào chạy không

**Có transcript → chạy §3. Không có → chuyển thẳng §4, đừng mất thời gian sửa mic trong ngày quay.**

### 1.4 Chuẩn bị sẵn 4 cửa sổ, không mở live trong lúc quay

1. Emulator AAOS đang mở app VIVA
2. Terminal chạy `adb logcat -s VIVA_TRACE:I` (để thấy trace chạy)
3. Trình duyệt CarSky ở trang node list của room `og4erd2wzaxe5xod8otuj` — chỗ thấy `VIVA ASR` = `Running` và 22/22
4. File `evidence/ablation/a4-grammar-ablation.csv` mở sẵn

---

## 2. Timeline video

| Mốc | Trên màn hình | Người dẫn nói |
|---|---|---|
| **0:00–0:30** | Slide tiêu đề: VIVA · Digital Cockpit · Challenge #3 | "VIVA là trợ lý giọng nói tiếng Việt trên Android Automotive. Đây là báo cáo tiến độ đến ngày 05/08. Nguyên tắc của bản báo cáo này: cái gì đã chạy thì demo, cái gì chưa chạy thì nói là chưa." |
| **0:30–1:10** | Sơ đồ kiến trúc, tô màu 3 mức: xanh đã chạy / vàng có code chưa chạy trên thiết bị / đỏ chưa nối | "Luồng đích là app → service framework → PropertyID → VHAL → CAN → CCU. Hôm nay đội đã đóng được phần xanh. Phần đỏ là service framework và quyền privileged — em sẽ nói rõ ở cuối." |
| **1:10–2:40** | **Emulator AAOS, dán nhãn MÔ PHỎNG** | Demo thật, xem §3 |
| **2:40–3:20** | Trình duyệt CarSky — node list | "Đây là nền tảng thật. Container nhận dạng tiếng Việt của đội, `viva-asr`, đã được CarSky pull theo digest từ registry của đội và đang chạy. 22 trên 22 node Running. Đây là bằng chứng nền tảng, **không phải** bằng chứng latency — đội chưa gửi được request nào vào nó từ ngoài." |
| **3:20–4:20** | Terminal: kết quả test + file A4 | "181 unit test JVM xanh trên commit b223552. Và đây là con số đội tự hào nhất: bảng ablation. Khi bỏ tầng grammar do đội tự viết, 12 trên 22 câu mất lệnh hoàn toàn, và 2 câu đáng lẽ phải bị từ chối lại trở thành lệnh xe thật. Đây là cách đội đo phần mình tự làm đóng góp bao nhiêu — không phải đếm số tính năng." |
| **4:20–5:20** | Bảng ba trạng thái (§4 của `24-N5`) | "Đội phân biệt ba nhãn: Đã tích hợp, Mô phỏng, Kế hoạch. Hôm nay: voice pipeline và HMI đã chạy trên AAOS nhưng ở nhãn Mô phỏng vì lớp xe là mock. VHAL, media adapter và service framework đang ở nhãn Kế hoạch. Đội chưa dùng và sẽ không dùng cụm *full-stack tới CAN* cho tới khi có readback thật." |
| **5:20–6:00** | Slide 3 blocker + lịch tới 10/08 | "Ba việc chặn: quyền privileged VHAL, service framework, và `nydus-reach` để đo trên nền tảng. Lịch đóng ba việc này là 06 đến 08/08, tổng duyệt trên Device ngày 08/08." |

---

## 3. Phần demo trên emulator — 6 câu, 1 phút 30

| # | Nói vào mic | Kỳ vọng thấy | Nếu không đúng thì nói gì |
|---|---|---|---|
| ① | "Viva ơi, hạ điều hòa xuống 24 độ" | Transcript hiện; HMI Driver Temp về 24 | — |
| ② | "Quạt mạnh lên" | **Hỏi lại** "mức mấy, từ 0 đến 5?" | Đây là điểm nhấn: hệ thống **không đoán** khi thiếu slot |
| ③ | "Quạt mức 3" | Quạt về mức 3 | — |
| ④ | "Khóa cửa" | HMI phản chiếu trạng thái khóa | "Ở bản mock, giá trị này ghi vào repository mô phỏng, chưa đi xuống VHAL" |
| ⑤ | "Đặt bàn ăn tối" | **Từ chối lịch sự**, không sinh action nào | "Ngoài phạm vi thì hệ thống từ chối chứ không gọi bừa" |
| ⑥ | "Chuyển bài" | Router nhận đúng intent `media_next` | **Phải nói rõ:** "Intent nhận đúng nhưng media adapter chưa có, nên không có track nào đổi. Đây là nhãn Kế hoạch." |

Câu ⑥ là chỗ dễ mất điểm trung thực nhất — **không được để người xem hiểu là track đã đổi**.

Sau 6 câu, chuyển sang cửa sổ logcat `VIVA_TRACE` và chỉ vào các chặng đã có.

---

## 4. Phương án B — nếu mic không vào emulator

Không hoãn video. Đổi phần §3 thành hai thứ **đã kiểm chứng là chạy sạch**:

**(a) Chạy lại toàn bộ test JVM trước ống kính** — 1 phút 45, kết thúc bằng `BUILD SUCCESSFUL`:

```powershell
Set-Location automotive
.\gradlew.bat test --console=plain
```

**(b) Mở `evidence/ablation/a4-grammar-ablation.csv`** và đọc 3 dòng: `B01` mất lệnh, `B04` mất câu từ chối, `B08` mất lệnh. Đây là bằng chứng định lượng cho phần đội tự làm.

Rồi:

- Thao tác HMI bằng chuột để cho thấy app sống và giao diện phản hồi
- Nói thẳng: "Trong bản quay này đội chưa đưa được câu qua mic của máy ảo, nên demo bằng thao tác và bằng bộ test. Đường mic sẽ được chứng minh trong buổi tổng duyệt trên Device ngày 08/08."

> ⚠️ **Không** quay `harness verify` chạy trên fixture. Đã thử ngày 05/08: fixture chỉ có 4 lượt nên kết quả ra `1 PASS · 3 FAIL · 18 MISSING`. Lệnh này chỉ có nghĩa khi `--input` là log thật từ Device (`harness verify --adb`), tức sau 08/08.

---

## 5. Danh sách câu CẤM nói

- "Đã chạy trên CarSky" cho luồng điều khiển xe *(chỉ container ASR mới được nói câu này)*
- "Full-stack tới CAN" / "tới CCU"
- Bất kỳ con số latency end-to-end nào (`p50`, `p95`) — chưa có E03/E04
- "SafetyGuard đã chặn lệnh trên xe" — guard có trong code và có test, **chưa chạy trên Device**
- Trích WER 0,411 mà không kèm ngay: "đo trên giọng TTS tổng hợp, CPU máy dev, chưa phải giọng người trong cabin"
- "139 unit test chứng minh tích hợp" — test chứng minh source, không chứng minh Device

---

## 6. Checklist trước khi gửi video

- [ ] Nhãn `MÔ PHỎNG` hiện suốt phần demo app
- [ ] Có nói commit `b223552` và ngày quay
- [ ] Câu ⑥ đã nói rõ media adapter chưa có
- [ ] Không có câu nào trong §5
- [ ] Thời lượng ≤ 6:00
- [ ] Ba blocker được nêu đúng tên và có ngày đóng
- [ ] File video lưu vào `evidence/c2/` cùng ngày quay, không đè lên E10
