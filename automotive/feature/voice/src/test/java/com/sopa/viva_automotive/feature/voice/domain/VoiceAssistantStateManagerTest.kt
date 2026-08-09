package com.sopa.viva_automotive.feature.voice.domain

import com.sopa.viva_automotive.feature.voice.domain.model.VoiceAssistantState
import com.sopa.viva_automotive.feature.voice.domain.model.VoiceEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceAssistantStateManagerTest {

    private val stateManager = VoiceAssistantStateManager()

    @Test
    fun `initial state is idle`() {
        assertEquals(VoiceAssistantState.Idle, stateManager.state.value)
    }

    @Test
    fun `full happy path transitions`() {
        stateManager.transitionToListening()
        assertEquals(VoiceAssistantState.Listening(), stateManager.state.value)

        stateManager.updatePartialTranscription("set temp")
        assertEquals(VoiceAssistantState.Listening("set temp"), stateManager.state.value)

        stateManager.transitionToProcessing("set temperature to 22")
        assertEquals(
            VoiceAssistantState.Processing("set temperature to 22"),
            stateManager.state.value,
        )

        stateManager.transitionToExecuting("Setting temperature")
        assertEquals(
            VoiceAssistantState.Executing("Setting temperature"),
            stateManager.state.value,
        )

        stateManager.transitionToSuccess("Temperature set to 22 degrees for driver")
        assertEquals(
            VoiceAssistantState.Success("Temperature set to 22 degrees for driver"),
            stateManager.state.value,
        )

        stateManager.transitionToIdle()
        assertEquals(VoiceAssistantState.Idle, stateManager.state.value)
    }

    @Test
    fun `partial transcription is ignored outside listening state`() {
        stateManager.transitionToProcessing("utterance")
        stateManager.updatePartialTranscription("should be ignored")
        assertEquals(VoiceAssistantState.Processing("utterance"), stateManager.state.value)
    }

    @Test
    fun `clarification keeps the heard transcript for the voice bar`() {
        stateManager.transitionToClarification(
            promptVi = "Bạn muốn đặt bao nhiêu độ?",
            heardTranscript = "hạ điều hòa",
        )
        assertEquals(
            VoiceAssistantState.Clarification(
                promptVi = "Bạn muốn đặt bao nhiêu độ?",
                heardTranscript = "hạ điều hòa",
            ),
            stateManager.state.value,
        )
    }

    @Test
    fun `clarification is a first class state and event, not a command failure`() =
        runTest(UnconfinedTestDispatcher()) {
            val received = mutableListOf<VoiceEvent>()
            val collector = launch { stateManager.events.collect { received.add(it) } }

            stateManager.transitionToClarification("Bạn muốn đặt bao nhiêu độ?")

            assertEquals(
                VoiceAssistantState.Clarification("Bạn muốn đặt bao nhiêu độ?"),
                stateManager.state.value,
            )
            assertEquals(
                listOf(VoiceEvent.ClarificationRequested("Bạn muốn đặt bao nhiêu độ?")),
                received,
            )
            collector.cancel()
        }

    @Test
    fun `success and error emit one-off events`() = runTest(UnconfinedTestDispatcher()) {
        val received = mutableListOf<VoiceEvent>()
        val collector = launch { stateManager.events.collect { received.add(it) } }

        stateManager.transitionToListening()
        stateManager.transitionToSuccess("done")
        stateManager.transitionToError("failed")

        assertEquals(
            listOf(
                VoiceEvent.ListeningStarted,
                VoiceEvent.CommandExecuted("done"),
                VoiceEvent.CommandFailed("failed"),
            ),
            received,
        )
        collector.cancel()
    }
}
