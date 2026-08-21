package com.sopa.viva_automotive.core.database.voicehistory

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class VoiceTurnHistoryRepository @Inject constructor(
    private val dao: VoiceTurnHistoryDao,
) {
    fun observeRecent(limit: Int = MAX_ENTRIES): Flow<List<VoiceTurnHistoryEntity>> =
        dao.observeRecent(limit)

    suspend fun record(
        asrEngine: String,
        transcript: String,
        intentName: String?,
        intentSlots: String?,
        status: String,
        succeeded: Boolean,
        note: String,
        createdAtEpochMs: Long = System.currentTimeMillis(),
    ) {
        dao.insert(
            VoiceTurnHistoryEntity(
                createdAtEpochMs = createdAtEpochMs,
                asrEngine = asrEngine,
                transcript = transcript,
                intentName = intentName,
                intentSlots = intentSlots,
                status = status,
                succeeded = succeeded,
                note = note,
            ),
        )
        val overflow = dao.count() - MAX_ENTRIES
        if (overflow > 0) {
            dao.deleteOldest(overflow)
        }
    }

    suspend fun clearAll() = dao.clearAll()

    companion object {
        const val MAX_ENTRIES = 100
        const val ASR_NONE = "none"
    }
}
