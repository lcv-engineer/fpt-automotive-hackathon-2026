package com.sopa.viva_automotive.feature.media

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sopa.viva_automotive.core.ui.components.SectionCard
import com.sopa.viva_automotive.core.ui.theme.VivaDimens
import com.sopa.viva_automotive.core.ui.theme.VivaTheme
import com.sopa.viva_automotive.feature.media.domain.AudioQuality
import com.sopa.viva_automotive.feature.media.domain.MediaSource
import com.sopa.viva_automotive.feature.media.domain.MediaTrack
import com.sopa.viva_automotive.feature.media.domain.PlaybackUiState
import com.sopa.viva_automotive.feature.media.domain.RadioStation
import com.sopa.viva_automotive.feature.media.domain.RepeatMode
import com.sopa.viva_automotive.feature.media.ui.AlbumArtwork
import com.sopa.viva_automotive.feature.media.ui.formatDurationMs

/**
 * Cabin media layout:
 * Now playing : Tracks/Presets : Controls = 5 : 4 : 1
 */
@Composable
fun MediaScreen(
    source: MediaSource,
    modifier: Modifier = Modifier,
    viewModel: MediaViewModel = hiltViewModel(),
) {
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val isRadio = source == MediaSource.RADIO

    LaunchedEffect(source) {
        viewModel.selectSource(source)
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(
                    if (isRadio) R.string.media_source_radio else R.string.media_title,
                ),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SectionCard(
                    title = stringResource(R.string.media_now_playing),
                    modifier = Modifier
                        .weight(5f)
                        .fillMaxHeight(),
                ) {
                    NowPlayingPane(
                        playback = playback,
                        isRadio = isRadio,
                        onPrevious = viewModel::previous,
                        onTogglePlayPause = viewModel::togglePlayPause,
                        onNext = viewModel::next,
                        onRewind = viewModel::rewind,
                        onFastForward = viewModel::fastForward,
                        onSeek = viewModel::seekTo,
                    )
                }

                SectionCard(
                    title = if (isRadio) {
                        stringResource(R.string.media_radio_presets)
                    } else {
                        stringResource(R.string.media_playlist)
                    },
                    modifier = Modifier
                        .weight(4f)
                        .fillMaxHeight(),
                    titleTrailing = if (!isRadio) {
                        {
                            TextButton(onClick = viewModel::refreshLibrary) {
                                Text(stringResource(R.string.media_refresh_library))
                            }
                        }
                    } else {
                        null
                    },
                ) {
                    TracksPane(
                        playback = playback,
                        isRadio = isRadio,
                        onSelectTrack = viewModel::selectTrack,
                        onSelectStation = viewModel::selectStation,
                        onFavoritesFilterChange = viewModel::setFavoritesFilter,
                    )
                }

                SectionCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                ) {
                    PlaybackControlsColumn(
                        playback = playback,
                        isRadio = isRadio,
                        onCycleRepeat = viewModel::cycleRepeatMode,
                        onCycleSpeed = viewModel::cyclePlaybackSpeed,
                        onCycleQuality = viewModel::cycleAudioQuality,
                        onToggleFavorite = viewModel::toggleFavoriteCurrent,
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }
}

@Composable
private fun NowPlayingPane(
    playback: PlaybackUiState,
    isRadio: Boolean,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onRewind: () -> Unit,
    onFastForward: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    val track = playback.track
    val display = playback.display
    val notAvailable = stringResource(R.string.media_not_available)
    val titleText = when {
        track == null && isRadio -> stringResource(R.string.media_radio_idle)
        track == null -> stringResource(R.string.media_idle)
        else -> display.title ?: notAvailable
    }
    val artistText = if (track == null) "" else display.artist ?: notAvailable
    val positionText = formatDurationMs(playback.positionMs) ?: "0:00"
    val durationText = formatDurationMs(playback.durationMs.takeIf { it > 0L } ?: display.durationMs)
        ?: notAvailable

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AlbumArtwork(
                artworkBytes = display.artworkBytes,
                size = 160.dp,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee(),
                )
                if (artistText.isNotEmpty()) {
                    Text(
                        text = artistText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee(),
                    )
                }
                if (track?.frequencyMhz != null) {
                    Text(
                        text = stringResource(R.string.media_radio_frequency, track.frequencyMhz),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (playback.queueSize > 0) {
                    Text(
                        text = stringResource(
                            R.string.media_track_format,
                            playback.queueIndex + 1,
                            playback.queueSize,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        SeekBar(
            positionMs = playback.positionMs,
            durationMs = playback.durationMs,
            positionLabel = positionText,
            durationLabel = durationText,
            onSeek = onSeek,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onPrevious,
                modifier = Modifier.size(VivaDimens.TouchTarget),
            ) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = stringResource(
                        if (isRadio) R.string.media_radio_seek_previous else R.string.media_previous,
                    ),
                    modifier = Modifier.size(36.dp),
                )
            }
            IconButton(
                onClick = onRewind,
                modifier = Modifier.size(VivaDimens.TouchTarget),
            ) {
                Icon(
                    Icons.Default.FastRewind,
                    contentDescription = stringResource(R.string.media_rewind),
                    modifier = Modifier.size(32.dp),
                )
            }
            FilledIconButton(
                onClick = onTogglePlayPause,
                modifier = Modifier.size(72.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(
                    imageVector = if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(
                        if (playback.isPlaying) R.string.media_pause else R.string.media_play,
                    ),
                    modifier = Modifier.size(40.dp),
                )
            }
            IconButton(
                onClick = onFastForward,
                modifier = Modifier.size(VivaDimens.TouchTarget),
            ) {
                Icon(
                    Icons.Default.FastForward,
                    contentDescription = stringResource(R.string.media_fast_forward),
                    modifier = Modifier.size(32.dp),
                )
            }
            IconButton(
                onClick = onNext,
                modifier = Modifier.size(VivaDimens.TouchTarget),
            ) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = stringResource(
                        if (isRadio) R.string.media_radio_seek_next else R.string.media_next,
                    ),
                    modifier = Modifier.size(36.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    positionLabel: String,
    durationLabel: String,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val duration = durationMs.coerceAtLeast(0L)
    var sliderValue by remember(duration) { mutableFloatStateOf(0f) }
    var dragging by remember { mutableFloatStateOf(Float.NaN) }
    val interactionSource = remember { MutableInteractionSource() }

    LaunchedEffect(positionMs, duration, dragging) {
        if (dragging.isNaN() && duration > 0L) {
            sliderValue = (positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Slider(
            value = if (!dragging.isNaN()) dragging else sliderValue,
            onValueChange = { value ->
                dragging = value
            },
            onValueChangeFinished = {
                val fraction = if (!dragging.isNaN()) dragging else sliderValue
                if (duration > 0L) {
                    onSeek((fraction * duration).toLong())
                }
                dragging = Float.NaN
            },
            enabled = duration > 0L,
            valueRange = 0f..1f,
            interactionSource = interactionSource,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = positionLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = durationLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlaybackControlsColumn(
    playback: PlaybackUiState,
    isRadio: Boolean,
    onCycleRepeat: () -> Unit,
    onCycleSpeed: () -> Unit,
    onCycleQuality: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val repeatIcon: ImageVector
    val repeatDesc: String
    when (playback.repeatMode) {
        RepeatMode.OFF -> {
            repeatIcon = Icons.Default.Repeat
            repeatDesc = stringResource(R.string.media_repeat_off)
        }
        RepeatMode.ONE -> {
            repeatIcon = Icons.Default.RepeatOne
            repeatDesc = stringResource(R.string.media_repeat_one)
        }
        RepeatMode.ALL -> {
            repeatIcon = Icons.Default.Repeat
            repeatDesc = stringResource(R.string.media_repeat_all)
        }
    }
    val qualityLabel = when (playback.audioQuality) {
        AudioQuality.HI_RES -> stringResource(R.string.media_quality_hi_res)
        AudioQuality.NORMAL -> stringResource(R.string.media_quality_normal)
    }
    val favoriteDesc = if (playback.isCurrentFavorite) {
        stringResource(R.string.media_remove_playlist)
    } else {
        stringResource(R.string.media_add_playlist)
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ControlIconButton(
            onClick = onCycleRepeat,
            enabled = !isRadio,
            selected = playback.repeatMode != RepeatMode.OFF,
            imageVector = repeatIcon,
            contentDescription = repeatDesc,
        )
        ControlIconButton(
            onClick = onCycleSpeed,
            selected = playback.playbackSpeed.multiplier != 1f,
            imageVector = Icons.Default.Speed,
            contentDescription = stringResource(
                R.string.media_speed,
                playback.playbackSpeed.label,
            ),
        )
        ControlIconButton(
            onClick = onCycleQuality,
            selected = playback.audioQuality == AudioQuality.HI_RES,
            imageVector = Icons.Default.HighQuality,
            contentDescription = qualityLabel,
        )
        ControlIconButton(
            onClick = onToggleFavorite,
            enabled = !isRadio && playback.track != null,
            selected = playback.isCurrentFavorite,
            imageVector = if (playback.isCurrentFavorite) {
                Icons.Filled.PlaylistAddCheck
            } else {
                Icons.AutoMirrored.Filled.PlaylistAdd
            },
            contentDescription = favoriteDesc,
        )
    }
}

@Composable
private fun ControlIconButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(VivaDimens.TouchTarget),
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        ),
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = Modifier.size(40.dp),
        )
    }
}

@Composable
private fun TracksPane(
    playback: PlaybackUiState,
    isRadio: Boolean,
    onSelectTrack: (String) -> Unit,
    onSelectStation: (String) -> Unit,
    onFavoritesFilterChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!isRadio) {
            FilterChip(
                selected = playback.favoritesFilterEnabled,
                onClick = {
                    onFavoritesFilterChange(!playback.favoritesFilterEnabled)
                },
                label = {
                    Text(stringResource(R.string.media_favorites_filter))
                },
            )
        }
        if (isRadio) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                items(playback.stations, key = RadioStation::id) { station ->
                    FilterChip(
                        selected = playback.track?.id == station.id,
                        onClick = { onSelectStation(station.id) },
                        label = {
                            Text(
                                text = String.format(
                                    "%.1f  %s",
                                    station.frequencyMhz,
                                    station.name,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(VivaDimens.TouchTargetMin),
                    )
                }
            }
        } else {
            val tracks = playback.visibleLibraryTracks
            when {
                playback.libraryTracks.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.media_playlist_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
                tracks.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.media_favorites_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 8.dp),
                    ) {
                        items(tracks, key = MediaTrack::id) { track ->
                            FilterChip(
                                selected = playback.track?.id == track.id,
                                onClick = { onSelectTrack(track.id) },
                                label = {
                                    Text(
                                        text = "${track.title} — ${track.artist}",
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(VivaDimens.TouchTargetMin),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(
    name = "Playback controls column",
    showBackground = true,
    widthDp = 96,
    heightDp = 420,
)
@Composable
private fun PlaybackControlsColumnPreview() {
    VivaTheme(darkTheme = true) {
        SectionCard(
            modifier = Modifier
                .fillMaxHeight()
                .padding(8.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
        ) {
            PlaybackControlsColumn(
                playback = PlaybackUiState(
                    repeatMode = RepeatMode.ALL,
                    audioQuality = AudioQuality.HI_RES,
                ),
                isRadio = false,
                onCycleRepeat = {},
                onCycleSpeed = {},
                onCycleQuality = {},
                onToggleFavorite = {},
            )
        }
    }
}
