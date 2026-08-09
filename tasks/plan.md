# Implementation Plan: SafetyGuard integration readiness

## Overview

Turn the existing `feature/safety-guard` policy classes into an actually enforced vehicle-write boundary without claiming Device evidence. The first slice covers the safety-critical door-unlock path only: obtain current speed from the underlying repository, deny before any setter at unsafe speed, and fail closed when speed cannot be read.

## Architecture Decisions

- Keep `SafetyGuard` in `vehicle-service` and preserve the repository decorator boundary so all vehicle writes share one enforcement point.
- Read safety state inside the suspend write operation instead of accepting a synchronous caller-provided snapshot that production DI cannot currently supply.
- Treat missing/invalid speed as a denial for door unlock; do not block unrelated HVAC or cabin-light writes on speed availability.
- Keep confirmation UX and voice confidence propagation out of this slice. They need an explicit command context/two-turn contract and must not be implied by wiring alone.

## Task List

### Phase 1: Safety-critical repository path

- [x] Add failing tests for live speed lookup and fail-closed behavior.
- [x] Make `GuardedVehicleRepository` build its door safety snapshot from the delegate.
- [x] Verify focused vehicle-service tests.

### Checkpoint: Repository boundary

- [x] Unsafe or unknown-speed door unlock never reaches the delegate setter.
- [x] Safe unrelated property writes still reach the delegate.

### Phase 2: Production wiring

- [x] Provide the decorated repository in both `real` and `mock` app variants.
- [x] Verify Hilt compilation and the full JVM test suite.

### Checkpoint: Complete

- [x] APK code path resolves `VehicleRepository` to the guarded decorator.
- [x] All automated tests pass.
- [x] Remaining confirmation/confidence limitations are documented without overclaiming.

## Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Speed unit mismatch | Unsafe command allowed or denied incorrectly | Convert repository m/s value to km/h in one tested location |
| Vehicle speed unavailable | Door unlock could fail open | Deny with `G1_STALE_STATE` before setter |
| Decorator not selected by Hilt | No runtime behavior change | Replace direct variant bindings with explicit provider tests/build verification |
| Confirmation loops | Door unlock unusable after prompt | Do not claim confirmation complete; leave explicit follow-up task |

## Open Questions

- Which UI/voice component will own the confirmation token for `G2_CONFIRM_DOOR`?
- How will ASR confidence be carried across the existing `VehicleRepository` API?

---

# Implementation Plan: Voice-to-media completion (09/08/2026)

## Overview

Close the three-command product slice requested in the mentor feedback: the live
voice pipeline must route `media_play`, `media_pause`, and `media_next`, then
control the existing media player through the AAOS-compatible
`MediaBrowserCompat` / `MediaControllerCompat` boundary. Keep voice and media in
one APK for the submission build; do not claim separate-APK or CarSky execution
without runtime evidence.

## Architecture Decisions

- Model play, pause, and next as one typed media-command family rather than
  preserving `MediaNext` as a one-off special case.
- Keep `:feature:voice` independent from `:feature:media` implementation types;
  the Android adapter connects to the exported media-browser service by
  `ComponentName`, which is the boundary that can survive a later APK split.
- Prepare the demo queue when the media session token is exposed so a transport
  `play` or `skipToNext` command cannot target an empty player.
- Treat a missing/suspended media service as an execution failure with an honest
  Vietnamese response. Do not turn a one-way command dispatch into a stronger
  playback-success claim than the available evidence supports.

## Task List

### Phase 1: NLU contract

- [x] Add failing mapper and process-use-case tests for all three media intents.
- [x] Introduce the typed media command and make all three intents cross the
      voice-core/app boundary.

### Checkpoint: NLU

- [x] Focused voice tests pass and B13/B14/B15 no longer stop at `NotWired`.

### Phase 2: Media transport

- [x] Add a testable media-control port to `:feature:voice`.
- [x] Implement and bind the Android `MediaBrowserCompat` client.
- [x] Dispatch media commands from `ExecuteVehicleControlUseCase`.
- [x] Ensure the media session has a prepared queue before exposing its token.

### Checkpoint: Integration

- [x] Voice and media modules compile through Hilt for mock and real variants.
- [x] Full JVM tests pass with no skipped tests.
- [x] Both debug APK variants assemble; lint remains green.

### Phase 3: Evidence-safe handoff

- [x] Review the diff for correctness, simplicity, architecture, security, and
      performance.
- [x] Update submission Markdown only to the level proven by tests/build and
      any runtime capture obtained in this workspace.

### Phase 4: CarSky Device runtime — 09/08

- [x] Upload private artifact `viva-apk` `0.0.1` and verify its byte count.
- [x] Download on Device, match SHA-256, replace the conflicting old mock
      package signature, and launch the exact verified APK.
- [x] Run `phát nhạc`, `dừng nhạc`, `chuyển bài` through the mock/debug text
      injection hook and capture `VIVA_TRACE_SUMMARY` plus MediaSession state.
- [x] Prove play (`PLAYING`), pause (`PAUSED`) and next (active item `0 → 1`).
- [x] Save evidence under `evidence/c2/carsky-runtime-20260809/` and update
      claims without merging mic/ASR/TTS/VHAL into the proven scope.

## Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Media session exposes an empty queue | Play/next is accepted but produces no audio | Prepare the demo queue before returning the session token |
| Browser service cannot connect | Voice turn hangs or falsely succeeds | Bound connection time and return an explicit failure |
| Exported browser service accepts an arbitrary package | Another installed app could obtain the session token and send transport commands | Keep as an explicit pre-production risk; add a signature/caller allowlist before a split APK or production release, after the CarSky host package is known |
| Compile-time dependency on media implementation | Later APK split requires rewriting voice | Connect through `ComponentName` and media-session APIs only |
| Dirty team worktree is overwritten | Evidence or teammate work is lost | Do not switch/reset; touch only planned tracked files |
| Text-injection runtime is described as full voice E2E | Submission overclaims mic/ASR/TTS | Claim only CarSky Device NLU → media; keep mic/VAD/ASR and audio-focus separate |

## Open Questions

- Which Vietnamese TTS/pre-rendered prompts should cover the media dispatch
  responses observed as degraded on the Device?
- When will the team capture a real microphone turn and TTS duck/release on the
  same Device so the remaining half of C-MEDIA/E11 can move beyond yellow?
