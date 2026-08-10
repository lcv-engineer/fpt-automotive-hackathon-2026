package com.sopa.viva_automotive.feature.voice.domain

import com.sopa.viva_automotive.feature.voice.data.TranscriptionEvent
import com.sopa.viva_automotive.feature.voice.domain.model.VehicleIntent
import com.sopa.viva_automotive.feature.voice.domain.media.MediaTransportException
import com.sopa.viva_automotive.vehicleservice.api.SafetyConfirmationRequiredException
import com.sopa.viva_automotive.vehicleservice.api.SafetyDeniedException
import com.viva.voice.trace.Stage
import com.viva.voice.trace.TraceVerdict

/**
 * How one voice turn is named and judged on the `VIVA_TRACE_SUMMARY` line.
 *
 * Split out of `VoiceAssistantService` so it can be unit tested on the JVM:
 * the service itself needs a device, and the two fields the harness groups by
 * (intent, verdict) are exactly the ones worth pinning with tests. See
 * vong2/03-contracts.md §1 for the wire format and §3 for the intent names.
 */
object VoiceTurnReport {

    /** Pre-rendered in `res/raw`; see `PrerenderedPrompts`. */
    const val DID_NOT_HEAR = "Mình chưa nghe rõ. Bạn thử lại giúp mình nhé."
    const val COMMAND_FAILED = "Mình chưa thực hiện được yêu cầu. Bạn thử lại giúp mình nhé."
    const val ASR_UNAVAILABLE = "Bộ nhận dạng giọng nói chưa sẵn sàng."
    const val MICROPHONE_UNAVAILABLE = "Mình chưa mở được micro. Bạn kiểm tra quyền ghi âm giúp mình nhé."

    /**
     * Dưới mức này thì một transcript **đã được đo** là không đủ chắc để thành lệnh
     * xe; lượt đó hỏi lại thay vì thực thi (28-PIPELINE §5, hàng "ASR confidence thấp").
     *
     * Chỉ áp dụng khi engine thật sự trả về một con số. Vosk small trả `null` và
     * `null` **không** rơi vào luật này — xem [needsRepeatForConfidence].
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
        TranscriptionEvent.CODE_MODEL_UNAVAILABLE -> ASR_UNAVAILABLE
        else -> DID_NOT_HEAR
    }

    /**
     * `true` khi ASR **đã đo** confidence và con số đó quá thấp.
     *
     * `null` trả về `false`: thiếu confidence là một trạng thái quan sát được, không
     * phải bằng chứng nghe kém. Nếu coi `null` là thấp thì bản offline dùng Vosk sẽ
     * hỏi lại mọi câu và không bao giờ chạy được lệnh nào.
     */
    fun needsRepeatForConfidence(acousticConfidence: Float?): Boolean =
        needsRepeatForConfidence(acousticConfidence, MIN_ACOUSTIC_CONFIDENCE)

    /**
     * Như trên nhưng nhận ngưỡng từ ngoài — dùng khi nơi gọi đã đọc
     * [SETTING_MIN_CONFIDENCE] qua [minAcousticConfidence].
     */
    fun needsRepeatForConfidence(acousticConfidence: Float?, minConfidence: Float): Boolean =
        acousticConfidence != null && acousticConfidence < minConfidence

    /**
     * Intent name for the summary line, using the §3 vocabulary where the app
     * path has an equivalent.
     *
     * Deliberately not `intent::class.simpleName`: the harness groups the
     * benchmark by this string, and "SetTemperature" would not join against the
     * grammar fixtures Vi already parses.
     */
    fun intentName(intent: VehicleIntent): String = when (intent) {
        is VehicleIntent.SetTemperature, is VehicleIntent.AdjustTemperature -> "hvac_set_temp"
        is VehicleIntent.SetFanSpeed, is VehicleIntent.AdjustFanSpeed -> "hvac_set_fan"
        is VehicleIntent.SetAc -> "hvac_ac"
        is VehicleIntent.SetHvacPower -> "hvac_power"
        is VehicleIntent.SetDoorLock -> "door_lock"
        is VehicleIntent.QueryStatus -> "vehicle_status_" + intent.kind.name.lowercase()
        is VehicleIntent.VolumeAdjust -> "volume_adjust"
        is VehicleIntent.Media -> intent.command.intentName
        is VehicleIntent.Delivery -> intent.command.intentName
        is VehicleIntent.NotWired -> intent.intentName
        is VehicleIntent.Clarification -> "clarify"
        is VehicleIntent.Unknown -> "unknown"
    }

