package com.sopa.viva_automotive.feature.voice.data.vosk

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Khoá những tính chất mà nếu mất đi thì việc thu hẹp vốn từ **im lặng hỏng**.
 *
 * Vosk không báo lỗi khi grammar sai kiểu hay khi có từ lạ — nó chỉ lặng lẽ giải
 * mã kém đi. Không có test này thì một dấu phẩy thừa hay một lần lỡ tay xoá
 * `[unk]` sẽ chỉ lộ ra khi ai đó nói vào micro, và lộ ra dưới dạng "dạo này nó
 * nghe tệ hơn" chứ không phải một dòng stack trace.
 */
class CommandVocabularyTest {

    private fun grammarWords(): List<String> {
        val array = JSONArray(CommandVocabulary.asGrammarJson())
        return (0 until array.length()).map { array.getString(it) }
    }

    @Test
    fun `grammar la JSON array hop le`() {
        // Chuỗi được nối tay chứ không qua thư viện JSON, nên đây là chỗ duy
        // nhất phát hiện một dấu ngoặc đặt sai.
        assertEquals(CommandVocabulary.size + 1, grammarWords().size)
    }

    @Test
    fun `grammar co token unk`() {
        // Đây là tính chất AN TOÀN, không phải chi tiết kỹ thuật. Thiếu `[unk]`,
        // decoder buộc phải giải thích mọi âm thanh nghe được bằng vốn từ lệnh —
        // tiếng ồn trong xe sẽ ra một lệnh xe hợp lệ và `SafetyGuard` nhận được
        // một câu trông hoàn toàn bình thường.
        assertTrue("[unk]" in grammarWords())
    }

    @Test
    fun `von tu nho hon nhieu so voi von tu day du cua model`() {
        // Toàn bộ lý do đổi sang bản small là để giải mã trên ~10² từ thay vì
        // 19.529. Nếu một ngày danh sách phình lên tới hàng nghìn thì lợi ích
        // đã mất, và test này nói ra điều đó trước khi ai kịp đo lại WER.
        assertTrue(
            "Von tu lenh dang co ${CommandVocabulary.size} tu — phinh qua muc thi " +
                "rang buoc mat tac dung",
            CommandVocabulary.size < 300,
        )
    }

    @Test
    fun `khong co tu nao trong veo hay lot dau nhay`() {
        // Chuỗi được nối tay không escape gì cả. Một từ chứa dấu nháy kép hay
        // khoảng trắng thừa sẽ đẩy ra JSON hỏng hoặc một "từ" Vosk không bao giờ
        // khớp được.
        val bad = grammarWords().filter { it.isBlank() || '"' in it || it != it.trim() }
        assertEquals(emptyList<String>(), bad)
    }

    @Test
    fun `khong co tu trung lap`() {
        val words = grammarWords()
        val duplicates = words.groupBy { it }.filterValues { it.size > 1 }.keys
        assertEquals(emptySet<String>(), duplicates)
    }

    @Test
    fun `co du tu so doc bang chu`() {
        // `graph/Gr.fst` không có token chữ số nào, nên "24 độ" luôn ra khỏi ASR
        // dưới dạng "hai mươi bốn độ". Thiếu nhóm từ này thì mọi lệnh có số đều
        // hỏng, mà benchmark bơm-text sẽ không thấy vì nó đưa vào chữ số.
        val words = grammarWords()
        listOf("hai", "mươi", "bốn", "tư", "lăm", "linh", "độ", "mức").forEach {
            assertTrue("thieu tu so: $it", it in words)
        }
    }
}
