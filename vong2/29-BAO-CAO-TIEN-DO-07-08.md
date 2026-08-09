# BÁO CÁO TIẾN ĐỘ — TEAM VIVA · 07/08/2026

> **Gửi:** Mentor CDC / BGK · **Người lập:** Ngô Văn Long (đội trưởng)
> **Snapshot được báo cáo:** commit `f93751e` · nhánh `main` (đã đồng bộ với `origin/main`)
> **Nguyên tắc của bản này:** mọi con số dưới đây được chạy lại trong ngày 07/08 trước khi viết.
> Việc chưa có bằng chứng thì ghi **chưa**, không ghi "gần xong". Việc đã làm nhưng chưa hợp
> nhất vào `main` thì ghi rõ là **chưa hợp nhất**, không tính là xong.

---

## 1. Trả lời thẳng ba câu mentor hỏi

**① "Đã chuẩn bị demo được gì chưa?"**

Có. Đội demo được **một sản phẩm chạy thật trên Android Automotive OS 14**, ở nhãn **MÔ PHỎNG**
(máy ảo AAOS trên máy dev, lớp xe là repository mô phỏng). Chi tiết ở §4.

**② "Video thuyết minh, demo sản phẩm đến thời điểm hiện tại."**

Kịch bản quay đã cập nhật theo đúng trạng thái hôm nay: `vong2/30-KICH-BAN-VIDEO-07-08.md`.

**③ "Nếu chưa demo cần file báo cáo tiến độ từng thành viên và cả team chạy đến đâu."**

Đây là file đó. Tiến độ từng người ở §6.

---

## 2. Điều quan trọng nhất trong bản báo cáo này

**Đội không thiếu công việc. Đội thiếu một lần hợp nhất.**

Phần lớn tiến bộ 48 giờ qua đang nằm trên **bốn nhánh chưa merge**, trong khi `main` — nhánh sẽ
được chấm — chưa có chúng:

| Nhánh | Người | Commit chưa merge | Nội dung | CI |
|---|---|---|---|---|
| `fix/nlu-chiu-loi-asr-tren-main` | Vĩ | 6 | Router chịu được lỗi thanh điệu của ASR (**18/34 → 34/34**), đọc được số viết bằng chữ (**34/41 → 41/41**), chặn false-accept `"sung"`/`"xăng"`, 2 phiên emulator mới | 🟢 **xanh** |
| `feature/automotive` | Dương | 4 | **Module media đầy đủ**: `MediaScreen`, `MediaViewModel`, `VivaMediaRepository` (464 dòng), cast, radio catalog, metadata inspector | — |
| `tung4506/vhal&dtc` | Tùng | 5 | **Property ID Luau đúng chuẩn AAOS**, safety scenario pack 8 tình huống, `VHAL_BASELINE_MANIFEST.md` | — |
| `feat/router-chiu-loi-thanh-dieu` | Vĩ | 8 | Runbook bật mic ảo cho emulator, chế độ `-Record` để thu ngữ liệu nói thật | — |

Hệ quả cụ thể, đo được:

- Tài liệu của đội đang ghi *"C-MEDIA đỏ — chưa có adapter nào"*. **Sai.** Adapter có thật, 12 file,
  chỉ là `:feature:media` chưa nằm trong `settings.gradle.kts` của `main`.
- `main` đang chạy `vhal_server.luau` với Property ID **không khớp** bảng M2 và app. Bản đúng nằm ở
  nhánh Tùng và **merge sạch, không xung đột**.
- `main` đang có **1 unit test đỏ** (§3), còn nhánh của Vĩ thì CI xanh.

Đây là rủi ro lịch nghiêm trọng hơn bất kỳ tính năng nào còn thiếu, vì nó không cần thêm code —
chỉ cần một buổi hợp nhất.

---

## 3. Kiểm chứng chạy lại ngày 07/08

