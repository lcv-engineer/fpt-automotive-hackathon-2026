# 07 — Đưa APK lên máy ảo Android: Artifact, USB, widget ADB

> `carsky-api.md` §4 kết luận *"ADB không dùng được"* — đúng với **REST API**
> (`/vms/{room}/{node}/adb-shell`, `/deployments/{room}/adb-exec/{node}` đều 502).
> **Widget `IVI ADB` trong web UI thì chạy** — không qua REST, không cần Conduit.

---

## 1. Mở shell trên VM Android

1. Cột trái: **Devices → VIVA → Widgets → `IVI ADB`**
2. Inspector cột phải: dropdown `ADB` → chọn **`face-adb`**
3. Khung giữa hiện `ADB SHELL` + badge `connected`, prompt `trout_arm64:/ $`

### Thông tin VM (đã đo)

| | |
|---|---|
| Kiến trúc | **`arm64-v8a`** (`ro.product.cpu.abi`), artifact khai `arch: aarch64` |
| Android | **14 / SDK 34**, `trout_arm64` |
| Current user | **`0`** — *không* phải `10` như emulator AAOS trên máy dev |
| `su` | có, cú pháp `su 0 sh` (**không** nhận `-c` ở dạng `su -c`) |
| `curl` | có ở `/system/bin/curl`; **không** có `wget`, `dhcptool`, `netstat` |
| Ra internet | 🚫 **KHÔNG** — `ip route` chỉ có `10.0.2.0/24` (phát hiện 19/08) |
| `/data` trống | 5.4 GB |
| RAM | ~3.8 GB, thường chỉ còn ~330 MB |

⚠️ Ghi chú lịch sử: 09/08 `curl -sI https://github.com` từ guest còn trả `200`; đến
19/08 thì guest **không còn route ra ngoài**. Đừng dựa vào đường internet của guest.

---

## 2. Chuyển file vào guest — ba đường, theo thứ tự ưu tiên

### ① USB image + widget USB Device — ✅ đường đang dùng (19/08)

Đường duy nhất **không phụ thuộc internet của guest**.

```
1. Tao anh dia FAT32 chua APK        viva-usb.img (256 MB)
   truncate + mkfs.vfat + mcopy      (hoac diskpart tren Windows khong WSL)
2. Upload artifact category USB      panel Artifacts -> `viva-usb`
3. Widget USB Device -> Plug
4. File hien o /sdcard/Music/usb_1/
```

⚠️ Ô "Mirror base" trong widget ghi `/sdcard/Movies/ota_` nhưng **đường mount thật là
`/sdcard/Music/usb_1`** — panel hiển thị đúng sau khi Plug. **Tin panel.**

### ② CarSky Artifacts + tải xuống trong guest

Upload APK lên panel **Artifacts** thành artifact **private** (đã làm 09/08:
`viva-apk` / `0.0.1`, 387.904.742 bytes; 19/08: `v0.0.3`). File không ra công khai
và nằm cùng nền tảng.

⚠️ **Chỉ chạy khi guest còn ra được mạng.** Từ 19/08 thì không.

⚠️ Category `ANDROID_IMAGE` cần **cả hai** ô Image + Host Package mới bấm Upload
được → khi chỉ có APK thì dùng một zip giả 245 B cho ô còn lại.

❌ **Không tìm được URL tải artifact qua REST.** `GET/POST /artifacts`,
`/artifacts/{id}`, `/artifacts/{id}/versions` đều có, nhưng 5 đường tải thử
(`/download`, `/files/…`, `?path=`) đều 404. Hiện phải copy link từ UI.

### ③ GitHub Release

Chỉ dùng khi repo public và chấp nhận file ra URL công khai.

```bash
gh release create <tag> --repo <owner/repo> --title "..." --notes "..." "<duong-dan-apk>"
```

---

## 3. Cài APK

APK phải có `lib/arm64-v8a/` — kiểm trước khi phí công:

```bash
unzip -l app-mock-debug.apk | grep -c "lib/arm64-v8a/"   # phai > 0
```

Trong ADB shell — **dán từng dòng một**, terminal trong trình duyệt hay dính lệnh
vào nhau khi dán nhiều dòng:

```sh
pm install -r /data/local/tmp/viva.apk
pm grant com.sopa.viva_automotive android.permission.RECORD_AUDIO
pm grant com.sopa.viva_automotive android.car.permission.CAR_SPEED
pm grant com.sopa.viva_automotive android.car.permission.CAR_ENERGY
am start -n com.sopa.viva_automotive/.MainActivity
```

