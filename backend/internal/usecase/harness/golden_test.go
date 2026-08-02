package harness

import (
	"path/filepath"
	"testing"

	"viva-tools/internal/infrastructure/logsource"
)

// The fixtures Long handed over in android/voice/fixtures/README.md are the
// acceptance test for the harness ("Kỳ vọng: 4 trace, 0 warning" and, for the
// edge file, "đúng 4 warning, không crash, mốc hợp lệ vẫn còn"). Asserting
// against them here means a format drift on either side fails `go test`
// instead of failing at 20:00 on benchmark day.
func fixture(t *testing.T, name string) logsource.FileSource {
	t.Helper()
	return logsource.FileSource{Path: filepath.Join("..", "..", "..", "..", "android", "voice", "fixtures", name)}
}

func TestGoldenTrace(t *testing.T) {
	result, err := Aggregate(fixture(t, "golden_trace.log"))
	if err != nil {
		t.Fatalf("Aggregate: %v", err)
	}
	if len(result.Traces) != 4 {
		t.Errorf("traces = %d, want 4", len(result.Traces))
	}
	if len(result.Warnings) != 0 {
		t.Errorf("warnings = %v, want none", result.Warnings)
	}

	breakdown := BuildVerdictBreakdown(result.Traces)
	want := map[string]int{
		"Allow|-":                     1,
		"Deny|G1_SPEED_LOCK":          1,
		"Confirm|G2_CONFIRM_DELIVERY": 1,
		"Error|asr_done":              1,
	}
	got := make(map[string]int, len(breakdown.Rows))
	for _, row := range breakdown.Rows {
		got[row.Kind+"|"+row.Rule] = row.Count
	}
	for k, n := range want {
		if got[k] != n {
			t.Errorf("verdict %s = %d, want %d (all rows: %v)", k, got[k], n, got)
		}
	}
	if breakdown.MissingSummary != 0 {
		t.Errorf("MissingSummary = %d, want 0", breakdown.MissingSummary)
	}
}

func TestGoldenTraceE2EMatchesReportedValue(t *testing.T) {
	// fixtures/README.md: "e2e_ms khai trong summary phải khớp
	// tts_start − speech_end tính lại từ mốc thô (sai số < 0.5ms)".
	result, err := Aggregate(fixture(t, "golden_trace.log"))
	if err != nil {
		t.Fatalf("Aggregate: %v", err)
	}
	for _, row := range BuildTraceRows(result.Traces, "golden") {
		if !row.HasSummary || !row.HasE2EComputed {
			continue // the Error turn has no tts_start, by design
		}
		if diff := row.E2EReportedMs - row.E2EComputedMs; diff > 0.5 || diff < -0.5 {
			t.Errorf("trace %s: reported e2e %.1fms vs computed %.1fms (diff %.1f)",
				row.TraceID, row.E2EReportedMs, row.E2EComputedMs, diff)
		}
	}
}

func TestGoldenTraceEdgeCases(t *testing.T) {
	result, err := Aggregate(fixture(t, "golden_trace_edge.log"))
	if err != nil {
		t.Fatalf("Aggregate: %v", err)
	}
	if len(result.Traces) != 7 {
		t.Errorf("traces = %d, want 7", len(result.Traces))
	}
	if len(result.Warnings) != 4 {
		t.Errorf("warnings = %d, want exactly 4: %v", len(result.Warnings), result.Warnings)
	}

	// The four malformed lines must not take valid data down with them: the
	// same traceId also emitted a good nlu_done mark.
	malformed, ok := result.Traces["edge-malformed"]
	if !ok {
		t.Fatal("trace edge-malformed missing entirely")
	}
	if _, ok := malformed.Marks["nlu_done"]; !ok {
		t.Error("valid nlu_done mark was dropped along with the malformed lines")
	}

	// A turn with marks but no summary is kept, and counted as abandoned
	// rather than folded into a verdict kind.
	if _, ok := result.Traces["edge-abandoned-no-summary"]; !ok {
		t.Error("abandoned turn was dropped; it must stay in the sample")
	}
	// Two: the deliberately abandoned turn, and edge-malformed — both of whose
	// summary lines are corrupt, so it ends up with marks and no verdict. That
	// is the honest count; folding it into "Allow" would invent an outcome.
	if n := BuildVerdictBreakdown(result.Traces).MissingSummary; n != 2 {
		t.Errorf("MissingSummary = %d, want 2", n)
	}

	// The utterance containing a pipe (already rewritten to "/" by the
	// emitter) must not push fields sideways.
	pipe := result.Traces["edge-pipe-in-utterance"]
	if pipe == nil || pipe.Summary == nil {
		t.Fatal("edge-pipe-in-utterance has no summary")
	}
	if pipe.Summary.Intent != "door_lock" {
		t.Errorf("intent = %q, want door_lock (field shifted?)", pipe.Summary.Intent)
	}
}
