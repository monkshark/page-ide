package page.atlas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import page.atlas.render.MapEdge
import page.atlas.render.mapCycleEdges

class MapCyclesTest {

    private fun edge(from: String, to: String) = MapEdge(from, to, 1)

    @Test
    fun `acyclic graph has no cycle edges`() {
        val edges = listOf(edge("a", "b"), edge("b", "c"), edge("a", "c"))
        assertTrue(mapCycleEdges(edges).isEmpty())
    }

    @Test
    fun `mutual dependency marks both edges`() {
        val edges = listOf(edge("a", "b"), edge("b", "a"), edge("b", "c"))
        assertEquals(setOf("a" to "b", "b" to "a"), mapCycleEdges(edges))
    }

    @Test
    fun `longer cycle marks every edge on the loop only`() {
        val edges = listOf(
            edge("a", "b"), edge("b", "c"), edge("c", "a"),
            edge("c", "d"), edge("x", "a"),
        )
        assertEquals(setOf("a" to "b", "b" to "c", "c" to "a"), mapCycleEdges(edges))
    }

    @Test
    fun `edge between two separate cycles is not marked`() {
        val edges = listOf(
            edge("a", "b"), edge("b", "a"),
            edge("c", "d"), edge("d", "c"),
            edge("b", "c"),
        )
        assertEquals(
            setOf("a" to "b", "b" to "a", "c" to "d", "d" to "c"),
            mapCycleEdges(edges),
        )
    }

    @Test
    fun `chord inside one component is part of the cycle component`() {
        val edges = listOf(
            edge("a", "b"), edge("b", "c"), edge("c", "a"), edge("a", "c"),
        )
        assertEquals(
            setOf("a" to "b", "b" to "c", "c" to "a", "a" to "c"),
            mapCycleEdges(edges),
        )
    }

    @Test
    fun `self loop is marked`() {
        assertEquals(setOf("a" to "a"), mapCycleEdges(listOf(edge("a", "a"))))
    }

    @Test
    fun `empty input yields empty output`() {
        assertTrue(mapCycleEdges(emptyList()).isEmpty())
    }
}
