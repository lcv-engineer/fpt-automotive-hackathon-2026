package com.sopa.viva_automotive.feature.media.data

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists embedded album art so AAOS Car Media / home widgets can load it via
 * a content:// URI (remote processes cannot read Media3 artwork byte arrays).
 */
@Singleton
class ArtworkStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val dir: File
        get() = File(context.filesDir, "artwork").also { it.mkdirs() }

    fun uriFor(mediaId: String): Uri =
        ArtworkProvider.uriFor(context.packageName, mediaId)

    fun fileFor(mediaId: String): File =
        File(dir, "${sanitize(mediaId)}.jpg")

    fun save(mediaId: String, bytes: ByteArray): Uri? {
        if (bytes.isEmpty()) return null
        return runCatching {
            val file = fileFor(mediaId)
            file.writeBytes(bytes)
            uriFor(mediaId)
        }.getOrNull()
    }

    fun existingUri(mediaId: String): Uri? =
        fileFor(mediaId).takeIf { it.isFile && it.length() > 0L }?.let { uriFor(mediaId) }

    private fun sanitize(mediaId: String): String =
        mediaId.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
