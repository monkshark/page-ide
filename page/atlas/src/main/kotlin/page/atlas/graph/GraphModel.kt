package page.atlas.graph

import java.nio.file.Path

enum class NodeKind { ACTIVE, WORKSPACE_FILE, EXTERNAL }

enum class EdgeKind { IMPORT }

data class GraphNode(
    val id: String,
    val label: String,
    val path: Path?,
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
