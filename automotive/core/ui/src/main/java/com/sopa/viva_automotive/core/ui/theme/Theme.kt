package com.sopa.viva_automotive.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val VivaDarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnPrimary,
    tertiary = DarkGreen,
    onTertiary = DarkOnPrimary,
    error = DarkRed,
    onError = DarkOnPrimary,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceHigh,
    onSurfaceVariant = DarkOnSurfaceMuted,
    outline = DarkOutline,
)

private val VivaLightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnPrimary,
    tertiary = LightGreen,
    onTertiary = LightOnPrimary,
    error = LightRed,
    onError = LightOnPrimary,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceHigh,
    onSurfaceVariant = LightOnSurfaceMuted,
    outline = LightOutline,
)

object VivaDimens {
    val TouchTargetMin: Dp = 44.dp
    val TouchTarget: Dp = 56.dp
    val ButtonHeight: Dp = 56.dp

    val SpacingXs: Dp = 4.dp
    val SpacingS: Dp = 8.dp
    val SpacingM: Dp = 16.dp
    val SpacingL: Dp = 24.dp
    val SpacingXl: Dp = 32.dp

    val ScreenPadding: Dp = 24.dp
}

@Composable
fun VivaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) VivaDarkColorScheme else VivaLightColorScheme,
        typography = VivaTypography,
        content = content,
    )
}
