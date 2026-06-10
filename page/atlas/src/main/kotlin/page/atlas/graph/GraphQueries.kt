package page.atlas.graph

import kotlin.math.min

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

    fun cycles(slice: GraphSlice): List<List<GraphNode>> {
        val nodesById = slice.nodes.associateBy { it.id }
        val adjacency = HashMap<String, MutableList<String>>()
        val selfLoops = HashSet<String>()
        for (edge in slice.edges) {
            if (edge.from !in nodesById || edge.to !in nodesById) continue
            if (edge.from == edge.to) {
                selfLoops += edge.from
                continue
            }
            adjacency.getOrPut(edge.from) { mutableListOf() }.add(edge.to)
        }
        val index = HashMap<String, Int>()
        val low = HashMap<String, Int>()
        val onStack = HashSet<String>()
        val stack = ArrayDeque<String>()
        var counter = 0
        val result = ArrayList<List<GraphNode>>()
        for (start in slice.nodes) {
            if (start.id in index) continue
            val work = ArrayDeque<Pair<String, Int>>()
            work.addLast(start.id to 0)
            while (work.isNotEmpty()) {
                val (v, childStart) = work.removeLast()
                if (childStart == 0) {
                    index[v] = counter
                    low[v] = counter
                    counter++
                    stack.addLast(v)
                    onStack += v
                }
                val children = adjacency[v].orEmpty()
                var descended = false
                var i = childStart
                while (i < children.size) {
                    val w = children[i]
                    if (w !in index) {
                        work.addLast(v to i + 1)
                        work.addLast(w to 0)
                        descended = true
                        break
                    }
                    if (w in onStack) low[v] = min(low[v]!!, index[w]!!)
                    i++
                }
                if (descended) continue
                if (low[v] == index[v]) {
                    val component = ArrayList<String>()
                    while (true) {
                        val w = stack.removeLast()
                        onStack -= w
                        component += w
                        if (w == v) break
                    }
                    if (component.size > 1 || component[0] in selfLoops) {
                        result += component.asReversed().mapNotNull { nodesById[it] }
                    }
                }
                work.lastOrNull()?.let { (parent, _) -> low[parent] = min(low[parent]!!, low[v]!!) }
            }
        }
        return result
    }
}
