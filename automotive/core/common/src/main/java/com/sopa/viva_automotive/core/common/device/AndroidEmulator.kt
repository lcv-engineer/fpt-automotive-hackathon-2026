package com.sopa.viva_automotive.core.common.device

import android.os.Build

/** Best-effort detection of the Android Emulator (goldfish / ranchu / SDK images). */
object AndroidEmulator {
    fun isEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT
        val model = Build.MODEL
        val product = Build.PRODUCT
        val hardware = Build.HARDWARE
        return fingerprint.startsWith("generic") ||
            fingerprint.contains("emulator", ignoreCase = true) ||
            model.contains("sdk", ignoreCase = true) ||
            model.contains("Emulator", ignoreCase = true) ||
            product.contains("sdk", ignoreCase = true) ||
            hardware.contains("goldfish", ignoreCase = true) ||
            hardware.contains("ranchu", ignoreCase = true)
    }
}
