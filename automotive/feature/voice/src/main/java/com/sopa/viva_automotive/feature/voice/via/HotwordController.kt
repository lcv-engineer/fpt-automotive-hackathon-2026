package com.sopa.viva_automotive.feature.voice.via

import android.content.Context
import android.os.SystemClock
import android.service.voice.VoiceInteractionService
import android.util.Log
import com.sopa.viva_automotive.core.database.settings.SettingsDataStore
import com.viva.voice.audio.Trigger
import com.viva.voice.hotword.HotwordConstants
import com.viva.voice.hotword.HotwordGate
import com.viva.voice.hotword.HotwordMetrics
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Prefers DSP [DspHotwordDetector]; falls back to [SoftwareHotwordEngine] when
 * SoundTrigger hardware/keyphrase is unavailable (AOSP integration-flows note).
 *
 * [ensureBound] lets the host app arm software KWS without being the default VIA
 * (needed for emulator bring-up).
 */
@Singleton
class HotwordController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val sessionBridge: VoiceSessionBridge,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val metrics = HotwordMetrics()
    private var dsp: DspHotwordDetector? = null
    private var software: SoftwareHotwordEngine? = null
    private var service: VoiceInteractionService? = null
    private var usingSoftware = false
    private var settingsJob: Job? = null

    private val _status = MutableStateFlow("idle")
    val status: StateFlow<String> = _status

    val isArmed: Boolean
        get() = _status.value.startsWith("software_listening") ||
            _status.value == "dsp_listening"

    fun metricsSnapshot(): HotwordMetrics.Snapshot =
        metrics.snapshot(SystemClock.elapsedRealtime())

    /** Observe settings and arm/disarm without requiring ROLE_ASSISTANT. */
    fun ensureBound() {
        if (settingsJob != null) return
        settingsJob = scope.launch {
            settingsDataStore.settings.collect { settings ->
                if (settings.hotwordEnabled && settings.voiceEnabled) {
                    start()
                } else {
                    stop()
                    _status.value = if (!settings.voiceEnabled) "voice_disabled" else "disabled"
                }
            }
        }
    }

    fun attach(service: VoiceInteractionService) {
        this.service = service
        KeyphraseSoundModelSupport.registerIfPresent(context)
        val dspProps = KeyphraseSoundModelSupport.dspModulePropertiesSummary(service)
        Log.i(TAG, "Hotword attach dspProps=$dspProps keyphrase=${HotwordConstants.KEYPHRASE}")
        ensureBound()
        // If already enabled, prefer DSP path now that VIA is ready.
        if (software != null && this.service != null) {
            stop()
            start()
        }
    }

    fun start() {
        if (dsp != null || software != null) return
        HotwordGate.forceResume()
        _status.value = "starting"
        val svc = service
        if (svc == null) {
            armSoftwareFallback("app_no_via")
            return
        }
        dsp = DspHotwordDetector(
            service = svc,
            onDetected = { onHotword("dsp") },
            onAvailability = { availability ->
                Log.i(TAG, "DSP availability=$availability")
                when (availability) {
                    DspHotwordDetector.Availability.KEYPHRASE_ENROLLED -> {
                        usingSoftware = false
                        software?.stop()
                        software = null
                        val ok = dsp?.startRecognition() == true
                        _status.value = if (ok) "dsp_listening" else "dsp_start_failed"
                    }
                    DspHotwordDetector.Availability.KEYPHRASE_UNENROLLED -> {
                        _status.value = "dsp_unenrolled"
                        armSoftwareFallback("dsp_unenrolled")
                    }
                    DspHotwordDetector.Availability.HARDWARE_UNAVAILABLE,
                    DspHotwordDetector.Availability.KEYPHRASE_UNSUPPORTED,
                    DspHotwordDetector.Availability.ERROR,
                    -> {
                        _status.value = availability.name.lowercase()
                        armSoftwareFallback(availability.name.lowercase())
                    }
                }
            },
        ).also { it.start() }
    }

    fun stop() {
        dsp?.destroy()
        dsp = null
        software?.stop()
        software = null
        usingSoftware = false
        _status.value = "disabled"
        HotwordGate.forceResume()
    }

    fun detach() {
        stop()
        service = null
    }

    fun saveSoftwareTemplate(pcm16: ShortArray) {
        HotwordTemplateStore.save(context, pcm16)
        software?.saveTemplate(pcm16)
    }

    fun enrollIntentFromDsp() = dsp?.createEnrollIntent()

    private fun armSoftwareFallback(reason: String) {
        if (usingSoftware && software != null) return
        usingSoftware = true
        val engine = SoftwareHotwordEngine(
            context = context,
            metrics = metrics,
            onDetected = { latency ->
                metrics.recordAccept(latency)
                onHotword("software")
            },
        )
        software = engine
        engine.loadTemplateFromDisk()
        engine.start(scope)
        _status.value = "software_listening:$reason"
        Log.i(TAG, "Armed software hotword fallback reason=$reason")
    }

    private fun onHotword(source: String) {
        if (HotwordGate.isPaused) {
            metrics.recordSelfWake()
            Log.w(TAG, "Ignoring hotword while paused source=$source")
            return
        }
        Log.i(TAG, "Hotword fire source=$source phrase=${HotwordConstants.KEYPHRASE}")
        // Wake-ack cue plays in VoiceAssistantService *alongside* VAD listen
        // (HotwordGate is already paused for the session).
        val shown = service?.let { tryShowSession(it) } == true
        if (!shown) {
            sessionBridge.startListening(
                trigger = Trigger.WAKE_WORD,
                showSource = VoiceSessionBridge.SHOW_SOURCE_HOTWORD,
            )
        }
    }

    private fun tryShowSession(svc: VoiceInteractionService): Boolean =
        runCatching {
            val showSession = svc.javaClass.methods.firstOrNull {
                it.name == "showSession" && it.parameterTypes.size >= 2
            } ?: return false
            val args = android.os.Bundle().apply {
                putString("viva_trigger", Trigger.WAKE_WORD.name)
                putString("viva_show_source", VoiceSessionBridge.SHOW_SOURCE_HOTWORD)
            }
            showSession.invoke(svc, args, VoiceSessionBridge.SHOW_SOURCE_ASSIST_GESTURE)
            true
        }.getOrElse { error ->
            Log.w(TAG, "showSession failed; will bridge directly", error)
            false
        }

    companion object {
        private const val TAG = "VIVA_VOICE"
    }
}
