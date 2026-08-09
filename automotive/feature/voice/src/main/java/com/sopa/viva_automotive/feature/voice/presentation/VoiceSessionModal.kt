package com.sopa.viva_automotive.feature.voice.presentation

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sopa.viva_automotive.feature.voice.R
import com.sopa.viva_automotive.feature.voice.domain.model.VoiceAssistantState

/**
 * Full-bleed voice plate shown while a turn is active (hotword or tap-to-talk).
 */
@Composable
fun VoiceSessionModal(
    state: VoiceAssistantState,
    onDismiss: () -> Unit,
) {
    if (state is VoiceAssistantState.Idle) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.72f),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 40.dp, vertical = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ListeningOrb(
                        active = state is VoiceAssistantState.Listening ||
                            state is VoiceAssistantState.WakeDetected,
                    )

                    Text(
                        text = modalTitle(state),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = modalBody(state),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    FilledTonalButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(stringResource(R.string.voice_modal_dismiss))
                    }
                }
            }
        }
    }
}

@Composable
private fun ListeningOrb(active: Boolean) {
    val scale = if (active) {
        val transition = rememberInfiniteTransition(label = "voiceOrb")
        val pulse by transition.animateFloat(
            initialValue = 0.92f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "voiceOrbScale",
        )
        pulse
    } else {
        1f
    }
    Box(
        modifier = Modifier
            .size(120.dp)
            .scale(scale)
            .background(
                color = if (active) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(
                    color = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                tint = if (active) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                },
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

@Composable
private fun modalTitle(state: VoiceAssistantState): String = when (state) {
    is VoiceAssistantState.WakeDetected -> stringResource(R.string.voice_modal_wake_title)
    is VoiceAssistantState.Listening -> if (state.fromHotword) {
        stringResource(R.string.voice_modal_wake_listening_title)
    } else {
        stringResource(R.string.voice_modal_listening_title)
    }
    is VoiceAssistantState.Processing,
    is VoiceAssistantState.Executing,
    -> stringResource(R.string.voice_modal_processing_title)
    is VoiceAssistantState.Clarification -> stringResource(R.string.voice_modal_clarify_title)
    is VoiceAssistantState.Success -> stringResource(R.string.voice_modal_success_title)
    is VoiceAssistantState.Error -> stringResource(R.string.voice_modal_error_title)
    else -> stringResource(R.string.voice_assistant_label)
}

@Composable
private fun modalBody(state: VoiceAssistantState): String = when (state) {
    is VoiceAssistantState.WakeDetected ->
        stringResource(R.string.voice_modal_wake_body)
    is VoiceAssistantState.Listening ->
        state.partialTranscription.ifBlank {
            stringResource(R.string.voice_modal_listening_body)
        }
    is VoiceAssistantState.Processing ->
        if (state.utterance.isNotBlank() && state.utterance != "…") {
            "“${state.utterance}”"
        } else {
            stringResource(R.string.voice_processing)
        }
    is VoiceAssistantState.Executing -> state.description
    is VoiceAssistantState.Clarification -> state.promptVi
    is VoiceAssistantState.Success -> state.message
    is VoiceAssistantState.Error -> state.message
    else -> ""
}
