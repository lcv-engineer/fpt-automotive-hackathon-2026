package cli

import (
	"flag"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"viva-tools/internal/infrastructure/logsource"
	"viva-tools/internal/infrastructure/report"
	"viva-tools/internal/infrastructure/suite"
	"viva-tools/internal/interfaces/repository"
	"viva-tools/internal/usecase/harness"
)

const harnessUsage = `usage: viva-tools harness <command>

  report   turn a captured VIVA_TRACE log into latency stats (V8/V10/V12)
  compare  diff two runs — baseline vs ablation (N4a/N4b)
  verify   run a benchmark suite against a capture, PASS/FAIL per case (V11)

  viva-tools harness report --input <path|-> [--out report.csv]
                            [--per-trace traces.csv] [--verdicts verdicts.csv]
                            [--variant <name>]
  viva-tools harness report --adb [--serial <id>] [--out report.csv]
  viva-tools harness compare --baseline <log> --candidate <log>
                             [--baseline-label full] [--candidate-label no-guard]
                             [--out compare.csv] [--verdicts-out verdicts_compare.csv]
  viva-tools harness verify --suite suites/benchmark_v1.csv --input run.log
                            [--variant quiet] [--match order|utterance]
                            [--out results.csv] [--summary-out summary.csv]
                            [--evidence-dir screenshots/]
`

func runHarness(args []string) int {
	if len(args) < 1 {
		fmt.Fprint(os.Stderr, harnessUsage)
		return 2
	}
	switch args[0] {
	case "report":
		return runHarnessReport(args[1:])
	case "compare":
		return runHarnessCompare(args[1:])
	case "verify":
		return runHarnessVerify(args[1:])
	default:
		fmt.Fprint(os.Stderr, harnessUsage)
		return 2
	}
}

func runHarnessReport(args []string) int {
	fs := flag.NewFlagSet("harness report", flag.ContinueOnError)
	input := fs.String("input", "", "path to a captured log file, or - for stdin")
	useAdb := fs.Bool("adb", false, "pull the log directly from a connected device via `adb logcat -d -s VIVA_TRACE:I`")
	serial := fs.String("serial", "", "adb device serial (only used with --adb, when more than one device is attached)")
	out := fs.String("out", "report.csv", "output CSV path for the aggregate report")
	perTrace := fs.String("per-trace", "", "also write one row per turn to this CSV (evidence for V11/N1)")
	verdicts := fs.String("verdicts", "", "also write the verdict/rule breakdown to this CSV (input for N4 ablation)")
	variant := fs.String("variant", "", "label for this run, e.g. full / no-guard / asr-cloud — recorded in --per-trace")
	if err := fs.Parse(args); err != nil {
		return 2
	}

	src, code := logSourceFrom(*useAdb, *input, *serial)
	if src == nil {
		return code
	}

	result, err := harness.Aggregate(src)
	if err != nil {
		fmt.Fprintf(os.Stderr, "error: %v\n", err)
		return 1
	}
	printWarnings(result)

	stats := harness.BuildReport(result.Traces)
	if err := report.WriteCSV(stats, *out); err != nil {
		fmt.Fprintf(os.Stderr, "error: %v\n", err)
		return 1
	}

	if *perTrace != "" {
		rows := harness.BuildTraceRows(result.Traces, *variant)
		if err := report.WriteTraceCSV(rows, *perTrace); err != nil {
			fmt.Fprintf(os.Stderr, "error: %v\n", err)
			return 1
		}
	}

	breakdown := harness.BuildVerdictBreakdown(result.Traces)
	if *verdicts != "" {
		if err := report.WriteVerdictCSV(breakdown, *verdicts); err != nil {
			fmt.Fprintf(os.Stderr, "error: %v\n", err)
			return 1
		}
	}

	label := *variant
	if label == "" {
		label = "(unlabelled)"
	}
	fmt.Printf("%d traces parsed [variant=%s], report written to %s\n", len(result.Traces), label, *out)
	printStats(stats)
	printVerdicts(breakdown)
	if *perTrace != "" {
		fmt.Printf("  per-trace rows -> %s\n", *perTrace)
	}
	if *verdicts != "" {
		fmt.Printf("  verdict breakdown -> %s\n", *verdicts)
	}
	return 0
}

