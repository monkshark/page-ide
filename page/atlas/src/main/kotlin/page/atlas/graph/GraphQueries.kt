package page.atlas.graph

object GraphQueries {

    fun merge(base: GraphSlice, overlay: GraphSlice): GraphSlice {
        if (overlay.nodes.isEmpty()) return base
        if (base.nodes.isEmpty()) return overlay
        val nodes = LinkedHashMap<String, GraphNode>()
        for (node in base.nodes) nodes[node.id] = node
        for (node in overlay.nodes) nodes.putIfAbsent(node.id, node)
        val edges = LinkedHashMap<Pair<String, String>, GraphEdge>()
        for (edge in base.edges) edges[edge.from to edge.to] = edge
        for (edge in overlay.edges) edges.putIfAbsent(edge.from to edge.to, edge)
        return GraphSlice(nodes.values.toList(), edges.values.toList())
    }
}
