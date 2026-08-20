package com.viva.voice.trace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Everything here asserts on the exact bytes that reach logcat, because the
 * wire format is the contract - the internal marks map is nobody's business.
 * The companion fixtures under android/voice/fixtures/ are generated from
 * these same expectations and handed to Vi as the parser's test input.
 */
class LatencyTraceTest {

    /** Advances by a caller-controlled amount so every assertion is exact. */
    private class FakeClock(var now: Long = 1_000_000_000L) : NanoClock {
        override fun nanos(): Long = now
        fun advanceMs(ms: Long) {
            now += ms * 1_000_000L
        }
    }

    private fun trace(
        clock: NanoClock,
        sink: TraceSink,
        diagnostics: TraceDiagnostics = TraceDiagnostics.NONE,
    ) = LatencyTrace("trace-001", clock, sink, diagnostics)

    // --- event lines -------------------------------------------------------

    @Test
    fun `mark emits one event line in the contract format`() {
        val sink = RecordingTraceSink()
        trace(FakeClock(1_000_000_000L), sink).mark(Stage.SPEECH_START)

        assertEquals(
            listOf("VIVA_TRACE|trace-001|speech_start|1000000000"),
            sink.lines,
        )
    }

    @Test
    fun `every stage emits its contract id, not its enum name`() {
        val sink = RecordingTraceSink()
        val clock = FakeClock()
        val t = trace(clock, sink)
        Stage.values().forEach { stage ->
            clock.advanceMs(10)
            t.mark(stage)
        }

        val stageIds = sink.lines.map { it.split("|")[2] }
        assertEquals(
            listOf(
                "speech_start", "acoustic_end", "speech_end", "asr_sent", "asr_done",
                "nlu_done", "guard_done", "exec_done", "render_done", "tts_start",
            ),
            stageIds,
        )
    }

    @Test
    fun `markAt back-dates a stage without consulting the clock`() {
        val sink = RecordingTraceSink()
        val t = trace(FakeClock(5_000_000_000L), sink)

        t.markAt(Stage.SPEECH_START, 4_200_000_000L)

        assertEquals("VIVA_TRACE|trace-001|speech_start|4200000000", sink.lines.single())
    }

    @Test
    fun `a stage marked twice keeps the first value and emits nothing extra`() {
        val sink = RecordingTraceSink()
        val warnings = mutableListOf<String>()
        val clock = FakeClock(1_000_000_000L)
        val t = trace(clock, sink) { warnings += it }

        t.mark(Stage.ASR_DONE)
        clock.advanceMs(500)
        val second = t.mark(Stage.ASR_DONE)

        assertEquals(1_000_000_000L, second)
        assertEquals(1, sink.lines.size)
        assertEquals(1, warnings.size)
    }

    @Test
    fun `multiple spoken segments keep the first tts start without a false duplicate warning`() {
        val sink = RecordingTraceSink()
        val warnings = mutableListOf<String>()
        val clock = FakeClock(1_000_000_000L)
        val t = trace(clock, sink) { warnings += it }

        t.mark(Stage.TTS_START)
        clock.advanceMs(500)
        val secondSegment = t.mark(Stage.TTS_START)

        assertEquals(1_000_000_000L, secondSegment)
        assertEquals(1, sink.lines.size)
        assertTrue(warnings.isEmpty())
    }

    // --- summary line ------------------------------------------------------

    @Test
    fun `summary emits the contract format with whole-millisecond e2e`() {
        val sink = RecordingTraceSink()
        val clock = FakeClock(1_000_000_000L)
        val t = trace(clock, sink)

        t.mark(Stage.SPEECH_START)
        clock.advanceMs(200)
        t.mark(Stage.SPEECH_END)
        clock.advanceMs(690)
        t.mark(Stage.TTS_START)
        t.summary("hạ điều hòa xuống 22 độ", "hvac_set_temp", TraceVerdict.Allow)

        assertEquals(
            "VIVA_TRACE_SUMMARY|trace-001|hạ điều hòa xuống 22 độ|hvac_set_temp|Allow|e2e_ms=690",
            sink.lines.last(),
        )
    }

    @Test
    fun `e2e is measured from speech_end, so a longer utterance does not inflate it`() {
        val shortSink = RecordingTraceSink()
        val shortClock = FakeClock(1_000_000_000L)
        val shortTurn = trace(shortClock, shortSink)
        shortTurn.mark(Stage.SPEECH_START)
        shortClock.advanceMs(400)          // driver spoke for 400ms
        shortTurn.mark(Stage.SPEECH_END)
        shortClock.advanceMs(700)          // system took 700ms
        shortTurn.mark(Stage.TTS_START)

        val longSink = RecordingTraceSink()
        val longClock = FakeClock(1_000_000_000L)
        val longTurn = trace(longClock, longSink)
        longTurn.mark(Stage.SPEECH_START)
        longClock.advanceMs(2_500)         // driver rambled for 2.5s
        longTurn.mark(Stage.SPEECH_END)
        longClock.advanceMs(700)           // system still took 700ms
        longTurn.mark(Stage.TTS_START)

        assertEquals(700.0, shortTurn.e2eMs()!!, 0.001)
        assertEquals(700.0, longTurn.e2eMs()!!, 0.001)
    }

