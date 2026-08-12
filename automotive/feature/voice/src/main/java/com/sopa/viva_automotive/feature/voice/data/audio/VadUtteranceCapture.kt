package com.sopa.viva_automotive.feature.voice.data.audio

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.viva.voice.audio.AndroidPcmSource
import com.viva.voice.audio.AudioConfig
import com.viva.voice.audio.SileroVadOnnxScorer
import com.viva.voice.audio.Utterance
import com.viva.voice.audio.VadConfig
import com.viva.voice.audio.VadEndpointer
import com.viva.voice.audio.VadEvent
import com.viva.voice.trace.NanoClock
import com.viva.voice.trace.SystemNanoClock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Long's mic path: AudioRecord → Silero VAD endpointer → speech-only PCM for AsrClient.
 */
@Singleton
class VadUtteranceCapture @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun capture(
        maxWaitForSpeechMs: Long = MAX_WAIT_FOR_SPEECH_MS,
        clock: NanoClock = SystemNanoClock,
    ): Utterance {
        val audioConfig = AudioConfig.DEFAULT
        // Defaults in voice-core (100ms silence / 30ms pad) cut Vietnamese cabin
        // commands mid-phrase — logs showed 0.3–0.6s clips → "bệt." / "hòa.".
        val vadConfig = VadConfig(
            minSpeechMs = 300,
            minSilenceMs = 800,
            speechPadMs = 300,
            maxSpeechMs = 12_000,
        )
        val source = AndroidPcmSource(audioConfig)
        val scorer = SileroVadOnnxScorer(context)
        val endpointer = VadEndpointer(vadConfig)
        Log.i(
            TAG,
            "VAD config silence=${vadConfig.minSilenceMs}ms pad=${vadConfig.speechPadMs}ms",
        )
        val pcm = ArrayList<Short>(audioConfig.sampleRate * 4)
        val frame = ShortArray(vadConfig.frameSamples)
        val readBuffer = ShortArray(vadConfig.frameSamples)

        source.start()
        scorer.reset()
        Log.i(TAG, "Silero VAD listening (maxWait=${maxWaitForSpeechMs}ms)")
        val wallStart = SystemClock.elapsedRealtime()
        var startNanos = clock.nanos()
        var speechStarted = false
        var frameStartSample = 0
        var pending = 0

        try {
            while (true) {
                val elapsed = SystemClock.elapsedRealtime() - wallStart
                if (!speechStarted && elapsed >= maxWaitForSpeechMs) {
                    Log.i(TAG, "No speech within ${maxWaitForSpeechMs}ms")
                    break
                }
                if (speechStarted &&
                    frameStartSample >= vadConfig.samplesFor(vadConfig.maxSpeechMs)
                ) {
                    Log.i(TAG, "Hit max speech duration at sample $frameStartSample")
                    break
                }

                val read = source.read(readBuffer)
                if (read < 0) break
                if (read == 0) continue

                var offset = 0
                while (offset < read) {
                    val copy = minOf(vadConfig.frameSamples - pending, read - offset)
                    System.arraycopy(readBuffer, offset, frame, pending, copy)
                    pending += copy
                    offset += copy
                    if (pending < vadConfig.frameSamples) break

                    for (i in 0 until vadConfig.frameSamples) {
                        pcm.add(frame[i])
                    }
                    val events = endpointer.accept(scorer.probability(frame), frameStartSample)
                    pending = 0
                    frameStartSample += vadConfig.frameSamples

                    events.forEach { event ->
                        when (event) {
                            is VadEvent.SpeechStarted -> {
                                speechStarted = true
                                startNanos = clock.nanos()
                                Log.i(TAG, "Speech started at sample ${event.startSample}")
                            }
                            is VadEvent.SpeechEnded -> {
                                val endNanos = clock.nanos()
                                val segment = slice(pcm, event.startSample, event.endSample)
                                Log.i(
                                    TAG,
                                    "Speech ended samples=${segment.size} " +
                                        "[${event.startSample}, ${event.endSample})",
                                )
                                return Utterance(
                                    pcm = segment,
                                    sampleRate = audioConfig.sampleRate,
                                    startNanos = startNanos,
                                    endNanos = endNanos,
                                    truncated = false,
                                    tooShort = segment.size <
                                        audioConfig.sampleRate * audioConfig.minDurationMs / 1000,
                                )
                            }
                        }
                    }
                }
            }

            val flush = endpointer.flush(pcm.size)
            val endNanos = clock.nanos()
            if (flush != null) {
                val segment = slice(pcm, flush.startSample, flush.endSample)
                Log.i(
                    TAG,
                    "Flush speech samples=${segment.size} " +
                        "[${flush.startSample}, ${flush.endSample})",
                )
                return Utterance(
                    pcm = segment,
                    sampleRate = audioConfig.sampleRate,
                    startNanos = startNanos,
                    endNanos = endNanos,
                    truncated = true,
                    tooShort = segment.size <
                        audioConfig.sampleRate * audioConfig.minDurationMs / 1000,
                )
            }

            val all = pcm.toShortArray()
            Log.i(
                TAG,
                "End capture speechStarted=$speechStarted samples=${all.size}",
            )
            return Utterance(
                pcm = all,
                sampleRate = audioConfig.sampleRate,
                startNanos = startNanos,
                endNanos = endNanos,
                truncated = false,
                tooShort = !speechStarted ||
                    all.size < audioConfig.sampleRate * audioConfig.minDurationMs / 1000,
            )
        } finally {
            runCatching { source.stop() }
            runCatching { scorer.close() }
        }
    }

    private fun slice(pcm: ArrayList<Short>, start: Int, end: Int): ShortArray {
        val from = start.coerceIn(0, pcm.size)
        val to = end.coerceIn(from, pcm.size)
        return ShortArray(to - from) { index -> pcm[from + index] }
    }

    private companion object {
        const val TAG = "VIVA_VOICE"
        const val MAX_WAIT_FOR_SPEECH_MS = 8_000L
    }
}
