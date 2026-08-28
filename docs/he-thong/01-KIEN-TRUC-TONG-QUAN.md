# 01 — Kiến trúc tổng quan

> Nguồn chuẩn: [`docs/architecture/VIVA-VOICE-BRAIN-BODY.md`](../architecture/VIVA-VOICE-BRAIN-BODY.md)
> và [`ADR-001`](../decisions/001-viva-voice-brain-body.md).

---

## 1. Ba khối logic

```text
┌──────────────────────┐       ┌───────────────────────────┐       ┌──────────────────────────┐
│ VIVA VOICE           │       │ VIVA BRAIN                │       │ VIVA BODY                │
│ wake/PTT · mic · VAD │──────▶│ context · intent routing  │──────▶│ skills · policy enforce  │
│ ASR · TTS · audio    │ text  │ orchestration · response  │ typed │ media · vehicle · VHAL   │
└──────────▲───────────┘       └─────────────┬─────────────┘ action└─────────────┬────────────┘
           │ spoken response                 │ decision trace                    │ result/readback
           └─────────────────────────────────┴───────────────────────────────────┘
```

- **Voice** biến tiếng nói thành văn bản, và văn bản thành tiếng nói.
- **Brain** biến văn bản thành **yêu cầu hành động có kiểu**.
- **Body** kiểm tra quyền thực thi rồi mới chạm tới media hoặc property của xe.

Đây là **ranh giới logic**, không phải tên module Gradle. Module vật lý chưa đổi tên.

---

## 2. Luồng active runtime — dùng khi được hỏi "code chạy thế nào"

```text
Wake/PTT
  -> VoiceAssistantService
  -> Silero VAD capture (VadUtteranceCapture -> PcmSourceAudioCapture -> VadStreamDriver)
  -> RoutingAsrClient [viva-asr HTTP | Google Cloud Speech | Vosk on-device]
  -> VoiceAgent
  -> GrammarIntentRouter
       -> [neu Unsupported + canFallback] RemoteLlmAgentPlanner
          -> viva-asr server /v1/brain/plan -> OpenAI Responses API
  -> AppCommandGateway
  -> CoreIntentMapper
  -> ExecuteVehicleControlUseCase
       -> media/delivery/volume/navigation executor, hoac
       -> GuardedVehicleRepository -> Mock/RealVehicleRepository -> CarPropertyManager/VHAL
  -> VoiceTurnResult
  -> HMI + AndroidTtsSpeaker + LatencyTrace
```

Hai sơ đồ (§1 và §2) trả lời hai mức khác nhau và không mâu thuẫn: §1 khi hỏi *"sản
phẩm được tổ chức ra sao"*, §2 khi hỏi *"code chạy thế nào"*.

---

## 3. Ranh giới cứng — năm ràng buộc không được vi phạm

| Ràng buộc | Vì sao |
|---|---|
| **`Intent` dừng ở biên app/service.** VHAL chỉ nhận `(propertyId, areaId, value)` | VHAL không biết `hvac_set_temp` là gì. Đây là nguyên văn mentor sửa 30/07 |
| **ASR confidence và NLU confidence là hai trường khác nhau** | Trộn hai thứ làm cổng chặn quyết định sai — xem [03 §6](03-VIVA-VOICE.md) |
| **`Action` là sealed/typed allowlist** — không nhận property ID hay shell command do LLM sinh | Nếu không, "thêm GenAI" đồng nghĩa với "trao quyền ghi tuỳ ý xuống xe" |
| **Mọi write vào xe đi qua `VehicleRepository` đã được guard bọc** | Có **ba** nơi ghi property: use case voice, `HvacViewModel`, `VehicleStatusViewModel`. Guard chỉ chắn đường voice thì hai màn hình kia vẫn ghi thẳng |
| **Mọi lượt có `traceId` xuyên suốt** | Nối transcript ↔ intent ↔ verdict ↔ action ↔ readback. Là nguồn dữ liệu duy nhất cho benchmark |

Và một ràng buộc về lời nói: **chỉ phát TTS dạng "Đã…" sau khi tầng thực thi trả
`Applied`.** `Denied`, `ConfirmationRequired`, timeout hay lỗi quyền đều có câu trả
lời riêng và không được giả thành thành công.

---

## 4. Contract giữa ba khối

Ba kiểu tối thiểu, khi refactor thì bắt đầu từ đây
(`docs/architecture/VIVA-VOICE-BRAIN-BODY.md` §4):

```kotlin
data class Transcript(
    val text: String,
    val acousticConfidence: Float?,
    val traceId: String,
)

data class ActionRequest(
    val action: Action,
    val source: CommandSource,
    val nluConfidence: Float?,
    val traceId: String,
)

sealed interface ActionResult {
    data class Applied(val responseData: Map<String, Any?>) : ActionResult
    data class Denied(val rule: String, val reasonVi: String) : ActionResult
    data class NeedsConfirmation(val rule: String, val questionVi: String) : ActionResult
    data class Failed(val code: String) : ActionResult
}
```

