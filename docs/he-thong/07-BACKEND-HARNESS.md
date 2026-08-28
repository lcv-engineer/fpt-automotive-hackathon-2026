# 07 — Backend harness `viva-tools` và cách đo

> Code: `backend/`. README gốc: [`backend/README.md`](../../backend/README.md).
> Contract log: [`vong2/03-contracts.md`](../../vong2/03-contracts.md) §1.

`viva-tools` là CLI Go gồm **benchmark harness** và **CarSky devops helper**.
Nó **không** phải Command Dispatcher / skills API — logic đó nằm trong app Kotlin.
**Không có dependency ngoài Go standard library.**

---

## 1. Định dạng log — hợp đồng giữa app và harness

App in đúng hai dạng dòng, harness parse ở `backend/internal/domain/parse.go`:

```text
VIVA_TRACE|<traceId>|<stage>|<elapsedRealtimeNanos>
VIVA_TRACE_SUMMARY|<traceId>|<utterance>|<intent>|<verdict>|e2e_ms=<so nguyen>
```

### Chín mốc chuẩn — không đặt tên khác

| Mốc | Ai gọi | Nghĩa |
|---|---|---|
| `speech_start` | VadSegmenter | VAD phát hiện bắt đầu có tiếng nói |
| `speech_end` | VadSegmenter | VAD xác định đã nói xong (endpoint) |
| `asr_sent` | AsrClient | Đã gửi audio đi |
| `asr_done` | AsrClient | Đã nhận text về |
| `nlu_done` | IntentRouter | Đã ra intent |
| `guard_done` | SafetyGuard | Đã có phán quyết |
| `exec_done` | Skill | Hành động đã thực thi xong |
| `render_done` | HMI | Frame đầu tiên phản ánh trạng thái mới |
| `tts_start` | TtsSpeaker | Bắt đầu phát tiếng |

### Sáu luật giữ cho dòng không vỡ — `LatencyTrace` tự làm

| Luật | Vì sao |
|---|---|
| `\|` trong text → `/` | Parser tách theo **số field cố định**. Một dấu `\|` lọt vào `utterance` đẩy `intent` sang ô `verdict` → cả dòng bị loại. Text từ ASR không được tin là sạch |
| xuống dòng / tab → dấu cách | Newline cắt 1 dòng summary thành 2, không nửa nào parse được |
| field rỗng → `-` | Giữ đủ số field |
| `utterance` cắt ở 200 ký tự | logcat cắt message ở ~4000 byte; summary bị cắt = không parse được |
| `e2e_ms` là **số nguyên** | 🔴 Máy chạy locale `vi-VN` thì `String.format("%.1f")` ra `690,0`, `ParseFloat` phía Go **từ chối** → mọi dòng summary hỏng, **chỉ hỏng trên máy thật, ngay lúc demo** |
| Mỗi `stage` in **1 lần** (ghi đè = bỏ qua) | Mốc đánh 2 lần sẽ **rút ngắn** đoạn đo và làm p95 đẹp giả |

⚠️ **Ngoại lệ có chủ đích:** ô `utterance` **giữ nguyên dấu tiếng Việt** (khác quy
tắc "không dấu trong `Log.i`"), vì nó là **bằng chứng** — mất dấu thì không đối chiếu
được với ground truth. Đọc log trên Windows: `chcp 65001` trước khi `adb logcat`.

### Ngữ pháp `<verdict>`

```text
verdict := "Allow" | "Deny:"<RULE_ID> | "Confirm:"<RULE_ID> | "Error:"<STAGE_ID>
```

Tách bằng dấu `:` **đầu tiên**. Ba quyết định phía sau:

| Quyết định | Lý do |
|---|---|
| Mã luật đi kèm, không phải `Deny` trơn | Ablation A1 phải ra bảng before/after. Có mã luật thì đó là **một câu group-by trên CSV**; không có thì phải chạy tay lại demo rồi đọc logcat |
| Thêm `Error:<stage>` | Lượt chết giữa chừng **không bao giờ tới SafetyGuard** → trước đây không có dòng summary và **biến mất khỏi benchmark**. Giờ nó khai chết ở chặng nào |
| `reasonVi`/`questionVi`/`suggestion` **không** vào log | Là câu tiếng Việt đọc cho tài xế, cần escape, máy không đọc được. **Mã luật là khoá join** |

