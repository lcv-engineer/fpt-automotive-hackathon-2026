package domain

import (
	"fmt"
	"strings"
)

// Verdict grammar, fixed by vong2/03-contracts.md §1.2 (answered 29/07, which
// closed the "what does <verdict> serialize to" question this package used to
// carry):
//
//	verdict := "Allow" | "Deny:"<RULE_ID> | "Confirm:"<RULE_ID> | "Error:"<STAGE_ID>
//
// Split on the FIRST ":" — left is the kind, right is the detail. The rule id
// matters: N4b's ablation table ("turn SafetyGuard off and 'mở cửa' at 60 km/h
// executes anyway") is a group-by over these values, not a hand-replayed demo.
type VerdictKind string

const (
	VerdictAllow   VerdictKind = "Allow"
	VerdictDeny    VerdictKind = "Deny"
	VerdictConfirm VerdictKind = "Confirm"
	// VerdictError marks a turn that died before reaching SafetyGuard; the
	// detail is the stage it died at. Contracts §1.2 added it so broken turns
	// stay in the sample instead of vanishing from the benchmark.
	VerdictError VerdictKind = "Error"
	// VerdictUnknown is for anything outside the grammar. Kept as a value
	// rather than an error-only case so an unrecognised verdict still shows
	// up in the breakdown instead of being dropped.
	VerdictUnknown VerdictKind = "Unknown"
)

// Verdict is a parsed <verdict> field.
type Verdict struct {
	Kind   VerdictKind
	Detail string // RULE_ID for Deny/Confirm, STAGE_ID for Error, "" for Allow
	Raw    string
}

// Blocked reports whether the turn was stopped before the skill ran. Used by
// the regression runner and the ablation tables: Deny and Error mean nothing
// was executed, Confirm means execution waits on the driver.
func (v Verdict) Blocked() bool {
	return v.Kind == VerdictDeny || v.Kind == VerdictError
}

// String renders the verdict back to its log form.
func (v Verdict) String() string {
	if v.Detail == "" {
		return string(v.Kind)
	}
	return string(v.Kind) + ":" + v.Detail
}

// ParseVerdict parses the <verdict> field of a VIVA_TRACE_SUMMARY line.
//
// An unrecognised value returns Kind=VerdictUnknown together with a non-nil
// error: the caller decides whether to warn, but the sample is never silently
// discarded — dropping turns we failed to classify would quietly improve
// every statistic computed afterwards.
func ParseVerdict(raw string) (Verdict, error) {
	trimmed := strings.TrimSpace(raw)
	if trimmed == "" || trimmed == "-" {
		return Verdict{Kind: VerdictUnknown, Raw: raw}, fmt.Errorf("empty verdict field")
	}

	kindPart, detail, hasDetail := strings.Cut(trimmed, ":")
	kind := VerdictKind(kindPart)

	switch kind {
	case VerdictAllow:
		if hasDetail && detail != "" {
			// Not fatal — Allow with a detail is still an Allow — but it means
			// the emitter and this parser disagree about the grammar.
			return Verdict{Kind: VerdictAllow, Detail: detail, Raw: raw},
				fmt.Errorf("verdict %q: Allow carries no detail per contracts §1.2", raw)
		}
		return Verdict{Kind: VerdictAllow, Raw: raw}, nil
	case VerdictDeny, VerdictConfirm, VerdictError:
		if !hasDetail || detail == "" {
			return Verdict{Kind: kind, Raw: raw},
				fmt.Errorf("verdict %q: %s must carry a detail after ':' per contracts §1.2", raw, kind)
		}
		return Verdict{Kind: kind, Detail: detail, Raw: raw}, nil
	default:
		return Verdict{Kind: VerdictUnknown, Detail: detail, Raw: raw},
			fmt.Errorf("verdict %q: unknown kind %q, want Allow/Deny/Confirm/Error", raw, kindPart)
	}
}
