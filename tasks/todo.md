# SafetyGuard integration checklist

## Task 1: Enforce live speed on door unlock

**Acceptance criteria:**
- [x] Door unlock reads `PERF_VEHICLE_SPEED` from the underlying repository.
- [x] Speed above 5 km/h returns `Deny:G1_SPEED_LOCK` without calling the setter.
- [x] Missing, unavailable, non-numeric, or non-finite speed returns `Deny:G1_STALE_STATE` without calling the setter.

**Verification:**
- [x] `automotive/gradlew :vehicle-service:impl:testDebugUnitTest`

**Dependencies:** None

**Estimated scope:** Medium (2-3 files)

## Task 2: Wire the decorator into app variants

**Acceptance criteria:**
- [x] Real and mock variants resolve `VehicleRepository` through `GuardedVehicleRepository`.
- [x] Existing repository implementations remain the delegate and retain singleton scope.

**Verification:**
- [x] `automotive/gradlew test`
- [x] Hilt/app compilation included in the repository's test/build gate.

**Dependencies:** Task 1

**Estimated scope:** Medium (2-4 files)

## Task 3: Record remaining evidence boundary

**Acceptance criteria:**
- [x] No document claims Device execution from JVM tests.
- [x] Confirmation and confidence propagation remain explicitly open if not implemented.

**Verification:**
- [x] Review final diff and current claim/evidence map.

**Remaining follow-ups (not completed by this slice):**

- [ ] Route the second voice turn into `VehicleWriteContext(isConfirmed = true)`.
- [ ] Propagate calibrated ASR confidence into `VehicleWriteContext.confidence`.
- [ ] Capture Device evidence before changing C-SAFETY/E09 from red.

**Dependencies:** Tasks 1-2

**Estimated scope:** Small (0-1 file)

---

# Voice-to-media completion checklist (09/08/2026)

## Task 4: Route all mentor-requested media intents

**Acceptance criteria:**
- [x] `media_play`, `media_pause`, and `media_next` map to typed executable intents.
- [x] Existing malformed-slot and unsupported-command behavior is unchanged.

**Verification:**
- [x] Focused `CoreIntentMapperTest` and `ProcessVoiceCommandUseCaseTest` fail before implementation.
- [x] The same focused tests pass after implementation.

**Dependencies:** Existing voice-core grammar rules

**Estimated scope:** Medium (3-5 files)

## Task 5: Execute media through the AAOS media-session boundary

**Acceptance criteria:**
- [x] Voice code connects with `MediaBrowserCompat` and controls playback with `MediaControllerCompat.TransportControls`.
- [x] The media session exposes a non-empty prepared demo queue.
- [x] Connection failure/timeout returns failure instead of hanging or claiming success.

**Verification:**
- [x] `:feature:voice:testDebugUnitTest`
- [x] `:feature:voice:compileDebugKotlin`
- [x] Hilt/app compilation for both mock and real variants

**Dependencies:** Task 4

**Estimated scope:** Medium (4-5 files)

## Task 6: Submission-quality gate

**Acceptance criteria:**
- [x] Full JVM suite, lint, and both APK assemblies pass.
- [x] Final diff has no unrelated edits, secrets, generated build output, or unsupported claims.
- [x] Documentation distinguishes build proof from emulator/CarSky runtime proof.

**Verification:**
- [x] `automotive/gradlew test`
- [x] `automotive/gradlew lintMockDebug lintRealDebug`
- [x] `automotive/gradlew :app:assembleMockDebug :app:assembleRealDebug`

**Dependencies:** Tasks 4-5

**Estimated scope:** Medium (verification plus evidence-safe docs)

**Remaining production hardening:**

- [ ] Restrict `VivaMediaBrowserService.onGetRoot` to trusted callers (same UID
      now; signed CarSky/host allowlist if the media service is split later).
- [ ] Capture the real CarSky host package/UID before changing the exported
      service contract; do not guess an allowlist that could break platform
      browsing.

## Task 7: CarSky Device runtime evidence

**Acceptance criteria:**
- [x] The installed `base.apk` SHA-256 matches the local mock APK.
- [x] `media_play`, `media_pause`, and `media_next` each produce an `Allow`
      summary from the product pipeline after ASR.
- [x] MediaSession proves `PLAYING`, `PAUSED`, and an active-item transition.
- [x] Evidence states that text injection bypasses mic/VAD/ASR and does not
      prove VHAL/CAN.

**Verification:**
- [x] `evidence/c2/carsky-runtime-20260809/README.md`
- [x] `evidence/c2/carsky-runtime-20260809/runtime-transcript.txt`

**Runtime follow-ups:**

- [ ] Add/align Vietnamese TTS or pre-rendered prompts for media dispatch
      responses; Device logged a missing-voice/prompt degradation.
- [ ] Capture one real mic → VAD → ASR → NLU → media turn on Device.
- [ ] Capture audible TTS duck/release and HMI in E11 video before claiming the
      complete compound C-MEDIA statement.
