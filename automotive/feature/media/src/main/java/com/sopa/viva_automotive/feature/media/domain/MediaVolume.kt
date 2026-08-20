package com.sopa.viva_automotive.feature.media.domain

/**
 * Âm lượng ở tầng ứng dụng (gain của `Player`), thang 0f..1f.
 *
 * ## Vì sao không đụng tới `AudioManager` ở đây
 *
 * Cấu hình AAOS dùng CarAudioService thường bật `config_useFixedVolume=true` để
 * gain được áp dụng dưới HAL, ở amply phần cứng. Vì app hiện không có quyền điều
 * khiển volume group, thanh âm lượng media chỉ chỉnh gain cục bộ của `Player`;
 * quan trọng là không chỉnh thêm `AudioManager` trong cùng một thao tác, tránh
 * hai tầng suy giảm nhân nhau.
 *
 * Đường đúng trên xe là `CarAudioManager.setGroupVolume`, nhưng nó cần
 * `CAR_CONTROL_AUDIO_VOLUME` (signature|privileged) mà đội chưa mở được — xem
 * `AndroidVolumeController`, nơi đã đo `isVolumeFixed == true` trên emulator
 * AAOS 14. Đây là phương án dự phòng ở tầng app, không thay thế volume group của
 * xe và vẫn có giới hạn chất lượng vốn có của software attenuation.
 */
object MediaVolume {

    /** Một nấc của nút tăng/giảm trên màn hình media. */
    const val STEP = 0.1f

    fun stepped(current: Float, delta: Int): Float = clamped(current + delta * STEP)

    fun clamped(volume: Float): Float = volume.coerceIn(MIN, MAX)

    private const val MIN = 0f
    private const val MAX = 1f
}
