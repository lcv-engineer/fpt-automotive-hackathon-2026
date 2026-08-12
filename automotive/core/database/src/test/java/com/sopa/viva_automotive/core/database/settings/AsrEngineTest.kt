package com.sopa.viva_automotive.core.database.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AsrEngineTest {

    @Test
    fun `unknown keys fall back to viva`() {
        assertEquals(AsrEngine.VIVA, AsrEngine.fromStorageKey(null))
        assertEquals(AsrEngine.VIVA, AsrEngine.fromStorageKey("nope"))
        assertEquals(AsrEngine.GOOGLE, AsrEngine.fromStorageKey("google"))
        assertEquals(AsrEngine.VIVA, AsrEngine.fromStorageKey("viva"))
    }
}
