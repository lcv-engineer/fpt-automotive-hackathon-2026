# 02 — Bản đồ repo và module

---

## 1. Thư mục gốc

```text
automotive/          App AAOS (Kotlin, Gradle) — feature module + vehicle-service
android/voice/       voice-core: JVM thuan + adapter Android (VAD, ASR client, trace, TTS)
asr/                 Service `viva-asr` (Python/FastAPI) + Dockerfile + brain planner
backend/             CLI Go `viva-tools`: benchmark harness + CarSky devops helper
embedded/            Script Luau VHAL<->CAN, UDS/DTC simulator, 4 script kiem thu Python
GATEWAY/             Script Lua do NEN TANG CarSky cap (doi chieu, khong phai team-owned)
evidence/            Bang chung chay that: ablation, ASR corpus, emulator, CarSky Device
docs/                Tai lieu — xem docs/he-thong/ va docs/carsky/
vong2/ · vong3/      Ke hoach, contract, kich ban demo, bao cao theo vong thi
tasks/               plan.md, todo.md
.github/workflows/   android-ci · asr-ci · backend-ci · carsky-push-asr-image · carsky-deploy-asr
```

---

## 2. `automotive/` — module Gradle

Từ `automotive/settings.gradle.kts`:

```text
:app                        Entry point: MainActivity, NavGraph, wiring DI theo flavor
:core:common                Dispatcher qualifier, application scope
:core:ui                    Design system automotive (Compose, dark, target lon), VoiceLanguage
:core:database              Room (command vocabulary, voice history) + DataStore (settings)
:feature:voice              ASR adapter, orchestration, service, overlay UI, navigation
:feature:media              MediaBrowserService, MediaSession, ExoPlayer
:feature:hvac               Man hinh dieu hoa
:feature:vehicle-status     Toc do / nhien lieu / pin / cua
:feature:diagnostics        Chan doan
:feature:settings           Cai dat giong noi va don vi
:vehicle-service:api        VehicleRepository, SafetyGuard, VehicleProperties, entity
:vehicle-service:impl       RealVehicleRepository (android.car) + MockVehicleRepository + DefaultSafetyGuard
:voice-core                 (project(":voice-core") tro toi android/voice/)
```

Hai module tuỳ chọn (`:phone-companion`, `:phone-companion-app`) chỉ include khi bật
cờ tương ứng.

**Kiến trúc trong app:** Clean Architecture + MVVM, unidirectional data flow, Kotlin
Coroutines/Flow, Hilt cho DI.

### File hay phải mở

| Việc | File |
|---|---|
| Bind ASR/NLU/guard | `feature/voice/di/VoiceModule.kt` |
| Chọn engine ASR lúc chạy | `feature/voice/data/asr/RoutingAsrClient.kt` |
| Gọi viva-asr | `feature/voice/data/asr/HttpAsrClient.kt` |
| Điều phối một lượt | `android/voice/…/agent/VoiceAgent.kt` |
| Luật NLU | `android/voice/…/intent/GrammarIntentRouter.kt`, `NegationGate.kt` |
| Dịch intent → lệnh có kiểu | `feature/voice/integration/CoreIntentMapper.kt` |
| Điều phối thực thi | `feature/voice/integration/AppCommandGateway.kt`, `domain/ExecuteVehicleControlUseCase.kt` |
| Luật an toàn | `vehicle-service/api/SafetyGuard.kt`, `vehicle-service/impl/DefaultSafetyGuard.kt` |
| Property ID / areaId | `vehicle-service/api/…/VehicleProperties.kt` |
| Cleartext cho node ASR | `app/src/main/res/xml/network_security_config.xml` |
| Cờ build voice | `feature/voice/build.gradle.kts` |

---

## 3. `android/voice/` — voice-core

Phần lớn là **JVM thuần** để test không cần thiết bị.

```text
voice/agent/       VoiceAgent (dieu phoi mot luot), AgentPlanner
voice/asr/         AsrClient interface + AsrResult
voice/audio/       PcmSource, AudioCapture, VadSegmenter, VadStreamDriver,
                   SileroVadOnnxScorer, PushToTalkRecorder, WavWriter, AndroidPcmSource
voice/hotword/     HotwordGate, SoftwareHotwordDetector, HotwordConstants, HotwordMetrics
voice/intent/      GrammarIntentRouter, Intent, NegationGate
voice/trace/       LatencyTrace, Trace, AndroidTrace, TraceVerdict
voice/tts/         TtsSpeaker, AndroidTtsSpeaker, AudioFocus, AndroidAudioFocusController,
                   PrerenderedPrompts
fixtures/          golden_trace.log, golden_trace_edge.log — ban giao cho harness Go
third_party/       silero-vad-LICENSE
```

---

## 4. `asr/` — service `viva-asr`

