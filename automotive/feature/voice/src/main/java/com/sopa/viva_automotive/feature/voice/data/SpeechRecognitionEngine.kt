package com.sopa.viva_automotive.feature.voice.data

import com.viva.voice.audio.PcmFrame
import kotlinx.coroutines.flow.Flow

sealed interface TranscriptionEvent {
    /** Remote utterance ASR đã bắt đầu gửi request; dùng để đặt trace `asr_sent` đúng lúc. */
    data object RequestStarted : TranscriptionEvent

    /** Chỉ để cập nhật HMI. **Không bao giờ** được route hay execute (28-PIPELINE §2.4). */
    data class Partial(val text: String) : TranscriptionEvent

    data class Final(
        val text: String,
        /**
         * ASR nghe âm thanh chắc đến đâu. `null` khi engine không cung cấp — Vosk
         * small không có (28-PIPELINE §2.4).
         *
         * Không được thay bằng 1.0: đó là biến "chưa đo được" thành "chắc chắn", và
         * luật low-confidence sẽ không bao giờ chạy trong khi báo cáo vẫn đẹp.
         */
        val acousticConfidence: Float?,
        /** Thời gian engine chạy, để tách chi phí ASR khỏi p95 tổng của lượt. */
        val engineMs: Int,
    ) : TranscriptionEvent

    /**
     * [code] là mã máy đọc được (§4: "error có code máy đọc được, không chỉ có
     * message"); [diagnostic] là văn bản cho log, **không** phải câu nói cho tài xế.
     */
    data class Error(
        val code: String,
        val diagnostic: String,
        val cause: Throwable? = null,
    ) : TranscriptionEvent

    companion object {
        const val CODE_MODEL_UNAVAILABLE = "asr_model_unavailable"
        const val CODE_NO_SPEECH = "asr_no_speech"
        const val CODE_ENGINE_FAILED = "asr_engine_failed"
    }
}

/**
 * ASR: audio vào, text ra — và **chỉ** thế (28-PIPELINE §0, quyết định 2).
 *
 * [transcribe] nhận một [Flow] khung PCM thay vì tự mở microphone. Đó là điểm khác
 * biệt so với bản trước: engine không còn sở hữu `AudioRecord`, nên VAD, ASR
 * on-device và ASR remote cùng đọc một nguồn duy nhất thay vì tranh mic của nhau.
 */
interface SpeechRecognitionEngine {
    /** `true` với Vosk streaming; `false` với endpoint chỉ nhận cả utterance. */
    val sendsAudioWhileCapturing: Boolean get() = true

    suspend fun initialize(): Result<Unit>

    fun transcribe(frames: Flow<PcmFrame>): Flow<TranscriptionEvent>

    /**
     * Ép chốt câu ngay ở khung kế tiếp. Được VAD gọi khi endpointer thấy biên, hoặc
     * khi nút push-to-talk được nhả — hai trigger, một đường (§2.1).
     */
    fun requestEndOfUtterance() {}
}
