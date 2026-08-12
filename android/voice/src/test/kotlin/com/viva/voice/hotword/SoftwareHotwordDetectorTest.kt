package com.viva.voice.hotword

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SoftwareHotwordDetectorTest {

    @Before
    fun armGate() {
        HotwordGate.forceResume()
    }

    @Test
    fun `matching enrolled template fires once`() {
        val detector = SoftwareHotwordDetector(correlationThreshold = 0.75f)
        val template = burstPattern(pattern = floatArrayOf(1f, 0.2f, 0.9f, 0.1f, 0.8f, 0.15f, 1f, 0.2f))
        detector.setTemplate(template)

        val hit = detector.accept(template)
        assertTrue(hit != null && hit.score >= 0.75f)
        // Refractory window must suppress an immediate duplicate.
        assertNull(detector.accept(template.copyOf()))
    }

    @Test
    fun `dissimilar audio does not fire`() {
        val detector = SoftwareHotwordDetector(correlationThreshold = 0.85f)
        detector.setTemplate(
            burstPattern(pattern = floatArrayOf(1f, 0.1f, 1f, 0.1f, 1f, 0.1f, 1f, 0.1f)),
        )
        assertNull(
            detector.accept(
                burstPattern(pattern = floatArrayOf(0.1f, 1f, 0.1f, 1f, 0.1f, 1f, 0.1f, 1f)),
            ),
        )
    }

    @Test
    fun `default threshold rejects smooth cabin speech envelope`() {
        // Envelope correlator previously false-fired on ambient speech when the
        // threshold sat at 0.82 — cabin talk has high RMS but a flatter cadence.
        val detector = SoftwareHotwordDetector()
        detector.setTemplate(
            burstPattern(
                pattern = floatArrayOf(1f, 0.05f, 0.95f, 0.08f, 0.9f, 0.05f, 1f, 0.1f, 0.85f, 0.05f),
            ),
        )
        val ambient = burstPattern(
            pattern = FloatArray(10) { 0.55f + (it % 3) * 0.05f },
        )
        assertNull(detector.accept(ambient))
    }

    @Test
    fun `paused gate suppresses detection`() {
        val detector = SoftwareHotwordDetector(correlationThreshold = 0.5f)
        val template = burstPattern(pattern = floatArrayOf(1f, 0.2f, 0.9f, 0.2f, 1f, 0.2f, 0.8f, 0.2f))
        detector.setTemplate(template)
        HotwordGate.pause("test")
        assertNull(detector.accept(template))
        HotwordGate.forceResume()
        assertTrue(detector.accept(template) != null)
    }

    /** Distinct energy envelopes — pure tones all look flat to the envelope correlator. */
    private fun burstPattern(
        pattern: FloatArray,
        hopSamples: Int = 512,
        sampleRate: Int = 16_000,
    ): ShortArray {
        val out = ShortArray(pattern.size * hopSamples)
        for (i in pattern.indices) {
            val amp = (pattern[i] * 20_000).toInt().coerceIn(0, 32_767)
            val start = i * hopSamples
            for (j in 0 until hopSamples) {
                // Alternating sign keeps RMS high without changing envelope bins.
                out[start + j] = if (j % 2 == 0) amp.toShort() else (-amp).toShort()
            }
        }
        require(out.size >= sampleRate / 4)
        return out
    }
}