---

## 2. 🔴 "End-to-end" nghĩa là gì — định nghĩa duy nhất

```
e2e_ms = speech_end -> tts_start
```

Đây là metric mà cam kết **p95 < 1500 ms** được đặt lên. Harness báo nó là
`e2e_computed`.

| Metric | Nghĩa | Được trích không? |
|---|---|---|
| `e2e_computed` | `speech_end → tts_start` | ✅ **đây là con số** |
| `screen_latency` | `speech_end → render_done` | ✅ khi nói về màn hình |
| `*_incl_speech` | bắt đầu từ `speech_start` → **cộng cả thời gian tài xế nói** | 🚫 **KHÔNG được trích là end-to-end**. Giữ lại chỉ để nối tiếp báo cáo cũ |
| `e2e_reported_minus_computed` | đối chiếu số app tự khai với số harness tính lại | Spread khác 0 ⇒ hai định nghĩa đã trôi khỏi nhau |

**Lượt không có `tts_start`** (chết giữa chừng) tính tới **lúc khai lỗi**, không phải
tới mốc cuối cùng ghi được. Lý do: lượt kẹt 3 s ở ASR timeout mà tính tới mốc cuối sẽ
báo ~10 ms — nhanh nhất cả bộ — và **lượt fail sẽ kéo p95 xuống**. Càng hỏng càng đẹp
số là hỏng cách đo.

---

## 3. Ba lệnh harness

### `harness report` — thống kê latency từng chặng

```bash
go run ./cmd/viva-tools harness report --input path/to/log.txt --out report.csv
```

```bash
go run ./cmd/viva-tools harness report --adb --serial <device-serial> --out report.csv
```

```bash
go run ./cmd/viva-tools harness report --input run.log --variant full --out report.csv --per-trace traces.csv --verdicts verdicts.csv
```

p50/p95/min/max trên mọi đoạn liền kề, cộng `e2e_ms` app tự khai để đối chiếu.

### `harness compare` — bảng before/after cho ablation

```bash
go run ./cmd/viva-tools harness compare --baseline full.log --candidate no_guard.log --baseline-label guard_on --candidate-label guard_off --out compare.csv --verdicts-out verdicts_compare.csv
```

Metric chỉ tồn tại ở một lần chạy vẫn được in, đánh dấu `not comparable` — **trong
ablation thì sự biến mất chính là phát hiện** (bỏ callback `VhalRepository` thì
`hmi_render` không bao giờ bắn).

### `harness verify` — PASS/FAIL từng câu

```bash
go run ./cmd/viva-tools harness verify --suite suites/benchmark_v1.csv --input run.log --variant quiet --out results.csv --summary-out summary.csv --evidence-dir screenshots/
```

`suites/benchmark_v1.csv` là bộ **22 câu**: 10 intent lõi của `03-contracts.md` §3,
5 tình huống phức tạp M7, wake phrase sai, và một trong năm lệnh đã cắt 29/07.

| Cột | Nghĩa |
|---|---|
| `id` | Mã ca ổn định; cũng là stem tên file để join evidence |
| `utterance` | Câu người vận hành demo nói |
| `expect_intent` / `expect_verdict` | Hành vi đích, theo ngữ pháp verdict §1. `Deny` trơn nhận mọi rule; `Deny:G1_SPEED_LOCK` ghim đúng rule |
| `evidence_id` | Stem ảnh chụp tra dưới `--evidence-dir` |
| `gate` | Khác rỗng = đang chờ việc chưa xong. Fail ở đây là **known gap**, đếm riêng và **không làm run đỏ** |

**Hai chế độ khớp:**

