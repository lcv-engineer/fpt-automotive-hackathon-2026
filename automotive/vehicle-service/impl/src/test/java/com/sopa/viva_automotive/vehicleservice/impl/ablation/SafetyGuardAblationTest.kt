package com.sopa.viva_automotive.vehicleservice.impl.ablation

import com.sopa.viva_automotive.vehicleservice.api.CarPropertyResult
import com.sopa.viva_automotive.vehicleservice.api.SafetyConfirmationRequiredException
import com.sopa.viva_automotive.vehicleservice.api.SafetyDeniedException
import com.sopa.viva_automotive.vehicleservice.api.VehicleAreas
import com.sopa.viva_automotive.vehicleservice.api.VehicleCommandSource
import com.sopa.viva_automotive.vehicleservice.api.VehicleProperties
import com.sopa.viva_automotive.vehicleservice.api.VehicleRepository
import com.sopa.viva_automotive.vehicleservice.api.VehicleWriteContext
import com.sopa.viva_automotive.vehicleservice.impl.DefaultSafetyGuard
import com.sopa.viva_automotive.vehicleservice.impl.GuardedVehicleRepository
import com.sopa.viva_automotive.vehicleservice.impl.MockVehicleRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * N4 ablation **A1 — bỏ `SafetyGuard`** và đo xem cái gì sụp.
 *
 * `23-N4-ABLATION.md` để cột `no_guard` là *chưa đo* với lý do *"chưa có cơ chế
 * tắt thật"*. Nhưng cơ chế tắt đã có sẵn trong chính kiến trúc: guard là một
 * **decorator** quanh `VehicleRepository`, nên "bỏ guard" đúng nghĩa là bind
 * thẳng repository gốc — **đúng bằng wiring production trừ đi một lớp**, không
 * phải một cờ `isEnabled` dựng riêng cho phép đo.
 *
 * Đây là điểm khác biệt đáng giá so với một cờ toggle: một cờ chỉ chứng minh
 * "code có nhánh tắt", còn bỏ decorator chứng minh **đúng thứ sẽ xảy ra nếu đội
 * không viết phần này** — tức là counterfactual mà barem hỏi ở ô *Mức quyết định
 * của phần team-owned*.
 *
 *  - **full**     — `GuardedVehicleRepository(MockVehicleRepository, DefaultSafetyGuard)`,
 *                   y hệt `VehicleServiceModule` của cả hai biến thể app
 *  - **no_guard** — `MockVehicleRepository` trần
 *
 * ## Giới hạn phải khai khi trích số này
 *
 * 1. Đầu kia là `MockVehicleRepository`, **không phải VHAL thật**. Bảng này
 *    chứng minh guard chặn *trước khi chạm setter*; nó không chứng minh gì về
 *    hành vi trên Device CarSky.
 * 2. Chỉ phủ các luật thật sự nằm trên đường sản phẩm. `vhal_server.luau` còn có
 *    G1.4 (âm lượng) và G2.1 (số), nhưng theo `03-contracts.md §0.1` thì
 *    `volume_adjust` đi `CarAudioManager` và không có intent nào về số — nên hai
 *    luật đó **không** thuộc phép đo này.
 * 3. Đây là ablation tầng JVM, cùng hạng với A4, **không phải** Device evidence.
 */
class SafetyGuardAblationTest {

    private data class Case(
        val id: String,
        val description: String,
        val propertyId: Int,
        val areaId: Int,
        val value: Any,
        val context: VehicleWriteContext,
        val speedKmh: Float?,
        /** Giá trị property trước khi thử ghi, để đọc lại mà biết có bị ghi không. */
        val initial: Any,
    )

    private data class Outcome(val verdict: String, val reachedVehicle: Boolean)

    private data class Row(val case: Case, val full: Outcome, val noGuard: Outcome)

