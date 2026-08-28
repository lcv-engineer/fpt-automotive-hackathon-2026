# 04 — VIVA Brain: hiểu và điều phối

> Code: `android/voice/…/agent/`, `android/voice/…/intent/`,
> `automotive/feature/voice/…/integration/`, `…/data/brain/`.

---

## 1. Một lượt được điều phối thế nào

```text
Transcript(text, acousticConfidence, traceId)
   -> NegationGate.inspect(text)                <- chay TRUOC router, doc chu CO DAU
        Negated -> hoi lai, khong thuc thi
   -> GrammarIntentRouter.route(text)           <- T0, tat dinh
        Matched(intent) | MatchedMany(<=3) | NeedsClarification | Unsupported
   -> [neu Unsupported(canFallback=true) VA co bat flag]
        RemoteLlmAgentPlanner -> POST /v1/brain/plan   <- T2
   -> AppCommandGateway
   -> CoreIntentMapper           intent + slots -> lenh CO KIEU
   -> ExecuteVehicleControlUseCase
   -> VoiceTurnResult (Applied | Denied | ConfirmationRequired | Failed)
```

Router active được Hilt bind trong `VoiceModule` là **`GrammarIntentRouter`**.
Keyword mapping và ONNX semantic matcher (`OnnxEmbeddingIntentMatcher`,
`IntentExemplarCatalog`) có trong repo nhưng **không nằm trên active path** — vì vậy
**đừng gọi runtime là "ba tầng NLU"**.

---

## 2. `Intent` và `RouteResult`

```kotlin
data class Intent(
    val name: String,           // snake_case
    val slots: Map<String, Any>,
    val confidence: Float,
    val tier: Tier,             // T0 = grammar, T1 = classifier, T2 = cloud LLM
)

sealed class RouteResult {
    data class Matched(val intent: Intent) : RouteResult()
    data class MatchedMany(val intents: List<Intent>) : RouteResult()   // 2..MAX_ACTIONS
    data class NeedsClarification(...) : RouteResult()
    data class Unsupported(val promptVi: String, val canFallback: Boolean) : RouteResult()

    companion object { const val MAX_ACTIONS = 3 }
}
```

Câu ghép (`"bật điều hòa và phát nhạc"`) tách theo liên từ và trả `MatchedMany`, tối
đa **3 thao tác**; nhiều hơn thì từ chối và mời chia thành hai lượt. Mỗi intent trong
đó vẫn đi qua gateway và SafetyGuard **riêng**, theo thứ tự người dùng nói.

---

## 3. Danh mục intent hiện có

Đọc từ `GrammarIntentRouter` và `CoreIntentMapper` (16 intent + `unknown`):

| Nhóm | Intent | Slots |
|---|---|---|
| HVAC | `hvac_set_temp` | `value: Float`, `zone: String?` |
| | `hvac_set_fan` | `level: Int` (0–5) |
| Thân xe | `door_lock` | `lock: Boolean` |
| | `cabin_lights` | `on: Boolean` |
| Âm thanh | `volume_adjust` | `delta: Int` |
| Media | `media_play` | `query: String?` |
| | `media_pause` · `media_next` · `media_favorite` | — |
| Trạng thái xe (chỉ đọc) | `vehicle_status_speed` · `vehicle_status_fuel` · `vehicle_status_battery` · `vehicle_status_temperature` | — |
| Giao hàng | `delivery_next_stop` | — |
| | `delivery_order_status` · `delivery_confirm` | `orderId: String?` |
| Fallback | `unknown` | `rawText: String` |

⚠️ Danh mục này **rộng hơn bộ 10 intent lõi** chốt trong `vong2/03-contracts.md` §3
— bốn intent `vehicle_status_*`, `media_favorite` và `cabin_lights` được thêm sau.
Khi trích contract, nhớ nói theo code chứ không theo bản chốt 29/07.

`VoiceIntentNavigator` còn ánh xạ intent sang màn hình trong cabin (`AppRoutes`:
`home`, `hvac`, `media`, `radio`, `status`, `settings`) — nói lệnh HVAC thì màn hình
điều hòa tự mở.

---

## 4. Bốn quyết định thiết kế của `GrammarIntentRouter`

| Quyết định | Vì sao |
|---|---|
| **Bỏ wake phrase ngay trong router** | Cùng một router chạy được với wake-word detector lẫn push-to-talk |
| **Fold dấu tiếng Việt khi so khớp** | Biến thể ASR có/không dấu cùng khớp một luật |
| **Số đọc bằng chữ chỉ mở rộng trong slot extractor** (nhiệt độ, quạt, mã đơn) | Text media query (`"phát bài Em và Trịnh"`) phải giữ nguyên |
| **Guard theo kiểu câu chạy trước luật ghi** | Câu hỏi ánh xạ sang intent **chỉ đọc** khi có thể, thay vì ghi nhầm |

Từ gọi sai (`"Hey Google…"`) trả `Unsupported(canFallback = false)` kèm câu nói rõ
phạm vi: *"Từ gọi của trợ lý là 'Viva ơi' (cũng nhận Vivi/Vi-Vi ơi)."*

---

## 5. `NegationGate` — ba lý do đứng trước router

`"đừng mở cửa"` chứa chuỗi `"mo cua"`; router khớp bằng `contains()` nên đặt cổng
**sau** router thì đã mở khoá cửa thật.

