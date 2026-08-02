// Package suite reads benchmark/regression suite files (CSV) into the
// harness use case's SuiteCase values.
package suite

import (
	"encoding/csv"
	"fmt"
	"os"
	"strings"

	"viva-tools/internal/usecase/harness"
)

// Load reads a suite CSV. Columns are addressed by header name, not position,
// so the team can add a column without breaking every existing file.
//
// Required: id, utterance. Optional: expect_intent, expect_verdict,
// evidence_id, gate, notes.
func Load(path string) ([]harness.SuiteCase, error) {
	f, err := os.Open(path)
	if err != nil {
		return nil, fmt.Errorf("open suite %s: %w", path, err)
	}
	defer f.Close()

	r := csv.NewReader(f)
	r.FieldsPerRecord = -1 // report the mismatch ourselves, with the row number
	records, err := r.ReadAll()
	if err != nil {
		return nil, fmt.Errorf("read suite %s: %w", path, err)
	}
	if len(records) < 2 {
		return nil, fmt.Errorf("suite %s has a header but no cases", path)
	}

	index := make(map[string]int, len(records[0]))
	for i, name := range records[0] {
		index[strings.TrimSpace(strings.ToLower(name))] = i
	}
	for _, required := range []string{"id", "utterance"} {
		if _, ok := index[required]; !ok {
			return nil, fmt.Errorf("suite %s is missing the %q column", path, required)
		}
	}

	get := func(row []string, column string) string {
		i, ok := index[column]
		if !ok || i >= len(row) {
			return ""
		}
		return strings.TrimSpace(row[i])
	}

	cases := make([]harness.SuiteCase, 0, len(records)-1)
	seen := make(map[string]int, len(records)-1)
	for rowNum, row := range records[1:] {
		line := rowNum + 2 // 1-based, and the header is line 1
		if len(row) != len(records[0]) {
			return nil, fmt.Errorf("suite %s line %d: %d fields, header has %d (unquoted comma?)",
				path, line, len(row), len(records[0]))
		}
		id := get(row, "id")
		if id == "" {
			return nil, fmt.Errorf("suite %s line %d: empty id", path, line)
		}
		if prev, dup := seen[id]; dup {
			// Duplicate ids would make results untraceable back to a row, and
			// silently pairing the wrong evidence file is worse than failing.
			return nil, fmt.Errorf("suite %s line %d: id %q already used on line %d", path, line, id, prev)
		}
		seen[id] = line

		cases = append(cases, harness.SuiteCase{
			ID:            id,
			Utterance:     get(row, "utterance"),
			ExpectIntent:  get(row, "expect_intent"),
			ExpectVerdict: get(row, "expect_verdict"),
			EvidenceID:    get(row, "evidence_id"),
			Gate:          get(row, "gate"),
			Notes:         get(row, "notes"),
		})
	}
	return cases, nil
}
