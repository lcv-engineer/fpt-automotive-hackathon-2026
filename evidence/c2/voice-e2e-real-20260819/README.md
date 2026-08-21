# GIỌNG NÓI END-TO-END TRÊN DEVICE CARSKY — BẢN `real` — 19/08/2026

**Nhãn:** REAL RUN trên Device CarSky. Mic thật → node ASR của đội trên CarSky →
NLU → SafetyGuard → MediaSession, **cùng một phiên, cùng một APK**.
Không phải emulator. Không bơm text. Không mock ASR.

## 1. Identity — mọi thứ dưới đây thuộc về một bản duy nhất

| Hạng mục | Giá trị |
|---|---|
| Commit | `0c67010878ebfbd4ca11915c0b8d5a85dc4cda78` (main) |
| APK | `app-real-debug.apk`, flavor **real**, `sha256 48f9830f6230c03a66f8b36e9c26c2448fdfc99028e5e0a00e93cec23677ea7e` |
| Hash đối chiếu | khớp **ba chặng**: máy dev → file trên USB image trong guest → `base.apk` đã cài (xem `real-apk-local-build-0819.txt`) |
| Package | `com.sopa.viva_automotive` (real, cài thường — **không** priv-app) |
| Build flag | `-PvivaAsrBaseUrl=http://10.99.0.3:8080` (node VIVA ASR trong room) |
| ASR engine | `AsrEngine.VIVA` (mặc định; DataStore chưa từng bị ghi đè — kiểm bằng `run-as … cat files/datastore/*.preferences_pb`) |
| Room | `v37aa3knc6t1embelr5yi` · deployment `VIVA-demo-0808` · 22/22 Running |
| Node ASR | `b8eada00-d137-4fdc-a131-2197b1d1356b` · image `viva-asr@sha256:6ca09c24…` · model `phowhisper-tiny-int8` |
| Thời gian | 2026-08-19, khoảng 13:55–14:02 (giờ guest) |

## 2. BIÊN NHẬN CÙNG IDENTITY — lượt thành công trọn vẹn

Lượt `45f04c7b-e02e-424b-9d00-46b834d9bedc`, cùng một UUID xuất hiện ở **ba nguồn độc lập**:

**(a) logcat trên Device (app)**
```
VIVA_TRACE_SUMMARY|45f04c7b-e02e-424b-9d00-46b834d9bedc|phát nhạc lên.|media_play|Allow|e2e_ms=1086
VoiceAgent status=APPLIED transcript="phát nhạc lên." spoken="Playing Midnight Cabin"
```

**(b) log container ASR — lấy TỪ NỀN TẢNG CarSky**
(`GET /deployments/{room}/logs/{node}?container=user`)
```
2026-08-19 14:01:17,247 INFO viva.asr VIVA_ASR|45f04c7b-e02e-424b-9d00-46b834d9bedc|ok|server_ms=680|audio_ms=1496|conf=0.67|chars=14|segments=1
```

**(c) readback consumer — MediaSession trong guest**
```
state=PlaybackState {state=STOPPED(1), position=4000, active item id=2, error=null}
audioAttrs: usage=USAGE_MEDIA content=CONTENT_TYPE_MUSIC
metadata: size=5, description=Midnight Cabin, VIVA Demo
```
Bài đang phát lúc mở app là **"Coastal Cruise"**; sau chuỗi lệnh đổi thành
**"Midnight Cabin"**, `active item id=2` ⇒ lệnh thoại thật sự tác động lên consumer.

Cơ chế nối: app gắn `X-Trace-Id` khi gọi `/asr` (`HttpAsrClient.kt:74`), server ASR
log lại đúng trace id đó (`asr/app/main.py:162`). Không phải trùng hợp — là hợp đồng.

## 3. Bảng expected-vs-observed — TOÀN BỘ lượt trong phiên

| # | UUID (rút gọn) | Câu nói (kỳ vọng) | Transcript (quan sát) | Intent | Verdict | e2e_ms | server_ms / conf (node) |
|---|---|---|---|---|---|---|---|
| 1 | `4eb82d43` | giảm nhiệt độ xuống 24 độ | "giảm nhiệt độ xuống hai tư độ." | `hvac_set_temp` | `Error:exec_done` | 1030 | 687 / 0.80 |
| 2 | `28c3181d` | tăng nhiệt độ lên 24 độ C | "tăng nhiệt độ lên hai tư độ xê." | `hvac_set_temp` | `Error:exec_done` | 890 | 648 / 0.80 |
| 3 | **`45f04c7b`** | **phát nhạc lên** | **"phát nhạc lên."** | **`media_play`** | **`Allow` → APPLIED** | **1086** | **680 / 0.67** |
| 4 | `4b76dc21` | dừng nhạc | "dân nhạc." ❌ | `unknown` | `Deny:G3_UNSUPPORTED` | 1055 | 755 / 0.61 |
| 5 | `d5dfe173` | dừng nhạc | "dân nhạc." ❌ | `unknown` | `Deny:G3_UNSUPPORTED` | 841 | 631 / 0.65 |
| 6 | `c879bfab` | (không rõ) | "" (rỗng) | `unknown` | `Error:asr_done` | 118 | — |
| 7 | `a4f7ae17` | chuyển bài | "cho dáng bay." ❌ | `unknown` | `Confirm:G3_LOW_CONFIDENCE` | 1073 | 753 / **0.46** |

