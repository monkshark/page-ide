package page.atlas.render

import page.atlas.graph.GraphSlice

data class MapFilterState(
    val focusDir: String? = null,
    val hiddenDirs: Set<String> = emptySet(),
    val mutedDirs: Set<String> = emptySet(),
) {
    val active: Boolean get() = focusDir != null || hiddenDirs.isNotEmpty() || mutedDirs.isNotEmpty()
}

internal fun filterForMap(slice: GraphSlice, filter: MapFilterState): GraphSlice {
    if (!filter.active) return slice
    val focus = filter.focusDir
    val nodes = slice.nodes.filter { node ->
        (focus == null || belongsTo(node.id, focus)) &&
            filter.hiddenDirs.none { belongsTo(node.id, it) }
    }
    val ids = nodes.mapTo(HashSet()) { it.id }
    val edges = slice.edges.filter { edge ->
        edge.from in ids && edge.to in ids &&
            filter.mutedDirs.none { belongsTo(edge.from, it) || belongsTo(edge.to, it) }
    }
    return GraphSlice(nodes, edges)
}
