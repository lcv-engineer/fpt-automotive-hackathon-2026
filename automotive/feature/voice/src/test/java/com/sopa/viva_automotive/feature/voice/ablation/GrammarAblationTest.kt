package com.sopa.viva_automotive.feature.voice.ablation

import com.sopa.viva_automotive.feature.voice.FakeCommandMappingDao
import com.sopa.viva_automotive.feature.voice.FakeSemanticIntentMatcher
import com.sopa.viva_automotive.feature.voice.data.CommandMappingRepository
import com.sopa.viva_automotive.feature.voice.domain.ProcessVoiceCommandUseCase
import com.sopa.viva_automotive.feature.voice.domain.model.VehicleIntent
import com.viva.voice.intent.GrammarIntentRouter
import com.viva.voice.intent.IntentRouter
import com.viva.voice.intent.RouteResult
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * N4 ablation — **bỏ tầng grammar T0** và đo xem cái gì sụp.
 *
 * `vong2/16-QUYET-DINH-DUONG-NLU.md` gọi tên đúng phép đo này:
 * *"Ablation: bỏ grammar → mọi lệnh phụ thuộc ngưỡng cosine, và các câu từ chối
 * ở bước 4 hết chốt chặn. Đây là một mục ablation rẻ, chạy được ngay, không cần
 * Device."* Đây là hiện thực của nó.
 *
 * Hai cấu hình chạy trên **cùng một bộ câu** — chính `backend/suites/benchmark_v1.csv`
 * mà harness dùng, nên không có chuyện hai artifact nói về hai tập câu khác nhau:
 *
 *  - **full**       — `GrammarIntentRouter` thật, đúng như production wiring
 *  - **no-grammar** — router trả `Unsupported(canFallback = true)` cho mọi câu,
 *                     tức mọi thứ rơi xuống keyword + embedding
 *
 * ⚠️ **Giới hạn phải khai khi trích số này.** Tầng embedding thật là MiniLM ONNX,
 * không nạp được trong unit test JVM, nên nhánh no-grammar ở đây chỉ có
 * **keyword mapping** (embedding fake trả null). Nghĩa là bảng này *đánh giá thấp*
 * mức độ hư hại: trên máy thật, embedding còn có thể suy ra một lệnh xe cho những
 * câu mà ở đây rơi vào `Unknown`. Không được viết ngược lại thành "chỉ mất X câu".
 */
class GrammarAblationTest {

    private object NoGrammarRouter : IntentRouter {
        override fun route(text: String): RouteResult =
            RouteResult.Unsupported(canFallback = true)
    }

    /** Một dòng của bộ câu benchmark; chỉ cần id + câu nói. */
    private data class Case(val id: String, val utterance: String)

    private data class Row(
        val case: Case,
        val full: VehicleIntent,
        val noGrammar: VehicleIntent,
    )

    @Test
    fun `bo grammar thi cau da bi cat lai thanh lenh xe that`() = runTest {
        val cases = loadSuite()
        assertTrue("Bộ câu rỗng — kiểm lại đường dẫn suite", cases.size >= 20)

        val rows = cases.map { case ->
            Row(
                case = case,
                full = route(case.utterance, GrammarIntentRouter()),
                noGrammar = route(case.utterance, NoGrammarRouter),
            )
        }

        writeCsv(rows)
        printTable(rows)

        // Phát hiện chính: câu bị grammar từ chối có lý do, khi bỏ grammar lại
        // trở thành một lệnh xe thật sự được thực thi.
        val refusalsTurnedIntoCommands = rows.filter { row ->
            row.full is VehicleIntent.Clarification && row.noGrammar.isExecutableVehicleCommand()
        }
        assertTrue(
            "Không câu nào bị grammar chặn mà lọt khi bỏ grammar — nếu đúng như vậy thì " +
                "luận điểm 'grammar là chốt chặn' trong 16-QUYET-DINH-DUONG-NLU không còn đúng " +
                "và phải sửa văn bản đó, chứ không phải sửa test này.",
            refusalsTurnedIntoCommands.isNotEmpty(),
        )

        // Phát hiện thứ hai: lệnh lõi ngừng hoạt động, không phải chỉ mất phần từ chối.
        val coreCommandsLost = rows.filter { row ->
            row.full.isExecutableVehicleCommand() && !row.noGrammar.isExecutableVehicleCommand()
        }
        assertTrue(
            "Bỏ grammar mà không mất lệnh lõi nào thì grammar không đóng góp gì cho happy path — " +
                "kiểm lại trước khi tin.",
            coreCommandsLost.isNotEmpty(),
        )
    }

    private suspend fun route(utterance: String, router: IntentRouter): VehicleIntent {
        val repository = CommandMappingRepository(FakeCommandMappingDao())
        repository.seedIfEmpty()
        // Embedding fake trả null cho mọi câu: xem ghi chú giới hạn ở đầu file.
        val useCase = ProcessVoiceCommandUseCase(repository, FakeSemanticIntentMatcher(), router)
        return useCase(utterance)
    }

