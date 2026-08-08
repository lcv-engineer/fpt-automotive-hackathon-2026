package com.viva.voice.agent

import com.viva.voice.asr.AsrResult
import com.viva.voice.asr.FakeAsrClient
import com.viva.voice.intent.GrammarIntentRouter
import com.viva.voice.intent.Intent
import com.viva.voice.trace.LatencyTrace
import com.viva.voice.trace.NanoClock
import com.viva.voice.trace.RecordingTraceSink
import com.viva.voice.trace.Stage
import com.viva.voice.tts.TtsSpeaker
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceAgentTest {

    private class StepClock : NanoClock {
        private var now = 1_000_000_000L
        override fun nanos(): Long = now.also { now += 10_000_000L }
    }

    private class RecordingTts : TtsSpeaker {
        val spoken = mutableListOf<String>()

        override suspend fun speak(text: String, trace: LatencyTrace) {
            spoken += text
            trace.mark(Stage.TTS_START)
        }
    }

    private class FailingTts : TtsSpeaker {
        override suspend fun speak(text: String, trace: LatencyTrace) {
            error("no TTS engine or fallback audio")
        }
    }

    private class FakeGateway(
        private val result: CommandResult,
    ) : CommandGateway {
        var received: Intent? = null

        override suspend fun execute(intent: Intent, trace: LatencyTrace): CommandResult {
            received = intent
            if (result is CommandResult.Applied) trace.mark(Stage.EXEC_DONE)
            return result
        }
    }

    private fun trace() = LatencyTrace(
        traceId = "voice-agent-test",
        clock = StepClock(),
        sink = RecordingTraceSink(),
    )

    @Test
    fun `successful climate command speaks only the verified gateway response`() = runImmediate {
        val tts = RecordingTts()
        val gateway = FakeGateway(
            CommandResult.Applied(
                spokenVi = "Đã đặt nhiệt độ mục tiêu 24°C.",
                hmiPatch = mapOf("climate.temperatureC" to 24f),
            ),
        )
        val agent = VoiceAgent(
            asr = FakeAsrClient(AsrResult("Viva ơi hạ điều hòa xuống 24 độ", 0.97f, 18)),
            router = GrammarIntentRouter(),
            gateway = gateway,
            tts = tts,
        )

        val result = agent.handleAudio(shortArrayOf(1, 2), 16_000, trace())

        assertEquals(VoiceTurnStatus.APPLIED, result.status)
        assertEquals("hvac_set_temp", result.intent?.name)
        assertEquals(24f, result.hmiPatch["climate.temperatureC"])
        assertEquals(listOf("Đã đặt nhiệt độ mục tiêu 24°C."), tts.spoken)
    }

    @Test
    fun `ambiguous cold complaint asks a question and never reaches the car gateway`() = runImmediate {
        val tts = RecordingTts()
        val gateway = FakeGateway(CommandResult.Failed("must not execute"))
        val agent = VoiceAgent(
            asr = FakeAsrClient(AsrResult("lạnh quá", 0.95f, 12)),
            router = GrammarIntentRouter(),
            gateway = gateway,
            tts = tts,
        )

        val result = agent.handleAudio(shortArrayOf(1), 16_000, trace())

        assertEquals(VoiceTurnStatus.NEEDS_CLARIFICATION, result.status)
        assertNull(gateway.received)
        assertEquals("Bạn muốn tăng nhiệt độ điều hòa lên bao nhiêu độ?", result.spokenVi)
    }

    @Test
    fun `safety denial is spoken instead of a false success`() = runImmediate {
        val tts = RecordingTts()
        val gateway = FakeGateway(
            CommandResult.Denied(
                rule = "G1_SPEED_LOCK",
                reasonVi = "Xe đang chạy, mình chưa mở cửa được.",
            ),
        )
        val agent = VoiceAgent(
            asr = FakeAsrClient(AsrResult("mở cửa", 0.94f, 11)),
            router = GrammarIntentRouter(),
            gateway = gateway,
            tts = tts,
        )

        val result = agent.handleAudio(shortArrayOf(1), 16_000, trace())

        assertEquals(VoiceTurnStatus.DENIED, result.status)
        assertEquals("Xe đang chạy, mình chưa mở cửa được.", result.spokenVi)
        assertEquals(listOf(result.spokenVi), tts.spoken)
    }

    @Test
    fun `gateway failure never produces the planned success sentence`() = runImmediate {
        val tts = RecordingTts()
        val agent = VoiceAgent(
            asr = FakeAsrClient(AsrResult("hạ điều hòa xuống 24 độ", 0.96f, 10)),
            router = GrammarIntentRouter(),
            gateway = FakeGateway(CommandResult.Failed("VivaCarService chưa kết nối")),
            tts = tts,
        )

        val result = agent.handleAudio(shortArrayOf(1), 16_000, trace())

        assertEquals(VoiceTurnStatus.FAILED, result.status)
        assertTrue(result.spokenVi.contains("chưa thực hiện được"))
        assertTrue(tts.spoken.none { it.startsWith("Đã ") })
    }

    @Test
    fun `gateway exception is contained and reported as a failed turn`() = runImmediate {
        val tts = RecordingTts()
        val agent = VoiceAgent(
            asr = FakeAsrClient(AsrResult("hạ điều hòa xuống 24 độ", 0.96f, 10)),
            router = GrammarIntentRouter(),
            gateway = object : CommandGateway {
                override suspend fun execute(
                    intent: Intent,
                    trace: LatencyTrace,
                ): CommandResult = error("binder died")
            },
            tts = tts,
        )

        val result = agent.handleAudio(shortArrayOf(1), 16_000, trace())

        assertEquals(VoiceTurnStatus.FAILED, result.status)
        assertTrue(tts.spoken.none { it.startsWith("Đã ") })
    }

    @Test
    fun `an engine that reports no confidence is not treated as a bad hearing`() = runImmediate {
        val gateway = FakeGateway(CommandResult.Applied("Đã đặt nhiệt độ mục tiêu 24°C."))
        val agent = VoiceAgent(
            asr = FakeAsrClient(
                AsrResult("hạ điều hòa xuống 24 độ", acousticConfidence = null, serverMs = 12),
            ),
            router = GrammarIntentRouter(),
            gateway = gateway,
            tts = RecordingTts(),
        )

        val result = agent.handleAudio(shortArrayOf(1), 16_000, trace())

        // Vosk small không trả confidence. Bắt lượt đó hỏi lại vô điều kiện thì trợ lý
        // không bao giờ thực thi được lệnh nào trên bản offline.
        assertEquals(VoiceTurnStatus.APPLIED, result.status)
        assertEquals("hvac_set_temp", gateway.received?.name)
    }

    @Test
    fun `a measured low confidence still asks the driver to repeat`() = runImmediate {
        val gateway = FakeGateway(CommandResult.Applied("must not execute"))
        val agent = VoiceAgent(
            asr = FakeAsrClient(
                AsrResult("mở cửa", acousticConfidence = 0.2f, serverMs = 12),
            ),
            router = GrammarIntentRouter(),
            gateway = gateway,
            tts = RecordingTts(),
        )

        val result = agent.handleAudio(shortArrayOf(1), 16_000, trace())

        assertEquals(VoiceTurnStatus.NEEDS_CLARIFICATION, result.status)
        assertNull(gateway.received)
    }

    @Test
    fun `acoustic confidence never overwrites the NLU confidence on the intent`() = runImmediate {
        val gateway = FakeGateway(CommandResult.Applied("Đã đặt nhiệt độ mục tiêu 24°C."))
        val agent = VoiceAgent(
            asr = FakeAsrClient(
                AsrResult("hạ điều hòa xuống 24 độ", acousticConfidence = 0.7f, serverMs = 9),
            ),
            router = GrammarIntentRouter(),
            gateway = gateway,
            tts = RecordingTts(),
        )

        agent.handleAudio(shortArrayOf(1), 16_000, trace())

        // Grammar T0 khớp tất định — độ chắc của NLU là 1.0 và không liên quan tới
        // việc mic hôm nay ồn tới đâu.
        assertEquals(1f, gateway.received?.confidence)
        assertEquals(Intent.Tier.T0, gateway.received?.tier)
    }

    @Test
    fun `text entry point lets Duong integrate before microphone and ASR are ready`() = runImmediate {
        val tts = RecordingTts()
        val gateway = FakeGateway(CommandResult.Applied("Đã chuyển bài.", emptyMap()))
        val agent = VoiceAgent(
            asr = FakeAsrClient(),
            router = GrammarIntentRouter(),
            gateway = gateway,
            tts = tts,
        )

        val result = agent.handleText("chuyển bài", trace())

        assertEquals(VoiceTurnStatus.APPLIED, result.status)
        assertEquals("media_next", gateway.received?.name)
    }

    @Test
    fun `tts failure preserves an already applied command and its HMI text`() = runImmediate {
        val response = "Đã đặt nhiệt độ mục tiêu 24°C."
        val agent = VoiceAgent(
            asr = FakeAsrClient(),
            router = GrammarIntentRouter(),
            gateway = FakeGateway(
                CommandResult.Applied(
                    response,
                    mapOf("climate.temperatureC" to 24f),
                ),
            ),
            tts = FailingTts(),
        )

        val result = agent.handleText("hạ điều hòa xuống 24 độ", trace())

        assertEquals(VoiceTurnStatus.APPLIED, result.status)
        assertEquals(response, result.spokenVi)
        assertEquals(24f, result.hmiPatch["climate.temperatureC"])
    }

    private fun <T> runImmediate(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) {
                outcome = result
            }
        })
        return outcome?.getOrThrow()
            ?: error("Test fake suspended; runImmediate only supports immediate fakes")
    }
}