- `--match order` (mặc định) — ghép ca thứ N với lượt thứ N. Vì `utterance` trong log
  là **thứ ASR nghe được**, không phải thứ được nói; khớp theo text sẽ **âm thầm bỏ
  mọi lượt nhận sai** — đúng dữ liệu mà benchmark sinh ra để đo.
- `--match utterance` — khớp theo text đã chuẩn hoá khi các lượt bắt được không đúng
  thứ tự.

**Exit code 1 chỉ khi một ca *ungated* fail** ⇒ chạy được trong CI khi các mốc còn
đang làm dở.

---

## 4. `scripts/run_benchmark.ps1` — một lần chạy, đủ artifact

```powershell
.\scripts\run_benchmark.ps1 -Variant quiet -Log D:\runs\quiet.log
```

```powershell
.\scripts\run_benchmark.ps1 -Variant highway -Adb -Serial <device-serial>
```

Sinh kèm `run_manifest.txt` mang **commit, hash của suite, và worktree có dirty
không** — *một p95 mà không có commit đứng cạnh thì không bảo vệ được trong write-up*.

Với V12 (20 câu × 3 mức nhiễu) chạy một lần cho mỗi mức rồi nối ba file `summary.csv`.

---

## 5. Fixture và test

| File | Dùng để |
|---|---|
| `android/voice/fixtures/golden_trace.log` | 4 lượt đủ dạng: `Allow` · `Deny:G1_SPEED_LOCK` · `Confirm:G2_CONFIRM_DELIVERY` · `Error:asr_done`, có prefix logcat thật và dòng log lạ xen giữa |
| `android/voice/fixtures/golden_trace_edge.log` | Ca biên: dấu `\|` trong câu · câu rỗng · câu quá dài · newline · lượt bỏ dở không summary · **4 dòng cố tình hỏng** |
| `backend/testdata/sample_trace.log` | Fixture đi kèm |

`golden_trace_edge.log` là **bài kiểm cho harness**: 4 dòng hỏng phải ra **4 warning**,
không crash, và **không được vứt các mốc hợp lệ cùng `traceId`**.

```bash
cd backend && go test ./...
```

---

## 6. Số đo đã có — và giới hạn của từng bộ

| Bộ | Điều kiện | Kết quả |
|---|---|---|
| `evidence/asr/` (36 clip qua container) | CPU **máy dev**, giọng tổng hợp | RTF median 0.167 · `server_ms` p50 439 / p95 667 · WER 0.411 |
| `evidence/c2/carsky-runtime-20260809/` | Device CarSky, flavor **mock**, **bơm text** (không mic/VAD/ASR) | 3 lượt media đi hết chuỗi tới MediaSession; `e2e_ms=0` **không phải** độ trễ giọng nói |
| `evidence/c2/carsky-voice-e2e-20260810/` | Device CarSky, **mic → VAD → viva-asr trong room → NLU → Guard → thực thi** | 25 lượt: 13 `Allow` đúng intent (6 nhóm chức năng), 2 `Deny:G1_SPEED_LOCK` đúng luật, 10 không nhận ra. **min 1230 · p50 1336 · p95 1664 · max 2091 ms** |

🚫 **p95 = 1664 ms VƯỢT ngân sách 1500 ms.** Đừng khai đạt claim latency dựa trên bộ
này. Và **chưa tách theo chặng** ⇒ **chưa biết chặng nào tốn nhất** — cần đo lại có
phân tách trước khi tối ưu.

---

## 7. Devops helper `viva-tools carsky`

```bash
go run ./cmd/viva-tools carsky blueprint export --id <blueprintId> --out backup.json
```

```bash
go run ./cmd/viva-tools carsky blueprint clone --id <blueprintId> --backup-out backup.json --clone-out clone.json
```

```bash
go run ./cmd/viva-tools carsky nodes --room <roomId> --out nodes.json
```

```bash
go run ./cmd/viva-tools carsky adb-tunnel --room <roomId>
```

Đọc cấu hình từ `backend/.env` (mẫu `backend/.env.example`). Chi tiết endpoint:
[`docs/carsky/02 §7`](../carsky/02-API-REFERENCE.md).
