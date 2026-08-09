package com.sopa.viva_automotive.core.database.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class VoiceSettings(
    val voiceEnabled: Boolean = true,
    /** Hands-free “Vi-Vi ơi”. Off by default until FA/FR gate is met (R7). */
    val hotwordEnabled: Boolean = false,
    val useFahrenheit: Boolean = false,
    val showPartialTranscription: Boolean = true,
    val playAudioCues: Boolean = true,
    val themeMode: String = "system",
    val language: String = "system",
    val voiceLanguage: String = "vi",
    /** `viva` (default) or `google` — see [AsrEngine]. */
    val asrEngine: String = AsrEngine.VIVA.storageKey,
)

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "viva_settings",
)

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val VOICE_ENABLED = booleanPreferencesKey("voice_enabled")
        val HOTWORD_ENABLED = booleanPreferencesKey("hotword_enabled")
        val USE_FAHRENHEIT = booleanPreferencesKey("use_fahrenheit")
        val SHOW_PARTIAL = booleanPreferencesKey("show_partial_transcription")
        val AUDIO_CUES = booleanPreferencesKey("play_audio_cues")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val VOICE_LANGUAGE = stringPreferencesKey("voice_language")
        val ASR_ENGINE = stringPreferencesKey("asr_engine")
    }

    val settings: Flow<VoiceSettings> = context.settingsDataStore.data.map { prefs ->
        VoiceSettings(
            voiceEnabled = prefs[Keys.VOICE_ENABLED] ?: true,
            hotwordEnabled = prefs[Keys.HOTWORD_ENABLED] ?: false,
            useFahrenheit = prefs[Keys.USE_FAHRENHEIT] ?: false,
            showPartialTranscription = prefs[Keys.SHOW_PARTIAL] ?: true,
            playAudioCues = prefs[Keys.AUDIO_CUES] ?: true,
            themeMode = prefs[Keys.THEME_MODE] ?: "system",
            language = prefs[Keys.LANGUAGE] ?: "system",
            voiceLanguage = prefs[Keys.VOICE_LANGUAGE] ?: "vi",
            asrEngine = prefs[Keys.ASR_ENGINE] ?: AsrEngine.VIVA.storageKey,
        )
    }

    suspend fun setVoiceEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.VOICE_ENABLED] = enabled }
    }

    suspend fun setHotwordEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.HOTWORD_ENABLED] = enabled }
    }

    suspend fun setUseFahrenheit(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.USE_FAHRENHEIT] = enabled }
    }

    suspend fun setShowPartialTranscription(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SHOW_PARTIAL] = enabled }
    }

    suspend fun setPlayAudioCues(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.AUDIO_CUES] = enabled }
    }

    suspend fun setThemeMode(mode: String) {
        context.settingsDataStore.edit { it[Keys.THEME_MODE] = mode }
    }

    suspend fun setLanguage(language: String) {
        context.settingsDataStore.edit { it[Keys.LANGUAGE] = language }
    }

    suspend fun setVoiceLanguage(language: String) {
        context.settingsDataStore.edit { it[Keys.VOICE_LANGUAGE] = language }
    }

    suspend fun setAsrEngine(engine: AsrEngine) {
        context.settingsDataStore.edit { it[Keys.ASR_ENGINE] = engine.storageKey }
    }
}
