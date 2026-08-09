package com.sopa.viva_automotive.feature.voice.data.asr

import android.util.Base64
import android.util.Log
import com.viva.voice.asr.AsrClient
import com.viva.voice.asr.AsrResult
import com.viva.voice.trace.LatencyTrace
import com.viva.voice.trace.Stage
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Utterance-mode Google Cloud Speech-to-Text (`speech:recognize`).
 *
 * Fits the existing VAD → full-PCM → [AsrClient] path. Streaming gRPC can layer
 * on later for interim partials; cabin mic capture + Silero endpointing stay
 * unchanged.
 */
@Singleton
class GoogleCloudSpeechAsrClient @Inject constructor(
    private val credentials: GoogleSpeechCredentials,
) : AsrClient {

    override suspend fun transcribe(
        pcm16: ShortArray,
        sampleRate: Int,
        trace: LatencyTrace,
    ): AsrResult = withContext(Dispatchers.IO) {
        require(pcm16.isNotEmpty()) { "pcm16 must not be empty" }
        require(sampleRate > 0) { "sampleRate must be positive" }

        trace.mark(Stage.ASR_SENT)
        val started = System.nanoTime()
        val token = credentials.accessToken()

        val pcmBytes = ByteArray(pcm16.size * 2)
        ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcm16)
        val audioB64 = Base64.encodeToString(pcmBytes, Base64.NO_WRAP)

        val body = JSONObject()
            .put(
                "config",
                JSONObject()
                    .put("encoding", "LINEAR16")
                    .put("sampleRateHertz", sampleRate)
                    .put("languageCode", LANGUAGE_CODE)
                    .put("enableAutomaticPunctuation", true)
                    .put("model", "latest_short"),
            )
            .put("audio", JSONObject().put("content", audioB64))
            .toString()

        val connection = URL(RECOGNIZE_URL).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = TRANSCRIBE_TIMEOUT_MS
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            val response = readBody(connection)
            if (code !in 200..299) {
                // Keep reason on one line — multiline System.out is hard to grep in logcat.
                val compact = response.replace('\n', ' ').replace(Regex("\\s+"), " ").take(500)
                Log.e(TAG, "recognize failed HTTP $code body=$compact")
                error("Google Speech HTTP $code: $compact")
            }

            val parsed = GoogleSpeechRecognizeParser.parse(response)
            val elapsedMs = ((System.nanoTime() - started) / 1_000_000L).toInt().coerceAtLeast(0)
            trace.mark(Stage.ASR_DONE)
            Log.i(
                TAG,
                "transcribe samples=${pcm16.size} rate=$sampleRate " +
                    "text=\"${parsed.text}\" conf=${parsed.confidence} wall_ms=$elapsedMs",
            )
            AsrResult(
                text = parsed.text,
                acousticConfidence = parsed.confidence,
                serverMs = elapsedMs,
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun readBody(connection: HttpURLConnection): String {
        val stream = if (connection.responseCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream ?: connection.inputStream
        } ?: return ""
        return BufferedInputStream(stream).use { input ->
            val buffer = ByteArrayOutputStream()
            input.copyTo(buffer)
            buffer.toString(Charsets.UTF_8.name())
        }
    }

    private companion object {
        const val TAG = "GoogleSpeechAsr"
        const val RECOGNIZE_URL = "https://speech.googleapis.com/v1/speech:recognize"
        const val LANGUAGE_CODE = "vi-VN"
        const val CONNECT_TIMEOUT_MS = 5_000
        const val TRANSCRIBE_TIMEOUT_MS = 30_000
    }
}

internal object GoogleSpeechRecognizeParser {
    data class Parsed(val text: String, val confidence: Float?)

    fun parse(body: String): Parsed {
        val root = JSONObject(body)
        val results = root.optJSONArray("results") ?: return Parsed("", null)
        if (results.length() == 0) return Parsed("", null)
        val first = results.getJSONObject(0)
        val alts = first.optJSONArray("alternatives") ?: return Parsed("", null)
        if (alts.length() == 0) return Parsed("", null)
        val best = alts.getJSONObject(0)
        val text = best.optString("transcript").trim()
        val confidence = if (best.has("confidence") && !best.isNull("confidence")) {
            best.optDouble("confidence").toFloat().coerceIn(0f, 1f)
        } else {
            null
        }
        return Parsed(text = text, confidence = confidence)
    }
}
