package com.sopa.viva_automotive.core.database.voicehistory

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceTurnHistoryDao {

    @Query(
        """
        SELECT * FROM voice_turn_history
        ORDER BY createdAtEpochMs DESC
        LIMIT :limit
        """,
    )
    fun observeRecent(limit: Int = 100): Flow<List<VoiceTurnHistoryEntity>>

    @Insert
    suspend fun insert(entity: VoiceTurnHistoryEntity): Long

    @Query("SELECT COUNT(*) FROM voice_turn_history")
    suspend fun count(): Int

    @Query(
        """
        DELETE FROM voice_turn_history WHERE id IN (
            SELECT id FROM voice_turn_history
            ORDER BY createdAtEpochMs ASC
            LIMIT :n
        )
        """,
    )
    suspend fun deleteOldest(n: Int)

    @Query("DELETE FROM voice_turn_history")
    suspend fun clearAll()
}
