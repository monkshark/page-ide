package page.atlas

import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import page.atlas.graph.GraphEdge
import page.atlas.graph.GraphNode
import page.atlas.graph.GraphSlice
import page.atlas.graph.NodeKind
import page.atlas.render.MapFilterState
import page.atlas.render.filterForMap

class MapFilterTest {

    private fun node(id: String, kind: NodeKind = NodeKind.WORKSPACE_FILE) =
        GraphNode(id, Paths.get(id).fileName.toString(), Paths.get(id), kind)

    private val ws = Paths.get("ws").toAbsolutePath().toString()
    private val coreA = "$ws\\core\\A.kt"
    private val coreB = "$ws\\core\\B.kt"
    private val uiC = "$ws\\ui\\C.kt"
    private val uiSubD = "$ws\\ui\\sub\\D.kt"
    private val coreDir = "$ws\\core"
    private val uiDir = "$ws\\ui"

    private val slice = GraphSlice(
        nodes = listOf(node(coreA, NodeKind.ACTIVE), node(coreB), node(uiC), node(uiSubD)),
        edges = listOf(
            GraphEdge(coreA, coreB),
            GraphEdge(coreA, uiC),
            GraphEdge(uiC, uiSubD),
        ),
    )

    @Test
    fun `inactive filter returns the slice unchanged`() {
        assertSame(slice, filterForMap(slice, MapFilterState()))
    }

    @Test
    fun `focusDir keeps only nodes inside the folder and edges between them`() {
        val out = filterForMap(slice, MapFilterState(focusDir = uiDir))
        assertEquals(setOf(uiC, uiSubD), out.nodes.map { it.id }.toSet())
        assertEquals(listOf(GraphEdge(uiC, uiSubD)), out.edges)
    }

    @Test
    fun `focusDir does not match sibling folders sharing a name prefix`() {
        val uiExtra = "$ws\\ui-extra\\E.kt"
        val withSibling = GraphSlice(slice.nodes + node(uiExtra), slice.edges)
        val out = filterForMap(withSibling, MapFilterState(focusDir = uiDir))
        assertTrue(out.nodes.none { it.id == uiExtra })
    }

    @Test
    fun `hiddenDirs drops the subtree and its incident edges`() {
        val out = filterForMap(slice, MapFilterState(hiddenDirs = setOf(uiDir)))
        assertEquals(setOf(coreA, coreB), out.nodes.map { it.id }.toSet())
        assertEquals(listOf(GraphEdge(coreA, coreB)), out.edges)
    }

    @Test
    fun `mutedDirs keeps nodes but removes edges touching the folder`() {
        val out = filterForMap(slice, MapFilterState(mutedDirs = setOf(uiDir)))
        assertEquals(slice.nodes, out.nodes)
        assertEquals(listOf(GraphEdge(coreA, coreB)), out.edges)
    }

    @Test
    fun `focus and hidden combine`() {
        val out = filterForMap(
            slice,
            MapFilterState(focusDir = uiDir, hiddenDirs = setOf("$ws\\ui\\sub")),
        )
        assertEquals(listOf(uiC), out.nodes.map { it.id })
        assertTrue(out.edges.isEmpty())
    }

    @Test
    fun `hiding everything leaves an empty slice instead of failing`() {
        val out = filterForMap(slice, MapFilterState(hiddenDirs = setOf(coreDir, uiDir)))
        assertTrue(out.nodes.isEmpty())
        assertTrue(out.edges.isEmpty())
    }

    @Test
    fun `pinned node survives hiddenDirs`() {
        val out = filterForMap(slice, MapFilterState(hiddenDirs = setOf(uiDir)), pinned = setOf(uiC))
        assertEquals(setOf(coreA, coreB, uiC), out.nodes.map { it.id }.toSet())
        assertEquals(setOf(GraphEdge(coreA, coreB), GraphEdge(coreA, uiC)), out.edges.toSet())
    }

    @Test
    fun `pinned node survives outside focusDir`() {
        val out = filterForMap(slice, MapFilterState(focusDir = coreDir), pinned = setOf(uiC))
        assertEquals(setOf(coreA, coreB, uiC), out.nodes.map { it.id }.toSet())
    }

    @Test
    fun `edges touching a pinned node survive mutedDirs`() {
        val out = filterForMap(slice, MapFilterState(mutedDirs = setOf(uiDir)), pinned = setOf(uiC))
        assertEquals(slice.edges.toSet(), out.edges.toSet())

        val without = filterForMap(slice, MapFilterState(mutedDirs = setOf(uiDir)))
        assertEquals(listOf(GraphEdge(coreA, coreB)), without.edges)
    }

    @Test
    fun `pin does not resurrect edges to nodes that stay hidden`() {
        val out = filterForMap(slice, MapFilterState(hiddenDirs = setOf(uiDir)), pinned = setOf(uiSubD))
        assertEquals(setOf(coreA, coreB, uiSubD), out.nodes.map { it.id }.toSet())
        assertEquals(listOf(GraphEdge(coreA, coreB)), out.edges)
    }
}
