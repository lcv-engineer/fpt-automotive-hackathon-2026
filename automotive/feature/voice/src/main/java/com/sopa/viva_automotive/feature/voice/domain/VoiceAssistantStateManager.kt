package com.sopa.viva_automotive.feature.voice.domain

import com.sopa.viva_automotive.feature.voice.domain.model.VoiceAssistantState
import com.sopa.viva_automotive.feature.voice.domain.model.VoiceEvent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Singleton
class VoiceAssistantStateManager @Inject constructor() {

    private val _state = MutableStateFlow<VoiceAssistantState>(VoiceAssistantState.Idle)
    val state: StateFlow<VoiceAssistantState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<VoiceEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<VoiceEvent> = _events.asSharedFlow()

    fun transitionToWakeDetected() {
        _state.value = VoiceAssistantState.WakeDetected
    }

    fun transitionToListening(fromHotword: Boolean = false) {
        _state.value = VoiceAssistantState.Listening(fromHotword = fromHotword)
        _events.tryEmit(VoiceEvent.ListeningStarted)
    }

    fun updatePartialTranscription(text: String) {
        _state.update { current ->
            if (current is VoiceAssistantState.Listening) {
                current.copy(partialTranscription = text)
            } else {
                current
            }
        }
    }

    fun transitionToProcessing(utterance: String) {
        _state.value = VoiceAssistantState.Processing(utterance)
    }

    fun transitionToExecuting(description: String) {
        _state.value = VoiceAssistantState.Executing(description)
    }

    fun transitionToClarification(promptVi: String) {
        _state.value = VoiceAssistantState.Clarification(promptVi)
        _events.tryEmit(VoiceEvent.ClarificationRequested(promptVi))
    }

    fun transitionToSuccess(message: String) {
        _state.value = VoiceAssistantState.Success(message)
        _events.tryEmit(VoiceEvent.CommandExecuted(message))
    }

    fun transitionToError(message: String) {
        _state.value = VoiceAssistantState.Error(message)
        _events.tryEmit(VoiceEvent.CommandFailed(message))
    }

    fun transitionToIdle() {
        _state.value = VoiceAssistantState.Idle
    }
}
