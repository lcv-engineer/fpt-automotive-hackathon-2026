package com.viva.voice.hotword

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HotwordMetricsTest {

    @Test
    fun `false accepts per hour uses armed duration`() {
        val metrics = HotwordMetrics()
        metrics.markArmed(0L)
        metrics.recordFalseAccept()
        metrics.markDisarmed(3_600_000L)
        val snap = metrics.snapshot()
        assertEquals(1.0, snap.falseAcceptsPerHour, 0.001)
    }

    @Test
    fun `mean latency tracks accepts`() {
        val metrics = HotwordMetrics()
        metrics.recordAccept(100)
        metrics.recordAccept(200)
        assertEquals(150.0, metrics.snapshot().meanTriggerLatencyMs, 0.001)
        assertTrue(metrics.snapshot().falseRejectRate == 0.0)
    }
}
