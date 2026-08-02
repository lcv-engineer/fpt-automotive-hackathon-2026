# N5 — Bảng ba trạng thái integration: *Đã tích hợp* / *Mô phỏng* / *Kế hoạch*

> **Chủ sở hữu:** Vĩ · hạn 🔴 07/08 · nguồn gốc: `06-PHAN-CONG-4-NGUOI.md` N5.
> Bảng này đi vào README bản nộp. Ô barem nó phục vụ: *Ranh giới và tính tương
> xứng* (2đ) và *Minh bạch phạm vi demo* (2đ).
>
> Cập nhật 02/08. **Mọi dòng ở đây chỉ được nâng nhãn khi có bằng chứng cụ thể
> đi kèm** — một dòng log, một ảnh, một file CSV có tên. Không có bằng chứng thì
> giữ nguyên nhãn cũ, kể cả khi "code chắc chắn chạy".

## Ba nhãn nghĩa là gì

| Nhãn | Điều kiện để dùng |
|---|---|
| **Đã tích hợp** | Đã chạy **trên Device/nền tảng thật**, có log hoặc ảnh làm bằng chứng |
| **Mô phỏng** | Chạy được, nhưng đầu kia là mock/simulator/synthetic — kể cả khi code là code thật |
| **Kế hoạch** | Contract đã có, code chưa chạy hoặc chưa nối |

Ranh giới hay bị nhầm nhất: *"unit test xanh"* là **Mô phỏng**, không phải *Đã tích hợp*.

## Bảng trạng thái

| Thành phần | Nhãn | Bằng chứng hiện có | Còn thiếu gì để lên nhãn cao hơn |
|---|---|---|---|
| Voice core (VAD · grammar 10 intent · TTS · audio focus · trace) | **Mô phỏng** | Unit test JVM xanh; APK `mock`/`real` build xanh | Chạy trên Device AAOS, nghe/thu trong cabin |
| `LatencyTrace` + format `VIVA_TRACE` | **Đã tích hợp** *(ở mức contract)* | 2 fixture `android/voice/fixtures/*.log`, harness parse đúng, `go test` khẳng định | Log **từ Device thật** thay vì fixture |
| Benchmark harness `viva-tools` | **Đã tích hợp** | `go test ./...` xanh; chạy thật trên fixture ra CSV; `harness verify` ra 4/4 PASS | Chạy trên capture thật của Device |
| Bộ 22 câu benchmark + PASS/FAIL | **Mô phỏng** | Suite đã có, runner đã chạy; số liệu mới chỉ từ fixture | Một lần chạy đủ 22 câu trên Device |
| `DeliverySkill` (3 intent) | **Mô phỏng** | Unit test JVM cho cả 3 intent + luồng xác nhận 2 lượt | ⚠️ **Chưa biên dịch lần nào** — máy dev không có JDK/Android SDK. Phải chạy `./gradlew :feature:voice:test` trước khi khai bất cứ điều gì |
| Lộ trình giao hàng (3 đơn Hà Nội) | **Mô phỏng** | `InMemoryDeliveryRepository` — dữ liệu do đội tạo | Không có kế hoạch nối dispatch thật ở Vòng 2 — **đây là simulator theo đúng cam kết proposal**, không phải thiếu sót |
| `viva-asr` container | **Kế hoạch** | Code + Dockerfile + 20 test HTTP xanh (dùng fake transcriber) | Build image thật, đo RTF, push Zot, thêm Container Node (V7) |
| Chất lượng nhận dạng tiếng Việt (WER) | **Kế hoạch** | — | Chưa chạy model thật lần nào. **Không được trích bất kỳ con số WER nào** ngoài WER công bố của PhoWhisper trên VIVOS, và phải ghi rõ đó là số của tác giả model, không phải của đội |
| `VivaCarService` → PropertyID → VHAL | **Kế hoạch** | Contract §0.2 đã chốt đủ 4 cột | M1a (quyền privileged) + M1 |
| VHAL → KUKSA → CAN | 🟠 Tùng xác nhận | Script Node Luau | M4 + T2 |
| CCU | **Mô phỏng** | Mentor cho phép giả lập | M5 echo `HvacCommand` → `HvacStatus`. **Không bao giờ khai "full-stack tới CCU"** |
| Media (`media_*`) và `volume_adjust` | **Kế hoạch** | Grammar nhận đúng intent; chưa có adapter | D7 · D8 |
| DTC / UDS | **Không làm ở Vòng 2** | `uds_dtc_simulator.py` còn trong repo | Đã bỏ 29/07 (T10). Giữ contract cho Vòng 3, **không khai là tính năng** |

## Dữ liệu synthetic — tạo thế nào

Bản nộp phải nói rõ dữ liệu nào là tự tạo và tạo bằng cách nào; nếu không, một
bảng số đẹp sẽ bị đọc là số đo thật.

| Dữ liệu | Cách tạo | Dùng cho |
|---|---|---|
| `android/voice/fixtures/golden_trace.log`, `golden_trace_edge.log` | Long sinh theo đúng luật format của `LatencyTrace.kt`, có prefix logcat thật và 4 dòng cố tình hỏng | Test harness — **không phải log chạy thật** |
| `backend/testdata/sample_trace.log`, `golden_suite.csv` | Đội tự viết, khớp với fixture trên | Smoke test CLI |
| `backend/suites/benchmark_v1.csv` | Đội tự soạn từ `03-contracts.md` §3 + 5 tình huống M7 | Bộ câu benchmark/regression |
| Lộ trình 3 đơn trong `InMemoryDeliveryRepository` | Địa chỉ Hà Nội tự đặt, không lấy từ dữ liệu người dùng thật | Demo delivery |
| WAV TTS pre-render trong `res/raw/` | Sinh offline bằng script của Long (`scripts/generate_tts_assets.ps1`) | Fallback khi thiếu giọng `vi-VN` |
| Ba mức nhiễu của benchmark (quiet / cabin / highway) | 🟠 **Chưa tạo.** Khi tạo phải ghi: nguồn nhiễu, mức SNR, cách trộn | V12 |

## Câu dùng nguyên văn trong README/write-up

> Đội phân biệt ba mức: **Đã tích hợp** (đã chạy trên Device/nền tảng thật, có log
> hoặc ảnh làm bằng chứng), **Mô phỏng** (chạy được nhưng đầu kia là mock hoặc dữ
> liệu tự tạo), và **Kế hoạch** (contract đã chốt, chưa chạy). Luồng `hvac_*` và
> `door_lock` là luồng duy nhất đi tới Vehicle Property; media, âm lượng và giao
> hàng đi qua adapter riêng trong app. CCU được giả lập theo hướng dẫn của mentor
> và được khai đúng nhãn **Mô phỏng** — đội không dùng cụm "full-stack tới CAN"
> cho bất kỳ phần nào chưa chạy trên CCU thật.
