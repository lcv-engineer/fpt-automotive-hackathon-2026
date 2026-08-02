package com.sopa.viva_automotive.feature.voice.domain.delivery

import com.sopa.viva_automotive.feature.voice.domain.CommandValidationException
import com.sopa.viva_automotive.feature.voice.domain.ConfirmationRequiredException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeliverySkillTest {

    private lateinit var repository: InMemoryDeliveryRepository
    private lateinit var skill: DeliverySkill

    @Before
    fun setUp() {
        repository = InMemoryDeliveryRepository()
        skill = DeliverySkill(repository)
    }

    @Test
    fun `handles exactly the three delivery intents of the contract`() {
        assertEquals(
            setOf("delivery_next_stop", "delivery_order_status", "delivery_confirm"),
            skill.handles,
        )
    }

    @Test
    fun `next stop names the order, the recipient and the address`() {
        val spoken = skill.execute(DeliveryCommand.NextStop).getOrThrow()

        assertTrue(spoken, spoken.contains("A 12"))
        assertTrue(spoken, spoken.contains("chị Hương"))
        assertTrue(spoken, spoken.contains("Trần Duy Hưng"))
    }

    @Test
    fun `order status reports the current stop when no id was heard`() {
        val spoken = skill.execute(DeliveryCommand.OrderStatus(orderId = null)).getOrThrow()

        assertTrue(spoken, spoken.contains("A 12"))
        assertTrue(spoken, spoken.contains("đang trên đường"))
    }

    @Test
    fun `order status accepts a lowercase id from ASR`() {
        val spoken = skill.execute(DeliveryCommand.OrderStatus("b7")).getOrThrow()

        assertTrue(spoken, spoken.contains("B 7"))
        assertTrue(spoken, spoken.contains("anh Tuấn"))
    }

    @Test
    fun `an unknown order id is a failure that repeats the id back`() {
        val error = skill.execute(DeliveryCommand.OrderStatus("Z9")).exceptionOrNull()

        assertTrue("$error", error is CommandValidationException)
        // The driver must hear WHICH id was not found — if ASR misheard "A12"
        // as "Z9", a generic failure hides the actual problem.
        assertTrue("$error", error!!.message!!.contains("Z 9"))
    }

    @Test
    fun `confirm asks first and does not deliver on the first turn`() {
        val error = skill.execute(DeliveryCommand.Confirm("A12")).exceptionOrNull()

        assertTrue("$error", error is ConfirmationRequiredException)
        assertEquals("G2_CONFIRM_DELIVERY", (error as ConfirmationRequiredException).rule)
        assertTrue(error.questionVi, error.questionVi.contains("A 12"))
        assertEquals(OrderState.IN_TRANSIT, repository.order("A12")!!.state)
    }

    @Test
    fun `confirm twice delivers the order and advances the route`() {
        skill.execute(DeliveryCommand.Confirm("A12"))
        val spoken = skill.execute(DeliveryCommand.Confirm("A12")).getOrThrow()

        assertTrue(spoken, spoken.contains("Đã xác nhận"))
        assertEquals(OrderState.DELIVERED, repository.order("A12")!!.state)
        // The next stop must be current immediately, not after a refresh.
        assertEquals("B7", repository.nextOrder()!!.id)
        assertEquals(OrderState.IN_TRANSIT, repository.order("B7")!!.state)
    }

    @Test
    fun `a pending confirmation does not carry over to a different order`() {
        skill.execute(DeliveryCommand.Confirm("A12"))

        val error = skill.execute(DeliveryCommand.Confirm("B7")).exceptionOrNull()

        assertTrue("$error", error is ConfirmationRequiredException)
        assertEquals(OrderState.PENDING, repository.order("B7")!!.state)
        assertEquals(OrderState.IN_TRANSIT, repository.order("A12")!!.state)
    }

    @Test
    fun `asking anything else clears the pending confirmation`() {
        skill.execute(DeliveryCommand.Confirm("A12"))
        skill.execute(DeliveryCommand.NextStop)

        // Without the reset, this second confirm would deliver silently — the
        // driver would never be asked, which is what G2_CONFIRM_DELIVERY exists
        // to prevent.
        val error = skill.execute(DeliveryCommand.Confirm("A12")).exceptionOrNull()

        assertTrue("$error", error is ConfirmationRequiredException)
        assertEquals(OrderState.IN_TRANSIT, repository.order("A12")!!.state)
    }

    @Test
    fun `confirming an already delivered order says so instead of pretending`() {
        skill.execute(DeliveryCommand.Confirm("A12"))
        skill.execute(DeliveryCommand.Confirm("A12"))

        val spoken = skill.execute(DeliveryCommand.Confirm("A12")).getOrThrow()

        assertTrue(spoken, spoken.contains("trước đó"))
    }

    @Test
    fun `the route reports completion once every order is delivered`() {
        listOf("A12", "B7", "C3").forEach { id ->
            skill.execute(DeliveryCommand.Confirm(id))
            skill.execute(DeliveryCommand.Confirm(id))
        }

        val spoken = skill.execute(DeliveryCommand.NextStop).getOrThrow()

        assertTrue(spoken, spoken.contains("giao xong"))
        assertEquals(null, repository.nextOrder())
    }

    @Test
    fun `confirming with no id left on the route fails instead of guessing`() {
        listOf("A12", "B7", "C3").forEach { id ->
            skill.execute(DeliveryCommand.Confirm(id))
            skill.execute(DeliveryCommand.Confirm(id))
        }

        val error = skill.execute(DeliveryCommand.Confirm(orderId = null)).exceptionOrNull()

        assertTrue("$error", error is CommandValidationException)
    }

    @Test
    fun `cod amount is spoken with thousands separators`() {
        val spoken = skill.execute(DeliveryCommand.OrderStatus("A12")).getOrThrow()

        assertTrue(spoken, spoken.contains("350.000"))
    }
}
