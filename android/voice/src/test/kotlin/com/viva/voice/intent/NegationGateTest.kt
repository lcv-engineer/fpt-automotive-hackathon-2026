package com.viva.voice.intent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * N1–N4 (phủ định phải chặn) và N5–N7 (hồi quy phải giữ nguyên).
 *
 * Bảng ca lấy từ `vong3/06-BO-CAU-DEMO-ON-DINH.md` §5. N5–N7 quan trọng hơn
 * N1–N4: vá phủ định mà làm hỏng lệnh đang chạy là đổi một điểm lấy năm điểm.
 */
class NegationGateTest {

    private fun negated(text: String) = NegationGate.inspect(text) is NegationVerdict.Negated

    // ---------- N1–N4: phải chặn ----------

    @Test fun `dung + verb is negation`() = assertTrue(negated("Vivi ơi đừng mở cửa"))

    @Test fun `khong duoc is negation`() = assertTrue(negated("Vivi ơi không được mở cửa"))

    @Test fun `khong muon tang nhiet do is negation`() =
        assertTrue(negated("tôi không muốn tăng nhiệt độ"))

    @Test fun `khong muon giam nhiet do is negation`() =
        assertTrue(negated("tôi không muốn giảm nhiệt độ"))

    @Test fun `dung bat den is negation`() = assertTrue(negated("Vivi ơi đừng bật đèn"))

    @Test fun `khoi can is negation`() = assertTrue(negated("khỏi cần chuyển bài tiếp theo"))

    @Test fun `huy lenh is negation`() = assertTrue(negated("Vivi ơi hủy lệnh mở cửa"))

    @Test fun `cam is negation`() = assertTrue(negated("Vivi ơi cấm mở cửa nhé"))

    @Test fun `bare khong before a verb is negation`() = assertTrue(negated("không mở cửa"))

    // ---------- N5–N7 + va chạm: KHÔNG được chặn ----------

    /** N5: "không" ở vị trí giá trị là số 0, không phải phủ định. */
    @Test fun `N5 quat muc khong stays a level zero command`() =
        assertFalse(negated("Vivi ơi quạt mức không"))

    @Test fun `N6 spoken temperature stays a command`() =
        assertFalse(negated("Vivi ơi nhiệt độ hai tư độ"))

    @Test fun `N7 nong qua stays a clarification`() = assertFalse(negated("Vivi ơi nóng quá"))

    @Test fun `plain command is untouched`() = assertFalse(negated("Vivi ơi mở cửa"))

    /**
     * Bẫy dấu: `foldVietnamese` biến cả "đừng" và "dừng" thành "dung". Cổng phủ
     * định vì thế phải đọc chữ CÓ DẤU — nếu fold trước thì "dừng nhạc" (media_pause)
     * bị chặn nhầm, đúng kiểu hồi quy mà §5 cảnh báo.
     */
    @Test fun `dung nhac is a media command not a negation`() =
        assertFalse(negated("Vivi ơi dừng nhạc"))

    @Test fun `tam dung nhac is a media command not a negation`() =
        assertFalse(negated("Vivi ơi tạm dừng nhạc"))

    @Test fun `dung roi is agreement not a negation`() = assertFalse(negated("đúng rồi"))

    // ---------- hành vi khi chặn ----------

    @Test
    fun `negation asks back instead of failing silently`() {
        val verdict = NegationGate.inspect("đừng mở cửa") as NegationVerdict.Negated
        assertTrue(verdict.promptVi.isNotBlank())
        assertTrue("phải là câu hỏi lại", verdict.promptVi.contains("?"))
    }
}
