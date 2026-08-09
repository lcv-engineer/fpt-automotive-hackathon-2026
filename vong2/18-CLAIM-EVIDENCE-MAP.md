# N1 — Claim–Evidence Map

Ngày lập bản v1: **02/08/2026**
Snapshot đã đối chiếu: `150df0b` — `main` và `feature/viva-agent` trùng nhau (`0 ahead / 0 behind`).

> Mục tiêu của bản này là khóa phạm vi claim và làm lộ evidence còn thiếu trước feature freeze 05/08.
> `source`, unit test JVM và fixture synthetic chứng minh implementation/contract; chúng **không** chứng minh luồng đã chạy trên CarSky.

## 1. Quy tắc đọc trạng thái

| Nhãn | Nghĩa |
|---|---|
| **XANH — có thể claim** | Có artifact chạy thật và evidence đúng môi trường, cùng identity |
| **VÀNG — source/JVM** | Có code hoặc unit test; còn thiếu runtime evidence trên Device |
| **ĐỎ — bị chặn** | Thiếu dependency trong core flow; chưa được nói như tính năng đã tích hợp |
| **MÔ PHỎNG** | Dùng fake, mock, fixture hoặc CCU giả lập; phải nói rõ khi demo |

Ba luật bắt buộc:

1. Evidence Device phải chứa commit/artifact identity, serial/fingerprint và thời điểm chạy.
2. `android/voice/fixtures/*.log`, `backend/testdata/sample_trace.log` và `vad-threshold-baseline.csv` là dữ liệu test; không gắn nhãn CarSky.
3. Chỉ `hvac_*` và `door_lock` có thể đi qua Vehicle Property. Media, volume và delivery không được claim đi qua VHAL/CAN.
4. Thành phần chỉ có unit test nhưng **chưa nằm trên đường chạy APK** phải được ghi rõ là
   ngoài đường chạy; không gộp với phần đang chạy. Xem `25-LECH-KIEN-TRUC-VOICE-PIPELINE.md`.

## 2. Ma trận claim

