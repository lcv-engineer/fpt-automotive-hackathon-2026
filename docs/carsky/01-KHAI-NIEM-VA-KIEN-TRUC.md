# 01 — Khái niệm & kiến trúc nền tảng CarSky

> Nguồn: `docs/platform/Car-Sky-Platform.html` (tài liệu chính thức, 39 mục),
> đối chiếu với những gì đội đã gọi/quan sát thật trên room `v37aa3knc6t1embelr5yi`.

---

## 1. CarSky là gì, theo đúng cách đội dùng nó

CarSky Hackathon-2 là một nền tảng **mô phỏng xe bằng phần mềm**, chạy trên
Kubernetes. Nó cho phép dựng một "xe ảo": máy ảo Android Automotive (AAOS), các
bus CAN, một KUKSA databroker theo chuẩn COVESA VSS, các ECU mô phỏng viết bằng
Lua, và các bảng điều khiển GPIO để con người kéo slider thay cho cảm biến thật.

Với đội VIVA, CarSky đóng ba vai:

| Vai | Cụ thể |
|---|---|
| **Nơi chạy app** | Máy ảo Android `IVI - Android` là chỗ cài APK `com.sopa.viva_automotive*` |
| **Nơi chạy service của đội** | Container node `VIVA ASR` phục vụ HTTP cho app trong cùng mạng room |
| **Nguồn tín hiệu xe** | GPIO panel → VCU (Lua) → CAN → gateway (Lua) → KUKSA → VHAL |

---

## 2. Mô hình khái niệm

```text
Account (đội)
 └── Device            "chỗ để chạy", có id riêng; quota MAX_DEVICES = 5
      └── Deployment   một lần dựng thật của Blueprint lên Device; có namespace K8s
           └── Node    một tiến trình/máy ảo trong deployment (21–22 node)
                └── Pin        cổng nối của node vào một mạng/bus ảo
                └── Part       "mặt" của node để con người/UI truy cập (face-adb, face-screen…)

Blueprint            bản thiết kế topology: node nào, pin nào, nối dây ra sao
Room                 định danh không gian chạy; thực tế trùng với id Device trong REST API
Artifact             kho file của nền tảng (APK, ảnh USB, DBC/VSS) — upload qua UI
Registry             OCI registry riêng để đẩy image container của đội
Widget               công cụ tương tác trong UI: IVI ADB, IVI Screen, GPIO Panel, USB Device, Log
```

⚠️ **`roomId` và `deviceId` là cùng một chuỗi** trong mọi lệnh REST đội đã gọi
(`v37aa3knc6t1embelr5yi`). Đừng đi tìm một "room id" khác.

---

## 3. Các loại node đã gặp thật

Đếm trên room VIVA (21 node ở V3, 22 node sau khi thêm `VIVA ASR`).
Bản đầy đủ: `backend/carsky/nodes.json`.

| `nodeType` | Số lượng | Là gì | Ví dụ trong room VIVA |
|---|---|---|---|
| `script-node` | 8 | Chạy một script Lua/Luau do người dùng nạp. Đây là chỗ viết logic gateway/ECU | **IVI Gateway**, **PWT Gateway**, BCM/Climate/VCU/BMS/TCU Gateway |
| `gpio-panel` | 4 | Bảng slider/switch trong UI, đóng vai cảm biến vật lý | **Drive Controls** (đặt tốc độ), TirePressure, SeatBelt, Battery |
| `can-bus` | 2 | Một bus CAN ảo, định nghĩa bằng DBC | BCM CAN, PWT CAN |
| `kuksa-databroker` | 1 | Databroker VSS trung tâm (1.268 tín hiệu) | Central Broker VSS |
| `skycraft` | 1 | **Máy ảo Android thật** (aarch64) | **IVI - Android** — nơi cài APK |
| `container` | 3 | Image OCI do đội đẩy lên registry | **VIVA ASR**, TCU-NAD, SeatBelt ECU |
| `eth-bridge` | 2 | Switch L2 ảo + DHCP server nhỏ | IVI Switch, TCU Switch |
| `device-proxy` | 1 | Cầu nối thiết bị (USB) từ trình duyệt vào VM | Device Proxy |

