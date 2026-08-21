package com.viva.voice.intent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrammarIntentRouterTest {

    private val router = GrammarIntentRouter()

    @Test
    fun `wake phrase plus explicit temperature becomes hvac command`() {
        val result = router.route("Viva ơi, hạ nhiệt độ điều hòa xuống 24 độ C")

        assertTrue(result is RouteResult.Matched)
        val intent = (result as RouteResult.Matched).intent
        assertEquals("hvac_set_temp", intent.name)
        assertEquals(24f, intent.slots["value"])
        assertEquals(Intent.Tier.T0, intent.tier)
    }

    @Test
    fun `vivi wake phrase is accepted as product alias`() {
        val result = router.route("Vivi ơi quạt mức 2") as RouteResult.Matched

        assertEquals("hvac_set_fan", result.intent.name)
        assertEquals(2, result.intent.slots["level"])
    }

    @Test
    fun `canonical vi-vi wake phrase is stripped before routing`() {
        val result = router.route("Vi-Vi ơi khóa cửa") as RouteResult.Matched

        assertEquals("door_lock", result.intent.name)
    }

    @Test
    fun `vi vi spaced wake phrase is accepted`() {
        val result = router.route("Vi Vi ơi tăng âm lượng") as RouteResult.Matched

        assertEquals("volume_adjust", result.intent.name)
    }

    @Test
    fun `cold complaint asks to raise temperature instead of doing the opposite`() {
        val result = router.route("lạnh quá")

        assertTrue(result is RouteResult.NeedsClarification)
        assertEquals(
            "Bạn muốn tăng nhiệt độ điều hòa lên bao nhiêu độ?",
            (result as RouteResult.NeedsClarification).promptVi,
        )
    }

    @Test
    fun `relative temperature command without target asks for a value`() {
        val result = router.route("giảm nhiệt độ điều hòa")

        assertTrue(result is RouteResult.NeedsClarification)
    }

    @Test
    fun `temperature outside cabin range is rejected before execution`() {
        val result = router.route("đặt điều hòa xuống 8 độ")

        assertTrue(result is RouteResult.NeedsClarification)
        assertEquals(
            "Nhiệt độ hỗ trợ từ 16 đến 32 độ C. Bạn muốn đặt bao nhiêu độ?",
            (result as RouteResult.NeedsClarification).promptVi,
        )
    }

    @Test
    fun `real DBC upper bounds are accepted`() {
        val temperature = router.route("đặt điều hòa 32 độ") as RouteResult.Matched
        val fan = router.route("quạt mức 5") as RouteResult.Matched

        assertEquals(32f, temperature.intent.slots["value"])
        assertEquals(5, fan.intent.slots["level"])
    }

    @Test
    fun `all ten core intents are recognized`() {
        val cases = mapOf(
            "hạ điều hòa xuống 24 độ" to "hvac_set_temp",
            "quạt mức 3" to "hvac_set_fan",
            "khóa cửa" to "door_lock",
            "tăng âm lượng" to "volume_adjust",
            "phát playlist đi làm" to "media_play",
            "dừng nhạc" to "media_pause",
            "chuyển bài" to "media_next",
            "chặng tiếp theo là gì" to "delivery_next_stop",
            "đơn A12 thế nào" to "delivery_order_status",
            "xác nhận giao thành công" to "delivery_confirm",
        )

        cases.forEach { (text, expectedIntent) ->
            val result = router.route(text) as RouteResult.Matched
            assertEquals(expectedIntent, result.intent.name)
        }
    }

    @Test
    fun `compound commands keep every independently matched action in spoken order`() {
        val result = router.route("Vivi ơi bật đèn cabin rồi chuyển bài")

        assertTrue(result is RouteResult.MatchedMany)
        assertEquals(
            listOf("cabin_lights", "media_next"),
            (result as RouteResult.MatchedMany).intents.map(Intent::name),
        )
    }

    @Test
    fun `an unresolved compound clause cannot silently execute only the first clause`() {
        val result = router.route("bật đèn cabin và kể chuyện cười")

        assertTrue(result is RouteResult.Unsupported)
        assertEquals(true, (result as RouteResult.Unsupported).canFallback)
    }

    @Test
    fun `a conjunction inside a media title remains one media query`() {
        val result = router.route("phát bài Em và Trịnh") as RouteResult.Matched

        assertEquals("media_play", result.intent.name)
        assertEquals("em va trinh", result.intent.slots["query"])
    }

    @Test
    fun `compound commands are bounded before they reach execution`() {
        val result = router.route(
            "bật đèn rồi chuyển bài rồi tăng âm lượng rồi quạt mức 2",
        )

        assertTrue(result is RouteResult.Unsupported)
    }

    @Test
    fun `media play keeps an optional query without normalizing numbers inside it`() {
        val result = router.route("phát playlist một ngày mới") as RouteResult.Matched

        assertEquals("media_play", result.intent.name)
        // Folded text; number words are not rewritten in media query slots.
        assertEquals("mot ngay moi", result.intent.slots["query"])
    }

    @Test
    fun `cabin lights and favorite are recognized`() {
        val lightsOn = router.route("bật đèn cabin") as RouteResult.Matched
        val lightsOff = router.route("tắt đèn") as RouteResult.Matched
        val favorite = router.route("thích bài này") as RouteResult.Matched

        assertEquals("cabin_lights", lightsOn.intent.name)
        assertEquals(true, lightsOn.intent.slots["on"])
        assertEquals("cabin_lights", lightsOff.intent.name)
        assertEquals(false, lightsOff.intent.slots["on"])
        assertEquals("media_favorite", favorite.intent.name)
    }

    @Test
    fun `delivery commands keep a canonical order id when spoken`() {
        val status = router.route("đơn a12 thế nào") as RouteResult.Matched
        val confirmation = router.route("xác nhận giao đơn b07 thành công") as RouteResult.Matched

        assertEquals("A12", status.intent.slots["orderId"])
        assertEquals("B07", confirmation.intent.slots["orderId"])
    }

    @Test
    fun `delivery commands parse spoken spelled-out numbers`() {
        val status = router.route("đơn a một hai ba thế nào") as RouteResult.Matched

        assertEquals("A123", status.intent.slots["orderId"])
    }

    @Test
    fun `fan boundary checks`() {
        assertTrue(router.route("quạt mức 6") is RouteResult.NeedsClarification)
        assertEquals(0, (router.route("quạt mức 0") as RouteResult.Matched).intent.slots["level"])
        assertEquals(5, (router.route("quạt mức 5") as RouteResult.Matched).intent.slots["level"])
    }

    @Test
    fun `temperature boundary checks`() {
        assertTrue(router.route("đặt điều hòa 15 độ") is RouteResult.NeedsClarification)
        assertTrue(router.route("đặt điều hòa 33 độ") is RouteResult.NeedsClarification)
        assertEquals(16f, (router.route("đặt điều hòa 16 độ") as RouteResult.Matched).intent.slots["value"])
        assertEquals(32f, (router.route("đặt điều hòa 32 độ") as RouteResult.Matched).intent.slots["value"])
    }

    @Test
    fun `vietnamese numbers are parsed gracefully for multiple variations`() {
        val cases = mapOf(
            "hạ điều hòa xuống hai mốt độ" to 21f,
            "hạ điều hòa xuống hai tư độ" to 24f,
            "hạ điều hòa xuống hai lăm độ" to 25f,
            "hạ điều hòa xuống hai sáu độ" to 26f,
        )

        cases.forEach { (text, expectedValue) ->
            val result = router.route(text) as RouteResult.Matched
            assertEquals(expectedValue, result.intent.slots["value"])
        }
    }

    @Test
    fun `unsupported wake phrase is not treated as part of the product command`() {
        val result = router.route("Siri ơi, hạ điều hòa xuống 24 độ")

        assertTrue(result is RouteResult.Unsupported)
        assertEquals(false, (result as RouteResult.Unsupported).canFallback)
    }

    @Test
    fun `removed intent variants are rejected without falling through`() {
        val removedCommands = listOf(
            "bật điều hòa",
            "bật ac",
            "đặt âm lượng 50",
            "quay lại bài trước",
            "xe có lỗi gì",
        )

        removedCommands.forEach { command ->
            val result = router.route(command) as RouteResult.Unsupported
            assertEquals("Unexpected fallback for '$command'", false, result.canFallback)
            assertTrue(result.promptVi.contains("chưa có trong bản demo"))
        }
    }

    @Test
    fun `an app can add an intent rule without changing the core router`() {
        val extendedRouter = GrammarIntentRouter(
            extensionRules = listOf(
                GrammarRule { command ->
                    // Folded command; number words are not rewritten before extensions.
                    if (command == "mo cop so mot") {
                        RouteResult.Matched(
                            Intent(
                                name = "trunk_open",
                                confidence = 1f,
                                tier = Intent.Tier.T0,
                            ),
                        )
                    } else {
                        null
                    }
                },
            ),
        )

        val result = extendedRouter.route("Viva ơi, mở cốp số một") as RouteResult.Matched

        assertEquals("trunk_open", result.intent.name)
    }

    @Test
    fun `extension rules cannot restore a removed core intent`() {
        val extendedRouter = GrammarIntentRouter(
            extensionRules = listOf(
                GrammarRule { command ->
                    RouteResult.Matched(
                        Intent(
                            name = "unsafe_override",
                            slots = mapOf("raw" to command),
                            confidence = 1f,
                            tier = Intent.Tier.T0,
                        ),
                    )
                },
            ),
        )

        val result = extendedRouter.route("bật điều hòa") as RouteResult.Unsupported

        assertEquals(false, result.canFallback)
    }

    @Test
    fun `extension configuration is snapshotted when the router is created`() {
        val rules = mutableListOf<GrammarRule>()
        val extendedRouter = GrammarIntentRouter(extensionRules = rules)
        rules += GrammarRule { command ->
            RouteResult.Matched(
                Intent(
                    name = "late_mutation",
                    slots = mapOf("raw" to command),
                    confidence = 1f,
                    tier = Intent.Tier.T0,
                ),
            )
        }

        val result = extendedRouter.route("mở cốp")

        assertTrue(result is RouteResult.Unsupported)
    }

    @Test
    fun `negation is owned by NegationGate not the folded router`() {
        // Router folds "đừng"/"dừng" to the same token; VoiceAgent runs NegationGate first.
        val cases = listOf(
            "đừng mở cửa",
            "không mở cửa",
            "đừng mở khóa cửa",
            "thôi khỏi mở cửa",
            "em ơi đừng mở máy lạnh nha",
            "đừng bật đèn",
            "không tắt đèn cabin",
        )

        cases.forEach { text ->
            assertTrue(
                "Expected NegationGate to block '$text'",
                NegationGate.inspect(text) is NegationVerdict.Negated,
            )
        }
    }

    @Test
    fun `fan level khong is zero not a negation in the router`() {
        val result = router.route("quạt mức không") as RouteResult.Matched

        assertEquals("hvac_set_fan", result.intent.name)
        assertEquals(0, result.intent.slots["level"])
    }

    @Test
    fun `pause music is not treated as negation of dung`() {
        val result = router.route("dừng nhạc") as RouteResult.Matched
        assertEquals("media_pause", result.intent.name)
    }

    @Test
    fun `status questions route to read-only vehicle queries`() {
        val speed = router.route("cho tôi biết tốc độ hiện tại") as RouteResult.Matched
        val fuel = router.route("nhiên liệu còn bao nhiêu") as RouteResult.Matched
        val temp = router.route("điều hòa bao nhiêu độ") as RouteResult.Matched

        assertEquals("vehicle_status_speed", speed.intent.name)
        assertEquals("vehicle_status_fuel", fuel.intent.name)
        assertEquals("vehicle_status_temperature", temp.intent.name)
    }

    @Test
    fun `yes-no question about climate does not set temperature`() {
        val result = router.route("điều hòa đã bật chưa")

        assertTrue(result is RouteResult.Unsupported)
        assertEquals("N1_QUESTION", (result as RouteResult.Unsupported).rule)
        assertEquals(false, result.canFallback)
    }

    @Test
    fun `fillers are stripped before affirmative routing`() {
        val result = router.route("em ơi khóa cửa giúp tôi nhé") as RouteResult.Matched

        assertEquals("door_lock", result.intent.name)
        assertEquals(true, result.intent.slots["lock"])
    }
}
