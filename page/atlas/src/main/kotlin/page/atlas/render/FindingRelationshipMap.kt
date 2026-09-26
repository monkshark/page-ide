package page.atlas.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import page.atlas.graph.GraphEdge
import page.atlas.graph.GraphNode
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

@Composable
internal fun FindingRelationshipMap(
    nodes: List<GraphNode>,
    edges: List<GraphEdge>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(modifier.fillMaxWidth().height(226.dp).clip(RoundedCornerShape(12.dp))
        .background(colors.surface).border(1.dp, colors.outlineVariant.copy(alpha = .5f), RoundedCornerShape(12.dp))) {
        val shown = nodes.take(6)
        val cardWidth = ((maxWidth - 72.dp) / 3).coerceAtMost(176.dp)
        val centers = shown.mapIndexed { index, node ->
            val angle = -PI / 2 + 2 * PI * index / shown.size
            val position = when (shown.size) {
                1 -> Offset(.5f, .5f)
                2 -> Offset(if (index == 0) .24f else .76f, .5f)
                else -> Offset(.5f + .34f * cos(angle).toFloat(), .5f + .33f * sin(angle).toFloat())
            }
            node.id to position
        }.toMap()
        val shownEdges = edges.filter { it.from in centers && it.to in centers }
        Canvas(Modifier.fillMaxSize()) {
            val halfWidth = cardWidth.toPx() / 2
            val halfHeight = 24.dp.toPx()
            for (edge in shownEdges) {
                val a = centers[edge.from]?.let { Offset(it.x * size.width, it.y * size.height) } ?: continue
                val b = centers[edge.to]?.let { Offset(it.x * size.width, it.y * size.height) } ?: continue
                val tint = if (selectedId == null || edge.from == selectedId || edge.to == selectedId) colors.primary
                    else colors.outlineVariant
                if (edge.from == edge.to) {
                    drawPath(Path().apply {
                        moveTo(a.x - 18.dp.toPx(), a.y - halfHeight)
                        cubicTo(a.x - 38.dp.toPx(), a.y - 60.dp.toPx(), a.x + 38.dp.toPx(), a.y - 60.dp.toPx(), a.x + 18.dp.toPx(), a.y - halfHeight)
                    }, tint.copy(alpha = .7f), style = Stroke(1.5.dp.toPx()))
                    drawLine(tint, Offset(a.x + 18.dp.toPx(), a.y - halfHeight), Offset(a.x + 13.dp.toPx(), a.y - halfHeight - 6.dp.toPx()), 1.5.dp.toPx())
                    continue
                }
                val delta = b - a
                val ratio = minOf(halfWidth / abs(delta.x).coerceAtLeast(.01f), halfHeight / abs(delta.y).coerceAtLeast(.01f))
                val start = a + delta * ratio
                val end = b - delta * ratio
                val midpoint = (start + end) / 2f
                val unit = delta / delta.getDistance()
                val normal = Offset(-unit.y, unit.x)
                val bend = 9.dp.toPx()
                val control = midpoint + normal * bend
                drawPath(Path().apply {
                    moveTo(start.x, start.y)
                    quadraticTo(control.x, control.y, end.x, end.y)
                }, tint.copy(alpha = .65f), style = Stroke(1.5.dp.toPx()))
                val arrow = midpoint + normal * (bend / 2)
                drawLine(tint, arrow - unit * 5.dp.toPx() + normal * 3.dp.toPx(), arrow + unit * 2.dp.toPx(), 1.5.dp.toPx())
                drawLine(tint, arrow - unit * 5.dp.toPx() - normal * 3.dp.toPx(), arrow + unit * 2.dp.toPx(), 1.5.dp.toPx())
            }
        }
        shown.forEach { node ->
            val position = centers.getValue(node.id)
            val selected = node.id == selectedId
            Box(Modifier.offset(maxWidth * position.x - cardWidth / 2, 226.dp * position.y - 24.dp)
                .width(cardWidth).height(48.dp).clip(RoundedCornerShape(8.dp))
                .background(if (selected) colors.primaryContainer else colors.background)
                .border(1.dp, if (selected) colors.primary else colors.outlineVariant, RoundedCornerShape(8.dp))
                .semantics { this.selected = selected; contentDescription = "Filter connections for ${node.label}" }
                .clickable(role = Role.Button) { onSelect(node.id) }.padding(horizontal = 9.dp),
                contentAlignment = Alignment.Center) {
                Text(node.label, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                    color = if (selected) colors.onPrimaryContainer else colors.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
