package com.sopa.viva_automotive.feature.voice.data.media

import android.content.ComponentName
import android.content.Context
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import com.sopa.viva_automotive.feature.voice.domain.media.MediaCommand
import com.sopa.viva_automotive.feature.voice.domain.media.MediaCommandDispatcher
import com.sopa.viva_automotive.feature.voice.domain.media.MediaCommandExecutor
import com.sopa.viva_automotive.feature.voice.domain.media.MediaTransportControls
import com.sopa.viva_automotive.feature.voice.domain.media.MediaTransportException
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Media client for the voice Agent.
 *
 * The concrete media feature stays behind the standard Android media-session
 * boundary. Splitting it into another APK later only changes [ComponentName],
 * not the voice-domain contract or command dispatch.
 */
@Singleton
class AndroidMediaCommandExecutor @Inject constructor(
    @ApplicationContext context: Context,
) : MediaCommandExecutor {

    private val appContext = context.applicationContext

    override suspend fun execute(command: MediaCommand): Result<String> =
        withContext(Dispatchers.Main.immediate) {
            val outcome = CompletableDeferred<Result<String>>()
            var browser: MediaBrowserCompat? = null
            val callback = object : MediaBrowserCompat.ConnectionCallback() {
                override fun onConnected() {
                    if (!outcome.isActive) return
                    val connectedBrowser = browser
                    if (connectedBrowser == null) {
                        outcome.complete(Result.failure(unavailable()))
                        return
                    }
                    outcome.complete(
                        runCatching {
                            val transport = MediaControllerCompat(
                                appContext,
                                connectedBrowser.sessionToken,
                            ).transportControls
                            MediaCommandDispatcher.dispatch(
                                command,
                                object : MediaTransportControls {
                                    override fun play() = transport.play()

                                    override fun pause() = transport.pause()

                                    override fun skipToNext() = transport.skipToNext()
                                },
                            )
                        }.recoverCatching { error ->
                            throw unavailable(error)
                        },
                    )
                }

                override fun onConnectionSuspended() {
                    if (outcome.isActive) {
                        outcome.complete(Result.failure(unavailable()))
                    }
                }

                override fun onConnectionFailed() {
                    if (outcome.isActive) {
                        outcome.complete(Result.failure(unavailable()))
                    }
                }
            }

            try {
                browser = MediaBrowserCompat(
                    appContext,
                    ComponentName(appContext.packageName, MEDIA_SERVICE_CLASS),
                    callback,
                    null,
                )
                browser.connect()
                withTimeout(CONNECTION_TIMEOUT_MS) { outcome.await() }
            } catch (_: TimeoutCancellationException) {
                Result.failure(unavailable())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Result.failure(unavailable(error))
            } finally {
                outcome.cancel()
                runCatching { browser?.disconnect() }
            }
        }

    private fun unavailable(cause: Throwable? = null): MediaTransportException =
        MediaTransportException(
            "Mình chưa kết nối được trình phát nhạc. Bạn thử lại giúp mình nhé.",
            cause,
        )

    private companion object {
        const val CONNECTION_TIMEOUT_MS = 3_000L
        const val MEDIA_SERVICE_CLASS =
            "com.sopa.viva_automotive.feature.media.service.VivaMediaBrowserService"
    }
}
