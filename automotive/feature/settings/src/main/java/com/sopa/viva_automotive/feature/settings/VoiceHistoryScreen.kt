package com.sopa.viva_automotive.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sopa.viva_automotive.core.database.voicehistory.VoiceTurnHistoryEntity
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VoiceHistoryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VoiceHistoryViewModel = hiltViewModel(),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val timeFormat = remember {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM, Locale.getDefault())
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.settings_voice_history_back),
                )
            }
            Text(
                text = stringResource(R.string.settings_voice_history_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            if (entries.isNotEmpty()) {
                TextButton(onClick = viewModel::clearHistory) {
                    Text(stringResource(R.string.settings_voice_history_clear))
                }
            }
        }

        Text(
            text = stringResource(R.string.settings_voice_history_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (entries.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_voice_history_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 32.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(entries, key = { it.id }) { entry ->
                    VoiceHistoryRow(
                        entry = entry,
                        formattedTime = timeFormat.format(Date(entry.createdAtEpochMs)),
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceHistoryRow(
    entry: VoiceTurnHistoryEntity,
    formattedTime: String,
) {
    val outcomeColor = if (entry.succeeded) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }
    val outcomeLabel = stringResource(
        if (entry.succeeded) {
            R.string.settings_voice_history_success
        } else {
            R.string.settings_voice_history_failed
        },
    )
    val engineLabel = when (entry.asrEngine) {
        "google" -> stringResource(R.string.settings_asr_google)
        "viva" -> stringResource(R.string.settings_asr_viva)
        "vosk" -> stringResource(R.string.settings_asr_vosk)
        else -> stringResource(R.string.settings_voice_history_asr_none)
    }
    val spoken = entry.transcript.ifBlank {
        stringResource(R.string.settings_voice_history_no_transcript)
    }
    val command = buildString {
        append(entry.intentName ?: stringResource(R.string.settings_voice_history_no_intent))
        entry.intentSlots?.takeIf { it.isNotBlank() }?.let { slots ->
            append(" (")
            append(slots)
            append(')')
        }
    }
    val noteLabel = when {
        !entry.succeeded && entry.note.isNotBlank() ->
            stringResource(R.string.settings_voice_history_note)
        entry.succeeded && entry.note.isNotBlank() ->
            stringResource(R.string.settings_voice_history_response)
        else -> null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = outcomeLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = outcomeColor,
                )
            }

            // Landscape: two columns so the right half is used, not left empty.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    HistoryField(
                        label = stringResource(R.string.settings_voice_history_said),
                        value = spoken,
                    )
                    HistoryField(
                        label = stringResource(R.string.settings_voice_history_command),
                        value = command,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    HistoryField(
                        label = stringResource(R.string.settings_voice_history_asr),
                        value = engineLabel,
                    )
                    HistoryField(
                        label = stringResource(R.string.settings_voice_history_status),
                        value = entry.status,
                    )
                }
            }

            if (noteLabel != null) {
                HistoryField(
                    label = noteLabel,
                    value = entry.note,
                    maxLines = 5,
                )
            }
        }
    }
}

@Composable
private fun HistoryField(
    label: String,
    value: String,
    maxLines: Int = 3,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
