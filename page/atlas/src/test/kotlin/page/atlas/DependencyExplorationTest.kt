package page.atlas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import page.atlas.graph.EdgeKind
import page.atlas.graph.GraphEdge
import page.atlas.graph.GraphNode
import page.atlas.graph.GraphSlice
import page.atlas.graph.NodeKind
import page.atlas.graph.SourceEvidence
import page.atlas.interaction.DependencyExploration
import page.atlas.interaction.ExplorationDirection
import page.atlas.render.ExplorationViewState
import page.atlas.render.MapViewState
import page.atlas.render.revealExploration
import page.shared.path.FilePath

class DependencyExplorationTest {
    @Test
    fun `collapse removes descendants while preserving the other direction and coordinates`() {
        val slice = graph("caller" to "root", "root" to "a", "a" to "b", "b" to "c")
        val state = DependencyExploration.start(slice, "root").select(slice, "a")
            .expand(slice, ExplorationDirection.USES).select(slice, "b")
            .expand(slice, ExplorationDirection.USES).select(slice, "root")
        val collapsed = state.collapse(ExplorationDirection.USES)
        assertEquals(setOf("caller", "root"), collapsed.positions.keys)
        for (id in collapsed.positions.keys) assertEquals(state.positions[id], collapsed.positions[id])
        assertFalse(collapsed.canCollapse(ExplorationDirection.USES))
        assertTrue(collapsed.canCollapse(ExplorationDirection.USED_BY))
        assertEquals(setOf("caller", "root", "a"), collapsed.expand(slice, ExplorationDirection.USES).positions.keys)
    }

    @Test
    fun `collapse preserves a dependency shared by another expanded branch`() {
        val slice = graph("root" to "a", "root" to "b", "a" to "shared", "b" to "shared", "a" to "private")
        val state = DependencyExploration.start(slice, "root").select(slice, "a").expand(slice, ExplorationDirection.USES)
            .select(slice, "b").expand(slice, ExplorationDirection.USES).select(slice, "a")
        val collapsed = state.collapse(ExplorationDirection.USES)
        assertTrue("shared" in collapsed.positions)
        assertFalse("private" in collapsed.positions)
        assertEquals(state.positions["shared"], collapsed.positions["shared"])
    }

    @Test
    fun `collapse handles cyclic branches and retains the start file`() {
        val slice = graph("root" to "a", "a" to "b", "b" to "a")
        val state = DependencyExploration.start(slice, "root").select(slice, "a").expand(slice, ExplorationDirection.USES)
            .select(slice, "b").expand(slice, ExplorationDirection.USES).select(slice, "root")
        assertEquals(setOf("root"), state.collapse(ExplorationDirection.USES).positions.keys)
    }

    @Test
    fun `clear removes inspection and highlight without collapsing the exploration`() {
        val slice = graph("root" to "a", "a" to "b")
        val state = DependencyExploration.start(slice, "root").traceTo(slice, "b")
        val cleared = state.clearHighlight()
        assertEquals(state.positions, cleared.positions)
        assertNull(cleared.highlight)
        assertNull(cleared.message)
        assertTrue(cleared.highlightedEdges.isEmpty())
        assertNull(cleared.inspect(slice, slice.edges.first()).clearHighlight().selectedEdge)
    }

    @Test
    fun `analysis refresh removes ownership of deleted relationships`() {
        val slice = graph("root" to "a", "a" to "b")
        val state = DependencyExploration.start(slice, "root").select(slice, "a").expand(slice, ExplorationDirection.USES)
        val refreshed = state.reconcile(slice.copy(edges = slice.edges.filter { it.to != "b" }))
        assertFalse(refreshed.canCollapse(ExplorationDirection.USES))
        assertTrue(refreshed.canCollapse(ExplorationDirection.USED_BY))
    }

    @Test
    fun `visited trail can return to a file and back restores the complete trail`() {
        val slice = graph("a" to "b", "b" to "c")
        val state = ExplorationViewState()
        state.start(slice, "a")
        state.update(state.exploration.select(slice, "b"))
        state.update(state.exploration.select(slice, "c"))
        assertEquals(listOf("a", "b", "c"), state.trail)
        state.revisit(slice, 0)
        assertEquals("a", state.exploration.selectedId)
        assertEquals(listOf("a"), state.trail)
        state.back(slice)
        assertEquals("c", state.exploration.selectedId)
        assertEquals(listOf("a", "b", "c"), state.trail)
        assertNull(state.revealRequest)
    }

