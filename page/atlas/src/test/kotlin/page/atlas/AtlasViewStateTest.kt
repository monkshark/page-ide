package page.atlas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import page.atlas.graph.GraphEdge
import page.atlas.graph.GraphNode
import page.atlas.graph.GraphSlice
import page.atlas.graph.NodeKind
import page.atlas.render.AtlasViewState

class AtlasViewStateTest {

    private fun slice(vararg ids: String) = GraphSlice(
        nodes = ids.map { GraphNode(it, it, null, NodeKind.WORKSPACE_FILE) },
        edges = emptyList<GraphEdge>(),
    )

    @Test
    fun selectionSurvivesEqualSlice() {
        val view = AtlasViewState()
        view.onSliceChanged(slice("a", "b"))
        view.selectedId = "a"
        view.onSliceChanged(slice("a", "b"))
        assertEquals("a", view.selectedId)
    }

    @Test
    fun selectionClearsWhenSliceChanges() {
        val view = AtlasViewState()
        view.onSliceChanged(slice("a", "b"))
        view.selectedId = "a"
        view.onSliceChanged(slice("a", "c"))
        assertNull(view.selectedId)
    }

    @Test
    fun pendingFocusSelectsNodeWhenPresent() {
        val view = AtlasViewState()
        view.onSliceChanged(slice("a", "b"))
        view.pendingFocusId = "b"
        view.onSliceChanged(slice("a", "b", "c"))
        assertEquals("b", view.selectedId)
        assertNull(view.pendingFocusId)
    }

    @Test
    fun pendingFocusConsumedOnEqualSlice() {
        val view = AtlasViewState()
        view.onSliceChanged(slice("a", "b"))
        view.pendingFocusId = "a"
        view.onSliceChanged(slice("a", "b"))
        assertEquals("a", view.selectedId)
        assertNull(view.pendingFocusId)
    }

    @Test
    fun pendingFocusSurvivesSliceWithoutNode() {
        val view = AtlasViewState()
        view.onSliceChanged(slice("a", "b"))
        view.pendingFocusId = "z"
        view.onSliceChanged(slice("a", "c"))
        assertNull(view.selectedId)
        assertEquals("z", view.pendingFocusId)
    }

    @Test
    fun cameraResetsOncePerSubject() {
        val view = AtlasViewState()
        view.onCameraSubject("file-a", projectMode = false)
        view.zoomUser = 2.5f
        view.pitch = 1.1f
        view.onCameraSubject("file-a", projectMode = false)
        assertEquals(2.5f, view.zoomUser)
        assertEquals(1.1f, view.pitch)
        view.onCameraSubject("file-b", projectMode = false)
        assertEquals(1f, view.zoomUser)
        assertEquals(0.5f, view.pitch)
    }

    @Test
    fun autoRotateAdvancesYawOncePerFrame() {
        val view = AtlasViewState()
        val before = view.yaw
        view.autoRotateTick(10_000_000_000L)
        view.autoRotateTick(11_000_000_000L)
        val after = view.yaw
        assertTrue(after > before)
        view.autoRotateTick(11_000_000_000L)
        assertEquals(after, view.yaw)
    }

    @Test
    fun autoRotatePausesWhileAnyInstanceHolds() {
        val view = AtlasViewState()
        val owner = Any()
        view.autoRotateTick(10_000_000_000L)
        view.holdRotation(owner, true)
        val before = view.yaw
        view.autoRotateTick(11_000_000_000L)
        assertEquals(before, view.yaw)
        view.holdRotation(owner, false)
        view.autoRotateTick(12_000_000_000L)
        assertTrue(view.yaw > before)
    }

    @Test
    fun autoRotateWaitsAfterInteraction()  {
        val view = AtlasViewState()
        view.autoRotateTick(10_000_000_000L)
        view.lastInteractNanos = 11_000_000_000L
        val before = view.yaw
        view.autoRotateTick(12_000_000_000L)
        assertEquals(before, view.yaw)
        view.autoRotateTick(15_000_000_000L)
        assertTrue(view.yaw > before)
    }
}
