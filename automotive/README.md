# Viva Automotive — Vietnamese Voice Assistant for AAOS

A native Android Automotive OS (AAOS) app that controls vehicle features (HVAC, doors,
vehicle status) and media through a Vietnamese voice assistant. The active ASR binding is
`RoutingAsrClient`, which selects viva-asr HTTP or Google Cloud Speech from Settings. The
active intent binding is the deterministic `GrammarIntentRouter`. Vehicle signals go through
`CarPropertyManager` (VHAL) or an in-memory simulator.

## Architecture

Clean Architecture + MVVM + unidirectional data flow, Kotlin Coroutines/Flow
throughout, Hilt for DI.

The product-level architecture is documented as
[`VIVA Voice · Brain · Body`](../docs/architecture/VIVA-VOICE-BRAIN-BODY.md). These are logical
boundaries; the physical Gradle modules have not been renamed before the final round.

```text
app/                      Entry point: MainActivity, NavGraph, flavor DI wiring
core/
  common/                 Dispatcher qualifiers, application scope
  ui/                     Automotive design system (Compose, dark, large targets)
  database/               Room (command vocabulary) + DataStore (settings)
feature/
  voice/                  ASR adapters, deterministic routing, orchestration, service, overlay UI
  hvac/                   Climate control screen
  vehicle-status/         Speed / fuel / battery / doors screen
  settings/               Voice & unit settings
vehicle-service/
  api/                    VehicleRepository + UxRestrictionsRepository interfaces, entities
  impl/                   RealVehicleRepository (android.car) + MockVehicleRepository (simulator)
```

Active voice pipeline: `AudioRecord` → Silero VAD → `RoutingAsrClient` → `VoiceAgent` →
`GrammarIntentRouter` → `AppCommandGateway`/`CoreIntentMapper` →
`ExecuteVehicleControlUseCase` → guarded `VehicleRepository`.
Runs in `VoiceAssistantService` (mic foreground service) via
`VoiceAssistantStateManager`
(IDLE → LISTENING → PROCESSING → EXECUTING → SUCCESS/ERROR).

## Build flavors

| Flavor | Vehicle backend | Use for |
| ------ | --------------- | ------- |
| `mock` (default) | In-memory simulator (speed wave, temp convergence, energy drain) | Any emulator, unit tests, UI work |
| `real` | `CarPropertyManager` via VHAL | AAOS devices / Automotive emulator images |

```bash
# Emulator-friendly debug build
gradlew :app:assembleMockDebug

# VHAL-backed build (see permission note below)
gradlew :app:assembleRealDebug

# Unit tests (voice core, NLU, contracts, repository, shared units)
gradlew :voice-core:testDebugUnitTest :feature:voice:testDebugUnitTest :vehicle-service:api:testDebugUnitTest :vehicle-service:impl:testDebugUnitTest :core:common:testDebugUnitTest
```

Verified again on 02/08/2026 with Temurin JDK 21 and Android SDK 37: 139 tests passed
(65 in `voice-core` + 74 in the automotive modules), with 0 failures/errors/skipped;
both `assembleMockDebug` and `assembleRealDebug` completed successfully.

> Note: `android.car.permission.CONTROL_CAR_CLIMATE`, `CONTROL_CAR_DOORS`, and
> `CONTROL_CAR_INTERIOR_LIGHTS` are privileged permissions. The `real` flavor can
> read properties on a standard AAOS emulator, but writes require installing the
> app as privileged/platform-signed, with the permissions allowlisted by the OEM
> (privapp-permissions XML).

### Running the real flavor against the emulator VHAL

The **mock flavor never syncs with the AAOS system bar HVAC** — that is by
design: mock talks to an in-memory simulator, while the system bar talks to the
real VHAL. To see the app and the system bar move together, install the `real`
flavor as a privileged app.

> **Do not `adb push` a new APK over `/system/priv-app/...` and launch without
> rebooting.** That leaves PackageManager with a stale APK mapping and crashes
> at process start with:
> `NullPointerException: Resources.getConfiguration()` in
> `ConfigurationController.updateLocaleListFromAppContext`.

**One-time privileged install** (userdebug / Google APIs image, not Play Store):

```bash
gradlew :app:assembleRealDebug
adb root && adb remount               # reboot once if remount asks for it
adb shell mkdir -p /system/priv-app/VivaAutomotive
adb push app/build/outputs/apk/real/debug/app-real-debug.apk /system/priv-app/VivaAutomotive/VivaAutomotive.apk
adb shell chmod 644 /system/priv-app/VivaAutomotive/VivaAutomotive.apk
adb push app/privapp-permissions-com.sopa.viva_automotive.xml /system/etc/permissions/
adb reboot
```

**Day-to-day updates** (keeps privileged permissions, no reboot, avoids the NPE):

```bash
gradlew :app:assembleRealDebug
adb install -r -d app/build/outputs/apk/real/debug/app-real-debug.apk
adb shell am start --user 10 -n com.sopa.viva_automotive/.MainActivity
```

If you already hit the Resources NPE, recover with:

```bash
adb shell am force-stop com.sopa.viva_automotive
adb install -r -d app/build/outputs/apk/real/debug/app-real-debug.apk
adb reboot
```

