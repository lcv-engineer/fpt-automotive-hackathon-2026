package harness

import (
	"strings"
	"testing"

	"viva-tools/internal/domain"
)

func turn(id, utterance, intent, verdict string, speechEndNanos, ttsStartNanos int64) *domain.Trace {
	t := domain.NewTrace(id)
	t.AddMark(domain.StageSpeechEnd, speechEndNanos)
	t.AddMark(domain.StageTtsStart, ttsStartNanos)
	t.Summary = &domain.TraceSummary{
		TraceID:   id,
		Utterance: utterance,
		Intent:    intent,
		Verdict:   verdict,
		E2EMs:     float64(ttsStartNanos-speechEndNanos) / 1_000_000,
	}
	return t
}

func TestVerifyMatchesByOrderNotByRecognizedText(t *testing.T) {
	cases := []SuiteCase{
		{ID: "B1", Utterance: "hạ điều hòa xuống 22 độ", ExpectIntent: "hvac_set_temp", ExpectVerdict: "Allow"},
		{ID: "B2", Utterance: "khóa cửa", ExpectIntent: "door_lock", ExpectVerdict: "Allow"},
	}
	traces := map[string]*domain.Trace{
		// ASR misheard the first one. Order matching must still pair it, so the
		// mistake shows up as data instead of disappearing from the run.
		"t1": turn("t1", "hạ điều hoà xuống hai hai độ", "hvac_set_temp", "Allow", 1_000_000_000, 1_400_000_000),
		"t2": turn("t2", "khóa cửa", "door_lock", "Allow", 5_000_000_000, 5_300_000_000),
	}

	results := Verify(cases, traces, MatchOrder)

	if results[0].Status != StatusPass {
		t.Errorf("case B1 = %s (%v), want PASS despite the misrecognized utterance",
			results[0].Status, results[0].Reasons)
	}
	if results[0].ActualUtterance != "hạ điều hoà xuống hai hai độ" {
		t.Errorf("heard utterance = %q, want it carried through for WER review", results[0].ActualUtterance)
	}
	if results[1].TraceID != "t2" {
		t.Errorf("case B2 paired with %q, want t2", results[1].TraceID)
	}
}

func TestVerifyReportsEveryMismatchNotJustTheFirst(t *testing.T) {
	cases := []SuiteCase{
		{ID: "B1", Utterance: "mở cửa", ExpectIntent: "door_lock", ExpectVerdict: "Deny:G1_SPEED_LOCK"},
	}
	traces := map[string]*domain.Trace{
		"t1": turn("t1", "mở cửa", "unknown", "Allow", 0, 500_000_000),
	}

	r := Verify(cases, traces, MatchOrder)[0]

	if r.Status != StatusFail {
		t.Fatalf("status = %s, want FAIL", r.Status)
	}
	joined := strings.Join(r.Reasons, " | ")
	if !strings.Contains(joined, "intent") || !strings.Contains(joined, "verdict") {
		t.Errorf("reasons = %q, want both the intent and the verdict mismatch", joined)
	}
}

func TestVerifyTreatsTheWrongRuleIdAsAFailure(t *testing.T) {
	// A Deny with the wrong rule is a different behaviour: N4b's ablation
	// table joins on exactly this field.
	cases := []SuiteCase{
		{ID: "B1", Utterance: "mở cửa", ExpectIntent: "door_lock", ExpectVerdict: "Deny:G1_SPEED_LOCK"},
	}
	traces := map[string]*domain.Trace{
		"t1": turn("t1", "mở cửa", "door_lock", "Deny:G1_GEAR_LOCK", 0, 400_000_000),
	}

	r := Verify(cases, traces, MatchOrder)[0]

	if r.Status != StatusFail {
		t.Fatalf("status = %s, want FAIL", r.Status)
	}
	if !strings.Contains(strings.Join(r.Reasons, " "), "G1_SPEED_LOCK") {
		t.Errorf("reasons = %v, want the expected rule id named", r.Reasons)
	}
}

func TestVerifyAcceptsAnyRuleWhenTheSuiteOnlyNamesAKind(t *testing.T) {
	cases := []SuiteCase{
		{ID: "B1", Utterance: "mở cửa", ExpectIntent: "door_lock", ExpectVerdict: "Deny"},
	}
	traces := map[string]*domain.Trace{
		"t1": turn("t1", "mở cửa", "door_lock", "Deny:G1_GEAR_LOCK", 0, 400_000_000),
	}

	if r := Verify(cases, traces, MatchOrder)[0]; r.Status != StatusPass {
		t.Errorf("status = %s (%v), want PASS — a bare kind must not pin the rule", r.Status, r.Reasons)
	}
}