    @Test
    fun `a pipe in the recognised text cannot shift the following fields`() {
        val sink = RecordingTraceSink()
        val t = trace(FakeClock(), sink)
        t.mark(Stage.SPEECH_END)
        t.mark(Stage.TTS_START)

        t.summary("mo cua | khoa cua", "door_lock", TraceVerdict.Deny("G1_SPEED_LOCK"))

        val fields = sink.lines.last().removePrefix(LatencyTrace.SUMMARY_MARKER).split("|")
        assertEquals(5, fields.size)
        assertEquals("mo cua / khoa cua", fields[1])
        assertEquals("door_lock", fields[2])
        assertEquals("Deny:G1_SPEED_LOCK", fields[3])
    }

    @Test
    fun `a newline in the recognised text cannot split the line in two`() {
        val sink = RecordingTraceSink()
        val t = trace(FakeClock(), sink)
        t.mark(Stage.SPEECH_END)

        t.summary("phat nhac\nchuyen bai", "media_play", TraceVerdict.Allow)

        assertEquals(1, sink.lines.count { it.startsWith(LatencyTrace.SUMMARY_MARKER) })
        assertFalse(sink.lines.last().contains("\n"))
    }

    @Test
    fun `an empty field keeps the field count`() {
        val sink = RecordingTraceSink()
        val t = trace(FakeClock(), sink)
        t.mark(Stage.SPEECH_END)

        t.summary("", "unknown", TraceVerdict.Allow)

        val fields = sink.lines.last().removePrefix(LatencyTrace.SUMMARY_MARKER).split("|")
        assertEquals(5, fields.size)
        assertEquals("-", fields[1])
    }

    @Test
    fun `an overlong utterance is truncated rather than risking logcat truncation`() {
        val sink = RecordingTraceSink()
        val t = trace(FakeClock(), sink)
        t.mark(Stage.SPEECH_END)

        t.summary("a".repeat(500), "unknown", TraceVerdict.Allow)

        val fields = sink.lines.last().removePrefix(LatencyTrace.SUMMARY_MARKER).split("|")
        assertEquals(200, fields[1].length)
    }

    @Test
    fun `e2e_ms is always locale-independent digits`() {
        // Reproduces the failure mode this format guards against: on a
        // vi-VN device String.format("%.1f") yields "690,0" and Go's
        // ParseFloat rejects it, so every summary line becomes malformed.
        assertEquals("690", LatencyTrace.formatE2eMs(690.4))
        assertEquals("691", LatencyTrace.formatE2eMs(690.5))
        assertEquals("0", LatencyTrace.formatE2eMs(null))
        assertTrue(LatencyTrace.formatE2eMs(1234.9).all { it.isDigit() })
    }

    @Test
    fun `a second summary is dropped so the turn is not counted twice`() {
        val sink = RecordingTraceSink()
        val warnings = mutableListOf<String>()
        val t = trace(FakeClock(), sink) { warnings += it }
        t.mark(Stage.SPEECH_END)

        t.summary("khoa cua", "door_lock", TraceVerdict.Allow)
        t.summary("khoa cua", "door_lock", TraceVerdict.Allow)

        assertEquals(1, sink.lines.count { it.startsWith(LatencyTrace.SUMMARY_MARKER) })
        assertEquals(1, warnings.size)
    }

    // --- failed turns ------------------------------------------------------

    @Test
    fun `a turn that dies at ASR still reports where it died`() {
        val sink = RecordingTraceSink()
        val clock = FakeClock(1_000_000_000L)
        val t = trace(clock, sink)

        t.mark(Stage.SPEECH_START)
        clock.advanceMs(150)
        t.mark(Stage.SPEECH_END)
        clock.advanceMs(10)
        t.mark(Stage.ASR_SENT)
        clock.advanceMs(3_000)                       // ASR timed out
        t.summary("", "unknown", TraceVerdict.Error(Stage.ASR_DONE))

        val fields = sink.lines.last().removePrefix(LatencyTrace.SUMMARY_MARKER).split("|")
        assertEquals("Error:asr_done", fields[3])
        assertEquals("e2e_ms=3010", fields[4])
        assertNull(t.nanosOf(Stage.TTS_START))
    }

    @Test
    fun `ms returns null for a segment whose marks never fired`() {
        val t = trace(FakeClock(), RecordingTraceSink())
        t.mark(Stage.SPEECH_START)

        assertNull(t.ms(Stage.ASR_SENT, Stage.ASR_DONE))
    }

    @Test
    fun `isClosed distinguishes a completed turn from an abandoned one`() {
        val t = trace(FakeClock(), RecordingTraceSink())
        t.mark(Stage.SPEECH_END)
        assertFalse(t.isClosed())

        t.summary("khoa cua", "door_lock", TraceVerdict.Allow)
        assertTrue(t.isClosed())
    }

