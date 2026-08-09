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
| Voice core **trên đường chạy APK** (grammar 10 intent · TTS · audio focus · trace) | **Mô phỏng / Device partial** | APK đã chạy trên emulator AAOS 14 với Vosk; ngày 09/08 bản mock đúng SHA chạy trên Device CarSky và sinh trace thật cho phần text-injection → NLU → media. Test hook bỏ qua mic/VAD/ASR; TTS media còn degrade. `evidence/emulator/`, `evidence/c2/carsky-runtime-20260809/` | Thu một lượt mic thật trên Device có `speech_start/speech_end/asr_done`, rồi capture TTS/audio focus |
| Một nguồn PCM · Silero VAD · Vosk — **đã nằm trên đường chạy APK** | **Mô phỏng** *(source/build; mic Device pending)* | `AndroidPcmSource` là nơi duy nhất mở mic; cùng frame qua `VadStreamDriver` rồi Vosk trong `VoiceAssistantService.runInteraction`; unit test và build xanh | Thu một lượt qua mic thật, lưu trace có `speech_start/speech_end`, rồi chạy lại trên Device CarSky |
| `AsrClient` → container `viva-asr` — **chưa nằm trên đường chạy APK** | **Kế hoạch** | Contract + fake client; 20 test HTTP dùng fake transcriber | Cắm client thật, build/chạy model thật và đo trên cùng PCM với Vosk |
| `SafetyGuard` (G1 · G2 · G3) | **Mô phỏng** *(nâng hai bậc 05/08)* | Có lớp hiện thực và **được cưỡng chế ở biên `VehicleRepository`** (`DefaultSafetyGuard`, `GuardedVehicleRepository`), cắm vào cả hai biến thể app. Chạy thật trên emulator: `Deny:G1_SPEED_LOCK` khi mở cửa ở 60 km/h, `Confirm:G2_CONFIRM_DOOR` khi xe đứng yên, `Deny:G3_UNSUPPORTED` cho câu ngoài phạm vi. B09/B10/B20 đều PASS. `evidence/emulator/session-20260805-165301/` | Chạy trên Device thật với property VHAL thật thay vì `MockVehicleRepository`. `G3_LOW_CONFIDENCE` vẫn chưa kích hoạt được vì `TranscriptionEvent.Final` chưa mang confidence (F5) |
| `LatencyTrace` + format `VIVA_TRACE` | **Đã tích hợp** *(ở mức contract)* | 2 fixture `android/voice/fixtures/*.log`, harness parse đúng, `go test` khẳng định | Log **từ Device thật** thay vì fixture |
| Benchmark harness `viva-tools` | **Đã tích hợp** | `go test ./...` xanh; chạy thật trên fixture ra CSV; `harness verify` ra 4/4 PASS | Chạy trên capture thật của Device |
| Bộ 22 câu benchmark + PASS/FAIL | **Mô phỏng** *(nâng 05/08)* | Đã chạy **đủ 22 câu trên app thật** (emulator AAOS): 17 PASS · 5 FAIL · 0 MISSING, cả 5 FAIL đều là *known gap*. `evidence/emulator/session-20260805-165301/results.csv` | ⚠️ Câu được **bơm bằng text**, không qua mic — đo `router → guard → skill`, KHÔNG đo ASR/WER/độ trễ (mọi `e2e_ms=0`). Muốn số ASR phải có người nói thật |
| `DeliverySkill` (3 intent) | **Mô phỏng** | Unit test JVM cho cả 3 intent + luồng xác nhận 2 lượt; **đã chạy thật trên emulator**: B16/B17/B18/B19 PASS, gồm cả hai lượt xác nhận | Dữ liệu lộ trình vẫn do đội tạo — đúng cam kết proposal, không phải thiếu sót |
| Lộ trình giao hàng (3 đơn Hà Nội) | **Mô phỏng** | `InMemoryDeliveryRepository` — dữ liệu do đội tạo | Không có kế hoạch nối dispatch thật ở Vòng 2 — **đây là simulator theo đúng cam kết proposal**, không phải thiếu sót |
| `viva-asr` container | **Đã tích hợp** *(nâng lần hai, 04/08)* | Image đã push lên `registry.hackathon-2.carsky.io/viva/viva-asr` (multi-arch amd64+arm64) và **chạy thật trên CarSky**: node `VIVA ASR` phase `Running`, 22/22 node của room lên hết. Bằng chứng: `evidence/carsky/v7-manifest.txt` | — (V6/V7 đóng ở mức "cluster pull được image và node chạy") |
| **Latency của `viva-asr` trên CarSky** | **Kế hoạch** | Chưa gửi được request nào vào container từ ngoài: nó nằm trên mạng `10.99.0.x` trong room, `adb-exec`/`shell`/`container-exec` chết vì Conduit, chưa có `nydus-reach` | Đo qua app chạy trên Device — **cùng nút thắt với E03/E04** |
| Chất lượng + tốc độ `viva-asr` (số hiện có) | **Mô phỏng** | RTF median 0.167 · `server_ms` p50=439/p95=667 · WER 0.411 — **đo trên CPU máy dev**, clip TTS tổng hợp. `evidence/asr/` | Đo lại trên node CarSky và giọng người thật |
| Chất lượng nhận dạng của `viva-asr` | **Mô phỏng** | WER 0.411 trên 36 clip **giọng TTS tổng hợp**, đã resample 22.05k→16k bằng nội suy tuyến tính | Đo trên giọng người thật trong cabin; và đo **intent accuracy** thay vì WER — xem ghi chú dưới |
| Chất lượng nhận dạng tiếng Việt (WER) | **Kế hoạch** | — | Chưa chạy model thật lần nào. **Không được trích bất kỳ con số WER nào** ngoài WER công bố của PhoWhisper trên VIVOS, và phải ghi rõ đó là số của tác giả model, không phải của đội |
| `VivaCarService` → PropertyID → VHAL | **Kế hoạch** | Contract §0.2 đã chốt đủ 4 cột | M1a (quyền privileged) + M1 |
| VHAL → KUKSA → CAN | 🟠 Tùng xác nhận | Script Node Luau | M4 + T2 |
| CCU | **Mô phỏng** | Mentor cho phép giả lập | M5 echo `HvacCommand` → `HvacStatus`. **Không bao giờ khai "full-stack tới CCU"** |
| `volume_adjust` | **Kế hoạch** *(đã nối API, nền tảng từ chối)* | Đã nối `AudioManager` thật + đọc lại giá trị. Đo trên emulator: `isVolumeFixed=true`, `getStreamVolume=15/15` cứng, còn âm lượng thật nằm ở `CarVolumeGroup(0)` gain 32/38 (`dumpsys car_service`) | Cần `CarAudioManager.setGroupVolume` → quyền `CAR_CONTROL_AUDIO_VOLUME` (privileged) → **chặn bởi M1a**. D8 |
| Media (`media_play` / `media_pause` / `media_next`) | **Đã tích hợp — CarSky Device từ NLU đến media (mock/debug)** | Bản mock đúng SHA-256 đã cài trên Device `VIVA`; ba text-injection tạo trace `Allow`, MediaSession đổi `PLAYING → PAUSED` và `next` đổi active item `0 → 1`. `evidence/c2/carsky-runtime-20260809/` | Thu lượt qua mic thật và capture audio/HMI. Sửa TTS media còn thiếu giọng/prompt; kiểm duck/release audio focus. Không dùng bằng chứng này cho VHAL/CAN |
| DTC / UDS | **Không làm ở Vòng 2** | `uds_dtc_simulator.py` còn trong repo | Đã bỏ 29/07 (T10). Giữ contract cho Vòng 3, **không khai là tính năng** |

