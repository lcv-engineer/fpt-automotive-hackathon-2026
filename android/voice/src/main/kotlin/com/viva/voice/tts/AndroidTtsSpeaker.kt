package com.viva.voice.tts

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.viva.voice.hotword.HotwordGate
import com.viva.voice.trace.LatencyTrace
import com.viva.voice.trace.Stage
import java.io.Closeable
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Vietnamese TTS for AAOS. Uses the installed engine first, then a bundled raw clip.
 * Call [close] from the owning Service/Application lifecycle to release native resources.
 */
class AndroidTtsSpeaker(
    context: Context,
    private val locale: Locale = Locale.forLanguageTag("vi-VN"),
    private val audioFocus: AudioFocusController = AndroidAudioFocusController(context.applicationContext),
) : TtsSpeaker, Closeable {
    private val appContext = context.applicationContext
    private val initLock = Any()
    private val initWaiters = mutableListOf<Continuation<Int>>()
    private val pending = ConcurrentHashMap<String, PendingUtterance>()
    private val rawPlayer = RawPromptPlayer(appContext)

    @Volatile
    private var initStatus: Int? = null

    private val engine: TextToSpeech = TextToSpeech(appContext) { status ->
        val waiters = synchronized(initLock) {
            initStatus = status
            initWaiters.toList().also { initWaiters.clear() }
        }
        waiters.forEach { it.resume(status) }
    }.also { tts ->
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                pending[utteranceId]?.trace?.mark(Stage.TTS_START)
            }

            override fun onDone(utteranceId: String) = complete(utteranceId, true)

            @Deprecated("Android calls this on API 15-20")
            override fun onError(utteranceId: String) = complete(utteranceId, false)

            override fun onError(utteranceId: String, errorCode: Int) =
                complete(utteranceId, false)

            override fun onStop(utteranceId: String, interrupted: Boolean) =
                complete(utteranceId, false)
        })
    }

    override suspend fun speak(text: String, trace: LatencyTrace) {
        require(text.isNotBlank()) { "TTS text must not be blank" }

        // Pause always-on hotword for the whole utterance to avoid self-wake.
        HotwordGate.pause("tts")
        try {
            withAudioFocus(audioFocus, ::stopPlayback) { ensureFocus ->
                if (canSpeakVietnamese() && speakWithEngine(text, trace)) return@withAudioFocus
                ensureFocus()
                if (rawPlayer.play(text, trace)) return@withAudioFocus

                error("No Vietnamese TTS voice or pre-rendered prompt for: $text")
            }
        } finally {
            HotwordGate.resume("tts")
        }
    }

    override fun stop() {
        stopPlayback()
        audioFocus.abandon()
    }

    private fun stopPlayback() {
        engine.stop()
        pending.keys.toList().forEach { utteranceId -> complete(utteranceId, false) }
        rawPlayer.stop()
    }

    override fun close() {
        stop()
        engine.shutdown()
    }

    private suspend fun canSpeakVietnamese(): Boolean {
        if (awaitInit() != TextToSpeech.SUCCESS) return false
        if (engine.setAudioAttributes(VoiceAudioAttributes.value) != TextToSpeech.SUCCESS) return false
        if (engine.isLanguageAvailable(locale) < TextToSpeech.LANG_AVAILABLE) return false
        return engine.setLanguage(locale) >= TextToSpeech.LANG_AVAILABLE
    }

    private suspend fun awaitInit(): Int = suspendCoroutine { continuation ->
        val ready = synchronized(initLock) {
            initStatus ?: run {
                initWaiters += continuation
                null
            }
        }
        if (ready != null) continuation.resume(ready)
    }

    private suspend fun speakWithEngine(text: String, trace: LatencyTrace): Boolean =
        suspendCoroutine { continuation ->
            val utteranceId = "viva-${UUID.randomUUID()}"
            pending[utteranceId] = PendingUtterance(trace, continuation)
            val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            if (result != TextToSpeech.SUCCESS) complete(utteranceId, false)
        }

    private fun complete(utteranceId: String, success: Boolean) {
        pending.remove(utteranceId)?.resume(success)
    }

    private class PendingUtterance(
        val trace: LatencyTrace,
        private val continuation: Continuation<Boolean>,
    ) {
        private val completed = AtomicBoolean(false)

        fun resume(success: Boolean) {
            if (completed.compareAndSet(false, true)) continuation.resume(success)
        }
    }

    private class RawPromptPlayer(
        private val context: Context,
    ) {
        private val lock = Any()
        private var active: RawPlayback? = null

        suspend fun play(text: String, trace: LatencyTrace): Boolean {
            val rawName = PrerenderedPrompts.rawNameFor(text) ?: NOTIFICATION_CUE
            val resourceId = context.resources.getIdentifier(rawName, "raw", context.packageName)
            if (resourceId == 0) return false

            return suspendCoroutine { continuation ->
                stop()
                val player = MediaPlayer.create(
                    context,
                    resourceId,
                    VoiceAudioAttributes.value,
                    0,
                )
                if (player == null) {
                    continuation.resume(false)
                    return@suspendCoroutine
                }
                val playback = RawPlayback(player, continuation)
                synchronized(lock) { active = playback }
                player.setOnCompletionListener { finish(playback, true) }
                player.setOnErrorListener { _, _, _ ->
                    finish(playback, false)
                    true
                }
                try {
                    trace.mark(Stage.TTS_START)
                    player.start()
                } catch (_: RuntimeException) {
                    finish(playback, false)
                }
            }
        }

        fun stop() {
            val playback = synchronized(lock) { active.also { active = null } } ?: return
            playback.player.runCatching { stop() }
            playback.complete(false)
        }

        private fun finish(playback: RawPlayback, success: Boolean) {
            synchronized(lock) {
                if (active === playback) active = null
            }
            playback.complete(success)
        }

        private companion object {
            const val NOTIFICATION_CUE = "viva_notification_ping"
        }
    }

    private class RawPlayback(
        val player: MediaPlayer,
        private val continuation: Continuation<Boolean>,
    ) {
        private val completed = AtomicBoolean(false)

        fun complete(success: Boolean) {
            if (completed.compareAndSet(false, true)) {
                player.release()
                continuation.resume(success)
            }
        }
    }
}
