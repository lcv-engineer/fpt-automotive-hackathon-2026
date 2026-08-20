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

    private class RecordingPlanner(
        private val result: AgentPlanResult,
    ) : AgentPlanner {
        val received = mutableListOf<String>()

        override suspend fun plan(text: String, traceId: String): AgentPlanResult {
            received += text
            return result
        }
    }

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

    private class SequencedGateway(
        private val results: List<CommandResult>,
    ) : CommandGateway {
        val received = mutableListOf<Intent>()

        override suspend fun execute(intent: Intent, trace: LatencyTrace): CommandResult {
            received += intent
            return results[received.lastIndex]
        }
    }

    private fun trace(id: String = "t-1") =
        LatencyTrace(id, StepClock(), RecordingTraceSink())

    @Test
    fun `happy path handles audio produces spoken response and updates HMI`() = runImmediate {
        val tts = RecordingTts()
        val gateway = FakeGateway(
            CommandResult.Applied(
                spokenVi = "Đã đặt nhiệt độ mục tiêu 24°C.",
                hmiPatch = mapOf("climate.temperatureC" to 24f),
            ),
        )
        val agent = VoiceAgent(
            asr = FakeAsrClient(AsrResult("hạ điều hòa xuống 24 độ", 0.98f, 12)),
            router = GrammarIntentRouter(),
            gateway = gateway,
            tts = tts,
        )

        val result = agent.handleAudio(shortArrayOf(1), 16_000, trace())

        assertEquals(VoiceTurnStatus.APPLIED, result.status)
        assertEquals("hvac_set_temp", gateway.received?.name)
        assertEquals(24f, gateway.received?.slots?.get("value"))
        assertEquals("Đã đặt nhiệt độ mục tiêu 24°C.", result.spokenVi)
        assertEquals(24f, result.hmiPatch["climate.temperatureC"])
        assertEquals(listOf("Đã đặt nhiệt độ mục tiêu 24°C."), tts.spoken)
    }

    /**
     * Khoảnh khắc ④ của kịch bản: "nóng quá" → hỏi lại → "hai hai độ" → thực hiện.
     * Trước đây lượt 2 rơi thẳng xuống `Unsupported` vì `VoiceAgent` không giữ
     * trạng thái nào, nên câu hỏi lại là một ngõ cụt.
     */
    @Test
    fun `answer to a clarification completes the pending command`() = runImmediate {
        val gateway = FakeGateway(CommandResult.Applied("Đã đặt nhiệt độ mục tiêu 22°C.", emptyMap()))
        val agent = VoiceAgent(
            asr = FakeAsrClient(AsrResult("", 0f, 0)),
            router = GrammarIntentRouter(),
            gateway = gateway,
            tts = RecordingTts(),
        )

        val first = agent.handleText("Vivi ơi nóng quá", trace())
        assertEquals(VoiceTurnStatus.NEEDS_CLARIFICATION, first.status)
        assertNull(gateway.received)

        val second = agent.handleText("hai hai độ", trace())

        assertEquals(VoiceTurnStatus.APPLIED, second.status)
        assertEquals("hvac_set_temp", gateway.received?.name)
        assertEquals(22f, gateway.received?.slots?.get("value"))
    }

    @Test
    fun `pending clarification is dropped once it is answered`() = runImmediate {
        val gateway = FakeGateway(CommandResult.Applied("ok", emptyMap()))
        val agent = VoiceAgent(
            asr = FakeAsrClient(AsrResult("", 0f, 0)),
            router = GrammarIntentRouter(),
            gateway = gateway,
            tts = RecordingTts(),
        )

        agent.handleText("Vivi ơi nóng quá", trace())
        agent.handleText("hai hai độ", trace())
        // Lượt 3 là một con số trần trụi, không còn ngữ cảnh nào để bám vào.
        val third = agent.handleText("hai tư", trace())

        assertEquals(VoiceTurnStatus.UNSUPPORTED, third.status)
    }

    /** Một lệnh đầy đủ ở lượt 2 phải thắng ngữ cảnh đang chờ, không bị ghép nhầm. */
    @Test
    fun `a complete command overrides a pending clarification`() = runImmediate {
        val gateway = FakeGateway(CommandResult.Applied("ok", emptyMap()))
        val agent = VoiceAgent(
            asr = FakeAsrClient(AsrResult("", 0f, 0)),
            router = GrammarIntentRouter(),
            gateway = gateway,
            tts = RecordingTts(),
        )

        agent.handleText("Vivi ơi nóng quá", trace())
        val second = agent.handleText("Vivi ơi chuyển bài tiếp theo", trace())

        assertEquals(VoiceTurnStatus.APPLIED, second.status)
        assertEquals("media_next", gateway.received?.name)
    }

    @Test
    fun `negated command never reaches the gateway or the planner`() = runImmediate {
        val gateway = FakeGateway(CommandResult.Failed("must not execute"))
        val planner = RecordingPlanner(AgentPlanResult.Unavailable("must not be consulted"))
        val agent = VoiceAgent(
            asr = FakeAsrClient(AsrResult("Vivi ơi đừng mở cửa", 0.97f, 15)),
            router = GrammarIntentRouter(),
            gateway = gateway,
            tts = RecordingTts(),
            planner = planner,
        )

        val result = agent.handleAudio(shortArrayOf(1), 16_000, trace())

        assertEquals(VoiceTurnStatus.NEEDS_CLARIFICATION, result.status)
        assertNull(gateway.received)
        assertEquals(emptyList<String>(), planner.received)
    }

    /** N5 hồi quy: "không" ở vị trí giá trị vẫn phải là quạt mức 0. */
    @Test
    fun `N5 fan level zero survives the negation gate`() = runImmediate {
        val gateway = FakeGateway(CommandResult.Applied("Đã đặt quạt mức 0.", emptyMap()))
        val agent = VoiceAgent(
            asr = FakeAsrClient(AsrResult("Vivi ơi quạt mức không", 0.95f, 15)),
            router = GrammarIntentRouter(),
            gateway = gateway,
            tts = RecordingTts(),
        )

        val result = agent.handleAudio(shortArrayOf(1), 16_000, trace())

        assertEquals(VoiceTurnStatus.APPLIED, result.status)
        assertEquals("hvac_set_fan", gateway.received?.name)
        assertEquals(0, gateway.received?.slots?.get("level"))
    }

    /** Bẫy dấu: "dừng nhạc" là media_pause, không phải "đừng". */
    @Test
    fun `dung nhac still pauses media`() = runImmediate {
        val gateway = FakeGateway(CommandResult.Applied("Đang dừng nhạc", emptyMap()))
        val agent = VoiceAgent(
            asr = FakeAsrClient(AsrResult("Vivi ơi dừng nhạc", 0.95f, 15)),
            router = GrammarIntentRouter(),
            gateway = gateway,
            tts = RecordingTts(),
        )

        val result = agent.handleAudio(shortArrayOf(1), 16_000, trace())

        assertEquals(VoiceTurnStatus.APPLIED, result.status)
        assertEquals("media_pause", gateway.received?.name)
    }

    @Test
    fun `ambiguous cold complaint asks a question and never reaches the car gateway`() = runImmediate {
        val tts = RecordingTts()
        val gateway = FakeGateway(CommandResult.Applied("must not execute"))
        val agent = VoiceAgent(
            asr = FakeAsrClient(AsrResult("lạnh quá", 0.95f, 10)),
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
    fun `unsupported grammar falls back to the constrained agent then uses the same gateway`() =
        runImmediate {
            val gateway = FakeGateway(CommandResult.Applied("Đã đặt nhiệt độ mục tiêu 22°C."))
            val planner = RecordingPlanner(
                AgentPlanResult.Action(
                    Intent(
                        name = "hvac_set_temp",
                        slots = mapOf("value" to 22f),
                        confidence = 0.91f,
                        tier = Intent.Tier.T2,
                    ),
                ),
            )
            val agent = VoiceAgent(
                asr = FakeAsrClient(),
                router = GrammarIntentRouter(),
                gateway = gateway,
                tts = RecordingTts(),
                planner = planner,
            )

            val result = agent.handleText("trong xe ngột ngạt quá, làm mát giúp mình", trace())

            assertEquals(VoiceTurnStatus.APPLIED, result.status)
            assertEquals(listOf("trong xe ngột ngạt quá, làm mát giúp mình"), planner.received)
            assertEquals("hvac_set_temp", gateway.received?.name)
            assertEquals(Intent.Tier.T2, gateway.received?.tier)
        }

    @Test
    fun `deterministic compound actions execute in spoken order through the same gateway`() =
        runImmediate {
            val tts = RecordingTts()
            val gateway = SequencedGateway(
                listOf(
                    CommandResult.Applied("Đã bật đèn cabin.", mapOf("lights.on" to true)),
                    CommandResult.Applied("Đã chuyển bài.", mapOf("media.track" to "next")),
                ),
            )
            val agent = VoiceAgent(
                asr = FakeAsrClient(),
                router = GrammarIntentRouter(),
                gateway = gateway,
                tts = tts,
            )

            val result = agent.handleText("bật đèn cabin rồi chuyển bài", trace())

            assertEquals(VoiceTurnStatus.APPLIED, result.status)
            assertEquals(listOf("cabin_lights", "media_next"), gateway.received.map(Intent::name))
            assertEquals(listOf("Đã bật đèn cabin.", "Đã chuyển bài."), result.spokenSegmentsVi)
            assertEquals(result.spokenSegmentsVi, tts.spoken)
            assertEquals(true, result.hmiPatch["lights.on"])
            assertEquals("next", result.hmiPatch["media.track"])
        }

    @Test
    fun `bounded agent action plan uses the same sequential executor`() = runImmediate {
        val gateway = SequencedGateway(
            listOf(
                CommandResult.Applied("Đã bật đèn cabin."),
                CommandResult.Applied("Đã chuyển bài."),
            ),
        )
        val planner = RecordingPlanner(
            AgentPlanResult.Actions(
                listOf(
                    Intent("cabin_lights", mapOf("on" to true), 0.91f, Intent.Tier.T2),
                    Intent("media_next", confidence = 0.88f, tier = Intent.Tier.T2),
                ),
            ),
        )
        val agent = VoiceAgent(
            asr = FakeAsrClient(),
            router = GrammarIntentRouter(),
            gateway = gateway,
            tts = RecordingTts(),
            planner = planner,
        )

        val result = agent.handleText("làm cabin dễ chịu rồi đổi bài giúp mình", trace())

        assertEquals(VoiceTurnStatus.APPLIED, result.status)
        assertEquals(listOf("cabin_lights", "media_next"), gateway.received.map(Intent::name))
    }

    @Test
    fun `compound execution stops after the first non-applied result without claiming rollback`() =
        runImmediate {
            val tts = RecordingTts()
            val gateway = SequencedGateway(
                listOf(
                    CommandResult.Applied("Đã bật đèn cabin.", mapOf("lights.on" to true)),
                    CommandResult.Denied("G1_SPEED_LOCK", "Xe đang chạy, mình chưa mở cửa được."),
                    CommandResult.Applied("Đã chuyển bài."),
                ),
            )
            val agent = VoiceAgent(
                asr = FakeAsrClient(),
                router = GrammarIntentRouter(),
                gateway = gateway,
                tts = tts,
            )

            val result = agent.handleText(
                "bật đèn cabin rồi mở cửa rồi chuyển bài",
                trace(),
            )

            assertEquals(VoiceTurnStatus.PARTIALLY_APPLIED, result.status)
            assertEquals(listOf("cabin_lights", "door_lock"), gateway.received.map(Intent::name))
            assertEquals(
                listOf("Đã bật đèn cabin.", "Xe đang chạy, mình chưa mở cửa được."),
                result.spokenSegmentsVi,
            )
            assertEquals(true, result.hmiPatch["lights.on"])
        }

    @Test
    fun `commands explicitly removed from the demo never reach the agent fallback`() = runImmediate {
        val planner = RecordingPlanner(
            AgentPlanResult.Action(
                Intent(
                    name = "hvac_set_temp",
                    slots = mapOf("value" to 22f),
                    confidence = 0.9f,
                    tier = Intent.Tier.T2,
                ),
            ),
        )
        val gateway = FakeGateway(CommandResult.Applied("must not execute"))
        val agent = VoiceAgent(
            asr = FakeAsrClient(),
            router = GrammarIntentRouter(),
            gateway = gateway,
            tts = RecordingTts(),
            planner = planner,
        )

        val result = agent.handleText("bật điều hòa", trace())

        assertEquals(VoiceTurnStatus.UNSUPPORTED, result.status)
        assertTrue(planner.received.isEmpty())
        assertNull(gateway.received)
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

    @Test
    fun `result is published before TTS so HMI can show spoken text with audio`() = runImmediate {
        val order = mutableListOf<String>()
        val tts = object : TtsSpeaker {
            override suspend fun speak(text: String, trace: LatencyTrace) {
                order += "tts:$text"
                trace.mark(Stage.TTS_START)
            }
        }
        val agent = VoiceAgent(
            asr = FakeAsrClient(
                AsrResult("mở cửa", acousticConfidence = 0.2f, serverMs = 12),
            ),
            router = GrammarIntentRouter(),
            gateway = FakeGateway(CommandResult.Applied("must not execute")),
            tts = tts,
            onResultReady = { result ->
                order += "ui:${result.status}:${result.spokenVi}"
            },
        )

        agent.handleAudio(shortArrayOf(1), 16_000, trace())

        assertEquals(
            listOf(
                "ui:NEEDS_CLARIFICATION:Mình chưa nghe rõ. Bạn nói lại giúp mình nhé.",
                "tts:Mình chưa nghe rõ. Bạn nói lại giúp mình nhé.",
            ),
            order,
        )
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
