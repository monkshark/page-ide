package page.atlas.graph

data class FileNeighbours(
    val usedBy: List<GraphNode>,
    val uses: List<GraphNode>,
    val cycle: List<GraphNode>,
) {
    companion object {
        val EMPTY = FileNeighbours(emptyList(), emptyList(), emptyList())
    }
}

fun neighboursOf(slice: GraphSlice, id: String): FileNeighbours {
    val local = slice.nodes.filter { it.kind == NodeKind.WORKSPACE_FILE || it.kind == NodeKind.ACTIVE }.associateBy { it.id }
    if (id !in local) return FileNeighbours.EMPTY
    val out = HashMap<String, MutableList<String>>()
    val incoming = LinkedHashSet<String>()
    val outgoing = LinkedHashSet<String>()
    for (e in slice.edges) {
        if (e.from !in local || e.to !in local || e.from == e.to) continue
        out.getOrPut(e.from) { ArrayList() }.add(e.to)
        if (e.to == id) incoming.add(e.from)
        if (e.from == id) outgoing.add(e.to)
    }
    val byName = compareBy<GraphNode> { it.label.lowercase() }
    return FileNeighbours(
        usedBy = incoming.mapNotNull { local[it] }.sortedWith(byName),
        uses = outgoing.mapNotNull { local[it] }.sortedWith(byName),
        cycle = shortestLoop(id, out).mapNotNull { local[it] },
    )
}

private fun shortestLoop(start: String, out: Map<String, List<String>>): List<String> {
    val parent = HashMap<String, String>()
    val queue = ArrayDeque<String>()
    for (next in out[start].orEmpty()) {
        if (next == start) return listOf(start, start)
        if (next !in parent) {
            parent[next] = start
            queue.addLast(next)
        }
    }
    while (queue.isNotEmpty()) {
        val node = queue.removeFirst()
        for (next in out[node].orEmpty()) {
            if (next == start) {
                val path = ArrayList<String>()
                var cur: String? = node
                while (cur != null && cur != start) {
                    path.add(cur)
                    cur = parent[cur]
                }
                return listOf(start) + path.reversed() + start
            }
            if (next !in parent) {
                parent[next] = node
                queue.addLast(next)
            }
        }
    }
    return emptyList()
}
