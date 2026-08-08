package com.viva.voice.agent

import com.viva.voice.asr.AsrClient
import com.viva.voice.intent.Intent
import com.viva.voice.intent.IntentRouter
import com.viva.voice.intent.RouteResult
import com.viva.voice.trace.LatencyTrace
import com.viva.voice.trace.Stage
import com.viva.voice.trace.TraceVerdict
import com.viva.voice.tts.TtsSpeaker

fun interface CommandGateway {
    suspend fun execute(intent: Intent, trace: LatencyTrace): CommandResult
}

sealed class CommandResult {
    data class Applied(
        val spokenVi: String,
        val hmiPatch: Map<String, Any> = emptyMap(),
    ) : CommandResult() {
        init {
            require(spokenVi.isNotBlank()) { "Applied result needs a verified spoken response" }
        }
    }

    data class Denied(val rule: String, val reasonVi: String) : CommandResult()
    data class ConfirmationRequired(val rule: String, val questionVi: String) : CommandResult()
    data class Failed(val diagnostic: String) : CommandResult()
}

enum class VoiceTurnStatus {
    APPLIED,
    DENIED,
    NEEDS_CONFIRMATION,
    NEEDS_CLARIFICATION,
    UNSUPPORTED,
    FAILED,
}

data class VoiceTurnResult(
    val transcript: String,
    val intent: Intent?,
    val status: VoiceTurnStatus,
    val spokenVi: String,
    val hmiPatch: Map<String, Any> = emptyMap(),
)

/** Orchestrates one voice turn without depending on an Activity or ViewModel. */
class VoiceAgent(
    private val asr: AsrClient,
    private val router: IntentRouter,
    private val gateway: CommandGateway,
    private val tts: TtsSpeaker,
) {

    suspend fun handleAudio(
        pcm16: ShortArray,
        sampleRate: Int,
        trace: LatencyTrace,
    ): VoiceTurnResult {
        val recognised = try {
            asr.transcribe(pcm16, sampleRate, trace)
        } catch (_: Exception) {
            return finish(
                VoiceTurnResult(
                    transcript = "",
                    intent = null,
                    status = VoiceTurnStatus.FAILED,
                    spokenVi = "Mình chưa nghe rõ. Bạn thử lại giúp mình nhé.",
                ),
                TraceVerdict.Error(Stage.ASR_DONE),
                trace,
            )
        }

        // `Partial` chỉ để hiển thị (§2.4) và câu rỗng thì không có gì để định tuyến.
        // Luật low-confidence chỉ chạy khi engine **thật sự** đưa ra một con số:
        // `acousticConfidence == null` nghĩa là không đo được, và một lượt không đo
        // được không phải là một lượt nghe kém.
        val tooQuiet = recognised.acousticConfidence?.let { it < MIN_ASR_CONFIDENCE } == true
        if (recognised.isPartial || recognised.text.isBlank() || tooQuiet) {
            return finish(
                VoiceTurnResult(
                    transcript = recognised.text,
                    intent = null,
                    status = VoiceTurnStatus.NEEDS_CLARIFICATION,
                    spokenVi = "Mình chưa nghe rõ. Bạn nói lại giúp mình nhé.",
                ),
                TraceVerdict.Confirm("G3_LOW_CONFIDENCE"),
                trace,
            )
        }
        return handleRecognisedText(recognised.text, trace)
    }

    /**
     * Entry point for text already available (benchmark inject, typed HMI).
     *
     * No audio means **no** acoustic confidence — so this path must not be used
     * for WER or any ASR quality claim.
     */
    suspend fun handleText(text: String, trace: LatencyTrace): VoiceTurnResult =
        handleRecognisedText(text, trace)

    private suspend fun handleRecognisedText(
        text: String,
        trace: LatencyTrace,
    ): VoiceTurnResult {
        val route = router.route(text)
        trace.mark(Stage.NLU_DONE)
        return when (route) {
            is RouteResult.NeedsClarification -> finish(
                VoiceTurnResult(
                    transcript = text,
                    intent = null,
                    status = VoiceTurnStatus.NEEDS_CLARIFICATION,
                    spokenVi = route.promptVi,
                ),
                TraceVerdict.Confirm(route.rule),
                trace,
            )

            is RouteResult.Unsupported -> finish(
                VoiceTurnResult(
                    transcript = text,
                    intent = null,
                    status = VoiceTurnStatus.UNSUPPORTED,
                    spokenVi = route.promptVi,
                ),
                TraceVerdict.Deny(route.rule),
                trace,
            )

            // `route.intent.confidence` là độ chắc của NLU và được giữ nguyên. Bản cũ
            // nhân nó với confidence của ASR, biến một trường thành hai ý nghĩa: sau
            // đó không ai đọc được con số trên trace là router thiếu chắc hay mic ồn.
            // §4: không dùng cùng một trường confidence cho cả ASR và NLU.
            is RouteResult.Matched -> execute(
                transcript = text,
                intent = route.intent,
                trace = trace,
            )
        }
    }

    private suspend fun execute(
        transcript: String,
        intent: Intent,
        trace: LatencyTrace,
    ): VoiceTurnResult {
        val gatewayResult = try {
            gateway.execute(intent, trace)
        } catch (error: Exception) {
            CommandResult.Failed(error.message ?: "command gateway failed")
        }
        return when (val result = gatewayResult) {
            is CommandResult.Applied -> finish(
                VoiceTurnResult(
                    transcript = transcript,
                    intent = intent,
                    status = VoiceTurnStatus.APPLIED,
                    spokenVi = result.spokenVi,
                    hmiPatch = result.hmiPatch,
                ),
                TraceVerdict.Allow,
                trace,
            )

            is CommandResult.Denied -> finish(
                VoiceTurnResult(
                    transcript = transcript,
                    intent = intent,
                    status = VoiceTurnStatus.DENIED,
                    spokenVi = result.reasonVi,
                ),
                TraceVerdict.Deny(result.rule),
                trace,
            )

            is CommandResult.ConfirmationRequired -> finish(
                VoiceTurnResult(
                    transcript = transcript,
                    intent = intent,
                    status = VoiceTurnStatus.NEEDS_CONFIRMATION,
                    spokenVi = result.questionVi,
                ),
                TraceVerdict.Confirm(result.rule),
                trace,
            )

            is CommandResult.Failed -> finish(
                VoiceTurnResult(
                    transcript = transcript,
                    intent = intent,
                    status = VoiceTurnStatus.FAILED,
                    spokenVi = "Mình chưa thực hiện được yêu cầu. Bạn thử lại giúp mình nhé.",
                ),
                TraceVerdict.Error(Stage.EXEC_DONE),
                trace,
            )
        }
    }

    private suspend fun finish(
        result: VoiceTurnResult,
        verdict: TraceVerdict,
        trace: LatencyTrace,
    ): VoiceTurnResult {
        return try {
            tts.speak(result.spokenVi, trace)
            trace.summary(result.transcript, result.intent?.name ?: "unknown", verdict)
            result
        } catch (_: Exception) {
            trace.summary(
                result.transcript,
                result.intent?.name ?: "unknown",
                TraceVerdict.Error(Stage.TTS_START),
            )
            result
        }
    }

    companion object {
        private const val MIN_ASR_CONFIDENCE = 0.6f
    }
}
