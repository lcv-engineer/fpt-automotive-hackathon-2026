package com.sopa.viva_automotive.feature.voice.domain

import com.sopa.viva_automotive.feature.voice.data.TranscriptionEvent
import com.sopa.viva_automotive.feature.voice.domain.delivery.DeliveryCommand
import com.sopa.viva_automotive.feature.voice.domain.media.MediaCommand
import com.sopa.viva_automotive.feature.voice.domain.media.MediaTransportException
import com.sopa.viva_automotive.feature.voice.domain.model.VehicleIntent
import com.sopa.viva_automotive.vehicleservice.api.SafetyConfirmationRequiredException
import com.sopa.viva_automotive.vehicleservice.api.SafetyDeniedException
import com.sopa.viva_automotive.vehicleservice.api.SafetyRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceTurnReportTest {

    @Test
    fun `an engine that reports no confidence does not trigger the repeat rule`() {
        // Missing confidence must not force clarify. If null counted as "low"
        // mọi câu và không bao giờ chạy được một lệnh nào.
        assertFalse(VoiceTurnReport.needsRepeatForConfidence(null))
    }

    @Test
    fun `a measured low confidence triggers the repeat rule`() {
        assertTrue(VoiceTurnReport.needsRepeatForConfidence(0.4f))
        assertFalse(VoiceTurnReport.needsRepeatForConfidence(0.9f))
    }

    @Test
    fun `khong dat co thi giu nguyen nguong mac dinh`() {
        // -1 la gia tri Settings.Global tra ve khi khoa chua ton tai.
        assertEquals(VoiceTurnReport.MIN_ACOUSTIC_CONFIDENCE, VoiceTurnReport.minAcousticConfidence(-1))
        assertEquals(VoiceTurnReport.MIN_ACOUSTIC_CONFIDENCE, VoiceTurnReport.minAcousticConfidence(null))
    }

    @Test
    fun `co hop le doi duoc nguong luc chay`() {
        assertEquals(0.40f, VoiceTurnReport.minAcousticConfidence(40))
        assertEquals(0.0f, VoiceTurnReport.minAcousticConfidence(0))
        assertEquals(1.0f, VoiceTurnReport.minAcousticConfidence(100))
    }

    @Test
    fun `gia tri rac KHONG duoc am tham noi long cong an toan`() {
        // Ngoai dai 0..100 deu ve mac dinh. Mot cai go nham `settings put global
        // viva_min_conf 4000` khong duoc phep bien thanh "cho qua tat ca", va mot
        // so am khong duoc phep bien thanh "chan tat ca" mot cach kho hieu.
        assertEquals(VoiceTurnReport.MIN_ACOUSTIC_CONFIDENCE, VoiceTurnReport.minAcousticConfidence(4000))
        assertEquals(VoiceTurnReport.MIN_ACOUSTIC_CONFIDENCE, VoiceTurnReport.minAcousticConfidence(101))
        assertEquals(VoiceTurnReport.MIN_ACOUSTIC_CONFIDENCE, VoiceTurnReport.minAcousticConfidence(-100))
    }

    @Test
    fun `nguong tu co duoc ap dung khi phan xet`() {
        // Chinh ca da do 09/08: whisper-tiny phien am DUNG "phat nhac" nhung
        // confidence 0,41 < 0,6 nen bi chan. Ha nguong xuong 0,40 thi cho qua.
        assertTrue(VoiceTurnReport.needsRepeatForConfidence(0.41f, 0.6f))
        assertFalse(VoiceTurnReport.needsRepeatForConfidence(0.41f, 0.40f))
    }

    @Test
    fun `ASR error codes are spoken in Vietnamese, never as the raw diagnostic`() {
        // Bản trước đọc thẳng "Microphone is unavailable" lên màn hình xe tiếng Việt.
        assertEquals(
            VoiceTurnReport.ASR_UNAVAILABLE,
            VoiceTurnReport.speechErrorSpeech(TranscriptionEvent.CODE_MODEL_UNAVAILABLE),
        )
        assertEquals(
            VoiceTurnReport.DID_NOT_HEAR,
            VoiceTurnReport.speechErrorSpeech(TranscriptionEvent.CODE_NO_SPEECH),
        )
        assertEquals(
            VoiceTurnReport.DID_NOT_HEAR,
            VoiceTurnReport.speechErrorSpeech("mã lỗi chưa từng thấy"),
        )
    }

    @Test
    fun `a guard denial reports Deny with the rule id A1 joins on`() {
        // This is the assertion N4b's ablation stands on: if the wire says
        // Error:exec_done instead, `harness compare --verdicts-out` counts zero
        // Deny:G1_SPEED_LOCK in both arms and the table proves nothing.
        assertEquals(
            "Deny:G1_SPEED_LOCK",
            VoiceTurnReport.verdictFor(
                VehicleIntent.SetDoorLock(locked = false),
                SafetyDeniedException(
                    SafetyRules.SPEED_LOCK,
                    "Xe đang chạy, mình chưa mở cửa được.",
                    "Bạn dừng hẳn rồi nói lại nhé.",
                ),
            ).wire,
        )
    }

    @Test
    fun `an unreadable speed denies rather than falling through to a generic error`() {
        assertEquals(
            "Deny:G1_STALE_STATE",
            VoiceTurnReport.verdictFor(
                VehicleIntent.SetDoorLock(locked = false),
                SafetyDeniedException(
                    SafetyRules.STALE_STATE,
                    "Mình chưa đọc được tốc độ hiện tại nên chưa thể mở khoá cửa.",
                ),
            ).wire,
        )
    }

    @Test
    fun `a guard confirmation is Confirm with its own rule id`() {
        assertEquals(
            "Confirm:G2_CONFIRM_DOOR",
            VoiceTurnReport.verdictFor(
                VehicleIntent.SetDoorLock(locked = false),
                SafetyConfirmationRequiredException(
                    SafetyRules.CONFIRM_DOOR,
                    "Bạn có chắc muốn mở khoá cửa không?",
                ),
            ).wire,
        )
    }

    @Test
    fun `a denied turn speaks the reason verbatim so the pre-rendered clip still matches`() {
        // PrerenderedPrompts.rawNameFor() is an exact-text lookup and this
        // sentence is tts_deny_door_while_moving. Appending the suggestion would
        // miss the clip and leave a device with no vi-VN voice playing a bare
        // ping instead of the refusal.
        assertEquals(
            "Xe đang chạy, mình chưa mở cửa được.",
            VoiceTurnReport.failureSpeech(
                VehicleIntent.SetDoorLock(locked = false),
                SafetyDeniedException(
                    SafetyRules.SPEED_LOCK,
                    "Xe đang chạy, mình chưa mở cửa được.",
                    "Bạn dừng hẳn rồi nói lại nhé.",
                ),
            ),
        )
    }

    @Test
    fun `a denial without a suggestion speaks only the reason`() {
        assertEquals(
            "Mình chưa đọc được tốc độ hiện tại nên chưa thể mở khoá cửa.",
            VoiceTurnReport.failureSpeech(
                VehicleIntent.SetDoorLock(locked = false),
                SafetyDeniedException(
                    SafetyRules.STALE_STATE,
                    "Mình chưa đọc được tốc độ hiện tại nên chưa thể mở khoá cửa.",
                ),
            ),
        )
    }

    @Test
    fun `intent names use the contract vocabulary the harness groups by`() {
        assertEquals("hvac_set_temp", VoiceTurnReport.intentName(VehicleIntent.SetTemperature(22.0)))
        assertEquals("hvac_set_fan", VoiceTurnReport.intentName(VehicleIntent.SetFanSpeed(3)))
        assertEquals("door_lock", VoiceTurnReport.intentName(VehicleIntent.SetDoorLock(true)))
        assertEquals("volume_adjust", VoiceTurnReport.intentName(VehicleIntent.VolumeAdjust(1)))
        assertEquals(
            "media_play",
            VoiceTurnReport.intentName(VehicleIntent.Media(MediaCommand.PLAY)),
        )
        assertEquals(
            "media_pause",
            VoiceTurnReport.intentName(VehicleIntent.Media(MediaCommand.PAUSE)),
        )
        assertEquals(
            "media_next",
            VoiceTurnReport.intentName(VehicleIntent.Media(MediaCommand.NEXT)),
        )
    }

    @Test
    fun `a not-wired intent reports the grammar name, not a generic label`() {
        assertEquals(
            "delivery_next_stop",
            VoiceTurnReport.intentName(VehicleIntent.NotWired("delivery_next_stop")),
        )
    }

    @Test
    fun `delivery turns report their grammar intent name`() {
        assertEquals(
            "delivery_confirm",
            VoiceTurnReport.intentName(VehicleIntent.Delivery(DeliveryCommand.Confirm("A12"))),
        )
        assertEquals(
            "delivery_next_stop",
            VoiceTurnReport.intentName(VehicleIntent.Delivery(DeliveryCommand.NextStop)),
        )
    }

    @Test
    fun `asking for delivery confirmation is Confirm with the rule id, not an error`() {
        // N4/N5 group the benchmark by this rule id; filing it as Error would
        // make the safety rule look like a defect.
        assertEquals(
            "Confirm:G2_CONFIRM_DELIVERY",
            VoiceTurnReport.verdictFor(
                VehicleIntent.Delivery(DeliveryCommand.Confirm("A12")),
                ConfirmationRequiredException("G2_CONFIRM_DELIVERY", "Xác nhận đã giao đơn A 12?"),
            ).wire,
        )
    }

    @Test
    fun `the confirmation question itself is what the driver hears`() {
        assertEquals(
            "Xác nhận đã giao đơn A 12?",
            VoiceTurnReport.failureSpeech(
                VehicleIntent.Delivery(DeliveryCommand.Confirm("A12")),
                ConfirmationRequiredException("G2_CONFIRM_DELIVERY", "Xác nhận đã giao đơn A 12?"),
            ),
        )
    }

    @Test
    fun `an executed turn is Allow`() {
        assertEquals(
            "Allow",
            VoiceTurnReport.verdictFor(VehicleIntent.SetFanSpeed(3), error = null).wire,
        )
    }

    @Test
    fun `asking one clarifying question is not filed as a failed turn`() {
        assertEquals(
            "Confirm:CLARIFY_SLOT",
            VoiceTurnReport.verdictFor(VehicleIntent.Clarification("mức mấy?"), error = null).wire,
        )
    }

    @Test
    fun `a command with no adapter dies at exec, not at nlu`() {
        assertEquals(
            "Error:exec_done",
            VoiceTurnReport.verdictFor(
                VehicleIntent.Media(MediaCommand.NEXT),
                CommandNotWiredException("chưa nối"),
            ).wire,
        )
    }

    @Test
    fun `a genuinely misheard command dies at nlu`() {
        assertEquals(
            "Error:nlu_done",
            VoiceTurnReport.verdictFor(
                VehicleIntent.Unknown("xyz"),
                CommandValidationException("nope"),
            ).wire,
        )
    }

    @Test
    fun `spoken failure text stays Vietnamese and keeps the not-wired reason`() {
        assertEquals(
            "mức mấy?",
            VoiceTurnReport.failureSpeech(VehicleIntent.Clarification("mức mấy?"), null),
        )
        assertEquals(
            VoiceTurnReport.OUT_OF_SCOPE,
            VoiceTurnReport.failureSpeech(
                VehicleIntent.Unknown("xyz"),
                CommandValidationException("Sorry, I didn't understand"),
            ),
        )
        assertEquals(
            "chưa nối trình phát nhạc",
            VoiceTurnReport.failureSpeech(
                VehicleIntent.Media(MediaCommand.NEXT),
                CommandNotWiredException("chưa nối trình phát nhạc"),
            ),
        )
    }

    @Test
    fun `media connection failure speaks its specific Vietnamese reason`() {
        val reason = "Mình chưa kết nối được trình phát nhạc. Bạn thử lại giúp mình nhé."

        assertEquals(
            reason,
            VoiceTurnReport.failureSpeech(
                VehicleIntent.Media(MediaCommand.PLAY),
                MediaTransportException(reason),
            ),
        )
    }
}
