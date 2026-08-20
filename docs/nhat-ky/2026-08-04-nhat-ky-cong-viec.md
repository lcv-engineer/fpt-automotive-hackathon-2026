# NHẬT KÝ TIẾN TRÌNH CÔNG VIỆC & TỔNG HỢP PHIÊN LÀM VIỆC (04/08/2026)
### Dự án: VIVA (Vietnamese In-Vehicle Assistant) · Vai trò: Embedded / System Engineer (Lê Đức Tùng)

---

## 📌 1. TỔNG QUAN HẠNG MỤC CÔNG VIỆC ĐÃ HOÀN THÀNH HÔM NAY (04/08/2026)

Bám sát theo **[06-PHAN-CONG-4-NGUOI.md](file:///E:/FPT%20Automotive%20-%20VIVA%20Project/06-PHAN-CONG-4-NGUOI.md)** và bảng tiêu chí chấm điểm Vòng 2, vị trí **Embedded / System Engineer** đã thực thi và nghiệm thu thành công 100% các task của ngày 04/08 sát mốc Feature Freeze (23:59 05/08):

| Task Code | Hạng mục công việc | Mô tả & Sản phẩm bàn giao | Trạng thái |
|---|---|---|---|
| 🟡 **Task T8** | **Bộ Unit Test VHAL Standalone** | Xây dựng test suite Kotlin [`SafetyGuardTest.kt`](file:///E:/FPT%20Automotive%20-%20VIVA%20Project/android_cockpit/app/src/test/java/com/viva/cockpit/SafetyGuardTest.kt) và Python [`test_safety_scenario_pack.py`](file:///E:/FPT%20Automotive%20-%20VIVA%20Project/embedded/test_safety_scenario_pack.py) chạy độc lập không cần phần cứng ECU/Room | ✅ **DONE (100% PASS)** |
| 🟡 **Task T9** | **Safety Scenario Pack (8 Kịch bản)** | Đóng gói bộ 8 kịch bản kiểm thử tự động Pass/Fail cho SafetyGuard G1 & G2, xuất file báo cáo [`viva_safety_scenario_report.csv`](file:///E:/FPT%20Automotive%20-%20VIVA%20Project/viva_safety_scenario_report.csv) | ✅ **DONE (8/8 Pass)** |
| 🆕 **Task N3b** | **VHAL Baseline Manifest** | Lập tài liệu [`VHAL_BASELINE_MANIFEST.md`](file:///E:/FPT%20Automotive%20-%20VIVA%20Project/embedded/VHAL_BASELINE_MANIFEST.md) phân định minh bạch các thuộc tính `provided`, `configured`, `modified`, `new` để ăn trọn điểm Platform utilization (15đ) | ✅ **DONE** |
| 🆕 **Task N4b** | **Ablation Study A1 Evidence** | Chạy kiểm thử so sánh BẬT vs TẮT `SafetyGuard.isEnabled` thu giữ bằng chứng [`viva_ablation_a1_report.csv`](file:///E:/FPT%20Automotive%20-%20VIVA%20Project/viva_ablation_a1_report.csv) chứng minh vai trò cấp thiết của SafetyGuard | ✅ **DONE (Evidence CSV Generated)** |

---

## 🚀 2. CHI TIẾT SẢN PHẨM VÀ KẾT QUẢ NGHIỆM THU

### 1️⃣ Standalone Unit Test Suite (`SafetyGuardTest.kt` & `test_safety_scenario_pack.py`)
* Thực thi kiểm thử 8 kịch bản tự động bao phủ dải tính năng của hệ thống:
  1. `S1`: Mở cửa xe khi $v = 60\text{ km/h} \rightarrow$ **BLOCKED (G1.1)**.
  2. `S2`: Khóa cửa xe khi $v = 60\text{ km/h} \rightarrow$ **ALLOWED**.
  3. `S3`: Chỉnh nhiệt độ $14^\circ\text{C}$ (ngoài dải $16-32^\circ\text{C}$) $\rightarrow$ **BLOCKED (G1.2)**.
  4. `S4`: Chỉnh nhiệt độ $22^\circ\text{C}$ (trong dải $16-32^\circ\text{C}$) $\rightarrow$ **ALLOWED**.
  5. `S5`: Chỉnh mức quạt $8$ (ngoài dải $0-5$) $\rightarrow$ **BLOCKED (G1.3)**.
  6. `S6`: Chỉnh âm lượng $150$ (ngoài dải $0-100$) $\rightarrow$ **BLOCKED (G1.4)**.
  7. `S7`: Cài số Lùi (R = -1) khi $v = 45\text{ km/h} \rightarrow$ **BLOCKED (G2.1)**.
  8. `S8`: Cài số Lùi (R = -1) khi $v = 0\text{ km/h} \rightarrow$ **ALLOWED**.

### 2️⃣ Ablation Study A1 Evidence Report
* Kết quả chạy ngắt cờ `SafetyGuard.isEnabled = false` thu thập file CSV minh chứng:
  * Khi có SafetyGuard: $0$ sự cố an toàn (Tất cả yêu cầu nguy hại bị chặn).
  * Khi tắt SafetyGuard: Toàn bộ lệnh nguy hiểm mở cửa ở $60\text{ km/h}$ hoặc cài số R ở $45\text{ km/h}$ đều đi qua $\rightarrow$ Chứng minh mấu chốt kỹ thuật của phần team-owned.

---

## 📅 3. KẾ HOẠCH CHO NGÀY MAI (05/08/2026 - FEATURE FREEZE)

1. Rà soát file [`BANG_ANH_XA_VHAL_CAN.md`](file:///E:/FPT%20Automotive%20-%20VIVA%20Project/BANG_ANH_XA_VHAL_CAN.md) lần cuối để Long và Vĩ đóng gói vào README chính thức (Task T11).
2. Chuẩn bị báo cáo Safety Pack sẵn sàng cho Write-up (Task T12).
3. Đóng băng mã nguồn phần Embedded trước **23:59 05/08/2026**.
