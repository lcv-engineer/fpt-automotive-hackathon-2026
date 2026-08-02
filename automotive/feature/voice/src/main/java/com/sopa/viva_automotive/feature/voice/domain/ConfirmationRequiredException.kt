package com.sopa.viva_automotive.feature.voice.domain

/**
 * The command was understood and is legal, but must be confirmed out loud
 * before it takes effect.
 *
 * [rule] is a SafetyGuard rule id from 03-contracts.md §4 (e.g.
 * `G2_CONFIRM_DELIVERY`) so the turn logs `Confirm:<rule>` and the benchmark
 * can group by it — same reason the rule id rides along in TraceVerdict.
 *
 * Separate from [CommandValidationException]: "are you sure?" and "I didn't
 * understand" are different answers to the driver, and folding them together
 * would file every confirmation as a failed turn in the benchmark.
 */
class ConfirmationRequiredException(
    val rule: String,
    val questionVi: String,
) : IllegalStateException(questionVi)
