package com.sopa.viva_automotive.feature.voice.domain

import com.sopa.viva_automotive.feature.voice.FakeCommandMappingDao
import com.sopa.viva_automotive.feature.voice.FakeSemanticIntentMatcher
import com.sopa.viva_automotive.feature.voice.data.CommandMappingRepository
import com.sopa.viva_automotive.feature.voice.domain.delivery.DeliveryCommand
import com.sopa.viva_automotive.feature.voice.domain.media.MediaCommand
import com.sopa.viva_automotive.feature.voice.domain.model.VehicleIntent
import com.sopa.viva_automotive.feature.voice.domain.model.VehicleIntentTypes
import com.sopa.viva_automotive.vehicleservice.api.VehicleZone
import com.viva.voice.intent.GrammarIntentRouter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProcessVoiceCommandUseCaseTest {

    private lateinit var useCase: ProcessVoiceCommandUseCase

    @Before
    fun setUp() = runTest {
        val repository = CommandMappingRepository(FakeCommandMappingDao())
        repository.seedIfEmpty()
        useCase = ProcessVoiceCommandUseCase(
            repository,
            FakeSemanticIntentMatcher(),
            GrammarIntentRouter(),
        )
    }

    @Test
    fun `set temperature with spoken number`() = runTest {
        val intent = useCase("set the temperature to twenty two degrees")
        assertEquals(VehicleIntent.SetTemperature(22.0, VehicleZone.DRIVER), intent)
    }

    @Test
    fun `set temperature for passenger zone`() = runTest {
        val intent = useCase("set passenger temperature to 19")
        assertEquals(VehicleIntent.SetTemperature(19.0, VehicleZone.PASSENGER), intent)
    }

    @Test
    fun `warmer maps to positive temperature adjustment`() = runTest {
        val intent = useCase("make it warmer")
        assertEquals(VehicleIntent.AdjustTemperature(1.0, VehicleZone.DRIVER), intent)
    }

    @Test
    fun `too hot maps to negative temperature adjustment`() = runTest {
        val intent = useCase("it is too hot in here")
        assertEquals(VehicleIntent.AdjustTemperature(-1.0, VehicleZone.DRIVER), intent)
    }

    @Test
    fun `ac on and off`() = runTest {
        assertEquals(VehicleIntent.SetAc(true), useCase("turn on the ac"))
        assertEquals(VehicleIntent.SetAc(false), useCase("turn off the ac"))
    }

    @Test
    fun `stt spaced a c normalizes to ac off`() = runTest {
        assertEquals(VehicleIntent.SetAc(false), useCase("turn off the a c"))
    }

    @Test
    fun `embedding fallback maps aircon paraphrase`() = runTest {
        val repository = CommandMappingRepository(FakeCommandMappingDao())
        repository.seedIfEmpty()
        val semantic = FakeSemanticIntentMatcher(
            mapOf("turn off aircon" to VehicleIntentTypes.AC_OFF),
        )
        val nlu = ProcessVoiceCommandUseCase(repository, semantic, GrammarIntentRouter())
        assertEquals(VehicleIntent.SetAc(false), nlu("turn off aircon"))
    }

    @Test
    fun `fan speed with spoken number`() = runTest {
        assertEquals(VehicleIntent.SetFanSpeed(5), useCase("set fan speed to five"))
    }

    @Test
    fun `unlock wins over lock for unlock utterances`() = runTest {
        assertEquals(VehicleIntent.SetDoorLock(true), useCase("lock the doors"))
        assertEquals(VehicleIntent.SetDoorLock(false), useCase("unlock the doors please"))
    }

    @Test
    fun `status queries`() = runTest {
        assertEquals(
            VehicleIntent.QueryStatus(VehicleIntent.StatusQueryKind.SPEED),
            useCase("how fast am i going"),
        )
        assertEquals(
            VehicleIntent.QueryStatus(VehicleIntent.StatusQueryKind.FUEL),
            useCase("how much fuel is left"),
        )
    }

    @Test
    fun `removed vietnamese ac power intent is rejected before legacy fallback`() = runTest {
        assertEquals(
            VehicleIntent.Clarification(
                "Lệnh này chưa hỗ trợ trong bản demo. Bạn thử một lệnh điều hòa, cửa, âm thanh hoặc giao hàng nhé.",
            ),
            useCase("bật điều hòa"),
        )
    }

    @Test
    fun `grammar handles wake phrase temperature before embedding fallback`() = runTest {
        assertEquals(
            VehicleIntent.SetTemperature(24.0, VehicleZone.DRIVER),
            useCase("Viva ơi hạ điều hòa xuống 24 độ"),
        )
    }

    @Test
    fun `ambiguous cold complaint returns a clarification instead of executing`() = runTest {
        assertEquals(
            VehicleIntent.Clarification("Bạn muốn tăng nhiệt độ điều hòa lên bao nhiêu độ?"),
            useCase("lạnh quá"),
        )
    }

    @Test
    fun `another assistant wake phrase cannot fall through to vehicle execution`() = runTest {
        assertEquals(
            VehicleIntent.Clarification(
                "Từ gọi của trợ lý là “Vi-Vi ơi” (cũng nhận Vivi/Viva ơi). Bạn thử lại nhé.",
            ),
            useCase("Siri ơi hạ điều hòa xuống 24 độ"),
        )
    }

    @Test
    fun `play music maps to media play`() = runTest {
        assertEquals(VehicleIntent.Media(MediaCommand.PLAY), useCase("play some jazz music"))
    }

    @Test
    fun `next song and pause music`() = runTest {
        assertEquals(VehicleIntent.Media(MediaCommand.NEXT), useCase("next song"))
        assertEquals(VehicleIntent.Media(MediaCommand.PAUSE), useCase("pause the music"))
    }

    @Test
    fun `radio tune and next station`() = runTest {
        assertTrue(useCase("play radio") is VehicleIntent.RadioTune)
        assertEquals(VehicleIntent.RadioNextStation, useCase("next station"))
        assertTrue(useCase("bật đài") is VehicleIntent.RadioTune)
    }

    @Test
    fun `unrecognized utterance maps to unknown`() = runTest {
        val intent = useCase("order sushi for dinner")
        assertTrue(intent is VehicleIntent.Unknown)
    }

    @Test
    fun `all three media commands survive the module boundary`() = runTest {
        assertEquals(VehicleIntent.Media(MediaCommand.PLAY), useCase("phát nhạc"))
        assertEquals(VehicleIntent.Media(MediaCommand.PAUSE), useCase("dừng nhạc"))
        assertEquals(VehicleIntent.Media(MediaCommand.NEXT), useCase("chuyển bài"))
    }

    @Test
    fun `volume commands keep their direction`() = runTest {
        assertEquals(VehicleIntent.VolumeAdjust(1), useCase("tăng âm lượng"))
        assertEquals(VehicleIntent.VolumeAdjust(-1), useCase("giảm âm lượng"))
    }

    @Test
    fun `delivery commands now reach the skill instead of being labelled not-wired`() = runTest {
        assertEquals(
            VehicleIntent.Delivery(DeliveryCommand.NextStop),
            useCase("chặng tiếp theo là gì"),
        )
        assertEquals(
            VehicleIntent.Delivery(DeliveryCommand.OrderStatus("A12")),
            useCase("đơn A12 thế nào"),
        )
        assertEquals(
            VehicleIntent.Delivery(DeliveryCommand.Confirm("A12")),
            useCase("xác nhận giao thành công đơn A12"),
        )
    }

    @Test
    fun `set temperature without number maps to unknown`() = runTest {
        val intent = useCase("set the temperature")
        assertTrue(intent is VehicleIntent.Unknown)
    }
}
