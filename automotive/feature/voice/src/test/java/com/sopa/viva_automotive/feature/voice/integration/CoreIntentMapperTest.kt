package com.sopa.viva_automotive.feature.voice.integration

import com.sopa.viva_automotive.feature.voice.domain.delivery.DeliveryCommand
import com.sopa.viva_automotive.feature.voice.domain.media.MediaCommand
import com.sopa.viva_automotive.feature.voice.domain.model.VehicleIntent
import com.viva.voice.intent.Intent
import org.junit.Assert.assertEquals
import org.junit.Test

class CoreIntentMapperTest {

    @Test
    fun `climate temperature maps to vehicle intent`() {
        val action = CoreIntentMapper.map(intent("hvac_set_temp", "value" to 24f))

        assertEquals(
            AutomotiveVoiceAction.VehicleControl(VehicleIntent.SetTemperature(24.0)),
            action,
        )
    }

    @Test
    fun `vehicle volume and all three media commands cross the module boundary`() {
        val cases = mapOf(
            intent("hvac_set_temp", "value" to 24f) to AutomotiveVoiceAction.VehicleControl(
                VehicleIntent.SetTemperature(24.0),
            ),
            intent("hvac_set_fan", "level" to 2) to AutomotiveVoiceAction.VehicleControl(
                VehicleIntent.SetFanSpeed(2),
            ),
            intent("door_lock", "lock" to true) to AutomotiveVoiceAction.VehicleControl(
                VehicleIntent.SetDoorLock(true),
            ),
            intent("cabin_lights", "on" to true) to AutomotiveVoiceAction.VehicleControl(
                VehicleIntent.SetCabinLights(true),
            ),
            intent("volume_adjust", "delta" to -1) to AutomotiveVoiceAction.VolumeAdjust(-1),
            intent("media_play") to AutomotiveVoiceAction.Media(MediaCommand.PLAY),
            intent("media_play", "query" to "beyonce") to AutomotiveVoiceAction.Media(
                MediaCommand.PLAY,
                query = "beyonce",
            ),
            intent("media_pause") to AutomotiveVoiceAction.Media(MediaCommand.PAUSE),
            intent("media_next") to AutomotiveVoiceAction.Media(MediaCommand.NEXT),
            intent("media_favorite") to AutomotiveVoiceAction.Media(MediaCommand.FAVORITE),
        )

        cases.forEach { (input, expected) ->
            assertEquals(expected, CoreIntentMapper.map(input))
        }
    }

    @Test
    fun `the three delivery intents cross the boundary with their optional order id`() {
        assertEquals(
            AutomotiveVoiceAction.Delivery(DeliveryCommand.NextStop),
            CoreIntentMapper.map(intent("delivery_next_stop")),
        )
        assertEquals(
            AutomotiveVoiceAction.Delivery(DeliveryCommand.OrderStatus("A12")),
            CoreIntentMapper.map(intent("delivery_order_status", "orderId" to "A12")),
        )
        assertEquals(
            AutomotiveVoiceAction.Delivery(DeliveryCommand.Confirm(null)),
            CoreIntentMapper.map(intent("delivery_confirm")),
        )
    }

    @Test
    fun `status query intents map to read-only vehicle queries`() {
        assertEquals(
            AutomotiveVoiceAction.VehicleControl(
                VehicleIntent.QueryStatus(VehicleIntent.StatusQueryKind.SPEED),
            ),
            CoreIntentMapper.map(intent("vehicle_status_speed")),
        )
        assertEquals(
            AutomotiveVoiceAction.VehicleControl(
                VehicleIntent.QueryStatus(VehicleIntent.StatusQueryKind.TEMPERATURE),
            ),
            CoreIntentMapper.map(intent("vehicle_status_temperature")),
        )
    }

    @Test
    fun `missing or wrong slot returns null`() {
        assertEquals(null, CoreIntentMapper.map(intent("hvac_set_temp")))
        assertEquals(null, CoreIntentMapper.map(intent("door_lock", "lock" to "true")))
        assertEquals(null, CoreIntentMapper.map(intent("hvac_set_temp", "value" to Float.NaN)))
        assertEquals(null, CoreIntentMapper.map(intent("hvac_set_fan", "level" to 6)))
    }

    private fun intent(name: String, vararg slots: Pair<String, Any>) = Intent(
        name = name,
        slots = mapOf(*slots),
        confidence = 1f,
        tier = Intent.Tier.T0,
    )
}
