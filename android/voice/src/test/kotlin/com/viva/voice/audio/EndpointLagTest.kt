package com.viva.voice.audio

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * VAD chỉ bắn `SpeechEnded` sau khi đã trôi hết `minSilenceMs`, nên đồng hồ
 * tại thời điểm đó đã trễ so với lúc người nói dứt câu. Khoảng trễ suy được từ
 * số mẫu audio giữa mốc âm học và frame đang xử lý — đây là phép quy đổi đó.
 */
class EndpointLagTest {

    @Test
    fun `lag is the audio distance between acoustic end and the current frame`() {
        // 16 kHz, im lặng bắt đầu ở mẫu 2048, frame hiện tại kết thúc ở 3584.
        // 1536 mẫu = 96 ms.
        assertEquals(96_000_000L, endpointLagNanos(2_048, 3_584, 16_000))
    }

    @Test
    fun `cabin config lag equals the configured silence window`() {
        // minSilenceMs=800 ở cabin: 12800 mẫu tại 16 kHz.
        assertEquals(800_000_000L, endpointLagNanos(0, 12_800, 16_000))
    }

    @Test
    fun `no lag when the acoustic end is the current frame`() {
        assertEquals(0L, endpointLagNanos(4_096, 4_096, 16_000))
    }

    @Test
    fun `negative distance is clamped rather than back-dating the mark`() {
        assertEquals(0L, endpointLagNanos(5_000, 4_096, 16_000))
    }
}