---

## 4. Pin — cổng nối của node

Pin quyết định node được nối vào bus/mạng nào. Đây là chỗ có **giới hạn thiết kế
quan trọng nhất của nền tảng**:

```
pinType hợp lệ toàn cục            : VHAL | KUKSA | CAN | LIN | VIDEO | GPIO | GENERIC
pinType hợp lệ trên container node : CAN, KUKSA, VIDEO
```

🚫 **Pin `ETHERNET` không tạo được bằng REST API** — dù blueprint do chính nền tảng
sinh ra thì đang dùng nó. Bằng chứng (đã chạy trên blueprint clone tạm, xoá sau):

| Đường thử | Kết quả |
|---|---|
| `POST /api/v1/nodes/{id}/pins` | `404 Route not found` |
| `POST /api/v1/blueprints/nodes/{id}/pins` với `ETHERNET` | `400` — enum từ chối |
| cùng route với `GENERIC` | `422 "Pin type GENERIC is not allowed on container nodes. Allowed: CAN, KUKSA, VIDEO"` |
| `POST /api/v1/blueprints/import` bản export của chính nền tảng | `400`, cùng lỗi enum |

**Hệ quả vận hành:**

1. Muốn thêm container node **có mạng** thì phải **vào web UI** thêm pin và nối dây.
2. 🔴 **Đừng `DELETE` node rồi tạo lại** để né lỗi — đường một chiều, node mới sẽ
   không nối lại được vào `IVI Switch`.
3. File JSON export **không phải** đường khôi phục. Đường rollback đúng là
   `POST /blueprints/{id}/clone` (bản sao server-side, giữ nguyên pin ETHERNET).

---

## 5. Part và widget — cách con người chạm vào node

Node Android có tiền tố part là `face`:

| Part | Dùng cho |
|---|---|
| `face-adb` | Widget **IVI ADB** — terminal xterm với shell thật trên VM |
| `face-screen` | Widget **IVI Screen** — màn hình + chuột/chạm |
| `face-audio` | Âm thanh hai chiều; ô **Recorder Part: Client Microphone** đưa mic laptop thẳng vào VM |
| `face-logcat` | Luồng logcat |

⚠️ **Touch Part hay biến mất sau mỗi lần VM reboot** — phải chọn lại trong Inspector.

Widget khác đã dùng thật: **GPIO Panel** (kéo slider tốc độ), **Log** (xem log
script node), **USB Device** (mount ảnh đĩa vào VM), **Artifacts** (upload file).

---

## 6. Quota — trần cứng của tài khoản

Đọc từ `GET /config/limits` và `/account-limits/effective/{acc}`:

```
MAX_DEVICES                 = 5
MAX_NODES_PER_BLUEPRINT     = 30
MAX_CONCURRENT_DEPLOYMENTS  = 2      ← trần đau nhất
MAX_SKYCRAFT_PER_BLUEPRINT  = 2
```

`MAX_CONCURRENT_DEPLOYMENTS = 2` là lý do:

- Không bật CI tự động deploy theo `push` (xem [06](06-REGISTRY-VA-CI.md)).
- Dựng deployment dự phòng để không gián đoạn thì **hết slot** — nhớ xoá sau khi xong.

---

## 7. Ba tầng có thể hỏng độc lập — nhớ khi chẩn đoán

Một triệu chứng ("app không nói được", "lệnh không tới xe") có thể đến từ ba tầng
hoàn toàn khác nhau. Đừng nhảy tầng khi chưa loại trừ:

```text
① Nền tảng CarSky   deployment còn không? node Running? script node còn subscription?
② Mạng trong room   eth1 của guest có IPv4? route đúng bảng? node ASR trả /health?
③ App Android       đúng flavor? đúng engine ASR? đủ quyền? đúng ngôn ngữ giọng nói?
```

Cách kiểm từng tầng: [08 — Preflight & khôi phục](08-PREFLIGHT-VA-KHOI-PHUC.md).
