// Package domain holds entities and business rules with zero dependency on
// any framework, transport, or storage detail. Nothing here imports
// net/http, os, or any infrastructure package.
package domain

// Stage names are fixed by vong2/03-contracts.md §1 ("Tên chặng chuẩn —
// không đặt tên khác"). Do not rename without updating the contract and
// telling the team, since Long's Kotlin code emits these exact strings.
const (
	StageSpeechStart = "speech_start" // VadSegmenter: bắt đầu có tiếng nói
	StageSpeechEnd   = "speech_end"   // VadSegmenter: endpoint (nói xong)
	StageAsrSent     = "asr_sent"     // AsrClient: đã gửi audio đi
	StageAsrDone     = "asr_done"     // AsrClient: đã nhận text về
	StageNluDone     = "nlu_done"     // IntentRouter: đã ra intent
	StageGuardDone   = "guard_done"   // SafetyGuard: đã có phán quyết
	StageExecDone    = "exec_done"    // Skill: hành động đã thực thi xong
	StageRenderDone  = "render_done"  // HMI: frame đầu tiên phản ánh trạng thái mới
	StageTtsStart    = "tts_start"    // TtsSpeaker: bắt đầu phát tiếng
)

// CanonicalStageOrder is the sequence stages occur in during one turn, per
// the pipeline diagram in 03-contracts.md §0. Used to derive adjacent
// latency segments (e.g. asr_sent -> asr_done = ASR processing time).
var CanonicalStageOrder = []string{
	StageSpeechStart,
	StageSpeechEnd,
	StageAsrSent,
	StageAsrDone,
	StageNluDone,
	StageGuardDone,
	StageExecDone,
	StageRenderDone,
	StageTtsStart,
}

// TraceEvent is one parsed "VIVA_TRACE|<traceId>|<stage>|<elapsedRealtimeNanos>" line.
type TraceEvent struct {
	TraceID              string
	Stage                string
	ElapsedRealtimeNanos int64
}

// TraceSummary is one parsed
// "VIVA_TRACE_SUMMARY|<traceId>|<utterance>|<intent>|<verdict>|e2e_ms=<số>" line.
//
// Verdict is kept as the raw string; call ParsedVerdict for the structured
// form. The serialization was pinned down in 03-contracts.md §1.2 on 29/07
// (it answers the open question this type used to carry).
type TraceSummary struct {
	TraceID   string
	Utterance string
	Intent    string
	Verdict   string
	E2EMs     float64
}

// ParsedVerdict splits the raw verdict field per the §1.2 grammar.
func (s TraceSummary) ParsedVerdict() (Verdict, error) {
	return ParseVerdict(s.Verdict)
}

// Trace aggregates every mark seen for one traceId, plus its summary line
// if one arrived (a trace with marks but no summary means the turn never
// completed, or the summary line hasn't been read yet).
type Trace struct {
	TraceID string
	Marks   map[string]int64 // stage name -> elapsedRealtimeNanos
	Summary *TraceSummary
}

// NewTrace returns an empty Trace ready to accumulate marks.
func NewTrace(traceID string) *Trace {
	return &Trace{TraceID: traceID, Marks: make(map[string]int64)}
}

// AddMark records one stage timestamp and reports whether it was accepted.
//
// First value wins. 03-contracts.md §1 fixes the emitter's behaviour as "mỗi
// stage in 1 lần (ghi đè = bỏ qua)", and the reason given there applies here
// too: a stage marked twice SHORTENS the measured segment, so overwriting
// would make p95 prettier the more buggy the app is. The harness mirrors the
// emitter rather than quietly disagreeing with it.
func (t *Trace) AddMark(stage string, nanos int64) bool {
	if _, exists := t.Marks[stage]; exists {
		return false
	}
	t.Marks[stage] = nanos
	return true
}

// MS returns the elapsed milliseconds between two marks, mirroring the
// Kotlin LatencyTrace.ms(from, to) helper in 03-contracts.md §1. Returns
// false if either mark is missing (e.g. that stage never fired for this turn).
func (t *Trace) MS(from, to string) (float64, bool) {
	f, ok1 := t.Marks[from]
	toV, ok2 := t.Marks[to]
	if !ok1 || !ok2 {
		return 0, false
	}
	return float64(toV-f) / 1_000_000.0, true
}
