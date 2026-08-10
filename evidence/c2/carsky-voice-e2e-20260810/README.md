# CarSky Device — giọng nói end-to-end THẬT — 10/08/2026

## Vì sao bộ này khác mọi bằng chứng trước

`carsky-runtime-20260809/` dùng hook bơm văn bản, **cố ý bỏ qua mic, VAD và ASR**,
nên `e2e_ms=0` ở đó không phải độ trễ giọng nói.

Bộ này là **lần đầu tiên** chuỗi đầy đủ chạy trên Device CarSky: người nói vào mic
→ Silero VAD → `viva-asr`/PhoWhisper qua mạng room → NLU → SafetyGuard → thực thi.
Mọi con số `e2e_ms` dưới đây là độ trễ giọng nói thật.

## Nhận dạng môi trường và artifact

| Trường | Giá trị |
|---|---|
| Commit | `214914e` + PR #42 (`network_security_config`) — xem mục "Cảnh báo" |
| APK | `app-mock-debug.apk`, 342.687.007 bytes |
| SHA-256 (local = tải xuống = `base.apk` đã cài) | `6fede5ae576f96e7c0920c15c79349f1aecea870cfcb758ded41a89035510706` |
| Build flags | `-PvivaAsrEngine=remote -PvivaAsrBaseUrl=http://10.99.0.3:8080` |
| Device | `VIVA` — `v37aa3knc6t1embelr5yi`, deployment `RUNNING`, 22 node |
| Android target | `trout_arm64`, `arm64-v8a`, Android 14 |
| Package | `com.sopa.viva_automotive.mock` |
| ASR | container `viva-asr` tại `10.99.0.3:8080`, model `phowhisper-tiny-int8`, `initial_prompt=null`, `hotwords=null` |
| Ngưỡng confidence | `viva_min_conf=40` (0.40) qua `Settings.Global` |
| Cách lấy log | widget `IVI ADB` trong UI CarSky (`face-adb`), `logcat -d -s VIVA_TRACE:I VIVA_VOICE:I`, dán tay |

⚠️ **Cách lấy log là dán tay qua terminal trình duyệt**, không phải `adb pull` — REST
`adb-exec`/`adb-shell` vẫn 502 (Conduit). File `capture.log` chỉ giữ các dòng
`VIVA_TRACE_SUMMARY`; các mốc `speech_start`/`asr_sent`/`nlu_done` có trong phiên gốc
nhưng không được sao chép hết vào đây.

## Kết quả — 25 lượt

| Nhóm | Số lượt |
|---|---|
| Intent đúng, `Allow` | **13** |
| SafetyGuard chặn đúng luật (`G1_SPEED_LOCK`) | 2 |
| Không nhận ra (`unknown` / `G3_UNSUPPORTED`) | 10 |

Sáu nhóm chức năng đi trọn tuyến tới `Allow`:

```
media_play            phát nhạc lên · phát nhạc đi
media_next            chuyển bài tiếp theo · trên bài tiếp theo
hvac_set_temp         nhiệt độ lên hai tư độ · giảm/tăng nhiệt độ ... độ
hvac_set_fan          tăng quạt lên mức năm · dãm quạt xuống mức một
vehicle_status_speed  cho tôi biết tốc độ hiện tại        (×2)
vehicle_status_fuel   cho tôi biết nhiên liệu hiện tại    (×2)
```

## Độ trễ — số thật đầu tiên, và nó CHƯA đạt claim

```
min = 1230 ms   p50 = 1336 ms   p95 = 1664 ms   max = 2091 ms
4/25 lượt vượt 1500 ms
```

🚫 **Không được khai "p95 ≤ 1500 ms" dựa trên bộ này.** `03-contracts.md` §1.3 đặt
ngân sách p95 là 1500 ms; đo được **1664 ms**. Mẫu 25 lượt là nhỏ và chưa phân tách
theo chặng, nhưng con số đang ở phía chưa đạt, và làm tròn xuống là khai sai.

## SafetyGuard trên phần cứng thật

```
hãy mở cửa.  -> door_lock | Deny:G1_SPEED_LOCK
mở cửa.      -> door_lock | Deny:G1_SPEED_LOCK
```

Xe trong room báo tốc độ > 5 km/h nên guard từ chối mở khoá — đúng `G1_SPEED_LOCK`.
Đây là bằng chứng luật an toàn chạy trên Device, **không phải** trên bộ mô phỏng
trong app.

Hệ quả cho demo: nhánh hỏi xác nhận `G2_CONFIRM_DOOR` **không xuất hiện** trong bộ
này vì xe không đứng yên. Muốn diễn nhánh đó phải đưa tốc độ về 0 trước.

## Khoảng trống đã biết

**TTS thiếu giọng tiếng Việt cho một số câu.** Lệnh chạy đúng nhưng máy không đọc
được câu trả lời:

```
W VIVA_VOICE: TTS failed for "Đã gửi lệnh phát nhạc tới trình phát.":
              No Vietnamese TTS voice or pre-rendered prompt for: ...
```

**10/25 lượt không nhận ra**, tập trung ở ba chỗ:

| Nói | ASR nghe |
|---|---|
| chuyển bài | `chuyển bay` — cụm phổ biến hơn nhiều trong dữ liệu huấn luyện |
| tốc độ (đứng đầu câu) | `tóc độ` |
| xăng / nhiên liệu (biến thể) | `xen` · `săn` · `mức binh` · `mức tinh` |

Cách nói đã chứng minh chạy được thì lặp lại **ổn định**; các biến thể chưa nằm
trong grammar hoặc bộ câu mẫu thì trượt. Đây là giới hạn nhận dạng, không phải lỗi
đường ống.

## Được phép khai gì

✅ *"Trên Device CarSky, chuỗi giọng nói đầy đủ — mic → VAD → PhoWhisper → NLU →
SafetyGuard → thực thi — chạy thật; 13/25 lượt đi tới `Allow` với intent đúng, phủ
6 nhóm chức năng; SafetyGuard chặn mở khoá cửa khi xe đang chạy đúng luật
`G1_SPEED_LOCK`; độ trễ đầu-cuối p50 = 1336 ms."*

❌ Không khai p95 ≤ 1500 ms — **đo được 1664 ms**.
❌ Không khai tỷ lệ nhận dạng dựa trên 25 lượt tự chọn: đây **không phải** bộ
benchmark có kịch bản, mà là phiên thử tay. Muốn có WER/accuracy phải chạy
`suites/benchmark_v1.csv` đúng quy trình.
❌ Không khai gì về `real` flavor — bộ này chạy biến thể `mock`.

## Cảnh báo về commit

Bản APK gồm `main@214914e` **cộng thêm** PR #42 (`network_security_config` cho
`10.99.0.3`) lúc đó chưa merge. Nếu #42 đã vào `main`, hãy build lại từ commit merge
và đối chiếu SHA-256 để bộ bằng chứng trỏ về đúng một commit duy nhất — hiện tại nó
trỏ về một trạng thái cây làm việc, yếu hơn về mặt truy nguyên.

Thiếu #42 thì mọi lượt đều chết ở `Cleartext HTTP traffic to 10.99.0.3 not permitted`
— 5 lượt đầu trong phiên gốc chính là như vậy, trước khi cài bản có PR #42.
