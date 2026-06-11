package page.atlas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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

    private fun edges(vararg pairs: Pair<String, String>) =
        pairs.map { (from, to) -> GraphEdge(from, to) }

    @Test
    fun `findPath returns direct edge`() {
        val path = GraphQueries.findPath(edges("a" to "b"), "a", "b")
        assertEquals(listOf("a" to "b"), path?.map { it.from to it.to })
    }

    @Test
    fun `findPath returns shortest chain over longer alternative`() {
        val all = edges("a" to "b", "b" to "c", "c" to "d", "a" to "x", "x" to "d")
        val path = GraphQueries.findPath(all, "a", "d")
        assertEquals(listOf("a" to "x", "x" to "d"), path?.map { it.from to it.to })
    }

    @Test
    fun `findPath respects edge direction`() {
        assertNull(GraphQueries.findPath(edges("a" to "b"), "b", "a"))
    }

    @Test
    fun `findPath returns null when no route exists`() {
        assertNull(GraphQueries.findPath(edges("a" to "b", "c" to "d"), "a", "d"))
    }

    @Test
    fun `findPath terminates on cycles`() {
        val all = edges("a" to "b", "b" to "a", "b" to "c")
        val path = GraphQueries.findPath(all, "a", "c")
        assertEquals(listOf("a" to "b", "b" to "c"), path?.map { it.from to it.to })
    }

    @Test
    fun `findPath from node to itself is empty`() {
        assertEquals(emptyList(), GraphQueries.findPath(edges("a" to "b"), "a", "a"))
    }
}
