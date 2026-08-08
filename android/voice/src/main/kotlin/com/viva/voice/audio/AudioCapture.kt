package com.viva.voice.audio

import com.viva.voice.trace.NanoClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Điểm vào duy nhất của âm thanh trong pipeline (28-PIPELINE §0, quyết định 1).
 *
 * Trước bản này mỗi thành phần tự mở microphone của nó: `VoskSpeechRecognitionEngine`
 * dựng `AudioRecord` riêng, `PushToTalkRecorder` dựng `PcmSource` riêng. Hai
 * `AudioRecord` sống song song trên AAOS không phải là "hai luồng audio" — cái mở sau
 * bị từ chối, hoặc tệ hơn, cái mở trước im lặng mất mẫu. Từ đây chỉ [AudioCapture] sở
 * hữu mic; VAD, ASR on-device và ASR remote đều nhận cùng một [PcmFrame].
 */
interface AudioCapture {
    /**
     * Mở microphone khi flow được collect và đóng lại khi collect kết thúc hoặc bị
     * hủy. Mỗi phần tử là **đúng** [AudioConfig.frameSamples] mẫu, để scorer 512 mẫu
     * của Silero không phải tự đệm lại.
     */
    fun frames(): Flow<PcmFrame>
}

/**
 * Một khung PCM16 16 kHz mono kèm dấu thời gian đơn điệu (28-PIPELINE §2.2, §4).
 *
 * [startNanos] là thời điểm **mẫu đầu tiên** của khung được đọc, lấy từ [NanoClock]
 * (elapsedRealtime), không phải wall-clock. Nó tồn tại để `speech_start`/`speech_end`
 * được back-date về đúng biên tiếng nói thay vì về lúc VAD kịp nhận ra — xem
 * `LatencyTrace.markAt`.
 */
class PcmFrame(
    val samples: ShortArray,
    val sampleRate: Int = 16_000,
    val startNanos: Long,
    /** Chỉ số mẫu đầu khung tính từ lúc mở session. Trục thời gian của VAD. */
    val startSample: Long,
) {
    init {
        require(samples.isNotEmpty()) { "PcmFrame must carry samples" }
        require(sampleRate > 0) { "sampleRate must be positive" }
        require(startSample >= 0) { "startSample must be non-negative" }
    }

    val endSample: Long get() = startSample + samples.size
}

/** Cách một [VoiceSessionOutcome] được kích hoạt. Sau trigger, hai đường đi chung. */
enum class Trigger { PUSH_TO_TALK, WAKE_WORD }

/**
 * Đoạn tiếng nói đã được đóng biên, kèm pre-roll (28-PIPELINE §4).
 *
 * [speechStartNanos]/[speechEndNanos] là thời điểm thật của biên tiếng nói, đã
 * back-date; chúng là hai mốc `speech_start`/`speech_end` trên `VIVA_TRACE`.
 */
class AudioUtterance(
    val pcm16: ShortArray,
    val sampleRate: Int,
    val speechStartNanos: Long,
    val speechEndNanos: Long,
    val trigger: Trigger,
) {
    val durationMs: Int get() = (pcm16.size * 1_000L / sampleRate).toInt()
}

/** Vì sao một lượt nghe kết thúc. Lượt không có tiếng nói **không** được gửi tới ASR. */
sealed interface VoiceSessionOutcome {
    data class Speech(val utterance: AudioUtterance) : VoiceSessionOutcome

    /** VAD không thấy tiếng nói nào cho tới khi hết thời gian chờ. */
    data object NoSpeech : VoiceSessionOutcome
}

/**
 * [AudioCapture] duy nhất của app, cắt [PcmSource] thành khung cố định.
 *
 * `AudioRecord.read` trả về số mẫu tùy ý theo kích thước buffer của thiết bị, còn
 * Silero VAD chỉ chấp nhận đúng 512 mẫu. Việc gộp/cắt nằm ở đây một lần, thay vì mỗi
 * consumer tự đệm lại và tự tính lệch một trục thời gian khác nhau.
 *
 * [maxDurationMs] là chốt an toàn giống [AudioConfig.maxDurationMs]: một session bị
 * kẹt không được giữ mic vô hạn.
 */
class PcmSourceAudioCapture(
    private val source: PcmSource,
    private val clock: NanoClock,
    private val config: AudioConfig = AudioConfig.DEFAULT,
    private val frameSamples: Int = VadConfig().frameSamples,
) : AudioCapture {

    override fun frames(): Flow<PcmFrame> = flow {
        val maxSamples = config.sampleRate.toLong() * config.maxDurationMs / 1_000L
        // Đọc theo chunk của thiết bị rồi phát ra theo khung của VAD: hai kích thước
        // này không cần bằng nhau và trên thực tế không bằng nhau.
        val readBuffer = ShortArray(maxOf(config.chunkSamples, frameSamples))
        val pending = ShortArray(frameSamples)
        var pendingCount = 0
        var emittedSamples = 0L

        source.start()
        try {
            while (emittedSamples < maxSamples) {
                val read = source.read(readBuffer)
                if (read <= 0) break
                // `readAtNanos` đo lúc chunk **về tới đây**; mẫu đầu của chunk đã được
                // thu trước đó đúng bằng độ dài chunk. Trừ ngược lại thay vì gán thẳng,
                // nếu không mọi khung đều bị trễ một chunk và speech_start trễ theo.
                val readAtNanos = clock.nanos()
                val chunkStartNanos = readAtNanos - samplesToNanos(read.toLong())

                var offset = 0
                while (offset < read && emittedSamples < maxSamples) {
                    val take = minOf(frameSamples - pendingCount, read - offset)
                    readBuffer.copyInto(
                        pending,
                        destinationOffset = pendingCount,
                        startIndex = offset,
                        endIndex = offset + take,
                    )
                    pendingCount += take
                    offset += take
                    if (pendingCount == frameSamples) {
                        emit(
                            PcmFrame(
                                samples = pending.copyOf(),
                                sampleRate = config.sampleRate,
                                startNanos = chunkStartNanos +
                                    samplesToNanos((offset - frameSamples).toLong()),
                                startSample = emittedSamples,
                            ),
                        )
                        emittedSamples += frameSamples
                        pendingCount = 0
                    }
                }
            }
        } finally {
            // Chạy cả khi collector hủy giữa chừng. Mic bị bỏ quên là lý do lượt sau
            // không mở được AudioRecord.
            source.stop()
        }
    }

    private fun samplesToNanos(samples: Long): Long =
        samples * 1_000_000_000L / config.sampleRate
}
