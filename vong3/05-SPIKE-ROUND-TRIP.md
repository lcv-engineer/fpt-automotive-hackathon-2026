# RUNBOOK G-A / G-B — QUYỀN PRIV-APP VÀ ROUND-TRIP VHAL

> **Chủ trì:** Vĩ (G-A, hạ tầng) · Tùng (G-B, VHAL) · **Hạn:** G-A 19/08 22:00 · G-B 20/08 22:00
>
> **Mục tiêu điểm:** ô `+10 tận dụng tối đa nền tảng & starter pack` và vế "readback có thẩm quyền"
> trong khoảnh khắc ① của kịch bản.

---

## 0. Đã biết gì trước khi bắt đầu

Không bắt đầu từ số không. Probe M1a ngày 03/08 (`evidence/c2/m1a-debuggable-privapp-probe.txt`)
đã xác lập trên chính Device CarSky:

| Sự kiện | Giá trị | Nghĩa |
|---|---|---|
| `ro.debuggable` | **1** | Image là userdebug → `adb root` **về nguyên tắc** dùng được |
| `ro.secure` | 1 | — |
| `/system/priv-app` | tồn tại, 61 entry, `root:root` | Cài priv-app là khả thi về nền tảng |
| Shell hiện tại ghi được `/system/priv-app`? | **không** | Cần `adb root` + `adb remount` |
| `adb root` / `adb remount` | **chưa từng thử** | ← Đây chính là G-A |
| Device | `CUTTLEFISHCVD01` | Cuttlefish → có AVB, có thể phải `disable-verity` |

Và allowlist đã viết sẵn, đúng định dạng:
`automotive/app/privapp-permissions-com.sopa.viva_automotive.xml` — 7 permission, trong đó
`CONTROL_CAR_CLIMATE`, `CONTROL_CAR_DOORS`, `CONTROL_CAR_INTERIOR_LIGHTS` là thứ cần cho `setProperty`.

> 💡 **Cơ chế này đã chạy thành công trên emulator** — commit `5cb9b31` mô tả *"complete privileged
> permission allowlist for emulator climate writes"*. Nghĩa là allowlist và cách cài đúng; ẩn số duy
> nhất là **Device CarSky có cho `adb root`/`remount` qua kênh của nó hay không**.

---

## 1. Rủi ro thật của G-A

Không phải Android từ chối, mà là **kênh truy cập**.

ADB tới Device đi qua widget `IVI ADB` trong UI CarSky (`face-adb`) — một terminal trong trình duyệt.
REST `adb-exec` / `adb-shell` đã trả **502 (Conduit)** ở phiên 10/08, nên log lúc đó phải **dán tay**.

`adb root` là lệnh của **adb host**, không phải lệnh shell. Nếu widget chỉ cấp một shell trên Device
thì `adb root` không gọi được từ trong đó.

**Vì vậy G-A thực chất hỏi hai câu, theo thứ tự:**
1. Có kênh nào chạy được lệnh **adb host** (không phải shell) tới Device không?
2. Nếu có, `adb root` + `adb remount` có thành công không?

Nếu câu 1 là không thì G-A fail sớm — và đó là kết quả hợp lệ, ghi lại rồi chuyển hướng ngay,
**không đốt thêm giờ**.

---

## 2. G-A — trình tự

Chạy từng bước, ghi output từng bước vào `evidence/vong3/ga-privapp-probe.txt`.

```bash
# B1 — xác nhận kênh và danh tính Device
adb devices -l
adb shell getprop ro.build.fingerprint
adb shell id

# B2 — thử quyền root ở tầng adb host   ← ĐÂY LÀ CÂU HỎI CHÍNH
adb root
adb devices -l          # phải thấy device quay lại sau vài giây
adb shell id            # kỳ vọng: uid=0(root)

# B3 — Cuttlefish có AVB, thường phải tắt verity trước khi remount
adb disable-verity
adb reboot
# chờ device boot xong
adb wait-for-device
adb root
adb remount             # kỳ vọng: "remount succeeded"

# B4 — xác nhận ghi được
adb shell touch /system/priv-app/.viva_write_test && echo WRITABLE
adb shell rm -f /system/priv-app/.viva_write_test
```

### Gate G-A

