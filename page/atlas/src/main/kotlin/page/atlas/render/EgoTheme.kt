package page.atlas.render

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import page.ui.Glass

data class EgoTheme(
    val canvas: Color,
    val focus: Color,
    val dependent: Color,
    val importNode: Color,
    val external: Color,
    val edgeDependent: Color,
    val edgeImport: Color,
    val edgeExternal: Color,
    val highlight: Color,
    val shadow: Color,
    val label: Color,
    val grid: Color,
)

@Composable
fun rememberEgoTheme(): EgoTheme {
    val colors = Glass.colors
    return remember(colors) {
        EgoTheme(
            canvas = Color(0xFF0A0D14),
            focus = colors.primary,
            dependent = Color(0xFF6E8BFF),
            importNode = Color(0xFF4FD3C7),
            external = Color(0xFF6B7689),
            edgeDependent = Color(0xFF6E8BFF),
            edgeImport = Color(0xFF4FD3C7),
            edgeExternal = Color(0xFF6B7689),
            highlight = Color.White,
            shadow = Color.Black,
            label = colors.muted,
            grid = Color(0xFF1B2233),
        )
    }
}

fun EgoTheme.columnColor(column: EgoColumn): Color = when (column) {
    EgoColumn.FOCUS -> focus
    EgoColumn.DEPENDENT -> dependent
    EgoColumn.IMPORT -> importNode
    EgoColumn.EXTERNAL -> external
}

fun EgoTheme.edgeColor(toFocus: Boolean, targetColumn: EgoColumn): Color = when {
    toFocus -> edgeDependent
    targetColumn == EgoColumn.EXTERNAL -> edgeExternal
    else -> edgeImport
}

fun egoEdgePath(edge: EgoEdge): Path = Path().apply {
    moveTo(edge.start.x, edge.start.y)
    cubicTo(edge.c1.x, edge.c1.y, edge.c2.x, edge.c2.y, edge.end.x, edge.end.y)
}

fun DrawScope.drawEgoBackground(theme: EgoTheme, gridStep: Float = 34f, dotRadius: Float = 1.1f) {
    drawRect(color = theme.canvas, size = size)
    val grid = theme.grid.copy(alpha = 0.5f)
    var y = gridStep / 2f
    while (y < size.height) {
        var x = gridStep / 2f
        while (x < size.width) {
            drawCircle(color = grid, radius = dotRadius, center = Offset(x, y))
            x += gridStep
        }
        y += gridStep
    }
}

fun DrawScope.drawEgoTerritoryBlob(center: Offset, color: Color, radius: Float, alpha: Float = 0.12f) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), Color.Transparent),
            center = center,
            radius = radius.coerceAtLeast(1f),
        ),
        radius = radius,
        center = center,
    )
}

fun DrawScope.drawEgoEdge(path: Path, color: Color, alpha: Float) {
    drawPath(path, color.copy(alpha = alpha * 0.2f), style = Stroke(width = 5f))
    drawPath(path, color.copy(alpha = alpha * 0.55f), style = Stroke(width = 1.7f))
}

fun DrawScope.drawEgoArrowHead(end: Offset, color: Color, alpha: Float, size: Float = 7f) {
    val tip = end
    val base = Offset(tip.x - size, tip.y)
    val head = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(base.x, base.y - size * 0.55f)
        lineTo(base.x, base.y + size * 0.55f)
        close()
    }
    drawPath(head, color.copy(alpha = alpha))
}

fun DrawScope.drawEgoDisc(theme: EgoTheme, column: EgoColumn, center: Offset, radius: Float, alpha: Float) {
    val base = theme.columnColor(column)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(base.copy(alpha = alpha * 0.3f), Color.Transparent),
            center = center,
            radius = radius * 1.9f,
        ),
        radius = radius * 1.9f,
        center = center,
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                lerp(base, theme.highlight, 0.5f).copy(alpha = alpha),
                base.copy(alpha = alpha),
                lerp(base, theme.shadow, 0.35f).copy(alpha = alpha),
            ),
            center = center + Offset(-radius * 0.35f, -radius * 0.35f),
            radius = (radius * 1.6f).coerceAtLeast(1f),
        ),
        radius = radius,
        center = center,
    )
    drawCircle(
        color = lerp(base, theme.highlight, 0.3f).copy(alpha = alpha * 0.8f),
        radius = radius,
        center = center,
        style = Stroke(width = 1.4f),
    )
    drawOval(
        color = theme.highlight.copy(alpha = alpha * 0.5f),
        topLeft = Offset(center.x - radius * 0.42f, center.y - radius * 0.6f),
        size = Size(radius * 0.6f, radius * 0.34f),
    )
}

fun DrawScope.drawEgoFocusRing(theme: EgoTheme, center: Offset, radius: Float, alpha: Float) {
    drawCircle(
        color = theme.focus.copy(alpha = alpha * 0.45f),
        radius = radius + 12f,
        center = center,
        style = Stroke(width = 1.4f),
    )
}

fun DrawScope.drawEgoFlowLabel(
    textMeasurer: TextMeasurer,
    titleStyle: TextStyle,
    subtitleStyle: TextStyle,
    title: String,
    subtitle: String,
    color: Color,
    topCenter: Offset,
) {
    val titleLayout = textMeasurer.measure(AnnotatedString(title), titleStyle)
    drawText(
        textLayoutResult = titleLayout,
        color = color,
        topLeft = Offset(topCenter.x - titleLayout.size.width / 2f, topCenter.y),
    )
    val subLayout = textMeasurer.measure(AnnotatedString(subtitle), subtitleStyle)
    drawText(
        textLayoutResult = subLayout,
        color = color.copy(alpha = 0.6f),
        topLeft = Offset(topCenter.x - subLayout.size.width / 2f, topCenter.y + titleLayout.size.height + 2f),
    )
}

fun DrawScope.drawEgoMinimap(theme: EgoTheme, model: EgoModel, area: Rect, viewport: Rect) {
    drawRect(
        color = theme.canvas.copy(alpha = 0.85f),
        topLeft = area.topLeft,
        size = area.size,
    )
    drawRect(
        color = theme.grid.copy(alpha = 0.8f),
        topLeft = area.topLeft,
        size = area.size,
        style = Stroke(width = 1f),
    )
    if (model.width <= 0f || model.height <= 0f) return
    val sx = area.width / model.width
    val sy = area.height / model.height
    for (n in model.nodes) {
        drawCircle(
            color = theme.columnColor(n.column).copy(alpha = 0.8f),
            radius = (n.radius * sx).coerceIn(1f, 4f),
            center = Offset(area.left + n.center.x * sx, area.top + n.center.y * sy),
        )
    }
    drawRect(
        color = theme.focus.copy(alpha = 0.7f),
        topLeft = Offset(area.left + viewport.left * sx, area.top + viewport.top * sy),
        size = Size(viewport.width * sx, viewport.height * sy),
        style = Stroke(width = 1f),
    )
}