func runHarnessCompare(args []string) int {
	fs := flag.NewFlagSet("harness compare", flag.ContinueOnError)
	baseline := fs.String("baseline", "", "log capture of the full system")
	candidate := fs.String("candidate", "", "log capture of the ablated/alternative run")
	baselineLabel := fs.String("baseline-label", "baseline", "column name for the baseline run")
	candidateLabel := fs.String("candidate-label", "candidate", "column name for the candidate run")
	out := fs.String("out", "compare.csv", "output CSV path for the latency comparison")
	verdictsOut := fs.String("verdicts-out", "", "also write the verdict-count comparison to this CSV")
	if err := fs.Parse(args); err != nil {
		return 2
	}
	if *baseline == "" || *candidate == "" {
		fmt.Fprintln(os.Stderr, "error: need --baseline <log> and --candidate <log>")
		return 2
	}

	baseResult, err := harness.Aggregate(logsource.FileSource{Path: *baseline})
	if err != nil {
		fmt.Fprintf(os.Stderr, "error: baseline: %v\n", err)
		return 1
	}
	candResult, err := harness.Aggregate(logsource.FileSource{Path: *candidate})
	if err != nil {
		fmt.Fprintf(os.Stderr, "error: candidate: %v\n", err)
		return 1
	}
	printWarnings(baseResult)
	printWarnings(candResult)

	rows := harness.CompareReports(
		harness.BuildReport(baseResult.Traces),
		harness.BuildReport(candResult.Traces),
	)
	if err := report.WriteCompareCSV(rows, *baselineLabel, *candidateLabel, *out); err != nil {
		fmt.Fprintf(os.Stderr, "error: %v\n", err)
		return 1
	}

	baseVerdicts := harness.BuildVerdictBreakdown(baseResult.Traces)
	candVerdicts := harness.BuildVerdictBreakdown(candResult.Traces)
	verdictRows := harness.CompareVerdicts(baseVerdicts, candVerdicts)
	if *verdictsOut != "" {
		if err := report.WriteVerdictCompareCSV(verdictRows, *baselineLabel, *candidateLabel, *verdictsOut); err != nil {
			fmt.Fprintf(os.Stderr, "error: %v\n", err)
			return 1
		}
	}

	fmt.Printf("%s: %d traces · %s: %d traces -> %s\n",
		*baselineLabel, len(baseResult.Traces), *candidateLabel, len(candResult.Traces), *out)
	for _, r := range rows {
		if !r.Comparable {
			// The asymmetry IS the finding in an ablation (a segment that
			// disappears entirely), so it prints instead of being skipped.
			fmt.Printf("  %-32s %s n=%d · %s n=%d — not comparable\n",
				r.Label, *baselineLabel, r.BaselineN, *candidateLabel, r.CandidateN)
			continue
		}
		fmt.Printf("  %-32s p95 %.1f -> %.1f ms (%+.1f)\n",
			r.Label, r.BaselineP95Ms, r.CandidateP95Ms, r.DeltaP95Ms)
	}
	for _, r := range verdictRows {
		fmt.Printf("  verdict %-12s %-24s %d -> %d (%+d)\n", r.Kind, r.Rule, r.BaselineCount, r.CandidateCount, r.Delta)
	}
	return 0
}

