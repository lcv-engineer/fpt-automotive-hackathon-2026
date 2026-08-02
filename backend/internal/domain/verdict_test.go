package domain

import "testing"

func TestParseVerdictGrammar(t *testing.T) {
	// Every form in 03-contracts.md §1.2, plus the four fixture verdicts Long
	// handed over in android/voice/fixtures/.
	cases := []struct {
		raw        string
		wantKind   VerdictKind
		wantDetail string
		wantErr    bool
	}{
		{"Allow", VerdictAllow, "", false},
		{"Deny:G1_SPEED_LOCK", VerdictDeny, "G1_SPEED_LOCK", false},
		{"Deny:G1_GEAR_LOCK", VerdictDeny, "G1_GEAR_LOCK", false},
		{"Confirm:G2_CONFIRM_DELIVERY", VerdictConfirm, "G2_CONFIRM_DELIVERY", false},
		{"Confirm:G3_LOW_CONFIDENCE", VerdictConfirm, "G3_LOW_CONFIDENCE", false},
		{"Error:asr_done", VerdictError, "asr_done", false},

		// Malformed: classified, reported, never dropped.
		{"Deny", VerdictDeny, "", true},
		{"Confirm:", VerdictConfirm, "", true},
		{"Allowed", VerdictUnknown, "", true},
		{"", VerdictUnknown, "", true},
		{"-", VerdictUnknown, "", true},
	}

	for _, tc := range cases {
		got, err := ParseVerdict(tc.raw)
		if (err != nil) != tc.wantErr {
			t.Errorf("ParseVerdict(%q) err = %v, wantErr = %v", tc.raw, err, tc.wantErr)
		}
		if got.Kind != tc.wantKind {
			t.Errorf("ParseVerdict(%q).Kind = %q, want %q", tc.raw, got.Kind, tc.wantKind)
		}
		if got.Detail != tc.wantDetail {
			t.Errorf("ParseVerdict(%q).Detail = %q, want %q", tc.raw, got.Detail, tc.wantDetail)
		}
		if got.Raw != tc.raw {
			t.Errorf("ParseVerdict(%q).Raw = %q, want the input verbatim", tc.raw, got.Raw)
		}
	}
}

func TestParseVerdictSplitsOnFirstColonOnly(t *testing.T) {
	// The contract says "tách bằng dấu : đầu tiên". A rule id that somehow
	// contains a colon must not be truncated.
	v, err := ParseVerdict("Deny:G1_SPEED_LOCK:extra")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if v.Detail != "G1_SPEED_LOCK:extra" {
		t.Errorf("Detail = %q, want everything after the first colon", v.Detail)
	}
}

func TestVerdictBlocked(t *testing.T) {
	// Blocked means the skill never ran. Confirm is NOT blocked — it is
	// waiting on the driver, which is a different outcome and matters for the
	// ablation tables.
	cases := map[string]bool{
		"Allow":                       false,
		"Confirm:G2_CONFIRM_DELIVERY": false,
		"Deny:G1_SPEED_LOCK":          true,
		"Error:asr_done":              true,
	}
	for raw, want := range cases {
		v, _ := ParseVerdict(raw)
		if v.Blocked() != want {
			t.Errorf("ParseVerdict(%q).Blocked() = %v, want %v", raw, v.Blocked(), want)
		}
	}
}

func TestVerdictStringRoundTrips(t *testing.T) {
	for _, raw := range []string{"Allow", "Deny:G1_SPEED_LOCK", "Error:asr_done"} {
		v, err := ParseVerdict(raw)
		if err != nil {
			t.Fatalf("ParseVerdict(%q): %v", raw, err)
		}
		if v.String() != raw {
			t.Errorf("String() = %q, want %q", v.String(), raw)
		}
	}
}

func TestAddMarkKeepsFirstValue(t *testing.T) {
	// Contracts §1: a stage marked twice must not shorten the segment.
	tr := NewTrace("t1")
	if !tr.AddMark(StageAsrDone, 100) {
		t.Fatal("first AddMark should be accepted")
	}
	if tr.AddMark(StageAsrDone, 50) {
		t.Error("second AddMark for the same stage should be rejected")
	}
	if tr.Marks[StageAsrDone] != 100 {
		t.Errorf("mark = %d, want the first value 100", tr.Marks[StageAsrDone])
	}
}
