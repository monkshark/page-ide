package page.atlas

import kotlin.test.Test
import kotlin.test.assertEquals
import page.atlas.graph.EdgeKind
import page.atlas.graph.GraphEdge
import page.atlas.graph.GraphNode
import page.atlas.graph.GraphQueries
import page.atlas.graph.GraphSlice
import page.atlas.graph.NodeKind

class GraphQueriesTest {

    private fun node(id: String, kind: NodeKind = NodeKind.WORKSPACE_FILE) =
        GraphNode(id, id, null, kind)

    private fun slice(nodes: List<String>, edges: List<Pair<String, String>>) = GraphSlice(
        nodes.map { node(it) },
        edges.map { (from, to) -> GraphEdge(from, to) },
    )

    @Test
    fun `merge keeps base nodes and adds overlay only nodes`() {
        val base = slice(listOf("a", "b"), listOf("a" to "b"))
        val overlay = GraphSlice(
            listOf(node("a", NodeKind.ACTIVE), node("ext", NodeKind.EXTERNAL)),
            listOf(GraphEdge("a", "ext")),
        )
        val merged = GraphQueries.merge(base, overlay)
        assertEquals(listOf("a", "b", "ext"), merged.nodes.map { it.id })
        assertEquals(NodeKind.WORKSPACE_FILE, merged.nodes.first { it.id == "a" }.kind)
        assertEquals(NodeKind.EXTERNAL, merged.nodes.first { it.id == "ext" }.kind)
        assertEquals(2, merged.edges.size)
    }

    @Test
    fun `merge keeps base edge kind on conflict`() {
        val base = GraphSlice(
            listOf(node("a"), node("b")),
            listOf(GraphEdge("a", "b", EdgeKind.EXTENDS)),
        )
        val overlay = GraphSlice(
            listOf(node("a"), node("b")),
            listOf(GraphEdge("a", "b", EdgeKind.IMPORT)),
        )
        val merged = GraphQueries.merge(base, overlay)
        assertEquals(1, merged.edges.size)
        assertEquals(EdgeKind.EXTENDS, merged.edges[0].kind)
    }

    @Test
    fun `merge with empty side returns other side`() {
        val s = slice(listOf("a"), emptyList())
        assertEquals(s, GraphQueries.merge(s, GraphSlice.EMPTY))
        assertEquals(s, GraphQueries.merge(GraphSlice.EMPTY, s))
    }
}
