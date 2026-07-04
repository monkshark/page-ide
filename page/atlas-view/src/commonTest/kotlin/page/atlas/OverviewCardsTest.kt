package page.atlas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import page.atlas.graph.ModuleEdge
import page.atlas.graph.ModuleGraph
import page.atlas.graph.ModuleNode
import page.atlas.graph.NodeKind
import page.atlas.render.buildOverviewCards
import page.atlas.render.layeredModuleLayout
import page.shared.path.FilePath

class OverviewCardsTest {
    private fun mod(id: String, fileCount: Int = 1, external: Boolean = false) =
        ModuleNode(id, id, FilePath.of(id), fileCount, NodeKind.WORKSPACE_FILE, "kotlin", external = external)

    private fun scene(graph: ModuleGraph) = buildOverviewCards(graph, layeredModuleLayout(graph))

    @Test
    fun chainPlacesCardsInAscendingColumns() {
        val graph = ModuleGraph(
            nodes = listOf(mod("a"), mod("b"), mod("c"), mod("d")),
            edges = listOf(ModuleEdge("a", "b", 1), ModuleEdge("b", "c", 1), ModuleEdge("c", "d", 1)),
        )
        val scene = scene(graph)
        assertEquals(4, scene.bands.size)
        val xs = listOf("a", "b", "c", "d").map { scene.boxes.getValue(it).x }
        assertEquals(xs.sorted(), xs)
        assertEquals(4, xs.toSet().size)
        assertEquals(0, scene.hiddenCount)
    }

    @Test
    fun externalCardsUseDashedExternalDimensions() {
        val graph = ModuleGraph(
            nodes = listOf(mod("app"), mod("lib", external = true)),
            edges = listOf(ModuleEdge("app", "lib", 1)),
        )
        val scene = scene(graph)
        val lib = scene.boxes.getValue("lib")
        assertEquals(156f, lib.w)
        assertEquals(50f, lib.h)
        assertEquals(132f, scene.boxes.getValue("app").w)
    }

    @Test
    fun withinColumnTallerFileCountStacksFirst() {
        val graph = ModuleGraph(
            nodes = listOf(mod("big", fileCount = 40), mod("small", fileCount = 2)),
            edges = emptyList(),
        )
        val scene = scene(graph)
        val big = scene.boxes.getValue("big")
        val small = scene.boxes.getValue("small")
        assertEquals(0f, big.y)
        assertTrue(big.y < small.y)
        assertTrue(big.h > small.h)
    }

    @Test
    fun capLimitsVisibleToLargestModules() {
        val nodes = (1..30).map { mod("f$it", fileCount = it) }
        val scene = scene(ModuleGraph(nodes = nodes, edges = emptyList()))
        assertEquals(24, scene.visible.size)
        assertEquals(6, scene.hiddenCount)
        assertTrue("f30" in scene.visible)
        assertFalse("f1" in scene.visible)
    }

    @Test
    fun tallLayerWrapsIntoBalancedSubColumns() {
        val nodes = (1..14).map { mod("m$it", fileCount = 5) }
        val scene = scene(ModuleGraph(nodes = nodes, edges = emptyList()))
        val perColumn = scene.boxes.values.groupBy { it.x }.mapValues { it.value.size }
        assertTrue(perColumn.size >= 2, "tall layer wraps into multiple sub-columns")
        assertTrue(perColumn.values.max() <= 6, "no sub-column exceeds the row cap")
        assertEquals(1, scene.bands.size)
        assertEquals(14, scene.boxes.size)
    }

    @Test
    fun emptyGraphHasNoCards() {
        val scene = scene(ModuleGraph.EMPTY)
        assertTrue(scene.boxes.isEmpty())
        assertTrue(scene.bands.isEmpty())
        assertEquals(0, scene.hiddenCount)
    }
}
