# 10 — Quan sát, trace và bản đồ bằng chứng

> Nguyên tắc: **mỗi claim phải chỉ được vào một thư mục evidence**. Claim không có
> evidence thì ghi là chưa có, không "gần như".

---

## 1. Ba nguồn quan sát

| Nguồn | Lấy bằng | Chứa gì |
|---|---|---|
| **logcat của app** | widget **Log** (`face-logcat`) trên CarSky; `adb logcat` trên emulator | `VIVA_TRACE`, `VIVA_TRACE_SUMMARY`, `VIVA_VOICE`, `RoutingAsrClient`, `RealVehicleRepo` |
| **Log node container** | `GET /deployments/{room}/logs/{node}?container=user` | Log `viva.asr`: `model ready in …`, `POST /asr` |
| **Tín hiệu nền tảng** | `POST /signals/{room}/{nodeKey}/values` | Giá trị GPIO / CAN / KUKSA tại thời điểm đọc |

⚠️ **`/logs/{node}` của node Android KHÔNG phải logcat** — nó là log pod (WebRTC).
`VIVA_TRACE` chỉ có trong logcat.

⚠️ **Log container chỉ sống theo vòng đời pod, Loki rỗng.** Kéo log **trước** mọi
thao tác restart.

---

## 2. Đọc một dòng trace

```text
VIVA_TRACE|<traceId>|<stage>|<elapsedRealtimeNanos>
VIVA_TRACE_SUMMARY|<traceId>|<utterance>|<intent>|<verdict>|e2e_ms=<so nguyen>
```

`verdict := "Allow" | "Deny:"<RULE_ID> | "Confirm:"<RULE_ID> | "Error:"<STAGE_ID>`

Định nghĩa metric và các bẫy: [07](07-BACKEND-HARNESS.md).

Ba câu hỏi trace trả lời được ngay:

| Câu hỏi | Nhìn vào |
|---|---|
| Lượt chết ở chặng nào | `Error:<stage>` trong summary |
| Guard có chặn không, luật nào | `Deny:G1_SPEED_LOCK` |
| Chậm ở chặng nào | hiệu hai mốc liền kề trong dòng `VIVA_TRACE` |

Lỗ hổng đã biết: `Confirm:G3_LOW_CONFIDENCE` **không ghi con số confidence** → không
phân biệt `0.59` (suýt qua) với `0.12` (rác thật). Thêm giá trị vào summary là một
dòng code.

---

## 3. Bản đồ `evidence/`

```text
evidence/
  ablation/        A1 (SafetyGuard) va A4 (grammar) — CSV + run manifest
  asr/             corpus, corpus-human, v12/v13/v14, asr-bench-manifest.txt
  carsky/          signals-rest-0808, asr-node-logs-0818, v7-* (them node ASR)
  emulator/        phien tren emulator AAOS: anh chup, log, artifact identity
  c2/              bang chung tren Device CarSky — xem bang duoi
  artifact-identity-ci.txt
  jvm-test-summary.txt
  device-info.txt
```

### `evidence/c2/` — bằng chứng trên Device CarSky

| Thư mục / file | Ngày | Chứng minh điều gì |
|---|---|---|
| `carsky-runtime-20260809/` | 09/08 | Voice → MediaBrowser → MediaSession/ExoPlayer chạy trên Device (flavor **mock**, **bơm text**) |
| `carsky-voice-e2e-20260810/` | 10/08 | **Giọng nói end-to-end THẬT**: mic → Silero VAD → `viva-asr` trong room → NLU → SafetyGuard → thực thi |
| `car-speed-permission-probe-0818.txt` | 18/08 | `CAR_SPEED` là `prot=dangerous` → `pm grant` được, không cần privileged install |
| `real-apk-local-build-0819.txt` | 19/08 | Bản `real` lên Device lần đầu; hash khớp ba chặng |
| `vhal-local-fake-server-blocker-0819.txt` | 19/08 | 🔴 Root cause chặn readback VHAL + chuỗi GPIO→CAN→KUKSA→VHAL push chạy đủ |
| `voice-e2e-real-20260819/` | 19/08 | Phiên giọng nói trên bản `real` |
| `voice-ab-prompt-20260820/` | 20/08 | A/B domain biasing (`ASR_INITIAL_PROMPT`) |
| `vosk-restored-20260820.txt` | 20/08 | Khôi phục Vosk on-device |
| `m1a-debuggable-privapp-probe.txt` | | Thăm dò privileged install |

