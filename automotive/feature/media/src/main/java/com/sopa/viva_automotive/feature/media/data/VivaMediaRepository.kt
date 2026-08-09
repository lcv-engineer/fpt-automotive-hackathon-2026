package com.sopa.viva_automotive.feature.media.data

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Looper
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.media3.cast.CastPlayer
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.google.android.gms.cast.framework.CastContext
import com.sopa.viva_automotive.feature.media.domain.MediaRepository
import com.sopa.viva_automotive.feature.media.domain.MediaSource
import com.sopa.viva_automotive.feature.media.domain.MediaTrack
import com.sopa.viva_automotive.feature.media.domain.PlaybackRoute
import com.sopa.viva_automotive.feature.media.domain.PlaybackUiState
import com.sopa.viva_automotive.feature.media.domain.TrackDisplayInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Singleton
class VivaMediaRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val metadataInspector: MetadataInspector,
) : MediaRepository {

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val libraryTracks: List<MediaTrack> by lazy {
        DemoToneFactory.ensureTracks(context.cacheDir)
    }
    private val radioTracks: List<MediaTrack> by lazy {
        RadioStationCatalog.ensureTracks(context.cacheDir)
    }

    private var exoPlayer: ExoPlayer? = null
    private var player: Player? = null
    private var mediaSession: MediaSession? = null
    private var castEnabled = false

    private var source: MediaSource = MediaSource.LIBRARY
    private var libraryIndex = 0
    private var radioIndex = 0
    private var mediaVolume: Float = 1f

    private val _state = MutableStateFlow(
        PlaybackUiState(
            stations = RadioStationCatalog.stations,
            mediaVolume = mediaVolume,
        ),
    )
    override val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private val currentQueue: List<MediaTrack>
        get() = when (source) {
            MediaSource.LIBRARY -> libraryTracks
            MediaSource.RADIO -> radioTracks
        }

    private var index: Int
        get() = when (source) {
            MediaSource.LIBRARY -> libraryIndex
            MediaSource.RADIO -> radioIndex
        }
        set(value) {
            when (source) {
                MediaSource.LIBRARY -> libraryIndex = value
                MediaSource.RADIO -> radioIndex = value
            }
        }

    fun sessionToken(): MediaSessionCompat.Token {
        val activePlayer = ensurePlayer()
        if (activePlayer.mediaItemCount == 0) {
            applyQueue(resetPosition = true, playWhenReady = false)
        }
        val session = mediaSession ?: error("MediaSession not ready")
        return MediaSessionCompat.Token.fromToken(session.platformToken)
    }

    override suspend fun setSource(source: MediaSource): Result<String> =
        withContext(Dispatchers.Main) {
            runCatching {
                if (this@VivaMediaRepository.source != source) {
                    pauseInternal()
                    this@VivaMediaRepository.source = source
                    applyQueue(resetPosition = true, playWhenReady = false)
                }
                when (source) {
                    MediaSource.LIBRARY -> "Library"
                    MediaSource.RADIO -> "Radio"
                }
            }
        }

    override suspend fun tuneRadio(query: String?): Result<String> =
        withContext(Dispatchers.Main) {
            runCatching {
                ensurePlayer()
                source = MediaSource.RADIO
                resolveRadioQuery(query)
                applyQueue(resetPosition = true, playWhenReady = true)
                "Tuned to ${currentQueue[index].title}"
            }
        }

    override suspend fun selectStation(stationId: String): Result<String> =
        withContext(Dispatchers.Main) {
            runCatching {
                ensurePlayer()
                source = MediaSource.RADIO
                val match = radioTracks.indexOfFirst { it.id == stationId }
                require(match >= 0) { "Unknown station: $stationId" }
                radioIndex = match
                applyQueue(resetPosition = true, playWhenReady = true)
                "Tuned to ${radioTracks[match].title}"
            }
        }

    override suspend fun play(query: String?): Result<String> = withContext(Dispatchers.Main) {
        runCatching {
            ensurePlayer()
            if (!query.isNullOrBlank() && looksLikeRadioQuery(query)) {
                source = MediaSource.RADIO
                resolveRadioQuery(query)
                applyQueue(resetPosition = true, playWhenReady = true)
            } else if (!query.isNullOrBlank()) {
                source = MediaSource.LIBRARY
                resolveLibraryQuery(query)
                applyQueue(resetPosition = true, playWhenReady = true)
            } else {
                val p = player ?: error("Player unavailable")
                if (p.mediaItemCount == 0) {
                    applyQueue(resetPosition = true, playWhenReady = true)
                } else {
                    p.playWhenReady = true
                    p.play()
                }
            }
            statusMessage()
        }
    }

    override suspend fun pause(): Result<String> = withContext(Dispatchers.Main) {
        runCatching {
            ensurePlayer()
            pauseInternal()
            "Paused"
        }
    }

    override suspend fun next(): Result<String> = withContext(Dispatchers.Main) {
        runCatching {
            ensurePlayer()
            val p = player ?: error("Player unavailable")
            if (p.hasNextMediaItem()) {
                p.seekToNextMediaItem()
            } else if (currentQueue.isNotEmpty()) {
                index = (index + 1).mod(currentQueue.size)
                applyQueue(resetPosition = true, playWhenReady = true)
            }
            p.playWhenReady = true
            "Đã chuyển bài."
        }
    }

    override suspend fun previous(): Result<String> = withContext(Dispatchers.Main) {
        runCatching {
            ensurePlayer()
            val p = player ?: error("Player unavailable")
            if (p.hasPreviousMediaItem()) {
                p.seekToPreviousMediaItem()
            } else if (currentQueue.isNotEmpty()) {
                index = (index - 1).mod(currentQueue.size)
                applyQueue(resetPosition = true, playWhenReady = true)
            }
            p.playWhenReady = true
            statusMessage()
        }
    }

    override fun adjustVolume(delta: Int): Result<String> = runCatching {
        val step = 0.1f
        val next = (mediaVolume + delta * step).coerceIn(0f, 1f)
        applyMediaVolume(next)
        runCatching {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                if (delta >= 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                AudioManager.FLAG_SHOW_UI,
            )
        }
        if (delta >= 0) "Đã tăng âm lượng." else "Đã giảm âm lượng."
    }

    override fun setMediaVolume(volume: Float): Result<String> = runCatching {
        applyMediaVolume(volume)
        "Đã đặt âm lượng ${(mediaVolume * 100).toInt()}%."
    }

    private fun applyMediaVolume(volume: Float) {
        mediaVolume = volume.coerceIn(0f, 1f)
        player?.volume = mediaVolume
        _state.update { it.copy(mediaVolume = mediaVolume) }
    }

    private fun statusMessage(): String {
        val track = currentQueue.getOrNull(index) ?: return "Ready"
        val routeLabel = if (_state.value.route == PlaybackRoute.CAST) " (Cast)" else ""
        return when (source) {
            MediaSource.RADIO -> "Tuned to ${track.title}$routeLabel"
            MediaSource.LIBRARY -> "Playing ${track.title}$routeLabel"
        }
    }

    private fun looksLikeRadioQuery(query: String): Boolean {
        val q = query.lowercase()
        return "radio" in q ||
            "đài" in q ||
            "dai" in q ||
            "fm" in q ||
            RadioStationCatalog.stations.any { station ->
                station.name.contains(query, ignoreCase = true) ||
                    station.id.contains(query, ignoreCase = true) ||
                    query.contains(station.frequencyMhz.toString())
            }
    }

    private fun resolveLibraryQuery(query: String) {
        val match = libraryTracks.indexOfFirst { track ->
            track.title.contains(query, ignoreCase = true) ||
                track.id.contains(query, ignoreCase = true) ||
                (query.contains("jazz", ignoreCase = true) && track.id == "highway")
        }
        if (match >= 0) libraryIndex = match
    }

    private fun resolveRadioQuery(query: String?) {
        if (query.isNullOrBlank()) return
        val normalized = query.lowercase().trim()
        val frequency = Regex("""(\d{2,3}(?:\.\d)?)""").find(normalized)?.groupValues?.get(1)
            ?.toFloatOrNull()
        val match = radioTracks.indexOfFirst { track ->
            track.title.contains(normalized, ignoreCase = true) ||
                track.id.contains(normalized, ignoreCase = true) ||
                (frequency != null && track.frequencyMhz != null &&
                    kotlin.math.abs(track.frequencyMhz - frequency) < 0.15f) ||
                track.artist.contains(normalized, ignoreCase = true)
        }
        if (match >= 0) radioIndex = match
    }

    private fun ensurePlayer(): Player {
        player?.let { return it }
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "Player must be created on the main thread"
        }

        val local = ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .build()
        exoPlayer = local

        val activePlayer: Player = createCastPlayerOrLocal(local)
        activePlayer.volume = mediaVolume
        player = activePlayer
        mediaSession = MediaSession.Builder(context, activePlayer).build()
        activePlayer.addListener(playerListener)
        publishState()
        return activePlayer
    }

    private fun createCastPlayerOrLocal(local: ExoPlayer): Player =
        runCatching {
            CastContext.getSharedInstance(context.applicationContext)
            val castPlayer = CastPlayer.Builder(context)
                .setLocalPlayer(local)
                .build()
            castEnabled = true
            Log.i(TAG, "CastPlayer ready")
            castPlayer
        }.getOrElse { error ->
            castEnabled = false
            Log.w(TAG, "Cast unavailable; using local ExoPlayer only", error)
            local
        }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            publishState()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaItem?.mediaId?.let { id ->
                val match = currentQueue.indexOfFirst { it.id == id }
                if (match >= 0) index = match
            }
            publishState()
            refreshDisplayMetadata()
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            publishState()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            publishState()
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_DEVICE_INFO_CHANGED) ||
                events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)
            ) {
                publishState()
            }
        }
    }

    private fun applyQueue(resetPosition: Boolean, playWhenReady: Boolean) {
        val p = ensurePlayer()
        val queue = currentQueue
        if (queue.isEmpty()) return
        val items = queue.map { it.toMediaItem() }
        val start = index.coerceIn(0, items.lastIndex)
        p.setMediaItems(items, start, /* startPositionMs= */ 0L)
        p.repeatMode = when (source) {
            MediaSource.RADIO -> Player.REPEAT_MODE_ONE
            MediaSource.LIBRARY -> Player.REPEAT_MODE_OFF
        }
        p.prepare()
        p.playWhenReady = playWhenReady
        if (playWhenReady) p.play()
        publishState()
        refreshDisplayMetadata()
    }

    private fun pauseInternal() {
        player?.pause()
        publishState()
    }

    private fun refreshDisplayMetadata() {
        val track = currentQueue.getOrNull(index) ?: run {
            _state.update { it.copy(display = TrackDisplayInfo()) }
            return
        }
        metadataInspector.cached(track.id)?.let { cached ->
            applyDisplay(cached)
            return
        }
        applyDisplay(TrackDisplayInfo(mediaId = track.id))
        scope.launch {
            val inspected = metadataInspector.inspect(track.id, track.file)
            if (_state.value.track?.id == track.id || currentQueue.getOrNull(index)?.id == track.id) {
                applyDisplay(inspected)
                enrichCurrentMediaItem(inspected)
            }
        }
    }

    private fun applyDisplay(display: TrackDisplayInfo) {
        _state.update { it.copy(display = display) }
    }

    private fun enrichCurrentMediaItem(display: TrackDisplayInfo) {
        val p = player ?: return
        val current = p.currentMediaItem ?: return
        if (current.mediaId != display.mediaId) return
        val builder = current.mediaMetadata.buildUpon()
        display.title?.let(builder::setTitle)
        display.artist?.let(builder::setArtist)
        display.durationMs?.let(builder::setDurationMs)
        display.artworkBytes?.let { bytes ->
            builder.setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }
        val enriched = current.buildUpon().setMediaMetadata(builder.build()).build()
        runCatching {
            p.replaceMediaItem(p.currentMediaItemIndex, enriched)
        }.onFailure { error ->
            Log.d(TAG, "Unable to enrich MediaItem metadata", error)
        }
    }

    private fun publishState() {
        val p = player
        val queue = currentQueue
        val mediaId = p?.currentMediaItem?.mediaId
        val resolvedIndex = mediaId?.let { id -> queue.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }
            ?: index
        if (resolvedIndex != index && resolvedIndex in queue.indices) {
            index = resolvedIndex
        }
        val track = queue.getOrNull(index)
        val casting = castEnabled &&
            p?.deviceInfo?.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE
        val display = track?.id?.let(metadataInspector::cached)
            ?: _state.value.display.takeIf { it.mediaId == track?.id }
            ?: TrackDisplayInfo(mediaId = track?.id)
        _state.update {
            PlaybackUiState(
                source = source,
                track = track,
                isPlaying = p?.isPlaying == true,
                queueIndex = index,
                queueSize = queue.size,
                stations = RadioStationCatalog.stations,
                route = if (casting) PlaybackRoute.CAST else PlaybackRoute.LOCAL,
                castAvailable = castEnabled,
                display = display,
                mediaVolume = mediaVolume,
            )
        }
    }

    private fun MediaTrack.toMediaItem(): MediaItem {
        val cached = metadataInspector.cached(id)
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(cached?.title)
            .setArtist(cached?.artist)
            .setGenre(if (frequencyMhz != null) "Radio" else "Music")
            .setMediaType(
                if (frequencyMhz != null) {
                    MediaMetadata.MEDIA_TYPE_RADIO_STATION
                } else {
                    MediaMetadata.MEDIA_TYPE_MUSIC
                },
            )
        cached?.durationMs?.let(metadataBuilder::setDurationMs)
        cached?.artworkBytes?.let { bytes ->
            metadataBuilder.setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(Uri.fromFile(file))
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    private companion object {
        const val TAG = "VivaMedia"
    }
}
