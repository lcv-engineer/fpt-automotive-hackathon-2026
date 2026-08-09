package com.sopa.viva_automotive.feature.voice.domain

import com.sopa.viva_automotive.core.common.units.TemperatureUnits
import com.sopa.viva_automotive.core.database.settings.SettingsDataStore
import com.sopa.viva_automotive.feature.voice.domain.delivery.DeliveryCommand
import com.sopa.viva_automotive.feature.voice.domain.delivery.DeliverySkill
import com.sopa.viva_automotive.feature.voice.domain.audio.VolumeController
import com.sopa.viva_automotive.feature.voice.domain.media.MediaCommandExecutor
import com.sopa.viva_automotive.feature.voice.domain.model.VehicleIntent
import com.sopa.viva_automotive.vehicleservice.api.FanSpeed
import com.sopa.viva_automotive.vehicleservice.api.SafetyDeniedException
import com.sopa.viva_automotive.vehicleservice.api.SafetyConfirmationRequiredException
import com.sopa.viva_automotive.vehicleservice.api.SafetyRules
import com.sopa.viva_automotive.vehicleservice.api.VehicleAreas
import com.sopa.viva_automotive.vehicleservice.api.VehicleCommandSource
import com.sopa.viva_automotive.vehicleservice.api.VehicleProperties
import com.sopa.viva_automotive.vehicleservice.api.VehicleRepository
import com.sopa.viva_automotive.vehicleservice.api.VehicleWriteContext
import com.sopa.viva_automotive.vehicleservice.api.VehicleZone
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

class CommandValidationException(message: String) : IllegalArgumentException(message)

/**
 * The command was routed correctly but no adapter in this build can execute it.
 *
 * Separate from [CommandValidationException] so the HMI, the demo script and the
 * integration table can report "understood, not wired" without dressing it up as
 * a misheard command.
 */
class CommandNotWiredException(message: String) : IllegalStateException(message)

