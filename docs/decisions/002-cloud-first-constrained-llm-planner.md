# ADR-002: Tích hợp constrained LLM planner theo hướng cloud-first

## Trạng thái

Accepted cho lát cắt tích hợp đầu tiên. Tính năng được compile và bind vào runtime nhưng tắt mặc định;
chỉ hoạt động khi build flag và credential phía server cùng được cấu hình.

## Ngày

2026-08-20

## Bối cảnh

`GrammarIntentRouter` xử lý tốt lệnh lõi có cấu trúc, nhưng không bao phủ paraphrase, câu gián tiếp và
multi-intent. Hai research trong `viva_brain/` đề xuất hybrid fast path + constrained agent slow path,
đồng thời giữ `SafetyGuard` trong Body làm enforcement point cuối cùng.

Repo hiện dùng AAR `onnxruntime-android:1.20.0` cho embedding. Tài liệu ONNX Runtime cho biết QNN trên
Android cần custom build với Qualcomm AI Engine Direct SDK; QNN không kiểm thử được trên emulator.
Vì chưa xác nhận SoC/SDK/NPU của thiết bị chung kết, nhúng SLM QNN ngay sẽ tạo rủi ro tích hợp và không
chứng minh được bằng môi trường hiện tại.

## Quyết định

1. Giữ `GrammarIntentRouter` làm fast path và chỉ gọi agent khi router trả
   `Unsupported(canFallback = true)`.
2. Thêm `AgentPlanner` vào `voice-core`. Interface này chỉ nhận transcript/trace ID và trả dữ liệu;
   không được nhận `CommandGateway`, repository, VHAL ID hoặc capability thực thi.
3. Lát cắt đầu tiên dùng `gpt-5.4-mini-2026-03-17` qua OpenAI Responses API ở server `viva-asr`.
   API key chỉ nằm trong `OPENAI_API_KEY` phía server, không đi vào Gradle, BuildConfig hoặc APK.
4. Dùng Structured Outputs với `text.format.type=json_schema`, `strict=true`, `store=false` và schema
   allowlist. Model không được cấp function/tool thực thi; nó chỉ sinh proposal.
5. Server và Android cùng validate output. Android còn gọi `CoreIntentMapper` để bảo đảm proposal ánh
   xạ được sang action ứng dụng hiện hữu.
6. Proposal hợp lệ được gắn `Intent.Tier.T2`, sau đó đi qua đúng đường cũ:
   `AppCommandGateway -> ExecuteVehicleControlUseCase -> GuardedVehicleRepository -> SafetyGuard`.
7. Feature tắt mặc định bằng `vivaBrainAgentEnabled=false`. Khi model/server timeout, lỗi hoặc trả dữ
   liệu sai, hệ thống fail closed về câu `Unsupported` của grammar; không thực thi action.

OpenAI công bố model này hỗ trợ Responses API và Structured Outputs. Tài liệu Structured Outputs yêu
cầu `additionalProperties=false`, mọi field phải nằm trong schema và khuyến nghị strict mode cho output
có cấu trúc:

- <https://developers.openai.com/api/docs/models/gpt-5.4-mini>
- <https://developers.openai.com/api/docs/guides/structured-outputs>
- <https://developers.openai.com/api/docs/guides/function-calling>

Điều kiện QNN/Android được đối chiếu tại:

- <https://onnxruntime.ai/docs/build/android.html>
- <https://onnxruntime.ai/docs/execution-providers/QNN-ExecutionProvider.html>

## Hợp đồng wire

```text
POST /v1/brain/plan
{
  "text": "trong xe ngột ngạt quá",
  "trace_id": "..."
}

200
{
  "kind": "action | actions | clarification | unsupported",
  "intent_name": "hvac_set_temp | ... | null",
  "value": 22.0,
  "level": null,
  "lock": null,
  "on": null,
  "delta": null,
  "query": null,
  "order_id": null,
  "prompt_vi": null,
  "confidence": 0.91,
  "actions": null
}
```

Các field legacy luôn hiện diện. Structured Output phía provider còn luôn mang field `actions` để schema
strict ổn định; HTTP response chỉ thêm field đó khi thật sự là multi-action. Semantic validator quy định
field nào được khác `null` cho từng intent. Action confidence dưới `0.75` bị từ chối; đây là safety
heuristic chứ không được claim là xác suất đã hiệu chuẩn.

`kind="actions"` dùng `actions` là danh sách 2–3 object action có cùng slot allowlist và confidence
riêng; các field action đơn ở top-level phải `null`. Android thực thi tuần tự theo thứ tự danh sách,
dừng ở deny/confirmation/failure đầu tiên và không tuyên bố rollback những action đã áp dụng. Với các
kind khác server bỏ field `actions=null` khỏi HTTP response để giữ shape v1 cũ cho client hiện hữu.

## Các phương án đã cân nhắc

### Nhúng ONNX Runtime GenAI + QNN ngay trong APK

- Ưu: offline, không gửi transcript ra cloud.
- Nhược: cần QNN SDK/custom AAR, model conversion, NDK và phần cứng Qualcomm thật; emulator không chứng
  minh được đường NPU.
- Hoãn: giữ `AgentPlanner` làm seam để thay remote adapter bằng on-device adapter khi có thiết bị mục tiêu.

### Gọi OpenAI trực tiếp từ Android

- Ưu: ít code server.
- Nhược: API key có thể bị trích xuất khỏi APK; khó kiểm soát rate/cost và thay provider.
- Loại: mọi credential và provider call nằm ở server.

### Cho model function-call trực tiếp vào Body

- Ưu: ít bước ánh xạ.
- Nhược: trao capability thực thi cho model và làm mờ enforcement boundary.
- Loại: model chỉ trả proposal; gateway và SafetyGuard hiện hữu giữ nguyên.

### Thay Grammar router bằng LLM

- Ưu: một đường NLU duy nhất.
- Nhược: tăng latency, cost và phụ thuộc mạng cho cả lệnh lõi vốn đang chạy tất định.
- Loại: fast path luôn chạy trước.

## Hệ quả

- Dự án có model LLM thật trên slow path mà không đổi đường an toàn của Body.
- Lệnh lõi vẫn không phát sinh LLM inference/cost.
- Slow path phụ thuộc mạng và OpenAI account; chưa được phép claim latency/accuracy cho đến khi chạy với
  API key thật và benchmark trên thiết bị/room chung kết.
- `store=false` giảm lưu application state phía Responses API, nhưng transcript vẫn rời thiết bị khi
  slow path được bật; đây phải là điều kiện consent/privacy khi sản phẩm hóa.
- Server `viva-asr` nay đồng thời làm gateway thử nghiệm cho Brain. Nếu scale độc lập, tách endpoint sang
  service riêng nhưng giữ nguyên wire contract.
