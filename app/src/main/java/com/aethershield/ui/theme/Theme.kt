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

/**
 * The premium dark look: near-black background, slightly lifted cards, one cyan accent.
 * AMOLED is the same design with true black so an OLED panel can switch pixels off.
 */
private fun darkScheme(background: Color, container: Color, containerHigh: Color) = darkColorScheme(
    primary = Palette.Cyan,
    onPrimary = Color(0xFF00131A),
    primaryContainer = Palette.Cyan.copy(alpha = 0.16f),
    onPrimaryContainer = Palette.Cyan,
    secondary = Palette.Blue,
    onSecondary = Color.White,
    background = background,
    onBackground = Palette.TextPrimary,
    surface = background,
    onSurface = Palette.TextPrimary,
    surfaceVariant = container,
    onSurfaceVariant = Palette.TextSecondary,
    surfaceContainer = container,
    surfaceContainerHigh = containerHigh,
    surfaceContainerHighest = containerHigh,
    outline = Palette.Divider,
    outlineVariant = Palette.Divider,
    error = Palette.Red,
    onError = Color.White,
)

private val PremiumDark = darkScheme(
    background = Palette.Background,
    container = Palette.Surface,
    containerHigh = Palette.SurfaceElevated,
)

private val AmoledDark = darkScheme(
    background = Color.Black,
    container = Palette.BackgroundDeep,
    containerHigh = Palette.Surface,
)

/** Light is not in the reference, so it is the same design on paper with the same accent. */
private val LightScheme = lightColorScheme(
    primary = Color(0xFF0087A8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF00C8F0).copy(alpha = 0.14f),
    onPrimaryContainer = Color(0xFF00566A),
    secondary = Color(0xFF0A6FD1),
    onSecondary = Color.White,
    background = Color(0xFFF6F8FA),
    onBackground = Color(0xFF0E141B),
    surface = Color(0xFFF6F8FA),
    onSurface = Color(0xFF0E141B),
    surfaceVariant = Color(0xFFEDF1F5),
    onSurfaceVariant = Color(0xFF5A6673),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFEFF3F6),
    surfaceContainerHighest = Color(0xFFE7ECF1),
    outline = Color(0xFFD3DAE2),
    outlineVariant = Color(0xFFE2E8EE),
    error = Color(0xFFC62828),
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
        mode == ThemeMode.AMOLED -> AmoledDark
        dark -> PremiumDark
        else -> LightScheme
    }

    // enableEdgeToEdge() picks its bar icons from the *phone's* setting, so a light theme on
    // a dark phone would leave dark icons on a light background. Follow the app's own choice.
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

    MaterialTheme(colorScheme = scheme, typography = AetherTypography, content = content)
}
