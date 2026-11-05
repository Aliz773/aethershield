package com.aethershield.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The one palette the whole app draws from. Cyan is the interaction colour and is used
 * sparingly: the power control, the selected tab, and active values. Everything else is
 * neutral so the screen stays calm.
 */
object Palette {
    val Background = Color(0xFF080B10)
    val BackgroundDeep = Color(0xFF0D1118)
    val Surface = Color(0xFF151A23)
    val SurfaceElevated = Color(0xFF1B222D)
    val Divider = Color(0xFF252D38)

    val Cyan = Color(0xFF00C8F0)
    val Blue = Color(0xFF2997FF)
    val Green = Color(0xFF30D158)
    val Amber = Color(0xFFFFB547)
    val Red = Color(0xFFFF453A)

    val TextPrimary = Color(0xFFF5F7FA)
    val TextSecondary = Color(0xFF9BA6B5)
    val TextTertiary = Color(0xFF697586)

    /** 1dp card borders: visible enough to separate two dark surfaces, never a "glow". */
    val Hairline = Color(0x14FFFFFF)
}

object Radius {
    val large = 28.dp
    val medium = 22.dp
    val small = 16.dp
    val button = 20.dp
    val icon = 16.dp
    val nav = 30.dp
}

object Space {
    val screen = 20.dp
    val card = 20.dp
    val standard = 16.dp
    val small = 8.dp
    val section = 24.dp
}

/** Semantic text styles, so a size is chosen once and used everywhere. */
object AetherText {
    val screenTitle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 28.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.4).sp,
    )
    val status = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 25.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.3).sp,
    )
    val cardTitle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 17.sp,
        fontWeight = FontWeight.Medium,
    )
    val value = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 21.sp,
        fontWeight = FontWeight.Medium,
    )
    val body = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
    )
    val label = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp,
    )
    val mono = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    )
}

val AetherTypography = Typography(
    headlineMedium = AetherText.screenTitle,
    titleLarge = AetherText.status,
    titleMedium = AetherText.cardTitle,
    bodyLarge = AetherText.body,
    labelSmall = AetherText.label,
)
