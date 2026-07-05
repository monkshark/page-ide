package page.shared.graph

enum class NodeKind { ACTIVE, WORKSPACE_FILE, EXTERNAL, SYMBOL }

enum class EdgeKind { IMPORT, IMPLEMENTS, EXTENDS, CALLS }

data class GraphNode(
    val id: String,
    val label: String,
    val kind: NodeKind,
)

data class GraphEdge(
    val from: String,
    val to: String,
    val kind: EdgeKind = EdgeKind.IMPORT,
)

data class GraphSlice(
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>,
) {
    companion object {
        val EMPTY = GraphSlice(emptyList(), emptyList())
    }
}
