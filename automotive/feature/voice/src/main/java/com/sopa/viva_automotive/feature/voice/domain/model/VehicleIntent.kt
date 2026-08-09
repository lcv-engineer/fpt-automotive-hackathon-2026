package com.sopa.viva_automotive.feature.voice.domain.model

import com.sopa.viva_automotive.feature.voice.domain.delivery.DeliveryCommand
import com.sopa.viva_automotive.feature.voice.domain.media.MediaCommand
import com.sopa.viva_automotive.vehicleservice.api.VehicleZone

sealed interface VehicleIntent {

    data class SetTemperature(
        val temperatureCelsius: Double,
        val zone: VehicleZone = VehicleZone.DRIVER,
    ) : VehicleIntent

    data class AdjustTemperature(
        val deltaCelsius: Double,
        val zone: VehicleZone = VehicleZone.DRIVER,
    ) : VehicleIntent

    data class SetFanSpeed(val level: Int) : VehicleIntent

    data class AdjustFanSpeed(val delta: Int) : VehicleIntent

    data class SetAc(val on: Boolean) : VehicleIntent

    data class SetHvacPower(val on: Boolean) : VehicleIntent

    data class SetDoorLock(val locked: Boolean) : VehicleIntent

    data class QueryStatus(val kind: StatusQueryKind) : VehicleIntent

    data class VolumeAdjust(val delta: Int) : VehicleIntent

    data class Media(val command: MediaCommand) : VehicleIntent

    data class RadioTune(val query: String? = null) : VehicleIntent

    data object RadioNextStation : VehicleIntent

    data class Delivery(val command: DeliveryCommand) : VehicleIntent

    data class NotWired(val intentName: String) : VehicleIntent

    data class Clarification(val promptVi: String) : VehicleIntent

    data class Unknown(val utterance: String) : VehicleIntent

    enum class StatusQueryKind { SPEED, FUEL, BATTERY, TEMPERATURE }
}

object VehicleIntentTypes {
    const val SET_TEMPERATURE = "SET_TEMPERATURE"
    const val TEMPERATURE_UP = "TEMPERATURE_UP"
    const val TEMPERATURE_DOWN = "TEMPERATURE_DOWN"
    const val SET_FAN_SPEED = "SET_FAN_SPEED"
    const val FAN_UP = "FAN_UP"
    const val FAN_DOWN = "FAN_DOWN"
    const val AC_ON = "AC_ON"
    const val AC_OFF = "AC_OFF"
    const val HVAC_ON = "HVAC_ON"
    const val HVAC_OFF = "HVAC_OFF"
    const val LOCK_DOORS = "LOCK_DOORS"
    const val UNLOCK_DOORS = "UNLOCK_DOORS"
    const val QUERY_SPEED = "QUERY_SPEED"
    const val QUERY_FUEL = "QUERY_FUEL"
    const val QUERY_BATTERY = "QUERY_BATTERY"
    const val QUERY_TEMPERATURE = "QUERY_TEMPERATURE"
    const val MEDIA_PLAY = "MEDIA_PLAY"
    const val MEDIA_PAUSE = "MEDIA_PAUSE"
    const val MEDIA_NEXT = "MEDIA_NEXT"
    const val RADIO_TUNE = "RADIO_TUNE"
    const val RADIO_NEXT = "RADIO_NEXT"
    const val VOLUME_UP = "VOLUME_UP"
    const val VOLUME_DOWN = "VOLUME_DOWN"
}
