package com.sopa.viva_automotive

import android.app.Application
import com.sopa.viva_automotive.core.common.coroutines.ApplicationScope
import com.sopa.viva_automotive.core.database.settings.SettingsDataStore
import com.sopa.viva_automotive.core.ui.locale.AppLanguage
import com.sopa.viva_automotive.feature.voice.VoiceRuntimeWarmUp
import com.sopa.viva_automotive.feature.voice.data.CommandMappingRepository
import com.sopa.viva_automotive.feature.voice.via.HotwordController
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltAndroidApp
class VivaApplication : Application() {

    @Inject lateinit var commandMappingRepository: CommandMappingRepository

    @Inject lateinit var settingsDataStore: SettingsDataStore

    @Inject lateinit var voiceRuntimeWarmUp: VoiceRuntimeWarmUp

    @Inject lateinit var hotwordController: HotwordController

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // Arm software hotword from app process (emulator / non-default VIA).
        hotwordController.ensureBound()
        applicationScope.launch {
            val language = AppLanguage.fromStorageKey(settingsDataStore.settings.first().language)
            getSharedPreferences("viva_locale", MODE_PRIVATE)
                .edit()
                .putString("language", language.storageKey)
                .apply()
            commandMappingRepository.seedIfEmpty()
        }
        applicationScope.launch {
            delay(1_500)
            voiceRuntimeWarmUp.warmUp()
        }
    }
}
