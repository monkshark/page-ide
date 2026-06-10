package page.atlas.render

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import page.atlas.graph.GraphSlice
import page.atlas.graph.NodeKind

data class NodePos(val x: Float, val y: Float)

fun radialLayout(slice: GraphSlice, width: Float, height: Float): Map<String, NodePos> {
    if (slice.nodes.isEmpty()) return emptyMap()
    val cx = width / 2f
    val cy = height / 2f
    val positions = LinkedHashMap<String, NodePos>()
    val ring = slice.nodes.filter { it.kind != NodeKind.ACTIVE }
    for (node in slice.nodes) {
        if (node.kind == NodeKind.ACTIVE) positions[node.id] = NodePos(cx, cy)
    }
    if (ring.isEmpty()) return positions
    val radius = (min(width, height) / 2f - 48f).coerceAtLeast(32f)
    ring.forEachIndexed { i, node ->
        val angle = (i.toDouble() / ring.size) * 2.0 * PI - PI / 2.0
        positions[node.id] = NodePos(
            cx + radius * cos(angle).toFloat(),
            cy + radius * sin(angle).toFloat(),
        )
    }
    return positions
}
