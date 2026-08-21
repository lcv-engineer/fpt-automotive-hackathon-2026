package com.viva.voice.audio

import com.viva.voice.trace.NanoClock
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

class VadSessionCaptureTest {
    private val config = VadConfig(
        minSpeechMs = 64,
        minSilenceMs = 96,
        speechPadMs = 32,
    )

    private class StepClock : NanoClock {
        private var now = -32_000_000L
        override fun nanos(): Long {
            now += 32_000_000L
            return now
        }
    }

    private class ScriptedScorer(private val values: List<Float>) : VoiceActivityScorer {
        private var index = 0
        override fun probability(frame: ShortArray): Float = values.getOrElse(index++) { 0f }
        override fun reset() {
            index = 0
        }
    }

    private fun capture(frameCount: Int, accepted: MutableList<Int>? = null): AudioCapture =
        object : AudioCapture {
            override fun frames() = flow {
                repeat(frameCount) { index ->
                    accepted?.add(index)
                    emit(
                        PcmFrame(
                            samples = ShortArray(config.frameSamples),
                            startNanos = index * 32_000_000L,
                            startSample = index * config.frameSamples.toLong(),
                        ),
                    )
                }
            }
        }

    @Test
    fun `no speech timeout stops collecting and does not manufacture an utterance`() = runTest {
        val accepted = mutableListOf<Int>()
        val outcome = captureVadSession(
            audioCapture = capture(frameCount = 20, accepted = accepted),
            driver = VadStreamDriver(ScriptedScorer(List(20) { 0.05f }), config),
            maxWaitForSpeechMs = 64,
            clock = StepClock(),
        )

        assertSame(VoiceSessionOutcome.NoSpeech, outcome)
        assertEquals(listOf(0, 1), accepted)
    }

    @Test
    fun `speech collection stops at the first completed streaming utterance`() = runTest {
        val probabilities = listOf(0.1f, 0.7f, 0.8f, 0.9f, 0.2f, 0.1f, 0.1f, 0.9f)
        val outcome = captureVadSession(
            audioCapture = capture(frameCount = probabilities.size),
            driver = VadStreamDriver(ScriptedScorer(probabilities), config),
            maxWaitForSpeechMs = 1_000,
            clock = StepClock(),
        )

        val utterance = (outcome as VoiceSessionOutcome.Speech).utterance
        assertEquals(128_000_000L, utterance.acousticEndNanos)
        assertEquals(224_000_000L, utterance.endpointDecisionNanos)
    }

    @Test
    fun `utterance equality includes the acoustic endpoint`() {
        val first = Utterance(
            pcm = shortArrayOf(1),
            sampleRate = 16_000,
            startNanos = 0,
            endNanos = 1_000,
            acousticEndNanos = 200,
        )

        assertNotEquals(first, first.copy(acousticEndNanos = 300))
    }
}
