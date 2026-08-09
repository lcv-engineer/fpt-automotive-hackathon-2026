package com.sopa.viva_automotive.feature.voice.domain.model

sealed interface VoiceAssistantState {
    data object Idle : VoiceAssistantState

    /** Brief flash after wake-word before mic capture starts. */
    data object WakeDetected : VoiceAssistantState

    data class Listening(
        val partialTranscription: String = "",
        val fromHotword: Boolean = false,
    ) : VoiceAssistantState

    data class Processing(val utterance: String) : VoiceAssistantState
    data class Executing(val description: String) : VoiceAssistantState
    data class Clarification(val promptVi: String) : VoiceAssistantState
    data class Success(val message: String) : VoiceAssistantState
    data class Error(val message: String) : VoiceAssistantState
}

sealed interface VoiceEvent {
    data object ListeningStarted : VoiceEvent
    data class ClarificationRequested(val promptVi: String) : VoiceEvent
    data class CommandExecuted(val message: String) : VoiceEvent
    data class CommandFailed(val message: String) : VoiceEvent
}