    @Test
    fun `bo SafetyGuard thi lenh nguy hiem di thang toi xe`() = runTest {
        val rows = cases().map { case ->
            Row(
                case = case,
                full = runCase(case, guarded = true, scope = backgroundScope),
                noGuard = runCase(case, guarded = false, scope = backgroundScope),
            )
        }

        writeCsv(rows)
        printTable(rows)

        // Phát hiện chính: lệnh mà guard chặn, khi bỏ guard thì ghi được xuống xe.
        val dangerousWritesThatLand = rows.filter { row ->
            !row.full.reachedVehicle && row.noGuard.reachedVehicle
        }
        assertTrue(
            "Không lệnh nguy hiểm nào bị chặn mà lọt khi bỏ guard — nếu đúng vậy thì " +
                "SafetyGuard không quyết định claim an toàn, và 23-N4/write-up phải sửa " +
                "theo, chứ không phải sửa test này.",
            dangerousWritesThatLand.isNotEmpty(),
        )

        // Phát hiện thứ hai: guard không chặn bừa. Lệnh hợp lệ vẫn phải đi qua ở
        // cả hai cấu hình — một guard chặn tất cả cũng cho `dangerousWritesThatLand`
        // khác rỗng, nên không có dòng này thì phép đo trên vô nghĩa.
        val safeWrites = rows.filter { it.case.id.startsWith("A1-OK") }
        assertTrue("Thiếu ca đối chứng hợp lệ", safeWrites.isNotEmpty())
        safeWrites.forEach { row ->
            assertEquals(
                "${row.case.id} là lệnh hợp lệ nhưng guard chặn",
                true,
                row.full.reachedVehicle,
            )
        }
    }

    private suspend fun runCase(
        case: Case,
        guarded: Boolean,
        scope: CoroutineScope,
    ): Outcome {
        val underlying = MockVehicleRepository(scope, simulate = false)
        underlying.setProperty(case.propertyId, case.areaId, case.initial).getOrThrow()
        case.speedKmh?.let { kmh ->
            underlying.setProperty(
                VehicleProperties.PERF_VEHICLE_SPEED,
                VehicleAreas.GLOBAL,
                kmh / MPS_TO_KMH,
            ).getOrThrow()
        }

        val source: VehicleRepository =
            if (case.speedKmh == null) UnreadableSpeed(underlying) else underlying
        val repo: VehicleRepository = if (guarded) {
            GuardedVehicleRepository(delegate = source, guard = DefaultSafetyGuard())
        } else {
            source
        }

        val result = repo.setProperty(case.propertyId, case.areaId, case.value, case.context)
        val verdict = when (val error = result.exceptionOrNull()) {
            null -> "Allow"
            is SafetyDeniedException -> "Deny:${error.rule}"
            is SafetyConfirmationRequiredException -> "Confirm:${error.rule}"
            else -> "Error:${error::class.simpleName}"
        }

        val readback = underlying.getProperty(case.propertyId, case.areaId).getOrNull()?.value
        return Outcome(verdict = verdict, reachedVehicle = readback == case.value)
    }

    /**
     * Che đường đọc tốc độ để dựng đúng tình huống của flavor `real` hôm nay:
     * manifest xin `android.car.permission.CAR_SPEED` nhưng allowlist privapp
     * chưa có quyền đó, nên `SafetyGuard` không có tốc độ để mà xét.
     */
    private class UnreadableSpeed(private val delegate: VehicleRepository) : VehicleRepository {
        override fun observeProperty(propertyId: Int): Flow<CarPropertyResult> =
            delegate.observeProperty(propertyId)

        override suspend fun getProperty(propertyId: Int, areaId: Int): Result<CarPropertyResult> =
            if (propertyId == VehicleProperties.PERF_VEHICLE_SPEED) {
                Result.failure(SecurityException("Missing android.car.permission.CAR_SPEED"))
            } else {
                delegate.getProperty(propertyId, areaId)
            }

        override suspend fun setProperty(
            propertyId: Int,
            areaId: Int,
            value: Any,
            context: VehicleWriteContext,
        ): Result<Unit> = delegate.setProperty(propertyId, areaId, value, context)
    }

