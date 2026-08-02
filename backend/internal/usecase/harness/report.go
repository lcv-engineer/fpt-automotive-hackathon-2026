package harness

import (
	"math"
	"sort"

	"viva-tools/internal/domain"
)

// segment is one adjacent stage-to-stage latency derivable from the
// pipeline order in vong2/03-contracts.md §0. From/To are the contract's
// exact stage name strings; Label is our own naming for the report.
type segment struct {
	Label    string
	From, To string
}

// standardSegments covers every adjacent pair in domain.CanonicalStageOrder,
// plus four aggregates:
//   - e2e_computed: speech_end -> tts_start, the contract's definition of
//     end-to-end (§1.3) and the metric p95 < 1500ms is promised on
//   - screen_latency: speech_end -> render_done, the same budget measured to
//     the screen instead of to the first spoken word
//   - action_latency_incl_speech / edge_pipeline_total_incl_speech: the older
//     speech_start-based figures, kept only so earlier reports stay readable
var standardSegments = []segment{
	{"vad_capture", domain.StageSpeechStart, domain.StageSpeechEnd},
	{"asr_dispatch", domain.StageSpeechEnd, domain.StageAsrSent},
	{"asr_processing", domain.StageAsrSent, domain.StageAsrDone},
	{"intent_routing", domain.StageAsrDone, domain.StageNluDone},
	{"safety_guard", domain.StageNluDone, domain.StageGuardDone},
	{"skill_exec", domain.StageGuardDone, domain.StageExecDone},
	{"hmi_render", domain.StageExecDone, domain.StageRenderDone},
	{"tts_kickoff", domain.StageRenderDone, domain.StageTtsStart},

	// ⭐ The two metrics the p95 < 1500ms commitment is actually made on.
	// 03-contracts.md §1.3 defines end-to-end as speech_end -> tts_start:
	// counting from speech_start would add however long the driver spoke, so
	// a long sentence would read as a slow system.
	{"e2e_computed", domain.StageSpeechEnd, domain.StageTtsStart},
	{"screen_latency", domain.StageSpeechEnd, domain.StageRenderDone},

	// Kept for continuity with earlier reports, but they include speaking
	// time — do not quote these as "end-to-end" in the write-up.
	{"action_latency_incl_speech", domain.StageSpeechStart, domain.StageExecDone},
	{"edge_pipeline_total_incl_speech", domain.StageSpeechStart, domain.StageRenderDone},
}

// Stat is a latency distribution over one metric (a stage segment, or the
// app-reported end-to-end figure) across every trace that had data for it.
type Stat struct {
	Label      string
	SampleSize int
	P50Ms      float64
	P95Ms      float64
	MinMs      float64
	MaxMs      float64
}

// BuildReport reduces a set of traces into one Stat per standardSegments
// entry, plus one Stat for the app-reported e2e_ms (taken directly from
// VIVA_TRACE_SUMMARY lines, not recomputed) so the two can be cross-checked
// against each other.
func BuildReport(traces map[string]*domain.Trace) []Stat {
	report := make([]Stat, 0, len(standardSegments)+1)
	for _, seg := range standardSegments {
		var samples []float64
		for _, t := range traces {
			if ms, ok := t.MS(seg.From, seg.To); ok {
				samples = append(samples, ms)
			}
		}
		report = append(report, statFrom(seg.Label, samples))
	}

	var reportedE2E []float64
	for _, t := range traces {
		if t.Summary != nil {
			reportedE2E = append(reportedE2E, t.Summary.E2EMs)
		}
	}
	report = append(report, statFrom("e2e_ms_reported_by_app", reportedE2E))

	// Cross-check: the app computes e2e_ms itself, the harness recomputes it
	// from raw marks. A non-zero spread here means the two definitions of
	// "end-to-end" have drifted apart, and that is worth catching before the
	// number lands on a slide — not after.
	var e2eDelta []float64
	for _, t := range traces {
		if t.Summary == nil {
			continue
		}
		if computed, ok := t.MS(domain.StageSpeechEnd, domain.StageTtsStart); ok {
			e2eDelta = append(e2eDelta, t.Summary.E2EMs-computed)
		}
	}
	report = append(report, statFrom("e2e_reported_minus_computed", e2eDelta))
	return report
}

// SegmentLabels returns the metric labels, in report order. Used by writers
// that need one column per segment.
func SegmentLabels() []string {
	labels := make([]string, 0, len(standardSegments))
	for _, seg := range standardSegments {
		labels = append(labels, seg.Label)
	}
	return labels
}

// TraceRow is one turn, flattened. The aggregate report answers "how fast is
// the system"; this answers "which turn was the slow one" — needed for V11
// (per-utterance PASS/FAIL) and for any claim that points at a specific run
// as evidence (N1 Claim-Evidence Map).
type TraceRow struct {
	TraceID   string
	Variant   string
	Utterance string
	Intent    string

	VerdictKind string
	VerdictRule string

	HasSummary     bool
	E2EReportedMs  float64
	HasE2EComputed bool
	E2EComputedMs  float64

	// Segments holds only the segments this turn actually has both marks for.
	// A missing key means "not measured", which is not the same as zero.
	Segments map[string]float64
}

// BuildTraceRows flattens every trace into one row, sorted by trace id so two
// runs over the same log produce identical files.
func BuildTraceRows(traces map[string]*domain.Trace, variant string) []TraceRow {
	rows := make([]TraceRow, 0, len(traces))
	for id, t := range traces {
		row := TraceRow{TraceID: id, Variant: variant, Segments: map[string]float64{}}
		for _, seg := range standardSegments {
			if ms, ok := t.MS(seg.From, seg.To); ok {
				row.Segments[seg.Label] = ms
			}
		}
		if ms, ok := t.MS(domain.StageSpeechEnd, domain.StageTtsStart); ok {
			row.HasE2EComputed = true
			row.E2EComputedMs = ms
		}
		if t.Summary != nil {
			v, _ := t.Summary.ParsedVerdict()
			rule := v.Detail
			if rule == "" {
				rule = "-"
			}
			row.HasSummary = true
			row.Utterance = t.Summary.Utterance
			row.Intent = t.Summary.Intent
			row.VerdictKind = string(v.Kind)
			row.VerdictRule = rule
			row.E2EReportedMs = t.Summary.E2EMs
		}
		rows = append(rows, row)
	}
	sort.Slice(rows, func(i, j int) bool { return rows[i].TraceID < rows[j].TraceID })
	return rows
}

func statFrom(label string, samples []float64) Stat {
	if len(samples) == 0 {
		return Stat{Label: label}
	}
	sort.Float64s(samples)
	return Stat{
		Label:      label,
		SampleSize: len(samples),
		P50Ms:      percentile(samples, 0.50),
		P95Ms:      percentile(samples, 0.95),
		MinMs:      samples[0],
		MaxMs:      samples[len(samples)-1],
	}
}

// percentile uses nearest-rank on an already-sorted slice — adequate for
// benchmark reporting on the ~20+ utterance samples the plan calls for, not
// a statistically rigorous interpolation method.
func percentile(sorted []float64, p float64) float64 {
	if len(sorted) == 1 {
		return sorted[0]
	}
	rank := int(math.Ceil(p*float64(len(sorted)))) - 1
	if rank < 0 {
		rank = 0
	}
	if rank >= len(sorted) {
		rank = len(sorted) - 1
	}
	return sorted[rank]
}
