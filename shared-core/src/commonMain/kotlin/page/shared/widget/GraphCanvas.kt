package page.shared.widget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import page.shared.graph.GraphInsights
import page.shared.graph.GraphNode
import page.shared.graph.GraphSlice
import page.shared.graph.NodeKind

data class GraphColors(
    val edge: Color,
    val edgeStrong: Color,
    val active: Color,
    val workspace: Color,
    val external: Color,
    val focus: Color,
    val label: Color,
)

@Composable
fun GraphCanvas(slice: GraphSlice, colors: GraphColors, modifier: Modifier = Modifier) {
    val world = remember(slice) { layout(slice.nodes) }
    val worldRadius = remember(world) { (world.values.maxOfOrNull { sqrt(it.x * it.x + it.y * it.y) } ?: 1f).coerceAtLeast(1f) }
    val measurer = rememberTextMeasurer()

    var pan by remember(slice) { mutableStateOf(Offset.Zero) }
    var zoom by remember(slice) { mutableStateOf(1f) }
    var hovered by remember(slice) { mutableStateOf<String?>(null) }

    val neighborhood = hovered?.let { GraphInsights.neighborhood(slice, it, limit = 1000) }
    val lit = buildSet {
        val f = hovered
        if (f != null && neighborhood?.focus != null) {
            add(f)
            neighborhood.incoming.forEach { add(it.node.id) }
            neighborhood.outgoing.forEach { add(it.node.id) }
        }
    }

    Canvas(
        modifier = modifier
            .pointerInput(slice) {
                detectDragGestures { _, delta -> pan += delta }
            }
            .pointerInput(slice) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        when (event.type) {
                            PointerEventType.Scroll -> {
                                val dy = change.scrollDelta.y
                                if (dy != 0f) {
                                    zoom = (zoom * if (dy > 0f) 0.9f else 1.1f).coerceIn(0.35f, 3.5f)
                                    change.consume()
                                }
                            }
                            PointerEventType.Move -> {
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val fit = minOf(size.width, size.height) * 0.42f / worldRadius * zoom
                                hovered = nearest(change.position, world, center, pan, fit)
                            }
                            else -> {}
                        }
                    }
                }
            },
    ) {
        if (world.isEmpty()) return@Canvas
        val center = Offset(size.width / 2f, size.height / 2f)
        val fit = minOf(size.width, size.height) * 0.42f / worldRadius * zoom
        fun screen(id: String): Offset? = world[id]?.let { center + pan + Offset(it.x * fit, it.y * fit) }

        val dim = hovered != null
        for (edge in slice.edges) {
            val a = screen(edge.from) ?: continue
            val b = screen(edge.to) ?: continue
            val strong = hovered != null && (edge.from == hovered || edge.to == hovered)
            val color = when {
                strong -> colors.edgeStrong
                dim -> colors.edge.copy(alpha = colors.edge.alpha * 0.25f)
                else -> colors.edge
            }
            drawLine(color, a, b, strokeWidth = if (strong) 1.8f else 1f)
        }

        val r = (7f * zoom).coerceIn(3.5f, 22f)
        for (node in slice.nodes) {
            val p = screen(node.id) ?: continue
            val isLit = node.id in lit
            val base = nodeColor(node.kind, colors)
            val color = when {
                dim && isLit && node.id == hovered -> colors.focus
                dim && isLit -> base
                dim -> base.copy(alpha = base.alpha * 0.22f)
                else -> base
            }
            drawCircle(color, radius = r, center = p)
            if (isLit) {
                drawText(
                    measurer,
                    node.label,
                    topLeft = Offset(p.x + r + 4f, p.y - 7f),
                    style = TextStyle(color = colors.label, fontSize = 11.sp),
                )
            }
        }
    }
}

private fun nodeColor(kind: NodeKind, colors: GraphColors): Color = when (kind) {
    NodeKind.ACTIVE -> colors.active
    NodeKind.EXTERNAL -> colors.external
    else -> colors.workspace
}

private fun nearest(
    pointer: Offset,
    world: Map<String, Offset>,
    center: Offset,
    pan: Offset,
    fit: Float,
): String? {
    var best: String? = null
    var bestDist = 18f
    for ((id, w) in world) {
        val screen = center + pan + Offset(w.x * fit, w.y * fit)
        val d = (screen - pointer).getDistance()
        if (d < bestDist) {
            bestDist = d
            best = id
        }
    }
    return best
}

private fun layout(nodes: List<GraphNode>): Map<String, Offset> {
    val golden = 2.399963229728653f
    val out = LinkedHashMap<String, Offset>(nodes.size)
    nodes.forEachIndexed { i, node ->
        val radius = sqrt(i + 0.5f)
        val angle = i * golden
        out[node.id] = Offset(radius * cos(angle), radius * sin(angle))
    }
    return out
}