| Kết quả | Nghĩa | Làm gì tiếp |
|---|---|---|
| B4 in ra `WRITABLE` | ✅ **PASS** | Sang §3 ngay trong đêm |
| B2 không cho root | ❌ FAIL | Ghi lại, **dừng**, sang §5 |
| B3 remount thất bại | ❌ FAIL | Thử `adb remount -R` một lần. Không được thì dừng, sang §5 |
| Không gọi được lệnh adb host | ❌ FAIL sớm | Hỏi mentor/supporter ngay tối 19/08. Đừng tự mò quá 1 giờ |

> ⏱ **Hộp thời gian cứng: 3 giờ.** Quá 3 giờ mà chưa qua B4 thì tuyên bố fail và sang §5. Ba giờ này
> không được ăn vào G-C.

---

## 3. Cài priv-app — chỉ chạy khi G-A pass

```bash
# gỡ bản thường trước, tránh hai bản cùng package
adb uninstall com.sopa.viva_automotive || true

# đẩy APK vào phân vùng privileged
adb shell mkdir -p /system/priv-app/VivaAutomotive
adb push app-real-debug.apk /system/priv-app/VivaAutomotive/VivaAutomotive.apk
adb shell chmod 644 /system/priv-app/VivaAutomotive/VivaAutomotive.apk

# đẩy allowlist
adb push automotive/app/privapp-permissions-com.sopa.viva_automotive.xml \
         /system/etc/permissions/
adb shell chmod 644 /system/etc/permissions/privapp-permissions-com.sopa.viva_automotive.xml

adb reboot
adb wait-for-device
```

⚠️ **Cảnh báo bootloop:** thiếu entry allowlist cho permission `signature|privileged` sẽ làm
`system_server` crash với *"not in privileged permission allowlist"* — máy vào bootloop. Nếu xảy ra:
`adb root && adb remount && adb shell rm -rf /system/priv-app/VivaAutomotive && adb reboot`.
**Chụp lại toàn bộ output trước khi sửa** — kể cả bootloop cũng là bằng chứng platform hợp lệ.

### Xác nhận quyền đã được cấp

```bash
adb shell dumpsys package com.sopa.viva_automotive | grep -i "CONTROL_CAR"
```

Kỳ vọng thấy `granted=true` cho `CONTROL_CAR_CLIMATE` và `CONTROL_CAR_DOORS`.

---

## 4. G-B — `setProperty` và readback có thẩm quyền

Đây là thứ BTC gọi là *"authoritative readback"* và là vế còn thiếu của khoảnh khắc ①.

**Ba property mục tiêu** (đã chốt từ Vòng 2, `vong2/03-contracts.md` §M2):

| Chức năng | propertyId | areaId | Giá trị thử |
|---|---|---|---|
| Nhiệt độ HVAC | `358614275` | `49` | `24.0` |
| Quạt HVAC | `356517120` | `0` | `3` |
| Khoá cửa | `371198722` | `1` | `true` |

**Quy trình cho mỗi property — bắt buộc đủ 3 nhịp:**

1. **Đọc trước** — ghi giá trị hiện tại
2. **Ghi** — qua chính app (không qua `adb shell cmd car_service`, vì phải chứng minh **app** ghi được)
3. **Đọc lại** — giá trị mới, đọc từ `CarPropertyManager`, **không phải** từ biến trong app

Ghi ra `evidence/vong3/gb-hvac-temp-readback.txt`, `gb-hvac-fan-readback.txt`, `gb-door-readback.txt`.
Mỗi file phải có: commit, APK SHA-256, Device serial, thời điểm, và cả ba nhịp.

### Gate G-B

| Kết quả | Làm gì |
|---|---|
| Cả 3 property đọc lại đúng giá trị vừa ghi | ✅ **PASS** — khoảnh khắc ① giữ nguyên vế readback |
| Chỉ HVAC được, cửa không | ⚠️ Một phần — demo chỉ dùng HVAC, nói rõ cửa chưa đóng |
| Không property nào ghi được | ❌ FAIL — bỏ vế readback khỏi kịch bản, xem `01-KICH-BAN` §4① |

---

## 5. Nếu G-A hoặc G-B fail — chuyển hướng, không đốt thêm giờ

**Đây không phải thất bại.** Ô `+10 tận dụng nền tảng` của Vòng 3 vẫn ăn được trọn vẹn bằng đường khác,
và đường đó đã chạy rồi.

