package com.viva.voice.audio

import com.viva.voice.trace.NanoClock
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmSourceAudioCaptureTest {

    /** 1600 mẫu = 100ms, đúng chunk mặc định của [AudioConfig]. */
    private fun chunk(value: Short, size: Int = 1_600) = ShortArray(size) { value }

    /** Nhích 100ms mỗi lần đọc, bằng đúng một chunk 1600 mẫu ở 16 kHz. */
    private class ChunkClock : NanoClock {
        private var now = 0L
        override fun nanos(): Long {
            now += 100_000_000L
            return now
        }
    }

    @Test
    fun `frames are exactly the size Silero VAD accepts`() = runTest {
        val capture = PcmSourceAudioCapture(
            source = FakePcmSource(listOf(chunk(1), chunk(2), chunk(3))),
            clock = ChunkClock(),
        )

        val frames = capture.frames().take(4).toList()

        assertTrue(frames.all { it.samples.size == 512 })
        assertEquals(listOf(0L, 512L, 1_024L, 1_536L), frames.map { it.startSample })
    }

    @Test
    fun `a frame spanning two source chunks keeps both halves in order`() = runTest {
        // 1600 không chia hết cho 512: khung thứ tư bắt đầu ở mẫu 1536 của chunk đầu
        // và kết thúc trong chunk thứ hai. Đó là trường hợp mà một consumer tự đệm
        // lấy sẽ làm rơi mẫu.
        val capture = PcmSourceAudioCapture(
            source = FakePcmSource(listOf(chunk(7), chunk(9))),
            clock = ChunkClock(),
        )

        val fourth = capture.frames().take(4).toList().last()

        assertEquals(64, fourth.samples.count { it == 7.toShort() })
        assertEquals(448, fourth.samples.count { it == 9.toShort() })
        assertEquals(listOf<Short>(7, 9), fourth.samples.toList().distinct())
    }

    @Test
    fun `frame timestamps advance by the real duration of a frame`() = runTest {
        val capture = PcmSourceAudioCapture(
            source = FakePcmSource(listOf(chunk(1), chunk(1), chunk(1))),
            clock = ChunkClock(),
        )

        val frames = capture.frames().take(3).toList()

        // 512 mẫu ở 16 kHz = 32ms. Không phải khoảng cách giữa hai lần đọc mic.
        assertEquals(32_000_000L, frames[1].startNanos - frames[0].startNanos)
        assertEquals(32_000_000L, frames[2].startNanos - frames[1].startNanos)
    }

    @Test
    fun `first frame is back-dated to when its samples were recorded`() = runTest {
        val capture = PcmSourceAudioCapture(
            source = FakePcmSource(listOf(chunk(1))),
            clock = ChunkClock(),
        )

        val first = capture.frames().take(1).toList().single()

        // Chunk đầu về lúc t=100ms và mang 100ms audio, nên mẫu đầu của nó được thu
        // lúc t=0. Gán thẳng 100ms sẽ đẩy speech_start trễ đúng một chunk.
        assertEquals(0L, first.startNanos)
    }

    @Test
    fun `microphone is released when the collector stops early`() = runTest {
        val source = FakePcmSource(listOf(chunk(1), chunk(2)))
        val capture = PcmSourceAudioCapture(source, ChunkClock())

        capture.frames().take(1).toList()

        assertTrue(source.started)
        assertTrue(source.stopped)
    }

    @Test
    fun `a stuck session stops at the duration cap instead of holding the mic`() = runTest {
        val source = FakePcmSource(chunks = emptyList(), padWith = 1)
        val capture = PcmSourceAudioCapture(
            source = source,
            clock = ChunkClock(),
            config = AudioConfig(maxDurationMs = 1_000),
        )

        val frames = capture.frames().toList()

        // 1000ms ở 16 kHz = 16000 mẫu. Khung cuối bắt đầu ở 15872 (< 16000) nên vẫn
        // được phát trọn vẹn: chốt dừng ở biên khung đầu tiên chạm mức, không cắt
        // giữa khung — VAD không nhận nổi khung thiếu mẫu.
        assertEquals(32, frames.size)
        assertEquals(15_872L, frames.last().startSample)
        assertTrue(source.stopped)
    }

    @Test
    fun `a source that ends mid-frame drops the incomplete tail rather than padding it`() = runTest {
        val capture = PcmSourceAudioCapture(
            source = EndingPcmSource(listOf(chunk(1, size = 700))),
            clock = ChunkClock(),
        )

        val frames = capture.frames().toList()

        // 700 mẫu chỉ đủ một khung 512; 188 mẫu thừa bị bỏ chứ không được đệm 0 —
        // đệm 0 sẽ tạo ra một khung "im lặng" giả mà VAD đọc như thật.
        assertEquals(1, frames.size)
    }

    /** [FakePcmSource] đệm vô hạn; bản này kết thúc thật để test đuôi dở dang. */
    private class EndingPcmSource(private val chunks: List<ShortArray>) : PcmSource {
        private var index = 0
        override fun start() = Unit
        override fun read(into: ShortArray): Int {
            val chunk = chunks.getOrNull(index++) ?: return -1
            chunk.copyInto(into, endIndex = minOf(chunk.size, into.size))
            return minOf(chunk.size, into.size)
        }
        override fun stop() = Unit
    }
}
