# BỘ Q&A — 10 PHÚT HỎI ĐÁP VỚI BAN GIÁM KHẢO

> **Điểm:** 10đ — `+05 câu trả lời rõ ràng, logic, thể hiện hiểu biết sâu sắc` ·
> `+05 cung cấp thông tin chính xác và đưa ra dẫn chứng cụ thể`
>
> **Nguồn dự đoán câu hỏi:** `Phan_hoi_Vong2_viva.docx` (BTC khoá 17/08) — BGK đã đọc bài nộp và viết ra
> đúng chỗ chưa hài lòng, nên khả năng cao họ hỏi lại chính chỗ đó · phản hồi mentor 10/08 và 15/08.

---

## Cách trả lời — dùng cho MỌI câu

**Ba nhịp, không hơn:**

1. **Trả lời thẳng** câu hỏi trong một câu. Có hoặc chưa. Không vòng.
2. **Một bằng chứng có locator** — tên file, số đo, hoặc thứ vừa diễn trên sân khấu.
3. **Một giới hạn tự nêu**, nếu có.

Nhịp 2 ăn ô *"dẫn chứng cụ thể"*. Nhịp 3 là thứ Vòng 2 đã ăn trần 5/5 — giữ nguyên phong cách đó.

**Ba điều cấm:**
- Cấm nói "về cơ bản là có" khi câu trả lời là chưa.
- Cấm bịa số. Không nhớ thì nói *"con số chính xác nằm trong `<file>`, em không nhớ chính xác nên
  không muốn nói sai."* — câu này **ăn điểm**, không mất điểm.
- Cấm để một người nói hết. Câu VHAL là của Tùng, nền tảng là của Vĩ, app/media là của Dương.

---

## NHÓM A — chắc chắn bị hỏi

### A1. "Car framework, VHAL và CCU các em đã phát triển được chưa?"

*Mentor hỏi nguyên văn ở Vòng 2. BTC chấm ô này 0/4 và 6/15.*

> **Chưa, và em nói rõ ba phần.**
>
> Một — service framework riêng với AIDL thì chưa xây; lớp vehicle của chúng em hiện là module thư viện
> trong app, không phải system service.
> Hai — CCU điều hoà đang chạy là `climate_ecu.lua` của nền tảng, không phải của đội. Chúng em đối chiếu
> từng byte và ghi trong `VHAL_BASELINE_MANIFEST.md`.
> Ba — phần chúng em **có** làm ở tầng đó là sửa hai gateway Lua: `IVI_GATEWAY.lua` và `BCM_GATEWAY.lua`,
> thêm khối chặn an toàn ngay trên đường `pins.vhal → actuate_kuksa`, và sửa một property ID sai của
> bản gốc — `EV_BATTERY_LEVEL` từ `0x11600204` thành `0x11600600` theo AOSP.
>
> Chúng em không claim luồng `App → VHAL → CAN → CCU` đã hoàn tất.

**Locator:** `VHAL_BASELINE_MANIFEST.md` §5 · `GATEWAY/IVI_GATEWAY.lua` · commit `c255ccc`

⚠️ Nếu **G-B pass**, thêm: *"Riêng phần `setProperty` và đọc ngược giá trị từ property trên Device
CarSky thì tuần này chúng em đóng được — các thầy cô vừa thấy ở khoảnh khắc đầu tiên."*

---

### A2. "Bỏ CarSky đi thì sản phẩm có còn chạy không?"

*BTC viết nguyên văn câu này khi cho 0/4 ô "Độ sâu trong core flow".*

> **Ở bài nộp Vòng 2 thì câu trả lời là có, và BGK chấm đúng.** Lúc đó app và node ASR là hai lát cắt rời.
>
> Hôm nay thì không. Nhận dạng tiếng Việt chạy trong container `viva-asr` trên một node CarSky, và bản
> demo hôm nay build với remote ASR là đường duy nhất — không có fallback on-device. Các thầy cô vừa
> thấy: em dừng node, lệnh chết; bật lại, lệnh chạy.

**Locator:** khoảnh khắc ② trên sân khấu · `evidence/c2/carsky-voice-e2e-20260810/`

> 💡 Đây là câu quan trọng nhất trong cả bộ. Trả lời bằng cách **thừa nhận nhận xét cũ là đúng** rồi chỉ
> vào thứ vừa diễn. Đừng tranh luận điểm Vòng 2.

---

### A3. "Con số 65% so với 28,75% ở đâu ra?"

