package com.viva.voice.audio

/** Biên tiếng nói mà [VadStreamDriver] phát ra trong lúc mic đang chạy. */
sealed interface VadStreamEvent {
    /**
     * Đã xác nhận có tiếng nói. [startNanos] đã được **back-date** về đúng mẫu bắt
     * đầu, không phải thời điểm VAD kịp nhận ra — dùng với `LatencyTrace.markAt`.
     */
    data class SpeechStarted(val startSample: Long, val startNanos: Long) : VadStreamEvent

    /** Endpoint. Mang theo đúng một [AudioUtterance] đã cắt kèm pre-roll. */
    data class SpeechEnded(val utterance: AudioUtterance) : VadStreamEvent
}

/**
 * Driver streaming cho [VadEndpointer] (28-PIPELINE §2.3, §8 P0.3).
 *
 * [VadEndpointer] là máy trạng thái thuần trên chuỗi xác suất; [VadSegmenter] chạy nó
 * trên một mảng PCM đã thu xong. Cả hai đều không dùng được khi mic đang chạy: lúc
 * VAD kết luận "có tiếng nói", phần âm đầu của câu đã trôi qua từ vài trăm mili-giây
 * trước. Lớp này giữ **circular pre-roll buffer** để lấy lại đoạn đó, nên
 * *"Viva ơi, hạ điều hòa xuống 24 độ"* nói liền mạch không bị mất chữ "hạ".
 *
 * Năm việc §2.3 yêu cầu, theo đúng thứ tự trong [accept]:
 *  1. giữ circular pre-roll buffer;
 *  2. cấp frame tuần tự cho scorer (Silero có recurrent state — đảo thứ tự là sai);
 *  3. mark `speech_start`/`speech_end` theo sample timestamp;
 *  4. phát **đúng một** [AudioUtterance] sau endpoint;
 *  5. [reset] xóa recurrent state khi đóng session.
 *
 * Không thread-safe: một session được lái bởi một coroutine, giống [com.viva.voice.trace.LatencyTrace].
 */
