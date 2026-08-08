package com.sopa.viva_automotive.feature.voice.data.audio

import android.content.Context
import android.util.Log
import com.viva.voice.audio.SileroVadOnnxScorer
import com.viva.voice.audio.VadStreamDriver
import com.viva.voice.audio.VoiceActivityScorer
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
 * thoại phải **xuống cấp về endpoint của Vosk**, chứ không phải chết cả trợ lý.
 * Lượt xuống cấp được ghi log để đọc lại được sau, không im lặng.
 */
@Singleton
class SileroVadDriverFactory @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scorer: VoiceActivityScorer? by lazy {
        runCatching { SileroVadOnnxScorer(context) }
            .onFailure { error -> Log.w(TAG, "Silero VAD unavailable, falling back to ASR endpoint", error) }
            .getOrNull()
    }

    fun create(): VadStreamDriver? = scorer?.let { active ->
        // Scorer mang recurrent state của lượt trước. `reset()` ở đây, không phải ở
        // cuối lượt trước: một lượt bị hủy giữa chừng sẽ không bao giờ chạy tới cuối.
        VadStreamDriver(active).also { it.reset() }
    }

    private companion object {
        const val TAG = "VIVA_VOICE"
    }
}
