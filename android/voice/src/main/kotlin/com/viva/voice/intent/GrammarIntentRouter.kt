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
 *
 * Sentence-type guards run before write rules: questions map to read-only
 * status intents when possible. Negation is owned by [NegationGate] in
 * VoiceAgent (accent-aware) so "quạt mức không" stays fan level 0.
 */
class GrammarIntentRouter(
    extensionRules: List<GrammarRule> = emptyList(),
) : IntentRouter {
    private val extensionRules = extensionRules.toList()

    override fun route(text: String): RouteResult {
        val normalized = normalize(text)
        if (UNSUPPORTED_WAKE.containsMatchIn(normalized)) {
            return RouteResult.Unsupported(
                "Từ gọi của trợ lý là “Viva ơi” (cũng nhận Vivi/Vi-Vi ơi). Bạn thử lại nhé.",
                canFallback = false,
            )
        }
        val command = normalized.replaceFirst(SUPPORTED_WAKE, "").trim()
        return routeCommand(command)
    }

    private fun routeCommand(command: String): RouteResult {
        if (command.isEmpty()) {
            return RouteResult.NeedsClarification(
                "Mình đang nghe. Bạn muốn điều hòa, cửa, đèn, nhạc hay âm lượng?",
            )
        }

        val clauses = command.split(COMPOUND_CONNECTOR).filter(String::isNotBlank)
        if (clauses.size > 1) {
            val clauseResults = clauses.map(::routeSingleCommand)
            if (clauseResults.all { it is RouteResult.Matched }) {
                if (clauses.size > RouteResult.MAX_ACTIONS) {
                    return RouteResult.Unsupported(
                        promptVi = "Mỗi lượt hỗ trợ tối đa ${RouteResult.MAX_ACTIONS} thao tác. Bạn chia yêu cầu thành hai lượt nhé.",
                        canFallback = false,
                    )
                }
                return RouteResult.MatchedMany(
                    clauseResults.map { (it as RouteResult.Matched).intent },
                )
            }

            // "phát bài Em và Trịnh" là một media query, không phải hai action.
            // Chỉ giữ nguyên cả câu khi mọi phần sau đều hoàn toàn không giống lệnh;
            // một clause đã match/clarify/removed phải khiến cả câu đi slow path.
            if (isMediaPlayCommand(command) && clauseResults.drop(1).all {
                    it is RouteResult.Unsupported && it.canFallback
                }
            ) {
                return routeSingleCommand(command)
            }

            val forbidsFallback = clauseResults.any {
                it is RouteResult.Unsupported && !it.canFallback
            }
            return RouteResult.Unsupported(
                promptVi = "Mình chưa hiểu đủ các phần của yêu cầu. Bạn nói lại từng thao tác giúp mình nhé.",
                canFallback = !forbidsFallback,
            )
        }
        return routeSingleCommand(command)
    }

    private fun routeSingleCommand(command: String): RouteResult {
        if (isRemovedCommand(command)) {
            return RouteResult.Unsupported(
                promptVi = "Lệnh này chưa có trong bản demo. Bạn thử: đặt điều hòa, mở cửa, bật đèn, phát nhạc, hoặc thích bài này.",
                canFallback = false,
            )
        }

        val cleaned = stripFillers(command)
        if (cleaned.isEmpty()) {
            return RouteResult.NeedsClarification(
                "Mình đang nghe. Bạn muốn điều hòa, cửa, đèn, nhạc hay âm lượng?",
            )
        }

        if (cleaned.contains("lanh qua")) {
            return RouteResult.NeedsClarification(
                "Bạn muốn tăng nhiệt độ điều hòa lên bao nhiêu độ?",
                resumePrefix = TEMPERATURE_PREFIX,
            )
        }
        if (cleaned.contains("nong qua")) {
            return RouteResult.NeedsClarification(
                "Bạn muốn giảm nhiệt độ điều hòa xuống bao nhiêu độ?",
                resumePrefix = TEMPERATURE_PREFIX,
            )
        }

        // Question before write rules: "... chưa / ... không" is interrogative.
        // Negation is handled upstream by NegationGate (not here): folding would
        // make "không" (number 0) look like a negation marker (N5 regression).
        routeQuestion(cleaned)?.let { return it }

        return routeAffirmative(cleaned)
    }

    private fun routeAffirmative(command: String): RouteResult {
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
        if (isCabinLightsOn(command)) {
            return matched("cabin_lights", mapOf("on" to true))
        }
        if (isCabinLightsOff(command)) {
            return matched("cabin_lights", mapOf("on" to false))
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
        if (isFavoriteCommand(command)) {
            return matched("media_favorite")
        }
        if (isMediaPlayCommand(command)) {
            // Slot `query` is what the player searches for — strip verb + kind words.
            val query = command
                .removePrefix("phat ")
                .removePrefix("playlist ")
                .removePrefix("nhac ")
                .removePrefix("bai ")
                .takeUnless { it == "nhac" || it == "playlist" || it == "bai" || it.isBlank() }
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

    /**
     * Read-only questions → status intents. Unknown yes/no cabin questions abort
     * without writing (N1_QUESTION), instead of falling through to set/unlock rules.
     */
    private fun routeQuestion(command: String): RouteResult? {
        if (!isQuestion(command)) return null

        when {
            command.contains("toc do") ->
                return matched("vehicle_status_speed")
            command.contains("nhien lieu") || command.contains("xang") ->
                return matched("vehicle_status_fuel")
            command.contains("pin") || command.contains("battery") ->
                return matched("vehicle_status_battery")
            command.contains("nhiet do") ||
                (command.contains("dieu hoa") &&
                    (command.contains("bao nhieu") || command.contains("may do"))) ->
                return matched("vehicle_status_temperature")
        }

        // Delivery "là gì / thế nào" stays on the affirmative path via caller order:
        // only invoked when isQuestion; delivery cues with "the nao" are questions too.
        if (command.contains("chang tiep theo") || command.contains("diem dung tiep theo")) {
            return matched("delivery_next_stop")
        }
        if (command.contains("don") && DELIVERY_STATUS_CUES.any(command::contains)) {
            return matched("delivery_order_status", orderIdSlot(command))
        }

        return RouteResult.Unsupported(
            promptVi = "Mình hiểu bạn đang hỏi. Bạn thử: tốc độ, nhiên liệu, hoặc nhiệt độ điều hòa hiện tại.",
            rule = "N1_QUESTION",
            canFallback = false,
        )
    }

    private fun isQuestion(command: String): Boolean {
        // Trailing "không" after "mức" is the numeral 0 (N5), not a yes/no tag.
        if (MUCN_KHONG_VALUE.containsMatchIn(command)) return false
        if (QUESTION_TAILS.any { command == it || command.endsWith(" $it") }) return true
        return QUESTION_CUES.any(command::contains)
    }

    private fun stripFillers(command: String): String {
        var text = " $command "
        for (filler in FILLERS) {
            text = text.replace(" $filler ", " ")
        }
        return text.replace(WHITESPACE, " ").trim()
    }

    private fun isMediaPlayCommand(command: String): Boolean =
        command.startsWith("phat nhac") ||
            command.startsWith("phat playlist") ||
            command.startsWith("phat bai")

    private fun isCabinLightsOn(command: String): Boolean =
        command.contains("bat den") ||
            command.contains("mo den") ||
            command.contains("bat den cabin") ||
            command.contains("bat den noi that")

    private fun isCabinLightsOff(command: String): Boolean =
        command.contains("tat den") ||
            command.contains("tat den cabin") ||
            command.contains("tat den noi that")

    private fun isFavoriteCommand(command: String): Boolean =
        command.contains("thich bai") ||
            command.contains("yeu thich bai") ||
            command.contains("them vao yeu thich") ||
            command.contains("luu bai nay")

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
                resumePrefix = TEMPERATURE_PREFIX,
            )
        if (value !in MIN_TEMPERATURE_C..MAX_TEMPERATURE_C) {
            return RouteResult.NeedsClarification(
                "Nhiệt độ hỗ trợ từ 16 đến 32 độ C. Bạn muốn đặt bao nhiêu độ?",
                resumePrefix = TEMPERATURE_PREFIX,
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
            ?: return RouteResult.NeedsClarification(
                "Bạn muốn đặt quạt ở mức mấy, từ 0 đến 5?",
                resumePrefix = FAN_PREFIX,
            )
        if (level !in MIN_FAN_LEVEL..MAX_FAN_LEVEL) {
            return RouteResult.NeedsClarification(
                "Mức quạt hỗ trợ từ 0 đến 5. Bạn chọn mức nào?",
                resumePrefix = FAN_PREFIX,
            )
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
        private const val TEMPERATURE_PREFIX = "nhiệt độ"
        private const val FAN_PREFIX = "quạt mức"

        private val NUMBER = Regex("""(\d{1,2})""")
        private val PUNCTUATION = Regex("""[,.!?;:]""")
        private val WHITESPACE = Regex("""\s+""")
        private val COMPOUND_CONNECTOR = Regex("""\s+(?:va|roi|sau do)\s+""")
        private val DIACRITICS = Regex("""\p{M}+""")
        // Canonical product wake: “Viva ơi”; keep Vivi/Vi-Vi aliases for PTT/ASR.
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

        // Folded (no diacritics). Skip standalone "phat" — it is also the media-play verb.
        private val FILLERS = listOf(
            "em oi",
            "bac tai",
            "giup toi",
            "ho cai",
            "nhe",
            "nha",
            "voi",
        )
        private val QUESTION_TAILS = listOf(
            "khong",
            "chua",
            "nhi",
            "phai khong",
            "dung khong",
        )
        private val QUESTION_CUES = listOf(
            "bao nhieu",
            "may do",
            "the nao",
            "sao roi",
            "cho toi biet",
            "cho minh biet",
            "hoi xem",
        )
        /** Folded: "quạt mức không" → level 0, must not hit question tails. */
        private val MUCN_KHONG_VALUE = Regex("""\bmuc\s+khong$""")

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
