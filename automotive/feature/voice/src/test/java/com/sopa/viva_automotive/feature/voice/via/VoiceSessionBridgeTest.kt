package com.sopa.viva_automotive.feature.voice.via

import com.viva.voice.audio.Trigger
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceSessionBridgeTest {

    @Test
    fun `assist gesture maps to wake word hotword source`() {
        val (trigger, source) = VoiceSessionBridge.triggerFromShowFlags(
            VoiceSessionBridge.SHOW_SOURCE_ASSIST_GESTURE,
        )
        assertEquals(Trigger.WAKE_WORD, trigger)
        assertEquals(VoiceSessionBridge.SHOW_SOURCE_HOTWORD, source)
    }

    @Test
    fun `ptt flag maps to push to talk`() {
        val (trigger, source) = VoiceSessionBridge.triggerFromShowFlags(
            VoiceSessionBridge.SHOW_SOURCE_PUSH_TO_TALK,
        )
        assertEquals(Trigger.PUSH_TO_TALK, trigger)
        assertEquals(VoiceSessionBridge.SHOW_SOURCE_PTT, source)
    }
}
