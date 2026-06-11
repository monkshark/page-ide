package page.atlas.render

fun mapCycleEdges(edges: List<MapEdge>): Set<Pair<String, String>> {
    val nodes = LinkedHashSet<String>()
    val adj = HashMap<String, MutableList<String>>()
    val radj = HashMap<String, MutableList<String>>()
    for (edge in edges) {
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
    val out = HashSet<Pair<String, String>>()
    for (edge in edges) {
        if (edge.from == edge.to) {
            out += edge.from to edge.to
            continue
        }
        val c = comp[edge.from]
        if (c != null && c == comp[edge.to] && (compSize[c] ?: 0) > 1) out += edge.from to edge.to
    }
    return out
}
