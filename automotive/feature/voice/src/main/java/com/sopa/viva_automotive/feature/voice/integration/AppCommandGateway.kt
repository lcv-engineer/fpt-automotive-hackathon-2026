package com.sopa.viva_automotive.feature.voice.integration

import com.sopa.viva_automotive.feature.voice.domain.CommandNotWiredException
import com.sopa.viva_automotive.feature.voice.domain.CommandValidationException
import com.sopa.viva_automotive.feature.voice.domain.ConfirmationRequiredException
import com.sopa.viva_automotive.feature.voice.domain.ExecuteVehicleControlUseCase
import com.sopa.viva_automotive.feature.voice.domain.model.VehicleIntent
import com.sopa.viva_automotive.vehicleservice.api.SafetyConfirmationRequiredException
import com.sopa.viva_automotive.vehicleservice.api.SafetyDeniedException
import com.viva.voice.agent.CommandGateway
import com.viva.voice.agent.CommandResult
import com.viva.voice.intent.Intent
import com.viva.voice.trace.LatencyTrace
import com.viva.voice.trace.Stage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppCommandGateway @Inject constructor(
    private val executeVehicleControl: ExecuteVehicleControlUseCase,
) : CommandGateway {

    override suspend fun execute(intent: Intent, trace: LatencyTrace): CommandResult {
        val action = CoreIntentMapper.map(intent)
            ?: return CommandResult.Failed("No adapter for intent \"${intent.name}\"")

        val vehicleIntent = when (action) {
            is AutomotiveVoiceAction.VehicleControl -> action.intent
            is AutomotiveVoiceAction.VolumeAdjust -> VehicleIntent.VolumeAdjust(action.delta)
            is AutomotiveVoiceAction.Media -> VehicleIntent.Media(action.command)
            is AutomotiveVoiceAction.Delivery -> VehicleIntent.Delivery(action.command)
        }

        return executeVehicleControl(vehicleIntent).fold(
            onSuccess = { spoken ->
                trace.mark(Stage.EXEC_DONE)
                CommandResult.Applied(spokenVi = spoken)
            },
            onFailure = { error ->
                when (error) {
                    is SafetyDeniedException ->
                        CommandResult.Denied(error.rule, error.reasonVi)
                    is SafetyConfirmationRequiredException ->
                        CommandResult.ConfirmationRequired(error.rule, error.questionVi)
                    is ConfirmationRequiredException ->
                        CommandResult.ConfirmationRequired(error.rule, error.questionVi)
                    is CommandValidationException ->
                        CommandResult.Failed(error.message ?: "validation failed")
                    is CommandNotWiredException ->
                        CommandResult.Failed(error.message ?: "not wired")
                    else -> CommandResult.Failed(error.message ?: "execution failed")
                }
            },
        )
    }
}
