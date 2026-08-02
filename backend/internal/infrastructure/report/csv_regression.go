package report

import (
	"encoding/csv"
	"strconv"
	"strings"

	"viva-tools/internal/usecase/harness"
)

// WriteSuiteResultsCSV writes one row per suite case.
//
// evidence maps evidence_id -> resolved file path; ids with no file get "-",
// so a missing screenshot is visible in the artifact instead of being implied
// by its absence.
func WriteSuiteResultsCSV(results []harness.CaseResult, evidence map[string]string, variant, path string) error {
	return writeCSV(path, func(w *csv.Writer) error {
		header := []string{
			"id", "variant", "status", "known_gap",
			"utterance_expected", "utterance_heard",
			"intent_expected", "intent_actual",
			"verdict_expected", "verdict_actual",
			"e2e_ms", "trace_id", "evidence", "reasons", "gate",
		}
		if err := w.Write(header); err != nil {
			return err
		}
		for _, r := range results {
			knownGap := "no"
			if r.Case.KnownGap() {
				knownGap = "yes"
			}
			if err := w.Write([]string{
				r.Case.ID,
				dashIfEmpty(variant),
				string(r.Status),
				knownGap,
				dashIfEmpty(r.Case.Utterance),
				dashIfEmpty(r.ActualUtterance),
				dashIfEmpty(r.Case.ExpectIntent),
				dashIfEmpty(r.ActualIntent),
				dashIfEmpty(r.Case.ExpectVerdict),
				dashIfEmpty(r.ActualVerdict),
				floatOrDash(r.HasE2E, r.E2EMs),
				dashIfEmpty(r.TraceID),
				dashIfEmpty(evidence[r.Case.EvidenceID]),
				dashIfEmpty(strings.Join(r.Reasons, "; ")),
				dashIfEmpty(r.Case.Gate),
			}); err != nil {
				return err
			}
		}
		return nil
	})
}

// WriteSuiteSummaryCSV writes the one-line headline of a regression run, so a
// series of runs (quiet / cabin / highway for V12) can be concatenated.
func WriteSuiteSummaryCSV(s harness.SuiteSummary, variant, path string) error {
	return writeCSV(path, func(w *csv.Writer) error {
		if err := w.Write([]string{
			"variant", "total", "pass", "fail", "missing", "known_gap_fail",
			"pass_pct", "e2e_n", "e2e_p50_ms", "e2e_p95_ms",
		}); err != nil {
			return err
		}
		passPct := "-"
		if s.Total > 0 {
			passPct = strconv.FormatFloat(100*float64(s.Pass)/float64(s.Total), 'f', 1, 64)
		}
		return w.Write([]string{
			dashIfEmpty(variant),
			strconv.Itoa(s.Total),
			strconv.Itoa(s.Pass),
			strconv.Itoa(s.Fail),
			strconv.Itoa(s.Missing),
			strconv.Itoa(s.KnownGapFail),
			passPct,
			strconv.Itoa(s.E2EN),
			fmtOrDash(s.E2EN, s.P50Ms),
			fmtOrDash(s.E2EN, s.P95Ms),
		})
	})
}
