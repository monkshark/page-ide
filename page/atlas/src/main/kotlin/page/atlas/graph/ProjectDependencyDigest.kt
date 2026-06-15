package page.atlas.graph

const val HUB_DEPENDENTS_MIN = 8

data class FileRole(
    val dependents: Int,
    val isHub: Boolean,
    val inCycle: Boolean,
    val truncated: Boolean = false,
)

data class DependencyDigest(
    val dependents: Map<String, Int>,
    val cycleMembers: Set<String>,
    val hubIds: Set<String>,
    val truncated: Boolean = false,
) {
    fun roleOf(id: String): FileRole = FileRole(
        dependents = dependents[id] ?: 0,
        isHub = id in hubIds,
        inCycle = id in cycleMembers,
        truncated = truncated,
    )

    companion object {
        val EMPTY = DependencyDigest(emptyMap(), emptySet(), emptySet())
    }
}

fun dependencyDigest(slice: GraphSlice, truncated: Boolean = false): DependencyDigest {
    if (slice.nodes.isEmpty()) return DependencyDigest.EMPTY.copy(truncated = truncated)
    val indegree = HashMap<String, Int>()
    for (edge in slice.edges) {
        if (edge.from == edge.to) continue
        indegree.merge(edge.to, 1, Int::plus)
    }
    val dependents = slice.nodes.associate { it.id to (indegree[it.id] ?: 0) }
    val hubIds = dependents.asSequence().filter { it.value >= HUB_DEPENDENTS_MIN }.map { it.key }.toHashSet()
    return DependencyDigest(dependents, cycleMembersOf(slice.edges), hubIds, truncated)
}

private fun cycleMembersOf(edges: List<GraphEdge>): Set<String> {
    if (edges.isEmpty()) return emptySet()
    val nodes = LinkedHashSet<String>()
    val adj = HashMap<String, MutableList<String>>()
    val radj = HashMap<String, MutableList<String>>()
    val selfLoops = HashSet<String>()
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
    val compSize = HashMap<Int, Int>()
    var compId = 0
    for (start in order.asReversed()) {
        if (start in comp) continue
        var size = 0
        val stack = ArrayDeque<String>()
        stack.addLast(start)
        comp[start] = compId
        while (stack.isNotEmpty()) {
            val v = stack.removeLast()
            size++
            for (w in radj[v].orEmpty()) {
                if (w !in comp) {
                    comp[w] = compId
                    stack.addLast(w)
                }
            }
        }
        compSize[compId] = size
        compId++
    }
    val out = HashSet<String>(selfLoops)
    for (node in nodes) {
        val c = comp[node]
        if (c != null && (compSize[c] ?: 0) > 1) out += node
    }
    return out
}
