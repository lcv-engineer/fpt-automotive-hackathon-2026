package com.sopa.viva_automotive.feature.voice.domain.delivery

import com.sopa.viva_automotive.feature.voice.domain.CommandValidationException
import com.sopa.viva_automotive.feature.voice.domain.ConfirmationRequiredException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `DeliverySkill` — the three `delivery_*` intents of 03-contracts.md §5.
 *
 * Owner: Vĩ (proposal slide 3, "delivery simulator"). Runs entirely in-app:
 * §0.1 puts `delivery_*` outside the VivaCarService → PropertyID → VHAL → CAN
 * path, so no claim about this skill may be worded as full-stack.
 *
 * Returns `Result<String>` — the same shape `ExecuteVehicleControlUseCase`
 * already uses — where the success value is the Vietnamese sentence the driver
 * hears and the HMI shows.
 */
@Singleton
class DeliverySkill @Inject constructor(
    private val repository: DeliveryRepository,
) {

    /** Intent names this skill handles, mirroring `Skill.handles` in §5. */
    val handles: Set<String> = setOf(
        "delivery_next_stop",
        "delivery_order_status",
        "delivery_confirm",
    )

    /**
     * The order the driver has been asked to confirm, if any.
     *
     * Confirmation is two-turn on purpose: `G2_CONFIRM_DELIVERY` in §4 applies
     * to `delivery_confirm` *always*. Marking a parcel delivered is not
     * reversible from the van, and a misheard ASR result must not close
     * somebody's order.
     *
     * There is no time window here — the pending question is cleared by any
     * other delivery command, and only the same order id can answer it. A
     * clock would need injecting for one expiry rule; determinism is worth
     * more in a demo. When SafetyGuard (T5/T6) lands it owns this flow and
     * this field goes away.
     */
    private var pendingConfirmation: String? = null

    private val lock = Any()

    fun execute(command: DeliveryCommand): Result<String> = synchronized(lock) {
        when (command) {
            DeliveryCommand.NextStop -> {
                pendingConfirmation = null
                nextStop()
            }
            is DeliveryCommand.OrderStatus -> {
                pendingConfirmation = null
                orderStatus(command.orderId)
            }
            is DeliveryCommand.Confirm -> confirm(command.orderId)
        }
    }

    private fun nextStop(): Result<String> {
        val order = repository.nextOrder()
            ?: return Result.success("Bạn đã giao xong toàn bộ đơn của lộ trình hôm nay.")
        val remaining = repository.orders().count { it.state != OrderState.DELIVERED }
        return Result.success(
            "Chặng tiếp theo là đơn ${spellOrderId(order.id)}, giao cho ${order.recipientVi} " +
                "tại ${order.stop.addressVi}, ${order.stop.districtVi}, " +
                "khoảng ${order.stop.etaMinutes} phút nữa. Còn $remaining đơn chưa giao.",
        )
    }

    private fun orderStatus(orderId: String?): Result<String> {
        val order = resolve(orderId)
            ?: return Result.failure(
                CommandValidationException(missingOrderMessage(orderId)),
            )

        val state = when (order.state) {
            OrderState.PENDING -> "đang chờ giao"
            OrderState.IN_TRANSIT -> "đang trên đường, khoảng ${order.stop.etaMinutes} phút nữa"
            OrderState.DELIVERED -> "đã giao xong"
        }
        val cod = if (order.codAmountVnd > 0 && order.state != OrderState.DELIVERED) {
            " Thu hộ ${formatVnd(order.codAmountVnd)} đồng."
        } else {
            ""
        }
        return Result.success(
            "Đơn ${spellOrderId(order.id)} của ${order.recipientVi} $state.$cod",
        )
    }

    private fun confirm(orderId: String?): Result<String> {
        val order = resolve(orderId)
            ?: run {
                pendingConfirmation = null
                return Result.failure(CommandValidationException(missingOrderMessage(orderId)))
            }

        if (order.state == OrderState.DELIVERED) {
            pendingConfirmation = null
            // Not an error: saying "already done" is true and useful. Silently
            // succeeding again would let a double-confirm look like progress.
            return Result.success("Đơn ${spellOrderId(order.id)} đã được xác nhận giao trước đó rồi.")
        }

        if (pendingConfirmation != order.id) {
            pendingConfirmation = order.id
            return Result.failure(
                ConfirmationRequiredException(
                    rule = CONFIRM_RULE,
                    questionVi = "Xác nhận đã giao đơn ${spellOrderId(order.id)} " +
                        "cho ${order.recipientVi} phải không?",
                ),
            )
        }

        pendingConfirmation = null
        val delivered = repository.markDelivered(order.id)
            ?: return Result.failure(
                CommandValidationException(
                    "Mình chưa cập nhật được đơn ${spellOrderId(order.id)}. Bạn thử lại giúp mình nhé.",
                ),
            )

        val remaining = repository.orders().count { it.state != OrderState.DELIVERED }
        val tail = if (remaining == 0) {
            "Hết đơn rồi, bạn hoàn thành lộ trình hôm nay."
        } else {
            "Còn $remaining đơn nữa."
        }
        return Result.success(
            "Đã xác nhận giao đơn ${spellOrderId(delivered.id)} cho ${delivered.recipientVi}. $tail",
        )
    }

    /** Falls back to the current stop when the driver said "đơn này". */
    private fun resolve(orderId: String?): DeliveryOrder? =
        if (orderId == null) repository.nextOrder() else repository.order(orderId)

    private fun missingOrderMessage(orderId: String?): String =
        if (orderId == null) {
            "Lộ trình hôm nay không còn đơn nào đang chờ giao."
        } else {
            // Naming the id back is deliberate: if ASR misheard "A12" as "A2",
            // the driver hears the mistake instead of a generic failure.
            "Mình không tìm thấy đơn ${spellOrderId(orderId)} trong lộ trình hôm nay."
        }

    /**
     * "A12" is read out as "A 12" so TTS does not run the letter into the
     * number; the driver is checking it against a label on a parcel.
     */
    private fun spellOrderId(id: String): String {
        val letters = id.takeWhile { !it.isDigit() }
        val digits = id.dropWhile { !it.isDigit() }
        return if (letters.isEmpty() || digits.isEmpty()) id else "$letters $digits"
    }

    private fun formatVnd(amount: Long): String =
        amount.toString().reversed().chunked(3).joinToString(".").reversed()

    private companion object {
        /** 03-contracts.md §4: applies to `delivery_confirm` unconditionally. */
        const val CONFIRM_RULE = "G2_CONFIRM_DELIVERY"
    }
}
