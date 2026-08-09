package com.sopa.viva_automotive.feature.voice.data.audio

import android.util.Log
import com.sopa.viva_automotive.feature.media.domain.MediaRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ducks in-app media while a voice turn owns the mic / reply path.
 *
 * Uses [MediaRepository.setVoiceDucked] (player gain) instead of AudioFocus:
 * on AAOS, `USAGE_ASSISTANT` + `MAY_DUCK` often delivers `LOSS_TRANSIENT` to
 * media and can leave playback silent after the session ends.
 */
@Singleton
class VoiceSessionDucker @Inject constructor(
    private val mediaRepository: MediaRepository,
) {
    @Volatile
    private var held = false

    fun begin(reason: String): Boolean {
        if (held) return true
        held = true
        mediaRepository.setVoiceDucked(true)
        Log.i(TAG, "session duck begin reason=$reason")
        return true
    }

    fun end(reason: String) {
        if (!held) {
            // Still force unduck — recovers from process races / stuck duck.
            mediaRepository.setVoiceDucked(false)
            return
        }
        held = false
        mediaRepository.setVoiceDucked(false)
        Log.i(TAG, "session duck end reason=$reason")
    }

    private companion object {
        const val TAG = "VIVA_VOICE"
    }
}
