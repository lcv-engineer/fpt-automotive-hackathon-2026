# PRODUCT & INTEGRATION CARD — VIVA

> **Owner:** Long · **Task:** N2 · **Ngày:** 01/08/2026
> **Phạm vi:** VIVA — trợ lý giọng nói tiếng Việt chạy trên Android Automotive OS (AAOS).
> Card này trả lời đúng 5 hạng mục của barem Vòng 2; không phải business case và không claim trạng thái tích hợp cao hơn bằng chứng hiện có.

---

## 1. Người dùng và người quyết định

| Vai | Đối tượng | Nhu cầu / quyết định |
|---|---|---|
| **User trực tiếp** | Tài xế dùng cockpit, ưu tiên tài xế Việt Nam và tài xế giao vận | Điều khiển điều hòa, cửa và media bằng giọng nói, giảm thao tác chạm và không phụ thuộc mạng cho core flow |
| **Buyer / người quyết định sản phẩm** | OEM hoặc Tier-1 sở hữu roadmap Digital Cockpit | Quyết định tích hợp module vào image/ứng dụng AAOS, cấp quyền privileged VHAL, duyệt an toàn và chịu trách nhiệm phát hành |
| **Process owner của pilot giao vận** | Quản lý vận hành / an toàn đội xe | Chọn nhóm tài xế thử nghiệm, định nghĩa tình huống vận hành và đánh giá tác động đến quy trình giao hàng |

**Ranh giới:** tài xế là người sử dụng nhưng không phải người cấp quyền VHAL hay quyết định tích hợp vào xe. VIVA không giả định bán ứng dụng trực tiếp cho tài xế ở giai đoạn này.

## 2. Offering và quan hệ tiếp nhận

**Offering:** một gói phần mềm tích hợp AAOS gồm:

- VIVA Agent: một nguồn PCM, Silero VAD, Vosk tiếng Việt, intent router, TTS và HMI;
- `GuardedVehicleRepository`: boundary do đội sở hữu, cưỡng chế `SafetyGuard` cho cả voice lẫn thao tác HMI trước khi ghi property;
- media boundary chuẩn AAOS: voice Agent dùng `MediaBrowserCompat`/`MediaControllerCompat` điều khiển `VivaMediaBrowserService`/MediaSession;
- contract M2 và bộ test/trace để OEM/Tier-1 thêm intent mới mà không đưa intent xuống VHAL.

**Quan hệ tiếp nhận:** **B2B2C** — đội cung cấp module và integration kit cho OEM/Tier-1; OEM/Tier-1 tích hợp, platform-sign, kiểm thử và phát hành tới tài xế. Với pilot giao vận, fleet là process owner/co-design partner, không thay thế vai trò phê duyệt kỹ thuật của OEM/Tier-1.

## 3. Outcome và giả thuyết áp dụng

| Đối tượng | Outcome mong đợi | Giả thuyết cần kiểm chứng |
|---|---|---|
| Tài xế | Hoàn thành core command mà không chạm màn hình; phản hồi đúng trạng thái thực thi và vẫn dùng được khi mất mạng | **H1:** 5 lệnh xương sống đạt tỉ lệ hoàn thành ≥ 90% trong cabin/noise test của đội; **H2:** p95 end-to-end < 1.500 ms trên đường edge |
| OEM / Tier-1 | Có lớp voice-to-vehicle tách khỏi app UI và VHAL; thêm intent bằng contract thay vì sửa xuyên nhiều tầng | **H3:** một intent vehicle-control mới có thể được thêm bằng mapping + policy + test, không sửa VHAL và không để LLM sinh trực tiếp PropertyID |
| Fleet operations | Giảm thao tác tay/mắt trong các bước lặp lại và có policy chặn hành động không an toàn | **H4:** tài xế pilot hoàn thành kịch bản đại diện với ít thao tác chạm hơn baseline màn hình; process owner chấp nhận policy deny/confirm |

Các con số trên là **mục tiêu/giả thuyết**, chưa phải kết quả đo. Chỉ công bố kết quả sau khi có log/trace và protocol test tương ứng.

## 4. Tích hợp và phụ thuộc bên ngoài

### Quy ước trạng thái

- **THẬT:** dùng implementation thật trong source/core flow; nếu chưa có bằng chứng Device thì ghi rõ.
- **MÔ PHỎNG:** thay thế có chủ đích để phát triển hoặc demo; không được dùng làm bằng chứng tích hợp thật.
- **KẾ HOẠCH:** contract hoặc hướng triển khai đã chốt nhưng runtime integration chưa hoàn tất.