```text
app/main.py        FastAPI: GET /health · POST /asr · POST /v1/brain/plan
app/model.py       faster-whisper/CTranslate2, segments_to_confidence
app/audio.py       decode PCM16 LE mono
app/brain.py       goi OpenAI Responses API, Structured Outputs
app/config.py      Settings.from_env() — moi tham so la bien moi truong
app/schemas.py     pydantic model cho ca ba route
tests/             12 file test; HTTP test chay voi transcriber gia
scripts/           bench_noise_levels · bench_tts_samples · make_bench_corpus · run_matrix
                   record_corpus · noise_mix · resample · send_pcm · check_corpus
Dockerfile         convert PhoWhisper -> CTranslate2 INT8 o stage tam
```

**Vì sao là Python, không nằm trong `backend/`:** `backend/` là CLI Go với quy tắc
**zero-dependency** có chủ đích. Phục vụ INT8 Whisper được hỗ trợ tốt hơn nhiều
trong Python (`faster-whisper`/CTranslate2) so với ONNX binding của Go.

---

## 5. `backend/` — CLI `viva-tools` (Go)

```text
cmd/viva-tools/            main
internal/domain/           parse.go (doc VIVA_TRACE), trace.go, verdict.go
internal/usecase/harness/  aggregate · report · compare · regression · verdicts
internal/usecase/devops/   blueprint.go (export/clone CarSky)
internal/infrastructure/   carsky/client.go · logsource/{file,adb,scan} · report/csv* · suite/csv
internal/interfaces/cli/   root.go · harness_cmd.go · devops_cmd.go
internal/config/           doc backend/.env
suites/benchmark_v1.csv    bo 22 cau benchmark
testdata/                  sample_trace.log
scripts/                   run_benchmark.ps1 · emulator_voice_session.ps1
carsky/                    nodes.json, blueprint backup, clone response (bang chung)
```

⚠️ **Không có dependency ngoài Go standard library** — cố ý, để không phải vendor gì.

Đây **không** phải Command Dispatcher / skills API: logic đó nằm trong app Kotlin.

---

## 6. `embedded/` — VHAL ↔ CAN và mô phỏng chẩn đoán

```text
vhal_server.luau                Script VHAL <-> CAN cua doi (Tung)
uds_dtc_simulator.py            Mo phong UDS/DTC
car_signals.dbc                 DBC lam viec (⚠️ ban that nam o docs/dbc/)
test_vhal_embedded.py           Kiem thu
test_vhal_server_luau.py        Kiem thu script Luau
test_safety_scenario_pack.py    Bo kich ban an toan
test_compatibility_checker.py   Doi chieu DBC/VSS that o docs/dbc/
```

---

## 7. `GATEWAY/` — script Lua của nền tảng

⚠️ **Không phải code của đội.** Giữ để đối chiếu khi viết script node.

```text
IVI_GATEWAY.lua · BCM_GATEWAY.lua · PWT_Gateway.lua · TCU_GATEWAY.lua
VCU.lua · BMS ECU.lua · BCM ECU.lua · Climate ECU.lua
README.md   (so do kien truc + bang anh xa tin hieu)
```

---

## 8. `docs/`

```text
docs/he-thong/       Bo tai lieu he thong (file nay)
docs/carsky/         Bo tai lieu nen tang CarSky
docs/architecture/   VIVA-VOICE-BRAIN-BODY.md — tai lieu kien truc chuan
docs/decisions/      ADR 001, 002
docs/backend-docs/   carsky-api.md · carsky-runbook.md · v6-viva-asr.md
docs/dbc/            DBC/VSS that export tu CarSky — ban duy nhat trong repo
docs/platform/       Car-Sky-Platform.html · Digital Cockpit.html · Middleware.html
docs/btc/            The le, terms, webinar, template cua BTC
docs/bai-nop/        Ban nop vong 1 va vong 2
docs/bao-cao/        Bao cao tien do gui mentor
docs/nhat-ky/        Nhat ky cong viec, log tin nhan BTC/mentor
docs/nghien-cuu/     Nghien cuu tham chieu (ViVi cua VinFast)
docs/superpowers/    Spec va implementation plan
```

`vong2/` và `vong3/` giữ kế hoạch, contract và kịch bản demo theo từng vòng thi —
`vong2/03-contracts.md` vẫn là **contract sống**, không phải tài liệu lịch sử.

---

## 9. Quy ước không được phá

| Quy ước | Ở đâu |
|---|---|
| Không commit `.env`, token, API key, keystore, APK | `.gitignore` |
| Không commit `openapi.json` của CarSky (nội bộ nền tảng, thể lệ 3.6) | — |
| `backend/` không thêm dependency ngoài stdlib | `backend/README.md` |
| DBC/VSS chỉ có **một** bản, ở `docs/dbc/` | `docs/dbc/README.md` |
| Không sửa `vong2/03-contracts.md` một mình | Header của chính file đó |
| Không escape tay khi ghi trace — `LatencyTrace` tự làm | `vong2/03-contracts.md` §1.1 |
