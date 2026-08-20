# 33 — Thuật ngữ: mỗi từ sống ở tầng nào

> **Viết 18/08/2026.** Dùng kèm `32-ENHANCE-KHOI-4-PLATFORM-CARSKY.md`.
> Mỗi mục trả lời hai câu: *"nó là cái gì"* và *"trong dự án này nó nằm ở đâu"*.
> Không giải thích chung chung — chỗ nào có tên file/số hiệu thật thì ghi ra.

---

## PHẦN 0 — BẢN ĐỒ: đọc cái này trước

Một câu nói của tài xế đi qua **bốn thế giới**. Hầu hết nhầm lẫn xảy ra vì một từ
ở thế giới này bị hiểu theo nghĩa của thế giới khác.

```
  ┌─ THẾ GIỚI 1: NỀN TẢNG CARSKY (hạ tầng thuê ngoài, chạy trên k8s) ─────┐
  │  Blueprint → Deployment → Room → Node → Pin                           │
  │  Đây là "phòng thí nghiệm ảo". Đội KHÔNG viết nó, đội thuê nó.        │
  └───────────────────────────────────────────────────────────────────────┘
             ▲ chứa bên trong
  ┌─ THẾ GIỚI 2: XE ẢO (chuẩn ngành ô tô) ────────────────────────────────┐
  │  VHAL ── VSS/KUKSA ── CAN ── ECU/CCU                                  │
  │  Đây là "ngôn ngữ của xe". Chuẩn có sẵn, không ai tự đặt ra.          │
  └───────────────────────────────────────────────────────────────────────┘
             ▲ app nói chuyện với nó
  ┌─ THẾ GIỚI 3: APP VIVA (đội tự viết) ──────────────────────────────────┐
  │  mic → VAD → ASR → NLU → Intent → SafetyGuard → Skill → thực thi      │
  └───────────────────────────────────────────────────────────────────────┘
             ▲ sinh ra dấu vết
  ┌─ THẾ GIỚI 4: BẰNG CHỨNG & CHẤM ĐIỂM ──────────────────────────────────┐
  │  trace · receipt · readback · ablation · barem · ba nhãn              │
  └───────────────────────────────────────────────────────────────────────┘
```

**Quy tắc vàng:** khi bí, hỏi *"từ này thuộc thế giới nào?"*. Ví dụ "node" ở thế
giới 1 là một máy ảo/container trong room — không liên quan gì tới "node" của
Node.js.

---

## PHẦN 1 — THẾ GIỚI 1: NỀN TẢNG CARSKY

### Blueprint — bản thiết kế
Một file JSON mô tả **sẽ có những máy nào và nối dây ra sao**. Giống bản vẽ mạch.
Chưa chạy gì cả. Của đội: `6deadb05-c856-4dab-976b-432b0fac0658`, tên
`VIVA-deploy-clone-0803`. Xuất ra bằng `GET /blueprints/{id}/export`.

### Deployment — lần bấm nút "chạy"
Lấy blueprint đem dựng thật lên cụm máy chủ. Của đội: `VIVA-demo-0808`.
Quota chỉ cho **2 deployment cùng lúc** — nên mới có chuyện "muốn deploy thêm
phải xoá bớt".

### Room — không gian chạy của một deployment
Định danh: `v37aa3knc6t1embelr5yi`. Mọi node trong cùng room nhìn thấy nhau qua
mạng nội bộ `10.99.0.x`. Ngoài room **không** gọi vào được — đây là lý do gốc của
gần hết khó khăn về bằng chứng.

> ⚠️ Room ID trong dự án này trùng với "Device ID". Tài liệu nền tảng gọi lẫn lộn
> hai tên cho cùng một chuỗi.

### Node — một máy trong room
21–22 cái. **Sáu loại**, mỗi loại chơi một luật khác nhau:

