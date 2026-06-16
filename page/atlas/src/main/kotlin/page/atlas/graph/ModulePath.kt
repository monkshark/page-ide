package page.atlas.graph

data class ModuleLink(val node: ModuleNode, val weight: Int)

fun moduleDependsOn(graph: ModuleGraph, moduleId: String): List<ModuleLink> =
    relatedModules(graph, moduleId, outgoing = true)

fun moduleUsedBy(graph: ModuleGraph, moduleId: String): List<ModuleLink> =
    relatedModules(graph, moduleId, outgoing = false)

fun modulePath(graph: ModuleGraph, from: String, to: String): List<String>? {
    if (from == to) return listOf(from)
    val adjacency = HashMap<String, MutableList<String>>()
    for (edge in graph.edges) {
        if (edge.from == edge.to) continue
        adjacency.getOrPut(edge.from) { ArrayList() }.add(edge.to)
    }
    for (targets in adjacency.values) targets.sort()
    val previous = HashMap<String, String>()
    val visited = HashSet<String>()
    val queue = ArrayDeque<String>()
    queue.addLast(from)
    visited.add(from)
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        if (current == to) break
        for (next in adjacency[current].orEmpty()) {
            if (visited.add(next)) {
                previous[next] = current
                queue.addLast(next)
            }
        }
    }
    if (to !in visited) return null
    val path = ArrayDeque<String>()
    var cursor: String? = to
    while (cursor != null) {
        path.addFirst(cursor)
        cursor = previous[cursor]
    }
    return path.toList()
}

private fun relatedModules(graph: ModuleGraph, moduleId: String, outgoing: Boolean): List<ModuleLink> {
    val byId = graph.nodes.associateBy { it.id }
    return graph.edges
        .asSequence()
        .filter { if (outgoing) it.from == moduleId else it.to == moduleId }
        .filter { it.from != it.to }
        .mapNotNull { edge ->
            val otherId = if (outgoing) edge.to else edge.from
            byId[otherId]?.let { ModuleLink(it, edge.weight) }
        }
        .sortedWith(compareByDescending<ModuleLink> { it.weight }.thenBy { it.node.id })
        .toList()
}