| Claim ID | Claim giới hạn dùng trong bài | Baseline / tài sản có sẵn | Phần team-owned quyết định claim | Expected result / oracle | Evidence hiện có | Evidence bắt buộc còn thiếu | Trạng thái 02/08 |
|---|---|---|---|---|---|---|---|
| **C-VOICE** | VIVA nhận câu tiếng Việt offline, định tuyến 10 intent và phản hồi bằng HMI/TTS | AAOS app + mic/audio API; baseline CarSky chưa có voice pipeline tiếng Việt *(chờ N3 xác nhận manifest)* | **Trên đường chạy:** Vosk on-device, grammar router 10 intent, TTS + 36 câu pre-render, audio focus, `LatencyTrace`. **Ngoài đường chạy** (có mã/test, chưa cắm): push-to-talk, Silero VAD, `AsrClient` | Một lượt thật có `speech_start → asr_done → nlu_done → tts_start`; câu/intent đúng oracle | Source + unit test trong `android/voice`; 36 TTS WAV + 1 notification cue; E12 ghi 139 Gradle test xanh. Fixture hiện có là synthetic | **E01**, **E03**, **E04**, **E10** | **VÀNG** — phần trên đường chạy chờ M6/Device; phần ngoài đường chạy không thể xanh chỉ bằng mở Device, xem `25-LECH-KIEN-TRUC-VOICE-PIPELINE.md` |
| **C-HVAC** | Câu “hạ điều hòa xuống 24 độ” và “quạt mức 3” được đổi thành PropertyID/area/value đúng; chỉ nói “Đã…” sau `Applied` | VHAL/CarService và app HVAC có sẵn ở tầng platform/app; mapping intent không có sẵn | `CoreIntentMapper`, M2, executor/repository và service framework do đội sở hữu | `358614275/49/24.0` và `356517120/0/3`; callback/HMI khớp giá trị đọc lại | M2 trong `03-contracts.md`; mapper/repository + JVM tests | **E02–E07**; đặc biệt phải có service/AIDL thật trước khi claim kiến trúc mentor yêu cầu | **ĐỎ** — thiếu M1 + M6 |
| **C-DOOR** | “Khóa cửa” tác động cửa tài xế với polarity đúng và HMI phản chiếu trạng thái | VHAL/CarService và door property platform; chưa có voice mapping | Grammar + `CoreIntentMapper` + M2 + safety/service boundary | `371198722/1/true`; property đọc lại là locked; không đổi cửa khác | Mapping/source + mapper test; Lua sửa trên branch Tùng chưa thuộc snapshot này | **E02–E05**, **E08** | **ĐỎ** — thiếu M1/M6; 3 commit Tùng chưa tính |
| **C-SAFETY** | Lệnh mở cửa ở `Speed=60 km/h` bị chặn trước property setter | Baseline không có policy voice-to-vehicle của đội | `SafetyGuard` + snapshot tốc độ + verdict `Deny:G1_SPEED_LOCK` | Không có `exec_done`/setter; TTS nêu lý do; ablation bỏ guard làm claim sụp | Contract, M7a và fake-gateway unit test; `golden_trace.log` chỉ là fixture synthetic | **E08** và **E09** | **VÀNG** — guard đã cưỡng chế trong mã và chặn thật trên emulator AAOS (`Deny:G1_SPEED_LOCK` ở 60 km/h, cửa không bị ghi); còn đỏ ở chỗ property là `MockVehicleRepository`, **chưa có bằng chứng trên Device CarSky** (E09 vẫn trống) |
| **C-ERROR** | Câu thiếu slot được hỏi lại; câu ngoài phạm vi bị từ chối và không gọi vehicle/media gateway | Baseline là unknown/error chung | Grammar rule, validation ở mapper, failure response và trace verdict | “Quạt mạnh lên” hỏi mức 0–5; “đặt bàn ăn tối” không sinh action | `GrammarIntentRouterTest`, `CoreIntentMapperTest`, `VoiceAgentTest`, M7-04/M7-05 | **E03**, **E04**, **E10** | **VÀNG** — JVM xanh, chưa chạy mic/TTS thật |
| **C-MEDIA** | “Chuyển bài” đổi track thật; TTS duck nhạc rồi trả audio focus | `MediaSession`/audio APIs của AAOS | Intent mapping, media adapter và audio-focus controller | Track ID đổi; focus loss/duck/gain quan sát được; không có Vehicle Property | **E11a:** APK đúng SHA chạy trên CarSky Device mock; NLU → media trace `Allow`, play/pause đổi state, next đổi active item `0 → 1` | **E11** cho mic/TTS duck/release và capture audio | **VÀNG** — được claim giới hạn “NLU → track đổi trên Device mock”; chưa được claim giọng nói end-to-end hoặc audio-focus |
| **C-OBS** | Mỗi lượt có `traceId`, stage trace và summary; harness tính p50/p95 mà không bỏ lượt lỗi | Baseline chưa có trace xuyên ranh giới của VIVA | `LatencyTrace` + parser/aggregator Go + quy tắc giữ failure | ≥20 lượt thật; CSV tái lập; failure nằm trong mẫu; p50/p95 khớp summary | Kotlin/Go tests và fixture synthetic parse được | **E03**, **E04** | **ĐỎ cho số C2** — chờ V10 + Device |
| **C-MODULAR** | Thêm một intent bằng rule + mapper/policy/test mà không sửa VHAL và không cho AI sinh PropertyID | Platform cung cấp VHAL/property contract, không cung cấp intent domain của VIVA | `GrammarRule`, boundary `CoreIntentMapper`, M2 và test extension | Intent mới không ghi đè core/removed rule; slot lỗi dừng trước execution | `GrammarIntentRouterTest` và `CoreIntentMapperTest` | **E12** + Baseline Manifest N3 để so provided/configured/modified/new | **VÀNG** — code/JVM có, baseline chưa khóa |
| **C-PLATFORM** | Với HVAC/door, core flow thật chạy `App → service fw → PropertyID → VHAL → KUKSA/VSS → CAN → CCU mô phỏng` | CarSky/VHAL/KUKSA/Script Node là platform; CCU được phép mô phỏng | Service fw, mapping, trace/callback và wiring của đội | Một `traceId` nối được voice intent với property write/readback và output platform | Contract M2 và real repository source; **chưa có runtime trace** | **E01–E08** | **ĐỎ — chưa được claim full-stack tới CAN** |

## 3. Evidence register cho C2 và bản nộp

Các đường dẫn dưới đây là **tên bắt buộc cần tạo từ lần chạy thật**. Tên xuất hiện trong bảng không có nghĩa file đã tồn tại; file chưa có thì gate vẫn đỏ.

