package com.sopa.viva_automotive.feature.media.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/**
 * Exported read-only provider for album art files under filesDir/artwork/.
 * Used by Car Media / cluster UIs that load [MediaDescriptionCompat] iconUri.
 */
class ArtworkProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode.contains('w')) {
            throw SecurityException("Artwork is read-only")
        }
        val mediaId = uri.lastPathSegment ?: throw FileNotFoundException("Missing mediaId")
        val context = context ?: throw FileNotFoundException("No context")
        val file = File(File(context.filesDir, "artwork"), sanitize(mediaId) + ".jpg")
        if (!file.isFile || file.length() <= 0L) {
            throw FileNotFoundException("No artwork for $mediaId")
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "image/jpeg"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    companion object {
        private const val PATH = "artwork"

        fun authority(packageName: String): String = "$packageName.artwork"

        fun uriFor(packageName: String, mediaId: String): Uri =
            Uri.Builder()
                .scheme("content")
                .authority(authority(packageName))
                .appendPath(PATH)
                .appendPath(sanitize(mediaId))
                .build()

        private fun sanitize(mediaId: String): String =
            mediaId.replace(Regex("[^A-Za-z0-9._-]"), "_")

        // Keep matcher for future query support.
        @Suppress("unused")
        private val matcher = UriMatcher(UriMatcher.NO_MATCH)
    }
}
