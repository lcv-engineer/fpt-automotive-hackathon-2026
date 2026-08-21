package com.sopa.viva_automotive.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sopa.viva_automotive.core.common.buildinfo.BuildInfo
import com.sopa.viva_automotive.core.database.settings.AsrEngine
import com.sopa.viva_automotive.core.ui.components.SectionCard
import com.sopa.viva_automotive.core.ui.components.VivaToggleRow
import com.sopa.viva_automotive.core.ui.locale.AppLanguage
import com.sopa.viva_automotive.core.ui.theme.ThemeMode
import com.sopa.viva_automotive.core.ui.theme.VivaDimens

private object SettingsRoutes {
    const val HOME = "settings_home"
    const val VOICE_HISTORY = "voice_history"
}

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = SettingsRoutes.HOME,
        modifier = modifier.fillMaxSize(),
    ) {
        composable(SettingsRoutes.HOME) {
            SettingsHomeScreen(
                onOpenVoiceHistory = { navController.navigate(SettingsRoutes.VOICE_HISTORY) },
            )
        }
        composable(SettingsRoutes.VOICE_HISTORY) {
            VoiceHistoryScreen(onBack = { navController.popBackStack() })
        }
    }
}

@Composable
private fun SettingsHomeScreen(
    onOpenVoiceHistory: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val buildInfo = viewModel.buildInfo

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.settings_voice),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SettingsToggleCard(
                    label = stringResource(R.string.settings_voice_enabled),
                    checked = settings.voiceEnabled,
                    onCheckedChange = viewModel::setVoiceEnabled,
                    icon = Icons.Default.Mic,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                SettingsToggleCard(
                    label = stringResource(R.string.settings_hotword_enabled),
                    checked = settings.hotwordEnabled,
                    onCheckedChange = viewModel::setHotwordEnabled,
                    icon = Icons.Default.RecordVoiceOver,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                SettingsToggleCard(
                    label = stringResource(R.string.settings_show_transcription),
                    checked = settings.showPartialTranscription,
                    onCheckedChange = viewModel::setShowPartialTranscription,
                    icon = Icons.Default.Subtitles,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                SettingsToggleCard(
                    label = stringResource(R.string.settings_audio_cues),
                    checked = settings.playAudioCues,
                    onCheckedChange = viewModel::setPlayAudioCues,
                    icon = Icons.Default.MusicNote,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
            Text(
                text = stringResource(R.string.settings_hotword_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = stringResource(R.string.settings_asr_engine)) {
            val currentAsr = AsrEngine.fromStorageKey(settings.asrEngine)
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(VivaDimens.ButtonHeight),
            ) {
                AsrEngine.entries.forEachIndexed { index, engine ->
                    SegmentedButton(
                        selected = currentAsr == engine,
                        onClick = { viewModel.setAsrEngine(engine) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = AsrEngine.entries.size,
                        ),
                        label = {
                            Text(
                                text = asrEngineLabel(engine),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        },
                    )
                }
            }
            Text(
                text = stringResource(R.string.settings_asr_engine_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = stringResource(R.string.settings_voice_history)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenVoiceHistory)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_voice_history_open),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.settings_voice_history_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard(title = stringResource(R.string.settings_display)) {
            val currentMode = ThemeMode.fromStorageKey(settings.themeMode)
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(VivaDimens.ButtonHeight),
            ) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = currentMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = ThemeMode.entries.size,
                        ),
                        label = {
                            Text(
                                text = themeModeLabel(mode),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        },
                    )
                }
            }
            Text(
                text = stringResource(R.string.settings_theme_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = stringResource(R.string.settings_language)) {
            val currentLanguage = AppLanguage.fromStorageKey(settings.language)
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(VivaDimens.ButtonHeight),
            ) {
                AppLanguage.entries.forEachIndexed { index, language ->
                    SegmentedButton(
                        selected = currentLanguage == language,
                        onClick = { viewModel.setLanguage(language) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = AppLanguage.entries.size,
                        ),
                        label = {
                            Text(
                                text = languageLabel(language),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        },
                    )
                }
            }
            Text(
                text = stringResource(R.string.settings_language_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = stringResource(R.string.settings_units)) {
            VivaToggleRow(
                label = stringResource(R.string.settings_use_fahrenheit),
                checked = settings.useFahrenheit,
                onCheckedChange = viewModel::setUseFahrenheit,
                icon = Icons.Default.Thermostat,
            )
        }

        SectionCard(title = stringResource(R.string.settings_about)) {
            AboutRow(
                label = stringResource(R.string.settings_about_version),
                value = buildInfo.versionLabel,
            )
            AboutRow(
                label = stringResource(R.string.settings_about_purpose),
                value = purposeLabel(buildInfo),
            )
            AboutRow(
                label = stringResource(R.string.settings_about_backend),
                value = backendLabel(buildInfo.vehicleBackend),
            )
            AboutRow(
                label = stringResource(R.string.settings_about_build_type),
                value = buildInfo.buildType,
            )
            AboutRow(
                label = stringResource(R.string.settings_about_app_id),
                value = buildInfo.applicationId,
            )
        }
    }
}

@Composable
private fun SettingsToggleCard(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Start,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                softWrap = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}

@Composable
private fun asrEngineLabel(engine: AsrEngine): String = stringResource(
    when (engine) {
        AsrEngine.VIVA -> R.string.settings_asr_viva
        AsrEngine.GOOGLE -> R.string.settings_asr_google
    },
)

@Composable
private fun themeModeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.settings_theme_auto
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    },
)

@Composable
private fun languageLabel(language: AppLanguage): String = stringResource(
    when (language) {
        AppLanguage.SYSTEM -> R.string.settings_language_system
        AppLanguage.ENGLISH -> R.string.settings_language_english
        AppLanguage.VIETNAMESE -> R.string.settings_language_vietnamese
    },
)

@Composable
private fun purposeLabel(buildInfo: BuildInfo): String = stringResource(
    when {
        buildInfo.vehicleBackend == "mock" && buildInfo.isDebuggable ->
            R.string.settings_purpose_dev_testing
        buildInfo.vehicleBackend == "mock" -> R.string.settings_purpose_testing_mock
        buildInfo.isDebuggable -> R.string.settings_purpose_product_debug
        else -> R.string.settings_purpose_product
    },
)

@Composable
private fun backendLabel(backend: String): String = when (backend) {
    "mock" -> stringResource(R.string.settings_backend_mock)
    "real" -> stringResource(R.string.settings_backend_real)
    else -> backend
}

@Composable
private fun AboutRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Start,
        )
    }
}
