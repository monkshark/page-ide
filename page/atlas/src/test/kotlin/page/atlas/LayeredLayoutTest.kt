package page.atlas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import page.atlas.graph.GraphEdge
import page.atlas.graph.GraphNode
import page.atlas.graph.GraphSlice
import page.atlas.graph.NodeKind
import page.atlas.render.layeredLayout

class LayeredLayoutTest {

    private fun node(id: String, kind: NodeKind = NodeKind.WORKSPACE_FILE) = GraphNode(id, id, null, kind)

    @Test
    fun `importer sits above its dependency`() {
        val slice = GraphSlice(
            listOf(node("app", NodeKind.ACTIVE), node("service"), node("util")),
            listOf(GraphEdge("app", "service"), GraphEdge("service", "util")),
        )
        val layout = layeredLayout(slice, 400f, 600f)
        val app = layout.getValue("app")
        val service = layout.getValue("service")
        val util = layout.getValue("util")
        assertTrue(app.y < service.y)
        assertTrue(service.y < util.y)
    }

    @Test
    fun `leaves share the bottom row`() {
        val slice = GraphSlice(
            listOf(node("app", NodeKind.ACTIVE), node("a"), node("b"), node("c")),
            listOf(GraphEdge("app", "a"), GraphEdge("app", "b"), GraphEdge("app", "c")),
        )
        val layout = layeredLayout(slice, 400f, 600f)
        val ys = listOf("a", "b", "c").map { layout.getValue(it).y }.distinct()
        assertEquals(1, ys.size)
        assertTrue(layout.getValue("app").y < ys.single())
        val xs = listOf("a", "b", "c").map { layout.getValue(it).x }
        assertEquals(xs.distinct().size, xs.size)
    }

    @Test
    fun `cycle terminates and still layers`() {
        val slice = GraphSlice(
            listOf(node("a", NodeKind.ACTIVE), node("b")),
            listOf(GraphEdge("a", "b"), GraphEdge("b", "a")),
        )
        val layout = layeredLayout(slice, 400f, 600f)
        assertEquals(2, layout.size)
        assertTrue(layout.getValue("a").y != layout.getValue("b").y)
    }

    @Test
    fun `single node is centered`() {
        val slice = GraphSlice(listOf(node("solo", NodeKind.ACTIVE)), emptyList())
        val layout = layeredLayout(slice, 400f, 600f)
        assertEquals(200f, layout.getValue("solo").x)
        assertEquals(300f, layout.getValue("solo").y)
    }

    @Test
    fun `layout is deterministic`() {
        val slice = GraphSlice(
            listOf(node("app", NodeKind.ACTIVE), node("x"), node("y"), node("z")),
            listOf(
                GraphEdge("app", "x"),
                GraphEdge("app", "y"),
                GraphEdge("x", "z"),
                GraphEdge("y", "z"),
            ),
        )
        assertEquals(layeredLayout(slice, 360f, 500f), layeredLayout(slice, 360f, 500f))
    }

    @Test
    fun `empty slice yields empty layout`() {
        assertTrue(layeredLayout(GraphSlice.EMPTY, 400f, 600f).isEmpty())
    }
}
