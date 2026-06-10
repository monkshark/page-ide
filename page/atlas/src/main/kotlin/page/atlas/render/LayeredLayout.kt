package page.atlas.render

import kotlin.math.max
import page.atlas.graph.GraphSlice

data class NodePos(val x: Float, val y: Float)

fun layeredLayout(slice: GraphSlice, width: Float, height: Float): Map<String, NodePos> {
    if (slice.nodes.isEmpty()) return emptyMap()
    val ids = slice.nodes.map { it.id }
    val deps = LinkedHashMap<String, MutableList<String>>()
    for (edge in slice.edges) {
        if (edge.from != edge.to) deps.getOrPut(edge.from) { mutableListOf() }.add(edge.to)
    }
    val layer = assignLayers(ids, deps)
    val maxLayer = layer.values.maxOrNull() ?: 0
    val byLayer = ids.groupBy { layer[it] ?: 0 }
    val normX = HashMap<String, Float>()
    for (l in 0..maxLayer) {
        val row = byLayer[l].orEmpty()
        val ordered = if (l == 0) row else row.sortedBy { id ->
            val below = deps[id].orEmpty().mapNotNull { normX[it] }
            if (below.isEmpty()) 0.5f else below.sum() / below.size
        }
        ordered.forEachIndexed { i, id -> normX[id] = (i + 1f) / (ordered.size + 1f) }
    }
    val usableHeight = (height - TOP_MARGIN - BOTTOM_MARGIN).coerceAtLeast(1f)
    val positions = LinkedHashMap<String, NodePos>()
    for (node in slice.nodes) {
        val l = layer[node.id] ?: 0
        val y =
            if (maxLayer == 0) height / 2f
            else TOP_MARGIN + usableHeight * (1f - l.toFloat() / maxLayer)
        val rowCount = byLayer[l]?.size ?: 1
        val rowWidth = max(width, MIN_SPACING * (rowCount + 1))
        val x = width / 2f + rowWidth * ((normX[node.id] ?: 0.5f) - 0.5f)
        positions[node.id] = NodePos(x, y)
    }
    return positions
}

private fun assignLayers(ids: List<String>, deps: Map<String, List<String>>): Map<String, Int> {
    val layer = HashMap<String, Int>()
    val visiting = HashSet<String>()
    fun assign(id: String): Int {
        layer[id]?.let { return it }
        if (!visiting.add(id)) return 0
        var maxBelow = -1
        for (dep in deps[id].orEmpty()) {
            maxBelow = max(maxBelow, assign(dep))
        }
        visiting.remove(id)
        val value = maxBelow + 1
        layer[id] = value
        return value
    }
    for (id in ids) assign(id)
    return layer
}

private const val TOP_MARGIN = 48f
private const val BOTTOM_MARGIN = 64f
private const val MIN_SPACING = 96f
