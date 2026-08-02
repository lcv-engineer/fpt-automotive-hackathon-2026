package harness

// Comparison is one metric measured in two runs — the ablation table N4a/N4b
// ask for ("2 kịch bản có số before/after"). Baseline is the full system;
// Candidate is the run with one component turned off or swapped.
type Comparison struct {
	Label string

	BaselineN     int
	BaselineP50Ms float64
	BaselineP95Ms float64

	CandidateN     int
	CandidateP50Ms float64
	CandidateP95Ms float64

	// DeltaP95Ms is candidate - baseline. Positive means the candidate is
	// slower. Only meaningful when both sides have samples.
	DeltaP95Ms float64
	Comparable bool
}

// CompareReports lines up two Stat sets by label.
//
// Metrics present in only one run are still emitted, with Comparable=false —
// that asymmetry is usually the interesting part of an ablation (e.g. turning
// off the VHAL callback makes `hmi_render` disappear entirely, which is the
// finding, not a gap in the data).
func CompareReports(baseline, candidate []Stat) []Comparison {
	byLabel := make(map[string]Stat, len(candidate))
	for _, s := range candidate {
		byLabel[s.Label] = s
	}

	out := make([]Comparison, 0, len(baseline)+len(candidate))
	seen := make(map[string]bool, len(baseline))

	for _, b := range baseline {
		seen[b.Label] = true
		c, ok := byLabel[b.Label]
		row := Comparison{
			Label:         b.Label,
			BaselineN:     b.SampleSize,
			BaselineP50Ms: b.P50Ms,
			BaselineP95Ms: b.P95Ms,
		}
		if ok {
			row.CandidateN = c.SampleSize
			row.CandidateP50Ms = c.P50Ms
			row.CandidateP95Ms = c.P95Ms
		}
		row.Comparable = b.SampleSize > 0 && c.SampleSize > 0
		if row.Comparable {
			row.DeltaP95Ms = c.P95Ms - b.P95Ms
		}
		out = append(out, row)
	}

	for _, c := range candidate {
		if seen[c.Label] {
			continue
		}
		out = append(out, Comparison{
			Label:          c.Label,
			CandidateN:     c.SampleSize,
			CandidateP50Ms: c.P50Ms,
			CandidateP95Ms: c.P95Ms,
		})
	}
	return out
}

// VerdictComparison is the same idea for verdict counts — the shape of the
// N4b claim: "with SafetyGuard on, Deny:G1_SPEED_LOCK fired 3 times; with it
// off, 0, and the door unlocked while moving".
type VerdictComparison struct {
	Kind           string
	Rule           string
	BaselineCount  int
	CandidateCount int
	Delta          int
}

// CompareVerdicts lines up two breakdowns by (kind, rule).
func CompareVerdicts(baseline, candidate VerdictBreakdown) []VerdictComparison {
	type key struct{ kind, rule string }
	rows := make(map[key]*VerdictComparison)
	order := make([]key, 0, len(baseline.Rows)+len(candidate.Rows))

	get := func(k key) *VerdictComparison {
		row, ok := rows[k]
		if !ok {
			row = &VerdictComparison{Kind: k.kind, Rule: k.rule}
			rows[k] = row
			order = append(order, k)
		}
		return row
	}

	for _, r := range baseline.Rows {
		get(key{r.Kind, r.Rule}).BaselineCount = r.Count
	}
	for _, r := range candidate.Rows {
		get(key{r.Kind, r.Rule}).CandidateCount = r.Count
	}

	out := make([]VerdictComparison, 0, len(order))
	for _, k := range order {
		row := rows[k]
		row.Delta = row.CandidateCount - row.BaselineCount
		out = append(out, *row)
	}
	return out
}