| Kiểm chứng | Lệnh | Kết quả |
|---|---|---|
| Unit test JVM | `automotive/gradlew test` | 🟡 **256 test · 255 pass · 1 fail** |
| Test service ASR | `asr/python -m pytest -q` | 🟢 **39 passed** |
| Backend harness Go | `backend/go test ./...` | 🟢 pass toàn bộ package |
| Build APK | đã có sẵn | 🟢 `mock` 334,9 MB · `real` 334,3 MB |

**Về 1 test đỏ:** đó là `GrammarIntentRouterTest > media play keeps an optional query…`. Nguyên nhân
là **kỳ vọng trong test viết sai**, không phải lỗi sản phẩm — router trả `"playlist một ngày mới"`
(đúng), test kỳ vọng `"một ngày mới"`. Sửa một dòng. Đội ghi ra đây thay vì lặng lẽ vá để giữ đúng
nguyên tắc evidence của mình.

**Về CI:** hai workflow trên `main` cho commit `f93751e` kết thúc `failure` vì job chính bị
**cancelled** (job `pytest` của `asr-ci` vẫn pass). Chưa có bằng chứng CI cho snapshot hiện tại.
Nhánh của Vĩ ở `831924e` thì `android-ci` và `backend-ci` **đều xanh**.

---

## 4. Demo được gì hôm nay — và không demo gì

### Demo được, có bằng chứng mở ngay

| Nội dung | Bằng chứng |
|---|---|
| APK cài lên AAOS 14, mở sạch, không crash/ANR | `evidence/c2/local-emulator-20260805/install-launch-crash.log` |
| HMI cockpit render ở 1408×792: Climate · Driver/Passenger Temp · Airflow · nút mic | `.../hmi-landscape-1408x792.png` |
| **SafetyGuard chặn thật**: mở khóa cửa ở 60 km/h → `Deny:G1_SPEED_LOCK`, cửa không bị ghi | `evidence/emulator/safety-speed60.log` + ảnh HMI |
| **Bộ 22 câu chạy trên app thật**: 17 PASS · 5 FAIL · 0 MISSING (5 FAIL đều là *known gap* media/volume) | `evidence/emulator/session-20260805-165301/results.csv` |
| **Ablation A4**: bỏ tầng grammar của đội → **12/22 câu mất lệnh**, **2 câu đáng lẽ bị từ chối lại thành lệnh xe thật** | `evidence/ablation/a4-grammar-ablation.csv` |
| **Container ASR tiếng Việt chạy trên CarSky**: 22/22 node `Running`, image pull theo digest | `evidence/carsky/v7-manifest.txt` |
| Bộ ngữ liệu giọng thật 20 câu × 4 mức nhiễu, thu thẳng ở 16 kHz | `evidence/asr/corpus-human/` |

### Không demo, và nói rõ vì sao

- **Chưa có một lượt end-to-end trên Device CarSky.** Đường vào đang đứt ở phía nền tảng: họ endpoint
  `adb-exec`/`shell`/`container-exec` trả `502 "Conduit service not configured"` kể cả khi room chạy
  đủ 22/22 node, và đội chưa lấy được binary `nydus-reach`. Bằng chứng và bốn phép thử loại trừ:
  `docs/backend-docs/carsky-api.md` §4.
- **Chưa nói bất kỳ số p95 end-to-end nào** — chưa có E03/E04.
- **Chưa nói "full-stack tới CAN"** cho luồng điều khiển xe.
- **Chưa có lượt nào qua micro thật.** Cả 3 phiên benchmark đều là **bơm text**, đo
  `router → guard → skill`, không đo mic/VAD/ASR/độ ồn. Mỗi phiên tự khai điều này trong
  `run_manifest.txt`.

---

## 5. Phát hiện lớn nhất tuần này: đội đo sai chính mình

Ngày 06/08 đội chạy ma trận model ASR và ghi kết luận *"intent accuracy 28,75% — chưa chọn được
model"*. Rà lại ngày 07/08 cho thấy **con số đó là lỗi thước đo, không phải lỗi sản phẩm.**

Nguyên nhân: `evidence/asr/v13/score.py` là một **bản port `GrammarIntentRouter` sang Python**. Chính
spec của đội đã cấm việc này bằng văn bản:

