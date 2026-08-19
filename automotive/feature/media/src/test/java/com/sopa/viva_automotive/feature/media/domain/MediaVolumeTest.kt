package com.sopa.viva_automotive.feature.media.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaVolumeTest {

    @Test
    fun `raising steps up by one step`() {
        assertEquals(0.6f, MediaVolume.stepped(current = 0.5f, delta = 1), TOLERANCE)
    }

    @Test
    fun `lowering steps down by one step`() {
        assertEquals(0.4f, MediaVolume.stepped(current = 0.5f, delta = -1), TOLERANCE)
    }

    @Test
    fun `raising at the ceiling stays at the ceiling`() {
        assertEquals(1f, MediaVolume.stepped(current = 1f, delta = 1), TOLERANCE)
    }

    @Test
    fun `lowering at the floor stays at the floor`() {
        assertEquals(0f, MediaVolume.stepped(current = 0f, delta = -1), TOLERANCE)
    }

    @Test
    fun `a volume above the ceiling is clamped`() {
        assertEquals(1f, MediaVolume.clamped(1.4f), TOLERANCE)
    }

    @Test
    fun `a negative volume is clamped to the floor`() {
        assertEquals(0f, MediaVolume.clamped(-0.3f), TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 0.0001f
    }
}
