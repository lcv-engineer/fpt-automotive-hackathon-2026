# Kiến trúc VIVA — Voice, Brain, Body

> **Baseline:** code tại workspace ngày 20/08/2026. Tài liệu này phân biệt rõ **đang chạy** và
> **kiến trúc đích**; roadmap không được dùng làm bằng chứng runtime.

## 1. Tóm tắt cho chung kết

VIVA là trợ lý khoang lái native trên AAOS, được chia thành ba khối logic:

```text
┌──────────────────────┐       ┌───────────────────────────┐       ┌──────────────────────────┐
│ VIVA VOICE           │       │ VIVA BRAIN                │       │ VIVA BODY                │
│ wake/PTT · mic · VAD │──────▶│ context · intent routing  │──────▶│ skills · policy enforce  │
│ ASR · TTS · audio    │ text  │ orchestration · response  │ typed │ media · vehicle · VHAL   │
└──────────▲───────────┘       └─────────────┬─────────────┘ action└─────────────┬────────────┘
           │ spoken response                 │ decision trace                    │ result/readback
           └─────────────────────────────────┴───────────────────────────────────┘
```

Một câu nói đi qua Voice để thành văn bản; Brain biến văn bản thành yêu cầu hành động có kiểu; Body
kiểm tra quyền thực thi rồi mới chạm tới media hoặc property của xe. Kết quả quay lại Brain để tạo phản
hồi, sau đó Voice đọc và HMI hiển thị.

**Thông điệp an toàn:** Brain có thể thông minh hơn theo thời gian; quyền ghi xuống xe không đi theo độ
thông minh đó. SafetyGuard ở biên Body vẫn là chốt cuối cùng.

Về thuật ngữ, không dùng “SLP” trên slide nếu ý muốn nói xử lý ngôn ngữ. Dùng **speech I/O** cho Voice
và **NLP/NLU** cho Brain; đây là hai trách nhiệm khác nhau và là cách gọi quen thuộc hơn với BGK.

## 2. VIVA phiên bản hiện tại — as-built

### 2.1 VIVA Voice

| Capability đang chạy | Hiện thực |
|---|---|
| Wake word/PTT và vòng đời phiên thoại | `VoiceAssistantService`, `HotwordGate`, lớp `via/*` |
| Capture và end-pointing | `VadUtteranceCapture` -> `PcmSourceAudioCapture` -> `VadStreamDriver`; một lazy Silero ONNX session dùng lại giữa các lượt |
| ASR có thể chọn | `RoutingAsrClient`: viva-asr HTTP hoặc Google Cloud Speech theo Settings |
| Phản hồi tiếng nói | `AndroidTtsSpeaker`, audio focus/ducking |
| Đo từng chặng | `LatencyTrace`, `VIVA_TRACE`/`VIVA_VOICE` |

Lưu ý: README cũ còn mô tả Vosk/offline, nhưng active binding hiện tại là `RoutingAsrClient`; không có
Vosk client được bind vào `VoiceAgent`.

### 2.2 VIVA Brain

| Capability đang chạy | Hiện thực |
|---|---|
| Điều phối một lượt | `VoiceAgent.handleAudio/handleText` |
| Intent router active | `GrammarIntentRouter` được Hilt bind trong `VoiceModule` |
| LLM slow path tùy chọn | `RemoteLlmAgentPlanner` -> `/v1/brain/plan` -> `gpt-5.4-mini-2026-03-17`; build flag mặc định tắt |
| Hỏi lại/từ chối/thực thi | `RouteResult` và `VoiceTurnStatus` |
| Ánh xạ action có kiểu | `CoreIntentMapper` -> `VehicleIntent`/media/delivery command |
| Điều phối thực thi | `AppCommandGateway` -> `ExecuteVehicleControlUseCase` |
| Ngữ cảnh ngắn nhiều lượt | pending confirmation cho mở cửa và delivery |

