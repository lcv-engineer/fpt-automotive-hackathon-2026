package com.sopa.viva_automotive.feature.voice.via

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class VivaVoiceInteractionSessionService : VoiceInteractionSessionService() {

    @Inject lateinit var sessionBridge: VoiceSessionBridge

    override fun onNewSession(args: Bundle?): VoiceInteractionSession =
        VivaVoiceInteractionSession(this, sessionBridge)
}
