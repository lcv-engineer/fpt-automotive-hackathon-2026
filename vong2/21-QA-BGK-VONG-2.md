# Q&A Vòng 2 — trả lời ngắn, đúng evidence

Tài liệu này dùng cho N7 live rehearsal. Mỗi câu trả lời có ba phần: **trả lời trực tiếp**, **evidence để mở** và
**ranh giới không được vượt**. Snapshot 09/08 cho phép claim source/build/emulator đúng evidence; mọi câu “trên
CarSky/VHAL thật” vẫn chỉ được mở khi Claim–Evidence Map có capture đúng Device và đúng artifact.

## 1. “AI nằm ở đâu nếu core dùng grammar?”

**Trả lời:** AI trên đường chạy APK gồm Silero VAD xác định biên tiếng nói và Vosk on-device chuyển cùng dòng PCM
thành text; grammar là policy có chủ đích cho tập lệnh xe hữu hạn. `AndroidPcmSource` là nơi duy nhất mở
`AudioRecord`, sau đó cùng frame đi qua VAD rồi Vosk. Chúng tôi không dùng LLM để tạo cảm giác “AI hơn” khi nó làm
execution khó kiểm soát hơn.

**Evidence:** `VoiceAssistantService.runInteraction`, `VadStreamDriverTest`, `GrammarIntentRouterTest`.

**Không nói:** “LLM đang chạy trong core flow” hoặc “AI tự quyết định actuator”.

## 2. “Vì sao chọn grammar thay vì LLM function-calling?”

**Trả lời:** Lệnh xe cần latency thấp, offline, schema ổn định và behavior tái lập. Grammar phù hợp với 10 intent lõi;
LLM chỉ đáng dùng cho câu tự do và nếu bật thì vẫn chỉ được đề xuất một intent trong whitelist trước policy/gateway.

**Evidence:** `android/voice/README.md`, `vong2/16-QUYET-DINH-DUONG-NLU.md`.

**Trade-off:** Grammar không bao phủ mọi cách diễn đạt; extension rule và vế ASR benchmark giải quyết theo dữ liệu,
không bằng cách mở quyền thực thi cho mô hình.

## 3. “Phần nào thật sự do đội tự xây?”

**Trả lời:** Voice orchestration, một nguồn PCM, Silero VAD integration, ASR boundary, grammar router 10 intent,
latency trace/verdict, TTS fallback 36 câu, audio focus, SafetyGuard ở biên repository và media-session adapter là
team-owned. Capture/VAD/Vosk đã nằm trên đường chạy APK; `AsrClient` container vẫn chỉ ở mức module/test. AAOS,
Car APIs, `AudioRecord`, Android TTS và các thư viện model là baseline/platform.

**Evidence:** `vong2/20-WRITE-UP-AI-VONG-2.md` §6, source và E12.

**Không nói:** Thư viện hoặc starter capability là code đội tự phát minh.

## 4. “Core flow đã chạy trên CarSky chưa?”

**Trả lời hiện tại:** Chưa có evidence đủ để nói câu đó. Chúng tôi có source/JVM và hai APK build xanh; claim platform
chỉ mở khi đúng artifact chạy trên Device và có trace/output từ CarSky.

**Evidence:** `vong2/18-CLAIM-EVIDENCE-MAP.md`, E12; khi có thì mở E09/E11.

**Không nói:** “Build được APK” đồng nghĩa “đã chạy trên CarSky”.

## 5. “p95 có dưới 1,5 giây không?”

**Trả lời hiện tại:** Chưa công bố. Budget là mục tiêu thiết kế, không phải số đo; p50/p95 chỉ được báo sau khi cùng
dataset, warm-up, failure policy và artifact identity đã khóa.

**Evidence:** `vong2/15-QUYET-DINH-BENCHMARK-ASR.md`; chờ E04.

**Không nói:** Dùng dự toán hoặc một lượt chạy đẹp để trả lời p95.

## 6. “Vì sao đổi edge-only vs hybrid thành Vosk vs container?”

**Trả lời:** Spike cho thấy cloud LLM tạo network dependency trái mục tiêu offline và làm trục so sánh thiếu thực chất.
Chúng tôi đổi sang hai deployment ASR có trong kiến trúc — Vosk on-device và `viva-asr` container — để quyết định
đường mặc định bằng WER/intent accuracy và latency trên cùng dữ liệu.

**Evidence:** `vong2/15-QUYET-DINH-BENCHMARK-ASR.md`.

**Trade-off:** Container có thể dùng model mạnh hơn nhưng thêm network/deployment dependency; on-device giữ privacy và
offline nhưng bị giới hạn tài nguyên.

## 7. “SafetyGuard đã chặn mở cửa khi xe chạy chưa?”

**Trả lời hiện tại:** Có, ở mức mô phỏng/emulator: `DefaultSafetyGuard` được cưỡng chế trong
`GuardedVehicleRepository`, trả `Deny:G1_SPEED_LOCK` trước setter; A1 cho thấy bỏ guard thì 6/9 lệnh nguy hiểm ghi
được xuống repository, gồm cả đường chạm HMI. Chưa được nói đã chặn trên VHAL CarSky vì chưa có trace Device.

**Evidence:** `evidence/ablation/a1-run-manifest.txt`, emulator B10; chờ E06 Device.

**Không nói:** “An toàn đã chạy trên CarSky/VHAL thật” từ unit test, emulator hoặc ablation repository.

## 8. “Có phải cả 10 intent đều đi xuống CAN?”

