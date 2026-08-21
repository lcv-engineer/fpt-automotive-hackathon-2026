package com.viva.voice.audio

import com.viva.voice.trace.NanoClock

/**
 * What one press of the talk button produced.
 *
 * [startNanos] / [endNanos] are monotonic readings, fed to
 * `LatencyTrace.markAt(Stage.SPEECH_START, …)` by the pipeline rather than
 * marked here - the recorder deliberately knows nothing about tracing, so
 * both can be tested apart.
 */
/**
 * Khoảng trễ giữa lúc người nói dứt câu và lúc VAD quyết định endpoint.
 *
 * `SpeechEnded` chỉ bắn ra sau khi đã trôi hết `minSilenceMs`, nên đồng hồ đọc
 * tại thời điểm đó không phải lúc dứt câu. Số mẫu audio giữa mốc âm học và
 * frame đang xử lý chính là khoảng trễ đó, quy về nanosecond.
 *
 * Kẹp về 0 khi âm nếu mốc pad vượt frame hiện tại: thà mất độ chính xác còn hơn
 * lùi ngày một mốc trace và đẻ ra độ trễ âm.
 */
fun endpointLagNanos(
    acousticEndSample: Int,
    currentFrameEndSample: Int,
    sampleRate: Int,
): Long {
    require(sampleRate > 0) { "sampleRate must be positive" }
    val distance = (currentFrameEndSample - acousticEndSample).toLong()
    if (distance <= 0L) return 0L
    return distance * 1_000_000_000L / sampleRate
}

data class Utterance(
    val pcm: ShortArray,
    val sampleRate: Int,
    val startNanos: Long,
    val endNanos: Long,
    /**
     * Ước lượng lúc người nói **dứt câu**, sớm hơn [endNanos] đúng bằng khoảng
     * chờ im lặng của VAD. Mặc định bằng [endNanos] cho đường push-to-talk,
     * nơi người dùng thả nút nên không có khoảng chờ nào.
     */
    val acousticEndNanos: Long = endNanos,
    /** Hit [AudioConfig.maxDurationMs]; the tail was dropped. */
    val truncated: Boolean = false,
    /** Shorter than [AudioConfig.minDurationMs]; a mis-tap, do not send to ASR. */
    val tooShort: Boolean = false,
) {
    val durationMs: Int get() = (pcm.size * 1000L / sampleRate).toInt()

    /** False for a mis-tap, so the caller has one thing to check before ASR. */
    val isUsable: Boolean get() = !tooShort && pcm.isNotEmpty()

    // ShortArray is an array: the generated data-class equals/hashCode would
    // compare by identity and quietly make every assertion on Utterance a
    // reference check. Both are spelled out so tests mean what they read.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Utterance) return false
        return pcm.contentEquals(other.pcm) &&
            sampleRate == other.sampleRate &&
            startNanos == other.startNanos &&
            endNanos == other.endNanos &&
            acousticEndNanos == other.acousticEndNanos &&
            truncated == other.truncated &&
            tooShort == other.tooShort
    }

    override fun hashCode(): Int {
        var result = pcm.contentHashCode()
        result = 31 * result + sampleRate
        result = 31 * result + startNanos.hashCode()
        result = 31 * result + endNanos.hashCode()
        result = 31 * result + acousticEndNanos.hashCode()
        result = 31 * result + truncated.hashCode()
        result = 31 * result + tooShort.hashCode()
        return result
    }
}

/**
 * Push-to-talk capture: record while the button is held (L3a).
 *
 * Until Silero VAD lands (L3b) the button IS the endpointer - press is
 * `speech_start`, release is `speech_end`. When VAD arrives it replaces the
 * release edge with a detected endpoint; this class stays as the fallback
 * for a noisy cabin, where hold-to-talk beats any VAD.
 *
 * Runs the read loop on the calling thread. Call it from a background
 * thread: [PcmSource.read] blocks, and blocking the main thread on the
 * microphone is how an AAOS app earns an ANR in front of the judges.
 */
class PushToTalkRecorder(
    private val source: PcmSource,
    private val clock: NanoClock,
    private val config: AudioConfig = AudioConfig.DEFAULT,
) {

    /**
     * Records until [isHeld] returns false or the duration cap is hit.
     *
     * [isHeld] is polled once per chunk (~100ms), which is the button's
     * release latency. The microphone is always released, including when
     * [PcmSource.read] throws, so a crash mid-utterance does not leave the
     * mic held open against the next turn.
     */
    fun record(isHeld: () -> Boolean): Utterance {
        val maxSamples = config.sampleRate.toLong() * config.maxDurationMs / 1000L
        val minSamples = config.sampleRate.toLong() * config.minDurationMs / 1000L

        val captured = ArrayList<ShortArray>()
        var total = 0L
        var truncated = false

        val startNanos = clock.nanos()
        source.start()
        try {
            val buffer = ShortArray(config.chunkSamples)
            while (isHeld()) {
                val n = source.read(buffer)
                if (n <= 0) break
                val room = (maxSamples - total).toInt()
                if (n >= room) {
                    captured += buffer.copyOf(room)
                    total += room
                    truncated = true
                    break
                }
                captured += buffer.copyOf(n)
                total += n
            }
        } finally {
            source.stop()
        }
        val endNanos = clock.nanos()

        val pcm = ShortArray(total.toInt())
        var offset = 0
        for (chunk in captured) {
            System.arraycopy(chunk, 0, pcm, offset, chunk.size)
            offset += chunk.size
        }

        return Utterance(
            pcm = pcm,
            sampleRate = config.sampleRate,
            startNanos = startNanos,
            endNanos = endNanos,
            truncated = truncated,
            tooShort = total < minSamples,
        )
    }
}