class VadStreamDriver(
    private val scorer: VoiceActivityScorer,
    private val config: VadConfig = VadConfig(),
    private val trigger: Trigger = Trigger.PUSH_TO_TALK,
    /**
     * Bao nhiêu audio giữ lại trước khi tiếng nói được xác nhận.
     *
     * Phải phủ được `minSpeechMs + speechPadMs` (chỗ [VadEndpointer] nhìn lại) và,
     * với wake-word, cả cụm đánh thức. 500ms = 16KB ở PCM16 16 kHz — rẻ hơn nhiều so
     * với một lượt bị mất âm đầu.
     */
    preRollMs: Int = 500,
) {
    private val preRollSamples: Int =
        maxOf(config.samplesFor(preRollMs), config.samplesFor(config.minSpeechMs + config.speechPadMs)) +
            config.frameSamples

    private val preRoll = ShortArray(preRollSamples)
    private var preRollWrite = 0

    private var endpointer = VadEndpointer(config)
    private var totalSamples = 0L
    private var lastFrameStartSample = 0L
    private var lastFrameStartNanos = 0L

    /** Đã phát utterance của session này chưa. Xem quy tắc 4. */
    private var closed = false

    private var speech: ShortArray? = null
    private var speechCount = 0
    private var speechStartSample = 0L

    /**
     * Nạp một khung và trả về biên nếu khung này đóng/mở một đoạn tiếng nói.
     *
     * Sau khi đã phát [VadStreamEvent.SpeechEnded], mọi khung tiếp theo bị bỏ qua cho
     * tới [reset]: một session chỉ sinh một lượt, và một lượt thứ hai chen vào giữa
     * lúc ASR đang chạy là cách chắc chắn nhất để hai transcript trộn vào nhau.
     */
    fun accept(frame: PcmFrame): VadStreamEvent? {
        if (closed) return null
        require(frame.samples.size == config.frameSamples) {
            "VadStreamDriver expects ${config.frameSamples}-sample frames, got ${frame.samples.size}"
        }
        require(frame.startSample == totalSamples) {
            "VAD needs frames in order: expected sample ${totalSamples}, got ${frame.startSample}"
        }

        lastFrameStartSample = frame.startSample
        lastFrameStartNanos = frame.startNanos

        appendToPreRoll(frame.samples)
        appendToSpeech(frame.samples)
        totalSamples = frame.endSample

        val events = endpointer.accept(
            scorer.probability(frame.samples),
            frame.startSample.toIntChecked(),
        )
        val event = events.firstOrNull() ?: return null
        return when (event) {
            is VadEvent.SpeechStarted -> {
                openSpeech(event.startSample.toLong())
                VadStreamEvent.SpeechStarted(
                    startSample = event.startSample.toLong(),
                    startNanos = nanosAt(event.startSample.toLong()),
                )
            }

            is VadEvent.SpeechEnded -> closeSpeech(
                event.startSample.toLong(),
                event.endSample.toLong(),
            )
        }
    }

    /**
     * Đóng session khi nguồn audio hết (nhả nút, chạm trần thời lượng) mà VAD chưa
     * kịp thấy khoảng lặng.
     *
     * Trả `null` khi chưa hề có tiếng nói — lượt đó là `NoSpeech`, và **không** được
     * gửi một đoạn rỗng xuống ASR (§5, hàng "VAD").
     */
    fun flush(): VadStreamEvent.SpeechEnded? {
        if (closed) return null
        val ended = endpointer.flush(totalSamples.toIntChecked()) ?: return null
        return closeSpeech(ended.startSample.toLong(), ended.endSample.toLong())
    }

    /** Xóa recurrent state của scorer và mọi buffer. Gọi khi mở session mới. */
    fun reset() {
        scorer.reset()
        endpointer = VadEndpointer(config)
        preRoll.fill(0)
        preRollWrite = 0
        totalSamples = 0
        lastFrameStartSample = 0
        lastFrameStartNanos = 0
        closed = false
        speech = null
        speechCount = 0
        speechStartSample = 0
    }

    private fun openSpeech(paddedStart: Long) {
        val lookBack = (totalSamples - paddedStart).toInt()
        check(lookBack <= preRollSamples) {
            "pre-roll of $preRollSamples samples cannot reach back $lookBack samples"
        }
        speechStartSample = paddedStart
        // Cấp phát theo maxSpeechMs: endpointer không bao giờ để một đoạn dài hơn thế.
        val capacity = config.samplesFor(config.maxSpeechMs) + preRollSamples
        val buffer = ShortArray(capacity)
        readPreRoll(lookBack, buffer)
        speech = buffer
        speechCount = lookBack
    }

    private fun closeSpeech(startSample: Long, endSample: Long): VadStreamEvent.SpeechEnded {
        val buffer = speech
        val from = (startSample - speechStartSample).toInt().coerceIn(0, speechCount)
        val to = (endSample - speechStartSample).toInt().coerceIn(from, speechCount)
        val pcm = buffer?.copyOfRange(from, to) ?: ShortArray(0)
        closed = true
        speech = null
        speechCount = 0
        return VadStreamEvent.SpeechEnded(
            AudioUtterance(
                pcm16 = pcm,
                sampleRate = config.sampleRate,
                speechStartNanos = nanosAt(startSample),
                speechEndNanos = nanosAt(endSample),
                trigger = trigger,
            ),
        )
    }

    private fun appendToPreRoll(samples: ShortArray) {
        for (sample in samples) {
            preRoll[preRollWrite] = sample
            preRollWrite = (preRollWrite + 1) % preRollSamples
        }
    }

    /** Chép [count] mẫu gần nhất của ring buffer vào đầu [into], theo đúng thứ tự. */
    private fun readPreRoll(count: Int, into: ShortArray) {
        var read = (preRollWrite - count + preRollSamples) % preRollSamples
        for (index in 0 until count) {
            into[index] = preRoll[read]
            read = (read + 1) % preRollSamples
        }
    }

    private fun appendToSpeech(samples: ShortArray) {
        val buffer = speech ?: return
        val room = minOf(samples.size, buffer.size - speechCount)
        if (room <= 0) return
        samples.copyInto(buffer, destinationOffset = speechCount, endIndex = room)
        speechCount += room
    }

    /**
     * Thời điểm thật của mẫu [sample], suy tuyến tính từ khung gần nhất.
     *
     * Ngoại suy **lùi** cho mẫu đã trôi qua là điều bắt buộc: VAD chỉ kết luận sau khi
     * đã đệm qua điểm bắt đầu, nên nếu lấy đồng hồ tại thời điểm kết luận thì mọi độ
     * trễ phía sau đều bị cộng thêm cả cửa sổ VAD (xem `LatencyTrace.markAt`).
     */
    private fun nanosAt(sample: Long): Long =
        lastFrameStartNanos +
            (sample - lastFrameStartSample) * 1_000_000_000L / config.sampleRate

    /**
     * [VadEndpointer] đánh chỉ số mẫu bằng `Int`. Một session bị chốt ở
     * [AudioConfig.maxDurationMs] nên không bao giờ chạm trần đó; nếu có ai bỏ chốt
     * thì hỏng ngay tại đây thay vì tràn số âm và sinh ra một đoạn cắt vô nghĩa.
     */
    private fun Long.toIntChecked(): Int {
        check(this <= Int.MAX_VALUE) { "VAD session exceeded Int sample range: $this" }
        return toInt()
    }
}
