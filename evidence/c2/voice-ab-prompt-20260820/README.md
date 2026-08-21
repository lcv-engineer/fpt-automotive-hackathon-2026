# A/B DOMAIN BIASING — KẾT QUẢ: PROMPT PHẢN TÁC DỤNG (20/08/2026)

**Nhãn:** phép đo A/B thật trên hai room CarSky chạy song song, mic thật, cùng người nói.
**Kết luận:** `ASR_INITIAL_PROMPT` **làm hỏng** nhận dạng trên `phowhisper-tiny-int8`.
Đã rollback. Phát hiện kèm theo: `ASR_MAX_NEW_TOKENS` chưa đặt là một **lỗ hổng thật**.

## 1. Thiết kế thí nghiệm — chỉ một biến khác nhau

| | **A — baseline** | **B — prompt, không giới hạn** | **C — prompt + max 32** |
|---|---|---|---|
| Room | `v37aa3knc6t1embelr5yi` | `wcmfnwigjse4hv9r8s0e3` | `wcmfnwigjse4hv9r8s0e3` |
| Deployment | `VIVA-demo-0808` | `VIVA-asr-prompt-0820` | `VIVA-asr-prompt-v2-0820` |
| Blueprint | `6deadb05` | **cùng** | **cùng** |
| APK sha256 | `48f9830f…23677ea7e` | **cùng** | **cùng** |
| Model | `phowhisper-tiny-int8` | **cùng** | **cùng** |
| Người nói | Vĩ | **cùng** | **cùng** |
| `ASR_INITIAL_PROMPT` | `null` | **có** (16 cụm từ) | **có** (16 cụm từ) |
| `ASR_MAX_NEW_TOKENS` | `0` (không giới hạn) | `0` | **`32`** |

Prompt dùng ở B/C:
> `Lệnh điều khiển xe: điều hòa, nhiệt độ, độ C, quạt gió, mức quạt, mở khóa cửa, khóa cửa, ghế sưởi, âm lượng, phát nhạc, dừng nhạc, chuyển bài, bật, tắt, tăng, giảm.`

## 2. Kết quả then chốt — CÙNG MỘT CÂU "phát nhạc lên"

| | **A (không prompt)** | **C (prompt + max 32)** |
|---|---|---|
| UUID | `45f04c7b-e02e-424b-9d00-46b834d9bedc` | `e1834029-201b-4556-8b6d-1e047ff7f7a4` |
| Transcript | **"phát nhạc lên."** | **"đám đồng độc da sưởi da di dụi da di dụi da di dụi da di dụi da di"** |
| Intent | `media_play` | `unknown` |
| Verdict | **`Allow` → APPLIED** ("Playing Midnight Cabin") | `Deny:G3_UNSUPPORTED` |
| `conf` | 0.67 | 0.52 |
| `chars` | 14 | **66** |
| `server_ms` | 680 | 904 |
| `audio_ms` | 1496 | 2008 |

⚠️ Chú ý chữ **"sưởi"** trong transcript của C — từ này lấy thẳng từ prompt (`ghế sưởi`).
Đây là bằng chứng trực tiếp: **prompt kéo model bịa ra chính từ vựng trong prompt**.

## 3. Toàn bộ số đo `server_ms` / `chars`

**A — baseline, không prompt** (room cũ, 19/08, 9 lượt; trích 4 lượt có trace)
```
VIVA_ASR|45f04c7b|ok|server_ms=680 |audio_ms=1496|conf=0.67|chars=14
VIVA_ASR|4b76dc21|ok|server_ms=755 |audio_ms=1208|conf=0.61|chars=9
VIVA_ASR|d5dfe173|ok|server_ms=631 |audio_ms=1132|conf=0.65|chars=9
VIVA_ASR|a4f7ae17|ok|server_ms=753 |audio_ms=1400|conf=0.46|chars=13
```

