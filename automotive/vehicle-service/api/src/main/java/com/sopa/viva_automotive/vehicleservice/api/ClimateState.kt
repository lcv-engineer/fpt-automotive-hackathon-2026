package com.sopa.viva_automotive.vehicleservice.api

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.scan

data class ClimateState(
    val driverTempCelsius: Float = 22f,
    val passengerTempCelsius: Float = 22f,
    val fanSpeed: Int = 0,
    val fanDirection: Int = FanDirection.FACE,
    val acOn: Boolean = false,
    val autoOn: Boolean = false,
    val hvacPowerOn: Boolean = true,
) {
    internal fun update(prop: CarPropertyResult): ClimateState = when (prop.propertyId) {
        VehicleProperties.HVAC_TEMPERATURE_SET -> when {
            // ROW_1_RIGHT (0x4) or legacy passenger mask 0x44
            prop.areaId and VehicleAreas.SEAT_ZONE_PASSENGER != 0 ||
                prop.areaId == VehicleAreas.LEGACY_SEAT_ZONE_PASSENGER ->
                copy(passengerTempCelsius = prop.floatValue() ?: passengerTempCelsius)
            // ROW_1_LEFT (0x1) or legacy driver mask 0x31
            prop.areaId and VehicleAreas.SEAT_ZONE_DRIVER != 0 ||
                prop.areaId == VehicleAreas.LEGACY_SEAT_ZONE_DRIVER ->
                copy(driverTempCelsius = prop.floatValue() ?: driverTempCelsius)
            else -> this
        }
        VehicleProperties.HVAC_FAN_SPEED -> copy(fanSpeed = prop.intValue() ?: fanSpeed)
        VehicleProperties.HVAC_FAN_DIRECTION -> copy(fanDirection = prop.intValue() ?: fanDirection)
        VehicleProperties.HVAC_AC_ON -> copy(acOn = prop.booleanValue() ?: acOn)
        VehicleProperties.HVAC_AUTO_ON -> copy(autoOn = prop.booleanValue() ?: autoOn)
        VehicleProperties.HVAC_POWER_ON -> copy(hvacPowerOn = prop.booleanValue() ?: hvacPowerOn)
        else -> this
    }
}

@Singleton
class ClimateStateObserver @Inject constructor(
    private val vehicle: VehicleRepository,
) {
    val climateState: Flow<ClimateState> = merge(
        vehicle.observeProperty(VehicleProperties.HVAC_TEMPERATURE_SET),
        vehicle.observeProperty(VehicleProperties.HVAC_FAN_SPEED),
        vehicle.observeProperty(VehicleProperties.HVAC_FAN_DIRECTION),
        vehicle.observeProperty(VehicleProperties.HVAC_AC_ON),
        vehicle.observeProperty(VehicleProperties.HVAC_AUTO_ON),
        vehicle.observeProperty(VehicleProperties.HVAC_POWER_ON),
    ).scan(ClimateState()) { state, prop -> state.update(prop) }
}
