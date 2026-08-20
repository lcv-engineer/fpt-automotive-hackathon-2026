package com.sopa.viva_automotive.feature.voice.data.brain

import com.sopa.viva_automotive.feature.voice.integration.CoreIntentMapper
import com.viva.voice.agent.AgentPlanResult
import com.viva.voice.agent.AgentResumePrefix
import com.viva.voice.intent.Intent
import org.json.JSONObject

/** Parses the remote model response as untrusted data and fails closed. */
internal object BrainPlanResponseParser {
    fun parse(body: String): AgentPlanResult = runCatching {
        val json = JSONObject(body)
        val kind = json.getString("kind")
        val fields = json.keys().asSequence().toSet()
        val requiredFields = when {
            kind == "actions" -> MULTI_REQUIRED_FIELDS
            kind == "clarification" && "resume_prefix" in fields -> RESUMABLE_REQUIRED_FIELDS
            else -> REQUIRED_FIELDS
        }
        require(fields == requiredFields) {
            "brain response fields do not match the contract"
        }
        val confidence = requiredFloat(json, "confidence")
        require(confidence.isFinite() && confidence in 0f..1f) {
            "brain confidence must be between 0 and 1"
        }

        when (kind) {
            "action" -> AgentPlanResult.Action(parseAction(json, confidence))
            "actions" -> parseActions(json)
            "clarification" -> AgentPlanResult.Clarification(
                requiredText(json, "prompt_vi", MAX_PROMPT_CHARS),
                json.optResumePrefix(),
            )
            "unsupported" -> AgentPlanResult.Unsupported(
                requiredText(json, "prompt_vi", MAX_PROMPT_CHARS),
            )
            else -> error("unsupported brain response kind")
        }
    }.getOrElse { error ->
        AgentPlanResult.Unavailable(error.message ?: "invalid brain response")
    }

    private fun parseActions(json: JSONObject): AgentPlanResult {
        require(json.isNull("intent_name") && json.isNull("prompt_vi")) {
            "multi-action plan must not contain singular intent or prompt"
        }
        require(SLOT_FIELDS.all(json::isNull)) {
            "multi-action plan must not contain singular slots"
        }
        val actions = json.getJSONArray("actions")
        require(actions.length() in 2..AgentPlanResult.MAX_ACTIONS) {
            "multi-action plan is outside the size bound"
        }
        val intents = (0 until actions.length()).map { index ->
            val action = actions.getJSONObject(index)
            require(action.keys().asSequence().toSet() == ACTION_FIELDS) {
                "action member fields do not match the contract"
            }
            val confidence = requiredFloat(action, "confidence")
            require(confidence.isFinite() && confidence in 0f..1f)
            parseAction(action, confidence)
        }
        return AgentPlanResult.Actions(intents)
    }

    private fun parseAction(json: JSONObject, confidence: Float): Intent {
        require(confidence >= MIN_ACTION_CONFIDENCE) {
            "agent action confidence below threshold"
        }
        require(json.isNull("prompt_vi")) { "action must not contain a spoken success claim" }

        val name = requiredText(json, "intent_name", MAX_INTENT_NAME_CHARS)
        val expectedSlots = EXPECTED_SLOT_FIELDS[name] ?: error("intent is outside the allowlist")
        val populatedSlots = SLOT_FIELDS.filterTo(mutableSetOf()) { field -> !json.isNull(field) }
        require(populatedSlots == expectedSlots ||
            (name in OPTIONAL_TEXT_SLOT_INTENTS && populatedSlots.isEmpty())) {
            "intent contains missing or unrelated slots"
        }

        val slots: Map<String, Any> = when (name) {
            "hvac_set_temp" -> mapOf("value" to requiredFloat(json, "value"))
            "hvac_set_fan" -> mapOf("level" to requiredInt(json, "level"))
            "cabin_lights" -> mapOf("on" to requiredBoolean(json, "on"))
            "volume_adjust" -> mapOf(
                "delta" to requiredInt(json, "delta").also { require(it in -1..1) },
            )
            "media_play" -> optionalTextSlot(json, "query", "query")
            "delivery_order_status", "delivery_confirm" ->
                optionalTextSlot(json, "order_id", "orderId")
            else -> emptyMap()
        }

        val intent = Intent(
            name = name,
            slots = slots,
            confidence = confidence,
            tier = Intent.Tier.T2,
        )
        require(CoreIntentMapper.map(intent) != null) {
            "agent proposal cannot be mapped to an application action"
        }
        return intent
    }

