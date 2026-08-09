package com.sopa.viva_automotive.feature.voice.via

import android.content.Context
import android.content.Intent
import android.util.Log
import com.sopa.viva_automotive.feature.voice.service.VoiceAssistantService
import com.viva.voice.audio.Trigger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges AOSP VIA session show / hotword detect into the existing post-trigger
 * pipeline owned by [VoiceAssistantService].
 */
@Singleton
class VoiceSessionBridge @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun startListening(
        trigger: Trigger,
        showSource: String,
    ) {
        Log.i(TAG, "startListening trigger=$trigger showSource=$showSource")
        // HotwordGate pause/resume is owned by VoiceAssistantService for the turn.
        VoiceAssistantService.startListening(
            context,
            trigger = trigger,
            showSource = showSource,
        )
    }

    fun processText(text: String) {
        VoiceAssistantService.processText(context, text)
    }

    fun stop() {
        VoiceAssistantService.stop(context)
    }

    companion object {
        private const val TAG = "VIVA_VOICE"

        const val SHOW_SOURCE_HOTWORD = "hotword"
        const val SHOW_SOURCE_PTT = "push_to_talk"
        const val SHOW_SOURCE_TTT = "tap_to_talk"
        const val SHOW_SOURCE_ASSIST = "assist"

        fun triggerFromShowFlags(flags: Int): Pair<Trigger, String> {
            // VoiceInteractionSession.SHOW_SOURCE_PUSH_TO_TALK = 4 (API)
            // VoiceInteractionSession.SHOW_SOURCE_ASSIST_GESTURE = 2
            return when {
                flags and SHOW_SOURCE_PUSH_TO_TALK != 0 ->
                    Trigger.PUSH_TO_TALK to SHOW_SOURCE_PTT
                flags and SHOW_SOURCE_ASSIST_GESTURE != 0 ->
                    Trigger.WAKE_WORD to SHOW_SOURCE_HOTWORD
                else -> Trigger.PUSH_TO_TALK to SHOW_SOURCE_TTT
            }
        }

        // Mirrored from VoiceInteractionSession to avoid hard dependency surprises
        // on OEM stubs; values are stable in AOSP.
        const val SHOW_SOURCE_ASSIST_GESTURE = 1 shl 1
        const val SHOW_SOURCE_PUSH_TO_TALK = 1 shl 2
    }
}

/** Optional extras for debug intents. */
fun Intent.putVoiceSessionExtras(trigger: Trigger, showSource: String): Intent =
    putExtra(VoiceAssistantService.EXTRA_TRIGGER, trigger.name)
        .putExtra(VoiceAssistantService.EXTRA_SHOW_SOURCE, showSource)
