package com.sopa.viva_automotive.core.database.voicehistory

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "voice_turn_history")
data class VoiceTurnHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAtEpochMs: Long,
    /** `viva`, `google`, or `none` (typed / injected text — no STT). */
    val asrEngine: String,
    val transcript: String,
    val intentName: String?,
    /** Compact slot summary, e.g. `value=24.0; lock=false`. */
    val intentSlots: String?,
    /** [com.viva.voice.agent.VoiceTurnStatus] name, or a service-level code. */
    val status: String,
    val succeeded: Boolean,
    /** User-facing note / failure reason (usually spoken copy). */
    val note: String,
)