3. Verify from the shell that app and VHAL agree (property 358614275 =
   `HVAC_TEMPERATURE_SET`, area 49 = driver zone):

```bash
adb shell cmd car_service get-property-value 358614275 49
```

If a privileged permission is missing, the app does not fail silently: writes
surface a snackbar naming the permission, and `RealVehicleRepo` logs the
details (`adb logcat -s RealVehicleRepo`).

## Vehicle IoT connectivity

The app never talks to the VHAL directly. Every vehicle signal travels this
multi-layered path (down for writes, up for state changes):

```text
Feature ViewModels / voice use cases
  → VehicleRepository (vehicle-service/api, framework-agnostic)
    → RealVehicleRepository: CarPropertyManager (android.car)
      → CarService (system process)
        → VHAL (translates property ids to hardware command codes)
          → MCU, e.g. Cortex-M over RPMsg / NXP SRTM AUTO (category 0x08)
```

The `mock` flavor replaces the bottom four layers with `MockVehicleRepository`,
an in-memory simulator, so features and the voice pipeline run unchanged on any
emulator.

### Supported vehicle units

| Domain | Properties |
| ------ | ---------- |
| HVAC | power, A/C, auto mode, fan speed, fan direction (face/floor/both/defrost), driver & passenger setpoint, cabin temperature |
| Body | door lock, door position, interior (cabin) lights |
| Sensors | speed, fuel level, EV battery level, ignition state |

### Dummy vehicle driver (mock flavor)

To simulate events arriving *from the vehicle side* without hardware — the
equivalent of NXP's dummy driver (`echo 1 > /sys/devices/platform/vehicle-dummy/ac_on`)
or pushing VSTATE commands from an EVK's Cortex-M4 console — broadcast a
VSTATE command to the mock build:

```bash
# AC on (vehicle-side event)
adb shell am broadcast -a com.sopa.viva_automotive.mock.VSTATE \
    --es unit_type ac --es state_value 1

# Fan speed level 5
adb shell am broadcast -a com.sopa.viva_automotive.mock.VSTATE \
    --es unit_type fan_speed --es state_value 5

# Driver setpoint 22.5 C, defrost airflow, driver door opened
adb shell am broadcast -a com.sopa.viva_automotive.mock.VSTATE --es unit_type temp_driver --es state_value 22.5
adb shell am broadcast -a com.sopa.viva_automotive.mock.VSTATE --es unit_type fan_direction --es state_value defrost
adb shell am broadcast -a com.sopa.viva_automotive.mock.VSTATE --es unit_type door --es state_value 1
```

Unit types: `hvac_power`, `ac`, `hvac_auto`, `fan_speed`, `fan_direction`,
`temp_driver`, `temp_passenger`, `temp_cabin`, `door_lock`, `door`,
`cabin_light`, `speed`, `fuel`, `battery`, `ignition` (see `VstateCommands`).
Injected events flow through the same observers as real VHAL callbacks, so the
UI and voice assistant react exactly as they would in a vehicle.

## Voice models

### Long voice-core integration

The app includes `../android/voice` as Gradle module `:voice-core`. The stable boundary is
`CoreIntentMapper`: Long's grammar emits the five backbone intent names, and the mapper translates
them into the app's existing `VehicleIntent`, media-next, or volume actions. Malformed or missing
slots return `null`; they never fall through to a default vehicle command.

This is intentionally a narrow bridge. Microphone, Silero VAD, NLU, vehicle execution and UI
remain on the same path. **STT is always `viva-asr`** (`HttpAsrClient` → HTTP `/asr`).

Embedding NLU uses multilingual ONNX under `feature/voice/src/main/assets/embeddings/`
(downloaded by Gradle `downloadEmbeddingModel`).

### Running viva-asr for local demos

```powershell
# host: start asr/ (uvicorn :8080)
.\gradlew :app:assembleMockDebug `
  -PvivaAsrBaseUrl=http://127.0.0.1:8080
```

On emulator, the app rewrites `127.0.0.1`/`localhost` to `10.0.2.2` automatically (no
`adb reverse` required). The adapter posts raw PCM16 LE mono to `/asr` with
`X-Sample-Rate` and `X-Trace-Id`. Cleartext is allowed for loopback / `10.0.2.2` in
dev; non-loopback deployments must use HTTPS.

## Example commands

English:

- "set the temperature to twenty two degrees" / "set passenger temperature to 19"
- "make it warmer" / "it's too hot"
- "set fan speed to five", "more air"
- "turn on the ac" / "turn off the a c"
- "lock the doors" / "unlock the doors"
- "how fast am I going", "how much fuel is left", "battery level"

Vietnamese (voice language = Tiếng Việt):

- "bật điều hòa" / "tắt điều hòa"
- "đặt nhiệt độ 22 độ" / "tăng quạt"
- "khóa cửa" / "mở khóa cửa"

The keyword vocabulary is seeded into Room on first launch
(`CommandMappingRepository`) and can be extended without a new APK.

## Driver safety

`UxRestrictionsRepository` exposes the car's distraction-optimization state
(`CarUxRestrictionsManager` in the real flavor; derived from simulated speed in the
mock). The voice overlay disables its pulse animation while restricted, and screens
use large touch targets and a dark, low-glare theme.
