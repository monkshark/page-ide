package page.atlas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import page.atlas.graph.GraphNode
import page.atlas.graph.GraphSlice
import page.atlas.graph.NodeKind
import page.atlas.render.NodePos
import page.atlas.render.radialLayout

class RadialLayoutTest {

    private fun slice(ringCount: Int): GraphSlice {
        val active = GraphNode("active", "active", null, NodeKind.ACTIVE)
        val ring = (1..ringCount).map { GraphNode("n$it", "n$it", null, NodeKind.EXTERNAL) }
        return GraphSlice(listOf(active) + ring, emptyList())
    }

    @Test
    fun `active node sits at center`() {
        val positions = radialLayout(slice(4), 800f, 600f)
        assertEquals(NodePos(400f, 300f), positions["active"])
    }

    @Test
    fun `ring nodes get distinct deterministic positions`() {
        val first = radialLayout(slice(6), 800f, 600f)
        val second = radialLayout(slice(6), 800f, 600f)
        assertEquals(first, second)
        assertEquals(7, first.size)
        val ringPositions = first.filterKeys { it != "active" }.values.toList()
        assertEquals(ringPositions.size, ringPositions.distinct().size)
    }

    @Test
    fun `empty slice yields empty layout`() {
        assertTrue(radialLayout(GraphSlice.EMPTY, 800f, 600f).isEmpty())
    }
}