*BTC ghi: "chưa đủ thẩm quyền để xác nhận mức cải thiện".*

> **Con số đó chúng em rút khỏi bài, và không thay bằng số khác.**
>
> Nó sinh từ một phiên thử tay, không phải bộ benchmark có kịch bản, nên không đủ thẩm quyền để so sánh.
> BGK nhận xét đúng.
>
> Con số chúng em **dám** đứng sau là độ trễ đầu-cuối trên Device thật: trung vị 1336 mili-giây trên
> 25 lượt. Và cả p95 là 1664 — **chưa đạt** ngân sách 1500 mà chúng em tự đặt.

**Locator:** `evidence/c2/carsky-voice-e2e-20260810/README.md`

---

### A4. "NLU của các em chỉ chứng minh trên tập câu nhỏ. Câu ngoài tập thì sao?"

*BTC ghi trong mục "Điểm còn giới hạn": chưa xử lý rõ capability overflow.*

> **Đúng, và bản demo hiện tại còn cố ý hẹp hơn code thử nghiệm.**
>
> Active runtime đang bind luật ngữ pháp tất định cho tập intent lõi. Repo có code keyword và embedding,
> nhưng chúng không nằm trên active path của `VoiceAgent`, nên chúng em không dùng chúng để claim độ phủ.
> Câu ngoài phạm vi được **từ chối và nói ra**, chứ không đoán bừa — vì với trợ lý điều khiển xe, thực
> thi sai nguy hiểm hơn không thực thi.
>
> Khi nào chuyển lên cloud thì chúng em có bảng quyết định theo confidence, capability, độ trễ, quyền
> riêng tư và tình trạng mạng.

**Locator:** `vong3/04-DECISION-TABLE-LOCAL-CLOUD.md` · `VoiceModule.kt` · `GrammarIntentRouter.kt`

---

### A5. "Khi nào chạy local, khi nào lên cloud?"

*BTC khuyến nghị chuẩn bị đúng bảng này.*

Mở [`04-DECISION-TABLE-LOCAL-CLOUD.md`](04-DECISION-TABLE-LOCAL-CLOUD.md) và nói 5 trục: **confidence ·
capability · latency · privacy · network**. Không đọc bảng — nói hai ví dụ:

> "Lệnh điều khiển xe thì luôn được định tuyến và thực thi local sau khi có transcript. Confidence âm
> học thấp thì hỏi lại, không gọi cloud. Chỉ câu hội thoại tự do ngoài capability của luật mới là ứng viên
> cloud, và chỉ khi mạng đủ tốt. Mất mạng thì hệ thống suy biến về tập lệnh lõi chứ không chết."

⚠️ Nói chính xác: **định tuyến intent và thực thi lệnh xe** chạy local sau khi có transcript. ASR của
build hiện tại là viva-asr HTTP hoặc Google theo Settings; không claim toàn bộ audio pipeline offline.

---

### A6. "VinFast đã có ViVi rồi, VIVA khác gì? Tên gọi có phải bắt chước không?"

> **Chúng em không cạnh tranh với ViVi ở quy mô dữ liệu hay độ rộng hội thoại.** Nguồn chính thức của
> VinBigdata công bố ViVi 2.0 có hơn 30.000 giờ dữ liệu thoại và tích hợp GenAI — đó là quy mô một công
> ty AI mà đội bốn người không nên giả vờ so hơn.
>
> VIVA khác ở phạm vi sản phẩm: đây là module tích hợp cho cockpit AAOS của OEM/Tier-1, với hợp đồng
> hành động có kiểu và một chốt SafetyGuard ở biên thực thi. Trọng tâm chứng minh không phải “hiểu nhiều
> câu hơn”, mà là lệnh nào được phép xuống xe và có trace để kiểm chứng.
>
> Tên sản phẩm là **VIVA**. Wake phrase “Vi-Vi ơi” được giữ vì artifact và tập test hiện tại đã dùng nó;
> grammar cũng nhận “Viva ơi”. Đây là trùng âm với tên cũ của trợ lý VinFast, không phải claim kế thừa
> hay liên kết thương hiệu. Sau chung kết, OEM có thể thay wake phrase/sound model mà không đổi Brain
> hoặc Body.

