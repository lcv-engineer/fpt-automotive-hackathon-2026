package com.sopa.viva_automotive.feature.voice.via

import android.os.Bundle
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges in-app ASR transcripts back to [RecognitionService.Callback] so other
 * apps using SpeechRecognizer get real results instead of an immediate ERROR_CLIENT.
 */
@Singleton
class RecognitionResultHub @Inject constructor() {

    @Volatile
    private var callback: RecognitionService.Callback? = null

    fun beginSession(listener: RecognitionService.Callback) {
        callback = listener
    }

    fun signalListening() {
        val listener = callback ?: return
        runCatching { listener.readyForSpeech(Bundle()) }
            .onFailure { Log.w(TAG, "readyForSpeech failed", it) }
        runCatching { listener.beginningOfSpeech() }
            .onFailure { Log.w(TAG, "beginningOfSpeech failed", it) }
    }

    fun publishPartial(text: String) {
        val listener = callback ?: return
        if (text.isBlank()) return
        val bundle = Bundle().apply {
            putStringArrayList(
                SpeechRecognizer.RESULTS_RECOGNITION,
                arrayListOf(text),
            )
        }
        runCatching { listener.partialResults(bundle) }
            .onFailure { Log.w(TAG, "partialResults failed", it) }
    }

    fun publishFinal(text: String) {
        val listener = callback ?: return
        callback = null
        val spoken = text.ifBlank { "…" }
        val bundle = Bundle().apply {
            putStringArrayList(
                SpeechRecognizer.RESULTS_RECOGNITION,
                arrayListOf(spoken),
            )
            putFloatArray(SpeechRecognizer.CONFIDENCE_SCORES, floatArrayOf(1f))
        }
        runCatching {
            listener.endOfSpeech()
            listener.results(bundle)
        }.onFailure { Log.w(TAG, "results failed", it) }
    }

    fun cancel(errorCode: Int = SpeechRecognizer.ERROR_CLIENT) {
        val listener = callback ?: return
        callback = null
        runCatching { listener.error(errorCode) }
            .onFailure { Log.w(TAG, "error callback failed", it) }
    }

    private companion object {
        const val TAG = "VIVA_VOICE"
    }
}
