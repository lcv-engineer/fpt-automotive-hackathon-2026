# 38 — Phép A/B domain biasing: mọi thứ đã sẵn, chỉ còn nói ba câu

> Lập 20/08/2026 · **TRẠNG THÁI: ĐÃ ĐO XONG, ROOM A/B ĐÃ XOÁ.**
>
> 🔴 **Kết quả: prompt PHẢN TÁC DỤNG, đã rollback.** Xem
> `evidence/c2/voice-ab-prompt-20260820/README.md` — cùng câu *"phát nhạc lên"*:
> không prompt ra `media_play|Allow`, có prompt ra `"đám đồng độc da sưởi da di dụi…"`.
> Phát hiện kèm theo: `ASR_MAX_NEW_TOKENS=0` là lỗ hổng thật (server_ms 25s → 904ms
> khi đặt `=32`); đã giữ `=32` trong blueprint.
>
> Room `wcmfnwigjse4hv9r8s0e3` **đã xoá 20/08** để trả quota. Muốn đo lại phải deploy
> mới + cài lại APK (undeploy xoá sạch `/data`).
>
> Phần dưới giữ nguyên làm hồ sơ cách dựng phép A/B — tái dùng được cho lần sau.

---

## 1. Đang chờ làm gì

Nói **đúng ba câu** vào mic ở room mới, rồi lấy log. Hết.

> *"phát nhạc lên"* → *"dừng nhạc"* → *"chuyển bài"*

Hai câu sau là trọng tâm: ở room cũ chúng bị nghe sai và không ra được lệnh.

---

## 2. Hai room đang chạy song song

| | **Room CŨ** (baseline) | **Room MỚI** (có prompt) |
|---|---|---|
| Device | `VIVA` | **`VIVA-AB-prompt`** (đổi tên 20/08 từ `VIVA (Copy)` cho khỏi nhầm) |
| roomId | `v37aa3knc6t1embelr5yi` | `wcmfnwigjse4hv9r8s0e3` |
| Deployment | `VIVA-demo-0808` | `VIVA-asr-prompt-0820` |
| namespace | `room-z0as6abg` | `room-9pjsm4pz` |
| Blueprint | `6deadb05-c856-4dab-976b-432b0fac0658` | **cùng blueprint** |
| APK sha256 | `48f9830f…23677ea7e` | **cùng hash** (đã đối chiếu) |
| Model | `phowhisper-tiny-int8` | **cùng model** |
| **`initial_prompt`** | **`null`** | **CÓ** ← *biến duy nhất khác nhau* |
| Trạng thái | có evidence 19/08 (7 lượt) | app đã cài, mạng đã vá, **chưa nói lượt nào** |

⇒ Đúng nghĩa *"cùng corpus, cùng baseline, đổi một biến"* — loại bằng chứng BGK gọi là
**có thẩm quyền** ở ô ③ *Lợi ích so với baseline* (−3 điểm).

Node ASR ở cả hai room đều là `b8eada00-d137-4fdc-a131-2197b1d1356b`.

---

## 3. Baseline đã đo — room CŨ, 19/08 (không prompt)

Nguồn đầy đủ: `evidence/c2/voice-e2e-real-20260819/README.md`

| # | UUID | Câu nói | **Transcript nghe được** | Intent | Verdict | e2e_ms | conf |
|---|---|---|---|---|---|---|---|
| 3 | `45f04c7b` | phát nhạc lên | "phát nhạc lên." ✅ | `media_play` | **`Allow` → APPLIED** | 1086 | 0.67 |
| 4 | `4b76dc21` | dừng nhạc | **"dân nhạc."** ❌ | `unknown` | `Deny:G3_UNSUPPORTED` | 1055 | 0.61 |
| 5 | `d5dfe173` | dừng nhạc | **"dân nhạc."** ❌ | `unknown` | `Deny:G3_UNSUPPORTED` | 841 | 0.65 |
| 7 | `a4f7ae17` | chuyển bài | **"cho dáng bay."** ❌ | `unknown` | `Confirm:G3_LOW_CONFIDENCE` | 1073 | **0.46** |

Tỉ lệ ra đúng lệnh: **1/4**.

Prompt được soạn để chữa đúng lớp lỗi này — chứa sẵn `phát nhạc`, `dừng nhạc`,
`chuyển bài`, `độ C`.

---

## 4. Việc cần làm khi quay lại (theo thứ tự)

### 4.1 Kiểm room mới còn sống (~1 phút)

Trong ADB shell của device **`VIVA-AB-prompt`** (bấm **Connect** ở sidebar trước, widget mới hiện):
```
run-as com.sopa.viva_automotive sh -c 'curl -sm 8 http://10.99.0.3:8080/health'
```
Phải thấy `initial_prompt` có nội dung. Nếu lỗi → xem mục 5.

