package com.sopa.viva_automotive.feature.voice.data.audio

import com.viva.voice.audio.VadConfig
import com.viva.voice.audio.VoiceActivityScorer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class ReusableVadDriverFactoryTest {
    @Test
    fun `two capture drivers load one scorer and reset it once per session`() {
        var loads = 0
        var resets = 0
        val factory = ReusableVadDriverFactory(
            scorerLoader = {
                loads++
                object : VoiceActivityScorer {
                    override fun probability(frame: ShortArray): Float = 0f
                    override fun reset() {
                        resets++
                    }
                }
            },
            config = VadConfig(),
        )

        val first = factory.create()
        val second = factory.create()

        assertNotSame(first, second)
        assertEquals(1, loads)
        assertEquals(2, resets)
    }
}
