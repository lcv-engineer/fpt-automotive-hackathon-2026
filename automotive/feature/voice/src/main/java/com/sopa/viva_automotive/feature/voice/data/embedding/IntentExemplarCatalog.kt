package com.sopa.viva_automotive.feature.voice.data.embedding

import com.sopa.viva_automotive.feature.voice.domain.model.VehicleIntentTypes

object IntentExemplarCatalog {

    data class Exemplar(val intentType: String, val phrase: String)

    val exemplars: List<Exemplar> = listOf(
        e(VehicleIntentTypes.AC_OFF, "turn off the ac"),
        e(VehicleIntentTypes.AC_OFF, "turn off the a c"),
        e(VehicleIntentTypes.AC_OFF, "turn off aircon"),
        e(VehicleIntentTypes.AC_OFF, "turn off air con"),
        e(VehicleIntentTypes.AC_OFF, "turn off air conditioning"),
        e(VehicleIntentTypes.AC_OFF, "ac off"),
        e(VehicleIntentTypes.AC_OFF, "stop the ac"),
        e(VehicleIntentTypes.AC_OFF, "disable the air conditioning"),
        e(VehicleIntentTypes.AC_OFF, "tắt điều hòa"),
        e(VehicleIntentTypes.AC_OFF, "tắt máy lạnh"),

        e(VehicleIntentTypes.AC_ON, "turn on the ac"),
        e(VehicleIntentTypes.AC_ON, "turn on the a c"),
        e(VehicleIntentTypes.AC_ON, "turn on aircon"),
        e(VehicleIntentTypes.AC_ON, "ac on"),
        e(VehicleIntentTypes.AC_ON, "turn on air conditioning"),
        e(VehicleIntentTypes.AC_ON, "bật điều hòa"),
        e(VehicleIntentTypes.AC_ON, "bật máy lạnh"),

        e(VehicleIntentTypes.TEMPERATURE_DOWN, "it's too hot"),
        e(VehicleIntentTypes.TEMPERATURE_DOWN, "too hot"),
        e(VehicleIntentTypes.TEMPERATURE_DOWN, "make it cooler"),
        e(VehicleIntentTypes.TEMPERATURE_DOWN, "lower the temperature"),
        e(VehicleIntentTypes.TEMPERATURE_DOWN, "quá nóng"),
        e(VehicleIntentTypes.TEMPERATURE_DOWN, "nóng quá"),

        e(VehicleIntentTypes.TEMPERATURE_UP, "it's too cold"),
        e(VehicleIntentTypes.TEMPERATURE_UP, "too cold"),
        e(VehicleIntentTypes.TEMPERATURE_UP, "make it warmer"),
        e(VehicleIntentTypes.TEMPERATURE_UP, "raise the temperature"),
        e(VehicleIntentTypes.TEMPERATURE_UP, "quá lạnh"),
        e(VehicleIntentTypes.TEMPERATURE_UP, "lạnh quá"),

        e(VehicleIntentTypes.SET_TEMPERATURE, "set the temperature to twenty two"),
        e(VehicleIntentTypes.SET_TEMPERATURE, "set temperature to 22 degrees"),
        e(VehicleIntentTypes.SET_TEMPERATURE, "đặt nhiệt độ 22 độ"),

        e(VehicleIntentTypes.FAN_UP, "increase the fan"),
        e(VehicleIntentTypes.FAN_UP, "fan up"),
        e(VehicleIntentTypes.FAN_UP, "more air"),
        e(VehicleIntentTypes.FAN_UP, "tăng quạt"),

        e(VehicleIntentTypes.FAN_DOWN, "decrease the fan"),
        e(VehicleIntentTypes.FAN_DOWN, "fan down"),
        e(VehicleIntentTypes.FAN_DOWN, "less air"),
        e(VehicleIntentTypes.FAN_DOWN, "giảm quạt"),

        e(VehicleIntentTypes.SET_FAN_SPEED, "set fan speed to three"),
        e(VehicleIntentTypes.SET_FAN_SPEED, "fan speed to 3"),
        e(VehicleIntentTypes.SET_FAN_SPEED, "đặt quạt mức 3"),

        e(VehicleIntentTypes.HVAC_OFF, "turn off climate"),
        e(VehicleIntentTypes.HVAC_OFF, "hvac off"),
        e(VehicleIntentTypes.HVAC_OFF, "climate off"),

        e(VehicleIntentTypes.HVAC_ON, "turn on climate"),
        e(VehicleIntentTypes.HVAC_ON, "hvac on"),
        e(VehicleIntentTypes.HVAC_ON, "climate on"),

        e(VehicleIntentTypes.LOCK_DOORS, "lock the doors"),
        e(VehicleIntentTypes.LOCK_DOORS, "lock the car"),
        e(VehicleIntentTypes.LOCK_DOORS, "khóa cửa"),

        e(VehicleIntentTypes.UNLOCK_DOORS, "unlock the doors"),
        e(VehicleIntentTypes.UNLOCK_DOORS, "unlock the car"),
        e(VehicleIntentTypes.UNLOCK_DOORS, "mở khóa cửa"),

        e(VehicleIntentTypes.QUERY_SPEED, "how fast am i going"),
        e(VehicleIntentTypes.QUERY_SPEED, "what is my speed"),
        e(VehicleIntentTypes.QUERY_SPEED, "tốc độ hiện tại"),

        e(VehicleIntentTypes.QUERY_FUEL, "how much fuel is left"),
        e(VehicleIntentTypes.QUERY_FUEL, "fuel level"),
        e(VehicleIntentTypes.QUERY_FUEL, "còn bao nhiêu xăng"),

        e(VehicleIntentTypes.QUERY_BATTERY, "how much battery"),
        e(VehicleIntentTypes.QUERY_BATTERY, "battery level"),
        e(VehicleIntentTypes.QUERY_BATTERY, "pin còn bao nhiêu"),

        e(VehicleIntentTypes.QUERY_TEMPERATURE, "what is the temperature"),
        e(VehicleIntentTypes.QUERY_TEMPERATURE, "current temperature"),
        e(VehicleIntentTypes.QUERY_TEMPERATURE, "nhiệt độ hiện tại"),

        e(VehicleIntentTypes.MEDIA_PLAY, "play music"),
        e(VehicleIntentTypes.MEDIA_PLAY, "play some jazz music"),
        e(VehicleIntentTypes.MEDIA_PLAY, "phát nhạc"),
        e(VehicleIntentTypes.MEDIA_PAUSE, "pause the music"),
        e(VehicleIntentTypes.MEDIA_PAUSE, "dừng nhạc"),
        e(VehicleIntentTypes.MEDIA_NEXT, "next song"),
        e(VehicleIntentTypes.MEDIA_NEXT, "chuyển bài"),
        e(VehicleIntentTypes.RADIO_TUNE, "play radio"),
        e(VehicleIntentTypes.RADIO_TUNE, "turn on the radio"),
        e(VehicleIntentTypes.RADIO_TUNE, "bật đài"),
        e(VehicleIntentTypes.RADIO_TUNE, "mở radio"),
        e(VehicleIntentTypes.RADIO_NEXT, "next station"),
        e(VehicleIntentTypes.RADIO_NEXT, "chuyển đài"),
        e(VehicleIntentTypes.VOLUME_UP, "volume up"),
        e(VehicleIntentTypes.VOLUME_UP, "tăng âm lượng"),
        e(VehicleIntentTypes.VOLUME_DOWN, "volume down"),
        e(VehicleIntentTypes.VOLUME_DOWN, "giảm âm lượng"),
    )

    private fun e(intentType: String, phrase: String) = Exemplar(intentType, phrase)
}
