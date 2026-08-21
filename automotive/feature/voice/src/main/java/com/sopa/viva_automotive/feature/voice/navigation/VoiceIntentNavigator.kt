package com.sopa.viva_automotive.feature.voice.navigation

import com.viva.voice.agent.VoiceTurnStatus

/**
 * Maps a finished voice turn to the cabin tab that should be shown so the
 * driver sees the surface the command just touched.
 */
object VoiceIntentNavigator {

    fun routeFor(intentName: String?, status: VoiceTurnStatus): String? {
        if (intentName.isNullOrBlank()) return null
        return when (status) {
            VoiceTurnStatus.APPLIED,
            VoiceTurnStatus.PARTIALLY_APPLIED,
            -> routeForApplied(intentName)
            // Door unlock / delivery confirm: show context while the driver answers.
            VoiceTurnStatus.NEEDS_CONFIRMATION -> routeForConfirmation(intentName)
            // HVAC "nóng quá / lạnh quá" asks for a value — open climate to edit.
            VoiceTurnStatus.NEEDS_CLARIFICATION -> routeForClarification(intentName)
            VoiceTurnStatus.DENIED,
            VoiceTurnStatus.UNSUPPORTED,
            VoiceTurnStatus.FAILED,
            -> null
        }
    }

    private fun routeForApplied(intentName: String): String? = when (intentName) {
        "media_play", "media_pause", "media_next", "media_favorite" -> AppRoutes.MEDIA
        "hvac_set_temp", "hvac_set_fan" -> AppRoutes.HVAC
        "door_lock", "cabin_lights",
        "vehicle_status_speed", "vehicle_status_fuel",
        "vehicle_status_battery", "vehicle_status_temperature",
        -> AppRoutes.STATUS
        "volume_adjust" -> AppRoutes.MEDIA
        "delivery_next_stop", "delivery_order_status", "delivery_confirm" -> AppRoutes.HOME
        else -> null
    }

    private fun routeForConfirmation(intentName: String): String? = when (intentName) {
        "door_lock" -> AppRoutes.STATUS
        "delivery_confirm" -> AppRoutes.HOME
        else -> null
    }

    private fun routeForClarification(intentName: String): String? = when (intentName) {
        "hvac_set_temp", "hvac_set_fan" -> AppRoutes.HVAC
        else -> null
    }
}
