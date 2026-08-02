package com.sopa.viva_automotive.feature.voice.domain

import com.sopa.viva_automotive.feature.voice.domain.model.VehicleIntent
import com.viva.voice.trace.Stage
import com.viva.voice.trace.TraceVerdict

/**
 * How one voice turn is named and judged on the `VIVA_TRACE_SUMMARY` line.
 *
 * Split out of `VoiceAssistantService` so it can be unit tested on the JVM:
 * the service itself needs a device, and the two fields the harness groups by
 * (intent, verdict) are exactly the ones worth pinning with tests. See
 * vong2/03-contracts.md §1 for the wire format and §3 for the intent names.
 */
object VoiceTurnReport {

    /** Pre-rendered in `res/raw`; see `PrerenderedPrompts`. */
    const val DID_NOT_HEAR = "Mình chưa nghe rõ. Bạn thử lại giúp mình nhé."
    const val COMMAND_FAILED = "Mình chưa thực hiện được yêu cầu. Bạn thử lại giúp mình nhé."

    /**
     * Intent name for the summary line, using the §3 vocabulary where the app
     * path has an equivalent.
     *
     * Deliberately not `intent::class.simpleName`: the harness groups the
     * benchmark by this string, and "SetTemperature" would not join against the
     * grammar fixtures Vi already parses.
     */
    fun intentName(intent: VehicleIntent): String = when (intent) {
        is VehicleIntent.SetTemperature, is VehicleIntent.AdjustTemperature -> "hvac_set_temp"
        is VehicleIntent.SetFanSpeed, is VehicleIntent.AdjustFanSpeed -> "hvac_set_fan"
        is VehicleIntent.SetAc -> "hvac_ac"
        is VehicleIntent.SetHvacPower -> "hvac_power"
        is VehicleIntent.SetDoorLock -> "door_lock"
        is VehicleIntent.QueryStatus -> "vehicle_status_" + intent.kind.name.lowercase()
        is VehicleIntent.VolumeAdjust -> "volume_adjust"
        VehicleIntent.MediaNext -> "media_next"
        is VehicleIntent.Delivery -> intent.command.intentName
        is VehicleIntent.NotWired -> intent.intentName
        is VehicleIntent.Clarification -> "clarify"
        is VehicleIntent.Unknown -> "unknown"
    }

    /**
     * Verdict for the summary line.
     *
     * There is no `SafetyGuard` in this build, so no turn can legitimately
     * report `Deny:<rule>` yet — a turn that executed reports plain `Allow`.
     * When T5/T6 lands, the guard verdict replaces this and the ablation table
     * (N4b) gets its before/after rows for free.
     *
     * `Confirm:CLARIFY_SLOT` is a stretch of the `Confirm` kind, which
     * TraceVerdict documents as a SafetyGuard outcome. It is used anyway
     * because the alternative — `Error:nlu_done` — would file M7-04 ("quạt
     * mạnh lên" → asks which level) as a failed turn, when asking exactly one
     * question is the behaviour that scenario is meant to prove. The verdict
     * grammar is frozen for Vi's parser, so inventing a fifth kind is not an
     * option.
     */
    fun verdictFor(intent: VehicleIntent, error: Throwable?): TraceVerdict = when {
        // A turn that asked "are you sure?" did exactly what §4 requires of it.
        // Filing it as an error would make G2_CONFIRM_DELIVERY look like a
        // defect in the benchmark instead of the safety rule it is.
        error is ConfirmationRequiredException -> TraceVerdict.Confirm(error.rule)
        error is CommandNotWiredException -> TraceVerdict.Error(Stage.EXEC_DONE)
        intent is VehicleIntent.Clarification -> TraceVerdict.Confirm("CLARIFY_SLOT")
        intent is VehicleIntent.Unknown -> TraceVerdict.Error(Stage.NLU_DONE)
        error != null -> TraceVerdict.Error(Stage.EXEC_DONE)
        else -> TraceVerdict.Allow
    }

    /** What the assistant says out loud when a turn does not end in success. */
    fun failureSpeech(intent: VehicleIntent, error: Throwable?): String = when {
        // The question itself is the answer the driver needs to hear.
        error is ConfirmationRequiredException -> error.questionVi
        intent is VehicleIntent.Clarification -> intent.promptVi
        intent is VehicleIntent.Unknown -> DID_NOT_HEAR
        error is CommandNotWiredException -> error.message ?: COMMAND_FAILED
        else -> COMMAND_FAILED
    }
}
