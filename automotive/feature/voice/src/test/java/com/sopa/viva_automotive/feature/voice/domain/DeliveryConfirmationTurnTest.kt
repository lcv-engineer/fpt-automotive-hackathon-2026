package com.sopa.viva_automotive.feature.voice.domain

import com.sopa.viva_automotive.feature.voice.domain.delivery.DeliveryCommand
import com.sopa.viva_automotive.feature.voice.domain.delivery.DeliverySkill
import com.sopa.viva_automotive.feature.voice.domain.delivery.InMemoryDeliveryRepository
import com.sopa.viva_automotive.feature.voice.domain.delivery.OrderState
import com.sopa.viva_automotive.feature.voice.domain.media.MediaCommand
import com.sopa.viva_automotive.feature.voice.domain.model.VehicleIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The two-turn delivery confirmation must be cancelled by ANY intervening turn,
 * not only by another delivery intent. G2_CONFIRM_DELIVERY exists so a misheard
 * ASR result cannot close someone's order; if the pending question survives an
 * unrelated command, the next confirm delivers without asking again.
 *
 * The cancel is owned by the turn dispatcher (ExecuteVehicleControlUseCase)
 * because DeliverySkill only ever sees delivery intents — a non-delivery intent
 * never reaches it, which is precisely the blind spot this test guards.
 *
 * ExecuteVehicleControlUseCase itself cannot be constructed in a JVM unit test
 * (SettingsDataStore requires an Android Context and no mocking library is on
 * the test classpath), so this reproduces the turn loop with the REAL
 * production decision — [ExecuteVehicleControlUseCase.retainsPendingConfirmation]
 * — driving the REAL skill. It integrates the two halves of the fix rather than
 * re-implementing either.
 */
class DeliveryConfirmationTurnTest {

    private lateinit var repository: InMemoryDeliveryRepository
    private lateinit var skill: DeliverySkill

    @Before
    fun setUp() {
        repository = InMemoryDeliveryRepository()
        skill = DeliverySkill(repository)
    }

    /** Mirrors ExecuteVehicleControlUseCase.invoke: cancel first, then dispatch. */
    private fun turn(intent: VehicleIntent) {
        if (!ExecuteVehicleControlUseCase.retainsPendingConfirmation(intent)) {
            skill.clearPendingConfirmation()
        }
        if (intent is VehicleIntent.Delivery) skill.execute(intent.command)
    }

    @Test
    fun `a non-delivery turn between two confirms forces a re-ask`() {
        turn(VehicleIntent.Delivery(DeliveryCommand.Confirm("A12"))) // turn 1: asked
        turn(VehicleIntent.SetAc(true)) // turn 2: HVAC, unrelated to delivery

        // Turn 3 must ask again, NOT deliver: the confirmation from turn 1 is
        // stale the moment an unrelated command was issued.
        val third = skill.execute(DeliveryCommand.Confirm("A12"))

        assertTrue("$third", third.exceptionOrNull() is ConfirmationRequiredException)
        assertEquals(OrderState.IN_TRANSIT, repository.order("A12")!!.state)
    }

    @Test
    fun `two back-to-back confirms still deliver`() {
        turn(VehicleIntent.Delivery(DeliveryCommand.Confirm("A12"))) // asked
        turn(VehicleIntent.Delivery(DeliveryCommand.Confirm("A12"))) // delivered

        assertEquals(OrderState.DELIVERED, repository.order("A12")!!.state)
    }

    @Test
    fun `only a delivery confirm keeps a pending confirmation alive`() {
        assertTrue(
            ExecuteVehicleControlUseCase.retainsPendingConfirmation(
                VehicleIntent.Delivery(DeliveryCommand.Confirm("A12")),
            ),
        )
        // Other delivery intents cancel it too — the driver asking for the next
        // stop or an order status has moved on from the question.
        assertFalse(
            ExecuteVehicleControlUseCase.retainsPendingConfirmation(
                VehicleIntent.Delivery(DeliveryCommand.NextStop),
            ),
        )
        assertFalse(
            ExecuteVehicleControlUseCase.retainsPendingConfirmation(
                VehicleIntent.Media(MediaCommand.NEXT),
            ),
        )
        assertFalse(
            ExecuteVehicleControlUseCase.retainsPendingConfirmation(VehicleIntent.SetAc(true)),
        )
    }
}
