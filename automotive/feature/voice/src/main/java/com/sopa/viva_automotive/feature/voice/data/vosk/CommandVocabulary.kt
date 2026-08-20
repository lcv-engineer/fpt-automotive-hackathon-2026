package com.sopa.viva_automotive.feature.voice.data.vosk

/**
 * Vốn từ mà Vosk được phép nhả ra cho một lượt lệnh xe.
 *
 * ## Vì sao có file này
 *
 * Model `vosk-model-small-vn-0.4` giải mã trên **19.529 từ** tiếng Việt. Trợ lý
 * này chỉ cần khoảng một trăm. Phần chênh lệch đó không hề vô hại — nó là toàn
 * bộ nguyên nhân của nhóm lỗi tệ nhất đo được trên máy ngày 05/08:
 *
 * ```
 * nói  "tăng nhiệt độ lên hai mươi tư độ"
 * nghe "chang nhiet do len ha my tu do"
 * nói  "giảm tốc độ quạt xuống hai"
 * nghe "giảm nhiệt lũ quét súng hơi hay"
 * ```
 *
 * Không câu nào trong đó là lỗi âm học thuần tuý. Model nghe đúng đại khái phần
 * âm, rồi **mô hình ngôn ngữ tổng quát** kéo kết quả về phía những chuỗi từ phổ
 * biến trong văn bản đời thường ("lũ quét", "súng") thay vì phía những chuỗi mà
 * người đang lái xe thực sự nói. Thu hẹp vốn từ là cách sửa đúng tầng: nó đổi
 * chính cái mô hình ngôn ngữ đang kéo sai.
 *
 * ## Vì sao là DANH SÁCH TỪ, không phải danh sách câu
 *
 * Vosk nhận grammar dạng JSON array. Mỗi phần tử có thể là cả câu, và khi đó
 * decoder chỉ chấp nhận đúng những câu đã liệt kê. Ở đây **cố ý không làm vậy**:
 * `GrammarIntentRouter` vốn khớp mờ và chịu được đảo trật tự từ, nên ràng ở mức
 * câu sẽ vứt bỏ đúng những cách nói hợp lệ mà router thừa sức hiểu ("hạ điều hòa
 * xuống hai mươi tư" và "hai mươi tư độ thôi" cùng ra một intent). Liệt kê ở mức
 * **từ** cho phép mọi tổ hợp của các từ này — vẫn giảm không gian tìm kiếm từ
 * 19.529 xuống ~130, nhưng không đoán trước tài xế sẽ xếp câu ra sao.
 *
 * ## `[unk]` là điều kiện an toàn, không phải tuỳ chọn
 *
 * Thiếu `[unk]`, decoder **buộc** phải giải thích mọi thứ nghe được bằng vốn từ
 * này — tiếng ồn trong xe sẽ ra một lệnh xe hợp lệ, và `SafetyGuard` nhận được
 * một câu trông hoàn toàn bình thường. Có `[unk]`, thứ ngoài phạm vi rơi vào
 * token đó và lượt nói chết ở NLU, đúng chỗ nó phải chết.
 *
 * ## Ràng buộc
 *
 * Mọi từ ở đây **phải** có trong bảng ký hiệu của model. Vosk lặng lẽ bỏ qua từ
 * lạ, nên một lỗi chính tả sẽ thành một lệnh không bao giờ nhận được mà không có
 * lấy một dòng log. Bảng ký hiệu nằm gắn trong `graph/Gr.fst` (model này không
 * kèm `words.txt` rời); `backend/scripts/check_command_vocab.py` rút bảng đó ra
 * và đối chiếu — chạy lại script đó mỗi khi sửa danh sách dưới đây.
 */
internal object CommandVocabulary {

    /**
     * Token Vosk dùng cho "nghe được gì đó nhưng không thuộc vốn từ này".
     *
     * Xem phần `[unk]` ở tài liệu lớp trên: bỏ token này đi thì tiếng ồn thành lệnh.
     */
    private const val UNKNOWN = "[unk]"

    private val WORDS: List<String> = listOf(
        // — từ gọi —
        "viva", "ơi",

        // — điều hòa / nhiệt độ —
        "điều", "hòa", "hoà", "máy", "lạnh", "nóng", "ấm",
        "nhiệt", "độ", "tăng", "giảm", "hạ", "lên", "xuống", "đặt", "để", "về",

        // — quạt gió —
        "quạt", "gió", "mức", "số", "mạnh", "nhẹ", "to", "nhỏ",

        // — cửa —
        "mở", "khóa", "khoá", "đóng", "cửa", "xe",

        // — nhạc / âm lượng —
        "âm", "lượng", "nhạc", "bài", "hát", "chuyển", "tiếp", "theo", "phát", "dừng", "tạm",

        // — giao hàng —
        "chặng", "điểm", "giao", "hàng", "đơn", "kế", "xác", "nhận", "đã", "thành", "công",

        // — hỏi trạng thái —
        "còn", "bao", "nhiêu", "xăng", "nhiên", "liệu", "pin", "điện", "ắc", "quy",
        "tốc", "hiện", "tại", "đang", "chạy", "là", "gì", "cho", "biết", "mấy", "thế", "nào",

        // — số đọc bằng chữ —
        //
        // `graph/Gr.fst` có **0 token chữ số** (đã kiểm: chỉ mỗi `#0` là ký hiệu
        // disambig của Kaldi). Nói "24 độ" thì thứ đi ra khỏi ASR luôn là
        // "hai mươi bốn độ", nên không có những từ này thì mọi lệnh có số đều hỏng.
        "không", "một", "hai", "ba", "bốn", "tư", "năm", "lăm", "sáu", "bảy", "tám", "chín",
        "mười", "mươi", "linh", "rưỡi", "phần", "trăm",

        // — chữ cái cho mã đơn ("đơn a mười hai") —
        "a", "b", "c", "d", "e", "g", "h",
    )

    /**
     * Grammar dạng JSON array cho `Recognizer(model, sampleRate, grammar)`.
     *
     * Tự nối chuỗi thay vì kéo thư viện JSON: mọi phần tử đều là chữ thường
     * không dấu ngoặc kép, nên không có gì để escape, và đây là đường chạy lúc
     * khởi tạo mỗi lượt nói.
     */
    fun asGrammarJson(): String =
        (WORDS + UNKNOWN).joinToString(prefix = "[\"", separator = "\", \"", postfix = "\"]")

    /** Số từ trong vốn — để ghi log so với 19.529 từ của model đầy đủ. */
    val size: Int get() = WORDS.size
}