func TestVerifyMarksUnmatchedCasesMissing(t *testing.T) {
	cases := []SuiteCase{
		{ID: "B1", Utterance: "khóa cửa", ExpectIntent: "door_lock", ExpectVerdict: "Allow"},
		{ID: "B2", Utterance: "quạt mức 2", ExpectIntent: "hvac_set_fan", ExpectVerdict: "Allow"},
	}
	traces := map[string]*domain.Trace{
		"t1": turn("t1", "khóa cửa", "door_lock", "Allow", 0, 300_000_000),
	}

	results := Verify(cases, traces, MatchOrder)

	if results[1].Status != StatusMissing {
		t.Errorf("status = %s, want MISSING when the run has fewer turns than the suite", results[1].Status)
	}
}

func TestVerifyByUtteranceDoesNotReuseOneTurnTwice(t *testing.T) {
	// "xác nhận giao thành công đơn A12" is said twice on purpose (ask, then
	// confirm). Each case must consume its own turn.
	cases := []SuiteCase{
		{ID: "B18", Utterance: "xác nhận giao", ExpectIntent: "delivery_confirm", ExpectVerdict: "Confirm:G2_CONFIRM_DELIVERY"},
		{ID: "B19", Utterance: "xác nhận giao", ExpectIntent: "delivery_confirm", ExpectVerdict: "Allow"},
	}
	traces := map[string]*domain.Trace{
		"t1": turn("t1", "xác nhận giao", "delivery_confirm", "Confirm:G2_CONFIRM_DELIVERY", 0, 300_000_000),
		"t2": turn("t2", "xác nhận giao", "delivery_confirm", "Allow", 5_000_000_000, 5_300_000_000),
	}

	results := Verify(cases, traces, MatchUtterance)

	if results[0].TraceID == results[1].TraceID {
		t.Fatalf("both cases matched trace %q; each turn may answer one case only", results[0].TraceID)
	}
	for _, r := range results {
		if r.Status != StatusPass {
			t.Errorf("case %s = %s (%v), want PASS", r.Case.ID, r.Status, r.Reasons)
		}
	}
}

func TestKnownGapsDoNotFailTheRunButAreCounted(t *testing.T) {
	cases := []SuiteCase{
		{ID: "B1", Utterance: "khóa cửa", ExpectIntent: "door_lock", ExpectVerdict: "Allow"},
		{ID: "B10", Utterance: "mở cửa", ExpectIntent: "door_lock", ExpectVerdict: "Deny:G1_SPEED_LOCK", Gate: "SafetyGuard T5"},
	}
	traces := map[string]*domain.Trace{
		"t1": turn("t1", "khóa cửa", "door_lock", "Allow", 0, 300_000_000),
		"t2": turn("t2", "mở cửa", "door_lock", "Allow", 5_000_000_000, 5_400_000_000),
	}

	results := Verify(cases, traces, MatchOrder)
	summary := Summarize(results)

	if summary.Pass != 1 || summary.Fail != 1 || summary.KnownGapFail != 1 {
		t.Errorf("summary = %+v, want 1 pass / 1 fail / 1 known gap", summary)
	}
	if !RegressionPassed(results) {
		t.Error("a failure on a declared gap must not make the run red — it is not a regression")
	}
}

func TestRegressionFailsOnAnUngatedBreak(t *testing.T) {
	cases := []SuiteCase{{ID: "B1", Utterance: "khóa cửa", ExpectIntent: "door_lock", ExpectVerdict: "Allow"}}
	traces := map[string]*domain.Trace{
		"t1": turn("t1", "khóa cửa", "unknown", "Error:nlu_done", 0, 300_000_000),
	}

	if RegressionPassed(Verify(cases, traces, MatchOrder)) {
		t.Error("an ungated failure must make the run red")
	}
}

func TestSummarizeMeasuresE2EOverTurnsThatHaveIt(t *testing.T) {
	cases := []SuiteCase{
		{ID: "B1", Utterance: "a", ExpectIntent: "door_lock", ExpectVerdict: "Allow"},
		{ID: "B2", Utterance: "b", ExpectIntent: "door_lock", ExpectVerdict: "Allow"},
	}
	traces := map[string]*domain.Trace{
		"t1": turn("t1", "a", "door_lock", "Allow", 0, 300_000_000),
		"t2": turn("t2", "b", "door_lock", "Allow", 5_000_000_000, 5_900_000_000),
	}

	s := Summarize(Verify(cases, traces, MatchOrder))

	if s.E2EN != 2 || s.P95Ms != 900 {
		t.Errorf("summary = %+v, want 2 samples with p95 900ms", s)
	}
}

func TestSummaryLineOfATurnWithoutOneIsAFailure(t *testing.T) {
	// A turn with marks but no summary never declared how it ended. Passing it
	// would let an abandoned turn count as a success.
	abandoned := domain.NewTrace("t1")
	abandoned.AddMark(domain.StageSpeechEnd, 0)

	r := Verify(
		[]SuiteCase{{ID: "B1", Utterance: "khóa cửa", ExpectIntent: "door_lock"}},
		map[string]*domain.Trace{"t1": abandoned},
		MatchOrder,
	)[0]

	if r.Status != StatusFail {
		t.Errorf("status = %s, want FAIL for a turn with no summary line", r.Status)
	}
}
