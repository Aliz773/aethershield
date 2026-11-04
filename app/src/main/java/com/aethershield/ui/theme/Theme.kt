package com.aethershield.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.aethershield.core.ThemeMode

/** AMOLED: true black, so the panel can actually switch pixels off. */
private val Amoled = darkColorScheme(
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

/** Ordinary dark: cards sit on a slightly lighter grey, not on pure black. */
private val Dark = darkColorScheme(
    primary = Color(0xFF5EEAD4),
    onPrimary = Color(0xFF00382F),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE8E8E8),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFE8E8E8),
    onSurfaceVariant = Color(0xFFAAAAAA),
    surfaceContainer = Color(0xFF1E1E1E),
    surfaceContainerHigh = Color(0xFF262626),
    outline = Color(0xFF5C5C5C),
    error = Color(0xFFFF6B6B),
    onError = Color.Black,
)

/** Light: the same teal brand, on paper. */
private val Light = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    background = Color(0xFFF7F8F8),
    onBackground = Color(0xFF161616),
    surface = Color(0xFFF7F8F8),
    onSurface = Color(0xFF161616),
    onSurfaceVariant = Color(0xFF5F6368),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEDEFEE),
    outline = Color(0xFFC2C6C4),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

@Composable
fun AetherShieldTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
    }
    val scheme = when {
        mode == ThemeMode.AMOLED -> Amoled
        dark -> Dark
        else -> Light
    }

    // enableEdgeToEdge() picks its bar icons from the *phone's* setting, so a light theme on
    // a dark phone would leave dark icons on a light background (invisible). Follow the app's
    // own choice instead.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    MaterialTheme(colorScheme = scheme, content = content)
}