**Trả lời:** Không. Chỉ HVAC và door thuộc đường Vehicle Property; media đi qua
`MediaBrowserCompat`/`MediaControllerCompat` tới MediaSession/ExoPlayer, volume qua audio adapter, delivery là in-app
skill. Ba media intent đã chạy qua NLU → MediaSession trên Device CarSky bằng mock/debug text-injection: play/pause đổi trạng thái và next đổi active item. Test này bỏ qua mic/VAD/ASR; TTS/audio-focus vẫn cần capture riêng.

**Evidence:** `vong2/03-contracts.md` §0.2/§5, README kiến trúc.

**Không nói:** “Voice → VHAL → CAN” như một đường chung cho mọi intent.

## 9. “Mock và real khác nhau thế nào?”

**Trả lời:** Mock repository cho test tái lập và demo fallback; real flavor dùng Car APIs/vehicle-service boundary và
cần quyền privileged/platform signing phù hợp. Chúng tôi giữ cùng contract để đổi implementation mà không đổi core.

**Evidence:** `automotive/README.md`, hai APK build trong E12.

**Không nói:** Mock state change là evidence VHAL/CAN hoặc Device.

## 10. “Hệ thống xử lý câu mơ hồ và ngoài phạm vi ra sao?”

**Trả lời:** Không đoán slot. “Quạt mạnh lên” hỏi mức 0–5; “đặt bàn ăn tối” bị từ chối lịch sự và không gọi gateway.
Hai behavior đã có unit test; ba tình huống cần state/service vẫn giữ nhãn Kế hoạch.

**Evidence:** `vong2/13-M7A-TINH-HUONG-PHUC-TAP.md`, test router/VoiceAgent.

## 11. “Làm sao biết TTS không nói thành công giả?”

**Trả lời:** Lượt chỉ được dùng câu xác nhận “Đã…” sau `CommandResult.Applied`. Timeout, deny, confirmation và lỗi có
verdict riêng; trace attribution giữ stage gây lỗi thay vì biến mọi failure thành success.

**Evidence:** `VoiceTurnReport.kt`, `TraceVerdict.kt`, golden trace và E12.

**Không nói:** Intent parse đúng đồng nghĩa actuator đã đổi trạng thái.

## 12. “Thêm intent mới có phải sửa core?”

**Trả lời:** Không sửa grammar core. Composition root đăng ký một `GrammarRule`, sau đó module sở hữu domain bổ sung
mapper/action, gateway/guard và test. Extension chạy sau core rule và safety pre-filter nên không thể ghi đè intent lõi.

**Evidence:** README “Thêm intent mà không sửa grammar core”, `GrammarRule` và test extension.

## 13. “Dữ liệu benchmark có phải dữ liệu thật?”

**Trả lời hiện tại:** Dataset/noise replay phải được gắn nhãn synthetic nếu được tạo hoặc trộn offline. Chúng tôi công
bố cách tạo, mức nhiễu, sample count và giới hạn; không gọi nó là cabin thật.

**Evidence:** `android/voice/fixtures/README.md`, `vong2/15-QUYET-DINH-BENCHMARK-ASR.md`.

## 14. “Vì sao bỏ DTC/UDS ở Vòng 2?”

**Trả lời:** Barem Vòng 2 không có điểm cộng cross-vertical riêng; mở DTC sẽ tiêu thời gian của đường găng trong khi
SafetyGuard, baseline manifest và ablation ăn thẳng vào tiêu chí team-owned. DTC/UDS được giữ cho Vòng 3, nơi
cross-vertical có dòng điểm riêng.

**Evidence:** `vong2/08-BAREM-VONG-2-CHINH-THUC.md`, write-up §10.

**Trade-off:** Ít bề ngang hơn, nhưng claim còn lại sâu hơn và có khả năng dựng evidence đúng hạn.

## 15. “Ai sẽ mua và giá trị nào cần kiểm chứng?”

**Trả lời:** User là tài xế; buyer/process owner có thể là fleet ops, OEM hoặc Tier-1. Offering là module voice tích hợp
cho cockpit AAOS. Giả thuyết cần kiểm chứng là giảm thao tác chạm và completion time, không phải doanh thu đã xác nhận.

**Evidence:** `vong2/12-PRODUCT-INTEGRATION-CARD.md`.

## 16. “Nếu demo lỗi giữa chừng thì sao?”

**Trả lời:** Sau 3 giây, người demo gọi đúng lỗi/stage, không lặp quá một lần và tiếp tục bằng trạng thái trước đó. Nếu
Device/VHAL lỗi, chuyển sang mock có nhãn; không giả vờ full-stack. Với media được phép nói “NLU → MediaSession đã đổi trạng thái trên Device CarSky bằng mock/debug text-injection”; không nói “giọng nói end-to-end” hoặc “TTS duck/release” khi chưa có capture mic/audio-focus.

**Evidence:** `vong2/14-KICH-BAN-DEMO-3-PHUT.md`, `vong2/19-TONG-DUYET-C2-10-PHUT.md`.

## Cách tập

- Một người hỏi ngẫu nhiên; người trả lời phải kết thúc phần chính trong 20–30 giây rồi mới mở evidence.
- Nếu câu hỏi chạm gate đỏ, câu đầu tiên phải là “Chưa có evidence để claim” trước khi giải thích kế hoạch.
- Không dùng từ “thật”, “end-to-end”, “trên CarSky”, “p95” hoặc “an toàn đã tích hợp” nếu evidence ID tương ứng chưa xanh.
- Sau mỗi buổi, cập nhật câu trả lời theo `18-CLAIM-EVIDENCE-MAP.md`; không sửa claim trực tiếp trong Q&A mà bỏ qua map.
