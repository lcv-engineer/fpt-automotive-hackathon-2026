package com.sopa.viva_automotive.feature.voice.data.asr

import android.content.Context
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * Loads the mock/debug Google Cloud service-account JSON and mints OAuth access
 * tokens for Cloud Speech-to-Text. Never logs private key material.
 */
@Singleton
class GoogleSpeechCredentials @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()
    private var cachedToken: String? = null
    private var cachedExpiryEpochMs: Long = 0L
    private var account: ServiceAccount? = null

    suspend fun accessToken(): String = mutex.withLock {
        val now = System.currentTimeMillis()
        val existing = cachedToken
        if (existing != null && now < cachedExpiryEpochMs - 60_000L) {
            return existing
        }
        val sa = loadAccount()
        val jwt = createJwt(sa, now / 1_000L)
        val (token, expiresInSec) = exchangeJwt(sa.tokenUri, jwt)
        cachedToken = token
        cachedExpiryEpochMs = now + expiresInSec * 1_000L
        Log.i(TAG, "Refreshed Google Speech access token expires_in=${expiresInSec}s")
        token
    }

    fun hasCredentials(): Boolean = runCatching { loadAccount(); true }.getOrDefault(false)

    private fun loadAccount(): ServiceAccount {
        account?.let { return it }
        val json = openCredentialsJson()
        val obj = JSONObject(json)
        val loaded = ServiceAccount(
            clientEmail = obj.getString("client_email"),
            privateKeyPem = obj.getString("private_key"),
            tokenUri = obj.optString("token_uri", DEFAULT_TOKEN_URI),
            projectId = obj.optString("project_id", ""),
        )
        account = loaded
        Log.i(TAG, "Loaded Speech SA email=${loaded.clientEmail} project=${loaded.projectId}")
        return loaded
    }

    private fun openCredentialsJson(): String {
        // Prefer mock secrets asset; fall back to filesDir drop-in for devices.
        val fromAssets = runCatching {
            context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        }.getOrNull()
        if (!fromAssets.isNullOrBlank()) return fromAssets

        val file = context.filesDir.resolve("secrets/google_speech_sa.json")
        if (file.isFile) return file.readText()

        error(
            "Google Speech credentials missing. Place service-account JSON at " +
                "assets/$ASSET_PATH (mock) or ${file.absolutePath}",
        )
    }

    private fun createJwt(sa: ServiceAccount, nowEpochSec: Long): String {
        val header = base64Url(
            """{"alg":"RS256","typ":"JWT"}""",
        )
        val claim = base64Url(
            JSONObject()
                .put("iss", sa.clientEmail)
                .put("scope", SPEECH_SCOPE)
                .put("aud", sa.tokenUri)
                .put("iat", nowEpochSec)
                .put("exp", nowEpochSec + 3_600L)
                .toString(),
        )
        val signingInput = "$header.$claim"
        val signature = signRs256(signingInput.toByteArray(Charsets.UTF_8), sa.privateKeyPem)
        return "$signingInput.${base64Url(signature)}"
    }

    private fun exchangeJwt(tokenUri: String, jwt: String): Pair<String, Long> {
        val body = "grant_type=" +
            URLEncoder.encode(JWT_BEARER_GRANT, Charsets.UTF_8.name()) +
            "&assertion=" + URLEncoder.encode(jwt, Charsets.UTF_8.name())
        val connection = URL(tokenUri).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val response = readBody(connection)
            if (code !in 200..299) {
                error("Google token exchange HTTP $code: ${response.take(300)}")
            }
            val json = JSONObject(response)
            val token = json.getString("access_token")
            val expiresIn = json.optLong("expires_in", 3_600L)
            return token to expiresIn
        } finally {
            connection.disconnect()
        }
    }

    private fun signRs256(data: ByteArray, privateKeyPem: String): ByteArray {
        val pkcs8 = privateKeyPem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\n", "")
            .replace("\n", "")
            .replace("\r", "")
            .trim()
        val keyBytes = Base64.decode(pkcs8, Base64.DEFAULT)
        val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyBytes))
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(key)
        signature.update(data)
        return signature.sign()
    }

    private fun base64Url(text: String): String =
        base64Url(text.toByteArray(Charsets.UTF_8))

    private fun base64Url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

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

    private data class ServiceAccount(
        val clientEmail: String,
        val privateKeyPem: String,
        val tokenUri: String,
        val projectId: String,
    )

    private companion object {
        const val TAG = "GoogleSpeechCreds"
        const val ASSET_PATH = "secrets/google_speech_sa.json"
        const val DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token"
        const val SPEECH_SCOPE =
            "https://www.googleapis.com/auth/cloud-platform " +
                "https://www.googleapis.com/auth/cloud-speech"
        const val JWT_BEARER_GRANT = "urn:ietf:params:oauth:grant-type:jwt-bearer"
    }
}
