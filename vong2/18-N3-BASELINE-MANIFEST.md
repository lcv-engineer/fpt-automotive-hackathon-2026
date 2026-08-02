# N3 — Baseline Manifest: nền tảng cấp sẵn gì, đội xây gì

> **Chủ sở hữu:** N3a (nền tảng + bảng tổng) — Vĩ · N3b (VHAL/CAN/Luau) — Tùng.
> Hạn 🟡 06/08. Bản này là **phần N3a + khung bảng tổng**; các dòng gắn nhãn
> 🟠 **CHỜ N3b** là phần chỉ Tùng trả lời được, đừng tự điền hộ.
>
> Vì sao có file này: barem Vòng 2 chấm ô *Tách phần team-owned* (5đ) và
> *Ranh giới và tính tương xứng* (2đ). Cả hai đều hỏi đúng một câu — **cái gì
> là của CarSky/starter pack, cái gì đội tự làm** — và câu đó chỉ trả lời được
> bằng một bảng có nhãn, không bằng một đoạn văn.

## Bốn nhãn, dùng đúng nghĩa

| Nhãn | Nghĩa | Ví dụ |
|---|---|---|
| `provided` | Nền tảng cấp sẵn, đội **dùng nguyên** | Device AAOS trong Room |
| `configured` | Cấp sẵn, đội chỉ **đổi tham số/cấu hình** | Blueprint clone từ bản mẫu |
| `modified` | Cấp sẵn, đội **sửa nội dung bên trong** | Script Node có sẵn bị sửa mapping |
| `new` | Đội **viết mới từ đầu** | `viva-asr`, harness `viva-tools` |

Quy tắc: nghi ngờ thì hạ một bậc (`new` → `modified`), đừng nâng.

## A. Nền tảng CarSky — phần N3a

| Thành phần | Nhãn | Căn cứ | Ghi chú |
|---|---|---|---|
| Device AAOS trong Room | `provided` | CarSky cấp sẵn trong blueprint | Đội **không** build lại image AAOS — xem quyết định ⑯ ở `11-PHAN-HOI-MENTOR-KICKOFF` |
| Blueprint (bản clone của đội) | `configured` | V2 export backup → clone → deploy | Đội chỉ thêm node, không dựng blueprint từ số 0 |
| KUKSA Data Broker (VSS) | `provided` | Có sẵn trong starter kit | |
| Script Node IVI Gateway / PWT Gateway | 🟠 **CHỜ N3b** | Mentor: *"ở phần starter kit bạn có thể check script node để truyền data giữa skycraft và kuksa"* | M4 phải trả lời: **sửa mapping** (`modified`) hay **viết mới** (`new`). Đây là dòng quan trọng nhất cả bảng |
| DBC (`body_can.dbc`, `powertrain_can.dbc`) | `provided` | Tải từ panel Artifacts của `hackathon-2.carsky.io` — xem `docs/dbc/README.md` | Đội **không** tự viết DBC; đội chỉ đọc và lập bảng đối chiếu |
| Catalog VSS (`vss_full_demo.json`) | `provided` | Cùng nguồn Artifacts | |
| Bảng đối chiếu property ↔ signal (`docs/dbc/README.md`) | `new` | V1 của Vĩ + T1 của Tùng | Bản thân bảng là sản phẩm của đội, dù dữ liệu đầu vào là `provided` |
| candb của BTC | 🟠 **CHỜ M4** | Mentor: *"có thể sử dụng candb BTC đã dựng sẵn"* | Nếu dùng → `provided`; nếu đội tự parse DBC thô → phần parse là `new` |
| Signal Watch · Road Simulator · GPIO Panel (widget) | `provided` | Widget của nền tảng, dùng để kiểm chứng | Dùng làm **công cụ đo**, không phải phần đội xây |
| Zot registry | `provided` | Nơi push image `viva-asr` | |
| CCU thật | **không có** | Mentor cho phép *"giả lập nhận gửi CAN signal"* | M5 CCU mô phỏng → khai nhãn **mô phỏng** ở N5, tuyệt đối không khai *đã tích hợp* |

## B. Phần đội tự xây — có trong repo, kiểm được bằng mắt

| Thành phần | Đường dẫn | Nhãn | Ai |
|---|---|---|---|
| Voice core: trace · VAD · grammar 10 intent · TTS · audio focus | `android/voice/` | `new` | Long |
| App AAOS + HMI + feature modules | `automotive/` | `new` | Dương |
| `vehicle-service` API + impl (mock/real) | `automotive/vehicle-service/` | `new` | Tùng + Dương |
| `DeliverySkill` + simulator lộ trình | `automotive/feature/voice/.../domain/delivery/` | `new` | Vĩ |
| `viva-asr` (service + Dockerfile) | `asr/` | `new` | Vĩ |
| Benchmark harness `viva-tools` | `backend/` | `new` | Vĩ |
| Bộ 22 câu benchmark + runner PASS/FAIL | `backend/suites/`, `harness verify` | `new` | Vĩ |
| Script Node Luau VHAL ↔ CAN | `vhal_server.luau` | 🟠 **CHỜ N3b** | Tùng |
| `VivaCarService` (M1) | 🟠 **CHỜ N3b** | — | Tùng + Vĩ |
| `SafetyGuard` (T5/T6) | 🟠 **CHỜ N3b** | — | Tùng |

> ⚠️ **Model không phải phần đội xây.** PhoWhisper/Whisper, Silero VAD, Vosk và MiniLM
> đều là model tải về — nhãn `provided` theo nghĩa "third-party", và phải nằm ở mục
> mã nguồn mở của README, không nằm ở cột team-owned. Cái đội tự làm quanh chúng là:
> đóng gói, quy đổi `confidence`, ngưỡng, và đường phục vụ.

## C. Cách nộp phần còn thiếu (cho Tùng — N3b)

Trả lời đúng 3 câu, mỗi câu một dòng bảng:

1. Script Node có sẵn của starter kit — đội **sửa** hay **viết mới**? (kết quả M4)
2. Property/signal nào CarSky đã wire sẵn tới KUKSA, cái nào đội tự wire trong Luau?
3. `SafetyGuard` và `VivaCarService`: có phần nào kế thừa từ mẫu của nền tảng không, hay `new` hoàn toàn?

Gửi lại cho Vĩ ghép vào mục A + B, **không tạo file riêng** — bảng bị tách đôi là bảng không ai đọc.
