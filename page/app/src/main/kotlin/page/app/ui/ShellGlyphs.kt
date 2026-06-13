package page.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private fun DrawScope.strokeStyle(width: Float) = Stroke(
    width = width,
    cap = StrokeCap.Round,
    join = StrokeJoin.Round,
)

@Composable
internal fun PlayGlyph(tint: Color, size: Dp = 14.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val p = Path().apply {
            moveTo(w * 0.22f, h * 0.14f)
            lineTo(w * 0.88f, h * 0.5f)
            lineTo(w * 0.22f, h * 0.86f)
            close()
        }
        drawPath(p, color = tint)
    }
}

@Composable
internal fun StopGlyph(tint: Color, size: Dp = 14.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val pad = this.size.width * 0.2f
        drawRoundRect(
            color = tint,
            topLeft = Offset(pad, pad),
            size = Size(this.size.width - pad * 2, this.size.height - pad * 2),
            cornerRadius = CornerRadius(this.size.width * 0.08f),
        )
    }
}

@Composable
internal fun TerminalGlyph(tint: Color, size: Dp = 14.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val strokeW = w * 0.14f
        val cx = w * 0.32f
        val cy = h * 0.5f
        val cs = w * 0.22f
        val p = Path().apply {
            moveTo(cx - cs, cy - cs * 0.95f)
            lineTo(cx + cs * 0.35f, cy)
            lineTo(cx - cs, cy + cs * 0.95f)
        }
        drawPath(p, color = tint, style = strokeStyle(strokeW))
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.55f, h * 0.64f),
            size = Size(w * 0.34f, strokeW),
            cornerRadius = CornerRadius(strokeW / 2),
        )
    }
}

@Composable
internal fun AtlasGlyph(tint: Color, size: Dp = 14.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val strokeW = w * 0.1f
        val center = Offset(w * 0.5f, h * 0.5f)
        val nodes = listOf(
            Offset(w * 0.5f, h * 0.14f),
            Offset(w * 0.16f, h * 0.78f),
            Offset(w * 0.84f, h * 0.78f),
        )
        nodes.forEach { node ->
            drawLine(color = tint, start = center, end = node, strokeWidth = strokeW, cap = StrokeCap.Round)
        }
        drawCircle(color = tint, radius = w * 0.16f, center = center)
        nodes.forEach { node ->
            drawCircle(color = tint, radius = w * 0.12f, center = node)
        }
    }
}

@Composable
internal fun OutputGlyph(tint: Color, size: Dp = 14.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val lineH = h * 0.11f
        val gap = h * 0.16f
        val widths = listOf(0.82f, 0.58f, 0.82f)
        var y = h * 0.22f
        widths.forEach { wf ->
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.09f, y),
                size = Size(w * wf, lineH),
                cornerRadius = CornerRadius(lineH / 2),
            )
            y += lineH + gap
        }
    }
}

@Composable
internal fun SettingsGlyph(tint: Color, size: Dp = 14.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w * 0.5f
        val cy = h * 0.5f
        val ring = w * 0.26f
        val strokeW = w * 0.13f
        val toothOuter = w * 0.45f
        val teeth = 8
        for (i in 0 until teeth) {
            val a = (Math.PI * 2.0 / teeth) * i
            val sx = cx + (ring * kotlin.math.cos(a)).toFloat()
            val sy = cy + (ring * kotlin.math.sin(a)).toFloat()
            val ex = cx + (toothOuter * kotlin.math.cos(a)).toFloat()
            val ey = cy + (toothOuter * kotlin.math.sin(a)).toFloat()
            drawLine(color = tint, start = Offset(sx, sy), end = Offset(ex, ey), strokeWidth = strokeW, cap = StrokeCap.Round)
        }
        drawCircle(color = tint, radius = ring, center = Offset(cx, cy), style = Stroke(width = strokeW))
        drawCircle(color = tint, radius = w * 0.1f, center = Offset(cx, cy))
    }
}

@Composable
internal fun FilesGlyph(tint: Color, size: Dp = 14.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val p = Path().apply {
            moveTo(w * 0.12f, h * 0.74f)
            lineTo(w * 0.12f, h * 0.30f)
            lineTo(w * 0.40f, h * 0.30f)
            lineTo(w * 0.48f, h * 0.40f)
            lineTo(w * 0.88f, h * 0.40f)
            lineTo(w * 0.88f, h * 0.74f)
            close()
        }
        drawPath(p, color = tint, style = strokeStyle(w * 0.1f))
    }
}

