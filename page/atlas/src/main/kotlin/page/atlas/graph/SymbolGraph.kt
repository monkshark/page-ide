package page.atlas.graph

import java.net.URI
import java.nio.file.Path

data class SymbolSpec(
    val name: String,
    val detail: String?,
    val uri: String,
    val line: Int,
    val character: Int = 0,
    val handle: Any? = null,
)

fun symbolNodeId(symbol: SymbolSpec): String =
    "${symbol.uri}#${symbol.name}@${symbol.line}"

interface CallHierarchySource {
    fun incoming(symbol: SymbolSpec): List<SymbolSpec>
    fun outgoing(symbol: SymbolSpec): List<SymbolSpec>
}

class SymbolGraphSession(
    private val source: CallHierarchySource,
    private val maxDepth: Int = MAX_DEPTH,
    private val maxNodes: Int = MAX_NODES,
) {
    private val nodes = LinkedHashMap<String, GraphNode>()
    private val edges = LinkedHashMap<Pair<String, String>, GraphEdge>()
    private val specs = HashMap<String, SymbolSpec>()
    private val depths = HashMap<String, Int>()
    private val expanded = HashSet<String>()

    var rootId: String? = null
        private set

    val slice: GraphSlice
        get() = GraphSlice(nodes.values.toList(), edges.values.toList())

    fun start(root: SymbolSpec): GraphSlice {
        nodes.clear()
        edges.clear()
        specs.clear()
        depths.clear()
        expanded.clear()
        val id = symbolNodeId(root)
        rootId = id
        register(root, id, 0)
        return expand(id)
    }

    fun symbolAt(nodeId: String): SymbolSpec? = specs[nodeId]

    fun canExpand(nodeId: String): Boolean {
        if (nodeId in expanded || nodeId !in specs) return false
        val depth = depths[nodeId] ?: return false
        return depth < maxDepth && nodes.size < maxNodes
    }

    fun expand(nodeId: String): GraphSlice {
        val spec = specs[nodeId] ?: return slice
        val depth = depths[nodeId] ?: return slice
        if (depth >= maxDepth || !expanded.add(nodeId)) return slice
        for (caller in source.incoming(spec)) {
            val callerId = addNeighbor(caller, depth + 1) ?: continue
            if (callerId == nodeId) continue
            edges.putIfAbsent(callerId to nodeId, GraphEdge(callerId, nodeId, EdgeKind.CALLS))
        }
        for (callee in source.outgoing(spec)) {
            val calleeId = addNeighbor(callee, depth + 1) ?: continue
            if (calleeId == nodeId) continue
            edges.putIfAbsent(nodeId to calleeId, GraphEdge(nodeId, calleeId, EdgeKind.CALLS))
        }
        return slice
    }

    private fun addNeighbor(spec: SymbolSpec, depth: Int): String? {
        val id = symbolNodeId(spec)
        if (id in nodes) {
            val current = depths[id]
            if (current == null || depth < current) depths[id] = depth
            return id
        }
        if (nodes.size >= maxNodes) return null
        register(spec, id, depth)
        return id
    }

    private fun register(spec: SymbolSpec, id: String, depth: Int) {
        nodes[id] = GraphNode(id, spec.name, pathOf(spec.uri), NodeKind.SYMBOL)
        specs[id] = spec
        depths[id] = depth
    }

    private fun pathOf(uri: String): Path? = try {
        Path.of(URI(uri))
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val MAX_DEPTH = 2
        const val MAX_NODES = 100
    }
}