Chuyển toàn bộ giờ còn lại của Vĩ và Tùng sang §6 — **correlated trace**. Nó phục vụ trực tiếp
khoảnh khắc ② (nền tảng là điều kiện cần), vốn là đòn chính vào ô +10.

Câu nói trên sân khấu khi G-B fail, đã soạn sẵn ở `02-QA-BGK-CHUNG-KET.md` mục A1:
> *"Chúng em không claim luồng App → VHAL → CAN → CCU đã hoàn tất."*

Nói đúng câu đó. Vòng 2 ăn trần 5/5 ô minh bạch bằng chính cách nói này.

---

## 6. Correlated trace — làm bất kể G-A/G-B pass hay fail

BTC lặp lại yêu cầu này ở **5 tiểu mục khác nhau**. Nó là deliverable đáng giá nhất của Làn 3.

> *"cần một correlated trace từ audio/transcript qua intent/policy đến VHAL/Media readback và
> failure recovery trong cùng run"*
> *"cần một biên nhận có cùng identity cho app, ASR node, invocation, policy verdict và
> vehicle/media readback"*

**Một lượt chạy, một `traceId`, nối đủ các mốc:**

```
traceId=<id>  speech_start
traceId=<id>  asr_sent        → node viva-asr 10.99.0.3:8080
traceId=<id>  asr_done        transcript="nhiệt độ hai tư độ"  conf=0.xx
traceId=<id>  nlu_done        intent=hvac_set_temp  slots={temp:24}
traceId=<id>  guard_verdict   Allow
traceId=<id>  exec_start      property=358614275 area=49 value=24.0
traceId=<id>  readback        property=358614275 area=49 value=24.0   ← chỉ khi G-B pass
traceId=<id>  tts_start
traceId=<id>  VIVA_TRACE_SUMMARY  e2e_ms=xxxx
```

**Yêu cầu đóng gói — đây là chỗ Vòng 2 mất điểm Artifact identity (1/2):**

- [ ] Một commit duy nhất, đã push, không phải trạng thái cây làm việc
- [ ] APK SHA-256 khớp giữa bản build, bản tải về và `base.apk` trên Device (`pm path` + `sha256sum`)
- [ ] Device serial và build fingerprint
- [ ] Digest image của node `viva-asr`
- [ ] Thời điểm chạy
- [ ] Log lấy bằng cách tái lập được, ghi rõ nếu phải dán tay

Ghi vào `evidence/vong3/correlated-trace-<ngày>/`.

> ⚠️ Phiên 10/08 mất điểm vì APK là `main@214914e` **cộng thêm** PR #42 chưa merge — trỏ về một trạng
> thái cây làm việc, không phải một commit. **Lần này build từ commit đã push.**

---

## 7. Chuẩn bị cho khoảnh khắc ② — nền tảng là điều kiện cần

Việc của Vĩ, làm cùng §6.

1. Build APK với **remote ASR là đường duy nhất**:
   `-PvivaAsrEngine=remote -PvivaAsrBaseUrl=http://10.99.0.3:8080`
2. **Xác nhận fallback Vosk on-device đã tắt.** Nếu node chết mà lệnh vẫn chạy thì khoảnh khắc ②
   phản tác dụng nặng — kiểm bằng cách dừng node và thử một lệnh **trước** khi lên sân khấu.
3. Tập thao tác dừng/bật node `viva-asr` cho nhanh và gọn. Đo mất bao lâu để node `Running` trở lại —
   nếu quá 20 giây thì phải đổi cách diễn (dừng node **trước**, kể chuyện trong lúc chờ bật lại).
4. Quay lại toàn bộ khoảnh khắc này vào video dự phòng G-C.

---

## 8. Checklist bàn giao cho G-C

Đến 21/08 20:00, Làn 3 phải giao được cho Dương để quay video:

- [ ] Device sống, deployment `RUNNING`, node `viva-asr` sống
- [ ] APK đúng một commit đã push, SHA-256 đã ghi
- [ ] Kết quả G-A và G-B, dù pass hay fail, đã ghi thành file trong `evidence/vong3/`
- [ ] Correlated trace ít nhất 1 lượt hoàn chỉnh
- [ ] Thao tác dừng/bật node đã tập, biết mất bao lâu
- [ ] Đã xác nhận: dừng node thì lệnh **thật sự** chết
