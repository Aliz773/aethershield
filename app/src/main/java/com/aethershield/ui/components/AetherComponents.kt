package com.aethershield.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aethershield.core.ConnectionState
import com.aethershield.ui.theme.AetherText
import com.aethershield.ui.theme.Palette
import com.aethershield.ui.theme.Radius
import com.aethershield.ui.theme.Space

/** The single card used everywhere: elevated surface, 28dp corners, one hairline. */
@Composable
fun AetherCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = Space.card,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(Radius.large)
    val base = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(scheme.surfaceContainer)
        .border(1.dp, scheme.outline.copy(alpha = 0.55f), shape)
    val clickable = if (onClick != null) base.clickable(onClick = onClick) else base
    Column(clickable.padding(contentPadding), content = content)
}

@Composable
fun AetherIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(scheme.surfaceContainer)
            .border(1.dp, scheme.outline.copy(alpha = 0.5f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Compact header: mark, product name, and up to two actions. */
@Composable
fun AetherTopBar(
    title: String,
    subtitle: String,
    onShare: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    back: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (back != null) {
            back()
            Spacer(Modifier.width(12.dp))
        } else {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(Radius.icon))
                    .background(scheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                GlyphPower(tint = scheme.primary, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = AetherText.screenTitle.copy(fontSize = 21.sp),
                color = scheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = AetherText.label,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onShare != null) {
            AetherIconButton(onClick = onShare) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        if (onMore != null) {
            AetherIconButton(onClick = onMore) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
    }
}

/**
 * The one control that matters. A single ring, a soft inner gradient, and a thin arc that
 * spins only while the app is actually doing something.
 */
@Composable
fun AetherConnectionButton(
    state: ConnectionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 230.dp,
) {
    val scheme = MaterialTheme.colorScheme
    val busy = state is ConnectionState.Preparing ||
        state is ConnectionState.Connecting ||
        state is ConnectionState.Reconnecting ||
        state is ConnectionState.Disconnecting
    val connected = state is ConnectionState.Connected
    val failed = state is ConnectionState.Error

    val accent = when {
        failed -> Palette.Red
        connected -> Palette.Cyan
        else -> Palette.Blue
    }
    val iconTint by animateColorAsState(
        targetValue = if (connected) Palette.Cyan else scheme.onSurface,
        label = "powerTint",
    )

    val spin by rememberInfiniteTransition(label = "connection").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "connectionSpin",
    )

    Box(
        modifier
            .size(diameter)
            .clip(CircleShape)
            .clickable(
                enabled = state !is ConnectionState.Disconnecting,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Outer ring: a very soft sweep so the circle does not read as a flat blob.
        Box(
            Modifier
                .fillMaxSize()
                .border(
                    BorderStroke(
                        1.dp,
                        Brush.sweepGradient(
                            listOf(
                                accent.copy(alpha = 0.55f),
                                accent.copy(alpha = 0.04f),
                                accent.copy(alpha = 0.30f),
                                accent.copy(alpha = 0.04f),
                                accent.copy(alpha = 0.55f),
                            )
                        ),
                    ),
                    CircleShape,
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .padding(13.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(scheme.surfaceContainerHigh, scheme.background),
                    )
                )
        )

        if (busy) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .padding(13.dp)
                    .graphicsLayer { rotationZ = spin }
            ) {
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 68f,
                    useCenter = false,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }

        GlyphPower(tint = iconTint, modifier = Modifier.size(64.dp))
    }
}

@Composable
fun ConnectionStatusText(
    title: String,
    subtitle: String?,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = AetherText.status, color = accent)
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = AetherText.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Latency, colour-coded, with an honest dash when it was never measured. */
@Composable
fun PingIndicator(millis: Int?, modifier: Modifier = Modifier, compact: Boolean = true) {
    val scheme = MaterialTheme.colorScheme
    val color = when {
        millis == null -> scheme.onSurfaceVariant
        millis <= 80 -> Palette.Green
        millis <= 150 -> Palette.Amber
        else -> Palette.Red
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        GlyphSignal(tint = color, modifier = Modifier.size(if (compact) 13.dp else 16.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            text = millis?.let { "$it ms" } ?: "-- ms",
            style = AetherText.label,
            color = color,
        )
    }
}

@Composable
fun AetherSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp),
        style = AetherText.label.copy(letterSpacing = 0.8.sp, fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** One row inside a card: leading glyph, label, value, optional trailing content. */
@Composable
fun AetherInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = {},
    trailing: @Composable () -> Unit = {},
    onClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val base = Modifier.fillMaxWidth()
    val clickable = if (onClick != null) base.clickable(onClick = onClick) else base
    Row(
        clickable.then(modifier).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = AetherText.label, color = scheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(value, style = AetherText.body, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

@Composable
fun AetherChevron() {
    Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(18.dp),
    )
}

/** Settings row: title, one line of explanation, and whatever control belongs there. */
@Composable
fun AetherSettingRow(
    title: String,
    description: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = { AetherChevron() },
) {
    val scheme = MaterialTheme.colorScheme
    val base = Modifier.fillMaxWidth()
    val clickable = if (onClick != null) base.clickable(onClick = onClick) else base
    Row(
        clickable.padding(horizontal = Space.card, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = AetherText.cardTitle.copy(fontSize = 15.sp), color = scheme.onSurface)
            if (description != null) {
                Spacer(Modifier.height(2.dp))
                Text(description, style = AetherText.label, color = scheme.onSurfaceVariant)
            }
        }
        trailing()
    }
}

@Composable
fun AetherPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val background = if (enabled) scheme.primary else scheme.surfaceContainerHigh
    val foreground = if (enabled) scheme.onPrimary else scheme.onSurfaceVariant
    Box(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(Radius.button))
            .background(background)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = AetherText.cardTitle, color = foreground)
    }
}

/** A quiet sparkline: only the shape of the last few samples, no axes, no labels. */
@Composable
fun Sparkline(values: List<Float>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val max = values.max().coerceAtLeast(1f)
        val stepX = size.width / (values.size - 1).toFloat()
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = index * stepX
            val y = size.height - (value / max) * size.height * 0.9f
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path,
            color = color.copy(alpha = 0.85f),
            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round),
        )
        // A soft fill under the line, kept subtle so it never dominates the card.
        val fill = Path().apply {
            addPath(path)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.16f), Color.Transparent)))
    }
}
