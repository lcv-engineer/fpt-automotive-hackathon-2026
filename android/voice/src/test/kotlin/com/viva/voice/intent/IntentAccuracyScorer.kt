package com.viva.voice.intent

/**
 * Scores an ASR benchmark by what the system would DO, not by how the words look.
 *
 * WER punishes "đặt"->"đạc" as a total miss while the grammar router, which keys
 * on "nhiệt độ" plus the number, is unaffected. It also under-punishes a clean
 * transcript with the wrong number, which changes the action. So both the
 * reference and the hypothesis are routed through the real [IntentRouter] and
 * the outcomes compared.
 *
 * Lives in test sources on purpose: this is measurement tooling, not product
 * code, and it must never end up on the APK path.
 */
class IntentAccuracyScorer(private val router: IntentRouter) {

    data class Row(val clip: String, val reference: String, val hypothesis: String)

    data class Score(
        val total: Int,
        val correct: Int,
        /**
         * Rows whose reference itself does not route to an action — a corpus
         * problem, not a model one. Covers both [RouteResult.Unsupported] and
         * [RouteResult.NeedsClarification]: neither one is an action the car
         * takes, so a reference landing on either can't be used to judge
         * whether the ASR error changed the outcome.
         */
        val referenceUnroutable: Int,
    ) {
        val accuracy: Double get() = if (total == 0) 0.0 else correct.toDouble() / total
    }

    fun score(rows: List<Row>): Score {
        var correct = 0
        var referenceUnroutable = 0
        for (row in rows) {
            val referenceKey = outcomeKey(router.route(row.reference))
            val hypothesisKey = outcomeKey(router.route(row.hypothesis))
            if (referenceKey.startsWith("unsupported") || referenceKey.startsWith("clarify")) {
                referenceUnroutable++
            }
            if (referenceKey == hypothesisKey) correct++
        }
        return Score(total = rows.size, correct = correct, referenceUnroutable = referenceUnroutable)
    }

    /**
     * Collapses a route into the identity of the action taken.
     *
     * Slots are part of the key: `hvac_set_temp(24)` and `hvac_set_temp(20)` are
     * two different actions, not two spellings of one. [RouteResult.Unsupported.canFallback]
     * is part of the key too: all three call sites in [GrammarIntentRouter] share
     * the default `rule = "G3_UNSUPPORTED"`, but `canFallback` decides whether a
     * lower tier gets a turn — that's a real behavioural difference, not noise.
     */
    fun outcomeKey(result: RouteResult): String = when (result) {
        is RouteResult.Matched -> {
            val slots = result.intent.slots.entries
                .sortedBy { it.key }
                .joinToString(",") { "${it.key}=${it.value}" }
            "matched:${result.intent.name}($slots)"
        }
        is RouteResult.NeedsClarification -> "clarify:${result.rule}"
        is RouteResult.Unsupported -> "unsupported:${result.rule}:canFallback=${result.canFallback}"
    }

    companion object {
        /**
         * Minimal RFC 4180 reader. Vietnamese prompts contain commas, so the
         * Python writer quotes them; splitting on ',' would shred those rows.
         */
        fun parseCsv(text: String): List<Row> {
            val records = splitRecords(text)
            if (records.isEmpty()) return emptyList()
            val header = records.first().map { it.trim() }
            val clipAt = header.indexOf("clip")
            val referenceAt = header.indexOf("reference")
            val hypothesisAt = header.indexOf("hypothesis")
            require(clipAt >= 0 && referenceAt >= 0 && hypothesisAt >= 0) {
                "CSV must have clip/reference/hypothesis columns, got $header"
            }
            return records.drop(1)
                .filter { it.size > maxOf(clipAt, referenceAt, hypothesisAt) }
                .map { Row(it[clipAt], it[referenceAt], it[hypothesisAt]) }
        }

        private fun splitRecords(text: String): List<List<String>> {
            val records = mutableListOf<List<String>>()
            var fields = mutableListOf<String>()
            val field = StringBuilder()
            var quoted = false
            var index = 0
            while (index < text.length) {
                val ch = text[index]
                when {
                    quoted && ch == '"' && index + 1 < text.length && text[index + 1] == '"' -> {
                        field.append('"')
                        index++
                    }
                    ch == '"' -> quoted = !quoted
                    !quoted && ch == ',' -> {
                        fields.add(field.toString()); field.setLength(0)
                    }
                    !quoted && (ch == '\n' || ch == '\r') -> {
                        if (field.isNotEmpty() || fields.isNotEmpty()) {
                            fields.add(field.toString()); field.setLength(0)
                            records.add(fields); fields = mutableListOf()
                        }
                        if (ch == '\r' && index + 1 < text.length && text[index + 1] == '\n') index++
                    }
                    else -> field.append(ch)
                }
                index++
            }
            if (field.isNotEmpty() || fields.isNotEmpty()) {
                fields.add(field.toString())
                records.add(fields)
            }
            return records
        }
    }
}