Brain vẫn lấy **orchestrator tất định làm fast path** và chưa phải hệ multi-agent. Từ ngày 20/08/2026,
repo có thêm constrained LLM slow path thật được bind vào `VoiceAgent`, nhưng mặc định tắt. Khi build với
`vivaBrainAgentEnabled=true`, Android có deployment bearer token và server có cùng token cùng
`OPENAI_API_KEY`, chỉ kết quả
`Unsupported(canFallback=true)` mới gọi model; proposal T2 hợp lệ quay lại cùng `AppCommandGateway`.
Planner hiện có thể trả tối đa ba proposal có kiểu cho một câu ghép. Mỗi proposal vẫn đi qua gateway
và SafetyGuard riêng, theo thứ tự người dùng nói; không có model/agent thứ hai và không có quyền thực
thi trực tiếp trong Brain.
Endpoint planner fail closed trước khi gọi model nếu thiếu/sai bearer token. Đây là access control ở
biên triển khai để bảo vệ quota và proposal endpoint; token nằm trong APK nên không thay thế HTTPS,
rate limit hoặc cơ chế attestation nếu đưa ra môi trường không tin cậy.
Clarification từ slow path chỉ giữ một `resume_prefix` dạng enum trong đúng lượt kế tiếp; app sở hữu
chuỗi canonical dùng để gọi lại planner, nên model không thể cài một tiền tố lệnh tùy ý vào context.
Keyword mapping và ONNX semantic matcher vẫn không nằm trên active path này, vì vậy không gọi runtime là
“ba tầng NLU”. Không được claim live latency/accuracy của LLM cho tới khi có smoke/benchmark bằng key và
thiết bị thật.

### 2.3 VIVA Body

| Capability đang chạy | Hiện thực |
|---|---|
| Vehicle control/query | `ExecuteVehicleControlUseCase` -> `VehicleRepository` |
| Chốt an toàn cuối | `GuardedVehicleRepository` + `DefaultSafetyGuard` |
| Xe mock/real | `MockVehicleRepository` hoặc `RealVehicleRepository` theo flavor |
| Media | `MediaRepository`, `MediaCommandExecutor`, volume controller |
| Delivery | `DeliverySkill` và repository |
| Hạ tầng xe | CarProperty/VHAL; bên ngoài app là KUKSA/VSS, CAN, gateway và ECU mô phỏng |

Guard bọc cả mock và real repository tại DI boundary. Đây là lý do lệnh từ HMI cũng không có đường vòng
qua SafetyGuard. Guard này là application guardrail, **không phải** functional safety theo ISO 26262.

Giới hạn hiện tại: `VehicleWriteContext(source = VOICE)` mới được truyền tường minh ở đường mở cửa;
một số lệnh HVAC còn dùng default `HMI`. Guard vẫn chạy, nhưng metadata nguồn chưa đồng nhất. Đây là
việc cần sửa bằng test trước khi policy bắt đầu phân nhánh theo source.

## 3. Luồng active runtime

```text
Wake/PTT
  -> VoiceAssistantService
  -> Silero VAD capture
  -> RoutingAsrClient [viva-asr HTTP | Google]
  -> VoiceAgent
  -> GrammarIntentRouter
       -> [nếu Unsupported + canFallback] RemoteLlmAgentPlanner
          -> viva server /v1/brain/plan -> OpenAI Responses API
  -> AppCommandGateway
  -> CoreIntentMapper
  -> ExecuteVehicleControlUseCase
       -> media/delivery executor, hoặc
       -> GuardedVehicleRepository -> Mock/RealVehicleRepository -> CarProperty/VHAL
  -> VoiceTurnResult
  -> HMI + AndroidTtsSpeaker + trace
```

Đây là sơ đồ dùng khi BGK hỏi “code chạy thế nào”. Sơ đồ ba khối ở §1 dùng khi hỏi “sản phẩm được tổ
chức ra sao”. Hai sơ đồ trả lời hai mức khác nhau và không mâu thuẫn.

## 4. Hợp đồng giữa ba khối

Chưa cần tạo framework agent mới trước chung kết. Khi refactor code, bắt đầu bằng ba contract tối thiểu:

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

Ràng buộc cứng:

- ASR confidence và NLU confidence là hai trường khác nhau.
- `Action` là sealed/typed allowlist; không nhận property ID hoặc shell command do LLM sinh.
- Body trả structured result; Brain quyết định câu diễn đạt, không suy đoán Body đã thực thi.
- Mọi write vào xe đi qua `VehicleRepository` đã được guard bọc.
- Mọi lượt có `traceId` xuyên suốt để nối transcript, intent, verdict, action và readback.

