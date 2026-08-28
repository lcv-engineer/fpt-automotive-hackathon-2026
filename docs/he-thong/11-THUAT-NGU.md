# 11 — Thuật ngữ và chỉ mục ký hiệu trong code

> 📖 **Từ điển khái niệm đầy đủ nằm ở
> [`vong2/33-THUAT-NGU-GIAI-THICH.md`](../../vong2/33-THUAT-NGU-GIAI-THICH.md)**
> (376 dòng, sắp theo "bốn thế giới": nền tảng CarSky · xe ảo · app VIVA · bằng
> chứng). Đừng chép lại nó ở đây — file này chỉ là **chỉ mục tra nhanh** và **bản đồ
> từ khái niệm sang tên trong code**.
>
> **Quy tắc vàng khi bí:** hỏi *"từ này thuộc thế giới nào?"*. Ví dụ "node" ở thế
> giới CarSky là một máy ảo/container trong room — không liên quan gì tới "node" của
> Node.js.

---

## 1. Khái niệm → tên trong code

| Khái niệm | Tên thật trong repo |
|---|---|
| Cắt câu bằng VAD | `VadUtteranceCapture`, `VadStreamDriver`, `SileroVadOnnxScorer` |
| Chọn engine ASR | `RoutingAsrClient`, `SettingsDataStore.asrEngine`, `AsrEngine.{VIVA,GOOGLE,VOSK}` |
| Gọi service ASR | `HttpAsrClient` → `POST /asr` |
| Cổng phủ định | `NegationGate` |
| NLU tất định (T0) | `GrammarIntentRouter` |
| Kết quả định tuyến | `RouteResult.{Matched, MatchedMany, NeedsClarification, Unsupported}` |
| LLM slow path (T2) | `RemoteLlmAgentPlanner` → `POST /v1/brain/plan` |
| Điều phối một lượt | `VoiceAgent` |
| Dịch intent sang lệnh có kiểu | `CoreIntentMapper` |
| Cổng thực thi | `AppCommandGateway`, `ExecuteVehicleControlUseCase` |
| Tầng an toàn | `SafetyGuard`, `DefaultSafetyGuard`, `GuardedVehicleRepository` |
| Phán quyết | `Verdict.{Allow, Deny, Confirm}`, `SafetyRules.*` |
| Kho xe | `VehicleRepository` → `RealVehicleRepository` \| `MockVehicleRepository` |
| Hằng số property | `VehicleProperties.*` (`HVAC_TEMPERATURE_SET = 358614275`, …) |
| Đo latency | `LatencyTrace`, `Stage`, `TraceVerdict` |
| Ngưỡng confidence âm học | `VoiceTurnReport.MIN_ACOUSTIC_CONFIDENCE`, khoá `viva_min_conf` |
| Nói | `AndroidTtsSpeaker`, `AndroidAudioFocusController`, `PrerenderedPrompts` |
| Wake word | `HotwordGate`, `HotwordConstants.KEYPHRASE = "Viva ơi"` |
| Lịch sử lượt thoại | `VoiceTurnHistoryRepository`, `VoiceHistoryScreen` |
| Điều hướng cabin theo lệnh | `VoiceIntentNavigator`, `AppRoutes` |

---

## 2. Ba khối logic ↔ module vật lý

| Khối logic | Nằm ở module nào |
|---|---|
| **VIVA Voice** | `:voice-core` (`voice/audio`, `voice/asr`, `voice/tts`, `voice/hotword`), `:feature:voice/data/*` |
| **VIVA Brain** | `:voice-core` (`voice/agent`, `voice/intent`), `:feature:voice/integration`, `:feature:voice/data/brain` |
| **VIVA Body** | `:vehicle-service:api`, `:vehicle-service:impl`, `:feature:media`, `:feature:voice/domain` |

⚠️ Module Gradle **chưa đổi tên** theo ba khối — có chủ đích. Đây là ranh giới logic.

---

## 3. Mã luật an toàn

