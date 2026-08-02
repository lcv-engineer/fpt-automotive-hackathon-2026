package com.sopa.viva_automotive.feature.voice.domain

import com.sopa.viva_automotive.feature.voice.domain.delivery.DeliveryCommand
import com.sopa.viva_automotive.feature.voice.domain.model.VehicleIntent
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceTurnReportTest {

    @Test
    fun `intent names use the contract vocabulary the harness groups by`() {
        assertEquals("hvac_set_temp", VoiceTurnReport.intentName(VehicleIntent.SetTemperature(22.0)))
        assertEquals("hvac_set_fan", VoiceTurnReport.intentName(VehicleIntent.SetFanSpeed(3)))
        assertEquals("door_lock", VoiceTurnReport.intentName(VehicleIntent.SetDoorLock(true)))
        assertEquals("volume_adjust", VoiceTurnReport.intentName(VehicleIntent.VolumeAdjust(1)))
        assertEquals("media_next", VoiceTurnReport.intentName(VehicleIntent.MediaNext))
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
                VehicleIntent.MediaNext,
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
            VoiceTurnReport.DID_NOT_HEAR,
            VoiceTurnReport.failureSpeech(
                VehicleIntent.Unknown("xyz"),
                CommandValidationException("Sorry, I didn't understand"),
            ),
        )
        assertEquals(
            "chưa nối trình phát nhạc",
            VoiceTurnReport.failureSpeech(
                VehicleIntent.MediaNext,
                CommandNotWiredException("chưa nối trình phát nhạc"),
            ),
        )
    }
}
