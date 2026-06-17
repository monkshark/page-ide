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
    val cycleGroups: List<List<GraphNode>> = emptyList(),
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
    val byId = slice.nodes.associateBy { it.id }
    val cycleMembers = slice.edges.filter { it.from == it.to }.mapTo(HashSet()) { it.from }
    val cycleGroups = ArrayList<List<GraphNode>>()
    for (component in stronglyConnectedComponents(slice.edges)) {
        if (component.size <= 1) continue
        cycleMembers += component
        cycleGroups += component.mapNotNull { byId[it] }
    }
    return DependencyDigest(
        dependents = dependents,
        cycleMembers = cycleMembers,
        hubIds = hubIds,
        cycleGroups = cycleGroups,
        truncated = truncated,
    )
}

private fun stronglyConnectedComponents(edges: List<GraphEdge>): List<List<String>> {
    if (edges.isEmpty()) return emptyList()
    val nodes = LinkedHashSet<String>()
    val adj = HashMap<String, MutableList<String>>()
    val radj = HashMap<String, MutableList<String>>()
    for (edge in edges) {
        if (edge.from == edge.to) continue
        nodes += edge.from
        nodes += edge.to
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
    val groups = ArrayList<MutableList<String>>()
    for (start in order.asReversed()) {
        if (start in comp) continue
        val id = groups.size
        val members = ArrayList<String>()
        val stack = ArrayDeque<String>()
        stack.addLast(start)
        comp[start] = id
        while (stack.isNotEmpty()) {
            val v = stack.removeLast()
            members += v
            for (w in radj[v].orEmpty()) {
                if (w !in comp) {
                    comp[w] = id
                    stack.addLast(w)
                }
            }
        }
        groups += members
    }
    return groups
}
