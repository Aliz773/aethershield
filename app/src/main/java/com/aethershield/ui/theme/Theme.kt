package com.aethershield.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Oled = darkColorScheme(
    primary = Color(0xFF5EEAD4),
    onPrimary = Color.Black,
    background = Color.Black,
    onBackground = Color(0xFFEDEDED),
    surface = Color.Black,
    onSurface = Color(0xFFEDEDED),
    onSurfaceVariant = Color(0xFF9A9A9A),
    surfaceContainer = Color(0xFF111111),
    surfaceContainerHigh = Color(0xFF171717),
    outline = Color(0xFF444444),
    error = Color(0xFFFF6B6B),
    onError = Color.Black,
)

@Composable
fun AetherShieldTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Oled, content = content)
}
