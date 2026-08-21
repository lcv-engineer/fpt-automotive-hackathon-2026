package com.sopa.viva_automotive.feature.voice.via

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.viva.voice.audio.AndroidPcmSource
import com.viva.voice.audio.AudioConfig
import com.viva.voice.audio.PcmSourceAudioCapture
import com.viva.voice.hotword.HotwordGate
import com.viva.voice.hotword.HotwordMetrics
import com.viva.voice.hotword.SoftwareHotwordDetector
import com.viva.voice.trace.SystemNanoClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

/**
 * Always-on software KWS that owns the mic only while armed and [HotwordGate]
 * is not paused. Yields before session capture / TTS.
 */
class SoftwareHotwordEngine(
    private val context: Context,
    private val metrics: HotwordMetrics,
    private val onDetected: (latencyHintMs: Long) -> Unit,
) {
    private val detector = SoftwareHotwordDetector()
    private var job: Job? = null

    val hasTemplate: Boolean get() = detector.hasTemplate

    fun loadTemplateFromDisk(): Boolean {
        val samples = HotwordTemplateStore.load(context) ?: return false
        detector.setTemplate(samples)
        Log.i(TAG, "Loaded software hotword template samples=${samples.size}")
        return true
    }

    fun saveTemplate(pcm16: ShortArray) {
        HotwordTemplateStore.save(context, pcm16)
        detector.setTemplate(pcm16)
        Log.i(TAG, "Saved software hotword template samples=${pcm16.size}")
    }

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        if (!detector.hasTemplate && !loadTemplateFromDisk()) {
            Log.w(TAG, "Software hotword armed without template — enroll “Viva ơi” first")
        }
        metrics.markArmed(SystemClock.elapsedRealtime())
        job = scope.launch(Dispatchers.IO) {
            Log.i(TAG, "Software hotword listening")
            while (isActive) {
                if (HotwordGate.isPaused || !detector.hasTemplate) {
                    kotlinx.coroutines.delay(200)
                    continue
                }
                val capture = PcmSourceAudioCapture(
                    source = AndroidPcmSource(AudioConfig.DEFAULT),
                    clock = SystemNanoClock,
                )
                try {
                    capture.frames().collect { frame ->
                        if (HotwordGate.isPaused) {
                            throw MicYield()
                        }
                        val match = detector.accept(frame.samples)
                        if (match != null) {
                            val latencyHint = (frame.samples.size * 1_000L) / frame.sampleRate
                            Log.i(
                                TAG,
                                "Software hotword matched score=%.3f rms=%.3f"
                                    .format(match.score, match.rms),
                            )
                            onDetected(latencyHint)
                            throw MicYield()
                        }
                    }
                } catch (_: MicYield) {
                    detector.reset()
                } catch (error: Throwable) {
                    Log.w(TAG, "Software hotword capture error", error)
                    kotlinx.coroutines.delay(500)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        metrics.markDisarmed(SystemClock.elapsedRealtime())
        detector.reset()
        Log.i(TAG, "Software hotword stopped")
    }

    private class MicYield : RuntimeException()

    companion object {
        private const val TAG = "VIVA_VOICE"
    }
}
