package page.shared.graph

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GraphInsightsTest {

    private fun node(id: String) = GraphNode(id, id, NodeKind.WORKSPACE_FILE)

    @Test
    fun neighborhoodSplitsIncomingOutgoing() {
        val slice = GraphSlice(
            nodes = listOf(node("a"), node("b"), node("c")),
            edges = listOf(GraphEdge("a", "b"), GraphEdge("c", "b"), GraphEdge("b", "a")),
        )
        val n = GraphInsights.neighborhood(slice, "b")
        assertEquals(setOf("a", "c"), n.incoming.map { it.node.id }.toSet())
        assertEquals(listOf("a"), n.outgoing.map { it.node.id })
        assertEquals(2, n.incomingTotal)
        assertEquals(1, n.outgoingTotal)
    }

    @Test
    fun neighborsSortByIndegreeWeight() {
        val slice = GraphSlice(
            nodes = listOf(node("hub"), node("x"), node("y"), node("root")),
            edges = listOf(
                GraphEdge("root", "hub"),
                GraphEdge("root", "x"),
                GraphEdge("y", "hub"),
                GraphEdge("z", "hub"),
            ),
        )
        val n = GraphInsights.neighborhood(slice, "root")
        assertEquals("hub", n.outgoing.first().node.id)
        assertTrue(n.outgoing.first().weight >= n.outgoing.last().weight)
    }

    @Test
    fun missingFocusIsEmpty() {
        val n = GraphInsights.neighborhood(GraphSlice.EMPTY, "nope")
        assertEquals(null, n.focus)
        assertTrue(n.incoming.isEmpty())
    }

    @Test
    fun limitCapsNeighbors() {
        val edges = (1..10).map { GraphEdge("root", "n$it") }
        val nodes = listOf(node("root")) + (1..10).map { node("n$it") }
        val n = GraphInsights.neighborhood(GraphSlice(nodes, edges), "root", limit = 3)
        assertEquals(3, n.outgoing.size)
        assertEquals(10, n.outgoingTotal)
    }

    @Test
    fun cycleDetected() {
        val slice = GraphSlice(
            nodes = listOf(node("a"), node("b"), node("c")),
            edges = listOf(GraphEdge("a", "b"), GraphEdge("b", "c"), GraphEdge("c", "a")),
        )
        assertTrue(GraphInsights.neighborhood(slice, "a").inCycle)
    }

    @Test
    fun acyclicIsNotInCycle() {
        val slice = GraphSlice(
            nodes = listOf(node("a"), node("b")),
            edges = listOf(GraphEdge("a", "b")),
        )
        assertFalse(GraphInsights.neighborhood(slice, "a").inCycle)
    }

    @Test
    fun indegreesDedupeParallelEdges() {
        val edges = listOf(GraphEdge("a", "b"), GraphEdge("a", "b"), GraphEdge("c", "b"))
        assertEquals(2, GraphInsights.indegrees(edges)["b"])
    }
}
