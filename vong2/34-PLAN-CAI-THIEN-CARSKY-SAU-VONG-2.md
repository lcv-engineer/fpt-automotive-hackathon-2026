# 34 — Plan cải thiện phần CarSky sau bảng chấm Vòng 2

> **Chủ sở hữu:** Vĩ · viết 18/08/2026 · nguồn: bảng chấm chính thức khóa 17/08 (70/100).
> Phạm vi: **chỉ các gap liên quan CarSky**, mục tiêu là cải thiện sản phẩm
> (BTC ghi rõ: không mở lại điểm Vòng 2, không có nghĩa vụ trước 23/08).
> Kỹ thuật nền: dùng phát hiện F1–F7 và đề xuất A/B/C của `32-ENHANCE-KHOI-4-PLATFORM-CARSKY.md`.

---

## PHẦN 1 — BẢN ĐỒ: các khoản trừ CarSky KHÔNG chỉ nằm ở khối ④

Đếm lại 30 điểm chưa ghi nhận, lọc những dòng mà điều kiện lên điểm có chữ
"CarSky / Device / VHAL / readback / ASR node / same run":

| Khối | Tiểu mục | Trừ | Điều kiện BGK nêu |
|---|---|---|---|
| ① Demo | Độ đầy đủ luồng cốt lõi | −3 | một run duy nhất mic→ASR node→NLU→SafetyGuard→VHAL/Media adapter→**authoritative readback** trên artifact final |
| ① Demo | Ổn định / lặp lại | −2 | repeated + failure-recovery run **gắn đúng artifact final**, cùng Device identity, có reconnect + fallback |
| ① Demo | Tính đúng | −2 | VHAL readback + ASR ground truth; **expected-vs-observed** cho transcript/intent/verdict/effect |
| ① Demo | Kịch bản biên | −1 | unsupported/speed-lock/confirm/range chạy **trên runtime Device final** |
| ② Kỹ thuật | Kiến trúc | −1 | App→ASR và adapter→vehicle boundary **khóa trên runtime đã nộp** |
| ② Kỹ thuật | Contract | −1 | App→CarSky ASR và PropertyID→VHAL/CAN **chạy xuyên boundary với readback** |
| ② Kỹ thuật | Test | −2 | test tái lập trên **đúng final artifact**, real transcriber (một phần CarSky) |
| ② Kỹ thuật | Observability | −1 | **một correlated trace** audio→intent→policy→readback + failure recovery **trong cùng run** |
| ② Kỹ thuật | Artifact identity | −1 | manifest nối commit→source build được→APK→**node image**→video→logs→test |
| ③ Giá trị | Mức quyết định outcome | −2 | ablation **same-input counterfactual trên final Device/VHAL path** |
| ③ Giá trị | Khác biệt | −1 | đối chứng trên **end-to-end vehicle path có authoritative readback** |
| ④ Platform | Align | −3 | App gọi đúng ASR node + policy output tới VHAL/CAN **hoặc Media consumer** trong cùng run |
| ④ Platform | Độ sâu | −4 | capability/contract của platform là **điều kiện cần** cho core outcome |
| ④ Platform | Evidence | −2 | **một biên nhận cùng identity**: app, ASR node, invocation, verdict, readback |

**Cộng: 14 dòng ≈ 26/30 điểm chưa ghi nhận có gốc CarSky** (phần còn lại: corpus/
benchmark thẩm quyền −3 ở ③, pilot H1–H4 −1 ở ⑤, accuracy mâu thuẫn trong −2 test).

### Một câu tóm tắt toàn bộ bảng chấm

BGK lặp đi lặp lại **cùng một yêu cầu** dưới 14 cách diễn đạt:

> *Một phiên chạy duy nhất, trên Device CarSky, với artifact được khóa danh tính,
> trong đó chuỗi mic→ASR node→NLU→SafetyGuard→thực thi có **đọc lại từ nguồn có
> thẩm quyền**, có lặp lại, có phục hồi lỗi, và mọi mảnh bằng chứng mang cùng một identity.*

Chính khuyến nghị của BGK cũng viết thẳng: *"Ưu tiên cùng mentor đóng một flow nhỏ
nhưng liên tục trên CarSky: input giọng nói hoặc transcript→intent→SafetyGuard→
VHAL/CarProperty→readback."* — và chấp nhận cả hai mức input (*"giọng nói **hoặc**
transcript"*), tức phiên bơm text vẫn có giá trị nếu khép kín vế sau.

⇒ **Kế hoạch không phải là 14 việc. Là MỘT PHIÊN CHẠY được chuẩn bị kỹ**, cộng vài
việc khóa danh tính trước đó và một lớp mở rộng sau đó.