| nodeType | Là gì | Ví dụ trong room của đội |
|---|---|---|
| `skycraft` | Máy ảo Android Automotive đầy đủ | `IVI - Android` — chỗ APK của đội chạy |
| `container` | Một container Docker thường | `VIVA ASR` — container nhận dạng giọng nói của đội |
| `script-node` | Chạy một đoạn Lua/Luau, đóng vai một hộp điều khiển | `IVI Gateway`, `Climate ECU`, `VCU` |
| `kuksa-databroker` | Kho tín hiệu xe trung tâm | `Central Broker (VSS)` |
| `can-bus` | Mô phỏng một bus CAN | `BCM CAN`, `PWT CAN` |
| `gpio-panel` | Bảng công tắc/cảm biến giả lập | `Drive Controls`, `Battery Sensor` |

### Pin — cổng cắm của node
Node có "chân cắm" giống IC. Loại pin quyết định node nói được thứ tiếng gì:
`ETHERNET`, `CAN`, `VHAL`, `USB`, `SENSOR`.

**Pin `vhal` là nhân vật chính của toàn bộ phân tích ở tài liệu 32.** Nó có một
danh sách property được phép đi qua (`properties`) — 15 mục. Property không nằm
trong danh sách đó thì app ghi vào cũng như ghi vào hư không.

### Edge — sợi dây nối hai pin
Blueprint = node + edge. Không có edge thì hai node không nói chuyện được dù
cùng room.

### Namespace / Pod — từ vựng Kubernetes rò rỉ ra
CarSky chạy trên Kubernetes. Mỗi node thật ra là một **pod**. Namespace của room
hiện tại: `room-z0as6abg`. Bạn không cần hiểu k8s, chỉ cần biết: **pod restart =
log cũ mất sạch**.

### Container `user` vs `sidecar` — hai nửa của một pod
Mỗi container-node thực ra chạy **hai** container cạnh nhau:

- `user` — code của đội (ví dụ server ASR bằng Python)
- `sidecar` — phần mềm của nền tảng, lo mạng ảo, kết nối, proxy

Đây chính là phát hiện F1: gọi `/logs/{node}` mà không nói lấy container nào thì
nền tảng trả 502 vì **không biết chọn cái nào**. Thêm `?container=user` là xong.
Script-node thì chỉ có `sidecar`.

### Nydus — tên phần mềm điều phối của CarSky
Xuất hiện khắp nơi: `nydus_sidecar_native` trong log, `nydus.kuksa.connect(...)`
trong Lua, `nydus-reach` là công cụ dòng lệnh mở đường hầm. Coi như "hệ điều hành
của room".

### Conduit — lớp điều khiển máy ảo, và nó đang HỎNG
Đường để gõ lệnh vào VM từ xa: `shell`, `adb-exec`, `screenshot`, `tap`.
Ở instance hackathon này **toàn bộ họ này trả 502 "Conduit service not configured"**.

> 🧠 Nhầm lẫn tốn nhiều ngày nhất của đội: thấy Conduit chết rồi kết luận
> *"không gửi được gì vào room"*. Sai. Họ `/signals` và `/logs` **không** đi qua
> Conduit và vẫn chạy tốt.

### Loki — kho log lịch sử
Về lý thuyết lưu log cũ để tìm lại sau. **Ở instance này nó rỗng** — đã thử 5 node,
mọi khoảng thời gian, đều trả về không có gì. Hệ quả thực tế: log phải lấy **ngay
trong phiên chạy**.

### Registry vs Artifact — hai kho khác nhau
- **Registry** (`registry.hackathon-2.carsky.io`): kho **image container**. Chỗ đội
  đẩy `viva-asr` lên để node chạy được.
- **Artifact**: kho **file** thường (APK, ảnh đĩa). Chỗ đội tải APK lên để cài vào máy ảo.

Đẩy nhầm kho là một lỗi hay gặp.

### Digest (`sha256:...`) — vân tay của image
Chuỗi băm định danh **chính xác** một bản build. Khác `:latest` (tên gợi nhớ, có thể
trỏ vào bản khác lúc khác). Đây là lý do phát hiện F6 quan trọng: manifest bằng chứng
ghi `63c2c56a…` nhưng cái đang chạy là `6ca09c24…` → **bằng chứng và thứ đang chạy
là hai bản khác nhau**.

