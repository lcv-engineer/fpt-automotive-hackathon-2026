# CarSky runtime — VIVA mock APK — 09/08/2026

## Kết luận có thể claim

- APK mock do đội build đã được tải lên artifact riêng tư `viva-apk` phiên bản `0.0.1`, tải xuống và cài trên Device `VIVA` của deployment `VIVA-demo-0808`.
- SHA-256 của file tải xuống và `base.apk` đã cài đều khớp bản local:
  `b3ad9a4b6c83c032a09cd240987738e63eda454cc410f260fe37613468bfa0da`.
- Ba câu bơm văn bản `phát nhạc`, `dừng nhạc`, `chuyển bài` đi qua đúng phần sản phẩm sau ASR: receiver debug → `VoiceAssistantService` → NLU → `MediaBrowserCompat`/`MediaControllerCompat` → MediaSession/ExoPlayer.
- MediaSession đổi trạng thái thật trên Device: `PLAYING`, `PAUSED` tại 3.124 s, và `media_next` đổi active item `0 → 1` rồi tiếp tục `PLAYING`.

Đây là bằng chứng **CarSky Device runtime cho NLU → media**, không phải bằng chứng giọng nói end-to-end. Test hook cố ý bỏ qua mic, VAD và ASR; `e2e_ms=0` vì vậy không phải latency giọng nói.

## Nhận dạng môi trường và artifact

| Trường | Giá trị |
|---|---|
| CarSky deployment | `VIVA-demo-0808` — 22/22 node Running |
| Device | `VIVA` — Device ID `v37aa3knc6t1embelr5yi` |
| Android target | `trout_arm64`, `arm64-v8a`, Android 14 / SDK 34 |
| Package | `com.sopa.viva_automotive.mock` |
| Version | `versionCode=1`, `versionName=1.0`, target SDK 36 |
| Install time (device UTC) | `2026-08-09 02:58:26` |
| CarSky artifact | private `viva-apk` / `0.0.1` / `app-mock-debug.apk` / 387,904,742 bytes |
| SHA-256 | `b3ad9a4b6c83c032a09cd240987738e63eda454cc410f260fe37613468bfa0da` |

`pm install -r` ban đầu bị `INSTALL_FAILED_UPDATE_INCOMPATIBLE` vì package cũ trên Device được ký bằng khóa khác. Đã gỡ đúng package mock cũ rồi cài sạch; package khác và bản real không bị tác động.

## Runtime trace và trạng thái quan sát được

```text
VIVA_TRACE_SUMMARY|3fa9c6df-7077-4696-9a00-5f22b160f897|phát nhạc|media_play|Allow|e2e_ms=0
MediaSession: state=PLAYING(3), position=0, speed=1.0

VIVA_TRACE_SUMMARY|a92f5f4c-6545-48e9-93c5-cb64cab46f13|dừng nhạc|media_pause|Allow|e2e_ms=0
MediaSession: state=PAUSED(2), position=3124, bufferedPosition=4000, speed=0.0, activeItemId=2

VIVA_TRACE_SUMMARY|3cf25f75-75e6-4fc4-af29-e2c001ee2d60|chuyển bài|media_next|Allow|e2e_ms=0
MediaSession before: activeItemId=0
MediaSession after:  state=PLAYING(3), position=0, speed=1.0, activeItemId=1
```

Lượt `media_next` đầu tiên được thử ở cuối queue nên không đổi item; lượt xác nhận cuối khởi tạo lại player ở đầu queue và chứng minh item đổi `0 → 1`. Chỉ lượt cuối được dùng làm evidence cho `next`.

## Khoảng trống còn lại

1. Chưa kiểm mic → VAD → ASR trên Device; receiver debug dùng `text_b64` và chỉ có trong mock/debuggable build.
2. TTS phản hồi cho `media_play` bị degrade trên Device: không có giọng Việt hoặc prompt pre-render tương ứng với câu `Đã gửi lệnh phát nhạc tới trình phát.`. Media vẫn chạy đúng nhưng không được claim phản hồi TTS hoàn chỉnh.
3. Chưa quay/capture âm thanh đầu ra; bằng chứng player hiện dựa trên trace và `dumpsys media_session`.
4. Đây là mock flavor, không chứng minh VHAL/CAN/CCU hoặc quyền privileged của real flavor.

## Lệnh tái lập cốt lõi

```sh
am broadcast \
  -a com.sopa.viva_automotive.mock.UTTERANCE \
  --es text_b64 cGjDoXQgbmjhuqFj \
  -n com.sopa.viva_automotive.mock/com.sopa.viva_automotive.debug.SimulatedUtteranceReceiver

# text_b64:
# cGjDoXQgbmjhuqFj       = phát nhạc
# ZOG7q25nIG5o4bqhYw==   = dừng nhạc
# Y2h1eeG7g24gYsOgaQ==   = chuyển bài
```

Nguồn: CarSky web ADB shell của Device `VIVA`, phiên làm việc ngày 09/08/2026. Không lưu credential, session key hoặc Local ADB gateway key trong repo.
