# Log mẫu `VIVA_TRACE` — bàn giao cho harness (L2b → V8)

Hai file này là **đầu vào test cho harness của Vĩ**, không phải log chạy thật.
Chúng được sinh ra từ đúng luật format mà `LatencyTrace.kt` hiện thực, và đã được
kiểm lại bằng đúng semantics của `backend/internal/domain/parse.go` +
`backend/internal/usecase/harness/aggregate.go`.

Chạy thử:

```bash
cd backend
go run ./cmd/viva-tools harness report --input ../android/voice/fixtures/golden_trace.log --out report.csv
```

> Hai file này cũng được `go test ./internal/usecase/harness` khẳng định trực tiếp
> (`golden_test.go`): số trace, số warning, mốc hợp lệ còn nguyên sau dòng hỏng, và
> `e2e_ms` khai trong summary khớp `tts_start − speech_end` trong 0.5ms. Format lệch
> ở một trong hai phía sẽ **fail `go test`** thay vì lộ ra lúc chạy benchmark.

## `golden_trace.log` — đường hạnh phúc, 4 lượt

Có prefix logcat thật (`07-29 21:14:07.000  4821  4821 I VIVA_TRACE: …`) và dòng log của
module khác xen giữa, vì parser tìm marker chứ không neo ở cột 0.

| traceId (…000N) | Câu | Intent | Verdict | Ghi chú |
|---|---|---|---|---|
| `…0001` | hạ điều hòa xuống 22 độ | `hvac_set_temp` | `Allow` | Đủ **cả 9 mốc** — lượt chuẩn |
| `…0002` | mở cửa | `door_lock` | `Deny:G1_SPEED_LOCK` | **Không có `exec_done`** — bị chặn nên không thực thi |
| `…0003` | xác nhận giao thành công đơn A12 | `delivery_confirm` | `Confirm:G2_CONFIRM_DELIVERY` | Chờ tài xế xác nhận |
| `…0004` | *(rỗng → `-`)* | `unknown` | `Error:asr_done` | ASR timeout 3s — lượt hỏng vẫn khai chết ở đâu, và khai **`e2e_ms=3013`** |

Kỳ vọng: **4 trace, 0 warning.** `e2e_ms` khai trong summary phải khớp
`tts_start − speech_end` tính lại từ mốc thô (sai số < 0.5ms do làm tròn số nguyên).

> ⚠️ Để ý lượt `…0004`: nó **không có** `tts_start`, và `e2e_ms` của nó là **3013**, không
> phải 13. Lượt chết giữa chừng tính tới **lúc khai lỗi**, không phải tới mốc cuối cùng ghi
> được. Nếu tính tới mốc cuối thì lượt này báo 13ms — nhanh nhất cả file — và **lượt fail
> sẽ kéo p95 xuống**, tức là càng hỏng càng đẹp số. Đừng lọc lượt `Error:` ra khỏi p95 mà
> không nói rõ trong write-up.

## `golden_trace_edge.log` — ca biên, đây là bài kiểm

| Trace | Kiểm cái gì |
|---|---|
| `edge-pipe-in-utterance` | Câu chứa `\|` — emitter đã đổi thành `/`. `intent` **phải vẫn** là `door_lock`, không bị đẩy field |
| `edge-empty-utterance` | Câu rỗng → `-`, vẫn đủ 5 field |
| `edge-overlong-utterance` | Câu 700+ ký tự → cắt còn **đúng 200** |
| `edge-newline-in-utterance` | Newline → dấu cách, **không** tách thành 2 dòng |
| `edge-abandoned-no-summary` | Lượt bỏ dở: có 3 mốc, **không có summary** → phải giữ lại chứ không bỏ |
| `edge-low-confidence` | `Confirm:G3_LOW_CONFIDENCE` |
| `edge-malformed` | **4 dòng cố tình hỏng** + 1 dòng hợp lệ |

Kỳ vọng cho `edge-malformed`:

- đúng **4 warning** (nanos không phải số · thiếu field ở dòng event · thiếu field ở dòng
  summary · sai prefix `e2e_ms=`);
- **không crash**;
- mốc hợp lệ `nlu_done` của cùng `traceId` đó **vẫn còn** — dòng hỏng không được kéo theo
  dữ liệu tốt.

Dòng cuối file là log của tag khác, phải bị bỏ qua im lặng.

## Sinh lại khi format đổi

Đừng sửa tay hai file này. Format là của `LatencyTrace.kt`; khi nó đổi thì đổi contract
`vong2/03-contracts.md` §1 trước, rồi sinh lại fixture, rồi báo Vĩ. Sửa tay là cách để
fixture và code lệch nhau mà không ai biết.