### Multi-arch / index digest
Một "image" có thể gói nhiều kiến trúc CPU (`amd64` cho PC, `arm64` cho máy ảo xe).
**Index digest** trỏ vào cả gói; mỗi kiến trúc lại có digest riêng.

---

## PHẦN 2 — THẾ GIỚI 2: XE ẢO

### AAOS — Android Automotive OS
Android chạy thẳng trên đầu xe, không phải Android Auto (cái chiếu màn hình điện
thoại). Máy ảo `IVI - Android` chạy cái này, Android 14.

### IVI — In-Vehicle Infotainment
Màn hình giữa táp-lô. Cũng là tên node.

### VHAL — Vehicle Hardware Abstraction Layer
**Từ khoá quan trọng nhất của thế giới 2.** Là lớp dịch giữa Android và phần cứng xe.
App Android **không** ra lệnh "bật điều hoà"; app ghi một con số vào một ô nhớ có
số hiệu, VHAL lo phần còn lại.

Mentor sửa đội đúng chỗ này (`03-contracts.md` §0.1): *"không có phần vhal nào nhận
intent cả"* — VHAL không biết `hvac_set_temp` là gì, nó chỉ biết `(propId, areaId, value)`.

### propId — số hiệu của một thuộc tính xe
Ví dụ `371198722` = khoá cửa. Trông như số ngẫu nhiên nhưng có cấu trúc:

```
0x16200B02
  ▲▲
  ││└─ 0x6 = vùng DOOR (cửa)
  │└── 0x2 = kiểu BOOLEAN
  └─── 0x1 = nhóm SYSTEM (chuẩn AOSP)   ·   0x2 = nhóm VENDOR (hãng tự thêm)
```

**SYSTEM vs VENDOR** là phân biệt then chốt ở phát hiện F2/F3: pin `vhal` của
blueprint bắc cầu 10 property VENDOR (do CarSky tự định nghĩa) nhưng lại **thiếu**
hai property SYSTEM mà app đội đang dùng cho HVAC.

### areaId — vùng nào của xe
Cùng một propId nhưng khác vùng. `1` = ghế/cửa trước bên trái (tài xế), `4` = bên
phải, `0` = toàn xe (GLOBAL).

Đây là nguồn lỗi âm thầm: app ghi `HVAC_FAN_SPEED` ở area `0`, gateway lại chờ ở
area `1`. Không crash, không log, chỉ **không có gì xảy ra**.

### CarPropertyManager
API Android mà app gọi để đọc/ghi property. `setProperty(...)` / `getProperty(...)`.

### VSS — Vehicle Signal Specification
Bộ **tên chuẩn** cho mọi tín hiệu xe, dạng đường dẫn có dấu chấm:
`Vehicle.Cabin.HVAC.Station.Row1.Driver.Temperature`. Broker của room có 1268 tín hiệu.

Khác VHAL ở chỗ: VHAL dùng **số**, VSS dùng **tên**. Gateway là chỗ dịch qua lại.

### KUKSA / databroker — kho tín hiệu trung tâm
Phần mềm giữ giá trị hiện tại của mọi tín hiệu VSS. Node `Central Broker (VSS)`.
Đây là nơi REST `/signals` đọc/ghi vào.

- `POST .../values` = **đọc** giá trị hiện tại (đọc mà lại là POST — bẫy tài liệu)
- `POST .../actuate` = **ghi** một giá trị

### CAN bus / DBC / ECU / CCU
- **CAN** — mạng dây thật trong xe, các hộp điều khiển nói chuyện với nhau qua đây.
- **DBC** — file mô tả mỗi khung tin CAN mang bit nào nghĩa gì. Repo có `body_can.dbc`.
- **ECU** — một hộp điều khiển (Electronic Control Unit). Room có `BCM ECU`, `Climate ECU`.
- **CCU** — hộp điều khiển trung tâm, đầu cuối của chuỗi. Đội **được phép mô phỏng**.

