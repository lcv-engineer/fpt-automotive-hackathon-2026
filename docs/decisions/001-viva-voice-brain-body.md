# ADR-001: Chia VIVA thành Voice, Brain và Body

## Trạng thái

Proposed for review cho kiến trúc logic và phần trình bày. Việc di chuyển package/module vật lý được
hoãn tới sau chung kết.

## Ngày

2026-08-20

## Bối cảnh

VIVA hiện có một luồng chạy được từ mic tới ASR, định tuyến intent, thực thi HVAC/media/delivery,
SafetyGuard, HMI và TTS. Tuy nhiên các capability đang nằm xen giữa `:voice-core`, `:feature:voice`,
`:feature:media` và `:vehicle-service:*`, nên sơ đồ tuyến tính không diễn đạt rõ ba trách nhiệm:

- giao tiếp bằng tiếng nói;
- hiểu, điều phối và quyết định;
- tác động ra xe và các hệ thống đích.

Nghiên cứu ViVi cho thấy trợ lý xe thương mại đang mở rộng từ lệnh giọng nói sang hội thoại GenAI và
cá nhân hóa. VIVA cần có chỗ cắm cho hướng đó nhưng không được biến LLM thành đường vòng qua chính sách
an toàn, cũng không được trình bày capability roadmap như capability đã chạy.

## Quyết định

Chọn ba khối sản phẩm logic:

1. **VIVA Voice** sở hữu biên âm thanh: wake/PTT, capture, VAD, ASR, TTS và audio focus. Voice trả về
   transcript cùng metadata; Voice không quyết định xe phải làm gì.
2. **VIVA Brain** sở hữu một lượt tương tác: ngữ cảnh hội thoại, định tuyến intent, lựa chọn local/cloud,
   tạo `ActionRequest` có kiểu và dựng phản hồi. Runtime hiện tại là orchestrator tất định, chưa phải
   LLM multi-agent.
3. **VIVA Body** sở hữu skill/executor và adapter tới media, delivery, VehicleRepository/VHAL. Mọi lệnh
   ghi xe phải qua SafetyGuard ở biên Body, bất kể nguồn là voice, HMI hay system.

Hai mối quan tâm cắt ngang không được biến thành “khối thứ tư” trên slide:

- **Safety plane:** Brain có thể từ chối/hỏi lại sớm; Body vẫn là nơi cấp quyền cuối cùng. Quyết định
  của LLM, nếu có sau này, chỉ là đề xuất hành động và không phải authorization.
- **Evidence plane:** trace, verdict, latency và readback đi xuyên ba khối để kiểm chứng một lượt chạy.

Luồng hợp đồng mục tiêu:

```text
VoiceInput -> Transcript
Transcript -> Brain -> ActionRequest
ActionRequest -> Body -> ActionResult
ActionResult -> Brain -> Response
Response -> Voice/HMI
```

`ActionRequest` phải có action/slot có kiểu, nguồn, confidence và correlation ID. Không cho phép Brain
truyền chuỗi lệnh, PropertyID do LLM tự sinh, hoặc gọi thẳng `CarPropertyManager`.

## Các phương án đã cân nhắc

### Giữ sơ đồ pipeline tuyến tính

- Ưu: đúng với demo hiện tại, ít thuật ngữ.
- Nhược: không cho thấy ranh giới sản phẩm, ownership và điểm mở rộng GenAI.
- Loại: vẫn giữ pipeline làm sơ đồ sequence, nhưng không dùng nó làm kiến trúc cấp cao duy nhất.

### Đặt SafetyGuard hoàn toàn trong Brain

- Ưu: nhìn giống một “bộ não an toàn”.
- Nhược: HMI/system có thể ghi xe mà không đi qua Brain; một planner lỗi có thể bỏ qua guard.
- Loại: Brain có policy tiền kiểm, Body cưỡng chế guard cuối cùng.

### Cho LLM function-call trực tiếp xuống VHAL

- Ưu: demo agentic nhanh và linh hoạt.
- Nhược: schema/hallucination trở thành lệnh xe; phá nguyên tắc PropertyID không do AI sinh; khó tái lập.
- Loại: LLM chỉ có thể đề xuất tool có allowlist; adapter tất định mới ánh xạ sang property.

### Di chuyển package/module ngay trước chung kết

- Ưu: cây thư mục trông giống slide.
- Nhược: thay đổi lớn, không tạo thêm bằng chứng người dùng và tăng rủi ro build/runtime.
- Hoãn: kiến trúc logic đi trước; migration vật lý chỉ làm sau khi có contract test.

## Hệ quả

- Slide chung kết có câu chuyện ba khối dễ nhớ nhưng vẫn trung thực với code hiện tại.
- `VoiceAgent` hiện là hạt nhân ban đầu của Brain; tên class không đồng nghĩa với GenAI agent.
- `GuardedVehicleRepository` tiếp tục là chốt chặn cuối; không chuyển guard vào voice pipeline.
- Keyword/embedding và cloud fallback chỉ được nói là code/roadmap khi chưa được bind vào active runtime.
- Refactor sau chung kết phải additive, contract-first và giữ đường chạy cũ cho tới khi test tương đương.
