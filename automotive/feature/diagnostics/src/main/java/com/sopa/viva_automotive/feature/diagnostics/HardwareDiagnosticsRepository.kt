package com.sopa.viva_automotive.feature.diagnostics

import com.sopa.viva_automotive.vehicleservice.api.ClimateState
import com.sopa.viva_automotive.vehicleservice.api.ClimateStateObserver
import com.sopa.viva_automotive.vehicleservice.api.VehicleProperties
import com.sopa.viva_automotive.vehicleservice.api.VehicleRepository
import com.sopa.viva_automotive.vehicleservice.api.VehicleStatus
import com.sopa.viva_automotive.vehicleservice.api.VehicleStatusObserver
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Singleton
class HardwareDiagnosticsRepository @Inject constructor(
    private val vehicle: VehicleRepository,
    vehicleStatusObserver: VehicleStatusObserver,
    climateStateObserver: ClimateStateObserver,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val ignitionOn = MutableStateFlow(false)
    private val cabinTempCelsius = MutableStateFlow(26f)

    private val _state = MutableStateFlow(
        HardwareDiagnosticsState(
            batterySohPercent = 92f,
            estimatedRangeKm = 280f,
            usableCapacityKwh = 68f,
            originalCapacityKwh = 74f,
            dtcs = defaultDtcs(),
            predictiveAlerts = defaultPredictive(),
            twinHotspots = defaultTwinHotspots(),
            ota = OtaStatus(),
            remoteDiagnosticsAvailable = false,
        ),
    )
    val state: StateFlow<HardwareDiagnosticsState> = _state.asStateFlow()

    init {
        publishDerived(
            status = VehicleStatus(),
            climate = ClimateState(),
            systemHealth = sampleSystemHealth(),
        )

        scope.launch {
            vehicle.observeProperty(VehicleProperties.IGNITION_STATE).collect { prop ->
                ignitionOn.value = (prop.intValue() ?: 0) >= 2
            }
        }
        scope.launch {
            vehicle.observeProperty(VehicleProperties.HVAC_TEMPERATURE_CURRENT).collect { prop ->
                cabinTempCelsius.value = prop.floatValue() ?: cabinTempCelsius.value
            }
        }
        scope.launch {
            combine(
                vehicleStatusObserver.vehicleStatus,
                climateStateObserver.climateState,
                ignitionOn,
                cabinTempCelsius,
            ) { status, climate, ign, cabin ->
                Quad(status, climate, ign, cabin)
            }.collect { (status, climate, ign, cabin) ->
                publishDerived(
                    status = status,
                    climate = climate,
                    ignitionOn = ign,
                    cabinTemp = cabin,
                    systemHealth = sampleSystemHealth(),
                )
            }
        }
        scope.launch {
            while (isActive) {
                delay(3_000L)
                _state.update { current ->
                    current.copy(systemHealth = sampleSystemHealth()).let(::withGenAi)
                }
            }
        }
    }

    fun runDiagnosisRefresh() {
        _state.update { withGenAi(it.copy(systemHealth = sampleSystemHealth())) }
    }

    fun beginScan() {
        _state.update {
            it.copy(scan = ScanStatus(isScanning = true, progress = 0f, etaSeconds = 4))
        }
    }

    fun updateScanProgress(progress: Float, etaSeconds: Int) {
        _state.update {
            it.copy(
                scan = ScanStatus(
                    isScanning = true,
                    progress = progress.coerceIn(0f, 1f),
                    etaSeconds = etaSeconds.coerceAtLeast(0),
                ),
            )
        }
    }

    fun finishScan() {
        _state.update {
            withGenAi(
                it.copy(
                    scan = ScanStatus(isScanning = false, progress = 1f, etaSeconds = 0),
                    systemHealth = sampleSystemHealth(),
                ),
            )
        }
    }

    fun startOtaDownload() {
        _state.update {
            it.copy(
                ota = it.ota.copy(
                    phase = OtaPhase.DOWNLOADING,
                    progress = 0.05f,
                ),
            )
        }
        scope.launch {
            for (step in 1..20) {
                delay(120L)
                val p = step / 20f
                _state.update { current ->
                    current.copy(
                        ota = current.ota.copy(
                            phase = if (p < 1f) OtaPhase.DOWNLOADING else OtaPhase.INSTALLING,
                            progress = p,
                        ),
                    )
                }
            }
            delay(400L)
            _state.update { current ->
                current.copy(
                    ota = current.ota.copy(
                        phase = OtaPhase.UP_TO_DATE,
                        progress = 1f,
                        currentVersion = current.ota.availableVersion ?: current.ota.currentVersion,
                        availableVersion = null,
                    ),
                )
            }
        }
    }

    private data class Quad(
        val status: VehicleStatus,
        val climate: ClimateState,
        val ignitionOn: Boolean,
        val cabinTemp: Float,
    )

    private fun publishDerived(
        status: VehicleStatus,
        climate: ClimateState,
        ignitionOn: Boolean = this.ignitionOn.value,
        cabinTemp: Float = cabinTempCelsius.value,
        systemHealth: SystemHealthMetrics,
    ) {
        val soh = _state.value.batterySohPercent
        val soc = status.batteryLevelPercent
        _state.update { current ->
            withGenAi(
                current.copy(
                    batterySocPercent = soc,
                    estimatedRangeKm = estimateRange(soc, soh),
                    ignitionOn = ignitionOn,
                    speedKmh = status.speedKmh,
                    fuelLevelPercent = status.fuelLevelPercent,
                    cabinTempCelsius = cabinTemp,
                    sensors = buildSensors(status, cabinTemp, soh),
                    units = buildUnits(status, climate),
                    systemHealth = systemHealth,
                    twinHotspots = buildTwinHotspots(current.dtcs, current.predictiveAlerts, status),
                ),
            )
        }
    }

    private fun withGenAi(state: HardwareDiagnosticsState): HardwareDiagnosticsState =
        state.copy(genAi = buildGenAiReport(state))

    private fun estimateRange(soc: Float, soh: Float): Float {
        val fullRange = 320f * (soh / 100f)
        return (fullRange * (soc / 100f)).coerceAtLeast(0f)
    }

    private fun sampleSystemHealth(): SystemHealthMetrics {
        val runtime = Runtime.getRuntime()
        val max = (runtime.maxMemory() / (1024L * 1024L)).coerceAtLeast(1L)
        val used = ((runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)).coerceAtLeast(0L)
        val memHealth = ((1f - used.toFloat() / max.toFloat()) * 100f).roundToInt().coerceIn(5, 100)
        val flashHealth = 94
        val cpuHint = ((used.toFloat() / max.toFloat()) * 55f).roundToInt().coerceIn(3, 90)
        return SystemHealthMetrics(
            memoryHealthPercent = memHealth,
            usedMemoryMb = used,
            maxMemoryMb = max,
            flashHealthPercent = flashHealth,
            cpuLoadHintPercent = cpuHint,
            watchdogOk = memHealth >= 25,
        )
    }

    private fun buildSensors(
        status: VehicleStatus,
        cabinTemp: Float,
        soh: Float,
    ): List<SensorReading> {
        // Tire pressure is demo until OEM tire properties are exposed in this VHAL map.
        val fl = 32.4f
        val fr = 32.1f
        val rl = 31.8f
        val rr = 28.6f
        return listOf(
            SensorReading(
                id = "tire_fl",
                label = "Áp suất lốp trước trái",
                valueLabel = "%.1f psi".format(fl),
                detail = "Trong ngưỡng an toàn",
                severity = HealthSeverity.OK,
            ),
            SensorReading(
                id = "tire_fr",
                label = "Áp suất lốp trước phải",
                valueLabel = "%.1f psi".format(fr),
                detail = "Trong ngưỡng an toàn",
                severity = HealthSeverity.OK,
            ),
            SensorReading(
                id = "tire_rl",
                label = "Áp suất lốp sau trái",
                valueLabel = "%.1f psi".format(rl),
                detail = "Trong ngưỡng an toàn",
                severity = HealthSeverity.OK,
            ),
            SensorReading(
                id = "tire_rr",
                label = "Áp suất lốp sau phải",
                valueLabel = "%.1f psi".format(rr),
                detail = "Thấp hơn khuyến nghị — kiểm tra sớm",
                severity = HealthSeverity.WARNING,
            ),
            SensorReading(
                id = "cabin_temp",
                label = "Nhiệt độ cabin (VHAL)",
                valueLabel = "%.0f°C".format(cabinTemp),
                detail = "HVAC_TEMPERATURE_CURRENT",
                severity = if (cabinTemp > 35f) HealthSeverity.CAUTION else HealthSeverity.OK,
            ),
            SensorReading(
                id = "pack_temp_demo",
                label = "Nhiệt độ pin (demo)",
                valueLabel = "31°C",
                detail = if (soh < 85f) "Theo dõi nhiệt khi sạc nhanh" else "Ổn định",
                severity = if (soh < 85f) HealthSeverity.CAUTION else HealthSeverity.OK,
            ),
            SensorReading(
                id = "speed",
                label = "Tốc độ xe",
                valueLabel = "${status.speedKmh.roundToInt()} km/h",
                detail = "PERF_VEHICLE_SPEED",
                severity = HealthSeverity.OK,
            ),
        )
    }

    private fun buildUnits(status: VehicleStatus, climate: ClimateState): List<UnitStatusItem> {
        val hvacSeverity = when {
            !climate.hvacPowerOn -> HealthSeverity.CAUTION
            else -> HealthSeverity.OK
        }
        val doorSeverity = when {
            status.driverDoorOpen && status.speedKmh > 5f -> HealthSeverity.EMERGENCY
            status.driverDoorOpen -> HealthSeverity.WARNING
            !status.doorsLocked -> HealthSeverity.CAUTION
            else -> HealthSeverity.OK
        }
        return listOf(
            UnitStatusItem(
                type = UnitType.HVAC,
                label = "Điều hòa (HVAC)",
                statusLabel = if (climate.hvacPowerOn) "Hoạt động" else "Tắt",
                detail = "AC ${if (climate.acOn) "ON" else "OFF"} · quạt ${climate.fanSpeed} · " +
                    "set ${climate.driverTempCelsius.roundToInt()}°C",
                severity = hvacSeverity,
            ),
            UnitStatusItem(
                type = UnitType.BODY,
                label = "Thân xe (Body)",
                statusLabel = "Sẵn sàng",
                detail = "Ignition / body domain qua VSTATE mock",
                severity = HealthSeverity.OK,
            ),
            UnitStatusItem(
                type = UnitType.LIGHTS,
                label = "Đèn cabin",
                statusLabel = if (status.cabinLightsOn) "Bật" else "Tắt",
                detail = "CABIN_LIGHTS_SWITCH",
                severity = HealthSeverity.OK,
            ),
            UnitStatusItem(
                type = UnitType.DOORS,
                label = "Cửa xe",
                statusLabel = when {
                    status.driverDoorOpen -> "Cửa lái mở"
                    status.doorsLocked -> "Đã khóa"
                    else -> "Đã mở khóa"
                },
                detail = "DOOR_LOCK / DOOR_POS",
                severity = doorSeverity,
            ),
        )
    }

    private fun buildTwinHotspots(
        dtcs: List<DiagnosticTroubleCode>,
        predictive: List<PredictiveAlert>,
        status: VehicleStatus,
    ): List<TwinHotspot> {
        val base = defaultTwinHotspots().toMutableList()
        if (status.driverDoorOpen) {
            base += TwinHotspot(
                id = "door_fl",
                xFraction = 0.22f,
                yFraction = 0.42f,
                label = "Cửa lái",
                severity = HealthSeverity.WARNING,
            )
        }
        predictive.forEach { alert ->
            if (base.none { it.id == alert.componentId }) {
                val anchor = when (alert.componentId) {
                    "tire_rr" -> TwinHotspot(alert.componentId, 0.78f, 0.72f, alert.title, alert.severity)
                    "battery_pack" -> TwinHotspot(alert.componentId, 0.50f, 0.55f, alert.title, alert.severity)
                    else -> TwinHotspot(alert.componentId, 0.50f, 0.30f, alert.title, alert.severity)
                }
                base += anchor
            }
        }
        dtcs.mapNotNull { it.componentId }.distinct().forEach { id ->
            if (base.none { it.id == id }) {
                base += TwinHotspot(id, 0.50f, 0.48f, id, HealthSeverity.WARNING)
            }
        }
        return base.distinctBy { it.id }
    }

    private fun buildGenAiReport(state: HardwareDiagnosticsState): GenAiDiagnosis {
        val steps = buildList {
            add("1. Đọc SOC (EV_BATTERY_LEVEL) = ${state.batterySocPercent.roundToInt()}%.")
            add("2. Đọc SOH (vendor/demo) = ${state.batterySohPercent.roundToInt()}% — không nhầm với SOC.")
            add("3. Ước lượng range ≈ ${state.estimatedRangeKm.roundToInt()} km từ SOC × SOH.")
            add("4. Quét DTC: ${if (state.dtcs.isEmpty()) "không có mã" else state.dtcs.joinToString { it.code }}.")
            add(
                "5. Head Unit — RAM ${state.systemHealth.memoryHealthPercent}%, " +
                    "flash ${state.systemHealth.flashHealthPercent}%, " +
                    "Watchdog ${if (state.systemHealth.watchdogOk) "OK" else "ALERT"}.",
            )
            if (state.speedKmh > 5f) {
                add("6. Xe đang chuyển động (${state.speedKmh.roundToInt()} km/h) — chỉ đề xuất hành động an toàn.")
            } else {
                add("6. Xe đứng yên / tốc độ thấp — có thể đề xuất kiểm tra sâu hơn.")
            }
        }

        val critical = state.dtcs.any { it.severity == DtcSeverity.CRITICAL }
        val lowSoc = state.batterySocPercent < 20f
        val lowSoh = state.batterySohPercent < 85f
        val memPressure = state.systemHealth.memoryHealthPercent < 35

        val summary = when {
            critical -> "Phát hiện DTC mức nghiêm trọng — ưu tiên đưa xe tới điểm dịch vụ."
            lowSoc && lowSoh -> "Pin vừa yếu (SOC) vừa suy giảm (SOH). Nên sạc sớm và theo dõi thoái hóa."
            lowSoc -> "Mức pin (SOC) thấp. SOH vẫn ổn — đây là dung lượng hiện tại, không phải độ bền."
            lowSoh -> "SOH giảm: dung lượng tối đa đã thoái hóa. SOC chỉ phản ánh phần còn lại hôm nay."
            memPressure -> "Head Unit đang áp lực bộ nhớ — theo dõi Watchdog / giải phóng app nền."
            else -> "Hệ thống phần cứng trong ngưỡng bình thường theo dữ liệu hiện có."
        }

        val recommendation = when {
            critical -> "Giữ Temperature thấp (0–0.2) khi đưa lệnh điều khiển; không mở khóa/cửa khi đang chạy."
            state.speedKmh > 5f -> "Chế độ điều khiển: Temperature thấp, Top-p thấp — chỉ cảnh báo an toàn."
            else -> "Có thể giải thích chi tiết bằng Temperature cao hơn (0.7+) sau khi đã khóa hành động an toàn."
        }

        val temperatureHint = if (critical || state.speedKmh > 5f || lowSoc) {
            "Temperature 0–0.2 · Top-p thấp (cảnh báo / điều khiển)"
        } else {
            "Temperature 0.7–1.0 · Top-p cao hơn (giải thích / trò chuyện)"
        }

        return GenAiDiagnosis(
            steps = steps,
            summary = summary,
            recommendation = recommendation,
            temperatureHint = temperatureHint,
        )
    }

    private fun defaultDtcs(): List<DiagnosticTroubleCode> = listOf(
        DiagnosticTroubleCode(
            code = "P0A80",
            title = "Gói pin hybrid/EV đang thoái hóa",
            severity = DtcSeverity.WARNING,
            guidance = "SOH đang giảm dần. Hẹn kiểm tra pin trong 2 tuần; tránh sạc nhanh liên tục khi nóng.",
            componentId = "battery_pack",
        ),
        DiagnosticTroubleCode(
            code = "U0100",
            title = "Mất liên lạc ECM/PCM (không liên tục)",
            severity = DtcSeverity.INFO,
            guidance = "Có thể tự hết sau khi khởi động lại. Nếu lặp lại khi lái, mang xe đi đọc bus.",
            componentId = "ecu_powertrain",
        ),
    )

    private fun defaultPredictive(): List<PredictiveAlert> = listOf(
        PredictiveAlert(
            id = "pred_tire_rr",
            title = "Lốp sau phải có thể non hơi",
            detail = "Xu hướng áp suất giảm ~0.4 psi/ngày trong 5 ngày gần đây (demo IoT).",
            severity = HealthSeverity.WARNING,
            componentId = "tire_rr",
            etaLabel = "Rủi ro trong ~7 ngày",
        ),
        PredictiveAlert(
            id = "pred_soh",
            title = "SOH có thể xuống dưới 90% trong quý tới",
            detail = "Dựa trên chu kỳ sạc nhanh và nhiệt độ cabin cao (mô hình demo).",
            severity = HealthSeverity.CAUTION,
            componentId = "battery_pack",
            etaLabel = "Dự báo 60–90 ngày",
        ),
    )

    private fun defaultTwinHotspots(): List<TwinHotspot> = listOf(
        TwinHotspot("battery_pack", 0.50f, 0.52f, "Gói pin", HealthSeverity.CAUTION),
        TwinHotspot("tire_rr", 0.78f, 0.74f, "Lốp RR", HealthSeverity.WARNING),
        TwinHotspot("hu", 0.50f, 0.22f, "Head Unit", HealthSeverity.OK),
    )
}
