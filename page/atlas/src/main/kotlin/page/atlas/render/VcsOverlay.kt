package page.atlas.render

import androidx.compose.ui.graphics.Color
import java.nio.file.Path
import page.atlas.graph.GraphEdge
import page.atlas.graph.GraphNode

data class DrillEntry(
    val id: String,
    val label: String,
    val path: Path?,
    val counterparts: List<String>,
)

internal fun belongsTo(id: String, ancestorId: String): Boolean =
    id == ancestorId ||
        (id.length > ancestorId.length && id.startsWith(ancestorId) &&
            (id[ancestorId.length] == '\\' || id[ancestorId.length] == '/'))

enum class VcsMark { MODIFIED, ADDED, DELETED }

val vcsImpactColor = Color(0xFFCC7832)

fun vcsColor(mark: VcsMark): Color = when (mark) {
    VcsMark.MODIFIED -> Color(0xFF6897BB)
    VcsMark.ADDED -> Color(0xFF629755)
    VcsMark.DELETED -> Color(0xFF808080)
}

fun vcsFolderCounts(marks: Map<String, VcsMark>, folderIds: Collection<String>): Map<String, Int> {
    if (marks.isEmpty() || folderIds.isEmpty()) return emptyMap()
    val result = HashMap<String, Int>()
    for (folder in folderIds) {
        val count = marks.keys.count { it != folder && belongsTo(it, folder) }
        if (count > 0) result[folder] = count
    }
    return result
}

fun vcsImpacted(edges: List<GraphEdge>, changedIds: Set<String>, maxDepth: Int = 2): Map<String, Int> {
    if (changedIds.isEmpty() || edges.isEmpty()) return emptyMap()
    val dependents = HashMap<String, MutableList<String>>()
    for (edge in edges) dependents.getOrPut(edge.to) { mutableListOf() }.add(edge.from)
    val distance = LinkedHashMap<String, Int>()
    var frontier: List<String> = changedIds.toList()
    var depth = 0
    while (frontier.isNotEmpty() && depth < maxDepth) {
        depth += 1
        val next = mutableListOf<String>()
        for (id in frontier) {
            for (dependent in dependents[id].orEmpty()) {
                if (dependent in changedIds || dependent in distance) continue
                distance[dependent] = depth
                next += dependent
            }
        }
        frontier = next
    }
    return distance
}

fun vcsImpactEntries(nodes: List<GraphNode>, impacted: Map<String, Int>): List<DrillEntry> =
    nodes.asSequence()
        .mapNotNull { node ->
            val depth = impacted[node.id] ?: return@mapNotNull null
            depth to DrillEntry(node.id, node.label, node.path, emptyList())
        }
        .sortedWith(compareBy({ it.first }, { it.second.label.lowercase() }))
        .map { it.second }
        .toList()
