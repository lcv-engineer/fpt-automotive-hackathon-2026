package com.sopa.viva_automotive.feature.voice.via

import android.content.Intent
import android.speech.RecognitionService
import android.util.Log
import com.viva.voice.audio.Trigger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * VIA companion [RecognitionService]. Cabin ASR still runs in
 * [com.sopa.viva_automotive.feature.voice.service.VoiceAssistantService]; this
 * service forwards listen requests and relays the final transcript to the
 * Android SpeechRecognizer callback so client apps are not left hanging.
 */
@AndroidEntryPoint
class VivaRecognitionService : RecognitionService() {

    @Inject lateinit var sessionBridge: VoiceSessionBridge
    @Inject lateinit var recognitionResultHub: RecognitionResultHub

    override fun onStartListening(recognizerIntent: Intent, listener: Callback) {
        Log.i(TAG, "RecognitionService onStartListening — cabin pipeline + callback bridge")
        recognitionResultHub.beginSession(listener)
        recognitionResultHub.signalListening()
        sessionBridge.startListening(
            trigger = Trigger.PUSH_TO_TALK,
            showSource = VoiceSessionBridge.SHOW_SOURCE_TTT,
        )
    }

    override fun onCancel(listener: Callback) {
        recognitionResultHub.cancel()
        sessionBridge.stop()
    }

    override fun onStopListening(listener: Callback) {
        // Endpoint is owned by Silero VAD inside VoiceAssistantService.
    }

    companion object {
        private const val TAG = "VIVA_VOICE"
    }
}
