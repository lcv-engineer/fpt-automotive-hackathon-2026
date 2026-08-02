# CLAUDE.md — backend/ (viva-tools)

Phạm vi file này: chỉ `backend/` (Go CLI `viva-tools` — benchmark harness +
CarSky devops helper). Xem `CLAUDE.md` ở repo root cho bối cảnh cuộc thi
chung — nhưng lưu ý bản gốc đó mô tả kiến trúc **đã cũ** (Command Dispatcher,
cloud LLM T2); kiến trúc thật hiện tại nằm ở `vong2/04-KE-HOACH-CAP-NHAT-28-07.md`
và `vong2/03-contracts.md`. Xem `backend/README.md` mục "Scope" để biết vì
sao `backend/` chỉ còn là harness + devops CLI, không phải service dispatcher.

## Quy tắc branch (Git Flow)

**Vấn đề:** làm việc trực tiếp trên `main`/`dev` dễ đẩy code chưa test lên
nhánh mà người khác (hoặc CI) đang dựa vào, và lịch sử commit lẫn lộn
feature/fix/refactor khiến `git log` vô dụng khi cần tìm lại một thay đổi
cụ thể lúc gấp rút trước deadline.

**Baseline hiện tại:** `vong2/03-contracts.md` §9 ghi quy ước của cả đội
(áp dụng cho repo Android/Kotlin dùng chung): *"Branch: `main` luôn build
được. Mỗi người làm trên `feat/<tên>-<module>`, merge khi xanh."* — không có
nhánh `dev` riêng, branch feature checkout thẳng từ `main`.

**Quy tắc cho `backend/` (theo yêu cầu của Vĩ, có thể khác quy ước Android ở trên):**

1. Hai nhánh dài hạn: `main` (luôn build được, là thứ được nộp) và `dev`
   (nhánh tích hợp — mọi nhánh tính năng merge vào đây trước, `main` chỉ
   nhận merge từ `dev` khi ổn định).
2. **Không code trực tiếp trên `main` hoặc `dev`.** Mọi thay đổi — kể cả
   nhỏ — đi qua một nhánh riêng, checkout từ `dev`:
   ```
   git checkout dev
   git pull
   git checkout -b feature/<mo-ta-ngan>
   ```
3. Đặt tên nhánh theo Git Flow, `kebab-case`, mô tả ngắn gọn việc đang làm:
   - `feature/<mo-ta>` — tính năng mới hoặc thay đổi hành vi (vd:
     `feature/harness-adb-source`, `feature/carsky-nodes-cmd`)
   - `fix/<mo-ta>` — sửa lỗi (vd: `fix/csv-empty-sample-crash`)
   - `hotfix/<mo-ta>` — sửa gấp trên `main` đã lỡ release, sau đó merge
     ngược lại cả `main` và `dev`
   - `chore/<mo-ta>` — việc không đổi hành vi runtime: cập nhật docs, CI,
     dọn dẹp, đổi tên
   - `refactor/<mo-ta>` — đổi cấu trúc code, giữ nguyên hành vi
4. Merge về `dev` khi: `go build ./...`, `go vet ./...`, `go test ./...`
   đều xanh. Không merge "để sửa sau".
5. Xoá nhánh sau khi merge (`git branch -d feature/...`) — nhánh sống lâu
   dễ conflict và không ai nhớ nó còn dở gì.

**Tradeoff:** thêm một bước gián tiếp (`dev`) so với quy ước `feat/* -> main`
của cả đội nghĩa là nhiều thao tác git hơn cho một CLI tool cá nhân — chấp
nhận được vì `backend/` ít người đụng vào cùng lúc hơn repo Android, và cái
giá của một `main` bị hỏng ngay trước demo cao hơn nhiều so với vài giây gõ
thêm lệnh. Nếu `backend/` sau này chuyển vào cùng repo/CI với Android, báo
cả đội và thống nhất lại một quy ước duy nhất — đừng để hai chuẩn branch
song song trong cùng một repo.

## Commit message

Theo Conventional Commits, dòng đầu ≤72 ký tự, thì hiện tại, mô tả **vì sao**
chứ không chỉ *làm gì* (diff đã nói làm gì rồi):

```
<type>(<phạm_vi_tuỳ_chọn>): <mô tả ngắn, thì hiện tại>

<tuỳ chọn: 1-3 dòng giải thích lý do, đặc biệt nếu không hiển nhiên từ diff>
```

`type` dùng: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `perf`.

Ví dụ tốt:
```
feat(harness): cross-check reported e2e_ms against computed edge_pipeline_total

Long's Kotlin code tự tính e2e_ms trong VIVA_TRACE_SUMMARY; báo cáo cần in
cả hai để phát hiện nếu định nghĩa "end-to-end" của app lệch với marks thô.
```

