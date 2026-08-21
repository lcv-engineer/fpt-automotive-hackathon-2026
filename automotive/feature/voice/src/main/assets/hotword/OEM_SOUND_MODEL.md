# OEM SoundTrigger model — “Viva ơi” / vi-VN

## Purpose

AOSP `AlwaysOnHotwordDetector` requires a keyphrase sound model enrolled through
SoundTrigger HAL. Custom Vietnamese wake phrases are **not** available on stock
emulator DSP modules; OEMs must supply a trained model.

## Drop-in path (privileged VIA)

At runtime the privileged app looks for:

```text
<filesDir>/hotword/vi_vi_oi_vi_VN.sm
```

See `KeyphraseSoundModelSupport.MODEL_FILE_NAME`.

## Enrollment metadata

Keyphrase enrollment apps must advertise support for:

| Field | Value |
|---|---|
| Keyphrase | `Viva ơi` |
| Locale | `vi-VN` |
| Recognition modes | voice trigger (+ optional user identification) |

Use `com.android.intent.action.MANAGE_VOICE_KEYPHRASES` for enroll / re-enroll /
un-enroll flows.

## Registration API (platform-signed)

```text
IVoiceInteractionManagerService.updateKeyphraseSoundModel(KeyphraseSoundModel)
```

Construct `KeyphraseSoundModel` with keyphrase id, locale, and DSP blob bytes from
the vendor training pipeline. Call `getDspModuleProperties()` on the active
`VoiceInteractionService` before enrollment to confirm max keyphrases / modes.

## Validation gate

Do not enable hotword by default until cabin measurements meet product targets:

- false accepts / hour
- false reject rate
- trigger latency (phrase end → session listening)
- self-wake rate during TTS (must be ~0 with `HotwordGate`)

Software KWS (`SoftwareHotwordDetector`) is the Cuttlefish / no-DSP fallback only.
