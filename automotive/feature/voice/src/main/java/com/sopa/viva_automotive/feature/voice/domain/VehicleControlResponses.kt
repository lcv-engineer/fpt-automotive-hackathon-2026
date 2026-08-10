package com.sopa.viva_automotive.feature.voice.domain

import java.math.BigDecimal

/**
 * Mọi câu trợ lý nói ra, ở một chỗ.
 *
 * Trước 05/08, chỉ nhiệt độ/quạt/cửa là tiếng Việt; AC, máy lạnh và toàn bộ câu
 * trả lời trạng thái nằm rải rác trong `ExecuteVehicleControlUseCase` dưới dạng
 * chuỗi **tiếng Anh** cứng. Một trợ lý tiếng Việt trả lời *"Current speed is 60
 * kilometers per hour"* thì hỏng ở hai tầng: người lái nghe không hiểu, và
 * `PrerenderedPrompts.rawNameFor()` tra cứu khớp chính xác nên câu tiếng Anh
 * không trúng clip nào — trên image không có giọng vi-VN nó tụt xuống còn một
 * tiếng ping.
 */
object VehicleControlResponses {

    fun temperatureTarget(celsius: Float): String =
        "Đã đặt nhiệt độ mục tiêu ${formatNumber(celsius)}°C."

    fun fanSpeed(level: Int): String = "Đã đặt quạt mức $level."

    fun driverDoor(locked: Boolean): String =
        if (locked) "Đã khóa cửa tài xế." else "Đã mở khóa cửa tài xế."

    fun cabinLights(on: Boolean): String =
        if (on) "Đã bật đèn cabin." else "Đã tắt đèn cabin."

    fun airConditioning(on: Boolean): String =
        if (on) "Đã bật điều hòa." else "Đã tắt điều hòa."

    fun climatePower(on: Boolean): String =
        if (on) "Đã bật hệ thống khí hậu." else "Đã tắt hệ thống khí hậu."

    /** [speedMetersPerSecond] là giá trị thô của `PERF_VEHICLE_SPEED`. */
    fun currentSpeed(speedMetersPerSecond: Float): String =
        "Xe đang chạy ${Math.round(speedMetersPerSecond * 3.6f)} ki lô mét một giờ."

    fun fuelLevel(percent: Float): String = "Xăng còn ${Math.round(percent)} phần trăm."

    fun batteryLevel(percent: Float): String = "Pin còn ${Math.round(percent)} phần trăm."

    /** [formatted] đã gồm đơn vị, do `TemperatureUnits.format` sinh ra. */
    fun temperatureSetting(formatted: String): String = "Nhiệt độ đang đặt ở $formatted."

    fun temperatureOutOfRange(min: Int, max: Int): String =
        "Nhiệt độ hỗ trợ từ $min đến $max độ C. Bạn muốn đặt bao nhiêu độ?"

    fun fanSpeedOutOfRange(min: Int, max: Int): String =
        "Bạn muốn đặt quạt ở mức mấy, từ $min đến $max?"

    private fun formatNumber(value: Float): String =
        BigDecimal.valueOf(value.toDouble()).stripTrailingZeros().toPlainString().replace('.', ',')
}
