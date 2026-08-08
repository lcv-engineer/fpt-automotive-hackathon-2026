package com.sopa.viva_automotive.feature.voice.data.embedding

class BertWordPieceTokenizer(
    private val vocab: Map<String, Int>,
    private val maxLength: Int = 128,
    private val lowercase: Boolean = true,
) {

    data class Encoding(
        val inputIds: LongArray,
        val attentionMask: LongArray,
        val tokenTypeIds: LongArray,
        /** Số token nội dung (không kể `[CLS]`/`[SEP]`) rơi vào `[UNK]`. */
        val unknownTokens: Int,
        /** Số token nội dung, tức độ dài trừ `[CLS]` và `[SEP]`. */
        val contentTokens: Int,
    ) {
        /**
         * Không một token nội dung nào nằm trong từ điển.
         *
         * Vector cho một chuỗi như vậy chỉ phản ánh **số lượng** `[UNK]`, không
         * phản ánh nghĩa: hai câu tiếng Việt khác hẳn nhau nhưng cùng số từ sẽ
         * cho vector giống hệt nhau và cosine bằng 1.0. Vocab của MiniLM là
         * WordPiece tiếng Anh, nên gần như mọi câu tiếng Việt có dấu đều rơi
         * vào trường hợp này.
         */
        val isAllUnknown: Boolean get() = contentTokens > 0 && unknownTokens == contentTokens
    }

    fun encode(text: String): Encoding {
        val tokens = ArrayList<String>(32)
        tokens.add(CLS)
        for (word in basicTokenize(text)) {
            tokens.addAll(wordPiece(word))
        }
        tokens.add(SEP)

        if (tokens.size > maxLength) {
            val kept = tokens.subList(0, maxLength - 1).toMutableList()
            kept.add(SEP)
            tokens.clear()
            tokens.addAll(kept)
        }

        val ids = LongArray(tokens.size)
        val mask = LongArray(tokens.size) { 1L }
        val types = LongArray(tokens.size)
        for (i in tokens.indices) {
            ids[i] = (vocab[tokens[i]] ?: unkId).toLong()
        }

        // Đếm trên chuỗi token, không đếm lại theo id: một token có mặt trong
        // vocab nhưng chính là "[UNK]" vẫn phải tính là không biết.
        val content = tokens.subList(1, (tokens.size - 1).coerceAtLeast(1))
        return Encoding(
            inputIds = ids,
            attentionMask = mask,
            tokenTypeIds = types,
            unknownTokens = content.count { it == UNK },
            contentTokens = content.size,
        )
    }

    private fun basicTokenize(text: String): List<String> {
        val normalized = (if (lowercase) text.lowercase() else text).trim()
        if (normalized.isEmpty()) return emptyList()
        val out = ArrayList<String>()
        val current = StringBuilder()
        fun flush() {
            if (current.isNotEmpty()) {
                out.add(current.toString())
                current.clear()
            }
        }
        for (ch in normalized) {
            when {
                ch.isWhitespace() -> flush()
                isPunctuation(ch) -> {
                    flush()
                    out.add(ch.toString())
                }
                else -> current.append(ch)
            }
        }
        flush()
        return out
    }

    private fun wordPiece(word: String): List<String> {
        if (word in vocab) return listOf(word)
        val chars = word
        if (chars.isEmpty()) return emptyList()
        val output = ArrayList<String>()
        var start = 0
        while (start < chars.length) {
            var end = chars.length
            var found: String? = null
            while (start < end) {
                val piece = if (start == 0) {
                    chars.substring(start, end)
                } else {
                    "##${chars.substring(start, end)}"
                }
                if (piece in vocab) {
                    found = piece
                    break
                }
                end--
            }
            if (found == null) {
                return listOf(UNK)
            }
            output.add(found)
            start = end
        }
        return output
    }

    private val unkId: Int = vocab[UNK] ?: 100

    companion object {
        private const val CLS = "[CLS]"
        private const val SEP = "[SEP]"
        private const val UNK = "[UNK]"

        fun fromVocabLines(
            lines: List<String>,
            maxLength: Int = 128,
            lowercase: Boolean = true,
        ): BertWordPieceTokenizer {
            val map = HashMap<String, Int>(lines.size)
            lines.forEachIndexed { index, raw ->
                val token = raw.trim()
                if (token.isNotEmpty()) map[token] = index
            }
            return BertWordPieceTokenizer(map, maxLength, lowercase)
        }

        private fun isPunctuation(ch: Char): Boolean {
            val type = Character.getType(ch).toByte()
            return ch in "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~" ||
                type == Character.CONNECTOR_PUNCTUATION ||
                type == Character.DASH_PUNCTUATION ||
                type == Character.START_PUNCTUATION ||
                type == Character.END_PUNCTUATION ||
                type == Character.OTHER_PUNCTUATION ||
                type == Character.INITIAL_QUOTE_PUNCTUATION ||
                type == Character.FINAL_QUOTE_PUNCTUATION
        }
    }
}
