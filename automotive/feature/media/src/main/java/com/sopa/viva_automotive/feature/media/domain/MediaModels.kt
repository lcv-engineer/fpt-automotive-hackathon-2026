package com.sopa.viva_automotive.feature.media.domain

import android.net.Uri
import java.io.File

enum class MediaSource {
    LIBRARY,
    RADIO,
}

enum class PlaybackRoute {
    LOCAL,
    CAST,
}

/** Library queue repeat (radio stays forced to one-station loop). */
enum class RepeatMode {
    OFF,
    ONE,
    ALL,
}

enum class PlaybackSpeed(val multiplier: Float, val label: String) {
    X0_75(0.75f, "0.75×"),
    X1_00(1.00f, "1×"),
    X1_25(1.25f, "1.25×"),
    X1_50(1.50f, "1.5×"),
    X2_00(2.00f, "2×"),
    ;

    fun next(): PlaybackSpeed {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }
}

/**
 * Preferred decode ceiling. Local single-file tracks often ignore bitrate caps;
 * still useful when Media3 can choose among adaptive/alternate tracks.
 */
enum class AudioQuality(val maxBitrate: Int) {
    NORMAL(maxBitrate = 192_000),
    HI_RES(maxBitrate = Int.MAX_VALUE),
    ;

    fun next(): AudioQuality = when (this) {
        NORMAL -> HI_RES
        HI_RES -> NORMAL
    }
}

data class MediaTrack(
    val id: String,
    val title: String,
    val artist: String,
    val file: File? = null,
    val contentUri: Uri? = null,
    /** MediaStore album-art URI when available (readable by Car Media). */
    val albumArtUri: Uri? = null,
    val frequencyMhz: Float? = null,
) {
    init {
        require(file != null || contentUri != null) {
            "MediaTrack needs a file or contentUri for playback"
        }
    }

    fun playbackUri(): Uri = contentUri ?: Uri.fromFile(requireNotNull(file))
}

data class RadioStation(
    val id: String,
    val name: String,
    val frequencyMhz: Float,
    val region: String,
) {
    fun toTrack(file: File): MediaTrack = MediaTrack(
        id = id,
        title = name,
        artist = String.format("%.1f FM · %s", frequencyMhz, region),
        file = file,
        frequencyMhz = frequencyMhz,
    )
}

data class TrackDisplayInfo(
    val mediaId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val durationMs: Long? = null,
    val artworkBytes: ByteArray? = null,
    /** content:// URI for system UIs (Car Media / home / cluster). */
    val artworkUri: Uri? = null,
) {
    val hasArtwork: Boolean
        get() = artworkUri != null || (artworkBytes != null && artworkBytes.isNotEmpty())

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TrackDisplayInfo) return false
        return mediaId == other.mediaId &&
            title == other.title &&
            artist == other.artist &&
            durationMs == other.durationMs &&
            artworkUri == other.artworkUri &&
            artworkBytes.contentEquals(other.artworkBytes)
    }

    override fun hashCode(): Int {
        var result = mediaId?.hashCode() ?: 0
        result = 31 * result + (title?.hashCode() ?: 0)
        result = 31 * result + (artist?.hashCode() ?: 0)
        result = 31 * result + (durationMs?.hashCode() ?: 0)
        result = 31 * result + (artworkUri?.hashCode() ?: 0)
        result = 31 * result + (artworkBytes?.contentHashCode() ?: 0)
        return result
    }
}

data class PlaybackUiState(
    val source: MediaSource = MediaSource.LIBRARY,
    val track: MediaTrack? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queueIndex: Int = 0,
    val queueSize: Int = 0,
    val libraryTracks: List<MediaTrack> = emptyList(),
    val stations: List<RadioStation> = emptyList(),
    val route: PlaybackRoute = PlaybackRoute.LOCAL,
    val castAvailable: Boolean = false,
    val display: TrackDisplayInfo = TrackDisplayInfo(),
    val mediaVolume: Float = 1f,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val playbackSpeed: PlaybackSpeed = PlaybackSpeed.X1_00,
    val audioQuality: AudioQuality = AudioQuality.HI_RES,
    /** Track ids only — MP3 stays on device MediaStore. */
    val favoriteTrackIds: Set<String> = emptySet(),
    val favoritesFilterEnabled: Boolean = false,
) {
    val progress: Float
        get() = if (durationMs > 0L) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    val isCurrentFavorite: Boolean
        get() = track?.id?.let(favoriteTrackIds::contains) == true

    val visibleLibraryTracks: List<MediaTrack>
        get() = if (favoritesFilterEnabled) {
            libraryTracks.filter { it.id in favoriteTrackIds }
        } else {
            libraryTracks
        }
}