### 4.2 Bật mic

Widget **IVI Screen** của `VIVA-AB-prompt` → **Recorder Part** → `Client Microphone`,
chấm xanh, không Mute. Giữ app ở **foreground** (app không có foreground service).

### 4.3 Nói ba câu

*"phát nhạc lên"* → *"dừng nhạc"* → *"chuyển bài"*, cách nhau vài giây.
Nói thêm 2–3 lượt lặp lại càng tốt (ăn thêm ô ① *ổn định/lặp lại*).

### 4.4 Lấy log NGAY (log node chết theo pod!)

Trong shell:
```
logcat -d -s VIVA_TRACE:I VIVA_VOICE:I | grep -E "SUMMARY|status=" | tail -15
```

Từ máy dev (kéo log node ASR của **room mới**):
```bash
cd backend
KEY=$(grep '^CARSKY_API_KEY=' .env | cut -d= -f2- | tr -d '\r')
B=$(grep '^CARSKY_BASE_URL=' .env | cut -d= -f2- | tr -d '\r')
curl -s -H "x-api-key: $KEY" \
 "$B/deployments/wcmfnwigjse4hv9r8s0e3/logs/b8eada00-d137-4fdc-a131-2197b1d1356b?container=user&tail=3000" \
 -o evidence/c2/voice-ab-prompt-20260820/asr-node-user.json
```

### 4.5 Dựng bảng so sánh

Ghép UUID giữa logcat và log node, rồi so với bảng mục 3. Điền vào bảng này:

| Câu | Room cũ (không prompt) | Room mới (có prompt) | Đổi? |
|---|---|---|---|
| phát nhạc lên | `media_play` `Allow` (conf 0.67) | ? | |
| dừng nhạc | `unknown` `Deny:G3` — "dân nhạc" | ? | |
| chuyển bài | `unknown` `Confirm` — "cho dáng bay" (conf 0.46) | ? | |

---

## 5. Khôi phục nếu room mới hỏng

Room mới cũng dính đúng các bệnh đã biết (runbook đầy đủ ở `37-RUNBOOK-PREFLIGHT-CARSKY.md`):

```
# guest mất IPv4 sau reboot VM
su 0 ip addr add 10.99.0.14/24 dev eth1
su 0 ip route add 10.99.0.0/24 dev eth1 table legacy_system

# sau khi restart node container: ARP cache giữ MAC cũ -> timeout (KHÔNG phải refused)
su 0 ip neigh flush all

# app tụt foreground do RAM trim (không phải crash)
am start -n com.sopa.viva_automotive/.MainActivity
```

Nếu phải cài lại APK: plug artifact `viva-usb` qua widget **USB Device** → file hiện ở
`/sdcard/Music/usb_1/` → `pm install -r` → `pm grant` lại `CAR_SPEED` và `RECORD_AUDIO`.
Hash phải là `48f9830f6230c03a66f8b36e9c26c2448fdfc99028e5e0a00e93cec23677ea7e`.

---

## 6. Đã học được gì khi dựng room này (đã ghi vào runbook 37)

1. **Config node chỉ áp dụng khi TẠO deployment mới.** Sửa blueprint rồi `Redeploy`
   hay `Restart Node` đều vô hiệu với room đang chạy — đã thử cả bốn đường.
2. Đường API đúng để sửa node: `PATCH /api/v1/blueprints/nodes/{nodeId}`.
   `openapi.json` khai `/api/v1/nodes/{nodeId}` là **SAI** (404).
3. Quota `MAX_CONCURRENT_DEPLOYMENTS=2`, `MAX_DEVICES=5`, đội có 4 device
   (`VIVA`, `VIVA (Copy)`, `Gemini`, `Gemini 2`) ⇒ luôn dựng được room thử nghiệm
   song song mà không đụng room demo.
4. Thời gian deploy: container ~1 phút, `IVI - Android` (skycraft) thêm ~2 phút.
4b. Đổi tên device được qua `PATCH /api/v1/devices/{id}` với `{"name":"..."}` — id (roomId)
   giữ nguyên nên không ảnh hưởng deployment hay evidence đang trỏ theo id.
5. **Đừng tin mã trả về, tin hệ quả quan sát được**: `restart` trả 500 mà vẫn chạy;
   `Redeploy` báo *"4 node failed"* mà API cho 22/22 `Running`.

---

## 7. Dọn dẹp khi xong hẳn

- Room mới `VIVA-asr-prompt-0820`: giữ nếu còn đo tiếp; xoá bằng
  `DELETE /api/v1/deployments/wcmfnwigjse4hv9r8s0e3` để trả quota.
- Room cũ: `vcu/Speed` đang ở **106** (gốc **0**) — trả về bằng slider panel Drive Controls.
- USB image `viva-usb` đang attach ở cả hai room — Unplug khi không cần.
