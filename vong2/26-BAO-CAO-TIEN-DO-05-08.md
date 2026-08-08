# BÁO CÁO TIẾN ĐỘ — TEAM VIVA · 05/08/2026

> **Gửi:** BGK / mentor CDC · **Người lập:** Ngô Văn Long (đội trưởng)
> **Snapshot được báo cáo:** commit `b223552` (04/08/2026 23:12) · nhánh `chore/android-ci-and-device-gate`
> **Nguyên tắc của bản này:** mỗi dòng "đã xong" đều trỏ tới một file mở được trong repo.
> Việc chưa có bằng chứng thì ghi là **chưa**, không ghi là "gần xong".

---

## 1. Trả lời thẳng: đội demo được gì tại thời điểm này

**Có sản phẩm chạy được và quay video được — ở mức MÔ PHỎNG trên máy ảo AAOS, chưa phải trên Device CarSky.**

Kiểm chứng lại ngày 05/08, ngay trước khi viết báo cáo này:

| Việc | Kết quả | Bằng chứng |
|---|---|---|
| Cài APK lên Android Automotive OS 14 | **Success**, 3,1 giây | `evidence/c2/local-emulator-20260805/run-manifest.txt` |
| Mở app, process sống sau 12s | **PASS**, pid 6890 | cùng file |
| Quét crash/ANR/SecurityException | **Sạch** — log rỗng | `evidence/c2/local-emulator-20260805/install-launch-crash.log` |
| HMI render ở độ phân giải cockpit 1408×792 | Climate · Driver/Passenger Temp · Airflow · thanh mic | `evidence/c2/local-emulator-20260805/hmi-landscape-1408x792.png` |
| Toàn bộ unit test JVM | **181 test, 0 failure, 0 error** | chạy lại 05/08 trên commit `b223552` |
| Backend harness Go | `go test ./...` **pass toàn bộ package** | chạy lại 05/08 |
| Container ASR tiếng Việt trên CarSky | **22/22 node `Running`**, image pull theo digest | `evidence/carsky/v7-manifest.txt` |

**Cái chưa có:** một lượt end-to-end **trên Device CarSky** — nói câu tiếng Việt → intent → ghi Vehicle Property → đọc lại giá trị → HMI/TTS phản hồi. Đây là gate duy nhất đang chặn phần lớn điểm demo và điểm platform utilization.

Nói ngắn gọn cho BGK: **đội có sản phẩm, chưa có lượt chạy trên nền tảng.**

---

## 2. Bảng gate — trạng thái đến 05/08

| Gate | Nội dung | Trạng thái | Chặn bởi |
|---|---|---|---|
| **Build gate** | Compile, unit test, đóng gói 2 APK variant | 🟢 **MỞ** | — |
| **CI gate** | `android-ci` / `backend-ci` / `asr-ci` chạy trên PR | 🟢 **MỞ** | — |
| **Container gate** | Image `viva-asr` được CarSky pull và node `Running` | 🟢 **MỞ** | — |
| **Emulator gate** | APK chạy trên AAOS 14, HMI hiển thị, không crash | 🟢 **MỞ** (mới đóng 05/08) | — |
| **Device identity** | Đọc được serial/fingerprint Device `VIVA` | 🟡 **PARTIAL** | thiếu `adb devices -l` qua `nydus-reach` |
| **Device core flow** | HVAC/door write + readback thật trên CarSky | 🔴 **ĐÓNG** | quyền privileged VHAL chưa kiểm; `VivaCarService` chưa dựng |
| **Benchmark trên Device** | ≥20 lượt thật, p50/p95 tái lập | 🔴 **ĐÓNG** | phụ thuộc gate trên |
| **Media/audio focus** | Đổi track thật, TTS duck rồi nhả focus | 🔴 **ĐÓNG** | chưa có media adapter |
| **App → container ASR** | App gọi `viva-asr` qua mạng room | 🔴 **ĐÓNG** | source chưa có `AsrClient` thật; REST Conduit của CarSky không dùng được |

---

## 3. Bằng chứng đã có (mở được ngay)