> **Cập nhật 05/08 — có thêm một môi trường thứ hai, đừng lẫn hai cái.**
> `evidence/emulator/` là **emulator AAOS 14 chạy trên máy dev**, không phải CarSky. Nó
> chứng minh app *chạy được* và guard *chặn được*, nhưng đầu kia là `MockVehicleRepository`
> nên nhãn cao nhất nó đạt được vẫn là **Mô phỏng**. Mọi câu có chữ "trên CarSky", "tới
> VHAL" hay "full-stack" **không** được dựa vào thư mục đó.
>
> **Device dùng để lấy evidence CarSky là máy ảo Cuttlefish.** `evidence/c2/device-info.txt` ghi
> serial `CUTTLEFISHCVD01` và fingerprint `aosp_trout_arm64`. Nó đủ để kiểm quyền, property
> và cài đặt app trên AAOS, nhưng không phải cabin hay xe thật. Mọi audio qua đường này phải
> khai là *audio thu ngoài rồi phát lại/inject*, không gọi là “đo trong cabin”.

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
| Ba mức nhiễu của benchmark (quiet / cabin / highway) | Đã tạo ở V12 — xem `evidence/asr/v12/v12-manifest.txt` và `asr/scripts/noise_mix.py` | V12 |
| Câu bơm text trong phiên benchmark trên emulator | `SimulatedUtteranceReceiver` đẩy thẳng câu vào `handleUtterance`, **bỏ qua mic/VAD/ASR**. Mỗi lượt có một dòng `VIVA_BENCH_INJECT` cùng trace id trong capture | Đo intent + verdict. **Không** dùng cho bất kỳ số độ trễ nào |

## Câu dùng nguyên văn trong README/write-up

> Đội phân biệt ba mức: **Đã tích hợp** (đã chạy trên Device/nền tảng thật, có log
> hoặc ảnh làm bằng chứng), **Mô phỏng** (chạy được nhưng đầu kia là mock hoặc dữ
> liệu tự tạo), và **Kế hoạch** (contract đã chốt, chưa chạy). Luồng `hvac_*` và
> `door_lock` là luồng duy nhất đi tới Vehicle Property; media, âm lượng và giao
> hàng đi qua adapter riêng trong app. CCU được giả lập theo hướng dẫn của mentor
> và được khai đúng nhãn **Mô phỏng** — đội không dùng cụm "full-stack tới CAN"
> cho bất kỳ phần nào chưa chạy trên CCU thật.
