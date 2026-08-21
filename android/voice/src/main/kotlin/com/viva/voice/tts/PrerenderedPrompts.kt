package com.viva.voice.tts

import java.util.Locale

data class PrerenderedPrompt(
    val textVi: String,
    val rawName: String,
)

/** Fixed Vietnamese responses bundled for AAOS images without a vi-VN TTS voice. */
object PrerenderedPrompts {
    val all: List<PrerenderedPrompt> =
        (16..32).map { degrees ->
            PrerenderedPrompt(
                textVi = "Đã đặt nhiệt độ mục tiêu $degrees°C.",
                rawName = "tts_temp_$degrees",
            )
        } +
            (0..5).map { level ->
                PrerenderedPrompt(
                    textVi = "Đã đặt quạt mức $level.",
                    rawName = "tts_fan_$level",
                )
            } +
            listOf(
                PrerenderedPrompt("Đã khóa cửa tài xế.", "tts_driver_door_locked"),
                PrerenderedPrompt("Đã mở khóa cửa tài xế.", "tts_driver_door_unlocked"),
                PrerenderedPrompt("Đã chuyển bài.", "tts_media_next"),
                PrerenderedPrompt("Đã tăng âm lượng.", "tts_volume_up"),
                PrerenderedPrompt("Đã giảm âm lượng.", "tts_volume_down"),
                PrerenderedPrompt(
                    "Mình chưa nghe rõ. Bạn thử lại giúp mình nhé.",
                    "tts_did_not_hear_try_again",
                ),
                PrerenderedPrompt(
                    "Mình chưa nghe rõ. Bạn nói lại giúp mình nhé.",
                    "tts_did_not_hear_repeat",
                ),
                PrerenderedPrompt(
                    "Mình chưa thực hiện được yêu cầu. Bạn thử lại giúp mình nhé.",
                    "tts_command_failed",
                ),
                PrerenderedPrompt(
                    "Bạn muốn tăng nhiệt độ điều hòa lên bao nhiêu độ?",
                    "tts_clarify_warmer",
                ),
                PrerenderedPrompt(
                    "Nhiệt độ hỗ trợ từ 16 đến 32 độ C. Bạn muốn đặt bao nhiêu độ?",
                    "tts_clarify_temperature_range",
                ),
                PrerenderedPrompt(
                    "Bạn muốn đặt quạt ở mức mấy, từ 0 đến 5?",
                    "tts_clarify_fan_level",
                ),
                PrerenderedPrompt(
                    "Xe đang chạy, mình chưa mở cửa được.",
                    "tts_deny_door_while_moving",
                ),
                PrerenderedPrompt(
                    "Từ gọi của trợ lý là “Viva ơi” (cũng nhận Vivi/Vi-Vi ơi). Bạn thử lại nhé.",
                    "tts_wrong_wake_phrase",
                ),
            )

    private val byText = all.associateBy { normalize(it.textVi) }

    fun rawNameFor(textVi: String): String? = byText[normalize(textVi)]?.rawName

    private fun normalize(text: String): String = text
        .trim()
        .replace(Regex("\\s+"), " ")
        .trimEnd('.', '!', '?')
        .lowercase(Locale.ROOT)
}
