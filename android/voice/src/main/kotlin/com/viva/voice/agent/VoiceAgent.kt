package com.viva.voice.agent

import com.viva.voice.asr.AsrClient
import com.viva.voice.intent.Intent
import com.viva.voice.intent.IntentRouter
import com.viva.voice.intent.NegationGate
import com.viva.voice.intent.NegationVerdict
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
    private val planner: AgentPlanner = AgentPlanner { _, _ ->
        AgentPlanResult.Unavailable("agent planner disabled")
    },
    /** Invoked after the turn is decided and before TTS, so HMI can show spoken copy with audio. */
    private val onResultReady: suspend (VoiceTurnResult) -> Unit = {},
) {

    /**
     * Ngữ cảnh hội thoại duy nhất được giữ: tiền tố để nối câu trả lời của
     * lượt sau. Cố ý hẹp — nó chỉ sống đúng một lượt và bị xoá ngay khi
     * lượt đó ra quyết định, nên không có trạng thái nào tồn đọng giữa các
     * phiên nói để đẻ ra lệnh bất ngờ.
     */
    private var pendingResumePrefix: String? = null

    suspend fun handleAudio(
        pcm16: ShortArray,
        sampleRate: Int,
        trace: LatencyTrace,
    ): VoiceTurnResult {
        val recognised = try {
            asr.transcribe(pcm16, sampleRate, trace)
        } catch (error: Exception) {
            // Keep spoken copy user-facing; log the real ASR failure (e.g. viva-asr down /
            // missing `adb reverse tcp:8080 tcp:8080`) so logcat is not just "unknown".
            System.out.println("VIVA_VOICE|asr_exception|${error.javaClass.simpleName}|${error.message}")
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
        // Cổng phủ định đứng trước router: router khớp bằng `contains()` nên
        // "đừng mở cửa" chứa "mo cua" và mở khóa cửa thật. Đứng sau router là
        // đã muộn — lệnh đã thành `Matched`. Planner cũng không được hỏi: câu
        // phủ định không phải câu cần suy luận thêm.
        when (val negation = NegationGate.inspect(text)) {
            is NegationVerdict.Negated -> {
                trace.mark(Stage.NLU_DONE)
                return finish(
                    VoiceTurnResult(
                        transcript = text,
                        intent = null,
                        status = VoiceTurnStatus.NEEDS_CLARIFICATION,
                        spokenVi = negation.promptVi,
                    ),
                    TraceVerdict.Confirm(NEGATION_RULE),
                    trace,
                )
            }

            NegationVerdict.None -> Unit
        }

        // Lượt trước đã hỏi lại và câu này có thể là câu trả lời. Thử đúng
        // văn bản người dùng nói trước — một lệnh đầy đủ luôn thắng ngữ cảnh
        // đang chờ. Chỉ khi nó không tự đứng được mới ghép tiền tố rồi cho
        // chạy lại qua CHÍNH router đó: không có bộ phân tích số thứ hai.
        val pending = pendingResumePrefix
        val direct = router.route(text)
        val route = if (direct is RouteResult.Unsupported && pending != null) {
            router.route("$pending $text").takeIf { it is RouteResult.Matched } ?: direct
        } else {
            direct
        }
        pendingResumePrefix = (route as? RouteResult.NeedsClarification)?.resumePrefix

        return when (route) {
            is RouteResult.NeedsClarification -> {
                trace.mark(Stage.NLU_DONE)
                finish(
                    VoiceTurnResult(
                        transcript = text,
                        intent = null,
                        status = VoiceTurnStatus.NEEDS_CLARIFICATION,
                        spokenVi = route.promptVi,
                    ),
                    TraceVerdict.Confirm(route.rule),
                    trace,
                )
            }

            is RouteResult.Unsupported -> {
                if (route.canFallback) {
                    handleAgentFallback(text, route, trace)
                } else {
                    trace.mark(Stage.NLU_DONE)
                    finishUnsupported(text, route.promptVi, route.rule, trace)
                }
            }

            // `route.intent.confidence` là độ chắc của NLU và được giữ nguyên. Bản cũ
            // nhân nó với confidence của ASR, biến một trường thành hai ý nghĩa: sau
            // đó không ai đọc được con số trên trace là router thiếu chắc hay mic ồn.
            // §4: không dùng cùng một trường confidence cho cả ASR và NLU.
            is RouteResult.Matched -> {
                trace.mark(Stage.NLU_DONE)
                execute(
                    transcript = text,
                    intent = route.intent,
                    trace = trace,
                )
            }
        }
    }

    private suspend fun handleAgentFallback(
        text: String,
        grammarResult: RouteResult.Unsupported,
        trace: LatencyTrace,
    ): VoiceTurnResult {
        val plan = try {
            planner.plan(text, trace.traceId)
        } catch (error: Exception) {
            AgentPlanResult.Unavailable(error.message ?: "agent planner failed")
        }
        trace.mark(Stage.NLU_DONE)
        return when (plan) {
            is AgentPlanResult.Action -> {
                if (plan.intent.tier != Intent.Tier.T2) {
                    finishUnsupported(text, grammarResult.promptVi, grammarResult.rule, trace)
                } else {
                    execute(text, plan.intent, trace)
                }
            }

            is AgentPlanResult.Clarification -> finish(
                VoiceTurnResult(
                    transcript = text,
                    intent = null,
                    status = VoiceTurnStatus.NEEDS_CLARIFICATION,
                    spokenVi = plan.promptVi,
                ),
                TraceVerdict.Confirm(AGENT_CLARIFICATION_RULE),
                trace,
            )

            is AgentPlanResult.Unsupported ->
                finishUnsupported(text, plan.promptVi, AGENT_UNSUPPORTED_RULE, trace)

            is AgentPlanResult.Unavailable ->
                finishUnsupported(text, grammarResult.promptVi, grammarResult.rule, trace)
        }
    }

    private suspend fun finishUnsupported(
        transcript: String,
        promptVi: String,
        rule: String,
        trace: LatencyTrace,
    ): VoiceTurnResult = finish(
        VoiceTurnResult(
            transcript = transcript,
            intent = null,
            status = VoiceTurnStatus.UNSUPPORTED,
            spokenVi = promptVi,
        ),
        TraceVerdict.Deny(rule),
        trace,
    )

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
            onResultReady(result)
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
        private const val NEGATION_RULE = "N1_NEGATION"
        private const val AGENT_CLARIFICATION_RULE = "G3_AGENT_CLARIFY"
        private const val AGENT_UNSUPPORTED_RULE = "G3_AGENT_UNSUPPORTED"
    }
}
