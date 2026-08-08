package com.viva.voice.asr

import com.viva.voice.trace.LatencyTrace
import com.viva.voice.trace.Stage

interface AsrClient {
    suspend fun transcribe(
        pcm16: ShortArray,
        sampleRate: Int,
        trace: LatencyTrace,
    ): AsrResult
}

data class AsrResult(
    val text: String,
    /**
     * ASR nghe **âm thanh** chắc đến đâu (28-PIPELINE §2.4). `null` khi engine không
     * cung cấp — Vosk small là một trong số đó.
     *
     * Nullable chứ không mặc định 1.0: gán 1.0 cho một engine im lặng biến "không đo
     * được" thành "chắc chắn", và luật low-confidence sẽ không bao giờ chạy trong khi
     * bảng benchmark vẫn báo confidence hoàn hảo. Thiếu confidence là một trạng thái
     * quan sát được, không phải một giá trị.
     *
     * Tách hẳn khỏi `Intent.confidence` (độ chắc của **NLU**). Trộn hai con số này
     * làm một là §4 cấm.
     */
    val acousticConfidence: Float?,
    val serverMs: Int,
    val isPartial: Boolean = false,
) {
    init {
        require(acousticConfidence == null || acousticConfidence in 0f..1f) {
            "acousticConfidence must be between 0 and 1 when present"
        }
        require(serverMs >= 0) { "serverMs must not be negative" }
    }
}

/** Immediate ASR fake for UI integration and deterministic tests. */
class FakeAsrClient(
    private val result: AsrResult = AsrResult("", acousticConfidence = null, serverMs = 0),
) : AsrClient {
    override suspend fun transcribe(
        pcm16: ShortArray,
        sampleRate: Int,
        trace: LatencyTrace,
    ): AsrResult {
        require(pcm16.isNotEmpty()) { "pcm16 must not be empty" }
        require(sampleRate > 0) { "sampleRate must be positive" }
        trace.mark(Stage.ASR_SENT)
        trace.mark(Stage.ASR_DONE)
        return result
    }
}