> *"Không port router sang Python. Router chính là thứ đang được kiểm; một bản sao Python sẽ trôi khỏi
> bản Kotlin và biến con số thành vô nghĩa đúng lúc nó bắt đầu quan trọng."*

Đội **đã viết đúng công cụ** cho việc này (`IntentAccuracyScorer.kt`, chấm qua router thật) rồi không
dùng nó. Chấm lại **chính bốn file CSV đó** bằng router thật:

| Cấu hình | Bản port Python | **Router thật (Kotlin)** | `server_ms` p95 |
|---|---|---|---|
| **PhoWhisper-tiny, không biasing** | 28,75% | **65,00%** | **244 ms** |
| PhoWhisper-base, có biasing | 21,25% | 43,75% | 6265 ms |
| PhoWhisper-base, không biasing | 11,25% | 30,00% | 920 ms |
| PhoWhisper-tiny, có biasing | 5,00% | 5,00% | 4238 ms |

Thêm nữa, 16/80 dòng trong mẫu là các câu **cố ý không sinh hành động** (ngoài phạm vi, thiếu slot),
nhưng vẫn nằm ở mẫu số — nên trên tập câu thật sự là lệnh xe, tỉ lệ còn cao hơn 65%.

**Ba kết luận:**

1. Cấu hình **PhoWhisper-tiny INT8, không domain biasing** vừa nhanh nhất vừa chính xác nhất → chọn
   được theo đúng luật đã chốt trước khi thấy số, không cần build thêm bậc `small`.
2. Kết luận *"chưa chọn được model"* trong manifest v13 phải viết lại.
3. Đội xin được tính đây là một mục cho phần **"AI sai ở đâu"** của write-up: một quy trình có AI hỗ trợ
   đã sinh ra một công cụ đo *trông hợp lý* nhưng lệch khỏi thứ đang được đo, và chính ràng buộc đội tự
   đặt ra trong spec là thứ bắt được nó.

---

## 6. Tiến độ từng thành viên

Số commit lấy từ `git log --all --no-merges` tính từ 21/07/2026, gộp cả bí danh Git của cùng một người.
Commit không đo hết đóng góp; phần "cần bổ sung" là chỗ repo không nói được.

### 6.1 Ngô Văn Long — đội trưởng · AI Engineer (Voice AI & kiến trúc)

**45 commit · gần nhất 07/08**

Đã xong, có bằng chứng:

- **Hợp nhất đường mic/VAD/ASR** — thành tựu kiến trúc lớn nhất tuần: nay chỉ còn **một** chủ sở hữu
  microphone trong toàn repo (`grep -rn 'AudioRecord('` ra đúng 1 kết quả). Silero VAD đã nằm trên
  đường chạy APK với pre-roll 500 ms; trước đó VAD có code, có test, nhưng không bao giờ được gọi.
- Tách `acousticConfidence` khỏi `nluConfidence`; Vosk trả `null` **có chủ đích** thay vì bịa `1.0`.
- Adapter remote PhoWhisper đọc chung dòng PCM với VAD/Vosk (`-PvivaAsrEngine=remote`).
- Thay embedding NLU tầng T1 từ MiniLM tiếng Anh sang **DistilUSE đa ngữ**, hiệu chỉnh lại ngưỡng
  cosine bằng số đo (0,82 — cặp đúng ≥ 0,849, đối chứng ngoài phạm vi đỉnh 0,797).
- Chuẩn hoá số tiếng Việt trong router (`"hai lăm"` → 25), gỡ prompt biasing 161 ký tự gây lặp từ.
- Bộ ngữ liệu **giọng thật 20 câu**, thu thẳng ở 16 kHz — gỡ cùng lúc hai giới hạn của bộ đo cũ
  (giọng tổng hợp + resample).
- Bộ tài liệu nộp: Claim–Evidence Map, Product & Integration Card, write-up, Q&A BGK, kịch bản demo,
  runbook CarSky device gate, spec + plan nâng cấp model.

Đang chặn / còn nợ:

