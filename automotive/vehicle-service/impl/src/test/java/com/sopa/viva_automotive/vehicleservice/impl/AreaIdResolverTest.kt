package com.sopa.viva_automotive.vehicleservice.impl

import com.sopa.viva_automotive.vehicleservice.api.VehicleAreas
import org.junit.Assert.assertEquals
import org.junit.Test

class AreaIdResolverTest {

    @Test
    fun `exact match is returned unchanged`() {
        val declared = intArrayOf(0x01, 0x04)

        assertEquals(listOf(0x01), AreaIdResolver.resolve(declared, 0x01))
        assertEquals(listOf(0x04), AreaIdResolver.resolve(declared, 0x04))
    }

    @Test
    fun `passenger zone resolves to ROW_1_RIGHT`() {
        val declared = intArrayOf(
            VehicleAreas.DOOR_ROW_1_LEFT,
            VehicleAreas.SEAT_ZONE_PASSENGER,
        )

        assertEquals(
            listOf(VehicleAreas.SEAT_ZONE_PASSENGER),
            AreaIdResolver.resolve(declared, VehicleAreas.SEAT_ZONE_PASSENGER),
        )
    }

    @Test
    fun `legacy passenger mask 0x44 resolves to ROW_1_RIGHT when declared`() {
        val declared = intArrayOf(0x01, 0x04, 0x40)

        assertEquals(
            listOf(0x04),
            AreaIdResolver.resolve(declared, VehicleAreas.LEGACY_SEAT_ZONE_PASSENGER),
        )
    }

    @Test
    fun `driver zone resolves to ROW_1_LEFT`() {
        val declared = intArrayOf(0x01, 0x04)

        assertEquals(
            listOf(VehicleAreas.SEAT_ZONE_DRIVER),
            AreaIdResolver.resolve(declared, VehicleAreas.SEAT_ZONE_DRIVER),
        )
    }

    @Test
    fun `legacy driver mask 0x31 picks front-left not every overlapping row-2 seat`() {
        val declared = intArrayOf(0x01, 0x04, 0x10, 0x20, 0x40)

        assertEquals(
            listOf(0x01),
            AreaIdResolver.resolve(declared, VehicleAreas.LEGACY_SEAT_ZONE_DRIVER),
        )
    }

    @Test
    fun `GLOBAL request fans out to every declared area`() {
        val declared = intArrayOf(0x01, 0x04)

        assertEquals(listOf(0x01, 0x04), AreaIdResolver.resolve(declared, VehicleAreas.GLOBAL))
    }

    @Test
    fun `unknown config returns the normalized requested id`() {
        assertEquals(
            listOf(VehicleAreas.SEAT_ZONE_PASSENGER),
            AreaIdResolver.resolve(null, VehicleAreas.LEGACY_SEAT_ZONE_PASSENGER),
        )
        assertEquals(
            listOf(VehicleAreas.SEAT_ZONE_PASSENGER),
            AreaIdResolver.resolve(intArrayOf(), VehicleAreas.LEGACY_SEAT_ZONE_PASSENGER),
        )
    }

    @Test
    fun `no-overlap request falls back to the requested id`() {
        assertEquals(listOf(0x20), AreaIdResolver.resolve(intArrayOf(0x01, 0x04), 0x20))
    }
}