    @Test
    fun `new files request visibility once without moving the camera on inspection`() {
        val slice = graph("a" to "b", "b" to "c")
        val state = ExplorationViewState()
        state.start(slice, "a")
        state.update(state.exploration.select(slice, "b").expand(slice, ExplorationDirection.USES))
        val request = requireNotNull(state.revealRequest)
        assertEquals(setOf("b", "c"), request.ids)
        state.acknowledgeReveal(request)
        state.update(state.exploration.inspect(slice, slice.edges.first()))
        assertNull(state.revealRequest)
    }

    @Test
    fun `viewport moves only enough to reveal targets and preserves readable scale when they fit`() {
        val camera = MapViewState().apply { scale = 1f; pan = Offset.Zero }
        revealExploration(camera, listOf(Rect(100f, 100f, 324f, 184f)), IntSize(800, 600))
        assertEquals(Offset.Zero, camera.pan)
        revealExploration(camera, listOf(Rect(750f, 100f, 974f, 184f)), IntSize(800, 600))
        assertEquals(1f, camera.scale)
        assertEquals(Offset(-210f, 0f), camera.pan)
        assertTrue(974f * camera.scale + camera.pan.x < 800f)
    }

    @Test
    fun `viewport fits all newly expanded rows without changing graph coordinates`() {
        val camera = MapViewState().apply { scale = 1f; pan = Offset.Zero }
        val targets = listOf(Rect(0f, -400f, 224f, -316f), Rect(330f, 400f, 554f, 484f))
        revealExploration(camera, targets, IntSize(800, 600))
        assertTrue(camera.scale < 1f)
        for (rect in targets) {
            assertTrue(rect.left * camera.scale + camera.pan.x >= 0f)
            assertTrue(rect.top * camera.scale + camera.pan.y >= 0f)
            assertTrue(rect.right * camera.scale + camera.pan.x <= 800f)
            assertTrue(rect.bottom * camera.scale + camera.pan.y <= 600f)
        }
    }

    private fun graph(vararg edges: Pair<String, String>): GraphSlice {
        val ids = edges.flatMap { listOf(it.first, it.second) }.distinct()
        return GraphSlice(ids.map { GraphNode(it, "$it.kt", FilePath.of("/project/$it.kt"), NodeKind.WORKSPACE_FILE) },
            edges.map { GraphEdge(it.first, it.second) })
    }

    @Test
    fun `starts with direct relationships and separates direction`() {
        val slice = graph("caller" to "focus", "focus" to "dependency", "dependency" to "deeper")
        val state = DependencyExploration.start(slice, "focus")
        assertEquals(setOf("focus", "caller", "dependency"), state.positions.keys)
        assertEquals(listOf("caller"), state.neighbors(slice, ExplorationDirection.USED_BY))
        assertEquals(listOf("dependency"), state.neighbors(slice, ExplorationDirection.USES))
        assertTrue(state.positions.getValue("caller").column < state.positions.getValue("focus").column)
        assertTrue(state.positions.getValue("dependency").column > state.positions.getValue("focus").column)
    }

    @Test
    fun `select and expand keep every existing coordinate`() {
        val slice = graph("a" to "b", "b" to "c", "b" to "d", "c" to "a")
        val initial = DependencyExploration.start(slice, "a")
        val expanded = initial.select(slice, "b").expand(slice, ExplorationDirection.USES)
        for ((id, slot) in initial.positions) assertEquals(slot, expanded.positions[id])
        assertEquals(expanded.positions.size, expanded.positions.values.toSet().size)
        assertTrue("d" in expanded.positions)
    }

    @Test
    fun `expansion is paged and never exceeds the disclosed view cap`() {
        val slice = graph(*(0..120).map { "root" to "child$it" }.toTypedArray())
        var state = DependencyExploration.start(slice, "root")
        assertEquals(4, state.positions.size)
        state = state.expand(slice, ExplorationDirection.USES)
        assertEquals(8, state.positions.size)
        repeat(30) { state = state.expand(slice, ExplorationDirection.USES) }
        assertEquals(DependencyExploration.MAX_VISIBLE, state.positions.size)
        assertTrue(state.message.orEmpty().contains("80"))
        assertEquals(state.positions.size, state.positions.values.toSet().size)
    }

