package page.atlas.graph

import page.shared.path.FilePath

enum class NodeKind { ACTIVE, WORKSPACE_FILE, EXTERNAL, SYMBOL }

enum class EdgeKind { IMPORT, IMPLEMENTS, EXTENDS, CALLS }

data class GraphNode(
    val id: String,
    val label: String,
    val path: FilePath?,
    val kind: NodeKind,
)

data class SourceEvidence(val line: Int, val text: String)

data class GraphEdge(
    val from: String,
    val to: String,
    val kind: EdgeKind = EdgeKind.IMPORT,
    val evidence: SourceEvidence? = null,
)

data class GraphSlice(
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>,
) {
    companion object {
        val EMPTY = GraphSlice(emptyList(), emptyList())
    }
}
