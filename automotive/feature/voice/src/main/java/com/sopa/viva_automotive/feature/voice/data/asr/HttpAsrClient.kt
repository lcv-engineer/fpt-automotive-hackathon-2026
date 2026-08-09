package com.sopa.viva_automotive.feature.voice.data.asr

import android.util.Log
import com.viva.voice.BuildConfig
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
 * HTTP [AsrClient] for the `viva-asr` container (`vong2/03-contracts.md` §2).
 *
 * Base URL comes from `voice-core` [BuildConfig.ASR_BASE_URL]
 * (default `http://127.0.0.1:8080`, use `adb reverse tcp:8080 tcp:8080` on device).
 */
@Singleton
class HttpAsrClient @Inject constructor() : AsrClient {

    private val baseUrl: String = BuildConfig.ASR_BASE_URL.trimEnd('/')

    suspend fun warmUp() {
        runCatching {
            withContext(Dispatchers.IO) {
                val connection = open("GET", "$baseUrl/health")
                try {
                    connection.connectTimeout = CONNECT_TIMEOUT_MS
                    connection.readTimeout = READ_TIMEOUT_MS
                    val code = connection.responseCode
                    val body = readBody(connection)
                    Log.i(TAG, "health HTTP $code body=$body baseUrl=$baseUrl")
                    if (code != 200) {
                        error("viva-asr not ready: HTTP $code $body")
                    }
                } finally {
                    connection.disconnect()
                }
            }
        }.onFailure { Log.w(TAG, "viva-asr warm-up failed ($baseUrl)", it) }
    }

    override suspend fun transcribe(
        pcm16: ShortArray,
        sampleRate: Int,
        trace: LatencyTrace,
    ): AsrResult = withContext(Dispatchers.IO) {
        require(pcm16.isNotEmpty()) { "pcm16 must not be empty" }
        require(sampleRate > 0) { "sampleRate must be positive" }

        trace.mark(Stage.ASR_SENT)
        val started = System.nanoTime()

        val connection = open("POST", "$baseUrl/asr")
        try {
            connection.doOutput = true
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = TRANSCRIBE_TIMEOUT_MS
            connection.setRequestProperty("Content-Type", "application/octet-stream")
            connection.setRequestProperty("X-Sample-Rate", sampleRate.toString())
            connection.setRequestProperty("X-Trace-Id", trace.traceId)
            connection.setFixedLengthStreamingMode(pcm16.size * 2)

            connection.outputStream.use { output ->
                val bytes = ByteArray(pcm16.size * 2)
                ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcm16)
                output.write(bytes)
            }

            val code = connection.responseCode
            val body = readBody(connection)
            if (code != 200) {
                error("viva-asr HTTP $code: $body")
            }

            val json = JSONObject(body)
            val text = json.optString("text").trim()
            val acousticConfidence = if (json.has("confidence") && !json.isNull("confidence")) {
                json.optDouble("confidence").toFloat().coerceIn(0f, 1f)
            } else {
                null
            }
            val serverMs = json.optInt("server_ms", 0).coerceAtLeast(0)
            val elapsedMs = ((System.nanoTime() - started) / 1_000_000L).toInt().coerceAtLeast(0)

            trace.mark(Stage.ASR_DONE)
            Log.i(
                TAG,
                "transcribe samples=${pcm16.size} rate=$sampleRate " +
                    "text=\"$text\" conf=$acousticConfidence server_ms=$serverMs wall_ms=$elapsedMs",
            )
            AsrResult(text = text, acousticConfidence = acousticConfidence, serverMs = serverMs)
        } finally {
            connection.disconnect()
        }
    }

    private fun open(method: String, url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.instanceFollowRedirects = false
        return connection
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
        const val TAG = "HttpAsrClient"
        const val CONNECT_TIMEOUT_MS = 3_000
        const val READ_TIMEOUT_MS = 5_000
        const val TRANSCRIBE_TIMEOUT_MS = 30_000
    }
}
