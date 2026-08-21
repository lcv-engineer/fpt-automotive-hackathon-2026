package com.sopa.viva_automotive.feature.voice.data.asr

import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers which STT backend served the current turn so history can record it
 * without putting engine metadata on [com.viva.voice.agent.VoiceTurnResult].
 */
@Singleton
class AsrTurnContext @Inject constructor() {
    private val engineKey = AtomicReference(NONE)

    fun markEngine(storageKey: String) {
        engineKey.set(storageKey)
    }

    fun markTextOnly() {
        engineKey.set(NONE)
    }

    /** Snapshot for history; leaves the value for the rest of the turn. */
    fun peek(): String = engineKey.get()

    companion object {
        const val NONE = "none"
    }
}
