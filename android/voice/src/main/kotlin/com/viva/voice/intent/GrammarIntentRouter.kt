package com.viva.voice.intent

import java.util.Locale

/**
 * Deterministic T0 router for the ten core intents and additive app-owned rules.
 *
 * The optional wake phrase is stripped here so the same router works with a
 * future wake-word detector and with today's push-to-talk fallback. Extension
 * rules are snapshotted at construction and run only after core rules and
 * removed-command filters.
 *
 * Matching folds Vietnamese diacritics so ASR variants (có/không dấu) hit the
 * same rules. Spoken number words are expanded only inside slot extractors
 * (temperature, fan, order id) — media query text is left alone.
 */
class GrammarIntentRouter(
    extensionRules: List<GrammarRule> = emptyList(),
) : IntentRouter {
    private val extensionRules = extensionRules.toList()

    override fun route(text: String): RouteResult {
        val normalized = normalize(text)
        if (UNSUPPORTED_WAKE.containsMatchIn(normalized)) {
            return RouteResult.Unsupported(
                "Từ gọi của trợ lý là “Vi-Vi ơi” (cũng nhận Vivi/Viva ơi). Bạn thử lại nhé.",
                canFallback = false,
            )
        }
        val command = normalized.replaceFirst(SUPPORTED_WAKE, "").trim()
        if (command.isEmpty()) {
            return RouteResult.NeedsClarification("Bạn muốn mình thực hiện việc gì?")
        }
        if (isRemovedCommand(command)) {
            return RouteResult.Unsupported(
                promptVi = "Lệnh này chưa hỗ trợ trong bản demo. Bạn thử một lệnh điều hòa, cửa, âm thanh hoặc giao hàng nhé.",
                canFallback = false,
            )
        }

        if (command.contains("lanh qua")) {
            return RouteResult.NeedsClarification(
                "Bạn muốn tăng nhiệt độ điều hòa lên bao nhiêu độ?",
            )
        }
        if (command.contains("nong qua")) {
            return RouteResult.NeedsClarification(
                "Bạn muốn giảm nhiệt độ điều hòa xuống bao nhiêu độ?",
            )
        }

        if (isTemperatureCommand(command)) {
            return routeTemperature(command)
        }
        if (command.contains("quat")) {
            return routeFan(command)
        }
        if (command.contains("mo cua") || command.contains("mo khoa cua")) {
            return matched("door_lock", mapOf("lock" to false))
        }
        if (command.contains("khoa cua")) {
            return matched("door_lock", mapOf("lock" to true))
        }
        if (command.contains("tang am luong")) {
            return matched("volume_adjust", mapOf("delta" to 1))
        }
        if (command.contains("giam am luong")) {
            return matched("volume_adjust", mapOf("delta" to -1))
        }
        if (command.contains("dung nhac") || command.contains("tam dung nhac")) {
            return matched("media_pause")
        }
        if (command.contains("chuyen bai") || command.contains("bai tiep theo")) {
            return matched("media_next")
        }
        if (command.startsWith("phat nhac") || command.startsWith("phat playlist")) {
            // Slot `query` is what the player searches for — strip verb + kind words.
            val query = command
                .removePrefix("phat ")
                .removePrefix("playlist ")
                .removePrefix("nhac ")
                .takeUnless { it == "nhac" || it == "playlist" }
            return matched("media_play", query?.let { mapOf("query" to it) }.orEmpty())
        }
        if (command.contains("chang tiep theo") || command.contains("diem dung tiep theo")) {
            return matched("delivery_next_stop")
        }
        if (command.contains("don") && DELIVERY_STATUS_CUES.any(command::contains)) {
            return matched("delivery_order_status", orderIdSlot(command))
        }
        if (command.contains("xac nhan") && command.contains("giao")) {
            return matched("delivery_confirm", orderIdSlot(command))
        }
        extensionRules.forEach { rule ->
            rule.route(command)?.let { return it }
        }
        return RouteResult.Unsupported()
    }

    private fun isRemovedCommand(command: String): Boolean =
        REMOVED_COMMANDS.any { pattern -> pattern.containsMatchIn(command) }

    private fun orderIdSlot(command: String): Map<String, Any> {
        val parsedCommand = parseVietnameseNumber(command)
        val match = ORDER_ID.find(parsedCommand) ?: return emptyMap()
        val letter = match.groupValues[1]
        val numbers = match.groupValues[2].replace(Regex("""\s+"""), "")
        val orderId = (letter + numbers).uppercase(Locale.ROOT)
        return mapOf("orderId" to orderId)
    }

    private fun routeTemperature(command: String): RouteResult {
        val parsed = parseVietnameseNumber(command)
        val value = NUMBER.find(parsed)?.groupValues?.get(1)?.toIntOrNull()
            ?: return RouteResult.NeedsClarification(
                "Bạn muốn đặt nhiệt độ điều hòa ở bao nhiêu độ?",
            )
        if (value !in MIN_TEMPERATURE_C..MAX_TEMPERATURE_C) {
            return RouteResult.NeedsClarification(
                "Nhiệt độ hỗ trợ từ 16 đến 32 độ C. Bạn muốn đặt bao nhiêu độ?",
            )
        }
        return matched("hvac_set_temp", mapOf("value" to value.toFloat()))
    }

    private fun isTemperatureCommand(command: String): Boolean {
        if (command.contains("nhiet do")) return true
        if (!command.contains("dieu hoa")) return false
        val parsed = parseVietnameseNumber(command)
        return NUMBER.containsMatchIn(parsed) ||
            TEMPERATURE_CUES.any { cue -> containsWord(command, cue) }
    }

    private fun routeFan(command: String): RouteResult {
        val parsed = parseVietnameseNumber(command)
        val level = NUMBER.find(parsed)?.groupValues?.get(1)?.toIntOrNull()
            ?: return RouteResult.NeedsClarification("Bạn muốn đặt quạt ở mức mấy, từ 0 đến 5?")
        if (level !in MIN_FAN_LEVEL..MAX_FAN_LEVEL) {
            return RouteResult.NeedsClarification("Mức quạt hỗ trợ từ 0 đến 5. Bạn chọn mức nào?")
        }
        return matched("hvac_set_fan", mapOf("level" to level))
    }

    private fun matched(name: String, slots: Map<String, Any> = emptyMap()) =
        RouteResult.Matched(
            Intent(
                name = name,
                slots = slots,
                confidence = GRAMMAR_CONFIDENCE,
                tier = Intent.Tier.T0,
            ),
        )

    private fun normalize(raw: String): String = raw
        .lowercase(Locale.ROOT)
        .replace(PUNCTUATION, " ")
        .replace(WHITESPACE, " ")
        .trim()
        .let(::foldVietnamese)

    private fun foldVietnamese(text: String): String {
        val normalized = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
        return DIACRITICS.replace(normalized, "")
            .replace('đ', 'd')
            .replace('Đ', 'd')
    }

    private fun containsWord(haystack: String, needle: String): Boolean =
        Regex("""(?:^|\s)${Regex.escape(needle)}(?:\s|$)""").containsMatchIn(haystack)

    /** Keys are folded (no diacritics) because [normalize] folds before routing. */
    private fun parseVietnameseNumber(raw: String): String {
        var normalized = raw
        val sortedKeys = NUMBER_WORDS.keys.sortedByDescending { it.length }
        for (key in sortedKeys) {
            normalized = normalized.replace(
                Regex("(?<=\\s|^)${Regex.escape(key)}(?=\\s|$)"),
                NUMBER_WORDS.getValue(key),
            )
        }
        return normalized
    }

    companion object {
        private const val MIN_TEMPERATURE_C = 16
        private const val MAX_TEMPERATURE_C = 32
        private const val MIN_FAN_LEVEL = 0
        private const val MAX_FAN_LEVEL = 5
        private const val GRAMMAR_CONFIDENCE = 1.0f

        private val NUMBER = Regex("""(\d{1,2})""")
        private val PUNCTUATION = Regex("""[,.!?;:]""")
        private val WHITESPACE = Regex("""\s+""")
        private val DIACRITICS = Regex("""\p{M}+""")
        // Canonical product wake: “Vi-Vi ơi”; keep Vivi/Viva aliases for PTT/ASR.
        private val SUPPORTED_WAKE =
            Regex("""^(?:viva|vivi|vi[\s-]?vi)\s+oi(?:\s+|$)""")
        private val UNSUPPORTED_WAKE = Regex("""^(?:siri|alexa|hey google)\s+oi?(?:\s+|$)""")
        private val ORDER_ID = Regex("""\b([a-z])\s*((?:\d\s*){1,6})\b""")
        private val DELIVERY_STATUS_CUES = listOf("the nao", "trang thai", "den dau")
        private val REMOVED_COMMANDS = listOf(
            Regex("""\b(?:bat|tat)\s+(?:dieu hoa|ac)\b"""),
            Regex("""dat\s+am luong"""),
            Regex("""\b(?:bai truoc|quay lai bai truoc)\b"""),
            Regex("""\b(?:dtc|ma loi|xe co loi)\b"""),
        )
        private val TEMPERATURE_CUES = listOf("dat", "ha", "tang", "giam", "xuong", "len", "do")

        private val NUMBER_WORDS = mapOf(
            "khong" to "0",
            "mot" to "1",
            "hai" to "2",
            "ba" to "3",
            "bon" to "4",
            "nam" to "5",
            "sau" to "6",
            "bay" to "7",
            "tam" to "8",
            "chin" to "9",
            "muoi" to "10",
            "muoi mot" to "11",
            "muoi hai" to "12",
            "muoi ba" to "13",
            "muoi bon" to "14",
            "muoi lam" to "15",
            "muoi sau" to "16",
            "muoi bay" to "17",
            "muoi tam" to "18",
            "muoi chin" to "19",
            "hai muoi" to "20",
            "hai muoi mot" to "21", "hai mot" to "21",
            "hai muoi hai" to "22", "hai hai" to "22",
            "hai muoi ba" to "23", "hai ba" to "23",
            "hai muoi bon" to "24", "hai muoi tu" to "24", "hai bon" to "24", "hai tu" to "24",
            "hai muoi lam" to "25", "hai lam" to "25", "hai nam" to "25",
            "hai muoi sau" to "26", "hai sau" to "26",
            "hai muoi bay" to "27", "hai bay" to "27",
            "hai muoi tam" to "28", "hai tam" to "28",
            "hai muoi chin" to "29", "hai chin" to "29",
            "ba muoi" to "30",
            "ba muoi mot" to "31", "ba mot" to "31",
            "ba muoi hai" to "32", "ba hai" to "32",
        )
    }
}
