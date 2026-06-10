package page.atlas.analyzer

import java.nio.file.Path
import page.atlas.graph.CodeGraphProvider
import page.atlas.graph.GraphEdge
import page.atlas.graph.GraphNode
import page.atlas.graph.GraphSlice
import page.atlas.graph.NodeKind

class ImportGraphProvider(root: Path) : CodeGraphProvider {

    private val index = WorkspaceIndex(root)

    override fun nodesForFile(path: Path, text: String): GraphSlice {
        if (!ImportExtractor.supports(path)) return GraphSlice.EMPTY
        val raws = ImportExtractor.extract(path, text).distinctBy { it.target }
        val activeId = nodeId(path)
        val nodes = ArrayList<GraphNode>()
        val edges = ArrayList<GraphEdge>()
        val seen = HashSet<String>()
        nodes += GraphNode(activeId, path.fileName.toString(), path, NodeKind.ACTIVE)
        seen += activeId
        for (raw in raws) {
            if (nodes.size >= MAX_NODES) break
            val resolved = ImportResolver.resolve(raw, path, index)
            val id = resolved?.let(::nodeId) ?: raw.target
            if (!seen.add(id)) continue
            nodes += if (resolved != null) {
                GraphNode(id, resolved.fileName.toString(), resolved, NodeKind.WORKSPACE_FILE)
            } else {
                GraphNode(id, raw.target, null, NodeKind.EXTERNAL)
            }
            edges += GraphEdge(activeId, id)
        }
        return GraphSlice(nodes, edges)
    }

    private fun nodeId(path: Path): String = path.toAbsolutePath().normalize().toString()

    private companion object {
        const val MAX_NODES = 100
    }
}