Node ASR phục vụ **13 lượt** trong phiên (xem `asr-node-viva-lines.txt`); bảng trên là
7 lượt có trace tương ứng trong logcat đã trích.

## 4. Điều này chứng minh — và KHÔNG chứng minh — cái gì

### Chứng minh được
1. **App trên Device CarSky gọi đúng node ASR của đội trong room**, không phải ASR
   on-device, không phải cloud. Bằng chứng hai phía cùng UUID.
2. **Chuỗi core flow chạy trong một run**: mic → Silero VAD → `/asr` trên CarSky →
   NLU → SafetyGuard → MediaSession, với **readback ở consumer**.
3. **Ngân sách latency đạt**: `e2e_ms` 841–1086 ms cho các lượt có tiếng, dưới mức
   1500 ms. `server_ms` phía node 631–755 ms.
4. **SafetyGuard hành xử đúng ở tình huống biên, trên Device thật**: G3 từ chối câu
   ngoài phạm vi thay vì đoán bừa (lượt 4, 5); G3_LOW_CONFIDENCE hỏi lại khi
   confidence 0.46 (lượt 7); transcript rỗng trả `Error:asr_done` (lượt 6).
   **Không lượt nào bị "giả thành công".**

### KHÔNG chứng minh được — đọc kỹ trước khi trích
1. **Không chứng minh VHAL/CAN.** Hai lượt `hvac_set_temp` trả `Error:exec_done` vì
   APK cài thường không có `android.car.permission.CONTROL_CAR_CLIMATE`
   (`signature|privileged`). Ngoài ra image còn bật `use_local_fake_server=true` khiến
   VHAL đọc/ghi không tới gateway — xem `vhal-local-fake-server-blocker-0819.txt`.
   Đường HVAC/door vẫn ở trạng thái **chưa chứng minh**.
2. **Không phải benchmark độ chính xác.** 3/7 lượt ASR sai từ ("dừng nhạc"→"dân nhạc",
   "chuyển bài"→"cho dáng bay"). Đây là phiên chứng minh đường đi, **không** phải phép
   đo WER có thẩm quyền — muốn đo phải chạy corpus cố định, cùng người nói, có kịch bản.
3. **Không phải cabin thật.** Mic là mic laptop truyền qua widget `Client Microphone`
   của CarSky vào máy ảo `trout_arm64`. Không được nói "đo trong cabin".
4. **Chưa có ablation.** Chưa chạy đối chứng `AsrEngine.VIVA → GOOGLE` để chứng minh
   node ASR là *điều kiện cần*.

## 5. Điều kiện tiên quyết phải lặp lại trước mỗi phiên

Phiên này chỉ chạy được sau khi khôi phục hai thứ đã hỏng âm thầm từ 12/08:

```
# 1. Node script mất subscription -> restart (API trả HTTP 500 nhưng vẫn có tác dụng)
POST /deployments/{room}/restart/{vcu-node}
POST /deployments/{room}/restart/{ivi-gateway-node}

# 2. Guest mất IPv4 trên eth1 -> mạng room không tới được (mất sau MỖI lần reboot VM)
su 0 ip addr add 10.99.0.14/24 dev eth1
su 0 ip route add 10.99.0.0/24 dev eth1 table legacy_system
# kiểm: ip route get 10.99.0.3  -> phải ra "dev eth1 src 10.99.0.14"
# kiểm đúng danh tính app:
run-as com.sopa.viva_automotive sh -c 'curl -sm 5 http://10.99.0.3:8080/health'
```

⚠️ **Log node ASR chỉ sống theo vòng đời pod và Loki rỗng** — phải kéo `?container=user`
**ngay trong phiên**, trước mọi thao tác restart. Mất pod là mất sạch, không lấy lại được.

## 6. File trong thư mục

| File | Nội dung |
|---|---|
| `asr-node-user.json` | Nguyên văn phản hồi `GET /logs/{node}?container=user&tail=3000` |
| `asr-node-viva-lines.txt` | 13 dòng `VIVA_ASR\|<uuid>\|ok\|…` trích từ trên |
| `README.md` | File này |

Thiếu (nên bổ sung khi có): bản logcat đầy đủ tải từ widget `face-logcat`, và video do
nút **Record** của nền tảng ghi.
