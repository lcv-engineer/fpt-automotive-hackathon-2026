package com.sopa.viva_automotive.feature.voice.domain

import com.sopa.viva_automotive.feature.voice.data.TranscriptionEvent
import com.sopa.viva_automotive.feature.voice.domain.model.VehicleIntent
import com.sopa.viva_automotive.feature.voice.domain.media.MediaTransportException
import com.sopa.viva_automotive.vehicleservice.api.SafetyConfirmationRequiredException
import com.sopa.viva_automotive.vehicleservice.api.SafetyDeniedException
import com.viva.voice.trace.Stage
import com.viva.voice.trace.TraceVerdict

object VoiceTurnReport {

    const val DID_NOT_HEAR = "Mình chưa nghe rõ. Bạn thử lại giúp mình nhé."
    const val OUT_OF_SCOPE =
        "Mình chưa hiểu lệnh đó. Mình hỗ trợ điều hòa, cửa, đèn cabin, âm lượng, nhạc và giao hàng."
    const val OUT_OF_SCOPE_HINT =
        "Bạn thử: \"đặt điều hòa 22 độ\", \"bật đèn\", \"phát nhạc\", \"thích bài này\"."
    const val COMMAND_FAILED = "Mình chưa thực hiện được yêu cầu. Bạn thử lại giúp mình nhé."
    const val ASR_UNAVAILABLE = "Bộ nhận dạng giọng nói chưa sẵn sàng."
    const val MICROPHONE_UNAVAILABLE = "Mình chưa mở được micro. Bạn kiểm tra quyền ghi âm giúp mình nhé."

    /**
     * Dưới mức này thì một transcript **đã được đo** là không đủ chắc để thành lệnh
     * xe; lượt đó hỏi lại thay vì thực thi (28-PIPELINE §5, hàng "ASR confidence thấp").
     *
     * Chỉ áp dụng khi engine thật sự trả về một con số. `null` **không** rơi vào
     * luật này — xem [needsRepeatForConfidence].
     */
    const val MIN_ACOUSTIC_CONFIDENCE = 0.6f

    /**
     * Câu tiếng Việt cho HMI/TTS ứng với mã lỗi của tầng ASR.
     *
     * Mã lỗi là thứ máy đọc; `diagnostic` là thứ để đọc log. Không cái nào được nói
     * ra cho tài xế — bản trước đọc thẳng "Microphone is unavailable" lên màn hình
     * xe tiếng Việt.
     */
    fun speechErrorSpeech(code: String): String = when (code) {
        TranscriptionEvent.CODE_MODEL_UNAVAILABLE -> ASR_UNAVAILABLE
        else -> DID_NOT_HEAR
    }

    /**
     * `true` khi ASR **đã đo** confidence và con số đó quá thấp.
     *
     * `null` trả về `false`: thiếu confidence là một trạng thái quan sát được, không
     * phải bằng chứng nghe kém.
     */
    fun needsRepeatForConfidence(acousticConfidence: Float?): Boolean =
        acousticConfidence != null && acousticConfidence < MIN_ACOUSTIC_CONFIDENCE

    fun intentName(intent: VehicleIntent): String = when (intent) {
        is VehicleIntent.SetTemperature, is VehicleIntent.AdjustTemperature -> "hvac_set_temp"
        is VehicleIntent.SetFanSpeed, is VehicleIntent.AdjustFanSpeed -> "hvac_set_fan"
        is VehicleIntent.SetAc -> "hvac_ac"
        is VehicleIntent.SetHvacPower -> "hvac_power"
        is VehicleIntent.SetDoorLock -> "door_lock"
        is VehicleIntent.SetCabinLights -> "cabin_lights"
        is VehicleIntent.QueryStatus -> "vehicle_status_" + intent.kind.name.lowercase()
        is VehicleIntent.VolumeAdjust -> "volume_adjust"
        is VehicleIntent.Media -> intent.command.intentName
        is VehicleIntent.RadioTune -> "radio_tune"
        VehicleIntent.RadioNextStation -> "radio_next"
        is VehicleIntent.Delivery -> intent.command.intentName
        is VehicleIntent.NotWired -> intent.intentName
        is VehicleIntent.Clarification -> "clarify"
        is VehicleIntent.Unknown -> "unknown"
    }

    fun verdictFor(intent: VehicleIntent, error: Throwable?): TraceVerdict = when {
        error is SafetyDeniedException -> TraceVerdict.Deny(error.rule)
        error is SafetyConfirmationRequiredException -> TraceVerdict.Confirm(error.rule)
        error is ConfirmationRequiredException -> TraceVerdict.Confirm(error.rule)
        error is CommandNotWiredException -> TraceVerdict.Error(Stage.EXEC_DONE)
        intent is VehicleIntent.Clarification -> TraceVerdict.Confirm("CLARIFY_SLOT")
        intent is VehicleIntent.Unknown -> TraceVerdict.Error(Stage.NLU_DONE)
        error != null -> TraceVerdict.Error(Stage.EXEC_DONE)
        else -> TraceVerdict.Allow
    }

    fun failureSpeech(intent: VehicleIntent, error: Throwable?): String = when {
        error is SafetyDeniedException -> error.reasonVi
        error is SafetyConfirmationRequiredException -> error.questionVi
        error is ConfirmationRequiredException -> error.questionVi
        intent is VehicleIntent.Clarification -> intent.promptVi
        intent is VehicleIntent.Unknown -> OUT_OF_SCOPE
        error is CommandNotWiredException -> error.message ?: COMMAND_FAILED
        error is MediaTransportException -> error.message ?: COMMAND_FAILED
        else -> COMMAND_FAILED
    }
}