@Composable
internal fun SearchGlyph(tint: Color, size: Dp = 14.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val strokeW = w * 0.12f
        drawCircle(
            color = tint,
            radius = w * 0.26f,
            center = Offset(w * 0.42f, h * 0.42f),
            style = Stroke(width = strokeW),
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.62f, h * 0.62f),
            end = Offset(w * 0.86f, h * 0.86f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
internal fun SourceControlGlyph(tint: Color, size: Dp = 14.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val strokeW = w * 0.1f
        val r = w * 0.12f
        val top = Offset(w * 0.28f, h * 0.24f)
        val bottom = Offset(w * 0.28f, h * 0.76f)
        val branch = Offset(w * 0.72f, h * 0.42f)
        drawLine(color = tint, start = Offset(top.x, top.y + r), end = Offset(bottom.x, bottom.y - r), strokeWidth = strokeW, cap = StrokeCap.Round)
        val curve = Path().apply {
            moveTo(top.x + r, top.y)
            cubicTo(w * 0.60f, h * 0.30f, branch.x, h * 0.30f, branch.x, branch.y - r)
        }
        drawPath(curve, color = tint, style = strokeStyle(strokeW))
        drawCircle(color = tint, radius = r, center = top, style = Stroke(width = strokeW))
        drawCircle(color = tint, radius = r, center = bottom, style = Stroke(width = strokeW))
        drawCircle(color = tint, radius = r, center = branch, style = Stroke(width = strokeW))
    }
}

@Composable
internal fun AppMarkGlyph(tint: Color, size: Dp = 14.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val p = Path().apply {
            moveTo(w * 0.5f, h * 0.08f)
            lineTo(w * 0.92f, h * 0.5f)
            lineTo(w * 0.5f, h * 0.92f)
            lineTo(w * 0.08f, h * 0.5f)
            close()
        }
        drawPath(p, color = tint)
    }
}

@Composable
internal fun PairGlyph(tint: Color, size: Dp = 14.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val strokeW = w * 0.1f
        drawCircle(color = tint, radius = w * 0.2f, center = Offset(w * 0.38f, h * 0.42f), style = Stroke(width = strokeW))
        drawCircle(color = tint, radius = w * 0.2f, center = Offset(w * 0.62f, h * 0.42f), style = Stroke(width = strokeW))
    }
}

@Composable
internal fun GlassGlyph(tint: Color, size: Dp = 14.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val p = Path().apply {
            moveTo(w * 0.5f, h * 0.14f)
            lineTo(w * 0.86f, h * 0.5f)
            lineTo(w * 0.5f, h * 0.86f)
            lineTo(w * 0.14f, h * 0.5f)
            close()
        }
        drawPath(p, color = tint, style = strokeStyle(w * 0.1f))
    }
}

@Composable
internal fun EchoGlyph(tint: Color, size: Dp = 14.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val strokeW = w * 0.1f
        val center = Offset(w * 0.5f, h * 0.5f)
        drawCircle(color = tint, radius = w * 0.32f, center = center, style = Stroke(width = strokeW))
        drawLine(color = tint, start = center, end = Offset(w * 0.5f, h * 0.28f), strokeWidth = strokeW, cap = StrokeCap.Round)
        drawLine(color = tint, start = center, end = Offset(w * 0.66f, h * 0.58f), strokeWidth = strokeW, cap = StrokeCap.Round)
    }
}

@Composable
internal fun AiGlyph(tint: Color, size: Dp = 14.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val p = Path().apply {
            moveTo(w * 0.5f, h * 0.1f)
            lineTo(w * 0.59f, h * 0.41f)
            lineTo(w * 0.9f, h * 0.5f)
            lineTo(w * 0.59f, h * 0.59f)
            lineTo(w * 0.5f, h * 0.9f)
            lineTo(w * 0.41f, h * 0.59f)
            lineTo(w * 0.1f, h * 0.5f)
            lineTo(w * 0.41f, h * 0.41f)
            close()
        }
        drawPath(p, color = tint)
    }
}

@Composable
internal fun CommandGlyph(tint: Color, size: Dp = 14.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val c = 0.36f
        val r = 0.135f
        val lo = c - r
        val hi = 1f - c + r
        val style = strokeStyle(w * 0.085f)
        drawCircle(color = tint, radius = w * r, center = Offset(w * lo, w * lo), style = style)
        drawCircle(color = tint, radius = w * r, center = Offset(w * hi, w * lo), style = style)
        drawCircle(color = tint, radius = w * r, center = Offset(w * lo, w * hi), style = style)
        drawCircle(color = tint, radius = w * r, center = Offset(w * hi, w * hi), style = style)
        drawLine(tint, Offset(w * lo, w * c), Offset(w * hi, w * c), strokeWidth = w * 0.085f, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * lo, w * (1f - c)), Offset(w * hi, w * (1f - c)), strokeWidth = w * 0.085f, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * c, w * lo), Offset(w * c, w * hi), strokeWidth = w * 0.085f, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * (1f - c), w * lo), Offset(w * (1f - c), w * hi), strokeWidth = w * 0.085f, cap = StrokeCap.Round)
    }
}

@Composable
internal fun ChevronGlyph(tint: Color, size: Dp = 10.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val p = Path().apply {
            moveTo(w * 0.24f, h * 0.38f)
            lineTo(w * 0.5f, h * 0.64f)
            lineTo(w * 0.76f, h * 0.38f)
        }
        drawPath(p, color = tint, style = strokeStyle(w * 0.14f))
    }
}
