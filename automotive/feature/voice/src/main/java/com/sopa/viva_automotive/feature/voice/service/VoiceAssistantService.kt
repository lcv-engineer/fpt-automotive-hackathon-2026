package com.sopa.viva_automotive.feature.voice.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.sopa.viva_automotive.feature.voice.data.SpeechRecognitionEngine
import com.sopa.viva_automotive.feature.voice.data.TranscriptionEvent
import com.sopa.viva_automotive.feature.voice.domain.ExecuteVehicleControlUseCase
import com.sopa.viva_automotive.feature.voice.domain.ProcessVoiceCommandUseCase
import com.sopa.viva_automotive.feature.voice.domain.VoiceAssistantStateManager
import com.sopa.viva_automotive.feature.voice.domain.VoiceTurnReport
import com.sopa.viva_automotive.feature.voice.domain.embedding.SemanticIntentMatcher
import com.sopa.viva_automotive.feature.voice.domain.model.VehicleIntent
import com.viva.voice.trace.LatencyTrace
import com.viva.voice.trace.Stage
import com.viva.voice.trace.TraceVerdict
import com.viva.voice.trace.startVoiceTrace
import com.viva.voice.tts.TtsSpeaker
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@AndroidEntryPoint
class VoiceAssistantService : LifecycleService() {

    @Inject lateinit var speechEngine: SpeechRecognitionEngine
    @Inject lateinit var stateManager: VoiceAssistantStateManager
    @Inject lateinit var processVoiceCommand: ProcessVoiceCommandUseCase
    @Inject lateinit var executeVehicleControl: ExecuteVehicleControlUseCase
    @Inject lateinit var semanticIntentMatcher: SemanticIntentMatcher
    @Inject lateinit var ttsSpeaker: TtsSpeaker