Ví dụ không đạt: `update code`, `fix bug`, `wip`, `asdf`.

## Trước khi merge — checklist

- [ ] `go build ./...` xanh
- [ ] `go vet ./...` xanh
- [ ] `go test ./...` xanh
- [ ] Không có secret/token thật trong code hoặc test fixture (`.env` phải
      nằm trong `.gitignore`, `git status` trước khi commit để soát)
- [ ] Nếu đổi format log/CSV/JSON output: cập nhật `README.md` cho khớp
- [ ] Nếu thêm dependency ngoài (hiện tại project **cố tình 0 dependency
      ngoài stdlib** — xem lý do trong README) — cân nhắc kỹ trước khi thêm,
      ghi rõ lý do trong commit message

## Edge case đã nghĩ tới (và cách xử lý hiện tại)

| Edge case | Xử lý hiện tại | Còn thiếu / cần biết thêm |
|---|---|---|
| Dòng log không khớp format `VIVA_TRACE\|...` | Bỏ qua im lặng (bình thường — phần lớn log không phải trace) | — |
| Dòng khớp marker nhưng sai số field / nanos không parse được số | Không crash, gom vào `Warnings`, in ra stderr, **không loại traceId đó khỏi kết quả** — các mark hợp lệ khác của cùng traceId vẫn được tính | — |
| Một turn thiếu mark (VD app crash giữa chừng, chưa tới `render_done`) | Segment nào thiếu mark thì tự động loại khỏi mẫu tính p50/p95 của segment đó — không panic, không tính sai bằng cách coi thiếu = 0 | — |
| File log rỗng / không có dòng `VIVA_TRACE` nào | In cảnh báo rõ ràng ra stderr thay vì âm thầm xuất báo cáo toàn cột trống | — |
| File log lớn (hàng trăm nghìn dòng) | Đọc hết vào bộ nhớ (`[]string`) — đủ dùng ở quy mô hackathon (vài chục–vài trăm utterance). | Nếu benchmark harness v2 (V11, 20+ câu × regression tự động nhiều lần) làm log phình to, cân nhắc đổi `LineSource.Lines()` sang streaming (channel/iterator) trước khi nó thành vấn đề thật, đừng tối ưu sớm |
| Hai lần chạy `--out report.csv` cùng file | Ghi đè (`os.Create` truncate) — có chủ đích, không âm thầm append lẫn 2 lần đo | Nếu cần giữ lịch sử nhiều lần đo, tự đặt tên file khác nhau (VD có timestamp) khi gọi — CLI không tự làm thay |
| `adb` không có trong PATH / chưa mở tunnel tới device | Lỗi rõ ràng kèm stderr của lệnh `adb` thật, không nuốt lỗi | Không tự động gọi `carsky adb-tunnel` trước — người dùng phải tự mở tunnel (V5) trước khi `--adb` |
| `adb logcat` treo (device/tunnel đơ, không phản hồi) | `exec.CommandContext` với timeout 30s cứng (`adbTimeout` trong `logsource/adb.go`) — hết giờ thì trả lỗi rõ ràng, không treo CLI vô thời hạn | — |
| CarSky API timeout / lỗi mạng | Timeout cấu hình được qua `CARSKY_TIMEOUT_SECONDS` (mặc định 30s) | — |
| CarSky trả 5xx hoặc lỗi mạng cho request **GET** (`nodes`, `adb-tunnel`, `blueprint export`) | Tự retry tối đa 3 lần, backoff tuyến tính 0/500ms/1s — vì GET không đổi trạng thái server nên lặp lại an toàn | Có test (`client_test.go`) xác nhận đúng số lần gọi, không chỉ tin lời khai |
| CarSky trả 4xx (token sai, id không tồn tại...) cho bất kỳ request nào | **Không retry** — lỗi client thì lặp lại vô ích, trả lỗi ngay kèm status code + body | — |
| `blueprint clone` (POST) gặp lỗi | **Không bao giờ tự retry** — POST không idempotent, retry sai lúc có thể tạo nhiều bản clone rác trên CarSky. Lỗi trả về ngay, người dùng tự quyết định thử lại sau khi đọc lỗi | — |
| `CARSKY_API_TOKEN` hết hạn / sai | Surfaced qua lỗi HTTP (thường 401/403) kèm body response | Chưa phân biệt rõ "token sai" vs "token hết hạn" vì chưa biết CarSky trả lỗi dạng gì — **hỏi lại nếu gặp, đừng đoán** |
| Blueprint clone thất bại sau khi export backup thành công | `SafeClone` vẫn trả backup đã lưu về cho caller — CLI ghi file backup trước, in lỗi clone sau, không làm mất backup đã lấy được | — |
| Log input quá lớn (trỏ nhầm file, hoặc filter adb thiếu nên dump cả logcat) | Giới hạn cứng 200MiB (`maxLogBytes` trong `logsource/scan.go`) — vượt là báo lỗi rõ ràng ngay, không cố load hết vào RAM rồi bị OS kill tiến trình | — |
| Ký tự tiếng Việt / dấu phẩy trong `utterance` khi ghi CSV | `encoding/csv` (Go stdlib) tự quote/escape đúng chuẩn RFC 4180 — không tự nối chuỗi CSV bằng tay | — |
| Một `stage` xuất hiện 2 lần trong log của cùng `traceId` | **Giữ giá trị đầu**, ghi warning. Đúng theo §1 (`ghi đè = bỏ qua`): mốc đánh 2 lần sẽ **rút ngắn** đoạn đo, tức là app càng bug thì p95 càng đẹp | — |
| Hai dòng `VIVA_TRACE_SUMMARY` cho cùng một `traceId` | Giữ dòng đầu, ghi warning — §1.1 nói đúng 1 dòng/lượt; 2 dòng nghĩa là trùng traceId hoặc double-emit, và mọi thống kê per-turn sẽ nhập nhằng | — |
| `verdict` không khớp grammar §1.2 (VD `Allowed`, `Deny` không có rule) | Phân loại thành `Unknown` (hoặc giữ kind nhưng detail rỗng) + warning, **vẫn nằm trong mẫu**. Loại bỏ lượt không phân loại được sẽ âm thầm làm đẹp mọi con số tính sau đó | — |
| Trace có mốc nhưng không có summary (lượt bỏ dở) | Đếm riêng ở `MissingSummary`, không gộp vào bất kỳ verdict nào — *"không biết lượt đó kết thúc ra sao"* là một kết quả riêng | — |
| Response JSON của CarSky có field không đoán trước được | Không có structs — decode raw JSON, in/lưu nguyên văn | **Việc thật cần làm**: kéo `GET /api/v1/openapi` khi có token, viết structs thật thay `json.RawMessage` |
| Nhiều thành viên chạy `carsky blueprint clone` cùng lúc trên cùng blueprint | Không có lock phía client — có thể tạo 2 bản clone nếu 2 người bấm gần như đồng thời | Đây là giới hạn của CarSky API (không có endpoint lock từ phía client gọi được an toàn ngoài `/blueprints/:id/lock`, chưa dùng) — **thống nhất bằng quy trình team** (chỉ 1 người giữ quyền clone) thay vì cố giải quyết bằng code |

