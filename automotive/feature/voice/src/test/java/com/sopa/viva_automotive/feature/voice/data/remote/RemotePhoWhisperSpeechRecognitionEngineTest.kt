package com.sopa.viva_automotive.feature.voice.data.remote

import com.sopa.viva_automotive.feature.voice.data.TranscriptionEvent
import com.viva.voice.audio.PcmFrame
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RemotePhoWhisperSpeechRecognitionEngineTest {

    @Test
    fun `endpoint sends accumulated PCM once and exposes server confidence`() = runTest {
        val transport = RecordingTransport(
            RemoteAsrResponse(
                text = "hạ điều hòa xuống 24 độ",
                confidence = 0.87f,
                serverMs = 321,
            ),
        )
        lateinit var engine: RemotePhoWhisperSpeechRecognitionEngine
        engine = RemotePhoWhisperSpeechRecognitionEngine(transport, UnconfinedTestDispatcher(testScheduler))
        val frames = flow {
            emit(frame(shortArrayOf(1, -2), startSample = 0))
            engine.requestEndOfUtterance()
            emit(frame(shortArrayOf(3, 4), startSample = 2))
        }

        val events = engine.transcribe(frames).toList()

        assertEquals(1, transport.calls.size)
        assertArrayEquals(shortArrayOf(1, -2), transport.calls.single().pcm16)
        assertEquals(16_000, transport.calls.single().sampleRate)
        assertEquals(TranscriptionEvent.RequestStarted, events.first())
        assertEquals(
            TranscriptionEvent.Final(
                text = "hạ điều hòa xuống 24 độ",
                acousticConfidence = 0.87f,
                engineMs = 321,
            ),
            events.last(),
        )
    }

    @Test
    fun `blank remote transcript is reported as no speech`() = runTest {
        val engine = RemotePhoWhisperSpeechRecognitionEngine(
            RecordingTransport(RemoteAsrResponse("  ", 0f, 11)),
            UnconfinedTestDispatcher(testScheduler),
        )

        val event = engine.transcribe(flow { emit(frame(shortArrayOf(7, 8), 0)) }).toList().last()

        assertTrue(event is TranscriptionEvent.Error)
        assertEquals(TranscriptionEvent.CODE_NO_SPEECH, (event as TranscriptionEvent.Error).code)
    }

    @Test
    fun `PCM body is signed 16 bit little endian without a WAV header`() {
        val bytes = encodePcm16Le(shortArrayOf(0x1234, -2))

        assertArrayEquals(
            byteArrayOf(0x34, 0x12, 0xFE.toByte(), 0xFF.toByte()),
            bytes,
        )
    }

    @Test
    fun `invalid confidence from remote is rejected instead of entering safety policy`() = runTest {
        val engine = RemotePhoWhisperSpeechRecognitionEngine(
            RecordingTransport(RemoteAsrResponse("mở cửa", 1.4f, 10)),
            UnconfinedTestDispatcher(testScheduler),
        )

        val event = engine.transcribe(flow { emit(frame(shortArrayOf(1), 0)) }).toList().last()

        assertTrue(event is TranscriptionEvent.Error)
        assertEquals(TranscriptionEvent.CODE_ENGINE_FAILED, (event as TranscriptionEvent.Error).code)
    }

    @Test
    fun `cleartext remote URL is accepted only for loopback adb reverse`() {
        HttpRemoteAsrTransport("http://127.0.0.1:8080", StandardTestDispatcher())

        assertThrows(IllegalArgumentException::class.java) {
            HttpRemoteAsrTransport("http://10.0.2.2:8080", StandardTestDispatcher())
        }
    }

    @Test
    fun `remote unavailable remains a machine readable ASR error`() = runTest {
        val engine = RemotePhoWhisperSpeechRecognitionEngine(
            RemoteAsrTransport { _, _ ->
                throw RemoteAsrException(
                    TranscriptionEvent.CODE_MODEL_UNAVAILABLE,
                    "service loading",
                )
            },
            UnconfinedTestDispatcher(testScheduler),
        )

        val event = engine.transcribe(flow { emit(frame(shortArrayOf(1), 0)) }).toList().last()

        assertTrue(event is TranscriptionEvent.Error)
        assertEquals(
            TranscriptionEvent.CODE_MODEL_UNAVAILABLE,
            (event as TranscriptionEvent.Error).code,
        )
    }

    @Test
    fun `wrong sample rate is rejected before audio crosses the network boundary`() = runTest {
        val transport = RecordingTransport(RemoteAsrResponse("khóa cửa", 0.9f, 10))
        val engine = RemotePhoWhisperSpeechRecognitionEngine(transport, UnconfinedTestDispatcher(testScheduler))

        val event = engine.transcribe(
            flow {
                emit(
                    PcmFrame(
                        samples = shortArrayOf(1, 2),
                        sampleRate = 8_000,
                        startNanos = 0,
                        startSample = 0,
                    ),
                )
            },
        ).toList().single()

        assertTrue(event is TranscriptionEvent.Error)
        assertEquals(TranscriptionEvent.CODE_ENGINE_FAILED, (event as TranscriptionEvent.Error).code)
        assertTrue(transport.calls.isEmpty())
    }

    private fun frame(samples: ShortArray, startSample: Long) = PcmFrame(
        samples = samples,
        startNanos = startSample * 62_500,
        startSample = startSample,
    )

    private class RecordingTransport(
        private val response: RemoteAsrResponse,
    ) : RemoteAsrTransport {
        val calls = mutableListOf<Call>()

        override suspend fun transcribe(pcm16: ShortArray, sampleRate: Int): RemoteAsrResponse {
            calls += Call(pcm16.copyOf(), sampleRate)
            return response
        }
    }

    private data class Call(val pcm16: ShortArray, val sampleRate: Int)
}
