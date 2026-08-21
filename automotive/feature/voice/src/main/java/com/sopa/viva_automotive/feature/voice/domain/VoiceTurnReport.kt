package com.sopa.viva_automotive.feature.voice.domain

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

    /** Mã lỗi ASR máy đọc — dùng cho [speechErrorSpeech], không đọc thẳng cho tài xế. */
    const val CODE_ASR_MODEL_UNAVAILABLE = "asr_model_unavailable"

    /**
     * Dưới mức này thì một transcript **đã được đo** là không đủ chắc để thành lệnh
     * xe; lượt đó hỏi lại thay vì thực thi (28-PIPELINE §5, hàng "ASR confidence thấp").
     *
     * Chỉ áp dụng khi engine thật sự trả về một con số. Một số engine (vd. Google STT
     * utterance-mode) trả `null` và `null` **không** rơi vào luật này — xem
     * [needsRepeatForConfidence].
     *
     * ⚠️ **Con số này gắn với MỘT MODEL, không phải với hệ thống.** Nó được chọn từ
     * corpus giọng thật chạy trên PhoWhisper. `confidence` mà tầng ASR trả về là
     * `exp(avg_logprob)` — tức *phỏng đoán của chính model về chính nó* — nên model
     * nào được huấn luyện kém hơn cho tiếng Việt sẽ cho số thấp hơn **kể cả khi
     * phiên âm ra đúng**.
     *
     * Đo 09/08 trên emulator với `Systran/faster-whisper-tiny`: **8/8 lượt bị chặn**,
     * gồm một lượt phiên âm chính xác tuyệt đối (`"phát nhạc"`, đúng ca B13). Đổi
     * model mà giữ nguyên ngưỡng thì hệ thống từ chối sạch, và triệu chứng nhìn y
     * hệt mic hỏng hay NLU dốt.
     *
     * Vì vậy có [SETTING_MIN_CONFIDENCE] để dò ngưỡng trên máy thật mà không phải
     * build lại. Mặc định giữ nguyên `0.6f`, nên đường demo không đổi hành vi.
     */
    const val MIN_ACOUSTIC_CONFIDENCE = 0.6f

    /**
     * Khoá `Settings.Global` để **tạm** ghi đè [MIN_ACOUSTIC_CONFIDENCE] lúc chạy.
     *
     * ```
     * adb shell settings put global viva_min_conf 40   # = 0.40
     * adb shell settings delete global viva_min_conf   # về mặc định 0.6
     * ```
     *
     * Đơn vị là **phần trăm nguyên 0..100** chứ không phải float: `Settings.Global`
     * chỉ có getter cho `Int`/`String`, và một chuỗi float phải tự parse thì sai
     * locale là hỏng ("0,4" vs "0.4").
     *
     * Cùng khuôn mẫu với công tắc `viva_asr_grammar` — chính công tắc đó cho phép
     * đo A/B trên **cùng một giọng nói** và ra con số WER 0,841 → 0,566. Không có
     * nó thì mỗi lần đổi ngưỡng phải build lại 14 phút và giọng nói đã khác đi.
     */
    const val SETTING_MIN_CONFIDENCE = "viva_min_conf"

    /**
     * Diễn giải giá trị thô đọc từ [SETTING_MIN_CONFIDENCE] thành ngưỡng thật.
     *
     * Thuần khiết có chủ đích: `Settings.Global` cần `Context` mà object này phải
     * test được trên JVM, nên nơi gọi đọc số rồi truyền vào đây.
     *
     * Ngoài dải `0..100` — gồm cả `-1` nghĩa là "chưa đặt" — đều rơi về mặc định.
     * Giá trị rác **không** được phép nới lỏng cổng an toàn một cách âm thầm.
     */
    fun minAcousticConfidence(rawPercent: Int?): Float =
        if (rawPercent != null && rawPercent in 0..100) rawPercent / 100f
        else MIN_ACOUSTIC_CONFIDENCE

    /**
     * Câu tiếng Việt cho HMI/TTS ứng với mã lỗi của tầng ASR.
     *
     * Mã lỗi là thứ máy đọc; `diagnostic` là thứ để đọc log. Không cái nào được nói
     * ra cho tài xế — bản trước đọc thẳng "Microphone is unavailable" lên màn hình
     * xe tiếng Việt.
     */
    fun speechErrorSpeech(code: String): String = when (code) {
        CODE_ASR_MODEL_UNAVAILABLE -> ASR_UNAVAILABLE
        else -> DID_NOT_HEAR
    }

    /**
     * `true` khi ASR **đã đo** confidence và con số đó quá thấp.
     *
     * `null` trả về `false`: thiếu confidence là một trạng thái quan sát được, không
     * phải bằng chứng nghe kém.
     */
    fun needsRepeatForConfidence(acousticConfidence: Float?): Boolean =
        needsRepeatForConfidence(acousticConfidence, MIN_ACOUSTIC_CONFIDENCE)

    /**
     * Như trên nhưng nhận ngưỡng từ ngoài — dùng khi nơi gọi đã đọc
     * [SETTING_MIN_CONFIDENCE] qua [minAcousticConfidence].
     */
    fun needsRepeatForConfidence(acousticConfidence: Float?, minConfidence: Float): Boolean =
        acousticConfidence != null && acousticConfidence < minConfidence

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
