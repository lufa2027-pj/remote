package com.vu.tvremote.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = AccentBlue,
    secondary = AccentAmber,
    tertiary = AccentGreen,
    background = DarkBackground,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantVariantDark ?: SurfaceDark,
    onPrimary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

private val SurfaceVariantVariantDark = Color(0xFF262C36)

@Composable
fun VuTvRemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
