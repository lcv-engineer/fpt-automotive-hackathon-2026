package com.sopa.viva_automotive.feature.voice.data.brain

import android.util.Log
import com.sopa.viva_automotive.core.common.device.AndroidEmulator
import com.sopa.viva_automotive.feature.voice.BuildConfig
import com.viva.voice.agent.AgentPlanResult
import com.viva.voice.agent.AgentPlanner
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Slow-path adapter to the server-side VIVA Brain gateway.
 *
 * The OpenAI credential never enters the APK. This client sends only the
 * transcript and trace ID to a trusted VIVA endpoint, then validates the
 * returned proposal before exposing it to [com.viva.voice.agent.VoiceAgent].
 */
@Singleton
class RemoteLlmAgentPlanner @Inject constructor() : AgentPlanner {
    private val endpoint = validatedEndpoint(resolveBaseUrl(BuildConfig.BRAIN_BASE_URL))

    override suspend fun plan(text: String, traceId: String): AgentPlanResult {
        if (!BuildConfig.BRAIN_AGENT_ENABLED) {
            return AgentPlanResult.Unavailable("brain agent disabled by build config")
        }
        if (text.isBlank() || text.length > MAX_TRANSCRIPT_CHARS) {
            return AgentPlanResult.Unavailable("transcript is outside the brain contract")
        }

        return withContext(Dispatchers.IO) {
            val connection = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                useCaches = false
                instanceFollowRedirects = false
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("X-Trace-Id", traceId)
            }
            try {
                val requestBody = JSONObject()
                    .put("text", text)
                    .put("trace_id", traceId)
                    .toString()
                    .toByteArray(Charsets.UTF_8)
                connection.setFixedLengthStreamingMode(requestBody.size)
                connection.outputStream.use { it.write(requestBody) }

                val status = connection.responseCode
                val responseBody = responseReader(connection, status).use { it.readLimited() }
                if (status !in 200..299) {
                    Log.w(TAG, "brain planner HTTP $status trace=$traceId")
                    AgentPlanResult.Unavailable("brain planner HTTP $status")
                } else {
                    BrainPlanResponseParser.parse(responseBody)
                }
            } catch (error: Exception) {
                Log.w(TAG, "brain planner unavailable trace=$traceId", error)
                AgentPlanResult.Unavailable(error.message ?: "brain planner unavailable")
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun responseReader(connection: HttpURLConnection, status: Int): BufferedReader {
        val stream = if (status in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream ?: connection.inputStream
        }
        return requireNotNull(stream) { "brain planner returned an empty response" }.bufferedReader()
    }

    private fun java.io.Reader.readLimited(): String {
        val output = StringBuilder()
        val buffer = CharArray(1_024)
        while (output.length <= MAX_RESPONSE_CHARS) {
            val read = read(buffer, 0, minOf(buffer.size, MAX_RESPONSE_CHARS + 1 - output.length))
            if (read < 0) return output.toString()
            output.append(buffer, 0, read)
        }
        error("brain response exceeds $MAX_RESPONSE_CHARS characters")
    }

    private companion object {
        const val TAG = "VivaBrainAgent"
        const val CONNECT_TIMEOUT_MS = 2_000
        const val READ_TIMEOUT_MS = 6_000
        const val MAX_TRANSCRIPT_CHARS = 500
        const val MAX_RESPONSE_CHARS = 8 * 1_024
        const val EMULATOR_HOST_LOOPBACK = "10.0.2.2"

        fun resolveBaseUrl(configured: String): String {
            val trimmed = configured.trimEnd('/')
            if (!AndroidEmulator.isEmulator()) return trimmed
            return trimmed
                .replace("://127.0.0.1", "://$EMULATOR_HOST_LOOPBACK")
                .replace("://localhost", "://$EMULATOR_HOST_LOOPBACK")
        }

        fun validatedEndpoint(baseUrl: String): java.net.URL {
            val uri = URI("${baseUrl.trimEnd('/')}/v1/brain/plan")
            require(uri.scheme.equals("https", ignoreCase = true) ||
                (uri.scheme.equals("http", ignoreCase = true) && isTrustedCleartextHost(uri.host))) {
                "vivaBrainBaseUrl must use HTTPS; cleartext is limited to loopback/private hosts"
            }
            return uri.toURL()
        }

        private fun isTrustedCleartextHost(host: String?): Boolean {
            if (host == null) return false
            if (host == "127.0.0.1" || host.equals("localhost", ignoreCase = true)) return true
            val octets = host.split('.')
                .takeIf { it.size == 4 }
                ?.mapNotNull { it.toIntOrNull()?.takeIf { value -> value in 0..255 } }
                ?.takeIf { it.size == 4 }
                ?: return false
            val (a, b) = octets[0] to octets[1]
            return a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168)
        }
    }
}

