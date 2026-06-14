package page.atlas.render

import page.atlas.graph.GraphEdge
import page.atlas.graph.GraphNode

enum class VcsMark { MODIFIED, ADDED, DELETED }

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
