package com.sopa.viva_automotive.feature.voice.domain.media

/** Media commands that the voice pipeline can route and execute. */
enum class MediaCommand(
    val intentName: String,
    val progressMessageVi: String,
    val dispatchedMessageVi: String,
) {
    PLAY("media_play", "Đang phát nhạc", "Đã gửi lệnh phát nhạc tới trình phát."),
    PAUSE("media_pause", "Đang dừng nhạc", "Đã gửi lệnh dừng nhạc tới trình phát."),
    NEXT("media_next", "Đang chuyển bài", "Đã gửi lệnh chuyển bài tới trình phát."),
}
