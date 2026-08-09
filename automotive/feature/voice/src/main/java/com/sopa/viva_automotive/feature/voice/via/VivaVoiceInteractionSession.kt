package com.sopa.viva_automotive.feature.voice.via

import android.content.Context
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.util.Log
import android.view.View
import com.viva.voice.audio.Trigger

/**
 * Bridges system PTT/TTT/hotword triggers into the in-app pipeline.
 *
 * UI lives in Compose ([com.sopa.viva_automotive.feature.voice.presentation.VoiceSessionModal]),
 * so the session window is disabled — otherwise AAOS System UI keeps its own
 * "Listening…" chip in the status bar while our modal also shows.
 */
class VivaVoiceInteractionSession(
    context: Context,
    private val sessionBridge: VoiceSessionBridge,
) : VoiceInteractionSession(context) {

    override fun onPrepareShow(args: Bundle?, showFlags: Int) {
        super.onPrepareShow(args, showFlags)
        // AOSP voice guide: disable the default session window when UI is elsewhere.
        setUiEnabled(false)
    }

    override fun onCreateContentView(): View {
        // Unused while [setUiEnabled] is false; keep a stub for API contract.
        return View(context).apply { visibility = View.GONE }
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        Log.i(TAG, "onShow flags=$showFlags args=$args (ui disabled; Compose modal owns UX)")

        val fromArgs = args?.getString("viva_trigger")?.let { name ->
            runCatching { Trigger.valueOf(name) }.getOrNull()
        }
        val showSourceArg = args?.getString("viva_show_source")
        val (trigger, showSource) = if (fromArgs != null && showSourceArg != null) {
            fromArgs to showSourceArg
        } else {
            VoiceSessionBridge.triggerFromShowFlags(showFlags)
        }
        sessionBridge.startListening(trigger, showSource)
    }

    companion object {
        private const val TAG = "VIVA_VOICE"
    }
}
