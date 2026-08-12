package com.sopa.viva_automotive.feature.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sopa.viva_automotive.feature.media.domain.MediaRepository
import com.sopa.viva_automotive.feature.media.domain.MediaSource
import com.sopa.viva_automotive.feature.media.domain.PlaybackUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MediaViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    val playback: StateFlow<PlaybackUiState> = mediaRepository.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaybackUiState())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun play() = launchMedia { mediaRepository.play() }

    fun pause() = launchMedia { mediaRepository.pause() }

    fun togglePlayPause() {
        if (playback.value.isPlaying) pause() else play()
    }

    fun next() = launchMedia { mediaRepository.next() }

    fun previous() = launchMedia { mediaRepository.previous() }

    fun selectSource(source: MediaSource) = launchMedia {
        mediaRepository.setSource(source)
    }

    fun selectStation(stationId: String) = launchMedia {
        mediaRepository.selectStation(stationId)
    }

    fun selectTrack(trackId: String) = launchMedia {
        mediaRepository.selectTrack(trackId)
    }

    fun refreshLibrary() = launchMedia {
        mediaRepository.refreshLibrary()
    }

    fun playRadio() = launchMedia { mediaRepository.tuneRadio() }

    fun cycleRepeatMode() = announce(mediaRepository.cycleRepeatMode())

    fun cyclePlaybackSpeed() = announce(mediaRepository.cyclePlaybackSpeed())

    fun cycleAudioQuality() = announce(mediaRepository.cycleAudioQuality())

    fun toggleFavoriteCurrent() = launchMedia(announceSuccess = true) {
        mediaRepository.toggleFavoriteCurrent()
    }

    fun setFavoritesFilter(enabled: Boolean) {
        mediaRepository.setFavoritesFilter(enabled)
    }

    fun seekTo(positionMs: Long) {
        mediaRepository.seekTo(positionMs)
    }

    fun rewind() {
        mediaRepository.seekBy(-SEEK_STEP_MS)
    }

    fun fastForward() {
        mediaRepository.seekBy(SEEK_STEP_MS)
    }

    private fun launchMedia(
        announceSuccess: Boolean = false,
        block: suspend () -> Result<String>,
    ) {
        viewModelScope.launch {
            block().fold(
                onSuccess = { message ->
                    if (announceSuccess) _messages.emit(message)
                },
                onFailure = { error ->
                    _messages.emit(error.message ?: "Media command failed")
                },
            )
        }
    }

    private fun announce(result: Result<String>) {
        viewModelScope.launch {
            result.fold(
                onSuccess = { message -> _messages.emit(message) },
                onFailure = { error ->
                    _messages.emit(error.message ?: "Media command failed")
                },
            )
        }
    }

    private companion object {
        const val SEEK_STEP_MS = 10_000L
    }
}
