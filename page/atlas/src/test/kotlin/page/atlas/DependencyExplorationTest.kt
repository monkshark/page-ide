package page.atlas

import androidx.compose.ui.geometry.Offset
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
import page.shared.path.FilePath

class DependencyExplorationTest {
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
