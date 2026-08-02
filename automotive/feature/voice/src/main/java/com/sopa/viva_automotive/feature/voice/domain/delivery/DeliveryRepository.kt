package com.sopa.viva_automotive.feature.voice.domain.delivery

import javax.inject.Inject
import javax.inject.Singleton

/**
 * The route the driver is working through.
 *
 * An interface rather than a concrete class so the demo manifest can be
 * swapped for a real dispatch backend without touching [DeliverySkill] — and
 * so the skill's tests do not depend on the seeded data staying the same.
 */
interface DeliveryRepository {
    /** Orders in route order; delivered ones keep their place in history. */
    fun orders(): List<DeliveryOrder>

    /** The stop the driver is heading to, or null when the route is finished. */
    fun nextOrder(): DeliveryOrder?

    fun order(id: String): DeliveryOrder?

    /**
     * Marks [id] delivered and returns the updated order, or null if there is
     * no such order. Marking an already-delivered order returns it unchanged —
     * the caller decides whether that is worth saying out loud.
     */
    fun markDelivered(id: String): DeliveryOrder?
}

/**
 * In-memory simulator seeded with a short Hanoi route.
 *
 * Deliberately not persisted: a demo that survives a restart would also carry
 * yesterday's half-finished state into the recording, and the delivery flow is
 * declared *mô phỏng* in the integration table either way. Restarting the app
 * resets the route, which is what a 3-minute demo wants.
 */
@Singleton
class InMemoryDeliveryRepository @Inject constructor() : DeliveryRepository {

    private val lock = Any()

    private val route = mutableListOf(
        DeliveryOrder(
            id = "A12",
            recipientVi = "chị Hương",
            stop = DeliveryStop(1, "số 27 ngõ 12 Trần Duy Hưng", "Cầu Giấy", "A12", etaMinutes = 6),
            state = OrderState.IN_TRANSIT,
            codAmountVnd = 350_000,
        ),
        DeliveryOrder(
            id = "B7",
            recipientVi = "anh Tuấn",
            stop = DeliveryStop(2, "tòa N03 Hoàng Đạo Thúy", "Thanh Xuân", "B7", etaMinutes = 14),
            state = OrderState.PENDING,
        ),
        DeliveryOrder(
            id = "C3",
            recipientVi = "chị Lan",
            stop = DeliveryStop(3, "số 5 Nguyễn Chí Thanh", "Đống Đa", "C3", etaMinutes = 23),
            state = OrderState.PENDING,
            codAmountVnd = 120_000,
        ),
    )

    override fun orders(): List<DeliveryOrder> = synchronized(lock) { route.toList() }

    override fun nextOrder(): DeliveryOrder? = synchronized(lock) {
        route.firstOrNull { it.state != OrderState.DELIVERED }
    }

    override fun order(id: String): DeliveryOrder? = synchronized(lock) {
        route.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }

    override fun markDelivered(id: String): DeliveryOrder? = synchronized(lock) {
        val index = route.indexOfFirst { it.id.equals(id, ignoreCase = true) }
        if (index < 0) return@synchronized null

        val delivered = route[index].copy(state = OrderState.DELIVERED)
        route[index] = delivered

        // Promote the next pending stop so "chặng tiếp theo" answers correctly
        // on the very next turn instead of after some background refresh.
        val nextPending = route.indexOfFirst { it.state == OrderState.PENDING }
        if (nextPending >= 0) {
            route[nextPending] = route[nextPending].copy(state = OrderState.IN_TRANSIT)
        }
        delivered
    }
}
