package com.aethershield.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * A small, hand-drawn icon set: same stroke weight, same rounded caps, same optical size.
 * The extended Material icon pack is not available here, and a bespoke set keeps the app
 * from mixing three icon languages.
 */
private const val STROKE_RATIO = 0.085f

@Composable
fun GlyphPower(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        val width = s * STROKE_RATIO
        val diameter = s * 0.72f
        drawArc(
            color = tint,
            startAngle = -60f,
            sweepAngle = 300f,
            useCenter = false,
            topLeft = Offset((s - diameter) / 2, (s - diameter) / 2),
            size = Size(diameter, diameter),
            style = Stroke(width = width, cap = StrokeCap.Round),
        )
        drawLine(
            color = tint,
            start = Offset(s / 2, s * 0.08f),
            end = Offset(s / 2, s * 0.46f),
            strokeWidth = width,
            cap = StrokeCap.Round,
        )
    }
}

/** Three rounded bars: statistics. */
@Composable
fun GlyphBars(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        val width = s * 0.16f
        val heights = listOf(0.42f, 0.68f, 0.92f)
        heights.forEachIndexed { index, h ->
            val x = s * (0.16f + index * 0.30f)
            drawLine(
                color = tint,
                start = Offset(x, s * 0.92f),
                end = Offset(x, s * (0.92f - h * 0.84f)),
                strokeWidth = width,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** Lines of a document, with a dot per line: logs. */
@Composable
fun GlyphLines(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        val width = s * 0.075f
        listOf(0.24f, 0.50f, 0.76f).forEach { y ->
            drawCircle(color = tint, radius = width * 0.85f, center = Offset(s * 0.16f, s * y))
            drawLine(
                color = tint,
                start = Offset(s * 0.32f, s * y),
                end = Offset(s * if (y < 0.5f) 0.88f else 0.74f, s * y),
                strokeWidth = width,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** Ascending bars: signal strength / latency. */
@Composable
fun GlyphSignal(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        val width = s * 0.11f
        listOf(0.30f, 0.52f, 0.74f, 0.96f).forEachIndexed { index, h ->
            val x = s * (0.16f + index * 0.23f)
            drawLine(
                color = tint,
                start = Offset(x, s * 0.94f),
                end = Offset(x, s * (0.94f - h * 0.82f)),
                strokeWidth = width,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** Two stacked trays: a server. */
@Composable
fun GlyphServer(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        val width = s * 0.075f
        listOf(0.20f, 0.54f).forEach { top ->
            drawRoundRect(
                color = tint,
                topLeft = Offset(s * 0.14f, s * top),
                size = Size(s * 0.72f, s * 0.30f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.09f),
                style = Stroke(width = width),
            )
            drawCircle(
                color = tint,
                radius = width * 0.9f,
                center = Offset(s * 0.72f, s * (top + 0.15f)),
            )
        }
    }
}

/** A globe: the exit country. */
@Composable
fun GlyphGlobe(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        val width = s * 0.075f
        val d = s * 0.78f
        val topLeft = Offset((s - d) / 2, (s - d) / 2)
        drawCircle(color = tint, radius = d / 2, center = Offset(s / 2, s / 2), style = Stroke(width))
        drawOval(
            color = tint,
            topLeft = Offset(s * 0.32f, topLeft.y),
            size = Size(s * 0.36f, d),
            style = Stroke(width),
        )
        drawLine(
            color = tint,
            start = Offset(topLeft.x, s / 2),
            end = Offset(topLeft.x + d, s / 2),
            strokeWidth = width,
        )
    }
}

/** Two offset frames: copy. */
@Composable
fun GlyphCopy(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        val width = s * 0.075f
        drawRoundRect(
            color = tint,
            topLeft = Offset(s * 0.16f, s * 0.16f),
            size = Size(s * 0.50f, s * 0.50f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.10f),
            style = Stroke(width),
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(s * 0.34f, s * 0.34f),
            size = Size(s * 0.50f, s * 0.50f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.10f),
            style = Stroke(width),
        )
    }
}

/** A rounded square of blocks: QR. */
@Composable
fun GlyphQr(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        val width = s * 0.07f
        listOf(0.14f, 0.62f).forEach { o ->
            drawRoundRect(
                color = tint,
                topLeft = Offset(s * o, s * 0.14f),
                size = Size(s * 0.24f, s * 0.24f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.05f),
                style = Stroke(width),
            )
        }
        drawRoundRect(
            color = tint,
            topLeft = Offset(s * 0.14f, s * 0.62f),
            size = Size(s * 0.24f, s * 0.24f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.05f),
            style = Stroke(width),
        )
        drawRect(tint, topLeft = Offset(s * 0.62f, s * 0.62f), size = Size(s * 0.10f, s * 0.10f))
        drawRect(tint, topLeft = Offset(s * 0.76f, s * 0.62f), size = Size(s * 0.10f, s * 0.10f))
        drawRect(tint, topLeft = Offset(s * 0.62f, s * 0.76f), size = Size(s * 0.10f, s * 0.10f))
        drawRect(tint, topLeft = Offset(s * 0.76f, s * 0.76f), size = Size(s * 0.10f, s * 0.10f))
    }
}
