package page.atlas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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
    fun `acyclic graph has no cycles`() {
        val s = slice(listOf("a", "b", "c"), listOf("a" to "b", "b" to "c", "a" to "c"))
        assertTrue(GraphQueries.cycles(s).isEmpty())
    }

    @Test
    fun `two node cycle detected`() {
        val s = slice(listOf("a", "b"), listOf("a" to "b", "b" to "a"))
        val cycles = GraphQueries.cycles(s)
        assertEquals(1, cycles.size)
        assertEquals(setOf("a", "b"), cycles[0].map { it.id }.toSet())
    }

    @Test
    fun `three node cycle with tail detected once`() {
        val s = slice(
            listOf("a", "b", "c", "d"),
            listOf("a" to "b", "b" to "c", "c" to "a", "c" to "d"),
        )
        val cycles = GraphQueries.cycles(s)
        assertEquals(1, cycles.size)
        assertEquals(setOf("a", "b", "c"), cycles[0].map { it.id }.toSet())
    }

    @Test
    fun `disjoint cycles reported separately`() {
        val s = slice(
            listOf("a", "b", "c", "d"),
            listOf("a" to "b", "b" to "a", "c" to "d", "d" to "c"),
        )
        val cycles = GraphQueries.cycles(s)
        assertEquals(2, cycles.size)
        assertEquals(
            setOf(setOf("a", "b"), setOf("c", "d")),
            cycles.map { c -> c.map { it.id }.toSet() }.toSet(),
        )
    }

    @Test
    fun `self loop counts as cycle`() {
        val s = slice(listOf("a", "b"), listOf("a" to "a", "a" to "b"))
        val cycles = GraphQueries.cycles(s)
        assertEquals(1, cycles.size)
        assertEquals(listOf("a"), cycles[0].map { it.id })
    }

    @Test
    fun `edges to unknown nodes are ignored`() {
        val s = GraphSlice(
            listOf(node("a")),
            listOf(GraphEdge("a", "ghost"), GraphEdge("ghost", "a")),
        )
        assertTrue(GraphQueries.cycles(s).isEmpty())
    }

    @Test
    fun `cycles are deterministic for same input`() {
        val s = slice(
            listOf("a", "b", "c", "d", "e"),
            listOf("a" to "b", "b" to "c", "c" to "a", "d" to "e", "e" to "d"),
        )
        assertEquals(GraphQueries.cycles(s), GraphQueries.cycles(s))
    }

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
