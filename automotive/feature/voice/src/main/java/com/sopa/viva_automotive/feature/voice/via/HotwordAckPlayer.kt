package com.sopa.viva_automotive.feature.voice.via

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.viva.voice.R as VoiceCoreR
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Plays the bundled “Vi Vi đây” cue when hotword fires successfully.
 */
@Singleton
class HotwordAckPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val lock = Any()
    private var active: MediaPlayer? = null

    /**
     * Plays the wake ack and suspends until completion (or returns immediately if
     * the clip cannot be created). Cancelling the coroutine stops playback.
     */
    suspend fun playAndAwait() = suspendCancellableCoroutine { cont ->
        val resId = VoiceCoreR.raw.hotword_ack_vivi_day
        try {
            stop()
            val player = MediaPlayer.create(
                context,
                resId,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
                /* audioSessionId */ 0,
            )
            if (player == null) {
                Log.w(TAG, "MediaPlayer.create failed for hotword_ack_vivi_day")
                cont.resume(Unit)
                return@suspendCancellableCoroutine
            }
            synchronized(lock) { active = player }
            player.setOnCompletionListener { finished ->
                finished.release()
                synchronized(lock) {
                    if (active === finished) active = null
                }
                if (cont.isActive) cont.resume(Unit)
            }
            player.setOnErrorListener { errored, _, _ ->
                errored.release()
                synchronized(lock) {
                    if (active === errored) active = null
                }
                if (cont.isActive) cont.resume(Unit)
                true
            }
            cont.invokeOnCancellation { stop() }
            player.start()
            Log.i(TAG, "Playing wake ack hotword_ack_vivi_day")
        } catch (error: Exception) {
            Log.w(TAG, "Wake ack playback failed", error)
            if (cont.isActive) cont.resume(Unit)
        }
    }

    fun stop() {
        val player = synchronized(lock) { active.also { active = null } } ?: return
        runCatching {
            if (player.isPlaying) player.stop()
            player.release()
        }
    }

    companion object {
        private const val TAG = "VIVA_VOICE"
    }
}