---

## PHẦN 2 — HAI TIN TỐT ĐÃ XÁC MINH, MỘT ĐIỀU PHẢI NÓI THẲNG

**Tin tốt 1 — "HEAD không build được" đã được sửa sau thời điểm nộp.**
BGK chấm snapshot `5cb9b31` (locator của họ trỏ `settings.gradle.kts` L40-43) —
lúc đó include `phone-companion` là bắt buộc và hai module đó không có trong repo
nộp. Commit `5d96156` (sau nộp) đã chuyển sang include **có điều kiện**
(`if (dir.isDirectory)`). Việc còn lại chỉ là build sạch từ main hiện tại và khóa
lại bằng manifest — không phải sửa code.

**Tin tốt 2 — kênh biên nhận hợp nhất đã thông (18/08).**
`/logs/{node}?container=user` trả đúng log `viva.asr` (F1). App đã gửi `X-Trace-Id`,
server đã log đúng UUID. Vòng ghi→đọc lại KUKSA qua REST đã chứng minh 07/08.
Ablation tốc độ qua `vcu/Speed` khả thi bằng REST (F5). **Không cần viết code mới
cho phần bằng chứng.**

**Điều phải nói thẳng — con đường VHAL write vẫn bị chặn kép.**
(a) M1a: chưa root được, chưa cài priv-app → `real` flavor chưa ghi được property.
(b) F2: kể cả qua M1a, pin `vhal` của blueprint **không bắc cầu** hai propId HVAC
chuẩn; chỉ `door_lock` khớp trọn. Nghĩa là "VHAL readback" ở mức đầy đủ là mục tiêu
**Phase 2**, không phải điều kiện của phiên đầu tiên. BGK đã chừa lối: *"VHAL/CAN
**hoặc** Media consumer"* — phiên đầu dùng Media consumer + speed ablation.

---

## PHẦN 3 — PLAN BA PHASE

### PHASE 0 — Khóa danh tính (không cần Device, làm trước, ~1 buổi)

| # | Việc | Trả lời khoản trừ |
|---|---|---|
| P0.1 | Build từ main hiện tại: `lintMockDebug lintRealDebug test assembleMockDebug assembleRealDebug` trên clone sạch (mô phỏng CI/fresh clone, xác nhận fix `5d96156` đủ) | ② artifact identity −1, ② test −2 (phần "HEAD không build") |
| P0.2 | Viết **MANIFEST hợp nhất** một file: commit → SHA-256 hai APK → digest image ASR đang chạy (`6ca09c24…`, không phải `63c2c56a…` — F6) → roomId/nodeId → đường dẫn video/log/test đi kèm. Mọi evidence từ nay trỏ về file này | ② artifact identity −1, ④ evidence −2 |
| P0.3 | **B4**: kiểm 3 property đọc trên Device (`speed` ✅ kỳ vọng, `fuel` ❌ không mapping, `battery` ❌ lệch số hiệu — F4). Quyết định kịch bản phiên vàng chỉ dùng câu đã kiểm | ① tính đúng −2 (tránh demo vỡ) |
| P0.4 | Kiểm app có gọi `getProperty` lúc subscribe-init không (contract "changes only" của gateway — F5). Nếu không → sửa nhỏ trước phiên | tiền đề của speed ablation |
| P0.5 | A5 + C2: sửa `03-contracts.md` §2 (PA-1 = `10.99.0.3`) và `carsky-api.md` §5 (`container=user`, Loki rỗng) | ② kiến trúc −1 (phần "tài liệu drift") |

### PHASE 1 — PHIÊN VÀNG trên Device CarSky (không chờ M1a, flavor `mock` + engine VIVA)

Một phiên duy nhất, chuẩn bị như một buổi ghi hình. Mọi block cùng một APK
(SHA-256 khớp P0.2), cùng Device `VIVA`, cùng ngày giờ, KHÔNG restart node giữa chừng.

