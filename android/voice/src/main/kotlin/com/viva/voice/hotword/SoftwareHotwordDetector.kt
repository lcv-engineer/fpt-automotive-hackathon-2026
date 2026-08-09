package com.viva.voice.hotword

import kotlin.math.sqrt

/**
 * On-device software keyphrase spotter used when SoundTrigger/DSP is unavailable.
 *
 * Matches a sliding window against an enrolled PCM16 mono template using
 * normalized energy-envelope correlation. Not a production DSP substitute —
 * OEM SoundTrigger models remain the primary path for “Vi-Vi ơi”.
 *
 * Default threshold is intentionally strict: envelope correlation alone is a
 * weak discriminator and will false-accept ambient cabin speech if set too low.
 */
class SoftwareHotwordDetector(
    private val sampleRate: Int = 16_000,
    private val hopSamples: Int = 512,
    private val correlationThreshold: Float = 0.90f,
    private val minRms: Float = 0.015f,
    /** Samples discarded after a hit so the same utterance cannot re-fire. */
    private val refractorySamples: Int = 16_000,
) {
    data class Match(val score: Float, val rms: Float)

    private var templateEnvelope: FloatArray = FloatArray(0)
    private val pending = ArrayList<Short>(sampleRate * 2)
    private var refractoryRemaining = 0

    val hasTemplate: Boolean get() = templateEnvelope.isNotEmpty()

    fun setTemplate(pcm16: ShortArray) {
        require(pcm16.isNotEmpty()) { "hotword template must not be empty" }
        templateEnvelope = envelope(pcm16)
        pending.clear()
        refractoryRemaining = 0
    }

    fun clearTemplate() {
        templateEnvelope = FloatArray(0)
        pending.clear()
        refractoryRemaining = 0
    }

    /**
     * Feed one PCM frame (any length). Returns a [Match] once when a wake fires;
     * internal buffer then advances past the match to avoid immediate re-trigger.
     */
    fun accept(frame: ShortArray): Match? {
        if (!hasTemplate || HotwordGate.isPaused) return null
        var index = 0
        if (refractoryRemaining > 0) {
            val skip = minOf(refractoryRemaining, frame.size)
            refractoryRemaining -= skip
            index = skip
        }
        while (index < frame.size) {
            pending.add(frame[index])
            index++
        }
        val need = templateEnvelope.size * hopSamples
        if (pending.size < need) return null

        var matched: Match? = null
        var offset = 0
        while (offset + need <= pending.size) {
            val window = ShortArray(need) { i -> pending[offset + i] }
            val score = correlation(envelope(window), templateEnvelope)
            val rms = rms(window)
            if (rms >= minRms && score >= correlationThreshold) {
                matched = Match(score = score, rms = rms)
                offset += need
                refractoryRemaining = refractorySamples
                break
            }
            offset += hopSamples
        }
        if (offset > 0) {
            val keepFrom = minOf(offset, pending.size)
            val kept = pending.subList(keepFrom, pending.size).toShortArray()
            pending.clear()
            kept.forEach { pending.add(it) }
        }
        // Cap buffer to ~3s to bound memory if never matched.
        val maxPending = sampleRate * 3
        if (pending.size > maxPending) {
            val drop = pending.size - maxPending
            val kept = pending.subList(drop, pending.size).toShortArray()
            pending.clear()
            kept.forEach { pending.add(it) }
        }
        return matched
    }

    fun reset() {
        pending.clear()
        refractoryRemaining = 0
    }

    private fun envelope(pcm: ShortArray): FloatArray {
        val bins = maxOf(1, pcm.size / hopSamples)
        val out = FloatArray(bins)
        for (i in 0 until bins) {
            val start = i * hopSamples
            val end = minOf(pcm.size, start + hopSamples)
            var sum = 0.0
            for (j in start until end) {
                val v = pcm[j] / 32768.0
                sum += v * v
            }
            out[i] = sqrt(sum / (end - start)).toFloat()
        }
        return out
    }

    private fun correlation(a: FloatArray, b: FloatArray): Float {
        val n = minOf(a.size, b.size)
        if (n == 0) return 0f
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in 0 until n) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        if (na == 0.0 || nb == 0.0) return 0f
        return (dot / (sqrt(na) * sqrt(nb))).toFloat()
    }

    private fun rms(pcm: ShortArray): Float {
        if (pcm.isEmpty()) return 0f
        var sum = 0.0
        for (s in pcm) {
            val v = s / 32768.0
            sum += v * v
        }
        return sqrt(sum / pcm.size).toFloat()
    }
}