| ID | File | Nói lên điều gì |
|---|---|---|
| E12 | `evidence/c2/jvm-test-summary.txt` | Test JVM xanh, hai APK build được — **chỉ là bằng chứng source**, không phải Device |
| E01 | `evidence/c2/device-info.txt` + `.png` | Device `VIVA` trên CarSky, serial `CUTTLEFISHCVD01`, Android 14 — **PARTIAL** |
| M1a | `evidence/c2/m1a-debuggable-privapp-probe.txt` | Image `ro.debuggable=1`, có `/system/priv-app` với 61 gói → cài privileged **khả thi về nguyên tắc**, nhưng shell hiện tại **không ghi được** vào đó |
| V7 | `evidence/carsky/v7-manifest.txt` + `v7-asr-node-phases.json` | Image của đội chạy thật trên CarSky, 22/22 node Running |
| ASR | `evidence/asr/asr-bench-manifest.txt` | RTF median 0,167 · `server_ms` p50 = 439 / p95 = 667 · WER 0,411 — **đo trên CPU máy dev, clip TTS tổng hợp** |
| A4 | `evidence/ablation/a4-grammar-ablation.csv` | Bỏ tầng grammar → **12/22 câu mất lệnh, 2 câu đáng lẽ bị từ chối lại thành lệnh xe thật** |
| **MỚI** | `evidence/c2/local-emulator-20260805/` | APK chạy trên AAOS 14, HMI hiển thị, không crash |

Con số đáng chú ý nhất với BGK là **A4**: nó định lượng được phần đội tự làm đóng góp bao nhiêu — bỏ tầng grammar của đội đi thì hệ thống mất 12 lệnh và **mở ra 2 hành vi không an toàn**.

---

## 4. Tiến độ từng thành viên

Số commit lấy từ `git log --all --no-merges` tính từ 21/07/2026. Commit không đo hết đóng góp — phần "cần thành viên tự bổ sung" là chỗ repo không nói được.

### 4.1 Ngô Văn Long — đội trưởng · AI Engineer (Voice AI & kiến trúc)

**26 commit · gần nhất 04/08**

Đã xong, có bằng chứng:
- Voice core: contract `LatencyTrace`, `AudioRecorder`, grammar router **10 intent** đủ bộ, `VoiceTurnReport`, audio focus (L7), Silero VAD, TTS speaker, 36 file WAV pre-render — `android/voice/`
- Bridge `CoreIntentMapper` nối voice core vào app AAOS — `automotive/feature/voice/`
- Ép ngôn ngữ mặc định về tiếng Việt (`fix(voice): default voice language to Vietnamese`)
- Thực thi SafetyGuard trên đường ghi vehicle (`fix(safety): enforce guard on vehicle writes`)
- CI Android + runbook device gate — `.github/workflows/android-ci.yml`, `vong2/25-CARSKY-AAOS-DEVICE-GATE.md`
- Bộ tài liệu nộp: Claim–Evidence Map (N1), Product & Integration Card (N2), write-up AI, Q&A BGK, kịch bản demo 3 phút, runbook tổng duyệt C2
- **Rà soát lệch kiến trúc voice pipeline** (`vong2/25-LECH-KIEN-TRUC-VOICE-PIPELINE.md`): tự phát hiện tài liệu của đội đang mô tả một pipeline khác với pipeline APK thật chạy, và sửa lại toàn bộ claim ở 7 file nộp

Đang chặn / còn nợ:
- E10 (video một lượt chạy liên tục) — chưa quay được vì thiếu Device
- Chưa gửi được tin nhắn F4/F5 cho Tùng qua kênh nhóm (nội dung đã soạn sẵn ở `25-LECH-...md` §0)

### 4.2 Lê Công Vĩ — Senior Backend (Agent & DevOps)

**24 commit · gần nhất 04/08** — người đóng nhiều gate nhất trong tuần này

Đã xong, có bằng chứng:
- **Benchmark harness `viva-tools` (Go)**: parse `VIVA_TRACE`, ngữ pháp verdict, tính p50/p95 giữ nguyên lượt lỗi trong mẫu, bộ 22 câu regression PASS/FAIL — `backend/`, `go test ./...` xanh
- **Container `viva-asr`**: PhoWhisper-tiny → CTranslate2 INT8, build được, chạy được, có số đo đầu tiên; image multi-arch push lên `registry.hackathon-2.carsky.io`
- **Đưa image lên CarSky thật**: 22/22 node `Running`, pull theo digest — đây là bằng chứng platform utilization mạnh nhất đội đang có
- **`SafetyGuard`**: đặt ở biên `VehicleRepository` (`GuardedVehicleRepository`) chứ không đặt trên đường voice — quyết định kiến trúc đúng, vì nó chặn **mọi** đường ghi chứ không chỉ đường giọng nói
- `DeliverySkill` 3 intent + luồng xác nhận 2 lượt, có xử lý huỷ khi lượt sau không phải câu xác nhận
- **Ablation A4** — số đo định lượng phần team-owned
- Lập bản đồ giới hạn REST API CarSky: `PATCH /pins/{id}` trả 404, không tạo được pin ETHERNET qua API, backup JSON không restore ngược được — `docs/backend-docs/carsky-api.md`