    @Test
    fun `trace follows directed path and reveals intermediate nodes`() {
        val slice = graph("a" to "b", "b" to "c", "c" to "d", "unrelated" to "b")
        val state = DependencyExploration.start(slice, "a").traceTo(slice, "d")
        assertEquals(setOf("a", "b", "c", "d"), state.positions.keys)
        assertEquals(setOf("a" to "b", "b" to "c", "c" to "d"), state.highlightedEdges.map { it.from to it.to }.toSet())
        val reverse = state.select(slice, "d").traceTo(slice, "a")
        assertTrue(reverse.highlightedEdges.isEmpty())
        assertTrue(reverse.message.orEmpty().startsWith("No dependency path"))
    }

    @Test
    fun `cycle excludes branches and unresolved phantom endpoints`() {
        val slice = graph("a" to "b", "b" to "c", "c" to "a", "c" to "leaf").let {
            it.copy(edges = it.edges + GraphEdge("leaf", "missing") + GraphEdge("missing", "leaf"))
        }
        val state = DependencyExploration.start(slice, "a").showCycle(slice)
        assertEquals(3, state.highlightedEdges.size)
        assertFalse(state.highlightedEdges.any { it.to == "leaf" })
        assertFalse("missing" in state.positions)
        assertTrue(state.select(slice, "leaf").showCycle(slice).highlightedEdges.isEmpty())
    }

    @Test
    fun `impact follows incoming edges without including dependencies`() {
        val slice = graph("caller" to "target", "caller2" to "caller", "target" to "library")
        val state = DependencyExploration.start(slice, "target").showImpact(slice)
        assertEquals(setOf("caller" to "target", "caller2" to "caller"), state.highlightedEdges.map { it.from to it.to }.toSet())
        assertTrue(state.message.orEmpty().contains("2 dependents"))
        assertTrue(state.message.orEmpty().contains("1 direct"))
    }

    @Test
    fun `refresh replaces stale evidence without moving nodes`() {
        val slice = graph("a" to "b")
        val state = DependencyExploration.start(slice, "a").inspect(slice, slice.edges.single())
        val updated = slice.copy(edges = listOf(slice.edges.single().copy(evidence = SourceEvidence(7, "import b"))))
        val refreshed = state.reconcile(updated)
        assertEquals(state.positions, refreshed.positions)
        assertEquals(7, refreshed.selectedEdge?.evidence?.line)
        val removed = refreshed.reconcile(updated.copy(edges = emptyList()))
        assertNull(removed.selectedEdge)
        assertTrue(removed.message.orEmpty().contains("no longer"))
    }

    @Test
    fun `refresh prunes removed selected nodes and keeps a valid selection`() {
        val slice = graph("a" to "b", "b" to "c")
        val state = DependencyExploration.start(slice, "b").showImpact(slice)
        val refreshed = state.reconcile(slice.copy(nodes = slice.nodes.filter { it.id != "b" }, edges = emptyList()))
        assertFalse("b" in refreshed.positions)
        assertTrue(refreshed.selectedId in refreshed.positions)
        assertTrue(refreshed.highlightedEdges.isEmpty())
        assertNull(refreshed.highlight)
    }

    @Test
    fun `back restores camera and expansion state`() {
        val slice = graph("a" to "b", "b" to "c", "c" to "d")
        val view = ExplorationViewState()
        view.start(slice, "a")
        view.camera.pan = Offset(123f, 456f)
        view.camera.scale = 1.4f
        val original = view.exploration
        view.update(view.exploration.select(slice, "b").expand(slice, ExplorationDirection.USES))
        view.camera.pan = Offset.Zero
        view.camera.scale = .7f
        view.back(slice)
        assertEquals(original, view.exploration)
        assertEquals(Offset(123f, 456f), view.camera.pan)
        assertEquals(1.4f, view.camera.scale)
    }

    @Test
    fun `existing session survives repeated initialization with another active file`() {
        val slice = graph("a" to "b", "b" to "c")
        val view = ExplorationViewState()
        view.onSliceChanged(slice, "a")
        view.update(view.exploration.select(slice, "b"))
        view.camera.pan = Offset(42f, 77f)
        view.onSliceChanged(slice, "c")
        assertEquals("b", view.exploration.selectedId)
        assertEquals(Offset(42f, 77f), view.camera.pan)
    }

    @Test
    fun `relationships keep their actual kind during inspection`() {
        val slice = graph("a" to "b").let { it.copy(edges = listOf(GraphEdge("a", "b", EdgeKind.IMPLEMENTS))) }
        val state = DependencyExploration.start(slice, "a").inspect(slice, slice.edges.single())
        assertEquals(EdgeKind.IMPLEMENTS, state.selectedEdge?.kind)
    }
}