func runHarnessVerify(args []string) int {
	fs := flag.NewFlagSet("harness verify", flag.ContinueOnError)
	suitePath := fs.String("suite", "suites/benchmark_v1.csv", "benchmark/regression suite CSV")
	input := fs.String("input", "", "path to a captured log file, or - for stdin")
	useAdb := fs.Bool("adb", false, "pull the log directly from a connected device")
	serial := fs.String("serial", "", "adb device serial (only used with --adb)")
	variant := fs.String("variant", "", "label for this run, e.g. quiet / cabin / highway")
	match := fs.String("match", string(harness.MatchOrder), "how to pair cases with turns: order|utterance")
	out := fs.String("out", "results.csv", "output CSV path, one row per case")
	summaryOut := fs.String("summary-out", "", "also write the one-line run summary to this CSV")
	evidenceDir := fs.String("evidence-dir", "", "directory of screenshots named <evidence_id>.<ext>")
	if err := fs.Parse(args); err != nil {
		return 2
	}

	mode := harness.MatchMode(*match)
	if mode != harness.MatchOrder && mode != harness.MatchUtterance {
		fmt.Fprintf(os.Stderr, "error: --match must be %q or %q\n", harness.MatchOrder, harness.MatchUtterance)
		return 2
	}

	cases, err := suite.Load(*suitePath)
	if err != nil {
		fmt.Fprintf(os.Stderr, "error: %v\n", err)
		return 1
	}

	src, code := logSourceFrom(*useAdb, *input, *serial)
	if src == nil {
		return code
	}
	result, err := harness.Aggregate(src)
	if err != nil {
		fmt.Fprintf(os.Stderr, "error: %v\n", err)
		return 1
	}
	printWarnings(result)

	results := harness.Verify(cases, result.Traces, mode)
	evidence := resolveEvidence(cases, *evidenceDir)
	if err := report.WriteSuiteResultsCSV(results, evidence, *variant, *out); err != nil {
		fmt.Fprintf(os.Stderr, "error: %v\n", err)
		return 1
	}

	summary := harness.Summarize(results)
	if *summaryOut != "" {
		if err := report.WriteSuiteSummaryCSV(summary, *variant, *summaryOut); err != nil {
			fmt.Fprintf(os.Stderr, "error: %v\n", err)
			return 1
		}
	}

	label := *variant
	if label == "" {
		label = "(unlabelled)"
	}
	fmt.Printf("%s: %d cases · %d PASS · %d FAIL · %d MISSING (%d of them known gaps) -> %s\n",
		label, summary.Total, summary.Pass, summary.Fail, summary.Missing, summary.KnownGapFail, *out)
	if summary.E2EN > 0 {
		fmt.Printf("  e2e over %d measured turns: p50=%.1fms p95=%.1fms\n", summary.E2EN, summary.P50Ms, summary.P95Ms)
	}
	for _, r := range results {
		if r.Status == harness.StatusPass {
			continue
		}
		gate := ""
		if r.Case.KnownGap() {
			gate = " [known gap: " + r.Case.Gate + "]"
		}
		fmt.Printf("  %-5s %-4s %q%s\n    %s\n",
			r.Status, r.Case.ID, r.Case.Utterance, gate, strings.Join(r.Reasons, "; "))
	}

	if missing := missingEvidence(cases, evidence); len(missing) > 0 && *evidenceDir != "" {
		fmt.Fprintf(os.Stderr, "warning: no screenshot found for %d evidence id(s): %s\n",
			len(missing), strings.Join(missing, ", "))
	}

	// Exit non-zero only for regressions, never for declared gaps: this is
	// meant to be runnable in CI while T5/D7 are still landing.
	if !harness.RegressionPassed(results) {
		return 1
	}
	return 0
}

// resolveEvidence looks for <dir>/<evidence_id>.<ext> for a few common
// screenshot extensions. Absent files map to "", which the writer renders as
// "-" so a gap in the evidence is visible in the artifact.
func resolveEvidence(cases []harness.SuiteCase, dir string) map[string]string {
	found := make(map[string]string, len(cases))
	if dir == "" {
		return found
	}
	for _, c := range cases {
		if c.EvidenceID == "" {
			continue
		}
		for _, ext := range []string{".png", ".jpg", ".jpeg", ".webp", ".mp4"} {
			candidate := filepath.Join(dir, c.EvidenceID+ext)
			if info, err := os.Stat(candidate); err == nil && !info.IsDir() {
				found[c.EvidenceID] = candidate
				break
			}
		}
	}
	return found
}

func missingEvidence(cases []harness.SuiteCase, found map[string]string) []string {
	var missing []string
	for _, c := range cases {
		if c.EvidenceID != "" && found[c.EvidenceID] == "" {
			missing = append(missing, c.EvidenceID)
		}
	}
	return missing
}

func logSourceFrom(useAdb bool, input, serial string) (repository.LineSource, int) {
	switch {
	case useAdb:
		return logsource.AdbSource{Serial: serial}, 0
	case input != "":
		return logsource.FileSource{Path: input}, 0
	default:
		fmt.Fprintln(os.Stderr, "error: need --input <path|-> or --adb")
		return nil, 2
	}
}

func printWarnings(result *harness.ParseResult) {
	for _, w := range result.Warnings {
		fmt.Fprintf(os.Stderr, "warning: %s\n", w)
	}
	if len(result.Traces) == 0 {
		fmt.Fprintln(os.Stderr, "warning: no VIVA_TRACE lines found — check --input/--adb and the log format")
	}
}

func printStats(stats []harness.Stat) {
	for _, s := range stats {
		if s.SampleSize == 0 {
			fmt.Printf("  %-32s no data\n", s.Label)
			continue
		}
		fmt.Printf("  %-32s n=%-4d p50=%.1fms p95=%.1fms\n", s.Label, s.SampleSize, s.P50Ms, s.P95Ms)
	}
}

func printVerdicts(b harness.VerdictBreakdown) {
	if b.TotalWithVerdict == 0 && b.MissingSummary == 0 {
		return
	}
	fmt.Printf("  verdicts (%d turns with a summary, %d abandoned):\n", b.TotalWithVerdict, b.MissingSummary)
	for _, row := range b.Rows {
		fmt.Printf("    %-10s %-24s %d\n", row.Kind, row.Rule, row.Count)
	}
}
