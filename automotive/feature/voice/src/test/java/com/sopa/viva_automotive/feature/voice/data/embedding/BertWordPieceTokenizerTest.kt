package com.sopa.viva_automotive.feature.voice.data.embedding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bộ test này sinh ra từ một lỗi đo được trên máy thật, không phải từ lo xa.
 *
 * Chạy bộ 22 câu trên emulator, câu B20 *"đặt bàn ăn tối"* bị định tuyến thành
 * `vehicle_status_speed` rồi **thực thi**. Log của matcher ghi `cos=1.0` — tức
 * vector của câu đặt bàn ăn **trùng khít** vector của exemplar *"tốc độ hiện
 * tại"*.
 *
 * Nguyên nhân nằm ở đây: vocab của MiniLM là WordPiece tiếng Anh, nên mọi từ
 * tiếng Việt có dấu đều tách thành `[UNK]`. Hai câu bốn từ khác hẳn nhau cho ra
 * cùng một chuỗi `[CLS] [UNK] [UNK] [UNK] [UNK] [SEP]`, cùng một vector, cosine
 * bằng 1. Tầng embedding không khớp ngữ nghĩa cho tiếng Việt — nó khớp theo
 * **số từ**.
 */
class BertWordPieceTokenizerTest {

    /** Đủ để phân biệt "biết" và "không biết"; không cần cả vocab 30k dòng. */
    private val vocab = listOf(
        "[PAD]", "[UNK]", "[CLS]", "[SEP]",
        "speed", "fuel", "what", "is", "my", "the", "level",
        "24", "temperature", "##s",
    ).withIndex().associate { (index, token) -> token to index }

    private val tokenizer = BertWordPieceTokenizer(vocab)

    @Test
    fun `an English sentence in vocabulary has no unknown tokens`() {
        val encoding = tokenizer.encode("what is my speed")

        assertEquals(4, encoding.contentTokens)
        assertEquals(0, encoding.unknownTokens)
        assertFalse(encoding.isAllUnknown)
    }

    @Test
    fun `a Vietnamese sentence collapses entirely to unknown tokens`() {
        val encoding = tokenizer.encode("đặt bàn ăn tối")

        assertEquals(4, encoding.contentTokens)
        assertEquals(4, encoding.unknownTokens)
        assertTrue(encoding.isAllUnknown)
    }

    @Test
    fun `two different Vietnamese sentences of equal length encode identically`() {
        // Đây chính xác là cơ chế đã đẩy B20 sang truy vấn tốc độ. Test này tồn
        // tại để nếu ai đó đổi sang model đa ngữ, nó ĐỔ — và đó là tin tốt.
        val dinner = tokenizer.encode("đặt bàn ăn tối")
        val speed = tokenizer.encode("tốc độ hiện tại")

        assertTrue(dinner.inputIds.contentEquals(speed.inputIds))
        assertTrue(dinner.isAllUnknown)
        assertTrue(speed.isAllUnknown)
    }

    @Test
    fun `a mixed sentence keeps its known tokens and is not refused`() {
        // Câu còn chữ trong từ điển thì vector vẫn mang thông tin thật, nên
        // không được vứt đi — chỉ vứt khi không còn gì để đọc.
        val encoding = tokenizer.encode("speed 24 độ")

        assertEquals(3, encoding.contentTokens)
        assertEquals(1, encoding.unknownTokens)
        assertFalse(encoding.isAllUnknown)
    }

    @Test
    fun `an empty string is not reported as all-unknown`() {
        // Rỗng nghĩa là không có gì để phán, khác với "có chữ nhưng không đọc
        // được". Gộp hai thứ này lại sẽ khiến tầng trên xử lý sai một trong hai.
        val encoding = tokenizer.encode("   ")

        assertEquals(0, encoding.contentTokens)
        assertFalse(encoding.isAllUnknown)
    }

    @Test
    fun `cased mode preserves case for a cased model`() {
        val casedVocab = listOf("[PAD]", "[UNK]", "[CLS]", "[SEP]", "VIVA", "viva")
            .withIndex()
            .associate { (index, token) -> token to index }
        val cased = BertWordPieceTokenizer(casedVocab, lowercase = false)

        val upper = cased.encode("VIVA")
        val lower = cased.encode("viva")

        assertFalse(upper.inputIds.contentEquals(lower.inputIds))
        assertEquals(casedVocab.getValue("VIVA").toLong(), upper.inputIds[1])
        assertEquals(casedVocab.getValue("viva").toLong(), lower.inputIds[1])
    }
}
