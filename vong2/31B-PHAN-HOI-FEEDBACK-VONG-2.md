# PHẢN HỒI FEEDBACK VÒNG 2 — TEAM VIVA

> **Bản sẵn để gửi BTC/Mentor**
> Evidence chi tiết: [`31-BO-SUNG-EVIDENCE-3-TIEU-CHI.md`](31-BO-SUNG-EVIDENCE-3-TIEU-CHI.md)

Chào BTC/Mentor ThuyDH,

Team VIVA xác nhận đúng ba khoảng trống mentor nêu: team chưa xây
`VivaCarService`/AIDL riêng, chưa có bằng chứng `setProperty` và readback Vehicle
Property trên Device CarSky, và Climate ECU hiện tại là thành phần mô phỏng do
nền tảng cung cấp. Vì vậy team không claim luồng vehicle-control
`App → VHAL → CAN → CCU` đã hoàn tất.

Team xin bổ sung, làm rõ evidence cho ba tiêu chí như sau:

**1. Giá trị tăng thêm & Team-owned**

- Baseline đã được tách theo `provided / configured / modified / new`; team không
  nhận tám Script Node, KUKSA, CAN, DBC/VSS hay Climate ECU của nền tảng là phần
  tự phát triển.
- Phần team-owned quyết định gồm voice orchestration, typed-intent mapping,
  `SafetyGuard`, `GuardedVehicleRepository`, ASR container và bộ test/trace.
- Ablation A1 cho thấy: trong bộ 9 ca, khi bỏ SafetyGuard, cả 6 ca nguy hiểm đều
  ghi được vào `MockVehicleRepository`; 3 ca đối chứng hợp lệ không đổi. Evidence
  này chứng minh vai trò quyết định của guard ở mức JVM/mock, không phải VHAL
  Device.
- Team có sửa `IVI_GATEWAY.lua` và `BCM_GATEWAY.lua` so với bản platform tại
  commit `c255ccc`. Hai file được ghi nhãn **modified — chưa xác nhận runtime**;
  team không claim chúng đã chạy trên CarSky khi chưa có pod log.

**2. Platform Utilization**

- Container `viva-asr` của team đã được CarSky pull theo digest và chạy thành
  node `VIVA ASR` trong deployment có 22/22 node Running.
- APK mock của team đã chạy trên CarSky Device; lát cắt
  `text injection → NLU → MediaSession` làm thay đổi trạng thái play/pause và
  active item trên Device.
- Team công bố rõ giới hạn: evidence media bỏ qua mic/VAD/ASR, dùng flavor mock;
  vòng KUKSA REST không đi qua APK/VHAL; Device là Cuttlefish; chưa có
  vehicle-control readback tới VHAL/CAN/CCU.

**3. Người dùng/khách hàng/triển khai**

- User trực tiếp là tài xế Việt Nam, ưu tiên tài xế giao vận; buyer là OEM/Tier-1;
  process owner của pilot là fleet operations/an toàn đội xe.
- Offering là module và integration kit AAOS theo mô hình B2B2C; OEM/Tier-1 chịu
  trách nhiệm platform-sign, cấp quyền, safety validation và phát hành.
- Product & Integration Card đã nêu rõ outcome dưới dạng giả thuyết chưa đo,
  dependency theo trạng thái thật/mô phỏng/kế hoạch, rào cản privileged
  permission và bước kiểm chứng tiếp theo.

Toàn bộ locator, số liệu, giới hạn và mapping theo từng ô con của barem nằm trong
[`vong2/31-BO-SUNG-EVIDENCE-3-TIEU-CHI.md`](31-BO-SUNG-EVIDENCE-3-TIEU-CHI.md).

Team đồng ý với nhận xét rằng phần vehicle-control tới VHAL/CAN/CCU chưa hoàn
tất. Đồng thời, team kính đề nghị BTC/Mentor xem xét các evidence bổ sung trên
cho ba tiêu chí và điều chỉnh mô tả “output mới chỉ cung cấp phần ứng dụng tầng
trên” thành: **team đã có một số thành phần team-owned và platform runtime,
nhưng chưa hoàn tất luồng vehicle-control tới VHAL/CAN/CCU**.

Cảm ơn BTC và Mentor.