    private fun cases(): List<Case> {
        val voice = VehicleWriteContext(source = VehicleCommandSource.VOICE)
        val touch = VehicleWriteContext(source = VehicleCommandSource.HMI)
        return listOf(
            Case(
                "A1-01", "Mở khóa cửa khi xe chạy 60 km/h (giọng nói)",
                VehicleProperties.DOOR_LOCK, VehicleAreas.DOOR_ROW_1_LEFT, false,
                voice, speedKmh = 60f, initial = true,
            ),
            Case(
                "A1-02", "Mở khóa cửa khi xe chạy 60 km/h (chạm HMI)",
                VehicleProperties.DOOR_LOCK, VehicleAreas.DOOR_ROW_1_LEFT, false,
                touch, speedKmh = 60f, initial = true,
            ),
            Case(
                "A1-03", "Mở khóa cửa khi xe đứng yên, chưa xác nhận (giọng nói)",
                VehicleProperties.DOOR_LOCK, VehicleAreas.DOOR_ROW_1_LEFT, false,
                voice, speedKmh = 0f, initial = true,
            ),
            Case(
                "A1-04", "Mở khóa cửa khi KHÔNG đọc được tốc độ (thiếu CAR_SPEED)",
                VehicleProperties.DOOR_LOCK, VehicleAreas.DOOR_ROW_1_LEFT, false,
                voice, speedKmh = null, initial = true,
            ),
            Case(
                "A1-05", "Đặt nhiệt độ 40°C — ngoài dải 16–32",
                VehicleProperties.HVAC_TEMPERATURE_SET, VehicleAreas.SEAT_ZONE_DRIVER, 40f,
                voice, speedKmh = 0f, initial = 24f,
            ),
            Case(
                "A1-06", "Đặt nhiệt độ 5°C — ngoài dải 16–32",
                VehicleProperties.HVAC_TEMPERATURE_SET, VehicleAreas.SEAT_ZONE_DRIVER, 5f,
                voice, speedKmh = 0f, initial = 24f,
            ),
            Case(
                "A1-OK-07", "Khóa cửa khi xe chạy 60 km/h — hành động an toàn",
                VehicleProperties.DOOR_LOCK, VehicleAreas.DOOR_ROW_1_LEFT, true,
                voice, speedKmh = 60f, initial = false,
            ),
            Case(
                "A1-OK-08", "Đặt nhiệt độ 24°C khi xe đứng yên — lệnh hợp lệ",
                VehicleProperties.HVAC_TEMPERATURE_SET, VehicleAreas.SEAT_ZONE_DRIVER, 24f,
                voice, speedKmh = 0f, initial = 20f,
            ),
            Case(
                "A1-OK-09", "Mở khóa cửa khi xe đứng yên, đã xác nhận (lượt thứ hai)",
                VehicleProperties.DOOR_LOCK, VehicleAreas.DOOR_ROW_1_LEFT, false,
                VehicleWriteContext(source = VehicleCommandSource.VOICE, isConfirmed = true),
                speedKmh = 0f, initial = true,
            ),
        )
    }

    private fun workingDir(): File = File(requireNotNull(System.getProperty("user.dir")))

    private fun writeCsv(rows: List<Row>) {
        val out = File(workingDir(), "build/reports/ablation/a1-safety-guard-ablation.csv")
        out.parentFile?.mkdirs()
        out.writeText(
            buildString {
                appendLine(
                    "id,description,full_verdict,full_reached_vehicle," +
                        "no_guard_verdict,no_guard_reached_vehicle,delta",
                )
                rows.forEach { row ->
                    val delta = when {
                        !row.full.reachedVehicle && row.noGuard.reachedVehicle -> "UNSAFE_WRITE_LANDS"
                        row.full.reachedVehicle == row.noGuard.reachedVehicle -> "same"
                        else -> "changed"
                    }
                    appendLine(
                        listOf(
                            row.case.id,
                            "\"${row.case.description}\"",
                            row.full.verdict,
                            row.full.reachedVehicle,
                            row.noGuard.verdict,
                            row.noGuard.reachedVehicle,
                            delta,
                        ).joinToString(","),
                    )
                }
            },
        )
        println("A1 ablation CSV -> ${out.absolutePath}")
    }

    private fun printTable(rows: List<Row>) {
        println("== N4 A1: SafetyGuard ON vs OFF (property la MockVehicleRepository) ==")
        rows.forEach { row ->
            println("  ${row.case.id}  ${row.case.description}")
            println(
                "      full    : ${row.full.verdict.padEnd(24)} ghi xuong xe = ${row.full.reachedVehicle}",
            )
            println(
                "      no_guard: ${row.noGuard.verdict.padEnd(24)} ghi xuong xe = ${row.noGuard.reachedVehicle}",
            )
        }
        val landed = rows.count { !it.full.reachedVehicle && it.noGuard.reachedVehicle }
        println("  → $landed/${rows.size} lenh bi guard chan lai ghi duoc xuong xe khi bo guard")
    }

    private companion object {
        const val MPS_TO_KMH = 3.6f
    }
}
