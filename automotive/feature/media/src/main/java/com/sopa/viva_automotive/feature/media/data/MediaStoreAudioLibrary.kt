package com.sopa.viva_automotive.feature.media.data

import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.Manifest
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import com.sopa.viva_automotive.feature.media.domain.MediaTrack
import java.io.File

/**
 * Loads user audio from [MediaStore] (e.g. MP3s pushed to Download/Music).
 * Falls back to an empty list when permission is missing or the catalog is empty;
 * callers keep the demo tones in that case.
 */
object MediaStoreAudioLibrary {

    fun hasReadPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun loadTracks(context: Context): List<MediaTrack> {
        if (!hasReadPermission(context)) {
            Log.i(TAG, "READ_MEDIA_AUDIO not granted; skipping MediaStore")
            return emptyList()
        }

        val resolver = context.contentResolver
        val tracks = mutableListOf<MediaTrack>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.MIME_TYPE,
        )
        // Emulator copies often land with IS_MUSIC=NULL; do not require that flag.
        val selection = "${MediaStore.Audio.Media.MIME_TYPE} LIKE ? OR " +
            "${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("audio/%", "%.mp3")
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        runCatching {
            resolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder,
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val displayCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol)
                        ?.takeIf { it.isNotBlank() }
                        ?: cursor.getString(displayCol)
                        ?: "Track $id"
                    val artist = cursor.getString(artistCol)
                        ?.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }
                        ?: "Unknown artist"
                    val albumId = cursor.getLong(albumIdCol)
                    val path = cursor.getString(dataCol)
                    val contentUri: Uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id,
                    )
                    val albumArtUri = albumId.takeIf { it > 0L }?.let {
                        ContentUris.withAppendedId(ALBUM_ART_URI, it)
                    }
                    val file = path?.let(::File)?.takeIf { it.exists() }
                    tracks += MediaTrack(
                        id = "local_$id",
                        title = title,
                        artist = artist,
                        file = file,
                        contentUri = contentUri,
                        albumArtUri = albumArtUri,
                    )
                }
            }
        }.onFailure { error ->
            Log.w(TAG, "MediaStore audio query failed", error)
        }

        Log.i(TAG, "Loaded ${tracks.size} MediaStore audio track(s)")
        return tracks
    }

    private const val TAG = "MediaStoreAudio"
    private val ALBUM_ART_URI: Uri = Uri.parse("content://media/external/audio/albumart")
}