| Dependency / điểm nối | Trạng thái 09/08 | Bằng chứng hoặc giới hạn |
|---|---|---|
| VIVA Agent + `voice-core` trong app AAOS | **THẬT — source/build, chưa Device-verified** | Bridge `CoreIntentMapper` đã tích hợp; **258 test JVM** *(99 voice-core + 159 automotive)*, 0 fail/error/skip; lint và hai APK variant xanh bằng JDK 21, kiểm lại 09/08 |
| ASR on-device (Vosk EN/VI) + intent routing | **THẬT — source, chưa đo trên Device** | Model/task Gradle và pipeline tồn tại trong `automotive/feature/voice`; chưa claim accuracy/latency thực tế |
| `SafetyGuard` trước vehicle execution | **THẬT — source/emulator, chưa Device-verified** | `DefaultSafetyGuard` được cưỡng chế tại `GuardedVehicleRepository`; A1: bỏ guard làm 6/9 lệnh nguy hiểm ghi được xuống repository. Đầu kia vẫn là mock, không phải VHAL |
| `MockVehicleRepository` | **MÔ PHỎNG** | Dùng cho emulator/unit test; không chứng minh core flow chạy trên CarSky |
| `VivaCarService` riêng + AIDL | **KẾ HOẠCH (M1)** | Contract M2 đã chốt; Tùng/Vĩ triển khai service và quyền privileged |
| VHAL/`CarPropertyManager` trên CarSky | **KẾ HOẠCH — real flavor có source** | Cần platform signing/privapp allowlist và xác nhận `setProperty` trả `Applied` trên Device |
| VHAL ↔ KUKSA/VSS ↔ CAN qua Script Node | **KẾ HOẠCH — contract verified** | M2 đã đối chiếu PropertyID, VSS và DBC; chưa có runtime trace CarSky/CAN |
| CCU nhận/gửi CAN | **MÔ PHỎNG** | Mentor cho phép mô phỏng; phải giữ đúng nhãn trong demo/write-up |
| Voice → MediaBrowser → MediaSession/ExoPlayer | **THẬT — CarSky Device, từ NLU đến media (mock/debug)** | Bản mock đúng SHA-256 đã cài trên Device `VIVA` 09/08. Ba câu text-injection qua đúng `VoiceAssistantService`/NLU tạo `media_play`/`media_pause`/`media_next|Allow`; MediaSession đổi `PLAYING → PAUSED` và active item `0 → 1`. Không tính mic/VAD/ASR; TTS/audio-focus còn thiếu. `evidence/c2/carsky-runtime-20260809/` |
| `CarAudioManager` / volume | **KẾ HOẠCH kiểm chứng Device** | Volume không đi qua VHAL; emulator báo volume fixed, cần quyền privileged để điều khiển group volume thật |

**Không phụ thuộc cloud cho core flow.** Network chỉ là dependency của bước tải model/build ban đầu, không phải dependency khi tài xế ra lệnh.

## 5. Bước kiểm chứng tiếp theo và rào cản lớn nhất

**Rào cản lớn nhất:** quyền privileged VHAL và khả năng cài **real flavor/service framework** lên đúng Device CarSky. Mock APK đã cài và NLU → media đã chạy trên Device 09/08, nhưng điều đó không chứng minh property thật. Nếu không ghi được property thật, core flow không đủ bằng chứng platform L2.

**Validation gate kế tiếp — Device Integration Gate:**

1. ✅ Dùng JDK 21 build `mockDebug` và `realDebug`; **258 test JVM xanh** *(99 voice-core + 159 automotive)*, 0 failure/error/skipped; lint mock/real xanh (kiểm lại 09/08).
2. Cài bản `realDebug`/`VivaCarService` theo allowlist OEM trên Device CarSky.
3. Chạy 3 intent vehicle-control M2: đặt nhiệt độ 24°C, đặt fan mức 5, khóa cửa tài xế.
4. Với từng lệnh, chỉ tính thành công khi service trả `Applied`; lưu cùng `traceId`: intent → policy → PropertyID/area/value → VHAL callback → VSS/CAN evidence.
5. Đối chiếu `cmd car_service get-property-value`, app HVAC/DOOR và CAN/CCU mô phỏng; không gộp media/volume vào claim VHAL.

**Tiêu chí qua gate:** 3/3 lệnh đúng mapping, không có lệnh bị xác nhận “Đã…” trước `Applied`, log không crash, và có ít nhất một trace CarSky hoàn chỉnh cho mỗi intent. Nếu quyền VHAL thất bại, mở lại quyết định packaging/service với mentor thay vì thay bằng mock rồi khai là thật.

**Validation gate thứ hai — Voice Pipeline Gate:**

Gate trên trả lời *“lệnh có xuống được xe không”*. Gate này trả lời *“câu nói có lên đúng
intent trong nhiễu không”* — hiện chưa có dữ liệu nào trả lời:

1. ✅ Một micro, một dòng PCM qua Silero VAD rồi Vosk; container `viva-asr` vẫn chưa nhận cùng dòng PCM đó.
2. Bộ audio 5 người × 22 câu × 3 điều kiện, cộng 20–30 phút audio không có lệnh. Với
   Cuttlefish, đây là audio thu ngoài rồi phát lại/inject, không phải cabin thật.
3. Truyền và hiệu chỉnh confidence trên bộ audio đó rồi mới chọn ngưỡng `SafetyGuard`;
   ngưỡng 0.6 hiện chưa validate.

**Tiêu chí qua gate:** có WER và intent accuracy trên cùng audio cho hai đường ASR, có false
accept/hour của VAD, và threshold confidence được chọn bằng số. Chi tiết ở
`25-LECH-KIEN-TRUC-VOICE-PIPELINE.md` §5.

---

## Nguồn nội bộ

- Barem 5 ô và quy tắc gắn nhãn: `08-BAREM-VONG-2-CHINH-THUC.md` §1.5.
- Luồng mentor đã sửa và ranh giới intent/PropertyID: `11-PHAN-HOI-MENTOR-KICKOFF-30-07.md` §1–2.
- Mapping M2: `03-contracts.md` §0.2.
- Trạng thái build/Device và thứ tự công việc: `07-PLAN-CA-NHAN-LONG.md` §2, §6.
- Định vị sản phẩm ban đầu: `../Proposal_Vong1_VIVA_DigitalCockpit.md` slide 5–7.