    /**
     * Verdict for the summary line.
     *
     * `SafetyGuard` is enforced at the `VehicleRepository` boundary since
     * `GuardedVehicleRepository` landed, so a blocked turn arrives here as a
     * [SafetyDeniedException] or [SafetyConfirmationRequiredException] carrying
     * its rule id. Both must be matched **before** the generic `error != null`
     * branch: falling through would file a speed-locked door unlock as
     * `Error:exec_done`, and N4b's A1 table would count zero
     * `Deny:G1_SPEED_LOCK` in both the `full` and `no_guard` runs — a table
     * that cannot show the guard doing anything.
     *
     * `Confirm:CLARIFY_SLOT` is a stretch of the `Confirm` kind, which
     * TraceVerdict documents as a SafetyGuard outcome. It is used anyway
     * because the alternative — `Error:nlu_done` — would file M7-04 ("quạt
     * mạnh lên" → asks which level) as a failed turn, when asking exactly one
     * question is the behaviour that scenario is meant to prove. The verdict
     * grammar is frozen for Vi's parser, so inventing a fifth kind is not an
     * option.
     */
    fun verdictFor(intent: VehicleIntent, error: Throwable?): TraceVerdict = when {
        // The guard refused before any setter ran. This is the rule id the
        // ablation joins on, so it rides the wire, not a bare "Deny".
        error is SafetyDeniedException -> TraceVerdict.Deny(error.rule)
        error is SafetyConfirmationRequiredException -> TraceVerdict.Confirm(error.rule)

        // A turn that asked "are you sure?" did exactly what §4 requires of it.
        // Filing it as an error would make G2_CONFIRM_DELIVERY look like a
        // defect in the benchmark instead of the safety rule it is.
        error is ConfirmationRequiredException -> TraceVerdict.Confirm(error.rule)
        error is CommandNotWiredException -> TraceVerdict.Error(Stage.EXEC_DONE)
        intent is VehicleIntent.Clarification -> TraceVerdict.Confirm("CLARIFY_SLOT")
        intent is VehicleIntent.Unknown -> TraceVerdict.Error(Stage.NLU_DONE)
        error != null -> TraceVerdict.Error(Stage.EXEC_DONE)
        else -> TraceVerdict.Allow
    }

    /** What the assistant says out loud when a turn does not end in success. */
    fun failureSpeech(intent: VehicleIntent, error: Throwable?): String = when {
        // `reasonVi` alone, deliberately: PrerenderedPrompts.rawNameFor() is an
        // exact-text lookup, and "Xe đang chạy, mình chưa mở cửa được." is one
        // of the bundled clips (tts_deny_door_while_moving). Appending
        // `suggestion` would miss that entry and, on an AAOS image with no vi-VN
        // voice, degrade the refusal to a bare notification ping. The suggestion
        // still reaches the driver as HMI text via the exception message.
        error is SafetyDeniedException -> error.reasonVi
        error is SafetyConfirmationRequiredException -> error.questionVi

        // The question itself is the answer the driver needs to hear.
        error is ConfirmationRequiredException -> error.questionVi
        intent is VehicleIntent.Clarification -> intent.promptVi
        intent is VehicleIntent.Unknown -> DID_NOT_HEAR
        error is CommandNotWiredException -> error.message ?: COMMAND_FAILED
        error is MediaTransportException -> error.message ?: COMMAND_FAILED
        else -> COMMAND_FAILED
    }
}
