package com.sopa.viva_automotive.core.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Brand palettes (user-provided).
 *
 * Light: cream + sage greens
 *   rgb(246, 240, 215) · rgb(197, 216, 157) · rgb(156, 171, 132) · rgb(137, 152, 109)
 *
 * Dark: charcoal + warm taupe/cream
 *   rgb(34, 40, 49) · rgb(57, 62, 70) · rgb(148, 137, 121) · rgb(223, 208, 184)
 */

// --- Light ---
// Whiter page background so cream cards (#F6F0D7) read as distinct surfaces.
val LightBackground = Color(0xFFFAF8F2)
val LightSurface = Color(0xFFF6F0D7) // cream cards
val LightSurfaceHigh = Color(0xFFC5D89D) // light sage

val LightPrimary = Color(0xFF89986D) // deep sage — interactive
val LightSecondary = Color(0xFF9CAB84) // mid sage
val LightPrimaryContainer = Color(0xFFC5D89D)
val LightOnPrimaryContainer = Color(0xFF121212)

val LightOnSurface = Color(0xFF121212) // black text
val LightOnSurfaceMuted = Color(0xB3121212) // 70% black
val LightOutline = Color(0xFF9CAB84)

val LightOnPrimary = Color(0xFFFAF8F2)
val LightAmber = Color(0xFF8F5F00)
val LightRed = Color(0xFFC62828)
val LightGreen = Color(0xFF2E7D32)

// --- Dark ---
val DarkBackground = Color(0xFF222831) // charcoal
val DarkSurface = Color(0xFF393E46)
val DarkSurfaceHigh = Color(0xFF393E46)

val DarkPrimary = Color(0xFFDFD0B8) // warm cream — interactive
val DarkSecondary = Color(0xFF948979) // taupe
val DarkPrimaryContainer = Color(0xFF948979)
val DarkOnPrimaryContainer = Color(0xFFDFD0B8)

val DarkOnSurface = Color(0xFFF5F1EA) // warmer near-white for body text
val DarkOnSurfaceMuted = Color(0xCCDFD0B8) // ~80% cream
val DarkOutline = Color(0xFF948979)

val DarkOnPrimary = Color(0xFF222831)
val DarkAmber = Color(0xFFE5B171)
val DarkRed = Color(0xFFE07A77)
val DarkGreen = Color(0xFF85BE88)

// Back-compat aliases used by older call sites / Theme.kt naming
val DarkCyan = DarkPrimary
val LightCyan = LightPrimary
val LightCyanContainer = LightPrimaryContainer
val LightOnCyanContainer = LightOnPrimaryContainer
