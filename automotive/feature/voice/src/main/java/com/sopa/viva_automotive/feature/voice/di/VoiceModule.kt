package com.sopa.viva_automotive.feature.voice.di

import android.content.Context
import com.sopa.viva_automotive.feature.voice.data.audio.AndroidVolumeController
import com.sopa.viva_automotive.feature.voice.data.asr.RoutingAsrClient
import com.sopa.viva_automotive.feature.voice.data.brain.RemoteLlmAgentPlanner
import com.sopa.viva_automotive.feature.voice.data.embedding.OnnxEmbeddingIntentMatcher
import com.sopa.viva_automotive.feature.voice.data.media.AndroidMediaCommandExecutor
import com.sopa.viva_automotive.feature.voice.domain.audio.VolumeController
import com.sopa.viva_automotive.feature.voice.domain.delivery.DeliveryRepository
import com.sopa.viva_automotive.feature.voice.domain.delivery.InMemoryDeliveryRepository
import com.sopa.viva_automotive.feature.voice.domain.embedding.SemanticIntentMatcher
import com.sopa.viva_automotive.feature.voice.domain.media.MediaCommandExecutor
import com.sopa.viva_automotive.feature.voice.integration.AppCommandGateway
import com.sopa.viva_automotive.feature.voice.domain.VoiceAssistantStateManager
import com.sopa.viva_automotive.feature.voice.domain.VoiceTurnHistoryRecorder
import com.sopa.viva_automotive.feature.voice.navigation.NavigationDispatcher
import com.sopa.viva_automotive.feature.voice.navigation.VoiceIntentNavigator
import com.sopa.viva_automotive.feature.voice.via.RecognitionResultHub
import com.viva.voice.agent.CommandGateway
import com.viva.voice.agent.AgentPlanner
import com.viva.voice.agent.VoiceAgent
import com.viva.voice.agent.VoiceTurnResult
import com.viva.voice.agent.VoiceTurnStatus
import com.viva.voice.asr.AsrClient
import com.viva.voice.intent.GrammarIntentRouter
import com.viva.voice.intent.IntentRouter
import com.viva.voice.tts.AndroidTtsSpeaker
import com.viva.voice.tts.TtsSpeaker
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Module
@InstallIn(SingletonComponent::class)
abstract class VoiceModule {

    @Binds
    @Singleton
    abstract fun bindSemanticIntentMatcher(
        impl: OnnxEmbeddingIntentMatcher,
    ): SemanticIntentMatcher

    @Binds
    @Singleton
    abstract fun bindCommandGateway(
        impl: AppCommandGateway,
    ): CommandGateway

    @Binds
    @Singleton
    abstract fun bindAgentPlanner(
        impl: RemoteLlmAgentPlanner,
    ): AgentPlanner

    @Binds
    @Singleton
    abstract fun bindAsrClient(
        impl: RoutingAsrClient,
    ): AsrClient

    @Binds
    @Singleton
    abstract fun bindVolumeController(
        impl: AndroidVolumeController,
    ): VolumeController

    @Binds
    @Singleton
    abstract fun bindMediaCommandExecutor(
        impl: AndroidMediaCommandExecutor,
    ): MediaCommandExecutor

    /**
     * Singleton because the route is mutable state: the stop the driver just
     * confirmed must still be delivered on the next turn, and a per-injection
     * instance would silently reset it.
     */
    @Binds
    @Singleton
    abstract fun bindDeliveryRepository(
        impl: InMemoryDeliveryRepository,
    ): DeliveryRepository

    companion object {
        /**
         * The T0 grammar tier, bound here instead of being constructed inside
         * `ProcessVoiceCommandUseCase` so the N4 ablation can replace it with a
         * no-op router and measure what the grammar tier is actually holding up
         * (`16-QUYET-DINH-DUONG-NLU.md`). Stateless apart from its rule list,
         * so one instance is enough.
         */
        @Provides
        @Singleton
        fun provideIntentRouter(): IntentRouter = GrammarIntentRouter()

        @Provides
        @Singleton
        fun provideTtsSpeaker(@ApplicationContext context: Context): TtsSpeaker =
            AndroidTtsSpeaker(context)

        @Provides
        @Singleton
        fun provideVoiceAgent(
            asr: AsrClient,
            gateway: CommandGateway,
            router: IntentRouter,
            planner: AgentPlanner,
            tts: TtsSpeaker,
            stateManager: VoiceAssistantStateManager,
            recognitionResultHub: RecognitionResultHub,
            historyRecorder: VoiceTurnHistoryRecorder,
            navigationDispatcher: NavigationDispatcher,
        ): VoiceAgent {
            val historyScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            return VoiceAgent(
                asr = asr,
                router = router,
                gateway = gateway,
                tts = tts,
                planner = planner,
                onResultReady = { result ->
                    if (result.transcript.isNotBlank()) {
                        recognitionResultHub.publishPartial(result.transcript)
                        recognitionResultHub.publishFinal(result.transcript)
                    } else {
                        recognitionResultHub.cancel()
                    }
                    publishTurnUi(stateManager, result)
                    VoiceIntentNavigator.routeFor(result.intent?.name, result.status)
                        ?.let(navigationDispatcher::navigateTo)
                    // Persist off the agent path so a DB hitch never blocks TTS.
                    historyScope.launch { historyRecorder.recordAgentTurn(result) }
                },
            )
        }
        /** Shows spoken copy on the voice bar before TTS starts. */
        fun publishTurnUi(
            stateManager: VoiceAssistantStateManager,
            result: VoiceTurnResult,
            displayTranscript: String = result.transcript.ifBlank { "…" },
        ) {
            val heard = displayTranscript.takeIf { it.isNotBlank() && it != "…" }.orEmpty()
            if (heard.isNotEmpty()) {
                stateManager.transitionToProcessing(heard)
            }
            when (result.status) {
                VoiceTurnStatus.APPLIED ->
                    stateManager.transitionToSuccess(result.spokenVi, heardTranscript = heard)
                VoiceTurnStatus.PARTIALLY_APPLIED ->
                    stateManager.transitionToError(result.spokenVi, heardTranscript = heard)
                VoiceTurnStatus.NEEDS_CLARIFICATION,
                VoiceTurnStatus.NEEDS_CONFIRMATION,
                -> stateManager.transitionToClarification(
                    result.spokenVi,
                    heardTranscript = heard,
                )
                VoiceTurnStatus.DENIED,
                VoiceTurnStatus.UNSUPPORTED,
                VoiceTurnStatus.FAILED,
                -> stateManager.transitionToError(result.spokenVi, heardTranscript = heard)
            }
        }
    }
}
