package com.sopa.viva_automotive.feature.media.domain

import kotlinx.coroutines.flow.StateFlow

interface MediaRepository {
    val state: StateFlow<PlaybackUiState>

    suspend fun play(query: String? = null): Result<String>
    suspend fun pause(): Result<String>
    suspend fun next(): Result<String>
    suspend fun previous(): Result<String>
    fun adjustVolume(delta: Int): Result<String>
    fun setMediaVolume(volume: Float): Result<String>

    /**
     * Windows-style communications duck: lower in-app media gain while the
     * voice session owns the mic/reply path. Does not use AudioFocus (AAOS
     * often maps ASSISTANT → [LOSS_TRANSIENT], which can leave playback silent).
     */
    fun setVoiceDucked(ducked: Boolean)

    suspend fun setSource(source: MediaSource): Result<String>
    suspend fun tuneRadio(query: String? = null): Result<String>
    suspend fun selectStation(stationId: String): Result<String>
    suspend fun selectTrack(trackId: String): Result<String>
    suspend fun refreshLibrary(): Result<String>
}
