package com.sopa.viva_automotive.feature.media.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.inspector.MetadataRetriever
import com.sopa.viva_automotive.feature.media.domain.MediaTrack
import com.sopa.viva_automotive.feature.media.domain.TrackDisplayInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class MetadataInspector @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val artworkStore: ArtworkStore,
) {
    private val cache = ConcurrentHashMap<String, TrackDisplayInfo>()

    suspend fun inspect(track: MediaTrack): TrackDisplayInfo =
        withContext(Dispatchers.IO) {
            cache[track.id] ?: extract(track).also { cache[track.id] = it }
        }

    fun cached(mediaId: String): TrackDisplayInfo? = cache[mediaId]

    fun artworkUri(track: MediaTrack): Uri? =
        cache[track.id]?.artworkUri
            ?: artworkStore.existingUri(track.id)
            ?: track.albumArtUri

    private fun extract(track: MediaTrack): TrackDisplayInfo {
        val uri = track.playbackUri()
        val mediaItem = MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(uri)
            .build()

        val durationMs = retrieveDurationMs(mediaItem)
            ?: retrieveDurationMsPlatform(uri)
        val tags = retrieveId3Tags(uri)
        val skipEmbeddedArt = track.file?.extension.equals("wav", ignoreCase = true) == true
        val artworkBytes = if (skipEmbeddedArt) null else tags.artworkBytes
        val artworkUri = when {
            artworkBytes != null -> artworkStore.save(track.id, artworkBytes)
            else -> artworkStore.existingUri(track.id) ?: track.albumArtUri
        }

        return TrackDisplayInfo(
            mediaId = track.id,
            title = tags.title ?: track.title,
            artist = tags.artist ?: track.artist,
            durationMs = durationMs,
            artworkBytes = artworkBytes,
            artworkUri = artworkUri,
        )
    }

    private fun retrieveDurationMs(mediaItem: MediaItem): Long? =
        runCatching {
            MetadataRetriever.Builder(context, mediaItem).build().use { retriever ->
                val durationUs = retriever.retrieveDurationUs()
                    .get(TIMEOUT_SEC, TimeUnit.SECONDS)
                if (durationUs == null || durationUs == C.TIME_UNSET || durationUs < 0L) {
                    null
                } else {
                    durationUs / 1_000L
                }
            }
        }.onFailure { error ->
            Log.w(TAG, "MetadataRetriever duration failed", error)
        }.getOrNull()

    private fun retrieveDurationMsPlatform(uri: Uri): Long? =
        runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?.takeIf { it > 0L }
            }
        }.getOrNull()

    private fun retrieveId3Tags(uri: Uri): Id3Tags {
        return runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, uri)
                val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    .takeIfMeaningful()
                val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    .takeIfMeaningful()
                    ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                        .takeIfMeaningful()
                val artwork = runCatching { retriever.embeddedPicture }.getOrNull()
                Id3Tags(title = title, artist = artist, artworkBytes = artwork)
            }
        }.onFailure { error ->
            Log.w(TAG, "ID3/tag retrieve failed for $uri", error)
        }.getOrDefault(Id3Tags())
    }

    private fun String?.takeIfMeaningful(): String? =
        this?.trim()?.takeIf { it.isNotEmpty() }

    private data class Id3Tags(
        val title: String? = null,
        val artist: String? = null,
        val artworkBytes: ByteArray? = null,
    )

    private companion object {
        const val TAG = "MetadataInspector"
        const val TIMEOUT_SEC = 8L
    }
}
