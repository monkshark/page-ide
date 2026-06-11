package page.atlas.render

import java.nio.file.Path
import page.atlas.graph.GraphEdge
import page.atlas.graph.GraphNode

data class DrillEntry(
    val id: String,
    val label: String,
    val path: Path?,
    val counterparts: List<String>,
)

data class MapDrilldown(val usedBy: List<DrillEntry>, val uses: List<DrillEntry>) {
    val any: Boolean get() = usedBy.isNotEmpty() || uses.isNotEmpty()

    companion object {
        val EMPTY = MapDrilldown(emptyList(), emptyList())
    }
}

fun mapDrilldown(nodes: List<GraphNode>, edges: List<GraphEdge>, selectedId: String?): MapDrilldown {
    if (selectedId == null) return MapDrilldown.EMPTY
    val byId = nodes.associateBy { it.id }
    val usedBy = LinkedHashMap<String, Pair<GraphNode, MutableList<String>>>()
    val uses = LinkedHashMap<String, Pair<GraphNode, MutableList<String>>>()
    for (edge in edges) {
        val fromIn = belongsTo(edge.from, selectedId)
        val toIn = belongsTo(edge.to, selectedId)
        if (fromIn == toIn) continue
        val bucket = if (toIn) usedBy else uses
        val outsideId = if (toIn) edge.from else edge.to
        val insideId = if (toIn) edge.to else edge.from
        val outside = byId[outsideId] ?: continue
        if (outside.path == null) continue
        val inside = byId[insideId] ?: continue
        val entry = bucket.getOrPut(outside.id) { outside to mutableListOf() }
        if (inside.label !in entry.second) entry.second += inside.label
    }
    fun finish(bucket: Map<String, Pair<GraphNode, MutableList<String>>>): List<DrillEntry> =
        bucket.values
            .map { (node, vias) -> DrillEntry(node.id, node.label, node.path, vias.sorted()) }
            .sortedBy { it.label.lowercase() }
    return MapDrilldown(finish(usedBy), finish(uses))
}