    private fun optionalTextSlot(
        json: JSONObject,
        wireName: String,
        intentName: String,
    ): Map<String, Any> = if (json.isNull(wireName)) {
        emptyMap()
    } else {
        mapOf(intentName to requiredText(json, wireName, MAX_QUERY_CHARS))
    }

    private fun requiredText(json: JSONObject, name: String, maxChars: Int): String {
        require(!json.isNull(name)) { "$name is required" }
        val raw = json.get(name) as? String ?: error("$name must be a string")
        return raw.trim().also { value ->
            require(value.isNotEmpty() && value.length <= maxChars) { "$name is invalid" }
        }
    }

    private fun requiredFloat(json: JSONObject, name: String): Float {
        val number = json.get(name) as? Number ?: error("$name must be numeric")
        return number.toFloat().also { require(it.isFinite()) { "$name must be finite" } }
    }

    private fun requiredInt(json: JSONObject, name: String): Int {
        val number = json.get(name) as? Number ?: error("$name must be numeric")
        val value = number.toDouble()
        require(value.isFinite() && value % 1.0 == 0.0 && value in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) {
            "$name must be an integer"
        }
        return value.toInt()
    }

    private fun requiredBoolean(json: JSONObject, name: String): Boolean =
        json.get(name) as? Boolean ?: error("$name must be boolean")

    private fun JSONObject.optResumePrefix(): AgentResumePrefix? {
        if (!has("resume_prefix") || isNull("resume_prefix")) return null
        return when (requiredText(this, "resume_prefix", MAX_RESUME_PREFIX_CHARS)) {
            "temperature" -> AgentResumePrefix.TEMPERATURE
            "fan_level" -> AgentResumePrefix.FAN_LEVEL
            "media_query" -> AgentResumePrefix.MEDIA_QUERY
            "order_id" -> AgentResumePrefix.ORDER_ID
            else -> error("resume prefix is outside the allowlist")
        }
    }

    private const val MIN_ACTION_CONFIDENCE = 0.75f
    private const val MAX_PROMPT_CHARS = 180
    private const val MAX_QUERY_CHARS = 100
    private const val MAX_INTENT_NAME_CHARS = 40
    private const val MAX_RESUME_PREFIX_CHARS = 20

    private val SLOT_FIELDS = setOf("value", "level", "lock", "on", "delta", "query", "order_id")
    private val REQUIRED_FIELDS = SLOT_FIELDS + setOf(
        "kind",
        "intent_name",
        "prompt_vi",
        "confidence",
    )
    private val ACTION_FIELDS = SLOT_FIELDS + setOf("intent_name", "confidence")
    private val MULTI_REQUIRED_FIELDS = REQUIRED_FIELDS + "actions"
    private val RESUMABLE_REQUIRED_FIELDS = REQUIRED_FIELDS + "resume_prefix"
    private val OPTIONAL_TEXT_SLOT_INTENTS = setOf(
        "media_play",
        "delivery_order_status",
        "delivery_confirm",
    )
    /**
     * `door_lock` cố ý VẮNG MẶT — xem `asr/app/brain.py`. Hai phía phải khớp
     * nhau, nhưng client vẫn từ chối độc lập chứ không tin server đã lọc.
     */
    private val EXPECTED_SLOT_FIELDS = mapOf(
        "hvac_set_temp" to setOf("value"),
        "hvac_set_fan" to setOf("level"),
        "cabin_lights" to setOf("on"),
        "volume_adjust" to setOf("delta"),
        "media_play" to setOf("query"),
        "media_pause" to emptySet(),
        "media_next" to emptySet(),
        "media_favorite" to emptySet(),
        "delivery_next_stop" to emptySet(),
        "delivery_order_status" to setOf("order_id"),
        "delivery_confirm" to setOf("order_id"),
    )
}