    // --- luot khong co tieng noi (benchmark bom text) ----------------------

    @Test
    fun `a turn with no speech mark reports e2e as unusable, not as a fast turn`() {
        // Luot bom text cho benchmark khong di qua mic hay ASR. Neu no van mang
        // speech_start thi se de ra mot e2e_ms trong nhu do tre dau-cuoi that,
        // trong khi chang ASR chua he chay — dung loai so lieu dep ma sai ma ca
        // file nay duoc viet ra de chan.
        //
        // `e2e_ms=0` la cach khai dung theo hop dong: truong nay BAT BUOC phai
        // co (parser Go bao loi neu thieu), va 0 chinh la quy uoc "khong co mark
        // dung duoc". Harness khong doc so nay de tinh p50/p95 — no tinh
        // e2e_computed tu speech_end -> tts_start trong cac dong event, ma luot
        // nay khong co, nen no tu roi ra ngoai thong ke do tre.
        val sink = RecordingTraceSink()
        val t = trace(FakeClock(), sink)

        t.mark(Stage.NLU_DONE)
        t.mark(Stage.EXEC_DONE)
        assertNull("khong co speech mark thi khong tinh duoc e2e", t.e2eMs())

        t.summary("hạ điều hòa xuống 24 độ", "hvac_set_temp", TraceVerdict.Allow)

        val summary = sink.lines.last { it.contains("VIVA_TRACE_SUMMARY") }
        assertTrue(summary, summary.endsWith("|e2e_ms=0"))
        assertFalse("khong duoc co dong speech_start nao", sink.lines.any { it.contains("speech_start") })
    }

    @Test
    fun `marking speech_start is what turns e2e back on`() {
        val clock = FakeClock(1_000_000_000L)
        val sink = RecordingTraceSink()
        val t = trace(clock, sink)

        t.mark(Stage.SPEECH_START)
        clock.now += 500_000_000L
        t.summary("khoa cua", "door_lock", TraceVerdict.Allow)

        assertEquals(500.0, t.e2eMs()!!, 0.001)
    }

    // --- verdict grammar ---------------------------------------------------

    @Test
    fun `verdict wire strings split into kind and detail on the first colon`() {
        assertEquals("Allow", TraceVerdict.Allow.wire)
        assertEquals("Deny:G1_SPEED_LOCK", TraceVerdict.Deny("G1_SPEED_LOCK").wire)
        assertEquals("Confirm:G2_CONFIRM_DOOR", TraceVerdict.Confirm("G2_CONFIRM_DOOR").wire)
        assertEquals("Error:asr_done", TraceVerdict.Error(Stage.ASR_DONE).wire)
    }

    @Test
    fun `a rule id with unexpected characters cannot corrupt the line`() {
        assertEquals("Deny:G1_SPEED_LOCK", TraceVerdict.Deny(" g1|speed lock ").wire)
        assertEquals("Deny:UNSPECIFIED", TraceVerdict.Deny("").wire)
    }
    /**
     * `speech_end` được đóng dấu tại lúc VAD *quyết định* endpoint, tức là sau
     * khi đã trôi hết `minSilenceMs` (800 ms ở cấu hình cabin). Nên `e2eMs()`
     * bỏ sót đúng khoảng đó. `feltLatencyMs()` đo từ mốc âm học — thời điểm tài
     * xế thật sự dứt câu — và phải lớn hơn `e2eMs()` đúng bằng khoảng chờ VAD.
     */
    @Test
    fun `felt latency measures from the acoustic end not the endpoint decision`() {
        val clock = ScriptedClock(
            listOf(
                0L,                    // ACOUSTIC_END  t=0
                800_000_000L,          // SPEECH_END    t=800ms (VAD quyet dinh)
                2_136_000_000L,        // TTS_START     t=2136ms
            ),
        )
        val trace = LatencyTrace("felt", clock, RecordingTraceSink())
        trace.mark(Stage.ACOUSTIC_END)
        trace.mark(Stage.SPEECH_END)
        trace.mark(Stage.TTS_START)

        assertEquals(1336.0, trace.e2eMs()!!, 0.5)
        assertEquals(2136.0, trace.feltLatencyMs()!!, 0.5)
    }

    /** Không có mốc âm học thì suy biến về `e2eMs()`, không trả null. */
    @Test
    fun `felt latency falls back to the endpoint mark when acoustic end is absent`() {
        val clock = ScriptedClock(listOf(0L, 1_336_000_000L))
        val trace = LatencyTrace("felt-fallback", clock, RecordingTraceSink())
        trace.mark(Stage.SPEECH_END)
        trace.mark(Stage.TTS_START)

        assertEquals(trace.e2eMs()!!, trace.feltLatencyMs()!!, 0.001)
    }

    private class ScriptedClock(times: List<Long>) : NanoClock {
        private val remaining = ArrayDeque(times)
        private var last = 0L
        override fun nanos(): Long {
            last = remaining.removeFirstOrNull() ?: last
            return last
        }
    }

}