- 1 test đỏ đang nằm trên `main` (§3).
- Chưa hợp nhất bốn nhánh ở §2 — **đây là việc của đội trưởng và là ưu tiên số 1**.
- Tài liệu nộp (write-up, Q&A, slide) đang mô tả snapshot 04/08, tức **tự khai thấp hơn** thực tế:
  vẫn ghi *"Silero VAD chưa cắm"*, *"SafetyGuard chưa có"*, *"139 test"*, *"MiniLM"*.

### 6.2 Lê Công Vĩ — Senior Backend (Agent & DevOps)

**50 commit · gần nhất 06/08** — người đóng nhiều gate nhất

Đã xong, có bằng chứng:

- **Benchmark harness `viva-tools` (Go)**: parse `VIVA_TRACE`, ngữ pháp verdict, p50/p95 giữ nguyên
  lượt lỗi trong mẫu, bộ 22 câu PASS/FAIL. Không dependency ngoài standard library.
- **Container `viva-asr`**: PhoWhisper → CTranslate2 INT8, image multi-arch, **đã được CarSky pull theo
  digest và chạy 22/22 node**. Đây là bằng chứng platform mạnh nhất đội đang có.
- **`SafetyGuard` đặt ở biên `VehicleRepository`** thay vì trên đường voice — quyết định kiến trúc
  đúng, vì nó chặn **mọi** đường ghi (giọng nói *và* chạm HMI), không chỉ đường giọng nói.
- **Ablation A4** — số đo định lượng phần team-owned.
- `DeliverySkill` 3 intent + luồng xác nhận hai lượt có xử lý huỷ.
- Lập bản đồ giới hạn REST API CarSky (`docs/backend-docs/carsky-api.md`) — tài liệu tốt nhất repo.
- **Chưa merge:** router chịu lỗi thanh điệu ASR 18/34 → 34/34, số viết bằng chữ 34/41 → 41/41,
  chặn false-accept, runbook bật mic ảo, chế độ `-Record` thu ngữ liệu nói thật.

Đang chặn:

- Không gửi được request nào vào container ASR từ ngoài room (Conduit 502, thiếu `nydus-reach`).
  Hệ quả: mọi số latency ASR vẫn là số CPU máy dev.

### 6.3 Lê Đức Tùng — Embedded/System (VHAL & DTC)

**7 commit · gần nhất 06/08**

Đã xong, có bằng chứng:

- `vhal_server.luau` — Script Node Luau làm VHAL ↔ CAN hai chiều, có tầng safety G1.
- **Chưa merge:** cập nhật Property ID đúng chuẩn AAOS (`0x15600503`, `0x15400500`, `0x16200b02`) và
  polarity door lock; safety scenario pack 8 tình huống; `VHAL_BASELINE_MANIFEST.md`.
- `test_vhal_embedded.py`, `uds_dtc_simulator.py`.

Đang chặn — **đây là đường găng của cả đội**:

- **M1a — quyền privileged VHAL chưa ai kiểm.** Probe 03/08 chứng minh image là `userdebug`,
  `ro.debuggable=1`, có `/system/priv-app` 61 gói → khả thi về nguyên tắc; nhưng shell hiện tại
  **không ghi được** vào đó, chưa `adb root`/`remount` thành công.
- **M1 — `VivaCarService`** (service framework mentor yêu cầu ở kick-off 30/07) **chưa được dựng**.
- **Lệch quyền chưa xử lý**: manifest xin `android.car.permission.CAR_SPEED` nhưng quyền này chưa
  được cấp bằng bất kỳ đường nào — app chỉ xin runtime `RECORD_AUDIO`, và allowlist privapp không có
  `CAR_SPEED`. Hệ quả trên flavor `real`: `SafetyGuard` không đọc được tốc độ → **mọi lệnh mở cửa đều
  bị từ chối** với `G1_STALE_STATE`. An toàn, nhưng kịch bản demo "xe dừng → xác nhận → mở cửa"
  sẽ không chạy được.
