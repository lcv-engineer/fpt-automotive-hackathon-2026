package com.sopa.viva_automotive.feature.voice.navigation

import com.viva.voice.agent.VoiceTurnStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceIntentNavigatorTest {

    @Test
    fun `media play switches to media tab`() {
        assertEquals(
            AppRoutes.MEDIA,
            VoiceIntentNavigator.routeFor("media_play", VoiceTurnStatus.APPLIED),
        )
    }

    @Test
    fun `hvac and status intents map to their tabs`() {
        assertEquals(
            AppRoutes.HVAC,
            VoiceIntentNavigator.routeFor("hvac_set_temp", VoiceTurnStatus.APPLIED),
        )
        assertEquals(
            AppRoutes.STATUS,
            VoiceIntentNavigator.routeFor("door_lock", VoiceTurnStatus.APPLIED),
        )
        assertEquals(
            AppRoutes.STATUS,
            VoiceIntentNavigator.routeFor("vehicle_status_speed", VoiceTurnStatus.APPLIED),
        )
    }

    @Test
    fun `unsupported turns do not navigate`() {
        assertNull(
            VoiceIntentNavigator.routeFor("media_play", VoiceTurnStatus.UNSUPPORTED),
        )
    }

    @Test
    fun `door confirmation opens status`() {
        assertEquals(
            AppRoutes.STATUS,
            VoiceIntentNavigator.routeFor("door_lock", VoiceTurnStatus.NEEDS_CONFIRMATION),
        )
    }
}
