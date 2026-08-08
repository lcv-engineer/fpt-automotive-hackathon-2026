package com.sopa.viva_automotive.feature.voice.data.remote

import com.sopa.viva_automotive.core.common.coroutines.IoDispatcher
import com.sopa.viva_automotive.feature.voice.data.TranscriptionEvent
import java.net.HttpURLConnection
import java.net.URI
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Thin HTTP adapter for the exact `vong2/03-contracts.md` §2 wire contract. */
class HttpRemoteAsrTransport(
    baseUrl: String,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val connectTimeoutMs: Int = 1_000,
    private val readTimeoutMs: Int = 2_500,
) : RemoteAsrTransport {

    private val endpoint = validatedEndpoint(baseUrl)

    override suspend fun transcribe(
        pcm16: ShortArray,
        sampleRate: Int,
    ): RemoteAsrResponse = withContext(ioDispatcher) {
        val traceId = UUID.randomUUID().toString()
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            useCaches = false
            instanceFollowRedirects = false
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("Content-Type", "application/octet-stream")
            setRequestProperty("X-Sample-Rate", sampleRate.toString())
            setRequestProperty("X-Trace-Id", traceId)
            setFixedLengthStreamingMode(pcm16.size * 2)
        }

        try {
            connection.outputStream.use { it.write(encodePcm16Le(pcm16)) }
            val status = connection.responseCode
            if (status !in 200..299) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readLimited() }
                    .orEmpty()
                val code = if (status == HttpURLConnection.HTTP_UNAVAILABLE) {
                    TranscriptionEvent.CODE_MODEL_UNAVAILABLE
                } else {
                    TranscriptionEvent.CODE_ENGINE_FAILED
                }
                throw RemoteAsrException(code, "viva-asr HTTP $status: $detail")
            }
            parseResponse(connection.inputStream.bufferedReader().use { it.readLimited() })
        } catch (error: RemoteAsrException) {
            throw error
        } catch (error: IOException) {
            throw RemoteAsrException(
                TranscriptionEvent.CODE_MODEL_UNAVAILABLE,
                "cannot reach viva-asr at $endpoint: ${error.message}",
                error,
            )
        } catch (error: Exception) {
            throw RemoteAsrException(
                TranscriptionEvent.CODE_ENGINE_FAILED,
                "invalid viva-asr response: ${error.message}",
                error,
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun parseResponse(body: String): RemoteAsrResponse {
        val json = JSONObject(body)
        return RemoteAsrResponse(
            text = json.getString("text"),
            confidence = json.getDouble("confidence").toFloat(),
            serverMs = json.getInt("server_ms"),
        )
    }

    private fun java.io.Reader.readLimited(): String {
        val output = StringBuilder()
        val buffer = CharArray(1_024)
        while (output.length <= MAX_RESPONSE_CHARS) {
            val read = read(buffer, 0, minOf(buffer.size, MAX_RESPONSE_CHARS + 1 - output.length))
            if (read < 0) return output.toString()
            output.append(buffer, 0, read)
        }
        throw RemoteAsrException(
            TranscriptionEvent.CODE_ENGINE_FAILED,
            "viva-asr response exceeds $MAX_RESPONSE_CHARS characters",
        )
    }

    private companion object {
        const val MAX_RESPONSE_CHARS = 64 * 1_024

        fun validatedEndpoint(baseUrl: String): java.net.URL {
            val uri = URI(baseUrl.trimEnd('/') + "/asr")
            val loopback = uri.host == "127.0.0.1" || uri.host.equals("localhost", ignoreCase = true)
            require(uri.scheme.equals("https", ignoreCase = true) ||
                (uri.scheme.equals("http", ignoreCase = true) && loopback)) {
                "vivaAsrBaseUrl must use HTTPS; HTTP is allowed only for loopback adb reverse"
            }
            return uri.toURL()
        }
    }
}
