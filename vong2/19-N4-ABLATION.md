# N4 — Ablation: bỏ phần đội làm thì claim nào sụp

> **Chủ sở hữu:** N4a (A2 + A3) — Vĩ · N4b (A1) — Tùng. Hạn 🟡 06/08.
> Bản này là **quy trình chạy + khung bảng kết quả**. Mọi ô số đang trống là
> **chưa đo**, không phải bằng 0 — không ai được điền ước lượng vào đó.
>
> Vì sao: ô *mức quyết định của phần team-owned* trong barem hỏi "bỏ phần các
> bạn làm thì sao". Câu trả lời có sức nặng nhất là hai lần chạy cùng một bộ
> câu, khác đúng một thành phần, và một bảng before/after.

## Công cụ — đã có, không phải viết thêm

```powershell
cd backend

# 1. Chạy baseline (hệ đầy đủ) và biến thể, mỗi lần một artifact set
.\scripts\run_benchmark.ps1 -Variant full     -Adb
.\scripts\run_benchmark.ps1 -Variant no-guard -Adb

# 2. Lập bảng before/after
go run ./cmd/viva-tools harness compare `
  --baseline runs\<stamp>-full\capture.log `
  --candidate runs\<stamp>-no-guard\capture.log `
  --baseline-label full --candidate-label no_guard `
  --out ablation_a1.csv --verdicts-out ablation_a1_verdicts.csv
```

`--verdicts-out` là cột sống của A1: nó đếm `Deny:G1_SPEED_LOCK` ở hai lần chạy.
Điều đó chỉ hoạt động vì `verdict` mang theo mã luật (`03-contracts.md` §1.2).

---

## A1 — Tắt `SafetyGuard` (N4b, Tùng)

**Giả thuyết:** bỏ tầng an toàn của đội thì *"mở cửa"* lúc `Speed=60` vẫn thực thi.

| Cách tắt | Kết quả mong đợi | Câu thử |
|---|---|---|
| Bypass `SafetyGuard.evaluate` (build flag / DI thay bằng no-op) | `Deny:G1_SPEED_LOCK` biến mất, property `DOOR_LOCK` bị ghi thật | B09, B10 trong `suites/benchmark_v1.csv` |

| Chỉ số | full | no_guard | Ghi chú |
|---|---|---|---|
| `Deny:G1_SPEED_LOCK` (số lượt) | *chưa đo* | *chưa đo* | Kỳ vọng >0 → 0 |
| `Allow` trên `door_lock` khi đang chạy | *chưa đo* | *chưa đo* | Kỳ vọng 0 → >0 |
| p95 `e2e_computed` | *chưa đo* | *chưa đo* | Kỳ vọng gần như không đổi — an toàn **không** phải thứ làm chậm |
| `safety_guard` (chặng) | *chưa đo* | *chưa đo* | Kỳ vọng biến mất ở no_guard |

> ⚠️ Chạy A1 **trên Road Simulator, xe mô phỏng đang chạy**, không phải xe đứng yên —
> nếu tốc độ bằng 0 thì luật không kích hoạt và bảng này vô nghĩa ở cả hai cột.

---

## A2 — Thay `viva-asr` container bằng đường cloud (N4a, Vĩ)

**Giả thuyết:** bỏ ASR chạy trong Room, đi qua mạng ngoài, thì p95 vượt ngân sách 1500ms.

| Cách đổi | Ghi chú |
|---|---|
| Trỏ `BuildConfig.ASR_BASE_URL` sang endpoint cloud thay vì Container Node | Không sửa code app — contract §2 đã bắt buộc đọc URL từ `BuildConfig` |

| Chỉ số | asr_container | asr_cloud | Ghi chú |
|---|---|---|---|
| p50 / p95 `asr_processing` | *chưa đo* | *chưa đo* | |
| p50 / p95 `e2e_computed` | *chưa đo* | *chưa đo* | Ngưỡng cam kết: p95 < 1500ms |
| Số lượt `Error:asr_done` | *chưa đo* | *chưa đo* | Timeout phải nằm trong mẫu, không được lọc ra |
| WER / intent accuracy | *chưa đo* | *chưa đo* | Từ `results.csv` của `harness verify` |

> Đây cũng chính là trục so sánh đã chốt ở `15-QUYET-DINH-BENCHMARK-ASR.md`
> (*ASR on-device Vosk* vs *`viva-asr` container*). Nếu chạy được cả ba đường
> (Vosk / container / cloud) thì bảng có ba cột; nếu không, khai đúng số cột đã đo.

---

## A3 — Bỏ callback của `VhalRepository` (N4a, Vĩ)

**Giả thuyết:** bỏ đường callback real-time thì HMI không còn phản chiếu trạng thái xe.

| Cách tắt | Kết quả mong đợi |
|---|---|
| Không đăng ký callback, chỉ đọc property theo yêu cầu | Chặng `hmi_render` **không còn mốc nào** |

| Chỉ số | full | no_callback | Ghi chú |
|---|---|---|---|
| `hmi_render` (n mẫu) | *chưa đo* | *chưa đo* | Kỳ vọng: n > 0 → **n = 0**, harness in `not comparable` |
| `screen_latency` p95 | *chưa đo* | *chưa đo* | |
| Đổi giá trị ở GPIO Panel → HMI tự đổi | *chưa đo* | *chưa đo* | Bằng chứng bằng ảnh, không bằng lời |

> `harness compare` in dòng `not comparable` khi một chỉ số biến mất hẳn ở một
> phía. Ở ablation, **chính sự biến mất đó là kết quả**, không phải lỗ hổng dữ liệu.

---

## Ba luật khi viết kết quả vào write-up

1. **Không ngoại suy.** Ô *chưa đo* để nguyên chữ "chưa đo" trong bản nộp nếu đến 06/08 vẫn chưa chạy được.
2. **Nói rõ cách tắt.** "Tắt SafetyGuard" phải kèm *tắt bằng cách nào* — bypass DI, build flag, hay sửa luật — nếu không thì không ai lặp lại được.
3. **Cùng bộ câu, cùng mức nhiễu, cùng commit.** `run_manifest.txt` của mỗi lần chạy ghi sẵn commit và hash của suite; nếu hai lần chạy khác commit thì bảng so sánh đó không dùng được.
