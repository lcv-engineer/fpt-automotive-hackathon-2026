package com.sopa.viva_automotive.feature.voice.domain

import com.sopa.viva_automotive.feature.voice.data.CommandMappingRepository
import com.sopa.viva_automotive.feature.voice.domain.embedding.SemanticIntentMatcher
import com.sopa.viva_automotive.feature.voice.integration.AutomotiveVoiceAction
import com.sopa.viva_automotive.feature.voice.integration.CoreIntentMapper
import com.sopa.viva_automotive.feature.voice.domain.model.VehicleIntent
import com.sopa.viva_automotive.feature.voice.domain.model.VehicleIntentTypes
import com.sopa.viva_automotive.vehicleservice.api.VehicleZone
import com.viva.voice.intent.GrammarIntentRouter
import com.viva.voice.intent.RouteResult
import javax.inject.Inject

class ProcessVoiceCommandUseCase @Inject constructor(
    private val commandMappingRepository: CommandMappingRepository,
    private val semanticIntentMatcher: SemanticIntentMatcher,
) {

    private val grammarRouter = GrammarIntentRouter()

    suspend operator fun invoke(utterance: String): VehicleIntent {
        val text = normalize(utterance)
        if (text.isBlank()) return VehicleIntent.Unknown(utterance)

        when (val coreRoute = grammarRouter.route(utterance)) {
            is RouteResult.Matched -> return when (val action = CoreIntentMapper.map(coreRoute.intent)) {
                is AutomotiveVoiceAction.VehicleControl -> action.intent
                is AutomotiveVoiceAction.VolumeAdjust -> VehicleIntent.VolumeAdjust(action.delta)
                AutomotiveVoiceAction.MediaNext -> VehicleIntent.MediaNext
                is AutomotiveVoiceAction.Delivery -> VehicleIntent.Delivery(action.command)
                // The mapper returns null for two different reasons, and they owe
                // the driver two different answers: a vehicle intent it could not
                // fill (bad slot) is genuinely not understood, while a media or
                // delivery intent simply has no adapter in this build.
                null -> if (isVehicleIntent(coreRoute.intent.name)) {
                    VehicleIntent.Unknown(utterance)
                } else {
                    VehicleIntent.NotWired(coreRoute.intent.name)
                }
            }
            is RouteResult.NeedsClarification ->
                return VehicleIntent.Clarification(coreRoute.promptVi)
            is RouteResult.Unsupported ->
                if (!coreRoute.canFallback) {
                    return VehicleIntent.Clarification(coreRoute.promptVi)
                }
        }

        val keywordIntent = commandMappingRepository.getMappings()
            .sortedByDescending { it.priority }
            .firstOrNull { mapping -> mapping.keywords.any { keyword -> keyword in text } }
            ?.intentType

        val intentType = keywordIntent
            ?: semanticIntentMatcher.bestIntent(text)
            ?: return VehicleIntent.Unknown(utterance)

        return toVehicleIntent(intentType, text, utterance)
    }

    private fun toVehicleIntent(
        intentType: String,
        text: String,
        originalUtterance: String,
    ): VehicleIntent {
        val number = SpokenNumberParser.parse(text)
        val zone = extractZone(text)

        return when (intentType) {
            VehicleIntentTypes.SET_TEMPERATURE ->
                if (number != null) {
                    VehicleIntent.SetTemperature(number, zone)
                } else {
                    VehicleIntent.Unknown(originalUtterance)
                }
            VehicleIntentTypes.TEMPERATURE_UP -> VehicleIntent.AdjustTemperature(number ?: 1.0, zone)
            VehicleIntentTypes.TEMPERATURE_DOWN -> VehicleIntent.AdjustTemperature(-(number ?: 1.0), zone)
            VehicleIntentTypes.SET_FAN_SPEED ->
                if (number != null) {
                    VehicleIntent.SetFanSpeed(number.toInt())
                } else {
                    VehicleIntent.Unknown(originalUtterance)
                }
            VehicleIntentTypes.FAN_UP -> VehicleIntent.AdjustFanSpeed(1)
            VehicleIntentTypes.FAN_DOWN -> VehicleIntent.AdjustFanSpeed(-1)
            VehicleIntentTypes.AC_ON -> VehicleIntent.SetAc(true)
            VehicleIntentTypes.AC_OFF -> VehicleIntent.SetAc(false)
            VehicleIntentTypes.HVAC_ON -> VehicleIntent.SetHvacPower(true)
            VehicleIntentTypes.HVAC_OFF -> VehicleIntent.SetHvacPower(false)
            VehicleIntentTypes.LOCK_DOORS -> VehicleIntent.SetDoorLock(true)
            VehicleIntentTypes.UNLOCK_DOORS -> VehicleIntent.SetDoorLock(false)
            VehicleIntentTypes.QUERY_SPEED -> VehicleIntent.QueryStatus(VehicleIntent.StatusQueryKind.SPEED)
            VehicleIntentTypes.QUERY_FUEL -> VehicleIntent.QueryStatus(VehicleIntent.StatusQueryKind.FUEL)
            VehicleIntentTypes.QUERY_BATTERY -> VehicleIntent.QueryStatus(VehicleIntent.StatusQueryKind.BATTERY)
            VehicleIntentTypes.QUERY_TEMPERATURE -> VehicleIntent.QueryStatus(VehicleIntent.StatusQueryKind.TEMPERATURE)
            else -> VehicleIntent.Unknown(originalUtterance)
        }
    }

    /** Intent families that reach the vehicle through [ExecuteVehicleControlUseCase]. */
    private fun isVehicleIntent(intentName: String): Boolean =
        intentName.startsWith("hvac_") || intentName == "door_lock"

        private fun normalize(utterance: String): String =
        utterance.lowercase().trim()
            .replace(Regex("""\s+"""), " ")
            .replace(Regex("""\ba\s+c\b"""), "ac")
            .replace(Regex("""\bair\s*con\b"""), "aircon")

    private fun extractZone(text: String): VehicleZone = when {
        "passenger" in text ||
            "hành khách" in text ||
            "hanh khach" in text ||
            "ghế phụ" in text ||
            "ghe phu" in text -> VehicleZone.PASSENGER
        "everyone" in text ||
            "everywhere" in text ||
            "all zones" in text ||
            "tất cả" in text ||
            "tat ca" in text ||
            "cả xe" in text -> VehicleZone.ALL
        "tài xế" in text ||
            "tai xe" in text ||
            "driver" in text -> VehicleZone.DRIVER
        else -> VehicleZone.DRIVER
    }
}
