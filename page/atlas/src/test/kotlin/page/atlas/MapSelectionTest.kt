package page.atlas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import page.atlas.render.MapEdge
import page.atlas.render.MapNeighbors
import page.atlas.render.isMapBoxDimmed
import page.atlas.render.mapNeighbors

class MapSelectionTest {

    private val core = "ws\\core"
    private val coreA = "ws\\core\\A.kt"
    private val uiC = "ws\\ui\\C.kt"
    private val uiD = "ws\\ui\\D.kt"
    private val libE = "ws\\lib\\E.kt"

    private val edges = listOf(
        MapEdge(uiC, coreA, 2),
        MapEdge(uiD, coreA, 1),
        MapEdge(coreA, libE, 3),
    )

    @Test
    fun `null selection yields empty neighbors`() {
        assertEquals(MapNeighbors.EMPTY, mapNeighbors(edges, null))
        assertFalse(MapNeighbors.EMPTY.any)
    }

    @Test
    fun `selection splits dependents and dependencies by edge direction`() {
        val out = mapNeighbors(edges, coreA)
        assertEquals(setOf(uiC, uiD), out.dependents)
        assertEquals(setOf(libE), out.dependencies)
        assertEquals(3, out.dependentWeight)
        assertEquals(3, out.dependencyWeight)
        assertTrue(out.any)
    }

    @Test
    fun `node without edges has no neighbors`() {
        val out = mapNeighbors(edges, "ws\\other\\Z.kt")
        assertEquals(MapNeighbors.EMPTY, out)
    }

    @Test
    fun `self edges are ignored`() {
        val out = mapNeighbors(listOf(MapEdge(coreA, coreA, 5)), coreA)
        assertEquals(MapNeighbors.EMPTY, out)
    }

    @Test
    fun `unrelated boxes are dimmed while neighbors stay lit`() {
        val out = mapNeighbors(edges, coreA)
        assertTrue(isMapBoxDimmed("ws\\other\\Z.kt", coreA, out))
        assertFalse(isMapBoxDimmed(uiC, coreA, out))
        assertFalse(isMapBoxDimmed(libE, coreA, out))
        assertFalse(isMapBoxDimmed(coreA, coreA, out))
    }

    @Test
    fun `containers and contents of the selection are never dimmed`() {
        val out = mapNeighbors(listOf(MapEdge(core, libE, 1)), core)
        assertFalse(isMapBoxDimmed(coreA, core, out))
        assertFalse(isMapBoxDimmed("ws", core, out))
        assertTrue(isMapBoxDimmed("ws\\core-extra\\F.kt", core, out))
    }

    @Test
    fun `nothing dims when the selection has no edges`() {
        assertFalse(isMapBoxDimmed(uiC, "ws\\other\\Z.kt", mapNeighbors(edges, "ws\\other\\Z.kt")))
        assertFalse(isMapBoxDimmed(uiC, null, mapNeighbors(edges, coreA)))
    }
}