Quyền **sống qua reboot** nhưng **mất khi gỡ cài đặt**. Xác minh:

```sh
dumpsys package com.sopa.viva_automotive | grep -i CAR_SPEED      # granted=true
dumpsys package com.sopa.viva_automotive | grep -i RECORD_AUDIO   # granted=true
```

✅ Quyền `CAR_SPEED` trên image này là `prot=dangerous` → `pm grant` được, **không
cần privileged/platform-signed install** (evidence:
`evidence/c2/car-speed-permission-probe-0818.txt`).

### Đối chiếu SHA-256 ba chặng

Máy dev = file trong guest = `base.apk` đã cài:

```sh
sha256sum $(pm path com.sopa.viva_automotive | cut -d: -f2)
```

### ⚠️ `INSTALL_FAILED_UPDATE_INCOMPATIBLE` — chữ ký khác nhau

Nếu trên Device đã có package cùng tên nhưng **ký bằng khoá debug khác**:

```
Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: existing package signature mismatch]
```

Không có cách ép. Phải gỡ rồi cài sạch:

```sh
pm uninstall com.sopa.viva_automotive.mock
pm install /data/local/tmp/viva.apk
```

> Hệ quả vận hành: **thống nhất một máy build** cho mọi bản đưa lên Device. Gỡ
> package cũng xoá DataStore → mất thiết lập ngôn ngữ giọng nói và engine ASR.

---

## 4. ⚠️ Mọi APK dùng làm evidence phải build sau `clean`

Hai lần build incremental cho APK **305 MB** mà listing **không** còn entry model —
AGP gỡ entry khỏi central directory nhưng **không compact file**, ~78 MB dữ liệu
chết vẫn nằm trong archive. Sau `clean`: 227 MB, hash tái lập được.

```bash
automotive/gradlew :app:clean :app:assembleRealDebug -PvivaAsrBaseUrl=http://10.99.0.3:8080
```

---

## 5. Cờ build APK cho Device

### ⚠️ ĐÍNH CHÍNH: `-PvivaAsrEngine` KHÔNG còn tồn tại

Các bản runbook trước ghi cần `-PvivaAsrEngine=remote`. **Cờ đó không còn trong
`automotive/feature/voice/build.gradle.kts`.** Engine ASR hiện được chọn **lúc chạy**
qua Settings trong app (`RoutingAsrClient` → `SettingsDataStore.asrEngine`), mặc định
`AsrEngine.VIVA`:

| Engine trong Settings | Client |
|---|---|
| `VIVA` (mặc định) | `HttpAsrClient` → container `viva-asr` trong room |
| `GOOGLE` | `GoogleCloudSpeechAsrClient` — cần internet + service-account json |
| `VOSK` | `VoskAsrClient` — hoàn toàn on-device, chạy được khi không có mạng (khôi phục 20/08) |

### Cờ build còn hiệu lực

| Cờ Gradle | `BuildConfig` | Mặc định |
|---|---|---|
| `-PvivaAsrBaseUrl` | `ASR_BASE_URL` | `http://127.0.0.1:8080` |
| `-PvivaBrainBaseUrl` | `BRAIN_BASE_URL` | rơi về `vivaAsrBaseUrl`, rồi `http://127.0.0.1:8080` |
| `-PvivaBrainAgentEnabled` | `BRAIN_AGENT_ENABLED` | `false` |
| `-PvivaBrainAuthToken` | `BRAIN_AUTH_TOKEN` | `""` |

- Trên Device CarSky: `-PvivaAsrBaseUrl=http://10.99.0.3:8080`
- Trên máy dev: để mặc định + `adb reverse tcp:8080 tcp:8080`

⚠️ Đổi địa chỉ node là phải sửa **cả** `network_security_config.xml` — xem
[04 §4](04-MANG-TRONG-ROOM.md).

### ⚠️ App mặc định chạy tiếng Anh

`VoiceLanguage.fromStorageKey` trả `ENGLISH`, và **mọi câu tiếng Việt đều sai bét**
— nhìn y hệt "mic không hoạt động". Phải vào **Cài đặt → Ngôn ngữ giọng nói → Tiếng
Việt** (bấm tay qua widget IVI Screen). Đặt luôn locale giao diện cho khớp:

```sh
cmd locale set-app-locales com.sopa.viva_automotive --user 0 --locales vi-VN
```

---

## 6. Mic — nằm trong widget Screen, không phải widget riêng