**Locator:** [`docs/architecture/VIVA-VOICE-BRAIN-BODY.md`](../docs/architecture/VIVA-VOICE-BRAIN-BODY.md)
§6 · [ViVi 2.0 — VinBigdata](https://vivi.vinbigdata.com/)

---

## NHÓM B — khả năng cao

### B1. "Ablation của các em chạy trên mock và JVM. Sao chứng minh được trên xe thật?"

> **Đúng, phản chứng A1 chạy ở tầng JVM với `MockVehicleRepository`.** Em không nói nó là bằng chứng
> trên xe.
>
> Nó chứng minh một điều hẹp nhưng chắc: bỏ tầng an toàn thì cả 6 lệnh nguy hiểm trong bộ 9 ca đều ghi
> được xuống repository, 3 ca đối chứng hợp lệ không đổi. Tái lập được bằng một lệnh Gradle.
>
> Trên Device thật thì thứ chúng em có là luật G1 chặn mở cửa khi xe chạy — các thầy cô vừa thấy ở
> khoảnh khắc ba, và nó có trong log phiên 10/08.

**Locator:** `evidence/ablation/a1-safety-guard-ablation.csv` ·
`./gradlew :vehicle-service:impl:testDebugUnitTest --tests "*SafetyGuardAblationTest*"`

---

### B2. "BGK ghi source các em nộp không build được. Giải thích?"

*BTC chấm Artifact identity 1/2 vì lý do này.*

> **Đúng, và đã sửa.** Nguyên nhân là `settings.gradle.kts` include hai module `phone-companion` không
> có trong bản snapshot nộp. Chúng em sửa ở commit `5d96156` bằng cách cho hai module đó thành tuỳ chọn.
>
> Hiện tại `./gradlew test` chạy sạch: **291 test, 0 fail, 0 error, 0 skip.**

**Locator:** commit `5d96156` · `automotive/settings.gradle.kts`

> ⚠️ Chạy lại lệnh này sáng 22/08 và cập nhật con số nếu đổi. **Không đọc số cũ.**

---

### B3. "SafetyGuard có phải functional safety không?"

*BTC nêu sẵn trong mục lưu ý — gần như chắc chắn BGK hỏi.*

> **Không, và em muốn nói rõ để không gây hiểu nhầm.**
>
> SafetyGuard là guardrail ở tầng ứng dụng: nó ngăn câu lệnh không hợp lệ đi xuống xe. Nó **không thay
> thế** cơ chế functional safety theo ISO 26262 ở safety MCU hay ECU. Hai lớp có vai trò khác nhau và
> tồn tại song song.
>
> Điểm kỹ thuật đáng nói là chúng em cưỡng chế nó ở **biên repository**, không ở UI — nên nó chặn cả
> lệnh chạm tay trên màn hình, không chỉ lệnh giọng nói. Đó là ca A1-02 trong bộ phản chứng.

**Locator:** `GuardedVehicleRepository.kt`

---

### B4. "Đâu là phần các em làm, đâu là của nền tảng?"

*Vòng 2 ô này đã đạt trần 5/5 — trả lời tự tin.*

> Nền tảng cấp: Device AAOS 14, tám Script Node Lua, KUKSA Data Broker với 1.268 tín hiệu VSS, hai bus
> CAN, DBC, và ECU mô phỏng.
>
> Đội làm: toàn bộ voice pipeline tiếng Việt, ánh xạ intent có kiểu sang `(propertyId, areaId, value)`,
> SafetyGuard, `GuardedVehicleRepository`, container ASR, các adapter media và toàn bộ đo đạc.
>
> Phần **sửa** của nền tảng thì chúng em ghi riêng: hai file gateway Lua, có diff.

**Locator:** `VHAL_BASELINE_MANIFEST.md`

---

### B5. "Độ trễ 1,3 giây có chấp nhận được trên xe không?"

> **Với lệnh cabin thì chấp nhận được, với lệnh an toàn thì không phải câu hỏi về độ trễ.**
>
> Ngân sách chúng em tự đặt là p95 dưới 1500 mili-giây; đo thật được 1664, tức chưa đạt, và chúng em
> chưa phân tách theo chặng nên chưa biết chỗ nào tốn nhất. Đó là việc tiếp theo.
>
> Lệnh an toàn như mở cửa thì điều quan trọng không phải nhanh mà là **fail-closed**: thiếu tín hiệu
> tốc độ thì từ chối, không đoán.

---

## NHÓM C — cơ hội ghi điểm, chuẩn bị sẵn để chủ động kể

### C1. "Các em dùng AI thế nào trong quá trình phát triển?"

*Ô `+05 câu chuyện AI ấn tượng` nằm trong khối Demo 40đ. Nếu BGK không hỏi, **chèn vào phần chốt**.*

> Kể **một câu chuyện cụ thể**, không kể danh sách công cụ. Gợi ý trục: dùng AI để dựng bộ phản chứng
> ablation và soát lại chính tài liệu của đội, phát hiện ra `VHAL_BASELINE_MANIFEST.md` lập ngày 08/08
> ghi cả 8 script là `provided` kèm câu "đội không sửa một dòng nào" — trong khi đến 09/08 đội đã sửa
> hai file. **Tài liệu của đội đang tự làm mờ đóng góp của chính đội**, và đó là lý do mất điểm ở Vòng 2.

**Locator:** `vong2/31-BO-SUNG-EVIDENCE-3-TIEU-CHI.md` §③.2a

---

### C2. "Sản phẩm kết hợp mấy domain?"

*Ô `+05 tích hợp đa dạng bài tập` — kết hợp nguyên liệu từ ≥2 domain.*

> **Hai.** Digital Cockpit — voice, HMI, media, trạng thái xe. Và Vehicle Middleware — VHAL/
> CarPropertyManager, KUKSA/VSS, CAN, hai gateway Lua chúng em sửa.
>
> Chúng không phải hai thứ ghép cạnh nhau: một câu nói tiếng Việt đi xuyên cả hai, và các thầy cô vừa
> thấy nó trong một lượt chạy duy nhất.

⚠️ Nếu **G-B fail**, hạ xuống: *"…đi xuyên cả hai tới lớp adapter; phần đọc ngược từ property thì
chúng em chưa đóng được."*

---

### C3. "Sao không dùng Google Assistant hay trợ lý có sẵn?"

> Ba lý do, theo thứ tự quan trọng.
>
> Một — trợ lý có sẵn không nhận lệnh xe. Chúng cần một tầng ánh xạ sang property của xe và một tầng
> chính sách, và đó chính là phần chúng em làm.
> Hai — TTS của nền tảng CarSky chỉ có `zh-TW` và `en-US`, không có tiếng Việt. Chúng em phải tự lo.
> Ba — lệnh điều khiển xe không nên rời khỏi xe. Đó là lý do đường mặc định là local.

> Kiến trúc ba khối giúp thay ASR hoặc thêm GenAI ở Voice/Brain mà không thay hợp đồng Body. Dù câu trả
> lời được sinh bằng luật hay LLM, nó không có quyền gọi thẳng VHAL; Body vẫn kiểm tra action allowlist
> và SafetyGuard.

---

### C4. "Bước tiếp theo là gì?"

*Vòng 2 ô này đạt trần 2/2 — trả lời chắc.*

> Pilot với một đội xe vận tải, đo bốn chỉ số: tỉ lệ hoàn thành lệnh, độ trễ, số lần chạm màn hình, và
> tỉ lệ thực thi sai.
>
> Rào cản lớn nhất không phải kỹ thuật mà là **quyền**: các permission `CONTROL_CAR_*` là privileged,
> nên phải có OEM ký ứng dụng và cấp allowlist. Đó là lý do offering của chúng em là module cho
> OEM/Tier-1 chứ không phải app bán thẳng cho tài xế.

---

## Phân vai trả lời

| Chủ đề | Người trả lời |
|---|---|
| Chiến lược, khách hàng, hiệu quả, roadmap, AI story | **Long** |
| VHAL, property, gateway Lua, CAN, an toàn | **Tùng** |
| Nền tảng CarSky, node, container, evidence, đo đạc | **Vĩ** |
| App, media, HMI, TTS, Android | **Dương** |

Long điều phối: nghe câu hỏi → *"Phần này em mời Tùng ạ"* → Tùng trả lời → Long chốt một câu.

> 💡 Chuyển câu hỏi cho đúng người **ăn điểm** ô *"hiểu biết sâu sắc"* — nó cho thấy đội có chuyên môn
> phân bố thật, không phải một người biết tất cả.

---

## Checklist trước Q&A

- [ ] Cả 4 người đọc hết file này, mỗi người thuộc phần của mình
- [ ] Chạy lại `./gradlew test` sáng 22/08, cập nhật con số ở B2
- [ ] Mở sẵn `VHAL_BASELINE_MANIFEST.md` và `a1-safety-guard-ablation.csv` ở tab riêng
- [ ] Tập hỏi chéo 20 phút tối 22/08 — Vĩ và Dương đóng vai BGK hỏi khó
- [ ] Thống nhất: câu nào **chưa** thì trả lời **chưa**
