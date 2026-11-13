package page.atlas.graph

data class Need(val node: GraphNode, val line: Int?)

data class FileMargins(
    val needs: List<Need>,
    val usedBy: List<GraphNode>,
    val cycle: List<GraphNode>,
) {
    val cycleLine: Int?
        get() = cycle.getOrNull(1)?.let { next -> needs.firstOrNull { it.node.id == next.id }?.line }

    fun asNeighbours(): FileNeighbours = FileNeighbours(usedBy, needs.map { it.node }, cycle)

    companion object {
        val EMPTY = FileMargins(emptyList(), emptyList(), emptyList())
    }
}

fun marginsOf(slice: GraphSlice, id: String, usedBy: List<GraphNode>): FileMargins {
    val byId = slice.nodes.associateBy { it.id }
    val needs = slice.edges
        .filter { it.from == id && it.to != id }
        .mapNotNull { edge -> byId[edge.to]?.takeIf { it.kind == NodeKind.WORKSPACE_FILE }?.let { Need(it, edge.evidence?.line) } }
        .distinctBy { it.node.id }
        .sortedWith(compareBy<Need> { it.line ?: Int.MAX_VALUE }.thenBy { it.node.label.lowercase() })
    return FileMargins(needs, usedBy, neighboursOf(slice, id).cycle)
}
