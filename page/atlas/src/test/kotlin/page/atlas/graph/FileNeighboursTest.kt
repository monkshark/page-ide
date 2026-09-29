package page.atlas.graph

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileNeighboursTest {

    private fun node(id: String, kind: NodeKind = NodeKind.WORKSPACE_FILE) = GraphNode(id, "$id.kt", null, kind)

    private fun slice(vararg edges: Pair<String, String>, external: Set<String> = emptySet()): GraphSlice {
        val ids = edges.flatMap { listOf(it.first, it.second) }.toSet()
        return GraphSlice(
            ids.map { node(it, if (it in external) NodeKind.EXTERNAL else NodeKind.WORKSPACE_FILE) },
            edges.map { GraphEdge(it.first, it.second) },
        )
    }

    @Test
    fun `used by lists the files that import it and uses lists what it imports`() {
        val n = neighboursOf(slice("screen" to "store", "api" to "store", "store" to "model"), "store")
        assertEquals(listOf("api", "screen"), n.usedBy.map { it.id })
        assertEquals(listOf("model"), n.uses.map { it.id })
    }

    @Test
    fun `libraries outside the project are not listed`() {
        val n = neighboursOf(slice("store" to "gson", "store" to "model", external = setOf("gson")), "store")
        assertEquals(listOf("model"), n.uses.map { it.id })
    }

    @Test
    fun `a file in a loop gets the shortest way back to itself`() {
        val n = neighboursOf(slice("a" to "b", "b" to "c", "c" to "a", "a" to "x", "x" to "y", "y" to "z", "z" to "a"), "a")
        assertEquals(listOf("a", "b", "c", "a"), n.cycle.map { it.id })
    }

    @Test
    fun `two files that need each other make a loop of two`() {
        assertEquals(listOf("a", "b", "a"), neighboursOf(slice("a" to "b", "b" to "a"), "a").cycle.map { it.id })
    }

    @Test
    fun `a file outside any loop has no cycle`() {
        assertTrue(neighboursOf(slice("a" to "b", "b" to "c"), "a").cycle.isEmpty())
    }

    @Test
    fun `a loop that does not pass through the file is not its cycle`() {
        assertTrue(neighboursOf(slice("a" to "b", "b" to "c", "c" to "b"), "a").cycle.isEmpty())
    }

    @Test
    fun `an unknown file has no neighbours`() {
        assertEquals(FileNeighbours.EMPTY, neighboursOf(slice("a" to "b"), "missing"))
    }
}
