package suite

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func write(t *testing.T, content string) string {
	t.Helper()
	path := filepath.Join(t.TempDir(), "suite.csv")
	if err := os.WriteFile(path, []byte(content), 0o600); err != nil {
		t.Fatalf("write fixture: %v", err)
	}
	return path
}

func TestLoadReadsColumnsByNameNotPosition(t *testing.T) {
	path := write(t, "gate,utterance,id,expect_verdict,expect_intent\n"+
		"SafetyGuard T5,mở cửa,B10,Deny:G1_SPEED_LOCK,door_lock\n")

	cases, err := Load(path)
	if err != nil {
		t.Fatalf("Load: %v", err)
	}
	if len(cases) != 1 {
		t.Fatalf("cases = %d, want 1", len(cases))
	}
	c := cases[0]
	if c.ID != "B10" || c.Utterance != "mở cửa" || c.ExpectIntent != "door_lock" {
		t.Errorf("case = %+v, want the columns resolved by header name", c)
	}
	if !c.KnownGap() {
		t.Error("KnownGap() = false, want true when a gate is declared")
	}
}

func TestLoadKeepsOptionalColumnsOptional(t *testing.T) {
	cases, err := Load(write(t, "id,utterance\nB1,khóa cửa\n"))
	if err != nil {
		t.Fatalf("Load: %v", err)
	}
	if cases[0].ExpectIntent != "" || cases[0].KnownGap() {
		t.Errorf("case = %+v, want empty optional fields", cases[0])
	}
}

func TestLoadRejectsDuplicateIds(t *testing.T) {
	// Duplicate ids would silently pair the wrong evidence file with a result.
	_, err := Load(write(t, "id,utterance\nB1,khóa cửa\nB1,mở cửa\n"))
	if err == nil || !strings.Contains(err.Error(), "already used") {
		t.Errorf("err = %v, want a duplicate-id error", err)
	}
}

func TestLoadRejectsAMissingRequiredColumn(t *testing.T) {
	_, err := Load(write(t, "id,expect_intent\nB1,door_lock\n"))
	if err == nil || !strings.Contains(err.Error(), "utterance") {
		t.Errorf("err = %v, want a missing-column error naming utterance", err)
	}
}

func TestLoadRejectsARaggedRow(t *testing.T) {
	// The most likely authoring mistake is an unquoted comma in the notes.
	_, err := Load(write(t, "id,utterance,notes\nB1,khóa cửa,một, hai\n"))
	if err == nil {
		t.Fatal("err = nil, want a field-count error")
	}
}

func TestLoadRejectsAHeaderOnlyFile(t *testing.T) {
	if _, err := Load(write(t, "id,utterance\n")); err == nil {
		t.Error("err = nil, want an error for a suite with no cases")
	}
}

func TestLoadTheRealBenchmarkSuite(t *testing.T) {
	// The shipped suite must stay loadable — it is the file V10/V12 run from,
	// and an unquoted comma in a Vietnamese note would only show up here.
	cases, err := Load(filepath.Join("..", "..", "..", "suites", "benchmark_v1.csv"))
	if err != nil {
		t.Fatalf("Load: %v", err)
	}
	if len(cases) < 20 {
		t.Errorf("cases = %d, want at least the 20 utterances V10 commits to", len(cases))
	}
	for _, c := range cases {
		if c.Utterance == "" {
			t.Errorf("case %s has no utterance", c.ID)
		}
		if c.ExpectIntent == "" || c.ExpectVerdict == "" {
			t.Errorf("case %s has no expectation — an unasserted row measures nothing", c.ID)
		}
	}
}