    /**
     * "Thực thi được" nghĩa là một lệnh xe/media chạm tới thiết bị, chứ không
     * phải một câu hỏi lại hay một lời từ chối. `NotWired` cũng tính là *đã định
     * tuyến thành lệnh* — nó chỉ thiếu adapter trong bản build này.
     */
    private fun VehicleIntent.isExecutableVehicleCommand(): Boolean = when (this) {
        is VehicleIntent.Clarification, is VehicleIntent.Unknown -> false
        else -> true
    }

    private fun VehicleIntent.label(): String = when (this) {
        is VehicleIntent.SetTemperature -> "SetTemperature(${temperatureCelsius})"
        is VehicleIntent.AdjustTemperature -> "AdjustTemperature(${deltaCelsius})"
        is VehicleIntent.SetFanSpeed -> "SetFanSpeed($level)"
        is VehicleIntent.AdjustFanSpeed -> "AdjustFanSpeed($delta)"
        is VehicleIntent.SetAc -> "SetAc($on)"
        is VehicleIntent.SetHvacPower -> "SetHvacPower($on)"
        is VehicleIntent.SetDoorLock -> "SetDoorLock($locked)"
        is VehicleIntent.QueryStatus -> "QueryStatus($kind)"
        is VehicleIntent.VolumeAdjust -> "VolumeAdjust($delta)"
        is VehicleIntent.Media -> "Media(${command.intentName})"
        is VehicleIntent.Delivery -> "Delivery(${command.intentName})"
        is VehicleIntent.NotWired -> "NotWired($intentName)"
        is VehicleIntent.Clarification -> "Clarification"
        is VehicleIntent.Unknown -> "Unknown"
    }

    /**
     * Đọc thẳng bộ câu của harness thay vì chép lại danh sách — hai artifact
     * lệch nhau là cách chắc chắn nhất để bảng ablation nói về một tập câu khác
     * với bảng benchmark.
     */
    private fun loadSuite(): List<Case> {
        val file = findRepoFile("backend/suites/benchmark_v1.csv")
        val lines = file.readLines().filter { it.isNotBlank() }
        val header = splitCsv(lines.first()).map { it.trim().lowercase() }
        val idIdx = header.indexOf("id")
        val utteranceIdx = header.indexOf("utterance")
        check(idIdx >= 0 && utteranceIdx >= 0) { "Suite thiếu cột id/utterance: $header" }

        return lines.drop(1).map { line ->
            val fields = splitCsv(line)
            Case(id = fields[idIdx].trim(), utterance = fields[utteranceIdx].trim())
        }
    }

    /** Tách CSV có tôn trọng dấu ngoặc kép — cột `notes` của suite có dấu phẩy. */
    private fun splitCsv(line: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    out += current.toString()
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        out += current.toString()
        return out
    }

    private fun workingDir(): File = File(requireNotNull(System.getProperty("user.dir")))

    private fun findRepoFile(relative: String): File {
        var dir: File? = workingDir()
        while (dir != null) {
            val candidate = File(dir, relative)
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        error("Không tìm thấy $relative khi đi ngược từ ${workingDir()}")
    }

    private fun writeCsv(rows: List<Row>) {
        val out = File(workingDir(), "build/reports/ablation/grammar-ablation.csv")
        out.parentFile?.mkdirs()
        val body = buildString {
            appendLine("id,utterance,full_intent,no_grammar_intent,delta")
            rows.forEach { row ->
                val delta = when {
                    row.full.label() == row.noGrammar.label() -> "same"
                    row.full is VehicleIntent.Clarification &&
                        row.noGrammar.isExecutableVehicleCommand() -> "REFUSAL_LOST"
                    row.full.isExecutableVehicleCommand() &&
                        !row.noGrammar.isExecutableVehicleCommand() -> "COMMAND_LOST"
                    else -> "changed"
                }
                appendLine(
                    listOf(
                        row.case.id,
                        "\"${row.case.utterance}\"",
                        row.full.label(),
                        row.noGrammar.label(),
                        delta,
                    ).joinToString(","),
                )
            }
        }
        out.writeText(body)
        println("Ablation CSV -> ${out.absolutePath}")
    }

    private fun printTable(rows: List<Row>) {
        println("== N4 ablation: grammar ON vs OFF (embedding faked, xem ghi chú giới hạn) ==")
        rows.forEach { row ->
            if (row.full.label() != row.noGrammar.label()) {
                println("  ${row.case.id}  \"${row.case.utterance}\"")
                println("      full       : ${row.full.label()}")
                println("      no-grammar : ${row.noGrammar.label()}")
            }
        }
        val changed = rows.count { it.full.label() != it.noGrammar.label() }
        println("  → ${changed}/${rows.size} câu đổi kết quả khi bỏ grammar")
    }
}
