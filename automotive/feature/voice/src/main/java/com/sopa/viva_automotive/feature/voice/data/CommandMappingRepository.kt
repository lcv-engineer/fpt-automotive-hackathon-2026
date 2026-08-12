package com.sopa.viva_automotive.feature.voice.data

import com.sopa.viva_automotive.core.database.command.CommandMappingDao
import com.sopa.viva_automotive.core.database.command.CommandMappingEntity
import com.sopa.viva_automotive.feature.voice.domain.model.VehicleIntentTypes
import javax.inject.Inject
import javax.inject.Singleton

data class CommandMapping(
    val intentType: String,
    val keywords: List<String>,
    val priority: Int,
)

@Singleton
class CommandMappingRepository @Inject constructor(
    private val dao: CommandMappingDao,
) {

    suspend fun getMappings(): List<CommandMapping> =
        dao.getAll().map { entity ->
            CommandMapping(
                intentType = entity.intentType,
                keywords = entity.keywords.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                priority = entity.priority,
            )
        }

        suspend fun seedIfEmpty() {
        val existing = dao.getAll()
        val hasVietnamese = existing.any { entity ->
            entity.keywords.contains("bật điều hòa") || entity.keywords.contains("đặt nhiệt độ")
        }
        val hasMedia = existing.any { entity ->
            entity.intentType == VehicleIntentTypes.MEDIA_PLAY
        }
        val hasRadio = existing.any { entity ->
            entity.intentType == VehicleIntentTypes.RADIO_TUNE
        }
        if (existing.isNotEmpty() && hasVietnamese && hasMedia && hasRadio) return

        dao.deleteAll()
        dao.insertAll(DEFAULT_MAPPINGS)
    }

    private companion object {
        val DEFAULT_MAPPINGS = listOf(
            entity(
                VehicleIntentTypes.SET_TEMPERATURE,
                20,
                "set temperature", "set the temperature", "temperature to", "degrees",
                "đặt nhiệt độ", "chỉnh nhiệt độ", "nhiệt độ thành", "độ c",
            ),
            entity(
                VehicleIntentTypes.TEMPERATURE_UP,
                10,
                "warmer", "too cold", "increase temperature", "temperature up",
                "turn up the heat", "raise the temperature",
                "làm ấm", "tăng nhiệt độ", "nhiệt độ lên", "quá lạnh", "lạnh quá",
            ),
            entity(
                VehicleIntentTypes.TEMPERATURE_DOWN,
                10,
                "cooler", "too hot", "too warm", "decrease temperature", "temperature down",
                "lower the temperature",
                "làm mát", "giảm nhiệt độ", "nhiệt độ xuống", "quá nóng", "nóng quá", "quá ấm",
            ),
            entity(
                VehicleIntentTypes.SET_FAN_SPEED,
                20,
                "fan speed to", "set fan", "set the fan",
                "tốc độ quạt", "đặt quạt", "chỉnh quạt", "quạt mức",
            ),
            entity(
                VehicleIntentTypes.FAN_UP,
                10,
                "fan up", "increase fan", "more air", "stronger fan",
                "tăng quạt", "quạt mạnh hơn", "thêm gió", "quạt lớn hơn",
            ),
            entity(
                VehicleIntentTypes.FAN_DOWN,
                10,
                "fan down", "decrease fan", "less air", "weaker fan",
                "giảm quạt", "quạt yếu hơn", "bớt gió", "quạt nhỏ hơn",
            ),
            entity(
                VehicleIntentTypes.AC_OFF,
                15,
                "turn off the ac", "ac off", "turn off air conditioning", "stop the ac",
                "tắt điều hòa", "tắt máy lạnh", "tắt ac",
            ),
            entity(
                VehicleIntentTypes.AC_ON,
                10,
                "turn on the ac", "ac on", "turn on air conditioning", "air conditioning",
                "bật điều hòa", "bật máy lạnh", "bật ac",
            ),
            entity(
                VehicleIntentTypes.HVAC_OFF,
                15,
                "climate off", "turn off climate", "hvac off",
                "tắt climate", "tắt hệ thống điều hòa",
            ),
            entity(
                VehicleIntentTypes.HVAC_ON,
                10,
                "climate on", "turn on climate", "hvac on",
                "bật climate", "bật hệ thống điều hòa",
            ),
            entity(
                VehicleIntentTypes.LOCK_DOORS,
                10,
                "lock the doors", "lock doors", "lock the car",
                "khóa cửa", "khóa xe", "đóng khóa cửa",
            ),
            entity(
                VehicleIntentTypes.UNLOCK_DOORS,
                15,
                "unlock the doors", "unlock doors", "unlock the car",
                "mở khóa cửa", "mở cửa", "mở khóa xe",
            ),
            entity(
                VehicleIntentTypes.QUERY_SPEED,
                10,
                "how fast", "current speed", "what speed", "what is my speed",
                "tốc độ bao nhiêu", "đang chạy bao nhanh", "tốc độ hiện tại",
            ),
            entity(
                VehicleIntentTypes.QUERY_FUEL,
                10,
                "fuel level", "how much fuel", "how much gas",
                "còn bao nhiêu xăng", "mức xăng", "nhiên liệu",
            ),
            entity(
                VehicleIntentTypes.QUERY_BATTERY,
                10,
                "battery level", "how much battery", "state of charge",
                "pin còn bao nhiêu", "mức pin", "dung lượng pin",
            ),
            entity(
                VehicleIntentTypes.QUERY_TEMPERATURE,
                10,
                "what temperature", "current temperature", "how warm is it", "how cold is it",
                "nhiệt độ bao nhiêu", "nhiệt độ hiện tại", "bao nhiêu độ",
            ),
            entity(
                VehicleIntentTypes.MEDIA_PAUSE,
                20,
                "pause music", "pause the music", "stop the music", "stop music",
                "pause song", "dừng nhạc", "tạm dừng nhạc", "dừng bài",
            ),
            entity(
                VehicleIntentTypes.MEDIA_NEXT,
                20,
                "next song", "next track", "skip song", "skip track", "skip this song",
                "chuyển bài", "bài tiếp", "bài tiếp theo", "qua bài",
            ),
            entity(
                VehicleIntentTypes.MEDIA_PLAY,
                15,
                "play music", "play some music", "play the music", "play song",
                "play some jazz", "play jazz", "start music",
                "phát nhạc", "bật nhạc", "phát bài",
            ),
            entity(
                VehicleIntentTypes.RADIO_NEXT,
                22,
                "next station", "next radio station", "seek station",
                "đài tiếp", "đài tiếp theo", "chuyển đài", "qua đài",
            ),
            entity(
                VehicleIntentTypes.RADIO_TUNE,
                18,
                "play radio", "turn on the radio", "turn on radio", "tune radio",
                "tune to", "fm radio",
                "bật radio", "bật đài", "mở đài", "nghe đài", "mở radio",
            ),
            entity(
                VehicleIntentTypes.VOLUME_UP,
                15,
                "volume up", "turn up the volume", "louder", "increase volume",
                "tăng âm lượng", "to hơn", "lớn tiếng hơn",
            ),
            entity(
                VehicleIntentTypes.VOLUME_DOWN,
                15,
                "volume down", "turn down the volume", "quieter", "decrease volume",
                "giảm âm lượng", "nhỏ hơn", "nhỏ tiếng hơn",
            ),
        )

        fun entity(intentType: String, priority: Int, vararg keywords: String) =
            CommandMappingEntity(
                intentType = intentType,
                keywords = keywords.joinToString(","),
                priority = priority,
            )
    }
}