- Bốn mục trong `docs/dbc/README.md` mục *"Cần xử lý"* vẫn mở: `EngineData` không tồn tại (xe là EV),
  quy đổi quạt percent ↔ mức 0–5, `HVAC_POWER_ON` chưa biết map vào signal nào, `DOOR_LOCK` là 4 cửa
  riêng chứ không phải 1.
- 5 dòng 🟠 **CHỜ N3b** trong Baseline Manifest — đầu vào cho 8 điểm khối team-owned.

### 6.4 Việt Dương — Fullstack/Android (HMI & Media)

**4 commit · gần nhất 05/08**

Đã xong, có bằng chứng:

- **Toàn bộ app AAOS đa module** dưới `automotive/`: `:app`, `:core:common/ui/database`,
  `:feature:voice/hvac/vehicle-status/settings`, `:vehicle-service:api/impl`. Đây là nền mà mọi thứ
  ở §4 đang chạy trên đó.
- Hai flavor `mock` / `real`; flavor `mock` đã xác nhận cài và chạy trên AAOS 14 không crash.
- **Chưa merge — và đây là chỗ tài liệu đội đang nói sai về Dương:** module media đầy đủ
  (`MediaScreen`, `MediaViewModel`, `VivaMediaRepository` 464 dòng, cast, radio catalog, metadata
  inspector) đã tồn tại trên `feature/automotive`, nhưng `:feature:media` chưa nằm trong
  `settings.gradle.kts` của `main`.

Đang chặn / còn nợ:

- Merge nhánh `feature/automotive` là nhánh có dấu hiệu xung đột cao nhất trong bốn nhánh — cần làm
  sớm, không để tới hạn.
- Text HMI vẫn là tiếng Anh (`Climate`, `Airflow`, `Fan speed`) — với sản phẩm bán câu chuyện trợ lý
  **tiếng Việt** thì đây là chỗ BGK nhìn thấy đầu tiên.
- E02 (artifact identity), E05 (install/launch/crash trên Device), E11 (video đổi track + duck) chưa có.

---

## 7. Bảng gate — trạng thái 07/08

| Gate | Nội dung | Trạng thái |
|---|---|---|
| Build gate | Compile, đóng gói 2 APK variant | 🟢 **MỞ** |
| Test gate | Toàn bộ unit test xanh | 🟡 **255/256** — 1 kỳ vọng test viết sai |
| CI gate | `android-ci` / `asr-ci` xanh trên `main` | 🔴 **ĐÓNG** — job bị cancelled, chưa có bằng chứng CI cho `f93751e` |
| **Merge gate** | Công việc của cả 4 người nằm trên `main` | 🔴 **ĐÓNG** — 4 nhánh, 23 commit chưa hợp nhất |
| Container gate | Image `viva-asr` được CarSky pull, node `Running` | 🟢 **MỞ** |
| Emulator gate | APK chạy trên AAOS 14, HMI hiển thị, guard chặn thật | 🟢 **MỞ** |
| Device identity | Đọc được serial/fingerprint Device `VIVA` | 🟡 **PARTIAL** — thiếu `adb devices -l` qua `nydus-reach` |
| Device core flow | HVAC/door write + readback thật trên CarSky | 🔴 **ĐÓNG** — quyền privileged chưa kiểm; `VivaCarService` chưa dựng |
| Benchmark trên Device | ≥20 lượt thật, p50/p95 tái lập | 🔴 **ĐÓNG** |
| Lượt qua micro thật | Một câu nói thật đi hết pipeline | 🔴 **ĐÓNG** — cả 3 phiên đều là bơm text |

---

## 8. Ba rủi ro đội tự đánh giá là thật

1. **Rủi ro hợp nhất, không phải rủi ro tính năng.** 23 commit trên 4 nhánh, trong đó có module media
   và bản sửa Property ID — hai thứ mà tài liệu của chính đội đang khai là "chưa có". Nếu không hợp
   nhất trước code freeze, đội sẽ nộp một bản `main` **kém hơn** thứ đội thật sự đã làm.

