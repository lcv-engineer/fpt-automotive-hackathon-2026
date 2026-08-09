package com.sopa.viva_automotive.feature.voice.domain.media

/** Executes a routed media command across the app/media-session boundary. */
interface MediaCommandExecutor {
    suspend fun execute(command: MediaCommand): Result<String>
}

/** Minimal transport surface kept free of Android so command dispatch is unit-testable. */
interface MediaTransportControls {
    fun play()
    fun pause()
    fun skipToNext()
}

object MediaCommandDispatcher {
    fun dispatch(command: MediaCommand, controls: MediaTransportControls): String {
        when (command) {
            MediaCommand.PLAY -> controls.play()
            MediaCommand.PAUSE -> controls.pause()
            MediaCommand.NEXT -> controls.skipToNext()
        }
        return command.dispatchedMessageVi
    }
}

class MediaTransportException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
