package com.sopa.viva_automotive.feature.media

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sopa.viva_automotive.core.ui.components.SectionCard
import com.sopa.viva_automotive.core.ui.theme.VivaDimens
import com.sopa.viva_automotive.feature.media.domain.MediaSource
import com.sopa.viva_automotive.feature.media.domain.MediaTrack
import com.sopa.viva_automotive.feature.media.domain.PlaybackUiState
import com.sopa.viva_automotive.feature.media.domain.RadioStation
import com.sopa.viva_automotive.feature.media.ui.AlbumArtwork
import com.sopa.viva_automotive.feature.media.ui.formatDurationMs

/**
 * Cabin media layout (Library or Radio screen — source comes from nav):
 *  (1) Title
 *  (2) One row — Now playing : Tracks/Presets : Volume = 5 : 4 : 1
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
                    )
                }

                SectionCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    // Edge-to-edge XL vertical slider; card radius = track radius (XL 28dp).
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(VolumeSliderXlCorner),
                ) {
                    VolumePane(
                        volume = playback.mediaVolume,
                        onVolumeChange = viewModel::setMediaVolume,
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
        // (2) Album art | track metadata
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

        // (3) M3 linear progress — flat when paused, wavy when playing
        // https://m3.material.io/components/progress-indicators/specs
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NowPlayingProgress(
                progress = playback.progress,
                isPlaying = playback.isPlaying,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = positionText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = durationText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
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

/**
 * M3 linear progress — Shape Flat when paused, Wavy when playing
 * (https://m3.material.io/components/progress-indicators/specs).
 * Amplitude 0 = flat; 1 = full-height wave.
 */
@Composable
private fun NowPlayingProgress(
    progress: Float,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val amplitude = if (isPlaying) 1f else 0f
    LinearWavyProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier.height(14.dp),
        amplitude = { _ -> amplitude },
        wavelength = WavyProgressIndicatorDefaults.LinearDeterminateWavelength,
    )
}

@Composable
private fun TracksPane(
    playback: PlaybackUiState,
    isRadio: Boolean,
    onSelectTrack: (String) -> Unit,
    onSelectStation: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
        } else if (playback.libraryTracks.isEmpty()) {
            Text(
                text = stringResource(R.string.media_playlist_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                items(playback.libraryTracks, key = MediaTrack::id) { track ->
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VolumePane(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
) {
    // M3 Expressive vertical XL slider with inset volume icon in the track.
    // Specs: https://m3.material.io/components/sliders/specs
    // VerticalSlider is still internal → measure as horizontal, then layout+rotate
    // so the track length equals the card height and thickness equals card width.
    var sliderValue by remember { mutableFloatStateOf(volume) }
    LaunchedEffect(volume) {
        sliderValue = volume
    }
    val interactionSource = remember { MutableInteractionSource() }
    val volumeLabel = stringResource(R.string.media_volume)
    val density = LocalDensity.current
    // Inactive track defaults to secondaryContainer; match SectionCard surface instead.
    val cardSurface = MaterialTheme.colorScheme.surface
    val sliderColors = SliderDefaults.colors(inactiveTrackColor = cardSurface)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val cardWidth = maxWidth
        val cardHeight = maxHeight
        val trackThicknessPx = with(density) { cardWidth.roundToPx() }
        val trackLengthPx = with(density) { cardHeight.roundToPx() }

        Slider(
            value = sliderValue,
            onValueChange = { value ->
                sliderValue = value
                onVolumeChange(value)
            },
            valueRange = 0f..1f,
            colors = sliderColors,
            modifier = Modifier
                .semantics { contentDescription = volumeLabel }
                .fillMaxSize()
                .layout { measurable, constraints ->
                    // Occupy the full card (W×H), but measure the slider as H×W
                    // so after -90° the bar is snug top-to-bottom and edge-to-edge.
                    val placeable = measurable.measure(
                        Constraints.fixed(
                            width = trackLengthPx.coerceAtLeast(0),
                            height = trackThicknessPx.coerceAtLeast(0),
                        ),
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(
                            x = (constraints.maxWidth - placeable.width) / 2,
                            y = (constraints.maxHeight - placeable.height) / 2,
                        )
                    }
                }
                .graphicsLayer {
                    rotationZ = -90f
                    transformOrigin = TransformOrigin.Center
                },
            interactionSource = interactionSource,
            thumb = {
                SliderDefaults.Thumb(
                    interactionSource = interactionSource,
                    colors = sliderColors,
                    thumbSize = VolumeSliderXlThumbSize,
                )
            },
            track = { sliderState ->
                // Card + track share VolumeSliderXlCorner so bo góc khớp nhau.
                val corner = RoundedCornerShape(VolumeSliderXlCorner)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(cardWidth)
                        .clip(corner),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(cardWidth),
                        colors = sliderColors,
                        // Default trackCornerSize is half track height (pill); force XL 28dp
                        // to match SectionCard shape = RoundedCornerShape(VolumeSliderXlCorner).
                        trackCornerSize = VolumeSliderXlCorner,
                        // M3 stop indicator at track end — hidden for volume fader.
                        // https://m3.material.io/components/sliders/specs
                        drawStopIndicator = null,
                    )
                    // Inset icon at leading (min) end → bottom of the vertical card.
                    Icon(
                        imageVector = if (sliderValue <= 0.01f) {
                            Icons.AutoMirrored.Filled.VolumeDown
                        } else {
                            Icons.AutoMirrored.Filled.VolumeUp
                        },
                        contentDescription = null,
                        modifier = Modifier
                            .padding(start = VolumeSliderXlIconInset)
                            .size(VolumeSliderXlIconSize)
                            .graphicsLayer { rotationZ = 90f },
                        tint = if (sliderValue > 0.12f) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            },
        )
    }
}

// M3 Expressive Slider XL tokens (track 96 / handle 108×4 / corner 28 / icon 32).
// https://m3.material.io/components/sliders/specs
private val VolumeSliderXlThumbSize = DpSize(width = 4.dp, height = 108.dp)
private val VolumeSliderXlCorner = 28.dp
private val VolumeSliderXlIconSize = 32.dp
private val VolumeSliderXlIconInset = 16.dp