**B — prompt, `max_new_tokens=0`** (room mới v1)
```
VIVA_ASR|47d632c7|ok|server_ms=20209|audio_ms=1464|conf=0.15|chars=55
VIVA_ASR|361524fc|ok|server_ms=25176|audio_ms=1944|conf=0.94|chars=297
VIVA_ASR|8c002c53|ok|server_ms=24816|audio_ms=1848|conf=0.11|chars=163
```

**C — prompt + `max_new_tokens=32`** (room mới v2)
```
VIVA_ASR|2bb4f4be|ok|server_ms=3714|audio_ms=1400|conf=0.65|chars=43   <- warmup
VIVA_ASR|078f2548|ok|server_ms=965 |audio_ms=2008|conf=0.56|chars=51
VIVA_ASR|a8e8486d|ok|server_ms=992 |audio_ms=1912|conf=0.50|chars=66
VIVA_ASR|e1834029|ok|server_ms=904 |audio_ms=2008|conf=0.52|chars=66
```

## 4. Hai kết luận

### 4.1 🔴 Prompt phản tác dụng — ĐÃ ROLLBACK

Cùng câu *"phát nhạc lên"*: không prompt ra **đúng lệnh**, có prompt ra **rác**.
Model `tiny` bị prompt kéo vào vòng lặp sinh từ trong chính prompt.

Điều này **phủ định** giả định ghi trong `asr/app/config.py`:
> *"Domain biasing … pulls near-homophone errors back toward in-domain words … It costs
> no extra decoding time because the prompt is just prepended context."*

Không đúng với model `tiny`: prompt vừa làm sai transcript, vừa (khi không chặn token)
làm chậm gấp ~33 lần. Comment này nên được sửa kèm dẫn chiếu tới file evidence.

⇒ **Đã gỡ `ASR_INITIAL_PROMPT` khỏi blueprint.** Nếu muốn thử lại domain biasing:
dùng `ASR_HOTWORDS` (nhẹ hơn, chỉ vài từ) thay vì prompt câu dài, và **đo lại A/B**
trước khi tin.

### 4.2 🟢 Tìm ra lỗ hổng thật: `ASR_MAX_NEW_TOKENS=0`

Mặc định không giới hạn số token sinh ra. Khi model hallucinate (dù có prompt hay
không), nó sinh chuỗi dài vô hạn → `server_ms` **20–25 giây**, phá vỡ ngân sách
p95 1500 ms của `03-contracts.md` §1.3.

Đặt `ASR_MAX_NEW_TOKENS=32`: **25176 ms → 904 ms**.

⇒ **Giữ lại thiết lập này** kể cả sau khi bỏ prompt. Đây là lưới an toàn latency mà
trước nay chưa có. Nên đưa vào **default trong `config.py`**, không chỉ env của node.

## 5. Điều này KHÔNG chứng minh

- Không chứng minh domain biasing vô dụng nói chung — chỉ vô dụng với **prompt câu dài
  16 cụm từ trên `phowhisper-tiny-int8`**. Model lớn hơn hoặc prompt ngắn hơn có thể khác.
- Không phải benchmark WER: mỗi nhánh chỉ vài lượt, không chạy hết corpus.
- Các lượt ở B là im lặng/nhiễu; chỉ lượt `e1834029` ở C là câu nói thật đã xác nhận.

## 6. Chi phí đã trả để có phép đo này

- Deploy room thứ hai trên device `VIVA-AB-prompt` (quota 2 concurrent, đội có 4 device)
- **`undeploy` xoá sạch `/data`** → phải cài lại APK qua USB image mỗi lần deploy lại
- Xác minh lại: config node **chỉ áp dụng khi tạo deployment mới** (xem `vong2/37` §phụ lục)

## 7. File trong thư mục

| File | Nội dung |
|---|---|
| `asr-node-user.json` | log node ASR nhánh **B** (prompt, không giới hạn) |
| `asr-node-v2.json` | log node ASR nhánh **C** (prompt + max 32) |
| `README.md` | file này |

Baseline nhánh **A**: `evidence/c2/voice-e2e-real-20260819/`.