## 5. GenAI/agent nên được thêm ở đâu

GenAI nằm trong Brain dưới dạng **planner bị giới hạn**, không thay Grammar router cho lệnh lõi. Lát cắt
cloud đầu tiên đã có code và feature flag; on-device SLM, RAG và multi-agent vẫn là roadmap:

```text
core vehicle intent ──> deterministic local route ──> Body
free conversation ────> cloud/LLM candidate ───────> response only
LLM tool proposal ────> schema + allowlist + policy ─> Body SafetyGuard ─> adapter
```

Roadmap hợp lý:

1. Giữ toàn bộ lệnh xe lõi local và tất định.
2. Thêm context store/pending clarification để hoàn thành hội thoại nhiều lượt.
3. Đã thêm cloud fallback có contract test để LLM đề xuất intent trong allowlist; Body vẫn tái kiểm tra.
4. Benchmark live, hoàn thiện consent/privacy và mở rộng QA/RAG sau khi slow path ổn định.
5. Chỉ gọi là “multi-agent” khi thực sự có nhiều agent có vai trò, tool và hand-off quan sát được.

## 6. Học từ ViVi, nhưng không sao chép claim chưa có nguồn

Nguồn chính thức xác nhận ViVi 2.0 tích hợp GenAI, công bố hơn 30.000 giờ dữ liệu thoại và khả năng
nhận diện tiếng Việt trên 98%; VinFast 3.0 được mô tả có hội thoại tự nhiên, hiểu ngữ cảnh và cá nhân
hóa, nằm trong các gói VF Connect nâng cao. Các nguồn này ủng hộ hướng tách speech foundation khỏi
Brain mở rộng, nhưng **không công bố đủ chi tiết để kết luận** topology nội bộ, vị trí guard, giao thức
CAN hay cơ chế multi-agent của ViVi.

- [ViVi 2.0 — VinBigdata](https://vivi.vinbigdata.com/)
- [Trợ lý ảo ViVi tích hợp AI tạo sinh — VinBigdata](https://vinbigdata.com/case-study/tro-ly-ao-vivi-mang-den-trai-nghiem-thoai-mai-va-ly-thu-tren-o-to-dien-vf-e34.html)
- [VF Connect và Trợ lý ảo VinFast 3.0 — VinFast](https://vinfastauto.com/vn_vi/vinfast-ra-mat-goi-dich-vu-thong-minh-vf-connect-nang-tam-trai-nghiem-ca-nhan-hoa-tren-xe-dien)

Không đưa WER, latency, edge/cloud topology hoặc nguyên nhân định giá từ file nghiên cứu lên slide nếu
chưa có nguồn sơ cấp tương ứng.

## 7. Câu nói 40 giây

> “VIVA được chia thành ba phần. Voice chịu trách nhiệm nghe và nói: wake word, VAD, ASR và TTS. Brain
> hiểu câu nói, giữ ngữ cảnh và tạo một yêu cầu hành động có kiểu. Body là nơi thực thi media hoặc giao
> tiếp với xe. Điểm quan trọng là Brain không được ghi thẳng xuống VHAL: mọi lệnh xe, kể cả từ giọng nói
> hay màn hình, đều bị SafetyGuard kiểm tra ở biên Body. Vì vậy sau này chúng em có thể làm Brain thông
> minh hơn bằng GenAI mà không trao cho AI quyền bỏ qua tầng an toàn.”

## 8. Phạm vi refactor sau chung kết

Không đổi package chỉ để giống sơ đồ. Thứ tự migration:

1. Chốt contract và contract test cho `Transcript`, `ActionRequest`, `ActionResult`.
2. Đổi tên/bao `VoiceAgent` thành Brain orchestrator mà không đổi behavior.
3. Tách voice I/O khỏi intent routing trong dependency graph.
4. Gom skill adapters dưới Body facade; giữ `GuardedVehicleRepository` làm enforcement point.
5. Chỉ sau khi full test + real-flavor smoke pass mới xóa đường cũ.

Quyết định và trade-off đầy đủ nằm tại
[`ADR-001`](../decisions/001-viva-voice-brain-body.md).