Mở **`IVI Screen`** → Inspector → `Video Part = face-screen`,
`Audio Part = face-audio`, chọn `Microphone`, bấm **Enable microphone** (trình duyệt
hỏi quyền, phải Allow).

✅ Widget IVI Screen có `Recorder Part: Client Microphone` → **mic laptop truyền
thẳng vào máy ảo**, tự chạy được phiên giọng nói tại máy mình.

Mic tắc được ở **ba tầng**, cách sửa khác hẳn nhau:

| Tầng | Kiểm bằng |
|---|---|
| CarSky có gửi tiếng vào VM không | đã bấm `Enable microphone` chưa, `Audio Part` đã chọn chưa |
| Android có thấy thiết bị thu không | `dumpsys media.audio_flinger \| grep -iA3 input` |
| App có mở được mic không | `logcat -c` → bấm mic → `logcat -d \| grep -iE "viva\|audiorecord\|permission denial\|vad"` |

| Thấy trong log | Nghĩa là |
|---|---|
| `Permission denial … RECORD_AUDIO` | quyền chưa ăn — cấp lại rồi **khởi động lại app** |
| Mở `AudioRecord` OK nhưng không có `speech_end` | mic mở được nhưng toàn mẫu 0 → tắc ở tầng 1 |
| Không có dòng nào của app | app chưa chạy tới đoạn thu |

---

## 7. Hai cái bẫy của terminal ADB trong trình duyệt

| Bẫy | Chi tiết |
|---|---|
| **Phiên tự kết nối lại và reset thư mục về `/`** (read-only) | `Failed to open the file viva.apk: Read-only file system`. Dùng **đường dẫn tuyệt đối cho mọi lệnh**. Đặc biệt: `rm -f viva.apk` sau khi phiên reset sẽ xoá `/viva.apk`, **không đụng file trong `/data/local/tmp`** |
| **`curl -C -` tin vào file rác đã có sẵn** | Nếu lần trước để lại file dở (kể cả trang lỗi 404 vài KB), `-C -` tải tiếp **từ vị trí đó** → file **đúng kích thước** nhưng sai nội dung, `sha256sum` không khớp mà không có dấu hiệu nào khác |

### Web ADB shell đứt (`Connection closed (code 1006)`)

1. Bấm **Reconnect** 2–3 lần, cách nhau ~10 s (`adbd` là service của init, thường tự lên)
2. Kiểm sidecar — nếu spam `hybrid-vsock CONNECT 5555 response failed: Connection reset by peer` thì `adbd` trong guest đang lỗi:
   ```bash
   curl -s -H "x-api-key: $KEY" "$B/deployments/$ROOM/logs/$IVI?container=sidecar&tail=15"
   ```
3. Restart node `IVI - Android` → **guest reboot** → phải vá lại mạng `eth1`

**Đường lấy log không phụ thuộc ADB:** widget **Log** với source **`face-logcat`**.
**Mở sẵn widget này trước mọi phiên** — mất shell giữa phiên vẫn còn `VIVA_TRACE`.

---

## 8. Hai triệu chứng UI hay gặp

| Triệu chứng | Nguyên nhân | Xử lý |
|---|---|---|
| App biến mất khỏi màn hình | RAM guest thấp → Android huỷ Activity nhưng **giữ process**. Không phải crash | `pidof com.sopa.viva_automotive` rồi `am start -n com.sopa.viva_automotive/.MainActivity` |
| Widget IVI Screen báo *"Input is not available"* | Touch part chưa đăng ký lại sau reboot | Inspector → kiểm **Touch Part** còn `face-touch-panel`; trống thì chọn lại |

⚠️ App **không có foreground service** (`dumpsys activity services … → (nothing)`) —
voice pipeline sống trong scope Activity. **Giữ app ở foreground suốt phiên.**

---

## 9. Đường REST/tunnel — ghi lại để không thử lại

| Đường | Trạng thái |
|---|---|
| `nydus-reach tunnel adb --conduit … --namespace … --node …` | API trả sẵn lệnh, nhưng **chưa ai trong đội xác nhận chạy được** (thiếu binary, tải trong UI CarSky) |
| `vm_tunnel_open` (MCP) → local ADB | ❌ port trả về là `localhost` **của máy chủ MCP**, không tới được từ máy dev |
| `adb_shell` / `container_shell` / `ui_tree` / `find_text` | ❌ 502 Conduit, kể cả gọi qua MCP |

⇒ Không có `adb` host thật → `adb install` / `adb push` / `adb logcat` từ máy dev
đều không dùng được với room CarSky. Tất cả phải qua widget.
