package com.sopa.viva_automotive.feature.voice.domain.delivery

/**
 * Delivery domain for the three `delivery_*` intents of 03-contracts.md §3.
 *
 * This is the "delivery simulator" the proposal (slide 3) assigns to Vĩ. It is
 * **in-app state, not a vehicle signal** — §0.1 is explicit that `delivery_*`
 * does not go through VivaCarService/VHAL/CAN, so nothing here may be claimed
 * as full-stack. The integration table (N5) labels it *mô phỏng*.
 */

/** One stop on the route the driver is working through. */
data class DeliveryStop(
    val sequence: Int,
    val addressVi: String,
    val districtVi: String,
    val orderId: String,
    val etaMinutes: Int,
)

enum class OrderState {
    /** Loaded on the van, not yet attempted. */
    PENDING,

    /** The stop the driver is currently heading to. */
    IN_TRANSIT,

    DELIVERED,
}

data class DeliveryOrder(
    val id: String,
    val recipientVi: String,
    val stop: DeliveryStop,
    val state: OrderState,
    val codAmountVnd: Long = 0,
)

/**
 * A delivery request after slot extraction, so the skill never has to know
 * about intent names or slot maps.
 */
sealed interface DeliveryCommand {
    /** Intent name from §3, kept so VIVA_TRACE_SUMMARY groups by the same vocabulary. */
    val intentName: String

    data object NextStop : DeliveryCommand {
        override val intentName: String = "delivery_next_stop"
    }

    /** [orderId] is null when the driver said "đơn này" without naming one. */
    data class OrderStatus(val orderId: String?) : DeliveryCommand {
        override val intentName: String = "delivery_order_status"
    }

    data class Confirm(val orderId: String?) : DeliveryCommand {
        override val intentName: String = "delivery_confirm"
    }
}