2. **Đường găng vẫn nằm ở một người.** M1a + M1 (Tùng) chặn 4 evidence ID và phần lớn ô platform
   utilization. Nếu không mở được, bản nộp phải hạ toàn bộ claim vehicle xuống nhãn *Mô phỏng*.

3. **Ba tài liệu nộp đang mô tả snapshot 04/08.** Write-up, Q&A và slide vẫn ghi *"Silero VAD chưa
   cắm"*, *"SafetyGuard chưa có"*, *"139 test"*, *"MiniLM"*. Sau khi đội sửa lỗi over-claim ngày 04/08,
   code đi tiếp ba ngày mà tài liệu đứng lại — giờ lệch theo chiều ngược lại. Với barem chấm
   *"nhất quán với log, artifact, version và evidence map"*, lệch chiều nào cũng mất điểm như nhau.

---

## 9. Kế hoạch 07/08 → 10/08

| Ngày | Việc bắt buộc | Người | Xong nghĩa là |
|---|---|---|---|
| **07/08 tối** | Sửa 1 dòng test đỏ; **merge nhánh Tùng** (không xung đột) | Long | `gradlew test` xanh; Property ID Luau khớp bảng M2 |
| **07/08 tối** | Chấm lại v13 bằng `IntentAccuracyScorer`, viết lại manifest, chốt `tiny-nobias` | Long | Manifest có số 65% + khối GIỚI HẠN |
| **08/08 sáng** | **Merge nhánh Vĩ** (CI xanh) rồi **nhánh Dương** (media) | Long + Vĩ + Dương | `:feature:media` có trong `settings.gradle.kts`; CI xanh trên `main` |
| **08/08** | Kiểm quyền `CAR_SPEED` trên Device; M1a quyền privileged VHAL | Tùng | Một dòng `setProperty` thành công **hoặc** một `SecurityException` — im lặng thì không |
| **08/08** | Thêm `Stage.GUARD_DONE` + `Stage.RENDER_DONE` | Long + Dương | Báo cáo harness không còn 6/12 cột trống |
| **08/08** | Chạy lại bộ 22 câu trên emulator sau khi merge | Vĩ | Bộ regression chưa chạy lần nào sau khi đổi embedding |
| **08/08 23:59** | **Code freeze** | — | Sau mốc này chỉ sửa lỗi chặn, đo đạc, tài liệu, quay video |
| **09/08** | Đồng bộ write-up + Q&A + slide + 3 README theo trạng thái thật | Long | Không còn "139 test", "MiniLM", "SafetyGuard chưa có" |
| **09/08** | Video 3 phút không cắt ghép + video bản nộp | Cả đội | Đúng thời lượng, nhãn *Mô phỏng* ở đúng chỗ |
| **10/08 trưa** | Nộp 8 deliverable | Long | Checklist nộp bài đóng hết |

---

## 10. Đề nghị với BGK / mentor

1. **`nydus-reach`** — đội cần binary hoặc đường lấy chính thức. Không có nó thì `adb-exec`, `shell`,
   `container-exec` đều trả `502 "Conduit service not configured"`, và đội không đo được gì trên
   nền tảng. Đội đã loại trừ bốn nguyên nhân từ phía mình; xem `docs/backend-docs/carsky-api.md` §4b.

2. **Quyền privileged trên Device** — image là `userdebug`, `ro.debuggable=1`, có `/system/priv-app`,
   nhưng shell hiện tại không ghi được vào đó. Xin xác nhận đường được phép: `adb root`/`remount`,
   hay có cơ chế allowlist khác.

3. **"Core flow chạy trên CarSky" được chấp nhận ở mức nào** — Device trong Room có đủ, hay phải kèm
   log/trace lấy từ chính platform? Câu trả lời quyết định đội dồn ba ngày còn lại vào đâu.

4. **Lịch và hình thức phiên demo trực tiếp + Q&A của Vòng 2** — thể lệ ghi *"BTC thông báo riêng"*
   mà đội chưa nhận được. 11 điểm (6đ demo live + 5đ trình bày) phụ thuộc câu này.
