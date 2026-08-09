package com.sopa.viva_automotive.feature.voice.via

import android.service.voice.VoiceInteractionService
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * AOSP VIA entrypoint. Arms hotword detection for as long as this service is the
 * active assistant (ROLE_ASSISTANT / VoiceInteractionManagerService).
 */
@AndroidEntryPoint
class VivaVoiceInteractionService : VoiceInteractionService() {

    @Inject lateinit var hotwordController: HotwordController

    override fun onReady() {
        super.onReady()
        Log.i(TAG, "VivaVoiceInteractionService ready — attaching hotword")
        hotwordController.attach(this)
    }

    override fun onShutdown() {
        hotwordController.detach()
        super.onShutdown()
    }

    companion object {
        private const val TAG = "VIVA_VOICE"
    }
}
