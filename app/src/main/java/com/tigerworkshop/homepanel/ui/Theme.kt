package com.tigerworkshop.homepanel.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// A calm, deep-blue panel palette.
val PanelBg = Color(0xFF0B1220)
val PanelSurface = Color(0xFF161F31)
val PanelSurfaceHi = Color(0xFF1E293B)
val PanelSurfaceOn = Color(0xFF324A6E) // noticeably brighter tile when the light is on
val Accent = Color(0xFFFFB74D)      // warm light glow
val AccentCool = Color(0xFF4FC3F7)  // cool accent
val TextPrimary = Color(0xFFECEFF4)
val TextSecondary = Color(0xFF9AA7BD)

private val PanelColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF1A1205),
    secondary = AccentCool,
    background = PanelBg,
    onBackground = TextPrimary,
    surface = PanelSurface,
    onSurface = TextPrimary,
    surfaceVariant = PanelSurfaceHi,
    onSurfaceVariant = TextSecondary,
)

@Composable
fun HomePanelTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_EXPRESSION") isSystemInDarkTheme() // panel is always dark
    MaterialTheme(colorScheme = PanelColors, content = content)
}
