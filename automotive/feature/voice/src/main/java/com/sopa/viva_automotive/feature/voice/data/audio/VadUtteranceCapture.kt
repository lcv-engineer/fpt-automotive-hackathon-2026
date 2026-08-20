package com.sopa.viva_automotive.feature.voice.data.audio

import android.content.Context
import android.util.Log
import com.viva.voice.audio.AndroidPcmSource
import com.viva.voice.audio.AudioConfig
import com.viva.voice.audio.PcmSourceAudioCapture
import com.viva.voice.audio.Utterance
import com.viva.voice.audio.VoiceSessionOutcome
import com.viva.voice.audio.captureVadSession
import com.viva.voice.trace.NanoClock
import com.viva.voice.trace.SystemNanoClock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android adapter for the single streaming capture path:
 * AudioRecord -> PcmSourceAudioCapture -> VadStreamDriver -> speech-only PCM.
 */
@Singleton
class VadUtteranceCapture @Inject constructor(
    @ApplicationContext private val context: Context,
    private val driverFactory: SileroVadDriverFactory,
) {
    suspend fun capture(
        maxWaitForSpeechMs: Long = MAX_WAIT_FOR_SPEECH_MS,
        clock: NanoClock = SystemNanoClock,
    ): Utterance {
        val audioConfig = AudioConfig.DEFAULT
        val driver = checkNotNull(driverFactory.create(clock)) {
            "Silero VAD session is unavailable"
        }
        val audioCapture = PcmSourceAudioCapture(
            source = AndroidPcmSource(audioConfig),
            clock = clock,
            config = audioConfig,
        )

        Log.i(TAG, "Silero VAD listening (maxWait=${maxWaitForSpeechMs}ms)")
        return when (
            val outcome = captureVadSession(
                audioCapture = audioCapture,
                driver = driver,
                maxWaitForSpeechMs = maxWaitForSpeechMs,
                clock = clock,
            )
        ) {
            is VoiceSessionOutcome.Speech -> {
                val speech = outcome.utterance
                Log.i(
                    TAG,
                    "Speech ended samples=${speech.pcm16.size} " +
                        "decisionLagMs=" +
                        (speech.endpointDecisionNanos - speech.acousticEndNanos) / 1_000_000L,
                )
                Utterance(
                    pcm = speech.pcm16,
                    sampleRate = speech.sampleRate,
                    startNanos = speech.speechStartNanos,
                    endNanos = speech.endpointDecisionNanos,
                    acousticEndNanos = speech.acousticEndNanos,
                    truncated = speech.truncated,
                    tooShort = speech.pcm16.size < minimumSamples(audioConfig),
                )
            }

            VoiceSessionOutcome.NoSpeech -> {
                val now = clock.nanos()
                Log.i(TAG, "No speech within ${maxWaitForSpeechMs}ms")
                Utterance(
                    pcm = ShortArray(0),
                    sampleRate = audioConfig.sampleRate,
                    startNanos = now,
                    endNanos = now,
                    acousticEndNanos = now,
                    tooShort = true,
                )
            }
        }
    }

    private fun minimumSamples(config: AudioConfig): Int =
        config.sampleRate * config.minDurationMs / 1_000

    private companion object {
        const val TAG = "VIVA_VOICE"
        const val MAX_WAIT_FOR_SPEECH_MS = 8_000L
    }
}
