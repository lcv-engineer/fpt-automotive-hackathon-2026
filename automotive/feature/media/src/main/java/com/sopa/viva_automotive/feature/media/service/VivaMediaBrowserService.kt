package com.sopa.viva_automotive.feature.media.service

import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.util.Log
import androidx.media.MediaBrowserServiceCompat
import androidx.media.utils.MediaConstants
import com.sopa.viva_automotive.feature.media.R
import com.sopa.viva_automotive.feature.media.data.DemoToneFactory
import com.sopa.viva_automotive.feature.media.data.VivaMediaRepository
import com.sopa.viva_automotive.feature.media.domain.MediaTrack
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AAOS Car Media browse host.
 *
 * Root children must be [MediaBrowserCompat.MediaItem.FLAG_BROWSABLE] tabs
 * (see https://developer.android.com/training/cars/media/create-media-browser/content-hierarchy).
 * Playable tracks live under those tabs — returning playables at root makes Car Media
 * show "Media isn't available for this list".
 */
@AndroidEntryPoint
class VivaMediaBrowserService : MediaBrowserServiceCompat() {

    @Inject lateinit var mediaRepository: VivaMediaRepository

    /** Scope theo vòng đời service; phần I/O được chuyển riêng sang [Dispatchers.IO]. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var libraryWatchJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        attachSessionToken()
        libraryWatchJob = scope.launch {
            mediaRepository.state
                .map { it.libraryTracks.map(MediaTrack::id) to it.stations.map { s -> s.id } }
                .distinctUntilChanged()
                .collect {
                    notifyChildrenChanged(ROOT_ID)
                    notifyChildrenChanged(LIBRARY_ID)
                    notifyChildrenChanged(RADIO_ID)
                }
        }
    }

    override fun onDestroy() {
        libraryWatchJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        attachSessionToken()
        return START_STICKY
    }

    private fun attachSessionToken() {
        if (sessionToken != null) return
        runCatching {
            sessionToken = mediaRepository.sessionToken()
            Log.i(TAG, "MediaSession token attached")
        }.onFailure { error ->
            Log.e(TAG, "Unable to attach MediaSession token", error)
        }
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?,
    ): BrowserRoot {
        val extras = Bundle().apply {
            putInt(
                MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
            )
            putInt(
                MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
            )
        }
        return BrowserRoot(ROOT_ID, extras)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>,
    ) {
        result.detach()
        scope.launch {
            val items = withContext(Dispatchers.IO) { loadChildren(parentId) }
            result.sendResult(items)
        }
    }

    private fun loadChildren(parentId: String): MutableList<MediaBrowserCompat.MediaItem> =
        when (parentId) {
            ROOT_ID -> mutableListOf(
                browsable(
                    id = LIBRARY_ID,
                    title = getString(R.string.media_playlist),
                ),
                browsable(
                    id = RADIO_ID,
                    title = getString(R.string.media_radio_presets),
                ),
            )
            LIBRARY_ID -> libraryTracks().map { it.toPlayableItem() }.toMutableList()
            RADIO_ID -> mediaRepository.state.value.stations.map { station ->
                playable(
                    id = station.id,
                    title = station.name,
                    subtitle = String.format("%.1f FM · %s", station.frequencyMhz, station.region),
                    mediaUri = null,
                )
            }.toMutableList()
            else -> mutableListOf()
        }

    private fun libraryTracks(): List<MediaTrack> {
        val loaded = mediaRepository.state.value.libraryTracks
        if (loaded.isNotEmpty()) return loaded
        // Cold start: MediaStore may still be loading; fall back to demo tones for browse.
        return DemoToneFactory.ensureTracks(cacheDir)
    }

    private fun MediaTrack.toPlayableItem(): MediaBrowserCompat.MediaItem {
        val cached = mediaRepository.displayInfo(id)
        return playable(
            id = id,
            title = cached?.title ?: title,
            subtitle = cached?.artist ?: artist,
            mediaUri = playbackUri(),
            iconUri = cached?.artworkUri ?: albumArtUri,
        )
    }

    private fun browsable(id: String, title: String): MediaBrowserCompat.MediaItem {
        val description = MediaDescriptionCompat.Builder()
            .setMediaId(id)
            .setTitle(title)
            .build()
        return MediaBrowserCompat.MediaItem(
            description,
            MediaBrowserCompat.MediaItem.FLAG_BROWSABLE,
        )
    }

    private fun playable(
        id: String,
        title: String,
        subtitle: String,
        mediaUri: Uri?,
        iconUri: Uri? = null,
    ): MediaBrowserCompat.MediaItem {
        val description = MediaDescriptionCompat.Builder()
            .setMediaId(id)
            .setTitle(title)
            .setSubtitle(subtitle)
            .apply {
                mediaUri?.let(::setMediaUri)
                iconUri?.let(::setIconUri)
            }
            .build()
        return MediaBrowserCompat.MediaItem(
            description,
            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE,
        )
    }

    private companion object {
        const val TAG = "VivaMediaBrowser"
        const val ROOT_ID = "viva_media_root"
        const val LIBRARY_ID = "viva_media_library"
        const val RADIO_ID = "viva_media_radio"
    }
}