---

## 4. Bảng claim ↔ evidence ↔ giới hạn

| Claim | Evidence | ⚠️ Giới hạn phải khai kèm |
|---|---|---|
| App chạy trên Device AAOS thật của CarSky | `real-apk-local-build-0819.txt` | `trout_arm64`, Android 14/SDK 34; VM ảo, không phải xe |
| Voice → media chạy tới MediaSession | `carsky-runtime-20260809/` | Flavor **mock**, **bơm text** qua receiver debug — **bỏ qua mic/VAD/ASR**, nên `e2e_ms=0` **không** phải độ trễ giọng nói. Không có TTS tiếng Việt trên Device |
| Giọng nói end-to-end thật, app gọi đúng node ASR trong room | `carsky-voice-e2e-20260810/` | 25 lượt: 13 `Allow` đúng intent, 2 `Deny:G1_SPEED_LOCK`, **10 không nhận ra**. **p95 = 1664 ms > ngân sách 1500 ms** |
| Chuỗi nền tảng GPIO → CAN → KUKSA → VHAL push chạy đủ | `vhal-local-fake-server-blocker-0819.txt` | Đây là chuỗi của **nền tảng**; app **chưa đọc lại được** vì `use_local_fake_server` |
| SafetyGuard có tác dụng đo được | `ablation/a1-*` | Chạy trên **mock/emulator**; A1 = bỏ guard → 6/9 lệnh nguy hiểm ghi được |
| Grammar có tác dụng đo được | `ablation/a4-grammar-ablation.csv` | Bỏ grammar → *"đặt nhiệt độ 40 độ"* đi thẳng thành `SetTemperature(40.0)` |
| `viva-asr` phiên âm tiếng Việt thật | `evidence/asr/` | 36 clip **giọng tổng hợp**; RTF 0.167, `server_ms` p50 439/p95 667 **trên CPU máy dev**, WER 0.411 |
| Nền tảng xác nhận metadata tín hiệu | `evidence/carsky/signals-rest-0808/` | REST gọi **thẳng KUKSA** — không có APK/VHAL/SafetyGuard trong đường này; là công cụ **đo**, không phải core flow |
| Container `viva-asr` chạy trong room | `evidence/carsky/v7-*`, `asr-node-logs-0818` | Không chứng minh gì về latency trong room |
| APK truy nguyên được về commit | `artifact-identity-ci.txt` | Chỉ đúng với APK build sau `clean` |

---

## 5. Ba lỗi trình bày phải tránh

| Lỗi | Vì sao sai |
|---|---|
| Trích `*_incl_speech` là "end-to-end" | Nó cộng cả thời gian tài xế **nói** → câu dài thành "hệ thống chậm" |
| Nói "hệ thống đạt p95 < 1500 ms" | Bộ đo trên Device cho **1664 ms**. Chỉ được nói con số thật kèm điều kiện đo |
| Nói "10/16 intent chạy tới CAN" | Chỉ `hvac_*`, `door_lock`, `cabin_lights` đi đường Vehicle Property |

Và một lỗi đóng gói đã trả giá thật: **bằng chứng mạnh nhất không được quay vào
video**. Bộ 10/08 có đúng cả hai vế mà BTC đòi (*app gọi đúng ASR node* **và**
*policy output đi tới consumer* trong **cùng một run**), nhưng video nộp lại quay lát
cắt emulator/mock — và BTC chấm đúng thứ có trong video.

---

## 6. Checklist ghi evidence cho một phiên

Trước khi kết thúc phiên, ghi lại **năm** thứ:

```
commit           git rev-parse HEAD (+ worktree dirty?)
sha256 APK       ba chang: may build = file tren Device = base.apk
digest image     node ASR dang chay image nao
roomId           va ten deployment
gio chay         de doi chieu voi log node/logcat
```

Và kéo về:

1. Log node ASR: `logs/{ASR}?container=user&tail=3000`
2. Logcat từ widget `face-logcat`
3. `dumpsys media_session | grep -iA3 "state=PlaybackState"` (readback consumer)

Chi tiết: [`docs/carsky/08 PHẦN 4`](../carsky/08-PREFLIGHT-VA-KHOI-PHUC.md).
