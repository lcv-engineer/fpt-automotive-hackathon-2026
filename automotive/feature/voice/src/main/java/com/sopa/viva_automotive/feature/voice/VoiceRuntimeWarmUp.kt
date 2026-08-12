package com.sopa.viva_automotive.feature.voice

import com.sopa.viva_automotive.feature.voice.data.asr.HttpAsrClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceRuntimeWarmUp @Inject constructor(
    private val httpAsrClient: HttpAsrClient,
) {
    suspend fun warmUp() {
        httpAsrClient.warmUp()
    }
}
