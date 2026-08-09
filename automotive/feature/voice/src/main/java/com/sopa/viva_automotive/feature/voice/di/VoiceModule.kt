package com.sopa.viva_automotive.feature.voice.di

import android.content.Context
import com.sopa.viva_automotive.feature.voice.data.audio.AndroidVolumeController
import com.sopa.viva_automotive.feature.voice.data.asr.HttpAsrClient
import com.sopa.viva_automotive.feature.voice.data.embedding.OnnxEmbeddingIntentMatcher
import com.sopa.viva_automotive.feature.voice.data.media.AndroidMediaCommandExecutor
import com.sopa.viva_automotive.feature.voice.domain.audio.VolumeController
import com.sopa.viva_automotive.feature.voice.domain.delivery.DeliveryRepository
import com.sopa.viva_automotive.feature.voice.domain.delivery.InMemoryDeliveryRepository
import com.sopa.viva_automotive.feature.voice.domain.embedding.SemanticIntentMatcher
import com.sopa.viva_automotive.feature.voice.domain.media.MediaCommandExecutor
import com.sopa.viva_automotive.feature.voice.integration.AppCommandGateway
import com.viva.voice.agent.CommandGateway
import com.viva.voice.agent.VoiceAgent
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
    abstract fun bindAsrClient(
        impl: HttpAsrClient,
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
            tts: TtsSpeaker,
        ): VoiceAgent = VoiceAgent(
            asr = asr,
            router = router,
            gateway = gateway,
            tts = tts,
        )
    }
}
