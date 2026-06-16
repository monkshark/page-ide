package page.atlas.render

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import kotlin.math.hypot
import page.atlas.graph.EdgeKind
import page.atlas.graph.NodeKind
import page.ui.Glass

data class AtlasTheme(
    val nodeActive: Color,
    val nodeWorkspace: Color,
    val nodeExternal: Color,
    val nodeSymbol: Color,
    val edgeImport: Color,
    val edgeRelation: Color,
    val label: Color,
    val selectionRing: Color,
    val ring: Color,
    val nodeHighlight: Color,
    val nodeShadow: Color,
)

@Composable
fun rememberAtlasTheme(): AtlasTheme {
    val colors = Glass.colors
    val edgeImport = MaterialTheme.colorScheme.outlineVariant
    val edgeRelation = MaterialTheme.colorScheme.tertiary
    return remember(colors, edgeImport, edgeRelation) {
        AtlasTheme(
            nodeActive = colors.primary,
            nodeWorkspace = colors.accent,
            nodeExternal = colors.outline,
            nodeSymbol = edgeRelation,
            edgeImport = edgeImport,
            edgeRelation = edgeRelation,
            label = colors.muted,
            selectionRing = colors.muted,
            ring = edgeImport,
            nodeHighlight = Color.White,
            nodeShadow = Color.Black,
        )
    }
}

fun AtlasTheme.nodeColor(kind: NodeKind): Color = when (kind) {
    NodeKind.ACTIVE -> nodeActive
    NodeKind.WORKSPACE_FILE -> nodeWorkspace
    NodeKind.EXTERNAL -> nodeExternal
    NodeKind.SYMBOL -> nodeSymbol
}

data class AtlasEdgeStyle(
    val strokeWidth: Float,
    val dashed: Boolean,
    val arrow: Boolean,
    val filledArrow: Boolean,
    val color: Color,
)

fun AtlasTheme.edgeStyle(kind: EdgeKind): AtlasEdgeStyle = when (kind) {
    EdgeKind.IMPORT -> AtlasEdgeStyle(1f, dashed = false, arrow = false, filledArrow = false, color = edgeImport)
    EdgeKind.EXTENDS -> AtlasEdgeStyle(2f, dashed = false, arrow = true, filledArrow = true, color = edgeRelation)
    EdgeKind.IMPLEMENTS -> AtlasEdgeStyle(1.5f, dashed = true, arrow = true, filledArrow = false, color = edgeRelation)
    EdgeKind.CALLS -> AtlasEdgeStyle(1f, dashed = false, arrow = true, filledArrow = true, color = edgeRelation)
}

fun DrawScope.drawAtlasRing(theme: AtlasTheme, center: Offset, rx: Float, ry: Float) {
    drawOval(
        color = theme.ring.copy(alpha = 0.1f),
        topLeft = Offset(center.x - rx, center.y - ry),
        size = Size(rx * 2f, ry * 2f),
        style = Stroke(width = 1f),
    )
}

fun DrawScope.drawAtlasEdge(
    theme: AtlasTheme,
    kind: EdgeKind,
    start: Offset,
    end: Offset,
    targetRadius: Float,
    alpha: Float,
) {
    val style = theme.edgeStyle(kind)
    val color = style.color.copy(alpha = alpha)
    drawLine(
        color = color,
        start = start,
        end = end,
        strokeWidth = style.strokeWidth,
        pathEffect = if (style.dashed) dashEffect() else null,
    )
    if (style.arrow) drawArrowHead(start, end, targetRadius, color, filled = style.filledArrow)
}

fun DrawScope.drawAtlasNode(
    theme: AtlasTheme,
    kind: NodeKind,
    center: Offset,
    radius: Float,
    alpha: Float,
) {
    val base = theme.nodeColor(kind)
    if (kind == NodeKind.ACTIVE) {
        drawCircle(base.copy(alpha = alpha * 0.18f), radius = radius * 2.4f, center = center)
        drawCircle(base.copy(alpha = alpha * 0.25f), radius = radius * 1.6f, center = center)
    }
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                lerp(base, theme.nodeHighlight, 0.55f).copy(alpha = alpha),
                base.copy(alpha = alpha),
                lerp(base, theme.nodeShadow, 0.35f).copy(alpha = alpha),
            ),
            center = center + Offset(-radius * 0.35f, -radius * 0.35f),
            radius = (radius * 1.7f).coerceAtLeast(1f),
        ),
        radius = radius,
        center = center,
    )
}

fun DrawScope.drawAtlasVcsMark(mark: VcsMark, center: Offset, radius: Float, alpha: Float) {
    drawCircle(
        color = vcsColor(mark).copy(alpha = alpha * 0.9f),
        radius = radius + 2.5f,
        center = center,
        style = Stroke(width = 1.5f),
    )
}

fun DrawScope.drawAtlasVcsImpact(impactDepth: Int, center: Offset, radius: Float, alpha: Float) {
    val impactA = alpha * if (impactDepth <= 1) 0.85f else 0.4f
    drawCircle(
        color = vcsImpactColor.copy(alpha = impactA),
        radius = radius + 2.5f,
        center = center,
        style = Stroke(width = 1f),
    )
}

fun DrawScope.drawAtlasSelectionRing(
    theme: AtlasTheme,
    center: Offset,
    radius: Float,
    hasMark: Boolean,
    alpha: Float,
) {
    val ringR = radius + if (hasMark) 5f else 3f
    drawCircle(
        color = theme.selectionRing.copy(alpha = alpha * 0.8f),
        radius = ringR,
        center = center,
        style = Stroke(width = 1.5f),
    )
}

fun DrawScope.drawAtlasLabel(
    theme: AtlasTheme,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    text: String,
    center: Offset,
    radius: Float,
    alpha: Float,
) {
    val measured = textMeasurer.measure(AnnotatedString(text), style)
    drawText(
        textLayoutResult = measured,
        color = theme.label.copy(alpha = alpha),
        topLeft = Offset(center.x - measured.size.width / 2f, center.y + radius + 3f),
    )
}

private fun dashEffect(): PathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))

private fun DrawScope.drawArrowHead(
    from: Offset,
    to: Offset,
    targetRadius: Float,
    color: Color,
    filled: Boolean,
) {
    val direction = to - from
    val length = hypot(direction.x, direction.y)
    if (length < 1f) return
    val unit = Offset(direction.x / length, direction.y / length)
    val tip = to - unit * (targetRadius + 2f)
    val base = tip - unit * 9f
    val normal = Offset(-unit.y, unit.x) * 4.5f
    val head = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(base.x + normal.x, base.y + normal.y)
        lineTo(base.x - normal.x, base.y - normal.y)
        close()
    }
    if (filled) drawPath(head, color) else drawPath(head, color, style = Stroke(width = 1.5f))
}
