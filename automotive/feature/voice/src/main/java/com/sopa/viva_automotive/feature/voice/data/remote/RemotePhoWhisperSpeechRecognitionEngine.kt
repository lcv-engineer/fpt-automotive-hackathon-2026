package com.sopa.viva_automotive.feature.voice.data.remote

import com.sopa.viva_automotive.feature.voice.data.SpeechRecognitionEngine
import com.sopa.viva_automotive.feature.voice.data.TranscriptionEvent
import com.viva.voice.audio.PcmFrame
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.takeWhile

data class RemoteAsrResponse(
    val text: String,
    val confidence: Float,
    val serverMs: Int,
)

fun interface RemoteAsrTransport {
    suspend fun transcribe(pcm16: ShortArray, sampleRate: Int): RemoteAsrResponse
}

/**
 * Adapter cho `viva-asr`/PhoWhisper. Engine chỉ gom chính dòng PCM mà VAD đang
 * quan sát; nó không mở microphone và không tự chạy endpointer thứ hai.
 *
 * PhoWhisper hiện là endpoint theo utterance, nên không phát
 * partial. Chỉ kết quả cuối mới đi xuống NLU, đúng contract 28-PIPELINE §2.4.
 */
class RemotePhoWhisperSpeechRecognitionEngine(
    private val transport: RemoteAsrTransport,
    private val ioDispatcher: CoroutineDispatcher,
) : SpeechRecognitionEngine {

    override val sendsAudioWhileCapturing: Boolean = false

    @Volatile
    private var endOfUtteranceRequested = false

    override suspend fun initialize(): Result<Unit> = Result.success(Unit)

    override fun requestEndOfUtterance() {
        endOfUtteranceRequested = true
    }

    override fun transcribe(frames: Flow<PcmFrame>): Flow<TranscriptionEvent> = flow {
        endOfUtteranceRequested = false
        val chunks = mutableListOf<ShortArray>()
        var sampleRate: Int? = null
        var sampleCount = 0

        val frameError = runCatching {
            frames.takeWhile { !endOfUtteranceRequested }.collect { frame ->
                require(frame.sampleRate == REQUIRED_SAMPLE_RATE) {
                    "remote ASR requires $REQUIRED_SAMPLE_RATE Hz, got ${frame.sampleRate}"
                }
                val expectedRate = sampleRate
                if (expectedRate == null) {
                    sampleRate = frame.sampleRate
                } else {
                    require(frame.sampleRate == expectedRate) {
                        "sample rate changed inside one utterance: $expectedRate -> ${frame.sampleRate}"
                    }
                }
                chunks += frame.samples.copyOf()
                sampleCount += frame.samples.size
            }
        }.exceptionOrNull()
        if (frameError != null) {
            emit(
                TranscriptionEvent.Error(
                    code = TranscriptionEvent.CODE_ENGINE_FAILED,
                    diagnostic = frameError.message ?: "invalid PCM frame stream",
                    cause = frameError,
                ),
            )
            return@flow
        }

        if (sampleCount == 0 || sampleRate == null) {
            emit(
                TranscriptionEvent.Error(
                    code = TranscriptionEvent.CODE_NO_SPEECH,
                    diagnostic = "remote ASR received no PCM before the endpoint",
                ),
            )
            return@flow
        }

        val pcm16 = ShortArray(sampleCount)
        var offset = 0
        for (chunk in chunks) {
            chunk.copyInto(pcm16, destinationOffset = offset)
            offset += chunk.size
        }

        emit(TranscriptionEvent.RequestStarted)
        val event = runCatching { transport.transcribe(pcm16, sampleRate!!).toEvent() }
            .fold(
                onSuccess = { it },
                onFailure = { error ->
                    val code = (error as? RemoteAsrException)?.code
                        ?: TranscriptionEvent.CODE_ENGINE_FAILED
                    TranscriptionEvent.Error(
                        code = code,
                        diagnostic = error.message ?: error::class.java.simpleName,
                        cause = error,
                    )
                },
            )
        emit(event)
    }
        // `flowOn` chi phối cả thượng nguồn, tức chính `audioCapture.frames()`. Thiếu
        // dòng này thì `AudioRecord.read()` — một lệnh CHẶN — chạy trên luồng gọi, mà
        // `VoiceAssistantService.startPipeline` chạy trên `lifecycleScope`, tức Main.
        //
        // Hậu quả đo được 09/08 trên emulator: bấm mic là UI đơ suốt phiên nghe, và
        // Android giết app sau 5s vì "Input dispatching timed out" — nhìn từ ngoài
        // giống hệt crash liên tục, nhưng crash buffer rỗng vì đây là ANR.
        //
        // `VoskSpeechRecognitionEngine` đã có đúng dòng này (`:174`), nên nhánh vosk
        // không dính. Lỗi chỉ nằm ở đây và chỉ lộ ra khi thật sự chạy engine remote.
        .flowOn(ioDispatcher)

    private fun RemoteAsrResponse.toEvent(): TranscriptionEvent {
        require(confidence.isFinite() && confidence in 0f..1f) {
            "remote confidence must be finite and between 0 and 1"
        }
        require(serverMs >= 0) { "remote server_ms must not be negative" }
        val cleanText = text.trim()
        require(cleanText.length <= MAX_TRANSCRIPT_CHARS) {
            "remote transcript exceeds $MAX_TRANSCRIPT_CHARS characters"
        }
        return if (cleanText.isEmpty()) {
            TranscriptionEvent.Error(
                code = TranscriptionEvent.CODE_NO_SPEECH,
                diagnostic = "PhoWhisper returned a blank transcript",
            )
        } else {
            TranscriptionEvent.Final(
                text = cleanText,
                acousticConfidence = confidence,
                engineMs = serverMs,
            )
        }
    }

    private companion object {
        const val REQUIRED_SAMPLE_RATE = 16_000
        const val MAX_TRANSCRIPT_CHARS = 1_000
    }
}

class RemoteAsrException(
    val code: String,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

internal fun encodePcm16Le(samples: ShortArray): ByteArray {
    val bytes = ByteArray(samples.size * 2)
    samples.forEachIndexed { index, sample ->
        val value = sample.toInt()
        bytes[index * 2] = (value and 0xFF).toByte()
        bytes[index * 2 + 1] = (value ushr 8 and 0xFF).toByte()
    }
    return bytes
}