### Gateway / Lua / Luau
`IVI Gateway` là script-node chạy ~480 dòng Lua, làm nhiệm vụ **phiên dịch VHAL ↔ VSS**.
Luau là biến thể Lua mà CarSky dùng. Đây là mảnh ghép then chốt: **chuỗi VSS→CAN chỉ
được kích hoạt từ phía VHAL**, nghĩa là bắt buộc phải có APK chạy thật mới đóng được
chuỗi đầy đủ.

### priv-app / privileged permission — và "M1a"
Một số quyền của Android (đổi âm lượng xe, ghi property xe) chỉ cấp cho app nằm ở
`/system/priv-app`. Cài vào đó cần quyền root trên máy ảo.

**M1a** là tên mốc công việc của đội cho việc này. Hiện đang **chặn**: shell không
root, không ghi được vào `/system/priv-app`.

### Flavor `mock` vs `real`
Hai biến thể build của cùng một app:
- `mock` — đầu kia là `MockVehicleRepository`, dữ liệu xe giả trong bộ nhớ
- `real` — nối vào VHAL thật

**Mọi bằng chứng CarSky của đội tới nay đều là `mock`.** Đây là lý do khoản trừ −4
tồn tại.

### Cuttlefish
Máy ảo Android của Google dùng để test. Serial `CUTTLEFISHCVD01`. Nó là AAOS thật
nhưng **không phải cabin xe thật** — nên không được nói "đo trong cabin".

---

## PHẦN 3 — THẾ GIỚI 3: APP VIVA

### Chuỗi xử lý giọng nói

```
mic → VAD → ASR → NLU → Intent → SafetyGuard → Skill → thực thi → TTS
```

| Từ | Nghĩa | Trong dự án |
|---|---|---|
| **PCM** | Âm thanh số thô, chưa nén | 16-bit, 16 kHz, mono |
| **VAD** | Voice Activity Detection — phát hiện *có người đang nói* | Dùng Silero VAD |
| **ASR** | Automatic Speech Recognition — âm thanh → chữ | Container `viva-asr` |
| **PhoWhisper** | Model ASR tiếng Việt (bản Whisper huấn luyện lại) | `phowhisper-tiny-int8` |
| **Vosk** | Model ASR chạy thẳng trên máy, nhẹ | Gỡ 10/08, **kéo lại 20/08** — nay là engine thứ ba (`VOSK`), chạy offline |
| **int8** | Nén model xuống số nguyên 8-bit cho nhanh | |
| **NLU** | Natural Language Understanding — chữ → ý định | |
| **Intent** | Ý định đã nhận ra, ví dụ `hvac_set_temp` | 10 intent |
| **Slot** | Tham số kèm theo intent, ví dụ `value: 24.0` | |
| **TTS** | Text-to-Speech — máy đọc câu trả lời | Thiếu giọng vi-VN |
| **Audio focus** | Luật Android: ai được phát tiếng lúc nào | "duck" = hạ nhạc khi trợ lý nói |
| **MediaSession** | Cơ chế Android quản lý trình phát nhạc | Chỗ đọc lại `PLAYING → PAUSED` |

### SafetyGuard — G1 / G2 / G3
Lớp luật an toàn, **điểm khác biệt số 1 của đội**:

| Mã | Luật |
|---|---|
| `G1_SPEED_LOCK` | Xe đang chạy → **từ chối** mở khoá cửa |
| `G2_CONFIRM_DOOR` | Xe đứng yên → **hỏi lại** trước khi mở |
| `G3_UNSUPPORTED` | Câu ngoài phạm vi → từ chối, không đoán bừa |

### Verdict — phán quyết
Kết quả của SafetyGuard cho mỗi lượt: `Allow`, `Deny:G1_SPEED_LOCK`,
`Confirm:G2_CONFIRM_DOOR`. Nó nằm ngay trong dòng log tóm tắt.

### Chỉ số đo

| Từ | Nghĩa |
|---|---|
| **WER** | Word Error Rate — tỉ lệ chữ sai. 0.411 = sai 41% |
| **RTF** | Real-Time Factor — 0.167 nghĩa là xử lý 1 giây tiếng mất 0,167 giây |
| **e2e_ms** | End-to-end: từ lúc nói xong tới lúc lệnh chạy |
| **server_ms** | Riêng phần model xử lý, không tính mạng |
| **p50 / p95** | Trung vị / 95% số lượt nhanh hơn mức này. p95 quan trọng hơn trung bình vì nó nói về *trường hợp tệ* |

