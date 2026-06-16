package page.atlas

import java.nio.file.Path
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import page.atlas.graph.ModuleEdge
import page.atlas.graph.ModuleGraph
import page.atlas.graph.ModuleNode
import page.atlas.graph.NodeKind
import page.atlas.render.FLPoint
import page.atlas.render.ForceParams
import page.atlas.render.forceLayout

class ForceLayoutTest {

    private fun mod(id: String): ModuleNode =
        ModuleNode(id, id, Path.of(id), 1, NodeKind.WORKSPACE_FILE, "kt")

    private fun graph(ids: List<String>, edges: List<Pair<String, String>>): ModuleGraph =
        ModuleGraph(ids.map { mod(it) }, edges.map { ModuleEdge(it.first, it.second, 1) })

    private fun dist(a: FLPoint, b: FLPoint): Double {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    @Test
    fun `same graph yields identical coordinates`() {
        val g = graph(listOf("a", "b", "c", "d"), listOf("a" to "b", "b" to "c", "c" to "d"))
        assertEquals(forceLayout(g).positions, forceLayout(g).positions)
    }

    @Test
    fun `node and edge ordering does not change the layout`() {
        val a = graph(listOf("a", "b", "c", "d"), listOf("a" to "b", "b" to "c", "c" to "d"))
        val b = graph(listOf("d", "c", "b", "a"), listOf("c" to "d", "b" to "c", "a" to "b"))
        assertEquals(forceLayout(a).positions, forceLayout(b).positions)
    }

    @Test
    fun `final iteration settles to a small step`() {
        val ids = (0 until 30).map { "m$it" }
        val edges = (0 until 29).map { ids[it] to ids[it + 1] }
        val result = forceLayout(graph(ids, edges))
        assertTrue(result.maxStep < 1.0, "cooling drives the last step toward zero, was ${result.maxStep}")
        assertTrue(result.width > 0.0 && result.height > 0.0, "non-degenerate bounding box")
    }

    @Test
    fun `nodes do not collapse onto each other`() {
        val ids = (0 until 12).map { "n$it" }
        val positions = forceLayout(graph(ids, emptyList())).positions.values.toList()
        var min = Double.MAX_VALUE
        for (i in positions.indices) {
            for (j in i + 1 until positions.size) {
                min = minOf(min, dist(positions[i], positions[j]))
            }
        }
        assertTrue(min > 1.0, "repulsion keeps disks apart, closest pair was $min")
    }

    @Test
    fun `connected nodes settle closer than unconnected ones`() {
        val leaves = (0 until 6).map { "leaf$it" }
        val edges = leaves.map { "hub" to it }
        val pos = forceLayout(graph(listOf("hub") + leaves, edges)).positions
        val hub = pos.getValue("hub")
        val connected = leaves.map { dist(hub, pos.getValue(it)) }.average()
        var unconnectedSum = 0.0
        var unconnectedCount = 0
        for (i in leaves.indices) {
            for (j in i + 1 until leaves.size) {
                unconnectedSum += dist(pos.getValue(leaves[i]), pos.getValue(leaves[j]))
                unconnectedCount++
            }
        }
        val unconnected = unconnectedSum / unconnectedCount
        assertTrue(connected < unconnected, "hub-leaf $connected should be tighter than leaf-leaf $unconnected")
    }

    @Test
    fun `custom iteration count is reported back`() {
        val g = graph(listOf("a", "b"), listOf("a" to "b"))
        assertEquals(50, forceLayout(g, ForceParams.DEFAULT.copy(iterations = 50)).iterations)
    }

    @Test
    fun `empty and single node graphs are handled`() {
        assertTrue(forceLayout(ModuleGraph.EMPTY).positions.isEmpty())
        val single = forceLayout(graph(listOf("solo"), emptyList())).positions
        assertEquals(FLPoint(0.0, 0.0), single.getValue("solo"))
    }
}
