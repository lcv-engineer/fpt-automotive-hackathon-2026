package harness

import (
	"fmt"
	"sort"
	"strings"

	"viva-tools/internal/domain"
)

// SuiteCase is one row of a benchmark/regression suite: what was said, and
// what the turn is supposed to produce. See backend/suites/benchmark_v1.csv.
type SuiteCase struct {
	ID            string
	Utterance     string
	ExpectIntent  string
	ExpectVerdict string
	EvidenceID    string
	// Gate names the dependency that is not landed yet (e.g. "SafetyGuard T5").
	// A case with a gate that fails is a known gap, not a regression — the two
	// are counted separately so a real break cannot hide behind them.
	Gate  string
	Notes string
}

// KnownGap reports whether this case is blocked on work that has not landed.
func (c SuiteCase) KnownGap() bool { return strings.TrimSpace(c.Gate) != "" }

// CaseStatus is the verdict of the regression runner itself.
type CaseStatus string

const (
	StatusPass    CaseStatus = "PASS"
	StatusFail    CaseStatus = "FAIL"
	StatusMissing CaseStatus = "MISSING" // no turn in the log to match this case
)

// CaseResult pairs a suite case with the turn that answered it.
type CaseResult struct {
	Case   SuiteCase
	Status CaseStatus

	TraceID         string
	ActualIntent    string
	ActualVerdict   string
	ActualUtterance string

	HasE2E bool
	E2EMs  float64

	// Reasons is empty on PASS; on FAIL it lists every mismatch, not just the
	// first, so one run tells you everything that moved.
	Reasons []string
}

// MatchMode decides how suite rows are paired with turns in the log.
type MatchMode string

const (
	// MatchOrder pairs the Nth case with the Nth turn, ordered by first mark.
	// This is the default because the utterance in the log is what ASR heard,
	// not what was said — matching on it would silently drop every turn the
	// recognizer got slightly wrong, which is exactly the data a benchmark
	// exists to measure.
	MatchOrder MatchMode = "order"
	// MatchUtterance pairs on normalized utterance text. Useful when turns
	// were captured out of order, at the cost of losing misrecognized turns.
	MatchUtterance MatchMode = "utterance"
)

// Verify runs a suite against one captured log.
func Verify(cases []SuiteCase, traces map[string]*domain.Trace, mode MatchMode) []CaseResult {
	ordered := orderedTraces(traces)
	results := make([]CaseResult, 0, len(cases))

	used := make(map[string]bool, len(ordered))
	byUtterance := make(map[string][]*domain.Trace)
	if mode == MatchUtterance {
		for _, t := range ordered {
			if t.Summary != nil {
				key := normalizeUtterance(t.Summary.Utterance)
				byUtterance[key] = append(byUtterance[key], t)
			}
		}
	}

	for i, c := range cases {
		var match *domain.Trace
		switch mode {
		case MatchUtterance:
			for _, candidate := range byUtterance[normalizeUtterance(c.Utterance)] {
				if !used[candidate.TraceID] {
					match = candidate
					break
				}
			}
		default:
			if i < len(ordered) {
				match = ordered[i]
			}
		}

		if match == nil {
			results = append(results, CaseResult{
				Case:    c,
				Status:  StatusMissing,
				Reasons: []string{"no turn in the log matched this case"},
			})
			continue
		}
		used[match.TraceID] = true
		results = append(results, evaluate(c, match))
	}
	return results
}

