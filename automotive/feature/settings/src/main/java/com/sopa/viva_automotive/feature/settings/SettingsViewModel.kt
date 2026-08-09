package com.sopa.viva_automotive.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sopa.viva_automotive.core.common.buildinfo.BuildInfo
import com.sopa.viva_automotive.core.common.buildinfo.BuildInfoProvider
import com.sopa.viva_automotive.core.database.settings.AsrEngine
import com.sopa.viva_automotive.core.database.settings.SettingsDataStore
import com.sopa.viva_automotive.core.database.settings.VoiceSettings
import com.sopa.viva_automotive.core.ui.locale.AppLanguage
import com.sopa.viva_automotive.core.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    buildInfoProvider: BuildInfoProvider,
) : ViewModel() {

    val settings: StateFlow<VoiceSettings> = settingsDataStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceSettings())

    val buildInfo: BuildInfo = buildInfoProvider.buildInfo

    fun setVoiceEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setVoiceEnabled(enabled) }
    }

    fun setHotwordEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setHotwordEnabled(enabled) }
    }

    fun setUseFahrenheit(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setUseFahrenheit(enabled) }
    }

    fun setShowPartialTranscription(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setShowPartialTranscription(enabled) }
    }

    fun setPlayAudioCues(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setPlayAudioCues(enabled) }
    }

    fun setAsrEngine(engine: AsrEngine) {
        viewModelScope.launch { settingsDataStore.setAsrEngine(engine) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsDataStore.setThemeMode(mode.storageKey) }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { settingsDataStore.setLanguage(language.storageKey) }
    }
}