### Trace ID / `X-Trace-Id`
Một mã UUID gắn cho **một lượt nói**, đi kèm suốt chuỗi. Nhờ nó mới ghép được
"dòng log ở app" với "dòng log ở container ASR" thành cùng một sự kiện.

Đây là hạt nhân của cả khoản trừ −2.

### Text injection — bơm chữ
Kỹ thuật test: đẩy thẳng câu chữ vào NLU, **cố ý bỏ qua mic/VAD/ASR**. Dùng để đo
phần sau. Bằng chứng ngày 09/08 là loại này — nên `e2e_ms=0` ở đó **không phải**
độ trễ giọng nói.

---

## PHẦN 4 — THẾ GIỚI 4: BẰNG CHỨNG & CHẤM ĐIỂM

### Barem — bảng chấm điểm
Sáu khối. Khối ④ *Platform utilization* 15đ là phần đang bàn, chia bốn ô:
align 5đ · độ sâu 4đ · evidence 4đ · ranh giới 2đ.

### Core flow — luồng chính
Chuỗi việc mà sản phẩm **phải** làm được. Với VIVA: nói → hiểu → kiểm an toàn → xe
phản ứng. Giám khảo dùng nó làm thước: *"bỏ CarSky đi mà core flow vẫn chạy thì
CarSky chưa phải điều kiện cần"*.

### Ablation — thí nghiệm cắt bỏ
Từ mượn của nghiên cứu khoa học: **gỡ một thành phần ra rồi đo lại**, để chứng minh
thành phần đó thật sự có tác dụng. Không có ablation thì mọi claim đều là lời nói.

Ba ablation của đội: A1 tắt SafetyGuard · A2 đổi đường ASR · A3 bỏ callback VHAL.

### Readback — đọc ngược lại
Sau khi ghi một giá trị, **đọc lại từ một đường khác** để chứng minh nó thật sự tới
nơi. Bằng chứng mạnh hơn hẳn "log nói là đã gửi".

Ví dụ đã làm 07/08: ghi `Temperature = 24` rồi đọc lại thấy `24` với timestamp
nhảy đúng thời điểm ghi.

### Receipt / biên nhận
Từ giám khảo dùng. Nghĩa: **một bộ bằng chứng mà mọi mảnh đều mang cùng một mã định
danh**. Rời rạc thì mỗi mảnh chỉ chứng minh được phần của nó; nối bằng cùng một
UUID thì chứng minh được cả chuỗi.

### L1 / L2 / L3 — mức đạt
`L1` = có chạm tới nền tảng. `L2` = core flow chạy trên nền tảng. `L3` = bỏ nền tảng
đi thì core flow **hỏng**. Barem đặt cổng cứng: không chứng minh được core flow chạy
trên CarSky thì trần là L1 cho cả 15đ.

### Ba nhãn của đội — Đã tích hợp / Mô phỏng / Kế hoạch

| Nhãn | Điều kiện |
|---|---|
| **Đã tích hợp** | Đã chạy trên nền tảng thật, có log hoặc ảnh |
| **Mô phỏng** | Chạy được nhưng đầu kia là mock/dữ liệu tự tạo |
| **Kế hoạch** | Contract đã chốt, chưa chạy |

> Ranh giới hay nhầm nhất: **"unit test xanh" là Mô phỏng, không phải Đã tích hợp.**

Kỷ luật này đang mang về 2/2 điểm ô "Ranh giới".

### Golden / fixture / synthetic
- **Fixture** — file dữ liệu mẫu để test, **do người viết ra**, không phải log chạy thật.
- **Golden** — bản mẫu chuẩn để so sánh kết quả.
- **Synthetic** — dữ liệu tổng hợp (ví dụ giọng do máy đọc thay vì người nói).