Đang chặn:
- Không gửi được request nào vào container ASR từ ngoài: nó nằm trên mạng `10.99.0.x` trong room; `adb-exec` / `shell` / `container-exec` đều trả `Conduit service not configured`, còn `nydus-reach` thì chưa có binary
- Hệ quả: mọi số latency của ASR vẫn là số CPU máy dev

### 4.3 Lê Đức Tùng — Embedded/System (VHAL & DTC)

**5 commit · gần nhất 01/08 (4 ngày trước)**

Đã xong, có bằng chứng:
- `vhal_server.luau` — Script Node Luau làm VHAL ↔ CAN hai chiều
- Cập nhật đúng PropertyID của AAOS và **polarity của door lock**
- `test_vhal_embedded.py`, `car_signals.dbc`, `uds_dtc_simulator.py`

Đang chặn — **đây là đường găng của cả đội**:
- **M1a — quyền privileged VHAL chưa ai kiểm.** Probe 03/08 mới chỉ chứng minh image cho phép về nguyên tắc; chưa `adb root` / `adb remount` thành công, chưa đẩy APK hay allowlist nào lên. Nếu `setProperty` bị `SecurityException` thì **toàn bộ 6 chặng full-stack sụp**.
- **M1 — `VivaCarService`** (service framework mà mentor yêu cầu ở kick-off 30/07) **chưa được dựng**. Hiện `VhalRepository` vẫn chỉ là library trong app, tức chỗ mentor chỉ ra là thiếu thì vẫn đang thiếu.
- **Lệch quyền chưa xử lý**: `AndroidManifest.xml` xin `android.car.permission.CAR_SPEED` nhưng allowlist `privapp-permissions-com.sopa.viva_automotive.xml` **không có quyền này**. SafetyGuard cần đọc tốc độ tin cậy cho luật G1 — không có `CAR_SPEED` thì G1 không có dữ liệu vào.
- E06/E07/E08/E09 (readback nhiệt độ, quạt, cửa, và log deny ở tốc độ 60) đều do Tùng chủ trì, **cả bốn đều chưa có**.

*Cần Tùng tự bổ sung:* phần Luau đã chạy trên blueprint CarSky chưa, hay mới chỉ chạy test cục bộ.

### 4.4 Việt Dương — Fullstack/Android (HMI & Media)

**1 commit · 29/07** — nhưng commit đó là **toàn bộ app AAOS** dưới `automotive/`, tức nền mà mọi thứ ở mục 1 đang chạy trên đó

Đã xong, có bằng chứng:
- App AAOS đa module: `:app`, `:core:common/ui/database`, `:feature:voice/hvac/vehicle-status/settings`, `:vehicle-service:api/impl`
- Hai flavor `mock` / `real` — đã xác nhận 05/08 là flavor `mock` **cài và chạy được trên AAOS 14, không crash**
- HMI cockpit: nav Climate / Vehicle / Settings, card Driver/Passenger Temp, nút mic

Đang chặn / còn nợ:
- **M3** — tách app HVAC và app DOOR riêng (kick-off 30/07 chốt "app phải tự bật lên và hiển thị"): chưa thấy trong repo
- **D7/D8** — media adapter (`MediaSession`) và `volume_adjust` qua `CarAudioManager`: **chưa có adapter nào**. Grammar nhận đúng intent nhưng không có gì nhận lệnh ở đầu kia → C-MEDIA đang đỏ
- E02 (artifact identity), E05 (install/launch/crash trên Device), E11 (video đổi track + duck) do Dương chủ trì, cả ba chưa có
- Text HMI đang là tiếng Anh — với sản phẩm bán câu chuyện "trợ lý tiếng Việt" thì đây là chỗ BGK sẽ nhìn thấy ngay

*Cần Dương tự bổ sung:* có nhánh làm việc chưa push không — repo hiện chỉ ghi nhận 1 commit từ 29/07.

---

## 5. Ba điều đội tự đánh giá là rủi ro thật

