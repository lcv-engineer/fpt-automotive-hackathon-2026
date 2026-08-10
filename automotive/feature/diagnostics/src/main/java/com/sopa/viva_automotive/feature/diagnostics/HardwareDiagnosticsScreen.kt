package com.sopa.viva_automotive.feature.diagnostics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sopa.viva_automotive.core.ui.components.SectionCard
import com.sopa.viva_automotive.core.ui.theme.DarkAmber
import com.sopa.viva_automotive.core.ui.theme.DarkGreen
import com.sopa.viva_automotive.core.ui.theme.DarkRed
import com.sopa.viva_automotive.core.ui.theme.LightAmber
import com.sopa.viva_automotive.core.ui.theme.LightGreen
import com.sopa.viva_automotive.core.ui.theme.LightRed
import com.sopa.viva_automotive.core.ui.theme.VivaDimens
import kotlin.math.roundToInt

@Composable
fun HardwareDiagnosticsScreen(
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
    viewModel: HardwareDiagnosticsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var section by rememberSaveable { mutableStateOf(DiagnosticsSection.OVERVIEW.name) }
    val selected = DiagnosticsSection.entries
        .firstOrNull { it.name == section }
        ?: DiagnosticsSection.OVERVIEW

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DiagnosticsDrawer(
            selected = selected,
            onSelect = { section = it.name },
            modifier = Modifier
                .width(168.dp)
                .fillMaxHeight(),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (showTitle) {
                Text(
                    text = stringResource(R.string.diagnostics_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            ScanBanner(
                scan = state.scan,
                onScan = viewModel::startHardwareScan,
            )

            when (selected) {
                DiagnosticsSection.OVERVIEW -> OverviewSection(state)
                DiagnosticsSection.BATTERY -> BatterySection(state)
                DiagnosticsSection.SENSORS -> SensorsSection(state)
                DiagnosticsSection.UNITS -> UnitsSection(state)
                DiagnosticsSection.ALERTS -> AlertsSection(state, viewModel::refreshDiagnosis)
                DiagnosticsSection.TWIN -> TwinSection(state)
                DiagnosticsSection.UPDATES -> UpdatesSection(state, viewModel::startOtaUpdate)
            }
        }
    }
}

@Composable
private fun DiagnosticsDrawer(
    selected: DiagnosticsSection,
    onSelect: (DiagnosticsSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.diagnostics_drawer_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
        DiagnosticsSection.entries.forEach { item ->
            val active = item == selected
            val bg = if (active) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                Color.Transparent
            }
            val fg = if (active) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(bg)
                    .clickable { onSelect(item) }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = when (item) {
                        DiagnosticsSection.OVERVIEW -> Icons.Default.Build
                        DiagnosticsSection.BATTERY -> Icons.Default.BatteryChargingFull
                        DiagnosticsSection.SENSORS -> Icons.Default.Sensors
                        DiagnosticsSection.UNITS -> Icons.Default.Build
                        DiagnosticsSection.ALERTS -> Icons.Default.Warning
                        DiagnosticsSection.TWIN -> Icons.Default.Build
                        DiagnosticsSection.UPDATES -> Icons.Default.CloudDownload
                    },
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = sectionLabel(item),
                    style = MaterialTheme.typography.labelLarge,
                    color = fg,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun sectionLabel(section: DiagnosticsSection): String = stringResource(
    when (section) {
        DiagnosticsSection.OVERVIEW -> R.string.diagnostics_section_overview
        DiagnosticsSection.BATTERY -> R.string.diagnostics_section_battery
        DiagnosticsSection.SENSORS -> R.string.diagnostics_section_sensors
        DiagnosticsSection.UNITS -> R.string.diagnostics_section_units
        DiagnosticsSection.ALERTS -> R.string.diagnostics_section_alerts
        DiagnosticsSection.TWIN -> R.string.diagnostics_section_twin
        DiagnosticsSection.UPDATES -> R.string.diagnostics_section_updates
    },
)

@Composable
private fun ScanBanner(
    scan: ScanStatus,
    onScan: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.diagnostics_scan_title)) {
        Text(
            text = stringResource(R.string.diagnostics_vhal_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (scan.isScanning) {
            Text(
                text = stringResource(R.string.diagnostics_scan_progress, (scan.progress * 100).roundToInt(), scan.etaSeconds),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            LinearProgressIndicator(
                progress = { scan.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
            )
        } else {
            Button(
                onClick = onScan,
                modifier = Modifier.height(VivaDimens.ButtonHeight),
            ) {
                Text(stringResource(R.string.diagnostics_scan_action))
            }
        }
    }
}

@Composable
private fun OverviewSection(state: HardwareDiagnosticsState) {
    val severity = state.highestAlertSeverity
    SectionCard(title = stringResource(R.string.diagnostics_overview_health)) {
        Text(
            text = severityLabel(severity),
            style = MaterialTheme.typography.headlineSmall,
            color = severityColor(severity),
        )
        Text(
            text = state.genAi.summary,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricChip(
                label = stringResource(R.string.diagnostics_soc_short),
                value = "${state.batterySocPercent.roundToInt()}%",
                severity = if (state.batterySocPercent < 20f) HealthSeverity.WARNING else HealthSeverity.OK,
                modifier = Modifier.weight(1f),
            )
            MetricChip(
                label = stringResource(R.string.diagnostics_soh_short),
                value = "${state.batterySohPercent.roundToInt()}%",
                severity = sohSeverity(state.sohBand),
                modifier = Modifier.weight(1f),
            )
            MetricChip(
                label = stringResource(R.string.diagnostics_hu_short),
                value = "${state.systemHealth.memoryHealthPercent}%",
                severity = if (state.systemHealth.memoryHealthPercent < 35) {
                    HealthSeverity.WARNING
                } else {
                    HealthSeverity.OK
                },
                modifier = Modifier.weight(1f),
            )
        }
    }

    AccordionCard(
        title = stringResource(R.string.diagnostics_system_health),
        initiallyExpanded = false,
    ) {
        Text(
            text = stringResource(
                R.string.diagnostics_memory,
                state.systemHealth.memoryHealthPercent,
                state.systemHealth.usedMemoryMb,
                state.systemHealth.maxMemoryMb,
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        SemanticBar(
            fraction = state.systemHealth.memoryHealthPercent / 100f,
            severity = if (state.systemHealth.memoryHealthPercent < 35) {
                HealthSeverity.WARNING
            } else {
                HealthSeverity.OK
            },
        )
        Text(
            text = stringResource(R.string.diagnostics_flash, state.systemHealth.flashHealthPercent),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.diagnostics_cpu, state.systemHealth.cpuLoadHintPercent),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(
                if (state.systemHealth.watchdogOk) {
                    R.string.diagnostics_watchdog_ok
                } else {
                    R.string.diagnostics_watchdog_alert
                },
            ),
            style = MaterialTheme.typography.labelLarge,
            color = severityColor(
                if (state.systemHealth.watchdogOk) HealthSeverity.OK else HealthSeverity.EMERGENCY,
            ),
        )
    }
}

@Composable
private fun BatterySection(state: HardwareDiagnosticsState) {
    SectionCard(title = stringResource(R.string.diagnostics_battery)) {
        Text(
            text = stringResource(R.string.diagnostics_soc_legend),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.diagnostics_soh_legend),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            text = stringResource(R.string.diagnostics_soc),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        BulletGauge(
            valuePercent = state.batterySocPercent,
            severity = if (state.batterySocPercent < 20f) {
                HealthSeverity.WARNING
            } else if (state.batterySocPercent < 40f) {
                HealthSeverity.CAUTION
            } else {
                HealthSeverity.OK
            },
        )

        Text(
            text = stringResource(R.string.diagnostics_soh),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        SohBandGauge(valuePercent = state.batterySohPercent)
        SohLegend(band = state.sohBand)

        Text(
            text = stringResource(R.string.diagnostics_range, state.estimatedRangeKm.roundToInt()),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(
                R.string.diagnostics_capacity,
                state.usableCapacityKwh,
                state.originalCapacityKwh,
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(
                R.string.diagnostics_degradation,
                state.batteryDegradationPercent.roundToInt(),
            ),
            style = MaterialTheme.typography.labelLarge,
            color = severityColor(sohSeverity(state.sohBand)),
        )
    }
}

@Composable
private fun SensorsSection(state: HardwareDiagnosticsState) {
    SectionCard(title = stringResource(R.string.diagnostics_section_sensors)) {
        state.sensors.forEach { sensor ->
            StatusLine(
                title = sensor.label,
                value = sensor.valueLabel,
                detail = sensor.detail,
                severity = sensor.severity,
            )
        }
    }
}

@Composable
private fun UnitsSection(state: HardwareDiagnosticsState) {
    SectionCard(title = stringResource(R.string.diagnostics_section_units)) {
        Text(
            text = stringResource(R.string.diagnostics_units_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.units.forEach { unit ->
            StatusLine(
                title = unit.label,
                value = unit.statusLabel,
                detail = unit.detail,
                severity = unit.severity,
            )
        }
        Text(
            text = stringResource(
                if (state.ignitionOn) R.string.diagnostics_ignition_on else R.string.diagnostics_ignition_off,
            ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun AlertsSection(
    state: HardwareDiagnosticsState,
    onRefresh: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.diagnostics_predictive)) {
        if (state.predictiveAlerts.isEmpty()) {
            Text(
                text = stringResource(R.string.diagnostics_predictive_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            state.predictiveAlerts.forEach { alert ->
                AlertBlock(
                    title = alert.title,
                    detail = alert.detail,
                    meta = alert.etaLabel,
                    severity = alert.severity,
                )
            }
        }
    }

    SectionCard(title = stringResource(R.string.diagnostics_dtc)) {
        if (state.dtcs.isEmpty()) {
            Text(
                text = stringResource(R.string.diagnostics_dtc_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            state.dtcs.forEach { dtc ->
                val severity = when (dtc.severity) {
                    DtcSeverity.CRITICAL -> HealthSeverity.EMERGENCY
                    DtcSeverity.WARNING -> HealthSeverity.WARNING
                    DtcSeverity.INFO -> HealthSeverity.CAUTION
                }
                AlertBlock(
                    title = "${dtc.code} · ${dtc.title}",
                    detail = dtc.guidance,
                    meta = severityLabel(severity),
                    severity = severity,
                )
            }
        }
    }

    AccordionCard(
        title = stringResource(R.string.diagnostics_genai),
        initiallyExpanded = false,
        titleTrailing = {
            TextButton(onClick = onRefresh) {
                Text(stringResource(R.string.diagnostics_genai_refresh))
            }
        },
    ) {
        Text(
            text = stringResource(R.string.diagnostics_genai_summary),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = state.genAi.summary,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.diagnostics_genai_steps),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.genAi.steps.forEach { step ->
            Text(
                text = step,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = state.genAi.recommendation,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun TwinSection(state: HardwareDiagnosticsState) {
    SectionCard(title = stringResource(R.string.diagnostics_twin_title)) {
        Text(
            text = stringResource(R.string.diagnostics_twin_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DigitalTwinCanvas(
            hotspots = state.twinHotspots,
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
        )
        state.twinHotspots.forEach { spot ->
            StatusLine(
                title = spot.label,
                value = severityLabel(spot.severity),
                detail = spot.id,
                severity = spot.severity,
            )
        }
    }
}

@Composable
private fun UpdatesSection(
    state: HardwareDiagnosticsState,
    onOta: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.diagnostics_ota_title)) {
        Text(
            text = stringResource(R.string.diagnostics_ota_current, state.ota.currentVersion),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        when (state.ota.phase) {
            OtaPhase.UP_TO_DATE -> Text(
                text = stringResource(R.string.diagnostics_ota_uptodate),
                style = MaterialTheme.typography.bodyLarge,
                color = severityColor(HealthSeverity.OK),
            )
            OtaPhase.AVAILABLE -> {
                Text(
                    text = stringResource(
                        R.string.diagnostics_ota_available,
                        state.ota.availableVersion.orEmpty(),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Button(
                    onClick = onOta,
                    modifier = Modifier.height(VivaDimens.ButtonHeight),
                ) {
                    Text(stringResource(R.string.diagnostics_ota_start))
                }
            }
            OtaPhase.DOWNLOADING, OtaPhase.INSTALLING -> {
                Text(
                    text = stringResource(
                        if (state.ota.phase == OtaPhase.DOWNLOADING) {
                            R.string.diagnostics_ota_downloading
                        } else {
                            R.string.diagnostics_ota_installing
                        },
                        (state.ota.progress * 100).roundToInt(),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                LinearProgressIndicator(
                    progress = { state.ota.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                )
            }
            OtaPhase.FAILED -> Text(
                text = stringResource(R.string.diagnostics_ota_failed),
                style = MaterialTheme.typography.bodyLarge,
                color = severityColor(HealthSeverity.EMERGENCY),
            )
        }
        Text(
            text = stringResource(R.string.diagnostics_remote_body, state.ota.channel),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MetricChip(
    label: String,
    value: String,
    severity: HealthSeverity,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = severityColor(severity),
        )
    }
}

@Composable
private fun StatusLine(
    title: String,
    value: String,
    detail: String,
    severity: HealthSeverity,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(severityColor(severity)),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = severityColor(severity),
        )
    }
}

@Composable
private fun AlertBlock(
    title: String,
    detail: String,
    meta: String,
    severity: HealthSeverity,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(severityColor(severity).copy(alpha = 0.12f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = severityColor(severity))
        Text(text = detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(text = meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AccordionCard(
    title: String,
    initiallyExpanded: Boolean,
    titleTrailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    SectionCard(
        title = null,
        contentPadding = PaddingValues(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { expanded = !expanded }
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            titleTrailing?.invoke()
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

@Composable
private fun BulletGauge(
    valuePercent: Float,
    severity: HealthSeverity,
) {
    val fraction = (valuePercent / 100f).coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SemanticBar(fraction = fraction, severity = severity)
        Text(
            text = "${valuePercent.roundToInt()}%",
            style = MaterialTheme.typography.labelLarge,
            color = severityColor(severity),
        )
    }
}

@Composable
private fun SohBandGauge(valuePercent: Float) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val marker = MaterialTheme.colorScheme.onSurface
    val excellent = severityColor(HealthSeverity.OK)
    val good = if (isSystemInDarkTheme()) DarkGreen.copy(alpha = 0.7f) else LightGreen.copy(alpha = 0.7f)
    val fair = severityColor(HealthSeverity.CAUTION)
    val poor = severityColor(HealthSeverity.EMERGENCY)
    val fraction = (valuePercent / 100f).coerceIn(0f, 1f)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp),
    ) {
        val h = size.height
        val w = size.width
        // Band layout: 0-70 Poor, 70-80 Fair, 80-90 Good, 90-100 Excellent
        fun band(from: Float, to: Float, color: Color) {
            val left = w * from
            drawRoundRect(
                color = color,
                topLeft = Offset(left, 0f),
                size = Size(w * (to - from), h),
                cornerRadius = CornerRadius(h / 2f, h / 2f),
            )
        }
        drawRoundRect(color = track, size = size, cornerRadius = CornerRadius(h / 2f, h / 2f))
        band(0f, 0.70f, poor.copy(alpha = 0.55f))
        band(0.70f, 0.80f, fair.copy(alpha = 0.65f))
        band(0.80f, 0.90f, good)
        band(0.90f, 1f, excellent)
        val x = w * fraction
        drawCircle(color = marker, radius = h * 0.42f, center = Offset(x, h / 2f))
        drawCircle(color = Color.White.copy(alpha = 0.85f), radius = h * 0.18f, center = Offset(x, h / 2f))
    }
    Text(
        text = "${valuePercent.roundToInt()}%",
        style = MaterialTheme.typography.labelLarge,
        color = severityColor(sohSeverity(
            when {
                valuePercent >= 90f -> SohBand.EXCELLENT
                valuePercent >= 80f -> SohBand.GOOD
                valuePercent >= 70f -> SohBand.FAIR
                else -> SohBand.POOR
            },
        )),
    )
}

@Composable
private fun SohLegend(band: SohBand) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LegendRow(HealthSeverity.OK, stringResource(R.string.diagnostics_soh_excellent))
        LegendRow(HealthSeverity.OK, stringResource(R.string.diagnostics_soh_good))
        LegendRow(HealthSeverity.CAUTION, stringResource(R.string.diagnostics_soh_fair))
        LegendRow(HealthSeverity.EMERGENCY, stringResource(R.string.diagnostics_soh_poor))
        Text(
            text = stringResource(R.string.diagnostics_soh_current_band, sohBandLabel(band)),
            style = MaterialTheme.typography.titleSmall,
            color = severityColor(sohSeverity(band)),
        )
    }
}

@Composable
private fun LegendRow(severity: HealthSeverity, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(severityColor(severity)),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SemanticBar(
    fraction: Float,
    severity: HealthSeverity,
) {
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp)),
        color = severityColor(severity),
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )
}

@Composable
private fun DigitalTwinCanvas(
    hotspots: List<TwinHotspot>,
    modifier: Modifier = Modifier,
) {
    val body = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val cabin = MaterialTheme.colorScheme.surfaceVariant
    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
    ) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val density = LocalDensity.current
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRoundRect(
                color = body,
                topLeft = Offset(w * 0.28f, h * 0.08f),
                size = Size(w * 0.44f, h * 0.84f),
                cornerRadius = CornerRadius(w * 0.12f, w * 0.12f),
            )
            drawRoundRect(
                color = cabin,
                topLeft = Offset(w * 0.34f, h * 0.22f),
                size = Size(w * 0.32f, h * 0.28f),
                cornerRadius = CornerRadius(18f, 18f),
            )
            drawRoundRect(
                color = cabin,
                topLeft = Offset(w * 0.34f, h * 0.54f),
                size = Size(w * 0.32f, h * 0.22f),
                cornerRadius = CornerRadius(18f, 18f),
            )
        }
        hotspots.forEach { spot ->
            val xDp = with(density) { (widthPx * spot.xFraction).toDp() } - 10.dp
            val yDp = with(density) { (heightPx * spot.yFraction).toDp() } - 10.dp
            Box(
                modifier = Modifier
                    .offset(x = xDp, y = yDp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(severityColor(spot.severity)),
            )
        }
    }
}

@Composable
private fun severityColor(severity: HealthSeverity): Color {
    val dark = isSystemInDarkTheme()
    return when (severity) {
        HealthSeverity.OK -> if (dark) DarkGreen else LightGreen
        HealthSeverity.CAUTION -> if (dark) DarkAmber else LightAmber
        HealthSeverity.WARNING -> if (dark) DarkAmber else LightAmber
        HealthSeverity.EMERGENCY -> if (dark) DarkRed else LightRed
    }
}

@Composable
private fun severityLabel(severity: HealthSeverity): String = stringResource(
    when (severity) {
        HealthSeverity.OK -> R.string.diagnostics_severity_ok
        HealthSeverity.CAUTION -> R.string.diagnostics_severity_caution
        HealthSeverity.WARNING -> R.string.diagnostics_severity_warning
        HealthSeverity.EMERGENCY -> R.string.diagnostics_severity_emergency
    },
)

private fun sohSeverity(band: SohBand): HealthSeverity = when (band) {
    SohBand.EXCELLENT, SohBand.GOOD -> HealthSeverity.OK
    SohBand.FAIR -> HealthSeverity.CAUTION
    SohBand.POOR -> HealthSeverity.EMERGENCY
}

@Composable
private fun sohBandLabel(band: SohBand): String = stringResource(
    when (band) {
        SohBand.EXCELLENT -> R.string.diagnostics_soh_band_excellent
        SohBand.GOOD -> R.string.diagnostics_soh_band_good
        SohBand.FAIR -> R.string.diagnostics_soh_band_fair
        SohBand.POOR -> R.string.diagnostics_soh_band_poor
    },
)
