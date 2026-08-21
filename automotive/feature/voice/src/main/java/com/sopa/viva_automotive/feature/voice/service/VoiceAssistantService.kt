package com.sopa.viva_automotive.feature.voice.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.sopa.viva_automotive.core.database.settings.SettingsDataStore
import com.sopa.viva_automotive.feature.voice.data.asr.AsrTurnContext
import com.sopa.viva_automotive.feature.voice.data.audio.VadUtteranceCapture
import com.sopa.viva_automotive.feature.voice.data.audio.VoiceSessionDucker
import com.sopa.viva_automotive.feature.voice.di.VoiceModule
import com.sopa.viva_automotive.feature.voice.domain.VoiceAssistantStateManager
import com.sopa.viva_automotive.feature.voice.domain.VoiceTurnHistoryRecorder
import com.sopa.viva_automotive.feature.voice.domain.VoiceTurnReport
import com.sopa.viva_automotive.feature.voice.domain.model.VoiceAssistantState
import com.sopa.viva_automotive.feature.voice.via.HotwordAckPlayer
import com.viva.voice.agent.VoiceAgent
import com.viva.voice.agent.VoiceTurnResult
import com.viva.voice.audio.Trigger
import com.viva.voice.hotword.HotwordGate
import com.viva.voice.trace.Stage
import com.viva.voice.trace.startSilentTrace
import com.viva.voice.trace.startVoiceTrace
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class VoiceAssistantService : LifecycleService() {

    @Inject lateinit var stateManager: VoiceAssistantStateManager
    @Inject lateinit var voiceAgent: VoiceAgent
    @Inject lateinit var vadCapture: VadUtteranceCapture
    @Inject lateinit var sessionDucker: VoiceSessionDucker
    @Inject lateinit var hotwordAckPlayer: HotwordAckPlayer
    @Inject lateinit var settingsDataStore: SettingsDataStore
    @Inject lateinit var asrTurnContext: AsrTurnContext
    @Inject lateinit var historyRecorder: VoiceTurnHistoryRecorder

    private var pipelineJob: Job? = null
    private val pendingTextCommands = ArrayDeque<String>()
    private var activeTrigger: Trigger = Trigger.PUSH_TO_TALK
    private var activeShowSource: String = "tap_to_talk"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START_LISTENING -> {
                activeTrigger = intent.getStringExtra(EXTRA_TRIGGER)
                    ?.let { runCatching { Trigger.valueOf(it) }.getOrNull() }
                    ?: Trigger.PUSH_TO_TALK
                activeShowSource = intent.getStringExtra(EXTRA_SHOW_SOURCE) ?: "tap_to_talk"
                Log.i(VOICE_TAG, "START_LISTENING trigger=$activeTrigger source=$activeShowSource")
                HotwordGate.pause("voice_session")
                startForegroundWithNotification()
                startPipeline()
            }
            ACTION_PROCESS_TEXT -> {
                val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
                startForegroundWithNotification()
                startTextPipeline(text)
            }
            ACTION_STOP -> {
                pipelineJob?.cancel()
                pendingTextCommands.clear()
                sessionDucker.end("voice_stop")
                HotwordGate.resume("voice_stop")
                stateManager.transitionToIdle()
                stopSelf()
            }
            ACTION_SIMULATE_UTTERANCE -> {
                val debuggable =
                    applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
                val utterance = intent.getStringExtra(EXTRA_UTTERANCE).orEmpty()
                when {
                    !debuggable ->
                        Log.w(VOICE_TAG, "Skip $ACTION_SIMULATE_UTTERANCE: non-debuggable build")
                    utterance.isBlank() ->
                        Log.w(VOICE_TAG, "Skip $ACTION_SIMULATE_UTTERANCE: missing $EXTRA_UTTERANCE")
                    else -> {
                        startForegroundWithNotification()
                        startPipeline { runInjectedUtterance(utterance) }
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startPipeline(turn: suspend () -> Unit = { runInteraction() }) {
        if (pipelineJob?.isActive == true) return
        pipelineJob = lifecycleScope.launch {
            try {
                turn()
            } finally {
                HotwordGate.resume("voice_session")
                stopSelf()
            }
        }
    }

    private fun startTextPipeline(text: String) {
        if (pipelineJob?.isActive == true) {
            pendingTextCommands.addLast(text)
            return
        }
        HotwordGate.pause("voice_text")
        pipelineJob = lifecycleScope.launch {
            try {
                var next: String? = text
                while (next != null) {
                    asrTurnContext.markTextOnly()
                    val trace = startVoiceTrace()
                    trace.mark(Stage.SPEECH_END)
                    trace.mark(Stage.ASR_DONE)
                    applyAgentResult(
                        voiceAgent.handleText(next, trace),
                        displayTranscript = next,
                    )
                    next = pendingTextCommands.removeFirstOrNull()
                }
            } finally {
                HotwordGate.resume("voice_text")
                stopSelf()
            }
        }
    }

    private suspend fun runInteraction() {
        // Duck media for the whole turn (listen → ASR → TTS), like Windows
        // Communications ducking — lower volume, don't pause the track.
        sessionDucker.begin("voice_turn")
        try {
            val fromHotword = activeTrigger == Trigger.WAKE_WORD
            if (fromHotword) {
                stateManager.transitionToWakeDetected()
                // Cue and mic start together — do not wait for the beep to finish.
                if (settingsDataStore.settings.first().playAudioCues) {
                    Log.i(VOICE_TAG, "Starting wake ack alongside listen")
                    hotwordAckPlayer.play()
                }
            }
            stateManager.transitionToListening(fromHotword = fromHotword)

            val captured = runCatching {
                withContext(Dispatchers.IO) {
                    Log.i(VOICE_TAG, "Starting Silero VAD capture")
                    val utterance = vadCapture.capture(maxWaitForSpeechMs = LISTENING_TIMEOUT_MS)
                    Log.i(VOICE_TAG, "Silero VAD capture finished samples=${utterance.pcm.size}")
                    utterance
                }
            }.getOrElse { error ->
                Log.e(VOICE_TAG, "Mic capture failed", error)
                hotwordAckPlayer.stop()
                stateManager.transitionToError(VoiceTurnReport.MICROPHONE_UNAVAILABLE)
                historyRecorder.recordEarlyFailure(
                    status = "MICROPHONE_UNAVAILABLE",
                    note = VoiceTurnReport.MICROPHONE_UNAVAILABLE,
                )
                delay(RESULT_DISPLAY_MS)
                stateManager.transitionToIdle()
                return
            }

            // Stop residual cue before ASR/TTS so they don't stack.
            hotwordAckPlayer.stop()

            Log.i(
                VOICE_TAG,
                "Captured utterance samples=${captured.pcm.size} rate=${captured.sampleRate} " +
                    "usable=${captured.isUsable} tooShort=${captured.tooShort}",
            )

            if (!captured.isUsable) {
                stateManager.transitionToError(VoiceTurnReport.DID_NOT_HEAR)
                historyRecorder.recordEarlyFailure(
                    status = "DID_NOT_HEAR",
                    note = VoiceTurnReport.DID_NOT_HEAR,
                )
                delay(RESULT_DISPLAY_MS)
                stateManager.transitionToIdle()
                return
            }

            val trace = startVoiceTrace(captured.startNanos)
            // Hai mốc, không phải một: ACOUSTIC_END là lúc tài xế dứt câu,
            // SPEECH_END là lúc VAD quyết định. Hiệu số giữa chúng chính là
            // giá phải trả cho `minSilenceMs` — trước đây nó vô hình.
            trace.markAt(Stage.ACOUSTIC_END, captured.acousticEndNanos)
            trace.markAt(Stage.SPEECH_END, captured.endNanos)
            stateManager.transitionToProcessing("…")
            val result = voiceAgent.handleAudio(captured.pcm, captured.sampleRate, trace)
            Log.i(
                VOICE_TAG,
                "VoiceAgent status=${result.status} transcript=\"${result.transcript}\" " +
                    "spoken=\"${result.spokenVi}\"",
            )
            applyAgentResult(result, displayTranscript = result.transcript.ifBlank { "…" })
        } finally {
            hotwordAckPlayer.stop()
            sessionDucker.end("voice_turn")
        }
    }

    private suspend fun runInjectedUtterance(utterance: String) {
        asrTurnContext.markTextOnly()
        val trace = startSilentTrace()
        Log.i(BENCH_TAG, "$BENCH_TAG|${trace.traceId}|injected_text|$utterance")
        applyAgentResult(
            voiceAgent.handleText(utterance, trace),
            displayTranscript = utterance,
        )
    }

    private suspend fun applyAgentResult(
        result: VoiceTurnResult,
        displayTranscript: String,
    ) {
        // onResultReady already published UI before TTS. Only re-publish if we are
        // still on Processing (callback missing / failed before state change).
        if (stateManager.state.value is VoiceAssistantState.Processing) {
            VoiceModule.publishTurnUi(stateManager, result, displayTranscript)
        }
        delay(RESULT_DISPLAY_MS)
        stateManager.transitionToIdle()
    }

    private fun startForegroundWithNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Voice assistant",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Viva voice assistant")
            .setContentText("Voice session active")
            .setOngoing(true)
            .build()
        startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )
    }

    companion object {
        private const val ACTION_START_LISTENING = "com.sopa.viva_automotive.action.START_LISTENING"
        private const val ACTION_PROCESS_TEXT = "com.sopa.viva_automotive.action.PROCESS_TEXT"
        private const val ACTION_STOP = "com.sopa.viva_automotive.action.STOP"
        private const val EXTRA_TEXT = "text"
        private const val CHANNEL_ID = "voice_assistant"
        private const val NOTIFICATION_ID = 0x5641
        private const val LISTENING_TIMEOUT_MS = 8_000L
        private const val RESULT_DISPLAY_MS = 3_000L
        private const val VOICE_TAG = "VIVA_VOICE"

        const val ACTION_SIMULATE_UTTERANCE =
            "com.sopa.viva_automotive.action.SIMULATE_UTTERANCE"
        const val EXTRA_UTTERANCE = "utterance"
        const val EXTRA_TRIGGER = "viva_trigger"
        const val EXTRA_SHOW_SOURCE = "viva_show_source"
        const val BENCH_TAG = "VIVA_BENCH_INJECT"

        fun startListening(
            context: Context,
            trigger: Trigger = Trigger.PUSH_TO_TALK,
            showSource: String = "tap_to_talk",
        ) {
            context.startForegroundService(
                Intent(context, VoiceAssistantService::class.java)
                    .setAction(ACTION_START_LISTENING)
                    .putExtra(EXTRA_TRIGGER, trigger.name)
                    .putExtra(EXTRA_SHOW_SOURCE, showSource),
            )
        }

        fun processText(context: Context, text: String) {
            context.startForegroundService(
                Intent(context, VoiceAssistantService::class.java)
                    .setAction(ACTION_PROCESS_TEXT)
                    .putExtra(EXTRA_TEXT, text),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, VoiceAssistantService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
