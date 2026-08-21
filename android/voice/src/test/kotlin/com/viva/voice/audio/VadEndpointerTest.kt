package com.viva.voice.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VadEndpointerTest {
    private val tuned = VadConfig(
        threshold = 0.50f,
        negativeThreshold = 0.35f,
        minSpeechMs = 64,
        minSilenceMs = 96,
        speechPadMs = 32,
    )

    @Test
    fun `speech starts after minimum speech and ends after sustained silence`() {
        val endpointer = VadEndpointer(tuned)
        val events = probabilities(0.1f, 0.7f, 0.8f, 0.9f, 0.2f, 0.1f, 0.1f)
            .flatMapIndexed { index, probability ->
                endpointer.accept(probability, index * tuned.frameSamples)
            }

        assertEquals(
            listOf(
                VadEvent.SpeechStarted(startSample = 0),
                VadEvent.SpeechEnded(
                    startSample = 0,
                    endSample = 2_560,
                    acousticEndSample = 2_048,
                ),
            ),
            events,
        )
    }

    /**
     * `endSample` là mốc đã cộng pad, và event chỉ bắn ra sau khi đã trôi hết
     * `minSilenceMs`. Cả hai đều KHÔNG phải lúc tài xế dứt câu. Trace đang đóng
     * dấu `speech_end` tại thời điểm bắn event, nên toàn bộ `minSilenceMs` nằm
     * ngoài số độ trễ công bố. Endpointer phải trả thêm mốc âm học để tính lại.
     */
    @Test
    fun `speech ended reports where silence actually began`() {
        val endpointer = VadEndpointer(tuned)
        val ended = probabilities(0.1f, 0.7f, 0.8f, 0.9f, 0.2f, 0.1f, 0.1f)
            .flatMapIndexed { index, probability ->
                endpointer.accept(probability, index * tuned.frameSamples)
            }
            .filterIsInstance<VadEvent.SpeechEnded>()
            .single()

        // Im lặng bắt đầu ở frame 4 (sample 2048); pad đẩy endSample tới 2560.
        assertEquals(2_048, ended.acousticEndSample)
        assertEquals(2_560, ended.endSample)
        assertTrue("mốc âm học phải sớm hơn mốc đã pad", ended.acousticEndSample < ended.endSample)
    }

    @Test
    fun `hysteresis ignores a noisy probability dip`() {
        val endpointer = VadEndpointer(tuned)
        val events = probabilities(0.7f, 0.8f, 0.4f, 0.75f, 0.2f, 0.1f, 0.1f)
            .flatMapIndexed { index, probability ->
                endpointer.accept(probability, index * tuned.frameSamples)
            }

        assertEquals(1, events.count { it is VadEvent.SpeechStarted })
        assertEquals(1, events.count { it is VadEvent.SpeechEnded })
    }

    @Test
    fun `raising the tuned threshold rejects marginal cabin noise`() {
        val endpointer = VadEndpointer(tuned.copy(threshold = 0.60f))
        val events = probabilities(0.55f, 0.55f, 0.55f, 0.1f, 0.1f, 0.1f)
            .flatMapIndexed { index, probability ->
                endpointer.accept(probability, index * tuned.frameSamples)
            }

        assertTrue(events.isEmpty())
    }

    @Test
    fun `segmenter cuts PCM at the detected padded boundaries`() {
        val scorer = QueueVadScorer(
            probabilities(0.1f, 0.7f, 0.8f, 0.9f, 0.2f, 0.1f, 0.1f),
        )
        val segmenter = VadSegmenter(scorer, tuned)
        val pcm = ShortArray(tuned.frameSamples * 7) { it.toShort() }

        val segments = segmenter.segment(pcm)

        assertEquals(1, segments.size)
        assertEquals(0, segments.single().startSample)
        assertEquals(2_560, segments.single().endSample)
        assertEquals(pcm.copyOfRange(0, 2_560).toList(), segments.single().pcm.toList())
    }

    private fun probabilities(vararg values: Float): List<Float> = values.toList()

    private class QueueVadScorer(
        probabilities: List<Float>,
    ) : VoiceActivityScorer {
        private val original = probabilities.toList()
        private var remaining = ArrayDeque(original)

        override fun probability(frame: ShortArray): Float = remaining.removeFirst()

        override fun reset() {
            remaining = ArrayDeque(original)
        }
    }
}