---

## 5. Toàn cảnh: từ giọng nói tới CAN

```text
[ Nguoi lai ]
     | tieng noi
     v
[ VIVA VOICE ]  mic -> Silero VAD -> ASR (viva-asr HTTP trong room CarSky)
     | text + acousticConfidence + traceId
     v
[ VIVA BRAIN ]  GrammarIntentRouter (T0)  [-> LLM planner T2, mac dinh tat]
     | Intent(name, slots, confidence, tier)
     v
[ VIVA BODY ]   AppCommandGateway -> CoreIntentMapper -> ExecuteVehicleControlUseCase
     |
     +-- hvac_* / door_lock --> GuardedVehicleRepository -> SafetyGuard -> Real/MockVehicleRepository
     |                                                          |
     |                                                          v
     |                                              CarPropertyManager -> CarService -> VHAL
     |                                                          |
     |                            [ NEN TANG CARSKY ]           v
     |                            IVI Gateway (Lua) -> KUKSA VSS -> BCM Gateway (Lua) -> Body CAN -> vECU
     |
     +-- media_*      --> MediaBrowser/MediaController -> VivaMediaBrowserService -> MediaSession/ExoPlayer
     +-- volume_adjust--> Android audio adapter (CarAudioManager)
     +-- delivery_*   --> DeliverySkill (in-app)
     +-- vehicle_status_* --> doc VehicleStatus, tra loi bang giong noi
     +-- cabin_lights --> vehicle property den cabin
     v
Applied | Denied | ConfirmationRequired | Failed
     v
HMI + TTS (audio focus)
```

⚠️ Phần trong khung `[ NEN TANG CARSKY ]` **không phải code của đội** — script Lua do
nền tảng cấp (`GATEWAY/`). Xem [08](08-EMBEDDED-VA-GATEWAY.md) và
[`docs/carsky/03`](../carsky/03-BLUEPRINT-VA-NODE.md).

---

## 6. GenAI nằm ở đâu

GenAI nằm trong **Brain** dưới dạng **planner bị giới hạn**, không thay Grammar
router cho lệnh lõi:

```text
core vehicle intent ──> deterministic local route ──> Body
free conversation ────> cloud/LLM candidate ───────> response only
LLM tool proposal ────> schema + allowlist + policy ─> Body SafetyGuard ─> adapter
```

Trạng thái thật:

- ✅ Có code: `RemoteLlmAgentPlanner` → `POST /v1/brain/plan` → `gpt-5.4-mini-2026-03-17`
  với Structured Outputs.
- 🟡 **Build flag `vivaBrainAgentEnabled` mặc định `false`.**
- ✅ Chỉ kết quả `Unsupported(canFallback=true)` mới gọi model; proposal hợp lệ quay
  lại **cùng** `AppCommandGateway` và **cùng** SafetyGuard.
- ❌ Chưa có benchmark live → **không được claim latency/accuracy của LLM**.

Keyword mapping và ONNX semantic matcher **không nằm trên active path** — vì vậy
**không gọi runtime là "ba tầng NLU"**.

---

## 7. Điều phải nói đúng khi trình bày

| Nói thế này | Đừng nói thế này |
|---|---|
| "SafetyGuard là **application guardrail**" | "functional safety theo ISO 26262" |
| "Guard đặt ở biên `VehicleRepository`, trong app" | "Guard nằm trong service fw" — `VivaCarService` chưa tồn tại |
| "speech I/O cho Voice, NLP/NLU cho Brain" | "SLP" — không phải cách gọi quen thuộc với BGK |
| "orchestrator tất định làm fast path" | "hệ multi-agent" — chỉ gọi vậy khi thật sự có nhiều agent có vai trò, tool và hand-off quan sát được |
| "`hvac_*` và `door_lock` đi đường Vehicle Property" | "cả 16 intent chạy full-stack tới CAN" |

---

## 8. Giới hạn đã biết của kiến trúc hiện tại

| Giới hạn | Chi tiết |
|---|---|
| `VehicleWriteContext(source = VOICE)` **chưa đồng nhất** | Mới truyền tường minh ở đường mở cửa; một số lệnh HVAC còn dùng default `HMI`. Guard vẫn chạy, nhưng metadata nguồn chưa nhất quán — phải sửa **bằng test** trước khi policy phân nhánh theo source |
| `G1_STALE_STATE` chưa hiện thực đúng nghĩa | Cần snapshot kèm mốc thời gian cùng gốc đồng hồ; hiện chỉ fail-closed khi không đọc được tốc độ |
| App **không có foreground service** trên Device | Voice pipeline sống trong scope Activity → phải giữ app foreground suốt phiên |
| Module Gradle chưa đổi tên theo Voice/Brain/Body | Có chủ đích — không đổi package chỉ để giống sơ đồ |