```
SETUP  Ghi manifest identity đầu phiên. REST đặt vcu/Speed = 0.

BLOCK A — Lặp lại (① ổn định −2)
  22 câu của suites/benchmark_v1.csv qua MIC THẬT, theo đúng thứ tự,
  người đọc theo kịch bản in sẵn = ASR ground truth có trước (① tính đúng −2).
  Thêm: 3 lần liên tiếp cùng một câu media_play/pause/next.

BLOCK B — Biên trên Device (① kịch bản biên −1)
  unsupported (G3) · range/confirm (G2, xe đang đứng yên) ·
  rồi REST đặt vcu/Speed = 60 → cùng câu "mở cửa" → Deny:G1_SPEED_LOCK.
  Hai lượt cuối = SAME-INPUT COUNTERFACTUAL trên Device (③ outcome −2, ④ độ sâu −4):
  cùng câu, cùng APK, verdict đổi CHỈ VÌ trạng thái nền tảng đổi.

THU HOẠCH — làm NGAY, trước mọi block phá hoại
  1. adb logcat -d -s VIVA_TRACE:I VIVA_VOICE:I  → ĐỦ MỐC speech_start/asr_sent/asr_done
  2. curl /logs/<asr-node>?container=user&tail=2000  → dòng VIVA_ASR|<uuid>|ok
  3. dumpsys media_session  → PLAYING/PAUSED/active-item (Media consumer readback)
  4. REST POST /signals/.../values  → giá trị + timestamp (speed, và media nếu có path)
  ⚠️ Thứ tự bắt buộc: log node chết theo pod (F1) — thu log TRƯỚC khi đụng node.

BLOCK C — Phục hồi lỗi (① ổn định −2, ② observability −1) — SAU thu hoạch
  POST /deployments/{room}/restart/<asr-node>  → nói trong lúc node down
  → app phải fallback (Google engine / thông báo lỗi, không crash)
  → node Running lại → nói tiếp → phục hồi. Ghi cả chuỗi vào logcat.

ĐÓNG GÓI
  Bảng expected-vs-observed: câu kịch bản | transcript ASR | intent | verdict | effect
  quan sát | readback. Mỗi dòng một UUID xuất hiện ở logcat + log node.
```

Phiên này trả lời: ① −3 (một phần: mic→ASR node→NLU→guard→**Media** readback khép
kín; vế VHAL ghi nhãn Planned), ① −2 ổn định, ① −2 tính đúng (transcript + intent +
verdict + media effect; VHAL effect chưa), ① −1 biên, ② −1 observability, ② −1
contract (App→CarSky ASR khép; PropertyID→VHAL chưa), ③ −2 outcome, ④ −3 align,
④ −4 độ sâu (qua counterfactual tốc độ), ④ −2 evidence.

> 🔴 **CẬP NHẬT 19/08 — BLOCK B BỊ CHẶN, ĐỌC `evidence/c2/vhal-local-fake-server-blocker-0819.txt` TRƯỚC.**
> Image AAOS bật `ro.vendor.vehiclehal.server.use_local_fake_server=true`: VHAL client
> trong guest nối vào **fake server nội bộ**, không bao giờ nối tới IVI Gateway. Chuỗi
> nền tảng GPIO→CAN→KUKSA→gateway-push đã chứng minh chạy (có log + readback 3 tầng),
> nhưng app đọc property vẫn `0.0`. ⇒ Counterfactual tốc độ **không diễn được** cho tới
> khi hạ tầng đổi image. Phiên vàng vẫn chạy được với **Media consumer readback**
> (BGK cho phép "VHAL/CAN **hoặc** Media consumer").
>
> Cũng phát hiện: sau pod restart 12/08 room đã **âm thầm hỏng** — hai node script mất
> subscription (phải restart node), và guest mất IPv4 trên `eth1` nên không gọi được
> ASR node. Cả hai đã khôi phục bằng tay 19/08; **phải kiểm lại trước mỗi phiên**.

**Điều kiện tiên quyết — ĐÃ KIỂM XONG 18/08:**
- `mock` flavor là mock thuần (kể cả đọc): tốc độ = sóng sin trong
  `MockVehicleRepository.kt:143`, không chạm VHAL. ⇒ Block B **bắt buộc** chạy trên
  `real` flavor.
- `CAR_SPEED` trên image FAuto Trout = **`prot=dangerous`** — xác nhận bằng
  `dumpsys package permission` trên chính Device qua web ADB shell
  (`evidence/c2/car-speed-permission-probe-0818.txt`). ⇒ cài `app-real-debug.apk`
  thường + `pm grant com.sopa.viva_automotive android.car.permission.CAR_SPEED`
  là đọc được speed, **không cần M1a**. Quyền GHI (climate/doors) vẫn
  `signature|privileged` → M1a giữ nguyên vai trò ở Phase 2.
- Bonus: shell của device có sẵn CAR_SPEED (SYSTEM_FIXED) → trong phiên vàng có
  thể đối chiếu bằng nguồn thứ ba: `cmd car_service get-property-value 0x11600207 0`
  (chưa chạy thử lệnh này).
