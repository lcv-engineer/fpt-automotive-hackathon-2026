package com.sopa.viva_automotive.vehicleservice.impl

import com.sopa.viva_automotive.vehicleservice.api.VehicleAreas

/**
 * Maps app-level zone ids onto areaIds declared by the running VHAL.
 *
 * AAOS emulator exposes per-seat areas (`ROW_1_LEFT=0x1`, `ROW_1_RIGHT=0x4`, …).
 * Older app builds used composite masks (`0x31`, `0x44`) that are **not** valid
 * VHAL areaIds — those must be reduced to an overlapping declared seat.
 */
object AreaIdResolver {

    fun resolve(declared: IntArray?, requestedAreaId: Int): List<Int> {
        val requested = normalizeRequested(requestedAreaId)
        if (declared == null || declared.isEmpty()) return listOf(requested)
        if (requested in declared) return listOf(requested)
        if (requested == VehicleAreas.GLOBAL) return declared.toList()

        val overlapping = declared.filter { it and requested != 0 }
        if (overlapping.isEmpty()) return listOf(requested)
        // One primary seat: prefer the lowest area id (front-left before row-2 seats).
        return listOf(overlapping.min())
    }

    private fun normalizeRequested(requestedAreaId: Int): Int = when (requestedAreaId) {
        VehicleAreas.LEGACY_SEAT_ZONE_DRIVER -> VehicleAreas.SEAT_ZONE_DRIVER
        VehicleAreas.LEGACY_SEAT_ZONE_PASSENGER -> VehicleAreas.SEAT_ZONE_PASSENGER
        else -> requestedAreaId
    }
}
