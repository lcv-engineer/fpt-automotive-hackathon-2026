package com.sopa.viva_automotive.feature.voice.integration

import com.sopa.viva_automotive.core.common.units.TemperatureUnits
import com.sopa.viva_automotive.feature.voice.domain.delivery.DeliveryCommand
import com.sopa.viva_automotive.feature.voice.domain.media.MediaCommand
import com.sopa.viva_automotive.feature.voice.domain.model.VehicleIntent
import com.sopa.viva_automotive.vehicleservice.api.FanSpeed
import com.viva.voice.intent.Intent

/** Action understood by Dương's app layer after Long's voice core has routed text. */
sealed interface AutomotiveVoiceAction {
    data class VehicleControl(val intent: VehicleIntent) : AutomotiveVoiceAction
    data class VolumeAdjust(val delta: Int) : AutomotiveVoiceAction
    data class Media(val command: MediaCommand) : AutomotiveVoiceAction

    /** `delivery_*` — handled in-app, never reaches VHAL (03-contracts.md §0.1). */
    data class Delivery(val command: DeliveryCommand) : AutomotiveVoiceAction
}

/**
 * The only type translation between `:voice-core` and `:feature:voice`.
 *
 * Returning null is deliberate: malformed slots stop at the module boundary
 * instead of becoming a default vehicle command.
 */
object CoreIntentMapper {
    fun map(intent: Intent): AutomotiveVoiceAction? = when (intent.name) {
        "hvac_set_temp" -> intent.number("value")
            ?.toDouble()
            ?.takeIf { value ->
                value.isFinite() && value in supportedTemperatureRange
            }
            ?.let { value ->
                AutomotiveVoiceAction.VehicleControl(
                    VehicleIntent.SetTemperature(value),
                )
            }

        "hvac_set_fan" -> intent.number("level")
            ?.toDouble()
            ?.takeIf { level ->
                level % 1.0 == 0.0 && FanSpeed.isValid(level.toInt())
            }
            ?.let { level ->
                AutomotiveVoiceAction.VehicleControl(
                    VehicleIntent.SetFanSpeed(level.toInt()),
                )
            }

        "door_lock" -> (intent.slots["lock"] as? Boolean)?.let { locked ->
            AutomotiveVoiceAction.VehicleControl(VehicleIntent.SetDoorLock(locked))
        }

        "volume_adjust" -> intent.number("delta")?.let { delta ->
            AutomotiveVoiceAction.VolumeAdjust(delta.toInt())
        }

        "media_play" -> AutomotiveVoiceAction.Media(MediaCommand.PLAY)

        "media_pause" -> AutomotiveVoiceAction.Media(MediaCommand.PAUSE)

        "media_next" -> AutomotiveVoiceAction.Media(MediaCommand.NEXT)

        // The order id slot is optional by design: "xác nhận giao thành công"
        // without an id means the stop the driver is currently on, and the
        // skill resolves that — the mapper does not guess one here.
        "delivery_next_stop" -> AutomotiveVoiceAction.Delivery(DeliveryCommand.NextStop)

        "delivery_order_status" ->
            AutomotiveVoiceAction.Delivery(DeliveryCommand.OrderStatus(intent.text("orderId")))

        "delivery_confirm" ->
            AutomotiveVoiceAction.Delivery(DeliveryCommand.Confirm(intent.text("orderId")))

        else -> null
    }

    private fun Intent.number(name: String): Number? = slots[name] as? Number

    private fun Intent.text(name: String): String? =
        (slots[name] as? String)?.trim()?.takeIf { it.isNotEmpty() }

    private val supportedTemperatureRange =
        TemperatureUnits.MIN_CELSIUS.toDouble()..TemperatureUnits.MAX_CELSIUS.toDouble()
}