- Hệ quả kịch bản: phiên vàng dùng **`real` flavor cài thường** cho Block B
  (verdict không cần write thành công — G1/G2 chỉ cần ĐỌC speed); các block
  media/ASR chạy được trên cả hai flavor, ưu tiên cùng một APK `real` để giữ
  một identity duy nhất.
- Việc kiểm động còn lại trước phiên: cài `real` + grant + xem Diagnostics hiển
  thị km/h đổi theo `POST /signals/.../drive-controls/actuate vcu/Speed`.

### PHASE 2 — Đóng vế VHAL write (cần M1a, hoặc chấp nhận giới hạn)

| # | Việc | Ghi chú |
|---|---|---|
| P2.1 | M1a: `adb root` / remount qua tunnel, cài `real` flavor làm priv-app | vẫn là nút chặn cũ |
| P2.2 | **B1**: `door_lock` ghi → REST đọc lại `...IsLocked` (intent duy nhất khớp trọn 3 tầng — F2) | trả lời ① −3 trọn, ② contract −1 trọn, ③ khác biệt −1 |
| P2.3 | **B3**: HVAC qua vendor propId `0x21600409`/`0x21400400` area 0 (F3) — hoặc PATCH pin thêm propId chuẩn | ⚠️ chưa xác minh quyền ghi vendor prop cần permission gì trên image này |
| P2.4 | Chạy lại PHIÊN VÀNG bản rút gọn trên `real` flavor: 5 câu + door_lock + readback | biên nhận cuối cùng, đủ cả VHAL |

### PHASE 3 — Hai việc BGK khuyến nghị, ngoài phiên chạy

| # | Việc | Trả lời |
|---|---|---|
| P3.1 | **Decision table local vs cloud**: bảng điều kiện fallback theo confidence / capability / latency / privacy / network — BGK nêu đích danh trong khuyến nghị. Block C của phiên vàng chính là dữ liệu thật đầu tiên cho bảng này | khuyến nghị #3, ② failure handling |
| P3.2 | Benchmark có thẩm quyền: cùng corpus (`benchmark_v1.csv`) chạy trên **cả** đường Vosk-cũ/Google (baseline) và viva-asr trên CarSky, cùng người nói, cùng nhiễu — thay cho cặp số 65% vs 28.75% bị đánh "chưa đủ thẩm quyền" | ③ lợi ích −3 (phần đo được) |

---

## PHẦN 4 — RỦI RO VÀ LUẬT CHƠI CỦA PHIÊN VÀNG

1. **Log node chết theo pod, Loki rỗng (F1).** Mọi thao tác restart chỉ được làm
   SAU khi đã kéo log. Block C đứng cuối là vì vậy — đây là ràng buộc cứng nhất.
2. **Restart node dùng quota/không?** Route `POST /deployments/{room}/restart/{node}`
   có trong openapi nhưng **chưa từng gọi** — thử trên node ASR ngoài giờ demo trước,
   xác nhận nó không kéo cả room xuống.
3. **Không nới nhãn.** Phiên vàng flavor `mock` thì vế VHAL vẫn ghi **Planned/Mô
   phỏng** trong bảng ba nhãn. Ô "Minh bạch" 2/2 và "Ranh giới" 2/2 là hai ô đội
   đang đầy — đổi chúng lấy điểm ảo là lỗ kép.
4. **p95 nói đúng số đo.** Lần trước 1664 ms > ngân sách 1500 ms. Nếu phiên mới
   vẫn vượt, ghi đúng và giải thích chặng — BGK đã thưởng điểm minh bạch, đừng phá.
5. **Một người vận hành, một người đọc kịch bản.** 25 lượt phiên 10/08 có 10 lượt
   trượt ASR do biến thể câu — phiên vàng dùng đúng câu đã có trong grammar/corpus,
   biến thể khó để riêng một block "known-gap" có chủ đích.

---

## PHẦN 5 — VIỆC BẮT ĐẦU NGAY (thứ tự trong tuần)

| Ngày | Việc |
|---|---|
| 1 | P0.1 build sạch + P0.2 manifest hợp nhất |
| 1 | P0.3 kiểm 3 property đọc + P0.4 kiểm getProperty init (một buổi ADB qua tunnel) |
| 2 | P0.5 sửa 2 tài liệu · viết kịch bản phiên vàng in được (bảng expected-vs-observed trống sẵn) |
| 2 | Thử `restart/{node}` trên node ASR (rủi ro #2) — ngoài giờ |
| 3 | **PHIÊN VÀNG** (Phase 1) — cần người đọc kịch bản (máy Long có giọng thật) |
| 4+ | Phase 2 nếu M1a tiến triển; song song P3.1 decision table (việc giấy, không chặn) |