| Mã | Nghĩa | Tầng áp dụng |
|---|---|---|
| `G1_SPEED_LOCK` | Tốc độ > 5 km/h → từ chối mở khoá cửa | `DefaultSafetyGuard` |
| `G1_GEAR_LOCK` | Số ≠ P → từ chối mở khoá (chỉ chạy khi đọc được số) | `DefaultSafetyGuard` |
| `G1_STALE_STATE` | Không đọc được trạng thái → fail closed | `DefaultSafetyGuard` (chưa đủ nghĩa "cũ quá") |
| `G2_CONFIRM_DOOR` | Mở khoá luôn phải hỏi lại | `DefaultSafetyGuard` |
| `G2_CONFIRM_DELIVERY` | Xác nhận giao hàng | `DeliverySkill` |
| `G3_LOW_CONFIDENCE` | ASR confidence < 0.6 | `DefaultSafetyGuard` + `VoiceTurnReport` |
| `G3_VALUE_RANGE` | Ngoài `16..32 °C`, hoặc `NaN` | `DefaultSafetyGuard` |
| `G3_MISSING_SLOT`, `G3_UNSUPPORTED` | Thiếu slot / ngoài phạm vi | `GrammarIntentRouter` |
| `G3_LLM_WHITELIST` | Proposal T2 ngoài allowlist | schema + `CoreIntentMapper` ở tầng planner |

---

## 4. Chín mốc trace

```
speech_start · speech_end · asr_sent · asr_done · nlu_done
guard_done · exec_done · render_done · tts_start
```

`e2e_ms = speech_end → tts_start`. Xem [07](07-BACKEND-HARNESS.md).

---

## 5. Từ hay bị dùng sai

| Đừng nói | Nói thế này |
|---|---|
| "SLP" | **speech I/O** (Voice) và **NLP/NLU** (Brain) — hai trách nhiệm khác nhau |
| "hệ multi-agent" | **orchestrator tất định làm fast path**; chỉ gọi multi-agent khi thật sự có nhiều agent có vai trò, tool và hand-off quan sát được |
| "ba tầng NLU" | Active path chỉ có **`GrammarIntentRouter`**; keyword mapping và ONNX matcher **không được bind** |
| "functional safety" | **application guardrail** — SafetyGuard không phải ISO 26262 |
| "confidence của model" | **xấp xỉ** `exp(avg_logprob)` có trọng số theo thời lượng |
| "end-to-end" cho `*_incl_speech` | `e2e_computed` = `speech_end → tts_start` mới là end-to-end |
| "SafetyGuard nằm trong service fw" | Nằm ở **biên `VehicleRepository`, trong app**; `VivaCarService` chưa tồn tại |
| "guard của nền tảng là của đội" | `GATEWAY/*.lua` G1.1–G1.3 là **của CarSky**, ngưỡng cũng khác |

---

## 6. Viết tắt hay gặp

| Viết tắt | Nghĩa |
|---|---|
| AAOS | Android Automotive OS |
| VHAL | Vehicle Hardware Abstraction Layer |
| VSS | Vehicle Signal Specification (COVESA) |
| KUKSA | Databroker VSS mã nguồn mở |
| DBC | Định dạng mô tả tín hiệu CAN |
| CAN | Controller Area Network |
| ECU / CCU | Electronic / Central Control Unit |
| BCM · PWT · VCU · BMS · TCU · IVI | Body Control Module · Powertrain · Vehicle Control Unit · Battery Management System · Telematics Control Unit · In-Vehicle Infotainment |
| UDS / DTC | Unified Diagnostic Services (ISO 14229) / Diagnostic Trouble Code |
| SOME/IP | Giao thức middleware ô tô |
| VAD | Voice Activity Detection |
| ASR / TTS | Nhận dạng / tổng hợp giọng nói |
| NLU | Natural Language Understanding |
| RTF | Real-Time Factor |
| WER | Word Error Rate |
| PTT | Push-to-talk |
