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

    /**
     * A `delivery_*` command for [com.sopa.viva_automotive.feature.voice.domain.delivery.DeliverySkill].
     *
     * It rides in the same sealed hierarchy as the vehicle commands so one
     * turn pipeline handles every intent, but it never reaches
     * `VehicleRepository` — 03-contracts.md §0.1 keeps `delivery_*` off the
     * VHAL path entirely.
     */
    data class Delivery(val command: DeliveryCommand) : VehicleIntent

    /**
     * The grammar understood the command but this build has no adapter that can
     * carry it out yet (media transport, delivery skill).
     *
     * Kept distinct from [Unknown] on purpose: telling the driver "I didn't
     * understand" for a command we routed correctly is a false statement, and it
     * hides the gap from the demo instead of labelling it.
     */
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
}
