package com.viva.voice.trace

/**
 * One voice turn, from "user pressed talk" to "assistant started speaking".
 *
 * Every module in the pipeline (VAD, AsrClient, IntentRouter, SafetyGuard,
 * Skill, HMI, TtsSpeaker) calls [mark] as it hands off. The turn ends with
 * exactly one [summary] call. Those two methods are the entire public
 * surface; everything else is derived.
 *
 * Wire format, verbatim from vong2/03-contracts.md §1:
 *
 *     VIVA_TRACE|<traceId>|<stage>|<elapsedRealtimeNanos>
 *     VIVA_TRACE_SUMMARY|<traceId>|<utterance>|<intent>|<verdict>|e2e_ms=<so>
 *
 * Consumed by backend/internal/domain/parse.go (Vi's harness, V8/V10/V12).
 * The harness splits on '|' with a fixed field count, so any '|' that leaks
 * into a text field silently shifts every field after it - see [sanitize].
 *
 * Not thread-safe by design. One turn is driven by one coroutine on the voice
 * pipeline; sharing a trace across threads means the marks are racing anyway
 * and the timings are already meaningless. If that changes, wrap the calls,
 * do not add a lock here and pretend the numbers are still comparable.
 */
class LatencyTrace(
    val traceId: String,
    private val clock: NanoClock,
    private val sink: TraceSink,
    private val diagnostics: TraceDiagnostics = TraceDiagnostics.NONE,
) {

    private val marks = LinkedHashMap<Stage, Long>()
    private var summarised = false

    /**
     * Records [stage] at the current clock reading and emits its trace line.
     * Returns the nanosecond value recorded.
     *
     * First write wins. A stage marked twice is a bug in the caller (a retry
     * that forgot to open a new trace, usually); overwriting would quietly
     * shrink the measured segment and flatter the p95, so the first value
     * stands and the duplicate is reported on the diagnostics channel.
     */
    fun mark(stage: Stage): Long = markAt(stage, clock.nanos())

    /**
     * Like [mark] but with an explicit timestamp, for the case where the
     * event already happened: VAD detects the start of speech only after it
     * has buffered past it, so `speech_start` must be back-dated to the real
     * onset or every latency downstream is inflated by the VAD window.
     */
    fun markAt(stage: Stage, nanos: Long): Long {
        val existing = marks[stage]
        if (existing != null) {
            diagnostics.warn("duplicate mark ${stage.id} on trace $traceId, keeping first")
            return existing
        }
        marks[stage] = nanos
        sink.emit(EVENT_MARKER + traceId + FIELD_SEP + stage.id + FIELD_SEP + nanos)
        return nanos
    }

    /** Raw nanos for [stage], or null if that stage never fired this turn. */
    fun nanosOf(stage: Stage): Long? = marks[stage]

    /**
     * Milliseconds between two marks, or null if either is missing.
     *
     * Nullable rather than throwing (03-contracts.md §1 sketched `marks[to]!!`)
     * because a turn that dies mid-pipeline is a normal, expected outcome we
     * want to *measure*, not an exception that takes the app down while the
     * judges are watching.
     */
    fun ms(from: Stage, to: Stage): Double? {
        val a = marks[from] ?: return null
        val b = marks[to] ?: return null
        return (b - a) / 1_000_000.0
    }

    /**
     * The number the 1500ms claim is about: `speech_end` -> `tts_start`.
     *
     * DECISION (Long, 29/07) - 03-contracts.md §1 never defined the endpoints
     * of "end-to-end", and the choice changes the reported number by seconds:
     *
     *  - from `speech_start` would include however long the driver spent
     *    talking, so p95 would grow with sentence length. "Ha dieu hoa xuong
     *    22 do" and "Ha dieu hoa xuong 22 do di" would be different systems.
     *    That is a measurement of the user, not of us.
     *  - to `tts_start` is the moment the driver hears a response, which is
     *    the latency actually felt.
     *
     * `render_done` (HMI frame) is deliberately not part of this number; the
     * harness can compute `speech_end -> render_done` from the raw marks when
     * the question is about the screen instead of the voice.
     *
     * When `tts_start` is missing the turn never answered, and the end is
     * *now* - not the last mark recorded. The difference matters: a turn that
     * marked `asr_sent` and then sat in a 3s ASR timeout would report 10ms
     * under a last-mark fallback, because the last thing it managed to do was
     * fast. A failed turn would then pull p95 *down*, which is the exact
     * opposite of the truth - the driver waited three seconds and got nothing.
     *
     * For a turn still in flight this therefore reads "elapsed so far".
     * [summary] calls it exactly once, at the end, which is when that is the
     * number wanted.
     */
    /**
     * Độ trễ tài xế **thật sự cảm nhận**: từ lúc dứt câu tới lúc nghe tiếng.
     *
     * Khác [e2eMs] ở điểm đầu. [Stage.SPEECH_END] được đóng dấu khi VAD quyết
     * định endpoint, tức là sau khi đã chờ hết `minSilenceMs` — với cấu hình
     * cabin hiện tại là 800 ms. Khoảng chờ đó tài xế phải ngồi im chịu, nên nó
     * thuộc về độ trễ cảm nhận dù không thuộc về thời gian xử lý.
     *
     * [e2eMs] được giữ nguyên định nghĩa cũ vì `03-contracts.md` §1.3 và
     * backend đang chốt trên nó; đây là số thứ hai, không phải số thay thế.
     */
    fun feltLatencyMs(): Double? {
        val start = marks[Stage.ACOUSTIC_END] ?: return e2eMs()
        val end = marks[Stage.TTS_START] ?: clock.nanos()
        if (end < start) return null
        return (end - start) / 1_000_000.0
    }

    fun e2eMs(): Double? {
        val start = marks[Stage.SPEECH_END] ?: marks[Stage.SPEECH_START] ?: return null
        val end = marks[Stage.TTS_START] ?: clock.nanos()
        if (end < start) return null
        return (end - start) / 1_000_000.0
    }

    /**
     * Closes the turn with the one summary line the harness keys its report on.
     *
     * [utterance] is the recognised Vietnamese text, [intent] an intent name
     * from 03-contracts.md §3, [verdict] see [TraceVerdict].
     *
     * Emitted at most once. A second call is dropped rather than appended,
     * because two summaries for one traceId would double-count that turn in
     * every p50/p95 the harness computes.
     */
    fun summary(utterance: String, intent: String, verdict: TraceVerdict) {
        if (summarised) {
            diagnostics.warn("second summary on trace $traceId ignored")
            return
        }
        summarised = true
        val e2e = e2eMs()
        sink.emit(
            SUMMARY_MARKER + traceId +
                FIELD_SEP + sanitize(utterance, MAX_UTTERANCE_CHARS) +
                FIELD_SEP + sanitize(intent, MAX_INTENT_CHARS) +
                FIELD_SEP + verdict.wire +
                FIELD_SEP + E2E_PREFIX + formatE2eMs(e2e),
        )
    }

    /** True once [summary] has run - a turn without one never completed. */
    fun isClosed(): Boolean = summarised

    companion object {
        const val EVENT_MARKER = "VIVA_TRACE|"
        const val SUMMARY_MARKER = "VIVA_TRACE_SUMMARY|"
        const val E2E_PREFIX = "e2e_ms="

        /** logcat tag for the trace stream. `adb logcat -s VIVA_TRACE`. */
        const val LOG_TAG = "VIVA_TRACE"

        private const val FIELD_SEP = "|"

        /**
         * logcat truncates a single message at roughly 4000 bytes and a
         * truncated summary line is an unparseable summary line. Vietnamese
         * text is 2-3 bytes per char in UTF-8, so these caps keep the whole
         * line comfortably short even in the worst case. A real spoken
         * command never comes close; a stuck ASR returning a paragraph does.
         */
        private const val MAX_UTTERANCE_CHARS = 200
        private const val MAX_INTENT_CHARS = 64

        /**
         * Makes an arbitrary text field safe to put between two '|'.
         *
         * '|' becomes '/' because the harness splits on a fixed field count:
         * one stray pipe inside the utterance shifts intent into the verdict
         * slot and the whole line is rejected as malformed (see the SplitN
         * calls in backend/internal/domain/parse.go). ASR output is not
         * trusted to be pipe-free.
         *
         * CR/LF become spaces: a newline would split one summary across two
         * logcat lines, and neither half parses.
         *
         * An empty field becomes "-" so the field count survives.
         *
         * NOTE - deliberate exception to 03-contracts.md §9 ("khong dau tieng
         * Viet trong Log.i"): Vietnamese diacritics ARE kept here. §9 exists
         * to stop mojibake in prose log messages, but the utterance is
         * evidence - "ha dieu hoa xuong 22 do" with the tones stripped cannot
         * be checked against ground truth, and barem ① scores exactly that
         * ("Tinh dung cua ket qua", 5d). Run `adb logcat` with a UTF-8
         * console; on Windows PowerShell that means `chcp 65001` first.
         */
        internal fun sanitize(raw: String, maxChars: Int): String {
            val cleaned = raw.map { c ->
                when {
                    c == '|' -> '/'
                    c == '\n' || c == '\r' || c == '\t' -> ' '
                    else -> c
                }
            }.joinToString("").trim()
            if (cleaned.isEmpty()) return "-"
            return if (cleaned.length <= maxChars) cleaned else cleaned.take(maxChars)
        }

        /**
         * Renders e2e as whole milliseconds.
         *
         * Integer, not a decimal, and never String.format: the default locale
         * on a Vietnamese device formats "690.0" as "690,0", ParseFloat on
         * the Go side rejects that, and every summary line silently becomes
         * malformed - on the device, in the room, and nowhere else. Sub-
         * millisecond precision buys nothing against a 1500ms budget, and the
         * harness recomputes exact segments from the raw nanos anyway.
         *
         * A turn with no usable marks reports 0; the missing marks in the
         * event lines are what tells the harness it was incomplete.
         */
        internal fun formatE2eMs(ms: Double?): String {
            if (ms == null || ms < 0.0) return "0"
            return Math.round(ms).toString()
        }
    }
}
