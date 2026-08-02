package report

import (
	"encoding/csv"
	"fmt"
	"os"
	"strconv"

	"viva-tools/internal/usecase/harness"
)

// WriteTraceCSV writes one row per turn: identity, verdict, and every segment
// that turn actually measured. Missing measurements are "-", never 0.
//
// Vietnamese utterances and commas are handled by encoding/csv (RFC 4180
// quoting) — never by string concatenation.
func WriteTraceCSV(rows []harness.TraceRow, path string) error {
	return writeCSV(path, func(w *csv.Writer) error {
		segments := harness.SegmentLabels()
		header := append([]string{
			"trace_id", "variant", "utterance", "intent",
			"verdict_kind", "verdict_rule",
			"e2e_reported_ms", "e2e_computed_ms",
		}, segments...)
		if err := w.Write(header); err != nil {
			return err
		}
		for _, r := range rows {
			record := []string{
				r.TraceID,
				dashIfEmpty(r.Variant),
				dashIfEmpty(r.Utterance),
				dashIfEmpty(r.Intent),
				dashIfEmpty(r.VerdictKind),
				dashIfEmpty(r.VerdictRule),
				floatOrDash(r.HasSummary, r.E2EReportedMs),
				floatOrDash(r.HasE2EComputed, r.E2EComputedMs),
			}
			for _, label := range segments {
				ms, ok := r.Segments[label]
				record = append(record, floatOrDash(ok, ms))
			}
			if err := w.Write(record); err != nil {
				return err
			}
		}
		return nil
	})
}

// WriteVerdictCSV writes the verdict breakdown — the raw material for the
// ablation claims in N4a/N4b.
func WriteVerdictCSV(b harness.VerdictBreakdown, path string) error {
	return writeCSV(path, func(w *csv.Writer) error {
		if err := w.Write([]string{"verdict_kind", "rule_or_stage", "count", "share_pct"}); err != nil {
			return err
		}
		for _, row := range b.Rows {
			share := "-"
			if b.TotalWithVerdict > 0 {
				share = strconv.FormatFloat(100*float64(row.Count)/float64(b.TotalWithVerdict), 'f', 1, 64)
			}
			if err := w.Write([]string{row.Kind, row.Rule, strconv.Itoa(row.Count), share}); err != nil {
				return err
			}
		}
		// Abandoned turns are a row of their own, not folded into any kind:
		// "we never found out how it ended" is its own outcome.
		if b.MissingSummary > 0 {
			if err := w.Write([]string{"NoSummary", "-", strconv.Itoa(b.MissingSummary), "-"}); err != nil {
				return err
			}
		}
		return nil
	})
}

// WriteCompareCSV writes the before/after latency table for an ablation run.
func WriteCompareCSV(rows []harness.Comparison, baselineLabel, candidateLabel, path string) error {
	return writeCSV(path, func(w *csv.Writer) error {
		header := []string{
			"label",
			baselineLabel + "_n", baselineLabel + "_p50_ms", baselineLabel + "_p95_ms",
			candidateLabel + "_n", candidateLabel + "_p50_ms", candidateLabel + "_p95_ms",
			"delta_p95_ms",
		}
		if err := w.Write(header); err != nil {
			return err
		}
		for _, r := range rows {
			if err := w.Write([]string{
				r.Label,
				strconv.Itoa(r.BaselineN),
				fmtOrDash(r.BaselineN, r.BaselineP50Ms),
				fmtOrDash(r.BaselineN, r.BaselineP95Ms),
				strconv.Itoa(r.CandidateN),
				fmtOrDash(r.CandidateN, r.CandidateP50Ms),
				fmtOrDash(r.CandidateN, r.CandidateP95Ms),
				floatOrDash(r.Comparable, r.DeltaP95Ms),
			}); err != nil {
				return err
			}
		}
		return nil
	})
}

// WriteVerdictCompareCSV writes the before/after verdict counts.
func WriteVerdictCompareCSV(rows []harness.VerdictComparison, baselineLabel, candidateLabel, path string) error {
	return writeCSV(path, func(w *csv.Writer) error {
		if err := w.Write([]string{
			"verdict_kind", "rule_or_stage",
			baselineLabel + "_count", candidateLabel + "_count", "delta",
		}); err != nil {
			return err
		}
		for _, r := range rows {
			if err := w.Write([]string{
				r.Kind, r.Rule,
				strconv.Itoa(r.BaselineCount),
				strconv.Itoa(r.CandidateCount),
				strconv.Itoa(r.Delta),
			}); err != nil {
				return err
			}
		}
		return nil
	})
}

func writeCSV(path string, body func(*csv.Writer) error) error {
	f, err := os.Create(path)
	if err != nil {
		return fmt.Errorf("create %s: %w", path, err)
	}
	defer f.Close()

	w := csv.NewWriter(f)
	if err := body(w); err != nil {
		return fmt.Errorf("write %s: %w", path, err)
	}
	w.Flush()
	if err := w.Error(); err != nil {
		return fmt.Errorf("flush %s: %w", path, err)
	}
	return nil
}

func dashIfEmpty(s string) string {
	if s == "" {
		return "-"
	}
	return s
}

func floatOrDash(present bool, v float64) string {
	if !present {
		return "-"
	}
	return strconv.FormatFloat(v, 'f', 2, 64)
}