## Câu hỏi còn treo — hỏi người, đừng tự đoán tiếp

- ~~`CARSKY_BASE_URL` thật là gì?~~ — ✅ **đã xác nhận 02/08 bằng cách gọi thử**:
  `https://hackathon-2.carsky.io/api/v1` **là** host API (trả JSON có cấu trúc
  `{"error":"UNAUTHORIZED",...}` chứ không phải HTML của web UI). Cơ chế auth
  cũng đã xác nhận: không có header → `"Missing credentials"`, có header
  `Authorization: Bearer <token>` → `"Invalid JWT"`. Header `x-api-key` cũng
  được chấp nhận làm nơi chứa token; cookie thì không.
  ⚠️ **Vẫn chưa xác nhận từng endpoint path** — middleware auth chạy **trước**
  routing (đường dẫn bịa cũng trả 401), nên chỉ khi có token hợp lệ mới kiểm
  được `/blueprints/:id/export`, `/deployments/:roomId/nodes`… và mới kéo được
  `GET /api/v1/openapi` để thay JSON thô bằng struct thật.
- ~~Format chuỗi `Verdict` trong dòng `VIVA_TRACE_SUMMARY`~~ — ✅ **đã trả lời
  29/07**, `vong2/03-contracts.md` §1.2:
  `verdict := "Allow" | "Deny:"<RULE_ID> | "Confirm:"<RULE_ID> | "Error:"<STAGE_ID>`,
  tách bằng dấu `:` **đầu tiên**. Hiện thực ở `internal/domain/verdict.go`,
  test ở `verdict_test.go`.
- `backend/` này nằm trong repo `fpt-automative-hackathon` (chủ yếu chứa
  docs/planning) — chưa rõ đây có phải repo Git chung cả đội dùng cho task
  V4 (`Repo Git + CI build APK`) hay là repo riêng của Vĩ. Nếu là repo
  chung, cần thống nhất lại quy tắc branch ở trên với `03-contracts.md` §9.