    private var pipelineJob: Job? = null
    private var warmUpJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START_LISTENING -> {
                startForegroundWithNotification()
                warmUpEmbeddings()
                startPipeline()
            }
            ACTION_STOP -> {
                pipelineJob?.cancel()
                stateManager.transitionToIdle()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startPipeline() {
        if (pipelineJob?.isActive == true) return
        pipelineJob = lifecycleScope.launch {
            try {
                runInteraction()
            } finally {
                stopSelf()
            }
        }
    }

        private fun warmUpEmbeddings() {
        if (warmUpJob?.isActive == true) return
        warmUpJob = lifecycleScope.launch {
            runCatching { semanticIntentMatcher.warmUp() }
        }
    }

    private suspend fun runInteraction() {
        speechEngine.initialize().onFailure { error ->
            stateManager.transitionToError(
                error.message ?: "Voice recognition is unavailable",
            )
            delay(RESULT_DISPLAY_MS)
            stateManager.transitionToIdle()
            return
        }

        stateManager.transitionToListening()

        // One trace per turn, opened when the microphone starts. This pipeline
        // has no VAD endpointer of its own, so `speech_start` is the moment we
        // begin listening rather than a detected onset, and `speech_end` is the
        // recognizer's own endpoint decision. Both are honest for a streaming
        // on-device ASR; do not back-date them to flatter the number.
        val trace = startVoiceTrace()

        var finalText: String? = null
        var engineError: String? = null
        trace.mark(Stage.ASR_SENT)
        withTimeoutOrNull(LISTENING_TIMEOUT_MS) {
            speechEngine.transcribe().collect { event ->
                when (event) {
                    is TranscriptionEvent.Partial ->
                        stateManager.updatePartialTranscription(event.text)
                    is TranscriptionEvent.Final -> {
                        trace.mark(Stage.SPEECH_END)
                        trace.mark(Stage.ASR_DONE)
                        finalText = event.text
                    }
                    is TranscriptionEvent.Error -> engineError = event.message
                }
            }
        }

        val utterance = finalText
        when {
            engineError != null -> {
                stateManager.transitionToError(engineError)
                speak(VoiceTurnReport.DID_NOT_HEAR, trace)
                trace.summary("-", "-", TraceVerdict.Error(Stage.ASR_DONE))
            }

            utterance == null -> {
                stateManager.transitionToError("I didn't hear anything")
                speak(VoiceTurnReport.DID_NOT_HEAR, trace)
                trace.summary("-", "-", TraceVerdict.Error(Stage.SPEECH_END))
            }

            else -> {
                stateManager.transitionToProcessing(utterance)
                val intent = processVoiceCommand(utterance)
                trace.mark(Stage.NLU_DONE)
                if (intent is VehicleIntent.Clarification) {
                    stateManager.transitionToClarification(intent.promptVi)
                    finish(trace, utterance, intent, error = null)
                } else {
                    stateManager.transitionToExecuting(describe(intent))
                    executeVehicleControl(intent).fold(
                        onSuccess = { message ->
                            trace.mark(Stage.EXEC_DONE)
                            stateManager.transitionToSuccess(message)
                            // The executor already answers in Vietnamese for the
                            // intents that have a pre-rendered clip, so the same
                            // string drives the HMI and the voice.
                            speak(message, trace)
                            trace.summary(
                                utterance,
                                VoiceTurnReport.intentName(intent),
                                TraceVerdict.Allow,
                            )
                        },
                        onFailure = { error ->
                            stateManager.transitionToError(error.message ?: "Command failed")
                            finish(trace, utterance, intent, error)
                        },
                    )
                }
            }
        }

        delay(RESULT_DISPLAY_MS)
        stateManager.transitionToIdle()
    }

    /** Speaks the turn's outcome and closes its trace. */
    private suspend fun finish(
        trace: LatencyTrace,
        utterance: String,
        intent: VehicleIntent,
        error: Throwable?,
    ) {
        speak(VoiceTurnReport.failureSpeech(intent, error), trace)
        trace.summary(
            utterance,
            VoiceTurnReport.intentName(intent),
            VoiceTurnReport.verdictFor(intent, error),
        )
    }

    /**
     * TTS is best-effort: it takes audio focus, and a rejected focus request or
     * a missing vi-VN voice throws. Losing the spoken answer is a degraded turn,
     * not a reason to take the assistant down mid-demo — but the failure is
     * logged so a silent run is diagnosable afterwards.
     */
    private suspend fun speak(text: String, trace: LatencyTrace) {
        runCatching { ttsSpeaker.speak(text, trace) }
            .onFailure { error -> Log.w(VOICE_TAG, "TTS failed for \"$text\": ${error.message}") }
    }

    private fun describe(intent: VehicleIntent): String = when (intent) {
        is VehicleIntent.SetTemperature -> "Setting temperature"
        is VehicleIntent.AdjustTemperature -> "Adjusting temperature"
        is VehicleIntent.SetFanSpeed, is VehicleIntent.AdjustFanSpeed -> "Adjusting fan"
        is VehicleIntent.SetAc -> "Switching air conditioning"
        is VehicleIntent.SetHvacPower -> "Switching climate system"
        is VehicleIntent.SetDoorLock -> "Updating door locks"
        is VehicleIntent.QueryStatus -> "Checking vehicle status"
        is VehicleIntent.VolumeAdjust -> "Adjusting volume"
        is VehicleIntent.MediaNext -> "Skipping to the next track"
        is VehicleIntent.Delivery -> "Checking the delivery route"
        is VehicleIntent.NotWired -> "Routing command"
        is VehicleIntent.Clarification -> "Clarifying command"
        is VehicleIntent.Unknown -> "Interpreting command"
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
            .setContentText("Listening for a command")
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
        private const val ACTION_STOP = "com.sopa.viva_automotive.action.STOP"
        private const val CHANNEL_ID = "voice_assistant"
        private const val NOTIFICATION_ID = 0x5641
        private const val LISTENING_TIMEOUT_MS = 15_000L
        private const val RESULT_DISPLAY_MS = 3_000L

        /** Diagnostics tag. Never VIVA_TRACE — that stream is the benchmark input. */
        private const val VOICE_TAG = "VIVA_VOICE"

        fun startListening(context: Context) {
            context.startForegroundService(
                Intent(context, VoiceAssistantService::class.java).setAction(ACTION_START_LISTENING),
            )
        }

                fun stop(context: Context) {
            context.startService(
                Intent(context, VoiceAssistantService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
