package harness

import (
	"testing"

	"viva-tools/internal/domain"
)

func traceWith(id, intent, verdict string, marks map[string]int64) *domain.Trace {
	t := domain.NewTrace(id)
	for stage, nanos := range marks {
		t.AddMark(stage, nanos)
	}
	if verdict != "" {
		t.Summary = &domain.TraceSummary{TraceID: id, Intent: intent, Verdict: verdict, E2EMs: 500}
	}
	return t
}

func TestBuildVerdictBreakdownCountsRulesAndAbandonedTurns(t *testing.T) {
	traces := map[string]*domain.Trace{
		"a": traceWith("a", "door_lock", "Deny:G1_SPEED_LOCK", nil),
		"b": traceWith("b", "door_lock", "Deny:G1_SPEED_LOCK", nil),
		"c": traceWith("c", "hvac_set_temp", "Allow", nil),
		"d": traceWith("d", "", "", map[string]int64{domain.StageSpeechStart: 1}),
	}

	b := BuildVerdictBreakdown(traces)
	if b.TotalTraces != 4 || b.TotalWithVerdict != 3 || b.MissingSummary != 1 {
		t.Fatalf("totals = %d/%d/%d, want 4/3/1", b.TotalTraces, b.TotalWithVerdict, b.MissingSummary)
	}
	// Sorted by count desc, so the two Deny turns come first.
	if b.Rows[0].Kind != "Deny" || b.Rows[0].Rule != "G1_SPEED_LOCK" || b.Rows[0].Count != 2 {
		t.Fatalf("first row = %+v, want Deny/G1_SPEED_LOCK/2", b.Rows[0])
	}
}

func TestBuildIntentBreakdownCountsBlockedTurns(t *testing.T) {
	traces := map[string]*domain.Trace{
		"a": traceWith("a", "door_lock", "Deny:G1_SPEED_LOCK", nil),
		"b": traceWith("b", "door_lock", "Allow", nil),
		// Confirm is waiting on the driver, not blocked.
		"c": traceWith("c", "delivery_confirm", "Confirm:G2_CONFIRM_DELIVERY", nil),
		"d": traceWith("d", "unknown", "Error:asr_done", nil),
	}

	rows := BuildIntentBreakdown(traces)
	byIntent := make(map[string]IntentCount, len(rows))
	for _, r := range rows {
		byIntent[r.Intent] = r
	}
	if got := byIntent["door_lock"]; got.Count != 2 || got.Blocked != 1 {
		t.Errorf("door_lock = %+v, want Count 2 Blocked 1", got)
	}
	if got := byIntent["delivery_confirm"]; got.Blocked != 0 {
		t.Errorf("delivery_confirm Blocked = %d, want 0 (Confirm is not blocked)", got.Blocked)
	}
	if got := byIntent["unknown"]; got.Blocked != 1 {
		t.Errorf("unknown Blocked = %d, want 1 (Error means nothing executed)", got.Blocked)
	}
}

func TestCompareReportsFlagsMetricsThatVanish(t *testing.T) {
	baseline := []Stat{
		{Label: "asr_processing", SampleSize: 10, P50Ms: 300, P95Ms: 400},
		{Label: "hmi_render", SampleSize: 10, P50Ms: 20, P95Ms: 30},
	}
	// Ablation A3: drop the VhalRepository callback and hmi_render never
	// fires. That disappearance is the finding, so it must survive into the
	// table rather than being skipped.
	candidate := []Stat{
		{Label: "asr_processing", SampleSize: 10, P50Ms: 900, P95Ms: 1600},
		{Label: "hmi_render", SampleSize: 0},
	}

	rows := CompareReports(baseline, candidate)
	byLabel := make(map[string]Comparison, len(rows))
	for _, r := range rows {
		byLabel[r.Label] = r
	}

	asr := byLabel["asr_processing"]
	if !asr.Comparable || asr.DeltaP95Ms != 1200 {
		t.Errorf("asr_processing = %+v, want comparable with delta 1200", asr)
	}
	hmi := byLabel["hmi_render"]
	if hmi.Comparable {
		t.Error("hmi_render must be marked not-comparable when the candidate has no samples")
	}
	if hmi.BaselineN != 10 {
		t.Errorf("hmi_render baseline n = %d, want 10 — the row must still carry the before side", hmi.BaselineN)
	}
}

func TestCompareReportsKeepsCandidateOnlyMetrics(t *testing.T) {
	rows := CompareReports(
		[]Stat{{Label: "asr_processing", SampleSize: 3, P95Ms: 100}},
		[]Stat{{Label: "asr_cloud_roundtrip", SampleSize: 3, P95Ms: 900}},
	)
	if len(rows) != 2 {
		t.Fatalf("rows = %d, want 2 (union of both runs)", len(rows))
	}
}

func TestCompareVerdictsShowsRulesThatStoppedFiring(t *testing.T) {
	// The N4b claim in one assertion: with SafetyGuard on the speed lock
	// denies twice; with it off, never — and an Allow appears instead.
	baseline := BuildVerdictBreakdown(map[string]*domain.Trace{
		"a": traceWith("a", "door_lock", "Deny:G1_SPEED_LOCK", nil),
		"b": traceWith("b", "door_lock", "Deny:G1_SPEED_LOCK", nil),
	})
	candidate := BuildVerdictBreakdown(map[string]*domain.Trace{
		"a": traceWith("a", "door_lock", "Allow", nil),
		"b": traceWith("b", "door_lock", "Allow", nil),
	})

	rows := CompareVerdicts(baseline, candidate)
	byKey := make(map[string]VerdictComparison, len(rows))
	for _, r := range rows {
		byKey[r.Kind+"|"+r.Rule] = r
	}
	if got := byKey["Deny|G1_SPEED_LOCK"]; got.BaselineCount != 2 || got.CandidateCount != 0 || got.Delta != -2 {
		t.Errorf("Deny row = %+v, want 2 -> 0 (delta -2)", got)
	}
	if got := byKey["Allow|-"]; got.BaselineCount != 0 || got.CandidateCount != 2 {
		t.Errorf("Allow row = %+v, want 0 -> 2", got)
	}
}
