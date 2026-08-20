package com.viva.voice.audio

data class VadConfig(
    val sampleRate: Int = 16_000,
    val frameSamples: Int = 512,
    val threshold: Float = 0.50f,
    val negativeThreshold: Float = 0.35f,
    val minSpeechMs: Int = 250,
    val minSilenceMs: Int = 100,
    val speechPadMs: Int = 30,
    val maxSpeechMs: Int = 15_000,
) {
    init {
        require(sampleRate == 16_000) { "Silero VAD asset supports 16 kHz PCM" }
        require(frameSamples == 512) { "Silero VAD requires 512 samples at 16 kHz" }
        require(threshold in 0f..1f) { "threshold must be between 0 and 1" }
        require(negativeThreshold in 0f..<threshold) {
            "negativeThreshold must be below threshold"
        }
        require(minSpeechMs > 0 && minSilenceMs > 0 && maxSpeechMs > 0)
        require(speechPadMs >= 0)
    }

    fun samplesFor(milliseconds: Int): Int =
        (sampleRate.toLong() * milliseconds / 1_000L).toInt()
}

interface VoiceActivityScorer {
    /** Returns a speech probability in the inclusive range 0..1. */
    fun probability(frame: ShortArray): Float

    /** Clears recurrent model state before a new recording. */
    fun reset()
}

sealed interface VadEvent {
    data class SpeechStarted(val startSample: Int) : VadEvent
    data class SpeechEnded(
        val startSample: Int,
        val endSample: Int,
        /** Mẫu nơi im lặng bắt đầu — xấp xỉ lúc người nói dứt câu. */
        val acousticEndSample: Int = 0,
    ) : VadEvent
}

data class VadSegment(
    val startSample: Int,
    val endSample: Int,
    val pcm: ShortArray,
)

/** Pure state machine for L3c threshold and endpoint tuning. */
class VadEndpointer(
    private val config: VadConfig = VadConfig(),
) {
    private var candidateStart: Int? = null
    private var activeStart: Int? = null
    private var silenceStart: Int? = null

    fun accept(probability: Float, frameStartSample: Int): List<VadEvent> {
        require(probability in 0f..1f) { "VAD probability must be between 0 and 1" }
        require(frameStartSample >= 0) { "frameStartSample must be non-negative" }

        val frameEnd = frameStartSample + config.frameSamples
        val currentStart = activeStart
        if (currentStart == null) {
            if (probability >= config.threshold) {
                val candidate = candidateStart ?: frameStartSample.also { candidateStart = it }
                if (frameEnd - candidate >= config.samplesFor(config.minSpeechMs)) {
                    val paddedStart = maxOf(0, candidate - config.samplesFor(config.speechPadMs))
                    activeStart = paddedStart
                    candidateStart = null
                    silenceStart = null
                    return listOf(VadEvent.SpeechStarted(paddedStart))
                }
            } else {
                candidateStart = null
            }
            return emptyList()
        }

        if (probability >= config.threshold) {
            silenceStart = null
        } else if (probability < config.negativeThreshold && silenceStart == null) {
            silenceStart = frameStartSample
        }

        val maxSpeechSamples = config.samplesFor(config.maxSpeechMs)
        if (frameEnd - currentStart >= maxSpeechSamples) {
            // Cắt vì quá dài, không phải vì im lặng: mốc âm học chính là chỗ cắt.
            val cut = minOf(frameEnd, currentStart + maxSpeechSamples)
            return listOf(endSpeech(cut, acousticEnd = cut))
        }

        val silence = silenceStart
        if (silence != null && frameEnd - silence >= config.samplesFor(config.minSilenceMs)) {
            val paddedEnd = minOf(frameEnd, silence + config.samplesFor(config.speechPadMs))
            return listOf(endSpeech(maxOf(paddedEnd, currentStart), acousticEnd = silence))
        }
        return emptyList()
    }

    fun flush(totalSamples: Int): VadEvent.SpeechEnded? {
        require(totalSamples >= 0)
        val start = activeStart ?: return null
        val end = maxOf(start, totalSamples)
        reset()
        // flush() là cắt cưỡng bức khi hết audio, không có khoảng im lặng nào
        // để suy ra mốc âm học — dùng luôn điểm cắt.
        return VadEvent.SpeechEnded(start, end, acousticEndSample = end)
    }

    fun reset() {
        candidateStart = null
        activeStart = null
        silenceStart = null
    }

    private fun endSpeech(endSample: Int, acousticEnd: Int): VadEvent.SpeechEnded {
        val start = checkNotNull(activeStart)
        reset()
        return VadEvent.SpeechEnded(start, endSample, acousticEndSample = acousticEnd)
    }
}

/** Runs the 512-sample scorer sequentially and returns padded speech-only PCM slices. */
class VadSegmenter(
    private val scorer: VoiceActivityScorer,
    private val config: VadConfig = VadConfig(),
) {
    fun segment(pcm: ShortArray): List<VadSegment> {
        if (pcm.isEmpty()) return emptyList()
        scorer.reset()
        val endpointer = VadEndpointer(config)
        val boundaries = mutableListOf<VadEvent.SpeechEnded>()

        var frameStart = 0
        while (frameStart < pcm.size) {
            val frame = ShortArray(config.frameSamples)
            val copied = minOf(config.frameSamples, pcm.size - frameStart)
            pcm.copyInto(frame, endIndex = frameStart + copied, destinationOffset = 0, startIndex = frameStart)
            endpointer.accept(scorer.probability(frame), frameStart).forEach { event ->
                if (event is VadEvent.SpeechEnded) boundaries += event
            }
            frameStart += config.frameSamples
        }
        endpointer.flush(pcm.size)?.let { boundaries += it }

        return boundaries.mapNotNull { boundary ->
            val start = boundary.startSample.coerceIn(0, pcm.size)
            val end = boundary.endSample.coerceIn(start, pcm.size)
            if (end == start) null else VadSegment(start, end, pcm.copyOfRange(start, end))
        }
    }
}
