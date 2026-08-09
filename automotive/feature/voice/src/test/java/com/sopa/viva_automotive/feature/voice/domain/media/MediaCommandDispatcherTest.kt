package com.sopa.viva_automotive.feature.voice.domain.media

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaCommandDispatcherTest {

    @Test
    fun `each media command calls its matching transport control`() {
        val controls = RecordingControls()

        MediaCommand.entries.forEach { command ->
            val message = MediaCommandDispatcher.dispatch(command, controls)

            assertEquals(command.name, controls.lastCall)
            assertEquals(command.dispatchedMessageVi, message)
        }
    }

    private class RecordingControls : MediaTransportControls {
        var lastCall: String? = null

        override fun play() {
            lastCall = MediaCommand.PLAY.name
        }

        override fun pause() {
            lastCall = MediaCommand.PAUSE.name
        }

        override fun skipToNext() {
            lastCall = MediaCommand.NEXT.name
        }
    }
}
