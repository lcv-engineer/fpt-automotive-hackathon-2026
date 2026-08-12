package com.sopa.viva_automotive.vehicleservice.api

object VehicleProperties {
    const val PERF_VEHICLE_SPEED = 291504647
    const val FUEL_LEVEL = 291504903
    const val EV_BATTERY_LEVEL = 291504905
    const val IGNITION_STATE = 289408009

    const val DOOR_LOCK = 371198722
    const val DOOR_POS = 373295872
    const val CABIN_LIGHTS_STATE = 289410817 // read-only, actual light state
    const val CABIN_LIGHTS_SWITCH = 289410818 // writable switch position

    const val HVAC_FAN_SPEED = 356517120
    const val HVAC_FAN_DIRECTION = 356517121
    const val HVAC_TEMPERATURE_CURRENT = 358614274
    const val HVAC_TEMPERATURE_SET = 358614275
    const val HVAC_AC_ON = 354419973
    const val HVAC_AUTO_ON = 354419978
    const val HVAC_POWER_ON = 354419984
}

/** Canonical discrete fan levels shared by UI, voice, and vehicle-service clients. */
object FanSpeed {
    const val MIN_LEVEL = 0
    const val MAX_LEVEL = 5

    fun isValid(level: Int): Boolean = level in MIN_LEVEL..MAX_LEVEL

    fun clamp(level: Int): Int = level.coerceIn(MIN_LEVEL, MAX_LEVEL)
}

object FanDirection {
    const val FACE = 0x1
    const val FLOOR = 0x2
    const val FACE_AND_FLOOR = 0x3
    const val DEFROST = 0x4
}

object LightSwitch {
    const val OFF = 0
    const val ON = 1
}

object VehicleAreas {
    const val GLOBAL = 0
    /** [android.car.VehicleAreaSeat.ROW_1_LEFT] — front driver on LHD AAOS images. */
    const val SEAT_ZONE_DRIVER = 0x00000001
    /** [android.car.VehicleAreaSeat.ROW_1_RIGHT] — front passenger on LHD AAOS images. */
    const val SEAT_ZONE_PASSENGER = 0x00000004
    const val DOOR_ROW_1_LEFT = 0x00000001

    /** Legacy composite masks some OEMs / older app builds used (not valid VHAL areaIds). */
    const val LEGACY_SEAT_ZONE_DRIVER = 0x31
    const val LEGACY_SEAT_ZONE_PASSENGER = 0x44
}

enum class VehicleZone(val areaId: Int) {
    DRIVER(VehicleAreas.SEAT_ZONE_DRIVER),
    PASSENGER(VehicleAreas.SEAT_ZONE_PASSENGER),
    ALL(VehicleAreas.GLOBAL),
}
