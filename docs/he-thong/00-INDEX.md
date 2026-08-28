# VIVA — Bộ tài liệu hệ thống

> **Baseline:** code trong repo tại ngày **28/08/2026**, nhánh `main` sau khi merge
> `feature/voice-assistant-nlu-history-nav`.
>
> **Nguyên tắc:** tài liệu này phân biệt rõ **đang chạy trong code**, **đã chứng
> minh trên thiết bị**, và **kế hoạch**. Roadmap không được dùng làm bằng chứng
> runtime.

| Ký hiệu | Nghĩa |
|---|---|
| ✅ | Có trong code và đã chạy thật, có evidence |
| 🟡 | Có trong code, mới kiểm bằng unit test / emulator / mock |
| ❌ | Chưa có, hoặc bị chặn bởi yếu tố ngoài đội |

---

## Đọc theo mục đích

| Bạn cần | Đọc file |
|---|---|
| Hiểu hệ thống trong 5 phút | [01 — Kiến trúc tổng quan](01-KIEN-TRUC-TONG-QUAN.md) |
| Tìm code nằm ở đâu | [02 — Bản đồ repo & module](02-BAN-DO-REPO.md) |
| Mic, VAD, ASR, TTS | [03 — VIVA Voice](03-VIVA-VOICE.md) |
| NLU, intent, LLM planner | [04 — VIVA Brain](04-VIVA-BRAIN.md) |
| SafetyGuard, VHAL, mapping intent → CAN | [05 — VIVA Body](05-VIVA-BODY.md) |
| Service ASR tiếng Việt | [06 — viva-asr](06-VIVA-ASR-SERVICE.md) |
| Đo latency, benchmark, PASS/FAIL | [07 — Backend harness](07-BACKEND-HARNESS.md) |
| Script Luau/Lua, DBC, VSS, UDS/DTC | [08 — Embedded & Gateway](08-EMBEDDED-VA-GATEWAY.md) |
| Build, test, CI | [09 — Build, test & CI](09-BUILD-TEST-CI.md) |
| Trace format, evidence nào chứng minh gì | [10 — Quan sát & bằng chứng](10-QUAN-SAT-VA-BANG-CHUNG.md) |
| Tra một thuật ngữ | [11 — Thuật ngữ](11-THUAT-NGU.md) |
| Nền tảng CarSky | [`docs/carsky/`](../carsky/00-INDEX.md) |

---

## Một đoạn: VIVA là gì

VIVA là prototype **buồng lái số cho Android Automotive OS (AAOS)** với trợ lý
giọng nói tiếng Việt. Người lái nói một câu; hệ thống nghe, hiểu, kiểm tra an toàn,
rồi thực thi lên HVAC/cửa (qua Vehicle Property), media (qua MediaSession), âm lượng
(qua CarAudioManager) hoặc skill giao hàng trong app — và trả lời bằng giọng nói.

Hệ thống được tổ chức thành ba khối logic: **VIVA Voice** (nghe/nói) · **VIVA Brain**
(hiểu/điều phối) · **VIVA Body** (thực thi/an toàn).

**Thông điệp thiết kế:** Brain có thể thông minh hơn theo thời gian; **quyền ghi
xuống xe không đi theo độ thông minh đó**. `SafetyGuard` ở biên Body vẫn là chốt cuối.

---

## Trạng thái hiện tại — bản tóm tắt

| Hạng mục | Trạng thái | Ghi chú |
|---|---|---|
| App AAOS, UI HVAC/vehicle status/media, mock repository | ✅ | Hai flavor `mock` và `real` build được |
| Voice pipeline: trace, PCM, Silero VAD, grammar NLU, TTS | ✅ | Active runtime đi qua `VoiceAgent` + `GrammarIntentRouter` |
| ASR ba engine chọn lúc chạy (viva-asr / Google / Vosk) | ✅ | `RoutingAsrClient`, mặc định `VIVA` |
| Giọng nói end-to-end trên Device CarSky | ✅ 10/08 | 25 lượt, p50 1336 ms, **p95 1664 ms — vượt ngân sách 1500 ms** |
| Voice → MediaBrowser → MediaSession/ExoPlayer trên Device | ✅ 09/08 | MediaSession đổi trạng thái thật |
| SafetyGuard ở biên `VehicleRepository` | 🟡 | Chặn cả voice và HMI; ablation A1 đo được. Chưa chạy trên VHAL thật |
| Chuỗi nền tảng GPIO → CAN → KUKSA → VHAL push | ✅ 19/08 | Bốn tầng cùng đổi khi kéo slider |
| App đọc lại property qua `CarPropertyManager` | ❌ | Bị chặn bởi `use_local_fake_server` trong image AAOS — [chi tiết](../carsky/09-SU-CO-VA-GIOI-HAN.md) |
| LLM slow path (`/v1/brain/plan`) | 🟡 | Có code + contract test; **build flag mặc định tắt**; chưa benchmark live |
| Benchmark harness + bộ 22 câu | ✅ (công cụ) | `go test ./...` xanh; số đo trên Device có nhưng chưa tách theo chặng |
| `VivaCarService` (service fw riêng) | ❌ | Kế hoạch — contract đã chốt, chưa hiện thực |

⚠️ **Không claim toàn bộ intent đi tới CAN.** Chỉ `hvac_*` và `door_lock` thuộc
đường Vehicle Property; media, volume, delivery, vehicle-status và cabin-lights đi
qua adapter riêng. Xem [05 §1](05-VIVA-BODY.md).

---

## Tài liệu gốc còn hiệu lực

| File | Vai trò |
|---|---|
| [`docs/architecture/VIVA-VOICE-BRAIN-BODY.md`](../architecture/VIVA-VOICE-BRAIN-BODY.md) | Tài liệu kiến trúc chuẩn (baseline 20/08) |
| [`vong2/03-contracts.md`](../../vong2/03-contracts.md) | Hợp đồng interface giữa 4 người: trace, ASR, intent, SafetyGuard, mapping M2 |
| [`docs/decisions/001-…`](../decisions/001-viva-voice-brain-body.md), [`002-…`](../decisions/002-cloud-first-constrained-llm-planner.md) | ADR |
| [`automotive/README.md`](../../automotive/README.md) · [`android/voice`](../../android/voice) | README module Android |
| [`asr/README.md`](../../asr/README.md) | Service ASR |
| [`backend/README.md`](../../backend/README.md) | Harness Go |
| [`docs/dbc/README.md`](../dbc/README.md) | Đối chiếu DBC/VSS thật |
