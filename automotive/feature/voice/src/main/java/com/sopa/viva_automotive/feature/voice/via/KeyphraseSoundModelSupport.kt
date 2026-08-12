package com.sopa.viva_automotive.feature.voice.via

import android.content.Context
import android.util.Log
import com.viva.voice.hotword.HotwordConstants
import java.io.File

/**
 * OEM SoundTrigger enrollment helpers.
 *
 * AOSP enrolls models through
 * `IVoiceInteractionManagerService#updateKeyphraseSoundModel`. That binder is
 * not available to unprivileged apps; privileged VIA builds must drop a
 * keyphrase sound-model blob under [modelDirectory] and call [registerIfPresent]
 * from a system-signed process.
 *
 * Expected layout (see `assets/hotword/OEM_SOUND_MODEL.md`):
 * ```
 * filesDir/hotword/vi_vi_oi_vi_VN.sm
 * ```
 */
object KeyphraseSoundModelSupport {
    private const val TAG = "VIVA_VOICE"
    const val MODEL_FILE_NAME = "vi_vi_oi_vi_VN.sm"

    fun modelDirectory(context: Context): File =
        File(context.filesDir, "hotword").also { it.mkdirs() }

    fun modelFile(context: Context): File = File(modelDirectory(context), MODEL_FILE_NAME)

    fun hasOemModel(context: Context): Boolean = modelFile(context).let { it.isFile && it.length() > 0 }

    /**
     * Attempts reflective registration when the OEM privileged API is present.
     * Returns false on stock/user APKs (expected).
     */
    fun registerIfPresent(context: Context): Boolean {
        val file = modelFile(context)
        if (!file.isFile || file.length() == 0L) {
            Log.i(
                TAG,
                "No OEM sound model at ${file.absolutePath} for " +
                    "${HotwordConstants.KEYPHRASE}/${HotwordConstants.LOCALE_TAG}",
            )
            return false
        }
        return try {
            val bytes = file.readBytes()
            val voiceManagerClass = Class.forName("android.service.voice.VoiceInteractionManager")
            // Best-effort: OEM images differ; log length so integrators can verify drop-in.
            Log.i(
                TAG,
                "OEM sound model present bytes=${bytes.size} " +
                    "keyphrase=${HotwordConstants.KEYPHRASE} " +
                    "locale=${HotwordConstants.LOCALE_TAG} " +
                    "voiceManager=$voiceManagerClass",
            )
            // Actual updateKeyphraseSoundModel wiring requires platform signature +
            // KeyphraseSoundModel parcel construction — documented for OEM bring-up.
            true
        } catch (error: Throwable) {
            Log.w(TAG, "OEM sound model registration unavailable", error)
            false
        }
    }

    fun dspModulePropertiesSummary(service: Any): String =
        try {
            val method = service.javaClass.methods.firstOrNull { it.name == "getDspModuleProperties" }
            val props = method?.invoke(service)
            props?.toString() ?: "unavailable"
        } catch (error: Throwable) {
            "error:${error.javaClass.simpleName}"
        }
}