func evaluate(c SuiteCase, t *domain.Trace) CaseResult {
	result := CaseResult{Case: c, TraceID: t.TraceID, Status: StatusPass}
	if ms, ok := t.MS(domain.StageSpeechEnd, domain.StageTtsStart); ok {
		result.HasE2E = true
		result.E2EMs = ms
	}

	if t.Summary == nil {
		result.Status = StatusFail
		result.Reasons = append(result.Reasons,
			"turn produced marks but no VIVA_TRACE_SUMMARY line — outcome unknown")
		return result
	}

	result.ActualIntent = t.Summary.Intent
	result.ActualVerdict = t.Summary.Verdict
	result.ActualUtterance = t.Summary.Utterance

	if c.ExpectIntent != "" && c.ExpectIntent != t.Summary.Intent {
		result.Status = StatusFail
		result.Reasons = append(result.Reasons,
			fmt.Sprintf("intent: want %q, got %q", c.ExpectIntent, t.Summary.Intent))
	}

	if c.ExpectVerdict != "" {
		want, wantErr := domain.ParseVerdict(c.ExpectVerdict)
		got, gotErr := t.Summary.ParsedVerdict()
		switch {
		case wantErr != nil && want.Kind == domain.VerdictUnknown:
			result.Status = StatusFail
			result.Reasons = append(result.Reasons,
				fmt.Sprintf("suite row has an unparseable expected verdict %q: %v", c.ExpectVerdict, wantErr))
		case gotErr != nil && got.Kind == domain.VerdictUnknown:
			result.Status = StatusFail
			result.Reasons = append(result.Reasons,
				fmt.Sprintf("verdict: want %q, got unparseable %q", want.String(), t.Summary.Verdict))
		case want.Kind != got.Kind:
			result.Status = StatusFail
			result.Reasons = append(result.Reasons,
				fmt.Sprintf("verdict kind: want %s, got %s", want.Kind, got.Kind))
		case want.Detail != "" && want.Detail != got.Detail:
			// A Deny with the wrong rule id is a different behaviour, not a
			// near miss: N4b's before/after table joins on exactly this field.
			result.Status = StatusFail
			result.Reasons = append(result.Reasons,
				fmt.Sprintf("verdict rule: want %s, got %s", want.Detail, orDash(got.Detail)))
		}
	}

	return result
}

// SuiteSummary is the headline of a regression run.
type SuiteSummary struct {
	Total   int
	Pass    int
	Fail    int
	Missing int

	// KnownGapFail counts failures on cases that declared a Gate. Reported
	// separately so "18/22, 4 of them known gaps" cannot be rounded up to
	// "everything passes" — nor down to "the build is broken".
	KnownGapFail int

	P50Ms float64
	P95Ms float64
	E2EN  int
}

// Summarize reduces results to counts plus the latency distribution over the
// turns that actually produced an end-to-end measurement.
func Summarize(results []CaseResult) SuiteSummary {
	s := SuiteSummary{Total: len(results)}
	var samples []float64
	for _, r := range results {
		switch r.Status {
		case StatusPass:
			s.Pass++
		case StatusMissing:
			s.Missing++
			if r.Case.KnownGap() {
				s.KnownGapFail++
			}
		default:
			s.Fail++
			if r.Case.KnownGap() {
				s.KnownGapFail++
			}
		}
		if r.HasE2E {
			samples = append(samples, r.E2EMs)
		}
	}
	if len(samples) > 0 {
		sort.Float64s(samples)
		s.E2EN = len(samples)
		s.P50Ms = percentile(samples, 0.50)
		s.P95Ms = percentile(samples, 0.95)
	}
	return s
}

// RegressionPassed reports whether the run may be called green: every case
// without a declared gate passed.
func RegressionPassed(results []CaseResult) bool {
	for _, r := range results {
		if r.Status != StatusPass && !r.Case.KnownGap() {
			return false
		}
	}
	return true
}

func orderedTraces(traces map[string]*domain.Trace) []*domain.Trace {
	ordered := make([]*domain.Trace, 0, len(traces))
	for _, t := range traces {
		ordered = append(ordered, t)
	}
	sort.Slice(ordered, func(i, j int) bool {
		fi, oki := firstMark(ordered[i])
		fj, okj := firstMark(ordered[j])
		if oki && okj && fi != fj {
			return fi < fj
		}
		// Traces with no marks (summary only) sort last but deterministically.
		if oki != okj {
			return oki
		}
		return ordered[i].TraceID < ordered[j].TraceID
	})
	return ordered
}

func firstMark(t *domain.Trace) (int64, bool) {
	first := int64(0)
	found := false
	for _, nanos := range t.Marks {
		if !found || nanos < first {
			first = nanos
			found = true
		}
	}
	return first, found
}

// normalizeUtterance folds the differences that do not change what was said:
// case, surrounding space, repeated space. Diacritics are deliberately NOT
// stripped — losing them would make "mở cửa" and "mo cua" the same string, and
// the whole point of keeping diacritics in the log (03-contracts.md §1.1) is
// that the utterance is evidence.
func normalizeUtterance(s string) string {
	return strings.Join(strings.Fields(strings.ToLower(strings.TrimSpace(s))), " ")
}

func orDash(s string) string {
	if s == "" {
		return "-"
	}
	return s
}
