package com.viva.voice.intent

/** A normalized command proposal. Only the gateway may decide to execute it. */
data class Intent(
    val name: String,
    val slots: Map<String, Any> = emptyMap(),
    val confidence: Float,
    val tier: Tier,
) {
    enum class Tier { T0, T1, T2 }
}

/** Result of language understanding before any vehicle or media action occurs. */
sealed class RouteResult {
    data class Matched(val intent: Intent) : RouteResult()

    data class NeedsClarification(
        val promptVi: String,
        val rule: String = "G3_MISSING_SLOT",
    ) : RouteResult()

    data class Unsupported(
        val promptVi: String =
            "Mình chưa hiểu yêu cầu này. Bạn thử: điều hòa, cửa, đèn cabin, nhạc, hoặc âm lượng nhé.",
        val rule: String = "G3_UNSUPPORTED",
        val canFallback: Boolean = true,
    ) : RouteResult()
}

fun interface IntentRouter {
    fun route(text: String): RouteResult
}

/**
 * Additive T0 extension point owned by the consuming app.
 *
 * [route] receives a lowercase, punctuation-normalized command with the supported wake phrase
 * removed. Return `null` when the rule does not match. A match is still only a proposal and must
 * pass through the app gateway and SafetyGuard before any action executes.
 */
fun interface GrammarRule {
    fun route(command: String): RouteResult?
}
