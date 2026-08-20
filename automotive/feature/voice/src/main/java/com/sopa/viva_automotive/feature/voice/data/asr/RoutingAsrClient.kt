package com.sopa.viva_automotive.feature.voice.data.asr

import android.util.Log
import com.sopa.viva_automotive.core.database.settings.AsrEngine
import com.sopa.viva_automotive.core.database.settings.SettingsDataStore
import com.sopa.viva_automotive.feature.voice.data.vosk.VoskAsrClient
import com.viva.voice.asr.AsrClient
import com.viva.voice.asr.AsrResult
import com.viva.voice.trace.LatencyTrace
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Picks the speech backend from Settings:
 *  - [AsrEngine.VIVA]   -> [HttpAsrClient], container `viva-asr` on CarSky (default)
 *  - [AsrEngine.GOOGLE] -> [GoogleCloudSpeechAsrClient], needs internet + SA json
 *  - [AsrEngine.VOSK]   -> [VoskAsrClient], fully on-device, works with no network
 */
@Singleton
class RoutingAsrClient @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val vivaAsr: HttpAsrClient,
    private val googleAsr: GoogleCloudSpeechAsrClient,
    private val voskAsr: VoskAsrClient,
) : AsrClient {

    override suspend fun transcribe(
        pcm16: ShortArray,
        sampleRate: Int,
        trace: LatencyTrace,
    ): AsrResult {
        val engine = AsrEngine.fromStorageKey(settingsDataStore.settings.first().asrEngine)
        Log.i(TAG, "ASR engine=$engine")
        return when (engine) {
            AsrEngine.VIVA -> vivaAsr.transcribe(pcm16, sampleRate, trace)
            AsrEngine.GOOGLE -> googleAsr.transcribe(pcm16, sampleRate, trace)
            AsrEngine.VOSK -> voskAsr.transcribe(pcm16, sampleRate, trace)
        }
    }

    private companion object {
        const val TAG = "RoutingAsrClient"
    }
}