1. **Đường găng nằm ở một người và đã đứng 4 ngày.** M1a + M1 (Tùng) chặn 4 evidence ID, 3 claim và phần lớn ô "platform utilization". Không mở được cái này thì bản nộp phải hạ toàn bộ claim vehicle xuống nhãn *Mô phỏng*.
2. **Chênh lệch tải rất lớn giữa 4 người.** Long và Vĩ đóng góp 50/56 commit. Đây không phải lời phàn nàn — nó là rủi ro lịch: hai đầu việc của Tùng và Dương không ai gánh thay được vì cần quyền/kiến thức riêng.
3. **Đội đã tự bắt được một lỗi trung thực nghiêm trọng của chính mình và đã sửa.** Ngày 04/08 phát hiện tài liệu mô tả pipeline `mic → PTT → VAD → AsrClient` trong khi APK thật chạy `Vosk tự mở AudioRecord → grammar router`. Toàn bộ claim ở 7 file nộp đã được sửa lại theo đúng thứ đang chạy. Đội xin được tính đây là **bằng chứng về kỷ luật evidence**, và sẵn sàng trình bày nó trong phần "AI sai ở đâu".

---

## 6. Kế hoạch 05/08 → 10/08

| Ngày | Việc bắt buộc | Người | Xong nghĩa là |
|---|---|---|---|
| **05/08 tối** | Sửa AVD về màn hình landscape; quay video thuyết minh + demo mô phỏng | cả đội | Có file video nộp được sáng 06/08 |
| **06/08** | **M1a — kiểm quyền privileged VHAL trên Device** | Tùng | Có một dòng log `setProperty` thành công **hoặc** một `SecurityException` — cả hai đều là kết quả hợp lệ, im lặng thì không |
| **06/08** | Thêm `CAR_SPEED` vào allowlist privapp | Tùng | Manifest và allowlist khớp nhau |
| **06–07/08** | `VivaCarService` + AIDL | Tùng + Vĩ | Chỉ ra được service đang chạy trong APK được chấm |
| **06–07/08** | Media adapter + `volume_adjust`; Việt hoá HMI | Dương | "Chuyển bài" đổi track thật, TTS duck rồi nhả focus |
| **07/08** | `nydus-reach` tunnel → đóng E01 đủ, mở đường đo ASR trên CarSky | Vĩ | `adb devices -l` cục bộ ra Device `VIVA` |
| **08/08** | **Chạy trọn runbook `19-TONG-DUYET-C2-10-PHUT.md` trên Device** | cả đội | E01–E11 có file thật; biên bản gate pass/fail |
| **08/08** | Code freeze | — | Không sửa code sau mốc này trừ lỗi chặn |
| **09/08** | Video 3 phút không cắt ghép + video 5–7 phút bản nộp | cả đội | Đúng thời lượng, có nhãn *Mô phỏng* ở đúng chỗ |
| **10/08 trưa** | Nộp 8 deliverable | Long | Checklist nộp bài đóng hết |

---

## 7. Video ngày mai — quay được gì, và không quay gì

Chi tiết kịch bản ở `vong2/27-KICH-BAN-VIDEO-06-08.md`. Tóm tắt ranh giới:

**Được quay và được nói:**
- HMI VIVA chạy trên AAOS 14 thật, nói câu tiếng Việt → intent → HMI phản hồi (nhãn **MÔ PHỎNG**, vehicle layer là mock)
- Màn hình CarSky: node `VIVA ASR` phase `Running`, 22/22 node — đây là platform thật
- Kết quả test: 181 test JVM, harness Go, ablation A4 với 12 lệnh mất khi bỏ tầng grammar
- Bảng ba trạng thái *Đã tích hợp / Mô phỏng / Kế hoạch* — nói thẳng cái gì chưa chạy

**Tuyệt đối không nói trong video:**
- "Full-stack tới CAN" hay "đã chạy trên CarSky" cho luồng điều khiển xe
- Bất kỳ con số p95 end-to-end nào (chưa có E03/E04)
- "SafetyGuard đã chặn lệnh trên xe thật" — guard có thật trong code và có test, nhưng chưa chạy trên Device
- Số WER 0,411 mà không kèm ngay câu "đo trên giọng TTS tổng hợp, CPU máy dev"

---

## 8. Đề nghị với BGK / mentor

1. **`nydus-reach`** — đội cần binary hoặc đường lấy chính thức. Không có nó thì `adb-exec`, `shell`, `container-exec` đều trả `Conduit service not configured`, và đội không đo được gì trên nền tảng.
2. **Quyền privileged trên Device** — image là `userdebug` nhưng shell hiện tại không ghi được `/system/priv-app`. Xin xác nhận đường được phép: `adb root`/`remount`, hay có cơ chế allowlist khác.
3. **"Core flow chạy trên CarSky" được chấp nhận ở mức nào** — Device trong Room là đủ, hay phải kèm log/trace lấy từ chính platform? Câu trả lời quyết định đội dồn 3 ngày còn lại vào đâu.
