package com.viva.voice.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/**
 * Requests standard Android audio focus; AAOS CarAudioService manages it for apps.
 *
 * Sources:
 * https://source.android.com/docs/automotive/audio/audio-focus
 * https://developer.android.com/media/optimize/audio-focus
 */
class AndroidAudioFocusController(
    context: Context,
) : AudioFocusController {
    private val audioManager = context.getSystemService(AudioManager::class.java)

    @Volatile
    private var onFocusLost: (() -> Unit)? = null

    private val focusRequest = AudioFocusRequest.Builder(
        // Same idea as Windows "Communications" ducking: lower other streams,
        // don't mute/pause them. Media3 ExoPlayer (handleAudioFocus=true) ducks
        // to ~20% gain on LOSS_TRANSIENT_CAN_DUCK.
        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
    )
        .setAudioAttributes(VoiceAudioAttributes.value)
        .setOnAudioFocusChangeListener { change ->
            // Holders of MAY_DUCK should only stop on hard/transient loss.
            // CAN_DUCK is for media players that need to attenuate — not us.
            if (
                change == AudioManager.AUDIOFOCUS_LOSS ||
                change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
            ) {
                onFocusLost?.invoke()
            }
        }
        .build()

    override fun request(onFocusLost: () -> Unit): Boolean {
        this.onFocusLost = onFocusLost
        val result = audioManager.requestAudioFocus(focusRequest)
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) this.onFocusLost = null
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    override fun abandon() {
        audioManager.abandonAudioFocusRequest(focusRequest)
        onFocusLost = null
    }
}

/** `USAGE_ASSISTANT` maps to AAOS `VOICE_COMMAND`; playback and focus must match. */
internal object VoiceAudioAttributes {
    val value: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
}
