package com.sopa.viva_automotive.feature.voice.domain

import android.util.Log
import com.sopa.viva_automotive.core.database.voicehistory.VoiceTurnHistoryRepository
import com.sopa.viva_automotive.feature.voice.data.asr.AsrTurnContext
import com.viva.voice.agent.VoiceTurnResult
import com.viva.voice.agent.VoiceTurnStatus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceTurnHistoryRecorder @Inject constructor(
    private val repository: VoiceTurnHistoryRepository,
    private val asrTurnContext: AsrTurnContext,
) {
    suspend fun recordAgentTurn(result: VoiceTurnResult) {
        runCatching {
            repository.record(
                asrEngine = asrTurnContext.peek(),
                transcript = result.transcript,
                intentName = result.intent?.name,
                intentSlots = result.intent?.slots
                    ?.entries
                    ?.joinToString("; ") { (k, v) -> "$k=$v" }
                    ?.takeIf { it.isNotBlank() },
                status = result.status.name,
                succeeded = result.status == VoiceTurnStatus.APPLIED ||
                    result.status == VoiceTurnStatus.PARTIALLY_APPLIED,
                note = result.spokenVi,
            )
        }.onFailure { error ->
            Log.w(TAG, "Failed to persist voice turn history", error)
        }
    }

    suspend fun recordEarlyFailure(
        status: String,
        note: String,
        transcript: String = "",
        asrEngine: String = AsrTurnContext.NONE,
    ) {
        runCatching {
            repository.record(
                asrEngine = asrEngine,
                transcript = transcript,
                intentName = null,
                intentSlots = null,
                status = status,
                succeeded = false,
                note = note,
            )
        }.onFailure { error ->
            Log.w(TAG, "Failed to persist early voice failure", error)
        }
    }

    private companion object {
        const val TAG = "VoiceTurnHistory"
    }
}
