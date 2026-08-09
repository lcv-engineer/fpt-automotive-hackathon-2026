package com.sopa.viva_automotive.feature.voice.via

import android.content.Intent
import android.os.Build
import android.service.voice.VoiceInteractionService
import android.util.Log
import com.viva.voice.hotword.HotwordConstants
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * Reflection bridge to [android.service.voice.AlwaysOnHotwordDetector].
 *
 * That type is `@SystemApi` and is not in the public SDK stubs used by this
 * Gradle project. On OEM privileged images the methods exist at runtime; on
 * Cuttlefish / userdebug APKs we report [Availability.HARDWARE_UNAVAILABLE] and
 * let [HotwordController] arm the software fallback.
 */
class DspHotwordDetector(
    private val service: VoiceInteractionService,
    private val onDetected: () -> Unit,
    private val onAvailability: (Availability) -> Unit,
) {
    enum class Availability {
        HARDWARE_UNAVAILABLE,
        KEYPHRASE_UNSUPPORTED,
        KEYPHRASE_UNENROLLED,
        KEYPHRASE_ENROLLED,
        ERROR,
    }

    private val detectorRef = AtomicReference<Any?>(null)

    fun start() {
        try {
            val locale = HotwordConstants.LOCALE
            val create = service.javaClass.methods.firstOrNull { method ->
                method.name == "createAlwaysOnHotwordDetector" &&
                    method.parameterTypes.size >= 2 &&
                    method.parameterTypes[0] == String::class.java &&
                    method.parameterTypes[1] == Locale::class.java
            }
            if (create == null) {
                Log.w(TAG, "createAlwaysOnHotwordDetector not on classpath — DSP path unavailable")
                onAvailability(Availability.HARDWARE_UNAVAILABLE)
                return
            }
            val callbackClass = create.parameterTypes.last()
            val callback = java.lang.reflect.Proxy.newProxyInstance(
                callbackClass.classLoader,
                arrayOf(callbackClass),
            ) { _, method, args ->
                when (method.name) {
                    "onAvailabilityChanged" -> {
                        val state = args?.getOrNull(0) as? Int ?: -1
                        onAvailability(mapAvailability(state))
                        null
                    }
                    "onDetected" -> {
                        Log.i(TAG, "DSP hotword detected for ${HotwordConstants.KEYPHRASE}")
                        onDetected()
                        null
                    }
                    "onError" -> {
                        Log.e(TAG, "DSP hotword error")
                        onAvailability(Availability.ERROR)
                        null
                    }
                    "onRecognitionPaused", "onRecognitionResumed" -> null
                    else -> null
                }
            }
            val detector = when (create.parameterTypes.size) {
                3 -> create.invoke(service, HotwordConstants.KEYPHRASE, locale, callback)
                else -> create.invoke(
                    service,
                    HotwordConstants.KEYPHRASE,
                    locale,
                    /* executor */ null,
                    callback,
                )
            }
            detectorRef.set(detector)
            Log.i(
                TAG,
                "DSP detector created keyphrase=${HotwordConstants.KEYPHRASE} " +
                    "locale=${HotwordConstants.LOCALE_TAG} sdk=${Build.VERSION.SDK_INT}",
            )
        } catch (error: Throwable) {
            Log.w(TAG, "Failed to create AlwaysOnHotwordDetector", error)
            onAvailability(Availability.HARDWARE_UNAVAILABLE)
        }
    }

    fun startRecognition(): Boolean {
        val detector = detectorRef.get() ?: return false
        return try {
            val method = detector.javaClass.methods.firstOrNull {
                it.name == "startRecognition" && it.parameterTypes.isEmpty()
            } ?: detector.javaClass.methods.firstOrNull { it.name == "startRecognition" }
            val result = method?.invoke(detector) as? Boolean ?: false
            Log.i(TAG, "DSP startRecognition → $result")
            result
        } catch (error: Throwable) {
            Log.w(TAG, "DSP startRecognition failed", error)
            false
        }
    }

    fun stopRecognition() {
        val detector = detectorRef.get() ?: return
        runCatching {
            detector.javaClass.methods.firstOrNull { it.name == "stopRecognition" }
                ?.invoke(detector)
        }
    }

    fun createEnrollIntent(): Intent? {
        val detector = detectorRef.get() ?: return null
        return try {
            val method = detector.javaClass.methods.firstOrNull {
                it.name == "createEnrollIntent" || it.name == "createIntentToEnroll"
            }
            method?.invoke(detector) as? Intent
        } catch (error: Throwable) {
            Log.w(TAG, "createEnrollIntent failed", error)
            null
        }
    }

    fun destroy() {
        stopRecognition()
        val detector = detectorRef.getAndSet(null) ?: return
        runCatching {
            detector.javaClass.methods.firstOrNull { it.name == "invalidate" || it.name == "destroy" }
                ?.invoke(detector)
        }
    }

    companion object {
        private const val TAG = "VIVA_VOICE"

        // Values from AlwaysOnHotwordDetector (AOSP).
        private const val STATE_HARDWARE_UNAVAILABLE = -2
        private const val STATE_KEYPHRASE_UNSUPPORTED = -1
        private const val STATE_KEYPHRASE_UNENROLLED = 1
        private const val STATE_KEYPHRASE_ENROLLED = 2

        private fun mapAvailability(state: Int): Availability = when (state) {
            STATE_HARDWARE_UNAVAILABLE -> Availability.HARDWARE_UNAVAILABLE
            STATE_KEYPHRASE_UNSUPPORTED -> Availability.KEYPHRASE_UNSUPPORTED
            STATE_KEYPHRASE_UNENROLLED -> Availability.KEYPHRASE_UNENROLLED
            STATE_KEYPHRASE_ENROLLED -> Availability.KEYPHRASE_ENROLLED
            else -> Availability.ERROR
        }
    }
}
