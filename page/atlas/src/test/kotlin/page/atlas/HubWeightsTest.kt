package page.atlas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import page.atlas.graph.GraphEdge
import page.atlas.graph.GraphNode
import page.atlas.graph.GraphSlice
import page.atlas.graph.NodeKind
import page.atlas.render.hubWeight
import page.atlas.render.hubWeights

class HubWeightsTest {

    private fun slice(ids: List<String>, edges: List<Pair<String, String>>) = GraphSlice(
        nodes = ids.map { GraphNode(it, it, null, NodeKind.WORKSPACE_FILE) },
        edges = edges.map { (from, to) -> GraphEdge(from, to) },
    )

    @Test
    fun zeroDependentsKeepsBaseSize() {
        assertEquals(1f, hubWeight(0))
    }

    @Test
    fun weightGrowsMonotonicallyWithDependents() {
        assertTrue(hubWeight(1) > hubWeight(0))
        assertTrue(hubWeight(5) > hubWeight(1))
        assertTrue(hubWeight(20) > hubWeight(5))
    }

    @Test
    fun weightIsCapped() {
        assertEquals(hubWeight(10_000), hubWeight(1_000_000))
        assertTrue(hubWeight(1_000_000) <= 1.8f)
    }

    @Test
    fun weightsCountIncomingEdgesPerNode() {
        val weights = hubWeights(
            slice(
                ids = listOf("hub", "a", "b", "c"),
                edges = listOf("a" to "hub", "b" to "hub", "c" to "hub", "hub" to "a"),
            ),
        )
        assertEquals(hubWeight(3), weights["hub"])
        assertEquals(hubWeight(1), weights["a"])
        assertEquals(hubWeight(0), weights["b"])
    }

    @Test
    fun selfLoopIsIgnored() {
        val weights = hubWeights(slice(ids = listOf("a"), edges = listOf("a" to "a")))
        assertEquals(hubWeight(0), weights["a"])
    }
}
