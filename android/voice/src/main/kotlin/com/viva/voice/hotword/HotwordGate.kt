package com.viva.voice.hotword

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Shared arm/disarm latch so hotword listening yields the mic during a voice
 * session and during TTS (anti self-wake). One owner for the always-on path.
 */
object HotwordGate {
    private val paused = AtomicBoolean(false)
    private val pauseDepth = AtomicInteger(0)

    val isPaused: Boolean get() = paused.get()

    fun pause(reason: String = "") {
        pauseDepth.incrementAndGet()
        paused.set(true)
        if (reason.isNotEmpty()) {
            // No Android Log dependency in voice-core unit tests.
            System.out.println("VIVA_VOICE|hotword_pause|$reason")
        }
    }

    fun resume(reason: String = "") {
        val depth = pauseDepth.updateAndGet { current -> maxOf(0, current - 1) }
        if (depth == 0) {
            paused.set(false)
            if (reason.isNotEmpty()) {
                System.out.println("VIVA_VOICE|hotword_resume|$reason")
            }
        }
    }

    fun forceResume() {
        pauseDepth.set(0)
        paused.set(false)
    }
}
