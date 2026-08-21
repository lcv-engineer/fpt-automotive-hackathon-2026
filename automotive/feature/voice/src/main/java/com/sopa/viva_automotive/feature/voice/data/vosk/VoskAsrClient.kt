package com.sopa.viva_automotive.feature.voice.data.vosk

import android.content.Context
import android.util.Log
import com.sopa.viva_automotive.core.database.settings.SettingsDataStore
import com.sopa.viva_automotive.core.ui.locale.VoiceLanguage
import com.viva.voice.asr.AsrClient
import com.viva.voice.asr.AsrResult
import com.viva.voice.trace.LatencyTrace
import com.viva.voice.trace.Stage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

/**
 * On-device STT adapter for Long's [AsrClient] boundary.
 * Captured PCM from voice-core is transcribed here; NLU/TTS stay in VoiceAgent.
 */
@Singleton
class VoskAsrClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
) : AsrClient {

    private val mutex = Mutex()

    @Volatile
    private var model: Model? = null

    @Volatile
    private var loadedLanguage: VoiceLanguage? = null

    suspend fun warmUp() {
        runCatching { ensureModelLoaded() }
            .onFailure { Log.w(TAG, "Vosk warm-up failed", it) }
    }

    override suspend fun transcribe(
        pcm16: ShortArray,
        sampleRate: Int,
        trace: LatencyTrace,
    ): AsrResult {
        require(pcm16.isNotEmpty()) { "pcm16 must not be empty" }
        require(sampleRate > 0) { "sampleRate must be positive" }

        trace.mark(Stage.ASR_SENT)
        val started = System.nanoTime()

        ensureModelLoaded()
        val loaded = model ?: error("Vosk model failed to load")

        val text = mutex.withLock {
            val recognizer = Recognizer(loaded, sampleRate.toFloat())
            try {
                var offset = 0
                while (offset < pcm16.size) {
                    val len = minOf(CHUNK_SAMPLES, pcm16.size - offset)
                    val chunk = if (offset == 0 && len == pcm16.size) {
                        pcm16
                    } else {
                        pcm16.copyOfRange(offset, offset + len)
                    }
                    recognizer.acceptWaveForm(chunk, len)
                    offset += len
                }
                JSONObject(recognizer.finalResult).optString("text").trim()
            } finally {
                recognizer.close()
            }
        }

        val serverMs = ((System.nanoTime() - started) / 1_000_000L).toInt().coerceAtLeast(0)
        trace.mark(Stage.ASR_DONE)
        Log.i(TAG, "transcribe sampleRate=$sampleRate samples=${pcm16.size} text=\"$text\" ${serverMs}ms")

        // Vosk small does not expose acoustic confidence; null keeps G3 honest.
        return AsrResult(
            text = text,
            acousticConfidence = null,
            serverMs = serverMs,
        )
    }

    private suspend fun ensureModelLoaded() {
        mutex.withLock {
            val language = VoiceLanguage.fromStorageKey(
                settingsDataStore.settings.first().voiceLanguage,
            )
            if (model != null && loadedLanguage == language) return@withLock

            runCatching { model?.close() }
            model = null
            loadedLanguage = null

            val assetDir = language.voskAssetDir
            val modelDir = File(context.filesDir, assetDir)
            if (!modelDir.resolve(MODEL_READY_MARKER).exists()) {
                copyAssetDir(assetDir, modelDir)
                modelDir.resolve(MODEL_READY_MARKER).createNewFile()
            }
            require(modelDir.resolve("conf/model.conf").exists()) {
                "Vosk model missing conf/model.conf under assets/$assetDir"
            }
            model = Model(modelDir.absolutePath)
            loadedLanguage = language
            Log.i(TAG, "Loaded Vosk model for ${language.storageKey} ($assetDir)")
            Unit
        }
    }

    private fun copyAssetDir(assetPath: String, target: File) {
        val assets = context.assets
        val children = assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            if (assetPath == "model-en-us" || assetPath == "model-vi") {
                error("Vosk model not found in assets/$assetPath")
            }
            target.parentFile?.mkdirs()
            assets.open(assetPath).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        } else {
            target.mkdirs()
            children.forEach { child ->
                copyAssetDir("$assetPath/$child", File(target, child))
            }
        }
    }

    private companion object {
        const val TAG = "VoskAsrClient"
        const val MODEL_READY_MARKER = ".unpacked"
        const val CHUNK_SAMPLES = 4_096
    }
}
