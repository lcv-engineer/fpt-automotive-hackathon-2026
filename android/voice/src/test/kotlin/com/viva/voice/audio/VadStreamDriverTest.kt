package com.viva.voice.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VadStreamDriverTest {

    private val tuned = VadConfig(
        threshold = 0.50f,
        negativeThreshold = 0.35f,
        minSpeechMs = 64,
        minSilenceMs = 96,
        speechPadMs = 32,
    )

    /** Một khung 32ms, mọi mẫu mang [value] để nhận ra nó trong PCM cắt ra. */
    private fun frame(index: Int, value: Int) = PcmFrame(
        samples = ShortArray(tuned.frameSamples) { value.toShort() },
        sampleRate = tuned.sampleRate,
        startNanos = index * 32_000_000L,
        startSample = index * tuned.frameSamples.toLong(),
    )

    private class ScriptedScorer(private val probabilities: List<Float>) : VoiceActivityScorer {
        private var index = 0
        var resets = 0
            private set

        override fun probability(frame: ShortArray): Float =
            probabilities.getOrElse(index++) { 0f }

        override fun reset() {
            index = 0
            resets++
        }
    }

    /** Im lặng, rồi tiếng nói, rồi im lặng — endpoint rơi vào khung thứ bảy. */
    private val oneSentence = listOf(0.1f, 0.7f, 0.8f, 0.9f, 0.2f, 0.1f, 0.1f)

    private fun driveOneSentence(driver: VadStreamDriver): List<VadStreamEvent> =
        oneSentence.indices.mapNotNull { index -> driver.accept(frame(index, index + 1)) }

    @Test
    fun `pre-roll keeps the audio recorded before VAD was sure it was speech`() {
        val driver = VadStreamDriver(ScriptedScorer(oneSentence), tuned)

        val ended = driveOneSentence(driver).filterIsInstance<VadStreamEvent.SpeechEnded>().single()

        // Khung 0 có xác suất 0.1 — VAD chưa coi là tiếng nói khi nó đi qua. Nó vẫn
        // phải nằm trong utterance, nếu không "hạ" trong "Viva ơi, hạ điều hòa" là
        // chữ bị mất.
        assertEquals(2_560, ended.utterance.pcm16.size)
        assertTrue(ended.utterance.pcm16.take(512).all { it == 1.toShort() })
        assertTrue(ended.utterance.pcm16.takeLast(512).all { it == 5.toShort() })
    }

    @Test
    fun `speech_start is back-dated to the onset sample, not to when VAD noticed`() {
        val driver = VadStreamDriver(ScriptedScorer(oneSentence), tuned)

        val started = driveOneSentence(driver)
            .filterIsInstance<VadStreamEvent.SpeechStarted>()
            .single()

        // VAD chỉ chốt ở khung 2 (t=64ms), nhưng biên thật là mẫu 0 (t=0). Lấy đồng hồ
        // tại lúc chốt sẽ thổi phồng mọi độ trễ phía sau đúng bằng cửa sổ VAD.
        assertEquals(0L, started.startSample)
        assertEquals(0L, started.startNanos)
    }

    @Test
    fun `speech_end nanos match the endpoint sample`() {
        val driver = VadStreamDriver(ScriptedScorer(oneSentence), tuned)

        val ended = driveOneSentence(driver).filterIsInstance<VadStreamEvent.SpeechEnded>().single()

        // Mẫu 2560 ở 16 kHz = 160ms, dù endpoint chỉ được phát ra ở khung 6 (t=192ms).
        assertEquals(160_000_000L, ended.utterance.speechEndNanos)
        assertEquals(0L, ended.utterance.speechStartNanos)
        assertEquals(160, ended.utterance.durationMs)
    }

    @Test
    fun `a session emits exactly one utterance and then ignores further frames`() {
        val driver = VadStreamDriver(ScriptedScorer(oneSentence + listOf(0.9f, 0.9f, 0.9f)), tuned)
        driveOneSentence(driver)

        val afterEndpoint = (7..9).mapNotNull { index -> driver.accept(frame(index, index + 1)) }

        assertTrue(afterEndpoint.isEmpty())
        assertNull(driver.flush())
    }

    @Test
    fun `silence alone never produces a segment for ASR`() {
        val driver = VadStreamDriver(ScriptedScorer(List(8) { 0.05f }), tuned)

        val events = (0 until 8).mapNotNull { index -> driver.accept(frame(index, 1)) }

        assertTrue(events.isEmpty())
        assertNull(driver.flush())
    }

    @Test
    fun `a source that ends mid-speech still yields the audio captured so far`() {
        // Không có khoảng lặng kết thúc: nút được nhả, hoặc chạm trần thời lượng.
        val driver = VadStreamDriver(ScriptedScorer(List(6) { 0.9f }), tuned)
        (0 until 6).forEach { index -> driver.accept(frame(index, index + 1)) }

        val flushed = driver.flush()

        assertEquals(3_072, flushed?.utterance?.pcm16?.size)
    }

    @Test
    fun `reset clears the recurrent scorer state before the next session`() {
        val scorer = ScriptedScorer(oneSentence)
        val driver = VadStreamDriver(scorer, tuned)
        driveOneSentence(driver)

        driver.reset()
        val second = driveOneSentence(driver)

        // Silero mang state giữa các khung; không xóa thì lượt sau bị lượt trước làm lệch.
        assertEquals(1, scorer.resets)
        assertEquals(1, second.count { it is VadStreamEvent.SpeechEnded })
    }

    @Test
    fun `out-of-order frames fail loudly instead of shifting the time axis`() {
        val driver = VadStreamDriver(ScriptedScorer(oneSentence), tuned)
        driver.accept(frame(0, 1))

        assertThrows(IllegalArgumentException::class.java) {
            driver.accept(frame(2, 3))
        }
    }
}
