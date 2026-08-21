package com.viva.voice.intent

import java.text.Normalizer
import java.util.Locale

/** Kết quả của cổng phủ định, chạy trước mọi tầng định tuyến. */
sealed interface NegationVerdict {
    /** Câu không mang phủ định — đi tiếp xuống router. */
    data object None : NegationVerdict

    /** Câu mang phủ định: không thực thi, hỏi lại. */
    data class Negated(val promptVi: String) : NegationVerdict
}

/**
 * Cổng phủ định N1–N2, chạy **trước** `GrammarIntentRouter`.
 *
 * Vì sao đứng trước: router khớp bằng `contains()` nên "đừng mở cửa" chứa
 * "mo cua" và mở khóa cửa thật. Đặt cổng sau router thì đã muộn.
 *
 * Vì sao đọc chữ **có dấu**: `GrammarIntentRouter.foldVietnamese` biến cả
 * "đừng" lẫn "dừng" thành "dung". Nếu cổng fold trước khi so thì "dừng nhạc"
 * (media_pause) bị chặn nhầm — đúng loại hồi quy mà 06-BO-CAU §5 cảnh báo là
 * "đổi một điểm lấy năm điểm".
 *
 * Vì sao so theo **token** chứ không phải chuỗi con: tiếng Việt dùng "không"
 * làm cả số 0. Corpus có `cmd_fan_0.wav` = "quạt mức không" nghĩa là quạt mức 0.
 * `contains("khong")` sẽ giết luôn lệnh đó (ca hồi quy N5).
 */
object NegationGate {

    fun inspect(text: String): NegationVerdict {
        val tokens = tokenize(text)
        if (tokens.isEmpty()) return NegationVerdict.None

        tokens.forEachIndexed { index, token ->
            val next = tokens.getOrNull(index + 1)
            if (isNegation(token, next)) return NegationVerdict.Negated(PROMPT_VI)
        }
        return NegationVerdict.None
    }

    private fun isNegation(token: String, next: String?): Boolean {
        if (token in STANDALONE_CUES) return true
        // "không" chỉ là phủ định khi có hành động ngay sau nó. Đứng cuối câu
        // hoặc trước một từ không phải động từ thì nó là giá trị số 0.
        if (token == KHONG) return next != null && next in ACTION_WORDS
        return false
    }

    private fun tokenize(raw: String): List<String> = Normalizer
        .normalize(raw, Normalizer.Form.NFC)
        .lowercase(Locale.ROOT)
        .split(SEPARATOR)
        .filter { it.isNotEmpty() }

    private val SEPARATOR = Regex("""[^\p{L}\p{N}]+""")

    private const val KHONG = "không"

    /**
     * Không có "không" ở đây: nó nhập nhằng với số 0 nên được xử lý riêng.
     * "dừng"/"đúng" cũng vắng mặt — chúng là lệnh media và câu đồng ý.
     */
    private val STANDALONE_CUES = setOf("đừng", "cấm", "chớ", "hủy", "huỷ", "khỏi")

    /** Động từ của miền lệnh xe; "không" + một trong số này mới là phủ định. */
    private val ACTION_WORDS = setOf(
        "bật", "tắt", "mở", "khóa", "khoá", "đóng",
        "tăng", "giảm", "đặt", "chỉnh", "hạ", "nâng", "để",
        "phát", "chuyển", "dừng", "thích", "lưu", "thêm",
        "cần", "muốn", "nên", "được", "phải", "cho",
    )

    private const val PROMPT_VI = "Mình chưa thực hiện gì cả. Bạn muốn mình làm gì?"
}
