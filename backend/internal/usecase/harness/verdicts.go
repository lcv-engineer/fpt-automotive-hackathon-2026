package harness

import (
	"sort"

	"viva-tools/internal/domain"
)

// VerdictCount is one row of the verdict breakdown: how many turns ended in
// a given kind, optionally attributed to a rule id.
type VerdictCount struct {
	Kind  string // Allow / Deny / Confirm / Error / Unknown
	Rule  string // RULE_ID or STAGE_ID; "-" for Allow
	Count int
}

// VerdictBreakdown is what makes the ablation tables (N4a/N4b) a group-by
// instead of a manual demo replay: "with SafetyGuard on, 3 turns ended
// Deny:G1_SPEED_LOCK; with it off, 0 did and the door opened at 60 km/h".
type VerdictBreakdown struct {
	Rows             []VerdictCount
	TotalWithVerdict int
	TotalTraces      int
	// MissingSummary counts turns that produced marks but no summary line —
	// abandoned turns. They are reported rather than folded into any kind,
	// because "we never found out how it ended" is its own outcome.
	MissingSummary int
}

// BuildVerdictBreakdown groups traces by (verdict kind, rule id).
func BuildVerdictBreakdown(traces map[string]*domain.Trace) VerdictBreakdown {
	type key struct{ kind, rule string }
	counts := make(map[key]int)
	out := VerdictBreakdown{TotalTraces: len(traces)}

	for _, t := range traces {
		if t.Summary == nil {
			out.MissingSummary++
			continue
		}
		v, _ := t.Summary.ParsedVerdict() // Unknown on error; still counted
		rule := v.Detail
		if rule == "" {
			rule = "-"
		}
		counts[key{string(v.Kind), rule}]++
		out.TotalWithVerdict++
	}

	for k, n := range counts {
		out.Rows = append(out.Rows, VerdictCount{Kind: k.kind, Rule: k.rule, Count: n})
	}
	// Deterministic order: biggest group first, then alphabetical, so two
	// runs of the same log produce byte-identical CSVs (map order is random
	// in Go, and a diffable artifact is worth more than the nanoseconds).
	sort.Slice(out.Rows, func(i, j int) bool {
		if out.Rows[i].Count != out.Rows[j].Count {
			return out.Rows[i].Count > out.Rows[j].Count
		}
		if out.Rows[i].Kind != out.Rows[j].Kind {
			return out.Rows[i].Kind < out.Rows[j].Kind
		}
		return out.Rows[i].Rule < out.Rows[j].Rule
	})
	return out
}

// IntentCount is one row of the per-intent breakdown.
type IntentCount struct {
	Intent  string
	Count   int
	Blocked int // Deny or Error — the turn never executed
}

// BuildIntentBreakdown groups turns by intent name. 16-QUYET-DINH-DUONG-NLU.md
// requires the benchmark to keep grammar-covered utterances separate from the
// ones that fall through to the embedding path, because the two cost very
// different amounts; per-intent counts are the first cut of that.
func BuildIntentBreakdown(traces map[string]*domain.Trace) []IntentCount {
	counts := make(map[string]*IntentCount)
	for _, t := range traces {
		if t.Summary == nil {
			continue
		}
		row, ok := counts[t.Summary.Intent]
		if !ok {
			row = &IntentCount{Intent: t.Summary.Intent}
			counts[t.Summary.Intent] = row
		}
		row.Count++
		if v, _ := t.Summary.ParsedVerdict(); v.Blocked() {
			row.Blocked++
		}
	}

	out := make([]IntentCount, 0, len(counts))
	for _, row := range counts {
		out = append(out, *row)
	}
	sort.Slice(out, func(i, j int) bool {
		if out[i].Count != out[j].Count {
			return out[i].Count > out[j].Count
		}
		return out[i].Intent < out[j].Intent
	})
	return out
}
