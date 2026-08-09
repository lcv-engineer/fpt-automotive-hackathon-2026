package com.sopa.viva_automotive.feature.voice.presentation

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sopa.viva_automotive.core.ui.theme.VivaDimens
import com.sopa.viva_automotive.feature.voice.R
import com.sopa.viva_automotive.feature.voice.domain.model.VoiceAssistantState

/**
 * Persistent bottom voice bar. Active turns (hotword / tap-to-talk) update this bar
 * only — no modal overlay.
 */
@Composable
fun VoiceOverlay(
    modifier: Modifier = Modifier,
    viewModel: VoiceViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val hotwordArmed by viewModel.hotwordArmed.collectAsStateWithLifecycle()

    val activeTurn = state !is VoiceAssistantState.Idle
    val listening = state is VoiceAssistantState.Listening ||
        state is VoiceAssistantState.WakeDetected
    val processing = state is VoiceAssistantState.Processing ||
        state is VoiceAssistantState.Executing

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MicButton(
                listening = listening,
                animate = listening,
                enabled = settings.voiceEnabled && (!processing || activeTurn),
                onClick = {
                    if (activeTurn) {
                        viewModel.onCancelPressed()
                    } else {
                        viewModel.onMicPressed()
                    }
                },
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = primaryStatus(
                        state = state,
                        voiceEnabled = settings.voiceEnabled,
                        hotwordArmed = hotwordArmed,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                secondaryStatus(state)?.let { secondary ->
                    Text(
                        text = secondary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun MicButton(
    listening: Boolean,
    animate: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val scale = if (animate) {
        val transition = rememberInfiniteTransition(label = "micPulse")
        val pulse by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
            label = "micPulseScale",
        )
        pulse
    } else {
        1f
    }

    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(VivaDimens.TouchTarget)
            .scale(scale),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (listening) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
        ),
    ) {
        Icon(
            imageVector = if (enabled) Icons.Default.Mic else Icons.Default.MicOff,
            contentDescription = stringResource(
                if (listening) R.string.voice_cd_stop else R.string.voice_cd_start,
            ),
            modifier = Modifier.size(36.dp),
        )
    }
}

@Composable
private fun primaryStatus(
    state: VoiceAssistantState,
    voiceEnabled: Boolean,
    hotwordArmed: Boolean,
): String = when {
    !voiceEnabled -> stringResource(R.string.voice_disabled)
    state is VoiceAssistantState.Idle -> if (hotwordArmed) {
        stringResource(R.string.voice_hotword_armed)
    } else {
        stringResource(R.string.voice_idle_prompt)
    }
    state is VoiceAssistantState.WakeDetected ->
        stringResource(R.string.voice_modal_wake_body)
    state is VoiceAssistantState.Listening ->
        if (state.partialTranscription.isNotBlank()) {
            state.partialTranscription
        } else if (state.fromHotword) {
            stringResource(R.string.voice_modal_wake_listening_title)
        } else {
            stringResource(R.string.voice_listening)
        }
    state is VoiceAssistantState.Processing ->
        state.utterance.ifBlank { stringResource(R.string.voice_processing) }
    state is VoiceAssistantState.Executing -> state.description
    state is VoiceAssistantState.Clarification -> state.promptVi
    state is VoiceAssistantState.Success -> state.message
    state is VoiceAssistantState.Error -> state.message
    else -> stringResource(R.string.voice_idle_prompt)
}

@Composable
private fun secondaryStatus(state: VoiceAssistantState): String? = when (state) {
    is VoiceAssistantState.Idle -> null
    is VoiceAssistantState.WakeDetected -> stringResource(R.string.voice_listening)
    is VoiceAssistantState.Listening ->
        if (state.partialTranscription.isNotBlank()) {
            stringResource(R.string.voice_listening)
        } else {
            stringResource(R.string.voice_modal_listening_body)
        }
    is VoiceAssistantState.Processing -> stringResource(R.string.voice_processing)
    is VoiceAssistantState.Executing -> stringResource(R.string.voice_modal_processing_title)
    is VoiceAssistantState.Clarification -> stringResource(R.string.voice_modal_clarify_title)
    is VoiceAssistantState.Success -> stringResource(R.string.voice_modal_success_title)
    is VoiceAssistantState.Error -> stringResource(R.string.voice_modal_error_title)
}
