package com.viva.voice.hotword

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Minimal FA/FR/latency counters for the R7 wake-word gate.
 *
 * Production enablement should stay off until [falseAcceptsPerHour] and
 * [falseRejectRate] are measured on cabin audio against agreed thresholds.
 */
class HotwordMetrics {
    private val accepts = AtomicInteger(0)
    private val rejects = AtomicInteger(0)
    private val falseAccepts = AtomicInteger(0)
    private val selfWakes = AtomicInteger(0)
    private val latencySumMs = AtomicLong(0)
    private val latencyCount = AtomicInteger(0)
    private val armedSinceElapsedMs = AtomicLong(-1L)
    private val armedAccumulatedMs = AtomicLong(0)

    fun markArmed(nowElapsedMs: Long) {
        armedSinceElapsedMs.compareAndSet(-1L, nowElapsedMs)
    }

    fun markDisarmed(nowElapsedMs: Long) {
        val since = armedSinceElapsedMs.getAndSet(-1L)
        if (since >= 0L && nowElapsedMs > since) {
            armedAccumulatedMs.addAndGet(nowElapsedMs - since)
        }
    }

    fun recordAccept(triggerLatencyMs: Long) {
        accepts.incrementAndGet()
        if (triggerLatencyMs >= 0) {
            latencySumMs.addAndGet(triggerLatencyMs)
            latencyCount.incrementAndGet()
        }
    }

    fun recordReject() {
        rejects.incrementAndGet()
    }

    fun recordFalseAccept() {
        falseAccepts.incrementAndGet()
    }

    fun recordSelfWake() {
        selfWakes.incrementAndGet()
    }

    fun snapshot(nowElapsedMs: Long = 0L): Snapshot {
        val armedMs = armedAccumulatedMs.get() +
            armedSinceElapsedMs.get().let { since ->
                if (since >= 0L && nowElapsedMs > since) nowElapsedMs - since else 0L
            }
        val hours = armedMs / 3_600_000.0
        val acceptCount = accepts.get()
        val rejectCount = rejects.get()
        val trials = acceptCount + rejectCount
        val latencyN = latencyCount.get()
        return Snapshot(
            accepts = acceptCount,
            rejects = rejectCount,
            falseAccepts = falseAccepts.get(),
            selfWakes = selfWakes.get(),
            armedMs = armedMs,
            falseAcceptsPerHour = if (hours > 0) falseAccepts.get() / hours else 0.0,
            falseRejectRate = if (trials > 0) rejectCount.toDouble() / trials else 0.0,
            meanTriggerLatencyMs = if (latencyN > 0) latencySumMs.get().toDouble() / latencyN else 0.0,
        )
    }

    data class Snapshot(
        val accepts: Int,
        val rejects: Int,
        val falseAccepts: Int,
        val selfWakes: Int,
        val armedMs: Long,
        val falseAcceptsPerHour: Double,
        val falseRejectRate: Double,
        val meanTriggerLatencyMs: Double,
    )
}