| Evidence ID | Tệp phải nộp | Nội dung tối thiểu | Chủ trì |
|---|---|---|---|
| **E01** | `evidence/c2/device-info.txt` | `adb devices -l`, build fingerprint, user, thời gian, CarSky room/node nếu có | Dương + Vĩ |
| **E02** | `evidence/c2/artifact-identity.txt` | commit, branch, APK filename, SHA-256, flavor/config và thời gian cài | Dương |
| **E03** | `evidence/c2/viva-trace-device.log` | logcat thật có `VIVA_TRACE`/summary; không chỉnh tay | Vĩ |
| **E04** | `evidence/c2/harness-report.csv` | p50/p95 từ E03, gồm failure/timeout | Vĩ |
| **E05** | `evidence/c2/install-launch-crash.log` | install/launch result và crash scan sau demo | Dương |
| **E06** | `evidence/c2/hvac-temp-readback.txt` | property `358614275`, area `49`, trước/sau 24°C | Tùng |
| **E07** | `evidence/c2/hvac-fan-readback.txt` | property `356517120`, area `0`, trước/sau mức 3 | Tùng |
| **E08** | `evidence/c2/door-lock-readback.txt` | property `371198722`, area `1`, trước/sau locked | Tùng |
| **E09** | `evidence/c2/safety-speed60.log` | verdict deny, không setter/`exec_done`; sau đó là ablation A1 riêng | Tùng |
| **E09-emu** | `evidence/emulator/safety-speed60.log` | ✅ **Đã có 05/08** — cùng kịch bản trên emulator AAOS: `VivaSafetyGuard: Deny:G1_SPEED_LOCK property=371198722 area=1 value=false source=HMI`, cửa vẫn `Locked`, kèm ảnh màn hình. **Không thay thế E09**: property là mock, máy là emulator | Vĩ |
| **E10** | `evidence/c2/demo-10min-run-01.mp4` | một lần chạy liên tục; quay được Device/HMI và âm thanh | Long |
| **E11** | `evidence/c2/media-audio-focus.mp4` | track đổi thật và hiện tượng duck/release focus | Dương |
| **E11a** | `evidence/c2/carsky-runtime-20260809/` | ✅ APK/SHA/package identity + `VIVA_TRACE_SUMMARY` cho 3 media intent + `dumpsys media_session` đổi state/item trên Device; text-injection bỏ qua mic/VAD/ASR, không thay E11 | Long |
| **E12** | `evidence/c2/jvm-test-summary.txt` | **Đã có:** command, JDK, commit, 139 test và build hai APK; chỉ gắn nhãn JVM/source | Long |

## 4. Gate ra quyết định trước C2

| Gate | Pass khi | Nếu fail tối 02/08 |
|---|---|---|
| **G1 Artifact** | E01, E02, E05 cùng một lần cài | Không nói “đã chạy trên CarSky” |
| **G2 Service framework** | Service/AIDL/SafetyGuard tồn tại trong `automotive/`, build từ snapshot được chấm | Hạ C-HVAC/C-DOOR còn source/JVM; không claim kiến trúc mentor yêu cầu |
| **G3 Device core flow** | HVAC temp + fan + door có trace và readback thật | Demo mock phải dán nhãn **MÔ PHỎNG**; C-PLATFORM đỏ |
| **G4 Benchmark** | E03 có ≥20 lượt và E04 có p50/p95 tái lập | C2 chỉ báo “harness sẵn sàng”, không công bố KPI đạt |
| **G5 Media/audio** | E11 quan sát được đổi track + duck/release | Bỏ C-MEDIA khỏi claim C2 hoặc ghi **KẾ HOẠCH** |

## 5. Claim tuyệt đối chưa được nói

- “Toàn bộ 10 intent đi tới CAN.”
- “p95 dưới 1.500 ms” khi E03/E04 chưa tồn tại.
- “SafetyGuard đã chặn lệnh trên xe thật” — implementation **đã** nằm trong repo và đã chặn thật, nhưng trên **emulator với property mock**. Câu được phép nói: *“guard chặn lệnh mở cửa ở 60 km/h trước khi chạm setter, kiểm chứng trên emulator AAOS”*. Câu bị cấm: bất kỳ biến thể nào có chữ *xe thật*, *CarSky* hay *VHAL*.
- “Fixture là log CarSky” hoặc “139 unit test chứng minh Device integration”.
- “CCU thật”; CCU của demo được phép nhưng phải ghi **mô phỏng**.

## 6. Cách đóng N1

N1 chỉ được đánh dấu hoàn tất khi:

- mỗi claim giữ lại có ít nhất một evidence file thật, mở được và đúng identity;
- claim đỏ bị loại khỏi bài hoặc đổi câu chữ về `Kế hoạch`/`Mô phỏng`;
- N3 Baseline Manifest được nối vào cột baseline;
- map được cập nhật lần cuối từ đúng commit/artifact dùng cho video và nộp bài.
