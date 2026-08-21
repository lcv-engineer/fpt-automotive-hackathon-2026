package com.sopa.viva_automotive.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.sopa.viva_automotive.core.database.command.CommandMappingDao
import com.sopa.viva_automotive.core.database.command.CommandMappingEntity
import com.sopa.viva_automotive.core.database.voicehistory.VoiceTurnHistoryDao
import com.sopa.viva_automotive.core.database.voicehistory.VoiceTurnHistoryEntity

@Database(
    entities = [
        CommandMappingEntity::class,
        VoiceTurnHistoryEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class VivaDatabase : RoomDatabase() {
    abstract fun commandMappingDao(): CommandMappingDao
    abstract fun voiceTurnHistoryDao(): VoiceTurnHistoryDao
}
