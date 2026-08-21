package com.sopa.viva_automotive.feature.media.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.Uri
import android.os.Looper
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import com.sopa.viva_automotive.feature.media.service.VivaMediaBrowserService
import androidx.media3.cast.CastPlayer
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.MediaSession
import androidx.media3.datasource.DataSourceBitmapLoader
import com.google.android.gms.cast.framework.CastContext
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import androidx.media3.common.PlaybackParameters
import com.sopa.viva_automotive.feature.media.domain.AudioQuality
import com.sopa.viva_automotive.feature.media.domain.MediaRepository
import com.sopa.viva_automotive.feature.media.domain.MediaSource
import com.sopa.viva_automotive.feature.media.domain.MediaTrack
import com.sopa.viva_automotive.feature.media.domain.MediaVolume
import com.sopa.viva_automotive.feature.media.domain.PlaybackRoute
import com.sopa.viva_automotive.feature.media.domain.PlaybackSpeed
import com.sopa.viva_automotive.feature.media.domain.PlaybackUiState
import com.sopa.viva_automotive.feature.media.domain.RepeatMode
import com.sopa.viva_automotive.feature.media.domain.TrackDisplayInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Singleton
class VivaMediaRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val metadataInspector: MetadataInspector,
    private val favoritesStore: MediaFavoritesStore,
) : MediaRepository {

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var libraryTracks: List<MediaTrack> = emptyList()
    private val radioTracks: List<MediaTrack> by lazy {
        RadioStationCatalog.ensureTracks(context.cacheDir)
    }
    private val libraryLock = Any()

    private var exoPlayer: ExoPlayer? = null
    private var player: Player? = null
    private var mediaSession: MediaSession? = null
    private var castEnabled = false

    private var source: MediaSource = MediaSource.LIBRARY
    private var libraryIndex = 0
    private var radioIndex = 0
    /** Normalized 0..1 mirror of system [AudioManager.STREAM_MUSIC]. */
    private var mediaVolume: Float = 1f
    /** True when system owns loudness (player gain stays at 1). False = player-gain fallback. */
    private var systemVolumeWritable: Boolean = true
    private var applyingSystemVolume: Boolean = false
    /** In-process duck while Viva is listening / speaking (see [setVoiceDucked]). */
    private var voiceDucked: Boolean = false
    private var positionTickerJob: Job? = null

    private var repeatMode: RepeatMode = RepeatMode.OFF
    private var playbackSpeed: PlaybackSpeed = PlaybackSpeed.X1_00
    private var audioQuality: AudioQuality = AudioQuality.HI_RES
    private var favoriteTrackIds: Set<String> = emptySet()
    private var favoritesFilterEnabled: Boolean = false

    private val _state = MutableStateFlow(
        PlaybackUiState(
            stations = RadioStationCatalog.stations,
            mediaVolume = mediaVolume,
        ),
    )
    override val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != VOLUME_CHANGED_ACTION) return
            val stream = intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, Int.MIN_VALUE)
            if (stream == AudioManager.STREAM_MUSIC) {
                scope.launch { syncUiFromSystemVolume() }
            }
        }
    }

    init {
        mediaVolume = readSystemMediaVolumeFraction()
        _state.update { it.copy(mediaVolume = mediaVolume) }
        runCatching {
            context.registerReceiver(
                volumeReceiver,
                IntentFilter(VOLUME_CHANGED_ACTION),
                Context.RECEIVER_NOT_EXPORTED,
            )
        }.onFailure { error ->
            Log.w(TAG, "Unable to register volume receiver", error)
        }
        scope.launch(Dispatchers.IO) {
            reloadLibraryTracks()
            withContext(Dispatchers.Main) { publishState() }
        }
        scope.launch {
            favoritesStore.favoriteIds.collect { ids ->
                favoriteTrackIds = ids
                publishState()
            }
        }
    }

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

    fun displayInfo(mediaId: String): TrackDisplayInfo? = metadataInspector.cached(mediaId)

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

    override suspend fun selectTrack(trackId: String): Result<String> =
        withContext(Dispatchers.Main) {
            runCatching {
                ensureLibraryLoaded()
                ensurePlayer()
                source = MediaSource.LIBRARY
                val match = libraryTracks.indexOfFirst { it.id == trackId }
                require(match >= 0) { "Unknown track: $trackId" }
                libraryIndex = match
                applyQueue(resetPosition = true, playWhenReady = true)
                "Playing ${libraryTracks[match].title}"
            }
        }

    override suspend fun refreshLibrary(): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val count = reloadLibraryTracks()
                withContext(Dispatchers.Main) {
                    if (source == MediaSource.LIBRARY && player != null) {
                        libraryIndex = libraryIndex.coerceIn(0, (libraryTracks.size - 1).coerceAtLeast(0))
                        applyQueue(resetPosition = false, playWhenReady = player?.isPlaying == true)
                    } else {
                        publishState()
                    }
                }
                if (count == 0) {
                    "No device tracks; using demo tones"
                } else {
                    "Loaded $count track(s) from device"
                }
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
        applyMediaVolume(MediaVolume.stepped(mediaVolume, delta), showSystemUi = true)
        if (delta >= 0) "Đã tăng âm lượng." else "Đã giảm âm lượng."
    }

    override fun setMediaVolume(volume: Float): Result<String> = runCatching {
        applyMediaVolume(volume, showSystemUi = false)
        "Đã đặt âm lượng ${(mediaVolume * 100).toInt()}%."
    }

    override fun cycleRepeatMode(): Result<String> = runCatching {
        check(source == MediaSource.LIBRARY) { "Radio luôn lặp một kênh." }
        repeatMode = when (repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        applyRepeatModeToPlayer()
        publishState()
        when (repeatMode) {
            RepeatMode.OFF -> "Tắt lặp."
            RepeatMode.ONE -> "Lặp một bài."
            RepeatMode.ALL -> "Lặp danh sách."
        }
    }

    override fun cyclePlaybackSpeed(): Result<String> = runCatching {
        playbackSpeed = playbackSpeed.next()
        applyPlaybackSpeedToPlayer()
        publishState()
        "Tốc độ ${playbackSpeed.label}."
    }

    override fun cycleAudioQuality(): Result<String> = runCatching {
        audioQuality = audioQuality.next()
        applyAudioQualityToPlayer()
        publishState()
        when (audioQuality) {
            AudioQuality.HI_RES -> "Chất lượng Hi-Res."
            AudioQuality.NORMAL -> "Chất lượng Normal."
        }
    }

    override suspend fun toggleFavoriteCurrent(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(source == MediaSource.LIBRARY) {
                "Chỉ thêm bài thư viện vào playlist."
            }
            val id = withContext(Dispatchers.Main) {
                currentQueue.getOrNull(index)?.id
                    ?: error("Không có bài đang phát để thêm playlist.")
            }
            val next = favoritesStore.toggle(id)
            if (id in next) "Đã thêm vào playlist." else "Đã bỏ khỏi playlist."
        }
    }

    override fun setFavoritesFilter(enabled: Boolean): Result<String> = runCatching {
        favoritesFilterEnabled = enabled
        publishState()
        if (enabled) "Lọc bài yêu thích." else "Hiện tất cả bài."
    }

    override fun seekTo(positionMs: Long): Result<String> = runCatching {
        val p = player ?: error("Chưa sẵn sàng phát.")
        val duration = p.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
        val target = positionMs.coerceIn(0L, duration)
        p.seekTo(target)
        publishState()
        "Đã tua."
    }

    override fun seekBy(deltaMs: Long): Result<String> = runCatching {
        val p = player ?: error("Chưa sẵn sàng phát.")
        val duration = p.duration.takeIf { it > 0L }
        val current = p.currentPosition.coerceAtLeast(0L)
        val target = if (duration != null) {
            (current + deltaMs).coerceIn(0L, duration)
        } else {
            (current + deltaMs).coerceAtLeast(0L)
        }
        p.seekTo(target)
        publishState()
        if (deltaMs >= 0L) "Tua nhanh." else "Tua ngược."
    }

    override fun setVoiceDucked(ducked: Boolean) {
        val apply = {
            if (voiceDucked != ducked) {
                voiceDucked = ducked
                Log.i(TAG, "voice duck=${if (ducked) "on" else "off"} gain=$VOICE_DUCK_GAIN")
            }
            applyPlayerGain()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            apply()
        } else {
            scope.launch { apply() }
        }
    }

    /**
     * Bidirectional sync with system media stream:
     * - App fader → [AudioManager.setStreamVolume] ([STREAM_MUSIC][AudioManager.STREAM_MUSIC])
     * - System volume UI → [VOLUME_CHANGED_ACTION] updates the fader
     * Player gain stays at 1 when system volume is writable (avoids double attenuation).
     */
    private fun applyMediaVolume(volume: Float, showSystemUi: Boolean = false) {
        val coerced = MediaVolume.clamped(volume)
        mediaVolume = coerced
        val wroteSystem = writeSystemMediaVolumeFraction(
            fraction = coerced,
            flags = if (showSystemUi) AudioManager.FLAG_SHOW_UI else 0,
        )
        systemVolumeWritable = wroteSystem
        applyPlayerGain()
        _state.update { it.copy(mediaVolume = mediaVolume) }
    }

    private fun syncUiFromSystemVolume() {
        if (applyingSystemVolume) return
        val fraction = readSystemMediaVolumeFraction()
        if (abs(fraction - mediaVolume) < 0.005f) return
        mediaVolume = fraction
        applyPlayerGain()
        _state.update { it.copy(mediaVolume = mediaVolume) }
    }

    private fun applyPlayerGain() {
        val base = if (systemVolumeWritable) 1f else mediaVolume
        val gain = if (voiceDucked) base * VOICE_DUCK_GAIN else base
        player?.volume = gain
    }

    private fun readSystemMediaVolumeFraction(): Float {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val min = runCatching { audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC) }
            .getOrDefault(0)
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            .coerceIn(min, max)
        val span = (max - min).coerceAtLeast(1)
        return ((current - min).toFloat() / span.toFloat()).coerceIn(0f, 1f)
    }

    private fun writeSystemMediaVolumeFraction(fraction: Float, flags: Int): Boolean {
        if (audioManager.isVolumeFixed) {
            Log.d(TAG, "System media volume is fixed; using player gain fallback")
            return false
        }
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return false
        val min = runCatching { audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC) }
            .getOrDefault(0)
        val span = (max - min).coerceAtLeast(1)
        val index = (min + (fraction.coerceIn(0f, 1f) * span).roundToInt()).coerceIn(min, max)
        return runCatching {
            applyingSystemVolume = true
            if (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) != index) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, index, flags)
            }
            true
        }.getOrElse { error ->
            Log.w(TAG, "setStreamVolume(STREAM_MUSIC) failed", error)
            false
        }.also {
            applyingSystemVolume = false
        }
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
        ensureLibraryLoaded()
        val match = libraryTracks.indexOfFirst { track ->
            track.title.contains(query, ignoreCase = true) ||
                track.artist.contains(query, ignoreCase = true) ||
                track.id.contains(query, ignoreCase = true) ||
                (query.contains("jazz", ignoreCase = true) && track.id == "highway")
        }
        if (match >= 0) libraryIndex = match
    }

    private fun ensureLibraryLoaded() {
        if (libraryTracks.isEmpty()) {
            reloadLibraryTracks()
        }
    }

    private fun reloadLibraryTracks(): Int = synchronized(libraryLock) {
        val local = MediaStoreAudioLibrary.loadTracks(context)
        libraryTracks = local.ifEmpty {
            DemoToneFactory.ensureTracks(context.cacheDir)
        }
        libraryIndex = libraryIndex.coerceIn(0, (libraryTracks.size - 1).coerceAtLeast(0))
        prefetchLibraryMetadata(libraryTracks.toList())
        local.size
    }

    /** Extract ID3 + persist artwork files so Car Media can load iconUri. */
    private fun prefetchLibraryMetadata(tracks: List<MediaTrack>) {
        scope.launch(Dispatchers.IO) {
            tracks.forEach { track ->
                runCatching { metadataInspector.inspect(track) }
                    .onFailure { Log.d(TAG, "Prefetch metadata failed for ${track.id}", it) }
            }
            withContext(Dispatchers.Main) {
                if (source == MediaSource.LIBRARY && player != null) {
                    applyQueue(
                        resetPosition = false,
                        playWhenReady = player?.isPlaying == true,
                    )
                } else {
                    publishState()
                }
            }
        }
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
        ensureLibraryLoaded()

        // handleAudioFocus=false: AAOS CarAudioFocus maps assistant TTS
        // (GAIN_TRANSIENT_MAY_DUCK + USAGE_ASSISTANT) to LOSS_TRANSIENT on
        // media, and Media3 then PAUSE — often without auto-resume. Voice
        // ducking is owned by setVoiceDucked() (player gain) instead.
        val local = ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ false,
            )
            .build()
        exoPlayer = local

        val activePlayer: Player = createCastPlayerOrLocal(local)
        player = activePlayer
        applyPlayerGain()
        mediaSession = MediaSession.Builder(context, activePlayer)
            .setSessionActivity(sessionActivityPendingIntent())
            .setCallback(sessionCallback)
            // Lets Media3 resolve artworkUri into bitmaps for platform session / system UI.
            .setBitmapLoader(CacheBitmapLoader(DataSourceBitmapLoader(context)))
            .build()
        // Publish session token via MediaBrowserService so AAOS Car Media can bind.
        runCatching {
            context.startService(Intent(context, VivaMediaBrowserService::class.java))
        }.onFailure { error ->
            Log.w(TAG, "Unable to start VivaMediaBrowserService", error)
        }
        activePlayer.addListener(playerListener)
        publishState()
        return activePlayer
    }

    /**
     * Car Media / MediaController play-from-browse lands here with mediaId-only items.
     * Resolve against library/radio and return a full queue so system UI and in-app stay aligned.
     */
    private val sessionCallback = object : MediaSession.Callback {
        override fun onSetMediaItems(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val mediaId = mediaItems.getOrNull(startIndex.coerceAtLeast(0))?.mediaId
                ?: mediaItems.firstOrNull()?.mediaId
            if (mediaId.isNullOrBlank()) {
                return Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(
                        mediaItems,
                        startIndex,
                        startPositionMs,
                    ),
                )
            }
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            scope.launch {
                runCatching {
                    withContext(Dispatchers.Main) {
                        resolveSessionMediaItems(mediaId, startPositionMs)
                    }
                }.onSuccess(future::set)
                    .onFailure(future::setException)
            }
            return future
        }
    }

    private fun resolveSessionMediaItems(
        mediaId: String,
        startPositionMs: Long,
    ): MediaSession.MediaItemsWithStartPosition {
        ensureLibraryLoaded()
        val libraryMatch = libraryTracks.indexOfFirst { it.id == mediaId }
        if (libraryMatch >= 0) {
            source = MediaSource.LIBRARY
            libraryIndex = libraryMatch
            val items = libraryTracks.map { it.toMediaItem() }
            publishState()
            return MediaSession.MediaItemsWithStartPosition(
                items,
                libraryMatch,
                startPositionMs.coerceAtLeast(0L),
            )
        }
        val radioMatch = radioTracks.indexOfFirst { it.id == mediaId }
        if (radioMatch >= 0) {
            source = MediaSource.RADIO
            radioIndex = radioMatch
            val items = radioTracks.map { it.toMediaItem() }
            publishState()
            return MediaSession.MediaItemsWithStartPosition(
                items,
                radioMatch,
                startPositionMs.coerceAtLeast(0L),
            )
        }
        Log.w(TAG, "Session play requested unknown mediaId=$mediaId")
        return MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L)
    }

    private fun sessionActivityPendingIntent(): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(context.packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        return PendingIntent.getActivity(
            context,
            /* requestCode = */ 0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
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
            syncPositionTicker(isPlaying)
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
        applyRepeatModeToPlayer()
        applyPlaybackSpeedToPlayer()
        applyAudioQualityToPlayer()
        p.prepare()
        p.playWhenReady = playWhenReady
        if (playWhenReady) p.play()
        publishState()
        refreshDisplayMetadata()
    }

    private fun applyRepeatModeToPlayer() {
        val p = player ?: return
        p.repeatMode = when (source) {
            MediaSource.RADIO -> Player.REPEAT_MODE_ONE
            MediaSource.LIBRARY -> when (repeatMode) {
                RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            }
        }
    }

    private fun applyPlaybackSpeedToPlayer() {
        val p = player ?: return
        p.playbackParameters = PlaybackParameters(playbackSpeed.multiplier)
    }

    private fun applyAudioQualityToPlayer() {
        val p = player ?: return
        val params = p.trackSelectionParameters.buildUpon()
            .setMaxAudioBitrate(audioQuality.maxBitrate)
            .build()
        p.trackSelectionParameters = params
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
        applyDisplay(
            TrackDisplayInfo(
                mediaId = track.id,
                title = track.title,
                artist = track.artist,
            ),
        )
        scope.launch {
            val inspected = metadataInspector.inspect(track)
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
        display.artworkUri?.let(builder::setArtworkUri)
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

    private fun syncPositionTicker(isPlaying: Boolean) {
        if (isPlaying) {
            if (positionTickerJob?.isActive == true) return
            positionTickerJob = scope.launch {
                while (isActive) {
                    publishState()
                    delay(POSITION_TICK_MS)
                }
            }
        } else {
            positionTickerJob?.cancel()
            positionTickerJob = null
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
        val playerDuration = p?.duration?.takeIf { it > 0L }
        val durationMs = playerDuration
            ?: display.durationMs?.takeIf { it > 0L }
            ?: 0L
        val positionMs = (p?.currentPosition ?: 0L).coerceAtLeast(0L)
            .let { if (durationMs > 0L) it.coerceAtMost(durationMs) else it }
        val playing = p?.isPlaying == true
        if (playing) syncPositionTicker(true)
        _state.update {
            PlaybackUiState(
                source = source,
                track = track,
                isPlaying = playing,
                positionMs = positionMs,
                durationMs = durationMs,
                queueIndex = index,
                queueSize = queue.size,
                libraryTracks = libraryTracks,
                stations = RadioStationCatalog.stations,
                route = if (casting) PlaybackRoute.CAST else PlaybackRoute.LOCAL,
                castAvailable = castEnabled,
                display = display,
                mediaVolume = mediaVolume,
                repeatMode = if (source == MediaSource.RADIO) RepeatMode.ONE else repeatMode,
                playbackSpeed = playbackSpeed,
                audioQuality = audioQuality,
                favoriteTrackIds = favoriteTrackIds,
                favoritesFilterEnabled = favoritesFilterEnabled,
            )
        }
    }

    private fun MediaTrack.toMediaItem(): MediaItem {
        val cached = metadataInspector.cached(id)
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(cached?.title ?: title)
            .setArtist(cached?.artist ?: artist)
            .setGenre(if (frequencyMhz != null) "Radio" else "Music")
            .setMediaType(
                if (frequencyMhz != null) {
                    MediaMetadata.MEDIA_TYPE_RADIO_STATION
                } else {
                    MediaMetadata.MEDIA_TYPE_MUSIC
                },
            )
        cached?.durationMs?.let(metadataBuilder::setDurationMs)
        val artUri = cached?.artworkUri ?: albumArtUri ?: metadataInspector.artworkUri(this)
        artUri?.let(metadataBuilder::setArtworkUri)
        cached?.artworkBytes?.let { bytes ->
            metadataBuilder.setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(playbackUri())
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    private companion object {
        const val TAG = "VivaMedia"
        const val POSITION_TICK_MS = 200L
        /** ~Windows “reduce other sounds by 50%”. */
        const val VOICE_DUCK_GAIN = 0.5f
        /** Hidden platform broadcast; same action SystemUI volume dialog uses. */
        const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
    }
}
