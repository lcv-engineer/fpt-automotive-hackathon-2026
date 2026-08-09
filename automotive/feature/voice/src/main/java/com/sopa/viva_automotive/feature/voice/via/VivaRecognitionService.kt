package com.sopa.viva_automotive.feature.voice.via

import android.content.Intent
import android.os.RemoteException
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.util.Log
import com.viva.voice.audio.Trigger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Required VIA companion. Speech recognition for cabin commands is owned by
 * VoiceAgent/VAD; this service forwards start-listening into that pipeline.
 */
@AndroidEntryPoint
class VivaRecognitionService : RecognitionService() {

    @Inject lateinit var sessionBridge: VoiceSessionBridge

    override fun onStartListening(recognizerIntent: Intent, listener: Callback) {
        Log.i(TAG, "RecognitionService onStartListening — hand off to in-app pipeline")
        try {
            // Do not call readyForSpeech: that is what keeps the AAOS System UI
            // "Listening…" chip alive. Cabin UX is VoiceSessionModal instead.
            sessionBridge.startListening(
                trigger = Trigger.PUSH_TO_TALK,
                showSource = VoiceSessionBridge.SHOW_SOURCE_TTT,
            )
            listener.error(SpeechRecognizer.ERROR_CLIENT)
        } catch (error: RemoteException) {
            Log.w(TAG, "Recognition callback failed", error)
        }
    }

    override fun onCancel(listener: Callback) {
        sessionBridge.stop()
    }

    override fun onStopListening(listener: Callback) {
        // Endpoint is owned by Silero VAD inside VoiceAssistantService.
    }

    companion object {
        private const val TAG = "VIVA_VOICE"
    }
}
