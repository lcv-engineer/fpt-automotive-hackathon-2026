package com.viva.voice.trace

/**
 * The nine canonical pipeline stages from vong2/03-contracts.md §1
 * ("Ten chang chuan - khong dat ten khac").
 *
 * This is an enum and not a String on purpose: Tung, Vi and Duong all call
 * [LatencyTrace.mark] from their own modules, and a typo'd stage name would
 * not fail the build - it would silently produce a trace the harness cannot
 * segment, and we would only notice when the benchmark CSV has a hole in it
 * on 02/08. The compiler catches it instead.
 *
 * [id] is the exact string emitted on the wire; never change it without
 * updating 03-contracts.md AND telling Vi, because backend/internal/domain
 * pins these same strings on the parsing side.
 */
enum class Stage(val id: String) {
    SPEECH_START("speech_start"),   // VadSegmenter: bat dau co tieng noi
    // Uoc luong luc nguoi noi DUT CAU. Khac SPEECH_END dung bang minSilenceMs:
    // SPEECH_END la luc VAD *quyet dinh* endpoint, sau khi da cho het im lang.
    // Them moi, khong doi id cu — backend/internal/domain van parse duoc trace cu.
    ACOUSTIC_END("acoustic_end"),   // VadEndpointer: im lang bat dau
    SPEECH_END("speech_end"),       // VadSegmenter: endpoint (VAD quyet dinh)
    ASR_SENT("asr_sent"),           // AsrClient: da gui audio di
    ASR_DONE("asr_done"),           // AsrClient: da nhan text ve
    NLU_DONE("nlu_done"),           // IntentRouter: da ra intent
    GUARD_DONE("guard_done"),       // SafetyGuard: da co phan quyet
    EXEC_DONE("exec_done"),         // Skill: hanh dong da thuc thi xong
    RENDER_DONE("render_done"),     // HMI: frame dau tien phan anh trang thai moi
    TTS_START("tts_start"),         // TtsSpeaker: bat dau phat tieng
}

/**
 * Monotonic nanosecond source.
 *
 * Exists so the whole trace/formatting path stays free of `android.*`: the
 * unit tests below run on a plain JVM with no Robolectric and no device. The
 * Android implementation is [com.viva.voice.trace.SystemNanoClock], which is
 * the only file in this package that imports the framework.
 *
 * Must be monotonic (elapsedRealtimeNanos), never wall-clock: wall-clock can
 * jump backwards on NTP sync mid-utterance and produce negative latencies.
 */
fun interface NanoClock {
    fun nanos(): Long
}

/**
 * Where trace lines go. One line per call, already fully formatted.
 *
 * Split out from [LatencyTrace] so tests can assert on the exact bytes we
 * would have written to logcat, which is the thing Vi's harness actually
 * consumes - asserting on the internal marks map would prove nothing about
 * the wire format.
 */
fun interface TraceSink {
    fun emit(line: String)
}

/** In-memory [TraceSink] for unit tests and for generating golden fixtures. */
class RecordingTraceSink : TraceSink {
    private val _lines = mutableListOf<String>()
    val lines: List<String> get() = _lines.toList()
    override fun emit(line: String) {
        _lines += line
    }
}

/**
 * Non-trace diagnostics (duplicate mark, second summary, unknown stage).
 *
 * Deliberately NOT the same channel as [TraceSink]: a warning about a
 * double-marked stage must never land in the VIVA_TRACE stream, or it ends
 * up as a row in the benchmark CSV. Tag on Android is VIVA_VOICE.
 */
fun interface TraceDiagnostics {
    fun warn(message: String)

    companion object {
        val NONE = TraceDiagnostics { }
    }
}
