package com.sopa.viva_automotive.feature.voice.data.audio

import android.content.Context
import android.util.Log
import com.viva.voice.audio.SileroVadOnnxScorer
import com.viva.voice.audio.VadConfig
import com.viva.voice.audio.VadStreamDriver
import com.viva.voice.audio.VoiceActivityScorer
import com.viva.voice.trace.NanoClock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dựng [VadStreamDriver] cho một session, dùng chung một scorer ONNX.
 *
 * Scorer được tạo một lần: nạp session ONNX tốn hàng trăm mili-giây và trả giá đó
 * bên trong một lượt là ném thẳng vào p95 mà claim 1500ms đang nói tới.
 *
 * [create] trả `null` khi model không nạp được. Đây là lựa chọn có chủ đích: một
 * ảnh AAOS thiếu asset, hoặc một thiết bị ONNX Runtime không chạy được, thì lượt
 * thoại phải fail rõ ràng, chứ không phải chết im cả trợ lý.
 * Lượt xuống cấp được ghi log để đọc lại được sau, không im lặng.
 */
@Singleton
class SileroVadDriverFactory @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val reusableFactory = ReusableVadDriverFactory(
        scorerLoader = {
            runCatching { SileroVadOnnxScorer(context) }
                .onFailure { error ->
                    Log.w(TAG, "Silero VAD unavailable; voice capture cannot start", error)
                }
                .getOrNull()
        },
        config = VIETNAMESE_CABIN_VAD,
    )

    fun create(decisionClock: NanoClock? = null): VadStreamDriver? =
        reusableFactory.create(decisionClock)

    private companion object {
        const val TAG = "VIVA_VOICE"

        /**
         * Mặc định của `VadConfig` (silence 100ms, pad 30ms) cắt câu tiếng Việt giữa
         * chừng: khoảng lặng tự nhiên giữa hai âm tiết của "phát nhạc" đã dài hơn
         * 100ms, nên endpoint đóng ngay sau âm đầu.
         *
         * Đo trên emulator AAOS 09/08 với bộ mặc định: mỗi lượt chỉ thu được
         * 380–540ms tiếng nói và ASR trả về đúng một âm tiết ("xã", "nhà") cho câu
         * "phát nhạc" — router không thể khớp, mọi lượt ra Deny:G3_UNSUPPORTED.
         * Cùng câu đó với bộ số dưới đây thu được ~984ms và ASR ra "hát nhạc".
         *
         * Ba con số này lấy từ `VadUtteranceCapture` trên nhánh `feature/automotive`
         * (commit 56aeb51), nơi chúng đã được chỉnh cho giọng nói trong cabin.
         */
        val VIETNAMESE_CABIN_VAD = VadConfig(
            minSpeechMs = 300,
            minSilenceMs = 800,
            speechPadMs = 300,
            maxSpeechMs = 12_000,
        )
    }
}

/** JVM-testable owner of the one recurrent scorer/session shared across turns. */
internal class ReusableVadDriverFactory(
    private val scorerLoader: () -> VoiceActivityScorer?,
    private val config: VadConfig,
) {
    private val scorer: VoiceActivityScorer? by lazy(scorerLoader)

    fun create(decisionClock: NanoClock? = null): VadStreamDriver? = scorer?.let { active ->
        // Reset at the next session boundary: a cancelled previous turn may never
        // have reached its normal cleanup path.
        VadStreamDriver(active, config = config, decisionClock = decisionClock).also { it.reset() }
    }
}
