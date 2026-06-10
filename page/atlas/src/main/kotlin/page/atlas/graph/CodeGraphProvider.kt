package page.atlas.graph

import java.nio.file.Path

interface CodeGraphProvider {
    fun nodesForFile(path: Path, text: String): GraphSlice
    fun nodesNear(path: Path, depth: Int): GraphSlice = GraphSlice.EMPTY
    fun findPath(fromId: String, toId: String): List<GraphEdge>? = null
}
