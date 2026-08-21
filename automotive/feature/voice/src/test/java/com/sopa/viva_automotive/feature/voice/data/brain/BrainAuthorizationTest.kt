package com.sopa.viva_automotive.feature.voice.data.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrainAuthorizationTest {

    @Test
    fun `blank deployment token fails closed before creating an authorization header`() {
        assertNull(brainAuthorizationHeader(""))
        assertNull(brainAuthorizationHeader("   "))
    }

    @Test
    fun `configured deployment token uses the bearer scheme without rewriting the token`() {
        assertEquals(
            "Bearer room-scoped-token",
            brainAuthorizationHeader("  room-scoped-token  "),
        )
    }
}
