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

    fun findPath(edges: List<GraphEdge>, fromId: String, toId: String): List<GraphEdge>? {
        if (fromId == toId) return emptyList()
        val outgoing = HashMap<String, MutableList<GraphEdge>>()
        for (edge in edges) outgoing.getOrPut(edge.from) { mutableListOf() }.add(edge)
        val prevEdge = HashMap<String, GraphEdge>()
        val visited = hashSetOf(fromId)
        var frontier = listOf(fromId)
        while (frontier.isNotEmpty()) {
            val next = mutableListOf<String>()
            for (id in frontier) {
                for (edge in outgoing[id].orEmpty()) {
                    if (!visited.add(edge.to)) continue
                    prevEdge[edge.to] = edge
                    if (edge.to == toId) {
                        val path = ArrayList<GraphEdge>()
                        var cursor = toId
                        while (cursor != fromId) {
                            val step = prevEdge.getValue(cursor)
                            path += step
                            cursor = step.from
                        }
                        return path.asReversed()
                    }
                    next += edge.to
                }
            }
            frontier = next
        }
        return null
    }
}
