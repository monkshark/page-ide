package page.shared.graph

data class Neighbor(val node: GraphNode, val weight: Int)

data class Neighborhood(
    val focus: GraphNode?,
    val incoming: List<Neighbor>,
    val outgoing: List<Neighbor>,
    val incomingTotal: Int,
    val outgoingTotal: Int,
    val inCycle: Boolean,
)

object GraphInsights {

    fun neighborhood(slice: GraphSlice, focusId: String, limit: Int = 6): Neighborhood {
        val byId = slice.nodes.associateBy { it.id }
        val focus = byId[focusId]
            ?: return Neighborhood(null, emptyList(), emptyList(), 0, 0, false)
        val indegree = indegrees(slice.edges)
        val incomingIds = LinkedHashSet<String>()
        val outgoingIds = LinkedHashSet<String>()
        for (edge in slice.edges) {
            if (edge.from == edge.to) continue
            if (edge.to == focusId) incomingIds += edge.from
            if (edge.from == focusId) outgoingIds += edge.to
        }
        fun neighbors(ids: Set<String>): List<Neighbor> =
            ids.mapNotNull { byId[it] }
                .map { Neighbor(it, indegree[it.id] ?: 0) }
                .sortedWith(
                    compareByDescending<Neighbor> { it.weight }
                        .thenBy { it.node.label.lowercase() }
                        .thenBy { it.node.id },
                )
        val incoming = neighbors(incomingIds)
        val outgoing = neighbors(outgoingIds)
        val inCycle = cycles(slice.edges).any { focusId in it }
        return Neighborhood(
            focus = focus,
            incoming = incoming.take(limit),
            outgoing = outgoing.take(limit),
            incomingTotal = incoming.size,
            outgoingTotal = outgoing.size,
            inCycle = inCycle,
        )
    }

    fun indegrees(edges: List<GraphEdge>): Map<String, Int> {
        val seen = HashSet<Pair<String, String>>()
        val indegree = HashMap<String, Int>()
        for (edge in edges) {
            if (edge.from == edge.to) continue
            if (!seen.add(edge.from to edge.to)) continue
            indegree.merge(edge.to, 1, Int::plus)
        }
        return indegree
    }

    fun cycles(edges: List<GraphEdge>): List<List<String>> {
        val nodes = LinkedHashSet<String>()
        val adj = HashMap<String, MutableList<String>>()
        val radj = HashMap<String, MutableList<String>>()
        val selfLoops = LinkedHashSet<String>()
        for (edge in edges) {
            nodes += edge.from
            nodes += edge.to
            if (edge.from == edge.to) {
                selfLoops += edge.from
                continue
            }
            adj.getOrPut(edge.from) { mutableListOf() } += edge.to
            radj.getOrPut(edge.to) { mutableListOf() } += edge.from
        }
        val visited = HashSet<String>()
        val order = ArrayList<String>(nodes.size)
        for (start in nodes) {
            if (!visited.add(start)) continue
            val stack = ArrayDeque<Pair<String, Int>>()
            stack.addLast(start to 0)
            while (stack.isNotEmpty()) {
                val (v, i) = stack.removeLast()
                val children = adj[v].orEmpty()
                if (i < children.size) {
                    stack.addLast(v to i + 1)
                    val w = children[i]
                    if (visited.add(w)) stack.addLast(w to 0)
                } else {
                    order += v
                }
            }
        }
        val comp = HashMap<String, Int>()
        val members = LinkedHashMap<Int, MutableList<String>>()
        var compId = 0
        for (start in order.asReversed()) {
            if (start in comp) continue
            val group = mutableListOf(start)
            comp[start] = compId
            val stack = ArrayDeque<String>()
            stack.addLast(start)
            while (stack.isNotEmpty()) {
                val v = stack.removeLast()
                for (w in radj[v].orEmpty()) {
                    if (w !in comp) {
                        comp[w] = compId
                        group += w
                        stack.addLast(w)
                    }
                }
            }
            members[compId] = group
            compId++
        }
        val grouped = HashSet<String>()
        val result = ArrayList<List<String>>()
        for (group in members.values) {
            if (group.size > 1) {
                result += group.sorted()
                grouped += group
            }
        }
        for (id in selfLoops) {
            if (grouped.add(id)) result += listOf(id)
        }
        return result.sortedBy { it.first() }
    }
}