| Quyết định | Vì sao |
|---|---|
| Chạy **trước** `GrammarIntentRouter` | Như trên |
| Đọc chữ **có dấu** | `foldVietnamese` biến cả `"đừng"` lẫn `"dừng"` thành `"dung"` → fold trước khi so sẽ chặn nhầm `"dừng nhạc"` (`media_pause`) |
| So theo **token**, không phải chuỗi con | Tiếng Việt dùng `"không"` làm cả số 0. Corpus có `cmd_fan_0.wav` = *"quạt mức không"* nghĩa là **quạt mức 0**; `contains("khong")` sẽ giết luôn lệnh đó |

---

## 6. LLM slow path — planner bị giới hạn (ADR-002, 20/08)

### Trạng thái

🟡 **Có code, bind vào runtime, nhưng `vivaBrainAgentEnabled` mặc định `false`.**
Chưa có benchmark live ⇒ **không được claim latency/accuracy của LLM**.

### Điều kiện để nó thật sự chạy

Cả bốn phải đủ:

1. Build với `-PvivaBrainAgentEnabled=true`
2. Android có `-PvivaBrainAuthToken=<token>`
3. Server `viva-asr` có **cùng** `VIVA_BRAIN_AUTH_TOKEN`
4. Server có `OPENAI_API_KEY`

Thiếu bất kỳ điều nào phía server → `/v1/brain/plan` trả **`503`**, ASR vẫn chạy bình
thường. Token sai/thiếu → **`401` trước khi gọi provider**.

### Tám ràng buộc của ADR-002

1. `GrammarIntentRouter` là fast path; **chỉ gọi agent khi router trả
   `Unsupported(canFallback = true)`**.
2. `AgentPlanner` chỉ nhận transcript + trace id và **trả dữ liệu** — không được nhận
   `CommandGateway`, repository, VHAL ID hay bất kỳ capability thực thi nào.
3. Model `gpt-5.4-mini-2026-03-17` qua OpenAI Responses API, **ở server `viva-asr`**.
   `OPENAI_API_KEY` **chỉ** nằm phía server, không vào Gradle/BuildConfig/APK.
4. Structured Outputs: `text.format.type=json_schema`, `strict=true`, `store=false`,
   schema allowlist. **Model không được cấp function/tool thực thi.**
5. **Server và Android cùng validate.** Android còn gọi `CoreIntentMapper` để bảo đảm
   proposal ánh xạ được sang action đã có.
6. Proposal hợp lệ gắn `Intent.Tier.T2` rồi đi **đúng đường cũ**:
   `AppCommandGateway → ExecuteVehicleControlUseCase → GuardedVehicleRepository → SafetyGuard`.
7. **Fail closed:** timeout/lỗi/dữ liệu sai → quay về câu `Unsupported` của grammar,
   **không thực thi action**.
8. Bearer token bảo vệ biên triển khai và quota. ⚠️ **Token nằm trong APK nên không
   phải bí mật bền vững** — production vẫn cần HTTPS, rotation, rate limiting.

### Clarification có giới hạn

Clarification từ slow path chỉ mang **một `resume_prefix` dạng enum đóng**, dùng đúng
trong lượt kế tiếp. **App sở hữu chuỗi canonical** dùng để gọi lại planner, nên model
**không thể cài một tiền tố lệnh tuỳ ý** vào ngữ cảnh.

### Vì sao cloud trước, không phải SLM on-device

`onnxruntime-android:1.20.0` đang dùng cho embedding. QNN trên Android cần custom
build với Qualcomm AI Engine Direct SDK và **không kiểm thử được trên emulator**.
Chưa xác nhận SoC/SDK/NPU của thiết bị chung kết ⇒ nhúng SLM QNN ngay là rủi ro tích
hợp không chứng minh được bằng môi trường hiện tại.

---

## 7. Ngữ cảnh nhiều lượt

- **Pending confirmation** cho mở cửa và `delivery_confirm`.
- ⚠️ Câu hỏi xác nhận phải nói luôn **cách trả lời**: grammar **không có intent
  có/không**, nên tài xế nói *"có"* sẽ rơi vào `unknown` và **huỷ luôn câu hỏi**.
  Vì vậy câu hỏi là: *"Bạn có chắc muốn mở khoá cửa không? Nói lại 'mở cửa' để xác
  nhận."*
- Lịch sử lượt thoại lưu vào Room (`VoiceTurnHistoryRepository`,
  `VoiceTurnHistoryRecorder`) kèm engine ASR đã dùng — hiển thị ở `VoiceHistoryScreen`.

---

## 8. Thêm intent mà không sửa grammar core

`GrammarIntentRouter` nhận `GrammarRule` bổ sung ở composition root. Rule chỉ phân
tích câu **đã lowercase, chuẩn hoá dấu câu và bỏ wake phrase**; nó **đề xuất**
`RouteResult`, không được tự thực thi lệnh.

```kotlin
val trunkRule = GrammarRule { command ->
    if (command == "mở cốp") {
        RouteResult.Matched(
            Intent(name = "trunk_open", slots = emptyMap(), confidence = 1.0f, tier = Intent.Tier.T0),
        )
    } else {
        null
    }
}

val router = GrammarIntentRouter(extensionRules = listOf(trunkRule))
```

Sau khi đăng ký rule, phải:

1. Bổ sung mapper/action ở module sở hữu domain,
2. Đưa action qua `AppCommandGateway` và `SafetyGuard`,
3. Thêm test cho parse, slot, deny/confirm và kết quả thực thi.

Extension chạy **sau** toàn bộ core rule và safety pre-filter, nên **không thể ghi đè
intent core** hoặc khôi phục biến thể đã chủ động loại bỏ.