class ExecuteVehicleControlUseCase @Inject constructor(
    private val vehicleRepository: VehicleRepository,
    private val settingsDataStore: SettingsDataStore,
    private val deliverySkill: DeliverySkill,
    private val volumeController: VolumeController,
    private val mediaCommandExecutor: MediaCommandExecutor,
) {

    /**
     * Set when `SafetyGuard` answered an unlock with `G2_CONFIRM_DOOR` and the
     * driver has not moved on to another turn yet. The next unlock turn carries
     * `isConfirmed = true` and is allowed through.
     *
     * Held here rather than in the guard because the guard is stateless by
     * design: it sees one write at a time and cannot tell a first request from
     * an answer to its own question. Same two-turn shape as
     * `DeliverySkill.pendingConfirmation`, and same reason for no expiry clock —
     * determinism beats a timer in a demo.
     *
     * The unlock still re-runs every deny rule on the second turn, so a car that
     * started moving between the question and the answer is denied by
     * `G1_SPEED_LOCK`: confirming is not a bypass.
     */
    private var pendingDoorUnlock = false

    suspend operator fun invoke(intent: VehicleIntent): Result<String> {
        // Walking away from a pending "are you sure?" by issuing any other turn
        // answers it as "no". This lives here, at the single point every intent
        // flows through, because DeliverySkill.execute() only ever sees delivery
        // intents — it cannot observe the non-delivery turn that must cancel the
        // confirmation, which is how the two-turn guard was defeated before.
        if (!retainsPendingConfirmation(intent)) {
            deliverySkill.clearPendingConfirmation()
        }
        if (!retainsPendingDoorUnlock(intent)) {
            pendingDoorUnlock = false
        }
        return dispatch(intent)
    }

    private suspend fun dispatch(intent: VehicleIntent): Result<String> = when (intent) {
        is VehicleIntent.SetTemperature -> setTemperature(intent.temperatureCelsius, intent.zone)

        is VehicleIntent.AdjustTemperature -> {
            val zone = concreteZones(intent.zone).first()
            currentFloat(VehicleProperties.HVAC_TEMPERATURE_SET, zone.areaId).mapCatching { current ->
                val target = (current + intent.deltaCelsius).coerceIn(
                    TemperatureUnits.MIN_CELSIUS.toDouble(),
                    TemperatureUnits.MAX_CELSIUS.toDouble(),
                )
                setTemperature(target, intent.zone).getOrThrow()
            }
        }

        is VehicleIntent.SetFanSpeed -> {
            if (!FanSpeed.isValid(intent.level)) {
                Result.failure(
                    CommandValidationException(
                        VehicleControlResponses.fanSpeedOutOfRange(FanSpeed.MIN_LEVEL, FanSpeed.MAX_LEVEL),
                    ),
                )
            } else {
                vehicleRepository
                    .setProperty(VehicleProperties.HVAC_FAN_SPEED, VehicleAreas.GLOBAL, intent.level)
                    .map { VehicleControlResponses.fanSpeed(intent.level) }
            }
        }

        is VehicleIntent.AdjustFanSpeed ->
            currentInt(VehicleProperties.HVAC_FAN_SPEED, VehicleAreas.GLOBAL).mapCatching { current ->
                val target = FanSpeed.clamp(current + intent.delta)
                vehicleRepository
                    .setProperty(VehicleProperties.HVAC_FAN_SPEED, VehicleAreas.GLOBAL, target)
                    .getOrThrow()
                VehicleControlResponses.fanSpeed(target)
            }

        is VehicleIntent.SetAc ->
            vehicleRepository
                .setProperty(VehicleProperties.HVAC_AC_ON, VehicleAreas.GLOBAL, intent.on)
                .map { VehicleControlResponses.airConditioning(intent.on) }

        is VehicleIntent.SetHvacPower ->
            vehicleRepository
                .setProperty(VehicleProperties.HVAC_POWER_ON, VehicleAreas.GLOBAL, intent.on)
                .map { VehicleControlResponses.climatePower(intent.on) }

        is VehicleIntent.SetDoorLock -> setDoorLock(intent.locked)

        is VehicleIntent.QueryStatus -> queryStatus(intent.kind)

        // Delivery is in-app state, not a vehicle property: it goes to the
        // skill directly and never touches vehicleRepository (§0.1).
        is VehicleIntent.Delivery -> deliverySkill.execute(intent.command)

        is VehicleIntent.VolumeAdjust -> volumeController.adjust(intent.delta)

        is VehicleIntent.Media -> mediaCommandExecutor.execute(intent.command)

        is VehicleIntent.NotWired ->
            Result.failure(
                CommandNotWiredException(
                    "Mình nhận đúng lệnh “${intent.intentName}”, nhưng bản demo này chưa có phần thực thi cho nó.",
                ),
            )

        is VehicleIntent.Clarification ->
            Result.failure(CommandValidationException(intent.promptVi))

        // 03-contracts.md §4, G3_UNSUPPORTED: câu ngoài 10 intent lõi bị **từ
        // chối có lý do**, không phải im lặng hay báo lỗi kỹ thuật. Không Skill
        // nào được gọi.
        //
        // Chặn ở đây chứ không ở `GuardedVehicleRepository` vì một câu ngoài
        // phạm vi không sinh ra lệnh ghi property nào để mà chặn ở biên đó.
        is VehicleIntent.Unknown ->
            Result.failure(
                SafetyDeniedException(
                    rule = SafetyRules.UNSUPPORTED,
                    reasonVi = "Mình chỉ hỗ trợ điều hoà, cửa xe, âm lượng, nhạc và lộ trình giao hàng.",
                    suggestion = "Bạn thử nói lại theo một trong các nhóm đó nhé.",
                ),
            )
    }

    /**
     * Second turn of an unlock carries the confirmation the guard asked for.
     *
     * Locking is never gated, so [pendingDoorUnlock] is always false by the time
     * a lock reaches here — [invoke] cleared it.
     */
    private suspend fun setDoorLock(locked: Boolean): Result<String> {
        val result = vehicleRepository.setProperty(
            VehicleProperties.DOOR_LOCK,
            VehicleAreas.DOOR_ROW_1_LEFT,
            locked,
            VehicleWriteContext(
                source = VehicleCommandSource.VOICE,
                isConfirmed = pendingDoorUnlock,
            ),
        )
        // Re-armed only by G2_CONFIRM_DOOR. A denial clears it: after "xe đang
        // chạy" the driver has to ask again from the start, and a repeat is a
        // fresh request rather than an answer to a question that was never asked.
        pendingDoorUnlock = armsDoorConfirmation(result.exceptionOrNull())
        return result.map { VehicleControlResponses.driverDoor(locked) }
    }

    private suspend fun setTemperature(celsius: Double, zone: VehicleZone): Result<String> {
        if (celsius < TemperatureUnits.MIN_CELSIUS || celsius > TemperatureUnits.MAX_CELSIUS) {
            return Result.failure(
                CommandValidationException(
                    VehicleControlResponses.temperatureOutOfRange(
                        TemperatureUnits.MIN_CELSIUS.roundToInt(),
                        TemperatureUnits.MAX_CELSIUS.roundToInt(),
                    ),
                ),
            )
        }
        val value = celsius.toFloat()
        for (target in concreteZones(zone)) {
            val result = vehicleRepository.setProperty(
                VehicleProperties.HVAC_TEMPERATURE_SET,
                target.areaId,
                value,
            )
            if (result.isFailure) return result.map { "" }
        }
        return Result.success(VehicleControlResponses.temperatureTarget(value))
    }

    private suspend fun queryStatus(kind: VehicleIntent.StatusQueryKind): Result<String> =
        when (kind) {
            VehicleIntent.StatusQueryKind.SPEED ->
                currentFloat(VehicleProperties.PERF_VEHICLE_SPEED, VehicleAreas.GLOBAL)
                    .map { VehicleControlResponses.currentSpeed(it) }
            VehicleIntent.StatusQueryKind.FUEL ->
                currentFloat(VehicleProperties.FUEL_LEVEL, VehicleAreas.GLOBAL)
                    .map { VehicleControlResponses.fuelLevel(it) }
            VehicleIntent.StatusQueryKind.BATTERY ->
                currentFloat(VehicleProperties.EV_BATTERY_LEVEL, VehicleAreas.GLOBAL)
                    .map { VehicleControlResponses.batteryLevel(it) }
            VehicleIntent.StatusQueryKind.TEMPERATURE ->
                currentFloat(VehicleProperties.HVAC_TEMPERATURE_SET, VehicleAreas.SEAT_ZONE_DRIVER)
                    .map { VehicleControlResponses.temperatureSetting(formatTemp(it)) }
        }

    private suspend fun formatTemp(celsius: Float): String {
        val useFahrenheit = settingsDataStore.settings.first().useFahrenheit
        return TemperatureUnits.format(celsius, useFahrenheit)
    }

        private fun concreteZones(zone: VehicleZone): List<VehicleZone> = when (zone) {
        VehicleZone.ALL -> listOf(VehicleZone.DRIVER, VehicleZone.PASSENGER)
        else -> listOf(zone)
    }

    private suspend fun currentFloat(propertyId: Int, areaId: Int): Result<Float> =
        vehicleRepository.getProperty(propertyId, areaId).mapCatching {
            it.floatValue() ?: error("Property $propertyId has no numeric value")
        }

    private suspend fun currentInt(propertyId: Int, areaId: Int): Result<Int> =
        vehicleRepository.getProperty(propertyId, areaId).mapCatching {
            it.intValue() ?: error("Property $propertyId has no numeric value")
        }

    companion object {
        /**
         * Whether [intent] should leave a pending delivery confirmation intact.
         *
         * Only the second turn of a `delivery_confirm` may answer the first;
         * every other intent — including delivery_next_stop / delivery_order_status
         * and any vehicle command — cancels the pending question. Extracted so the
         * cross-turn guard can be tested without an Android `Context` (which
         * constructing this use case would otherwise require via SettingsDataStore).
         */
        fun retainsPendingConfirmation(intent: VehicleIntent): Boolean =
            intent is VehicleIntent.Delivery && intent.command is DeliveryCommand.Confirm

        /**
         * Whether [intent] leaves a pending door-unlock confirmation intact.
         *
         * Only repeating the unlock answers the question — the grammar has no
         * yes/no intent (`GrammarIntentRouter.kt:54` matches "mở cửa" / "mở khóa
         * cửa"), so "có" would route to [VehicleIntent.Unknown] and cancel the
         * confirmation. Locking the door cancels it too: that is the driver
         * changing their mind, not confirming.
         */
        fun retainsPendingDoorUnlock(intent: VehicleIntent): Boolean =
            intent is VehicleIntent.SetDoorLock && !intent.locked

        /**
         * Whether [error] is the guard asking about a door unlock specifically.
         *
         * Deliberately not "any confirmation": `G3_LOW_CONFIDENCE` also returns
         * `Confirm`, but it asks the driver to *repeat the command*, not to
         * approve it. Arming on it would let a misheard unlock through on the
         * repeat without the safety question ever being asked.
         */
        fun armsDoorConfirmation(error: Throwable?): Boolean =
            error is SafetyConfirmationRequiredException && error.rule == SafetyRules.CONFIRM_DOOR
    }
}
