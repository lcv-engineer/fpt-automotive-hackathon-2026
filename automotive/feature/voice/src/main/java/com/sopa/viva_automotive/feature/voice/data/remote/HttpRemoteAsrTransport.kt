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
            require(uri.scheme.equals("https", ignoreCase = true) ||
                (uri.scheme.equals("http", ignoreCase = true) && isTrustedCleartextHost(uri.host))) {
                "vivaAsrBaseUrl must use HTTPS; HTTP is allowed only for loopback " +
                    "or a private address on the same isolated segment"
            }
            return uri.toURL()
        }

        /**
         * Nơi được phép gửi audio dạng rõ.
         *
         * Lý do có luật này: một lượt thoại là giọng nói thật của tài xế, và gửi
         * nó qua HTTP trên mạng không tin được là rò rỉ. HTTPS là mặc định.
         *
         * Hai ngoại lệ, cùng một lý lẽ — gói tin không rời khỏi một đoạn mạng đã
         * cô lập, nên không có ai ở giữa để nghe trộm:
         *
         *  - **loopback**: `adb reverse` trên máy dev, gói không ra khỏi thiết bị.
         *  - **địa chỉ private (RFC 1918)**: room CarSky nối các node bằng một
         *    bridge L2 ảo trong `10.99.0.0/24` — Android ở `.14`, `viva-asr` ở
         *    `.3`. Đoạn này không định tuyến ra internet, và container ASR không
         *    có TLS. Xem `docs/backend-docs/carsky-runbook.md` §6.
         *
         * KHÔNG nới rộng thêm: mọi địa chỉ công khai vẫn bắt buộc HTTPS. Một
         * hostname (kể cả trỏ tới IP private) cũng bị từ chối — phân giải tên xảy
         * ra sau khi kiểm, nên cho qua theo tên là mở một lỗ không kiểm được.
         */
        private fun isTrustedCleartextHost(host: String?): Boolean {
            if (host == null) return false
            if (host == "127.0.0.1" || host.equals("localhost", ignoreCase = true)) return true
            val octets = host.split('.')
                .takeIf { it.size == 4 }
                ?.mapNotNull { it.toIntOrNull()?.takeIf { n -> n in 0..255 } }
                ?.takeIf { it.size == 4 }
                ?: return false
            val (a, b) = octets[0] to octets[1]
            return a == 10 ||
                (a == 172 && b in 16..31) ||
                (a == 192 && b == 168)
        }
    }
}
