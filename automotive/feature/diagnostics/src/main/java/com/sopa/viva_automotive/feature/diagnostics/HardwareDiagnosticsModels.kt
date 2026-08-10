package com.sopa.viva_automotive.feature.diagnostics

/**
 * Hardware diagnostics snapshot for AAOS HMI.
 *
 * Live signals come from [com.sopa.viva_automotive.vehicleservice.api.VehicleRepository]
 * (CarPropertyManager / mock VHAL). SOH, tire pressures, predictive items, OTA, and
 * digital-twin hotspots are vendor/demo until OEM properties are wired.
 */
enum class HealthSeverity {
    OK,
    CAUTION,
    WARNING,
    EMERGENCY,
}

enum class UnitType {
    HVAC,
    BODY,
    LIGHTS,
    DOORS,
}

enum class SohBand {
    EXCELLENT,
    GOOD,
    FAIR,
    POOR,
}

enum class OtaPhase {
    UP_TO_DATE,
    AVAILABLE,
    DOWNLOADING,
    INSTALLING,
    FAILED,
}

enum class DiagnosticsSection {
    OVERVIEW,
    BATTERY,
    SENSORS,
    UNITS,
    ALERTS,
    TWIN,
    UPDATES,
}

data class DiagnosticTroubleCode(
    val code: String,
    val title: String,
    val severity: DtcSeverity,
    /** Short driver-facing guidance (microcopy), not just a tech code. */
    val guidance: String,
    /** Maps to a digital-twin hotspot id when present. */
    val componentId: String? = null,
)

enum class DtcSeverity {
    INFO,
    WARNING,
    CRITICAL,
}

data class SystemHealthMetrics(
    val memoryHealthPercent: Int = 100,
    val usedMemoryMb: Long = 0L,
    val maxMemoryMb: Long = 0L,
    val flashHealthPercent: Int = 100,
    val cpuLoadHintPercent: Int = 0,
    val watchdogOk: Boolean = true,
)

data class SensorReading(
    val id: String,
    val label: String,
    val valueLabel: String,
    val detail: String,
    val severity: HealthSeverity,
)

data class UnitStatusItem(
    val type: UnitType,
    val label: String,
    val statusLabel: String,
    val detail: String,
    val severity: HealthSeverity,
)

data class PredictiveAlert(
    val id: String,
    val title: String,
    val detail: String,
    val severity: HealthSeverity,
    val componentId: String,
    val etaLabel: String,
)

data class TwinHotspot(
    val id: String,
    /** 0..1 within the twin canvas. */
    val xFraction: Float,
    val yFraction: Float,
    val label: String,
    val severity: HealthSeverity,
)

data class OtaStatus(
    val currentVersion: String = "viva-hu-1.0.0",
    val availableVersion: String? = "viva-hu-1.0.1",
    val phase: OtaPhase = OtaPhase.AVAILABLE,
    val progress: Float = 0f,
    val channel: String = "demo",
)

data class ScanStatus(
    val isScanning: Boolean = false,
    val progress: Float = 0f,
    val etaSeconds: Int = 0,
)

data class GenAiDiagnosis(
    val steps: List<String>,
    val summary: String,
    val recommendation: String,
    val temperatureHint: String,
)

data class HardwareDiagnosticsState(
    val batterySocPercent: Float = 0f,
    val batterySohPercent: Float = 92f,
    val estimatedRangeKm: Float = 280f,
    val usableCapacityKwh: Float = 68f,
    val originalCapacityKwh: Float = 74f,
    val ignitionOn: Boolean = false,
    val speedKmh: Float = 0f,
    val fuelLevelPercent: Float = 0f,
    val cabinTempCelsius: Float = 26f,
    val sensors: List<SensorReading> = emptyList(),
    val units: List<UnitStatusItem> = emptyList(),
    val dtcs: List<DiagnosticTroubleCode> = emptyList(),
    val predictiveAlerts: List<PredictiveAlert> = emptyList(),
    val twinHotspots: List<TwinHotspot> = emptyList(),
    val systemHealth: SystemHealthMetrics = SystemHealthMetrics(),
    val genAi: GenAiDiagnosis = GenAiDiagnosis(
        steps = emptyList(),
        summary = "",
        recommendation = "",
        temperatureHint = "",
    ),
    val ota: OtaStatus = OtaStatus(),
    val scan: ScanStatus = ScanStatus(),
    val remoteDiagnosticsAvailable: Boolean = false,
) {
    val batteryDegradationPercent: Float
        get() = (100f - batterySohPercent).coerceIn(0f, 100f)

    val sohBand: SohBand
        get() = when {
            batterySohPercent >= 90f -> SohBand.EXCELLENT
            batterySohPercent >= 80f -> SohBand.GOOD
            batterySohPercent >= 70f -> SohBand.FAIR
            else -> SohBand.POOR
        }

    val highestAlertSeverity: HealthSeverity
        get() {
            val fromDtc = dtcs.maxOfOrNull {
                when (it.severity) {
                    DtcSeverity.CRITICAL -> HealthSeverity.EMERGENCY
                    DtcSeverity.WARNING -> HealthSeverity.WARNING
                    DtcSeverity.INFO -> HealthSeverity.CAUTION
                }
            } ?: HealthSeverity.OK
            val fromPredictive = predictiveAlerts.maxOfOrNull { it.severity } ?: HealthSeverity.OK
            return maxOf(fromDtc, fromPredictive)
        }
}