Bản nộp **bắt buộc** khai rõ cái nào là synthetic, nếu không một bảng số đẹp sẽ bị
đọc là số đo thật.

---

## PHẦN 5 — TỪ VỰNG KỸ THUẬT CHUNG HAY GẶP

| Từ | Nghĩa gọn | Ghi chú trong dự án |
|---|---|---|
| **REST / endpoint / route** | Cách gọi dịch vụ qua HTTP | `GET /deployments/{room}/nodes` |
| **`GET` / `POST` / `PATCH`** | Đọc / gửi / sửa một phần | `/signals/.../values` là POST dù nó đọc |
| **Query param** | Phần sau dấu `?` trong URL | `?container=user` — chìa khoá của F1 |
| **Header** | Dòng thông tin kèm request | `X-Trace-Id`, `x-api-key` |
| **404 / 502 / 200** | Không tìm thấy / cổng sau hỏng / thành công | 502 hay bị đọc nhầm là "chết", thực ra có thể chỉ thiếu tham số |
| **Upstream** | Dịch vụ nằm sau cùng | Thông báo lỗi upstream là chỗ tìm nguyên nhân thật |
| **`openapi.json`** | File mô tả toàn bộ API | Không phải lúc nào cũng đủ — `container` không có trong đó |
| **SSE / subscribe** | Nhận sự kiện liên tục thay vì hỏi từng lần | `/signals/.../subscribe` |
| **allowlist** | Danh sách được phép | Danh sách 15 property của pin `vhal` |
| **silent no-op** | Lệnh chạy nhưng **không làm gì và không báo lỗi** | Nguy hiểm nhất — F2/F4 đều thuộc loại này |
| **SHA-256** | Vân tay của một file | Dùng để chứng minh APK đã cài đúng bản đã build |
| **`adb`** | Công cụ điều khiển máy Android qua dây/mạng | `adb logcat` = xem log |
| **`logcat`** | Log của Android | Chỗ có `VIVA_TRACE_SUMMARY` |
| **`dumpsys`** | Lệnh Android hỏi trạng thái một dịch vụ | `dumpsys media_session` |
| **cleartext HTTP** | HTTP không mã hoá | Android 14 chặn mặc định — PR #42 phải mở riêng cho `10.99.0.3` |
| **tunnel** | Đường hầm nối máy dev vào mạng trong room | `nydus-reach tunnel adb` |
| **flavor / build flag** | Biến thể build và cờ truyền lúc build | `-PvivaAsrBaseUrl=...` |

---

## PHẦN 6 — MƯỜI CHỖ HAY BỊ NÓI SAI

| Nói sai | Đúng phải là |
|---|---|
| "Container chạy rồi nên ASR đã tích hợp" | Node `Running` chỉ chứng minh **cụm kéo được image**. Chưa có request nào đi vào. |
| "Unit test xanh nên đã chạy được" | Unit test xanh = **Mô phỏng**. |
| "Conduit 502 nên không gửi được gì vào room" | `/signals` và `/logs` **không** qua Conduit và vẫn chạy. |
| "`/logs` chỉ trả log WebRTC" | Đúng cho node **skycraft**. Container node trả đúng log ứng dụng. |
| "Chạy trên emulator = chạy trên CarSky" | Hai môi trường khác nhau. `evidence/emulator/` **không** dùng được cho câu nào có chữ "trên CarSky". |
| "p95 khoảng 1500ms" | Đo được **1664 ms**. Làm tròn xuống là khai sai. |
| "e2e_ms có số nên đó là độ trễ giọng nói" | Phiên bơm text có `e2e_ms=0`; phiên có mic mới là số thật. |
| "Full-stack tới CAN" | Chỉ nhóm `hvac_*` + `door_lock` mới đi qua VHAL. Media, âm lượng, giao hàng đi đường riêng trong app. |
| "Ghi property xong là tới xe" | Property phải nằm trong **allowlist của pin** và **đúng areaId**. Sai một trong hai = im lặng không xảy ra gì. |
| "Cuttlefish là xe thật" | Là máy ảo. Không được nói "đo trong cabin". |
