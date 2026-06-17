package page.atlas

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import page.atlas.graph.EdgeKind
import page.atlas.graph.GraphEdge
import page.atlas.graph.GraphNode
import page.atlas.graph.GraphSlice
import page.atlas.graph.NodeKind
import page.atlas.render.EgoColumn
import page.atlas.render.EgoModel
import page.atlas.render.buildEgoModel
import page.atlas.render.egoNodeAt
import page.atlas.render.egoTransform

class EgoLayoutTest {

    private fun node(id: String, kind: NodeKind = NodeKind.WORKSPACE_FILE) =
        GraphNode(id, "$id.kt", null, kind)

    private fun egoSlice(): GraphSlice {
        val nodes = listOf(
            node("focus", NodeKind.ACTIVE),
            node("dep"),
            node("imp"),
            node("ext"),
        )
        val edges = listOf(
            GraphEdge("dep", "focus", EdgeKind.IMPORT),
            GraphEdge("focus", "imp", EdgeKind.IMPORT),
            GraphEdge("imp", "ext", EdgeKind.IMPORT),
        )
        return GraphSlice(nodes, edges)
    }

    private fun EgoModel.byId(id: String) = nodes.first { it.id == id }

    @Test
    fun `only dependents are drawn and they sit left of the focus`() {
        val model = buildEgoModel(egoSlice(), "focus")
        val dep = model.byId("dep")
        val focus = model.byId("focus")

        assertEquals(EgoColumn.DEPENDENT, dep.column)
        assertEquals(EgoColumn.FOCUS, focus.column)
        assertTrue(dep.center.x < focus.center.x, "dependents left of focus")
        assertTrue(model.nodes.none { it.id == "imp" }, "imports are not drawn as nodes")
        assertTrue(model.nodes.none { it.id == "ext" }, "externals are not drawn as nodes")
    }

    @Test
    fun `focus radius is the largest`() {
        val model = buildEgoModel(egoSlice(), "focus")
        val focus = model.byId("focus")
        assertTrue(model.nodes.filter { it.id != "focus" }.all { it.radius < focus.radius })
    }

    @Test
    fun `every drawn edge points at the focus`() {
        val model = buildEgoModel(egoSlice(), "focus")
        val depEdge = model.edges.first { it.from == "dep" && it.to == "focus" }
        assertTrue(depEdge.toFocus)
        assertTrue(model.edges.all { it.to == "focus" && it.toFocus }, "only dependent edges are drawn")
    }

    @Test
    fun `edges have horizontal tangents at both ends`() {
        val model = buildEgoModel(egoSlice(), "focus")
        for (e in model.edges) {
            assertEquals(e.start.y, e.c1.y, "start tangent horizontal")
            assertEquals(e.end.y, e.c2.y, "end tangent horizontal")
        }
    }

    @Test
    fun `same input produces identical model`() {
        val a = buildEgoModel(egoSlice(), "focus")
        val b = buildEgoModel(egoSlice(), "focus")
        assertEquals(a, b)
    }

    @Test
    fun `dual role node renders once in the dependent column`() {
        val nodes = listOf(node("focus", NodeKind.ACTIVE), node("other"))
        val edges = listOf(
            GraphEdge("focus", "other", EdgeKind.IMPORT),
            GraphEdge("other", "focus", EdgeKind.IMPORT),
        )
        val model = buildEgoModel(GraphSlice(nodes, edges), "focus")
        assertEquals(1, model.nodes.count { it.id == "other" })
        assertEquals(EgoColumn.DEPENDENT, model.byId("other").column)
        assertEquals(1, model.edges.size)
        assertTrue(model.edges.first().toFocus)
    }

    @Test
    fun `overflow collapses the tail into a single more node carrying the hidden count`() {
        val nodes = ArrayList<GraphNode>()
        val edges = ArrayList<GraphEdge>()
        nodes += node("focus", NodeKind.ACTIVE)
        repeat(50) { i ->
            val id = "dep%02d".format(i)
            nodes += node(id)
            edges += GraphEdge(id, "focus", EdgeKind.IMPORT)
        }
        val model = buildEgoModel(GraphSlice(nodes, edges), "focus")
        val column = model.nodes.filter { it.column == EgoColumn.DEPENDENT }
        val real = column.filter { it.overflow == 0 }
        val more = column.filter { it.overflow > 0 }
        assertEquals(8, real.size, "only the visible cap is drawn as real nodes")
        assertEquals(1, more.size, "exactly one overflow node collapses the tail")
        assertEquals(42, more.first().overflow, "overflow carries the hidden remainder")
        assertEquals("+42 more", more.first().label)
    }

    @Test
    fun `empty model when focus is absent`() {
        val model = buildEgoModel(GraphSlice(listOf(node("a")), emptyList()), "missing")
        assertEquals(EgoModel.EMPTY, model)
    }

    @Test
    fun `transform and hit test find the node under a screen point`() {
        val model = buildEgoModel(egoSlice(), "focus")
        val transform = egoTransform(model, 640f, 400f, Offset.Zero, 1f)
        val focus = model.byId("focus")
        val screen = transform.toScreen(focus.center)

        val hit = egoNodeAt(model, transform, screen)
        assertNotNull(hit)
        assertEquals("focus", hit.id)

        val miss = egoNodeAt(model, transform, Offset(screen.x + 10_000f, screen.y))
        assertNull(miss)
    }
}
