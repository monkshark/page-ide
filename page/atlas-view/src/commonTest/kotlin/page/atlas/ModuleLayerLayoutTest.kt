package page.atlas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import page.atlas.graph.ModuleEdge
import page.atlas.graph.ModuleGraph
import page.atlas.graph.ModuleLayer
import page.atlas.graph.ModuleNode
import page.atlas.graph.NodeKind
import page.atlas.render.layeredModuleLayout
import page.shared.path.FilePath

class ModuleLayerLayoutTest {
    private fun mod(id: String, fileCount: Int = 1, external: Boolean = false) =
        ModuleNode(id, id, FilePath.of(id), fileCount, NodeKind.WORKSPACE_FILE, "kotlin", external = external)

    @Test
    fun chainPlacesEachLayerInItsOwnColumn() {
        val graph = ModuleGraph(
            nodes = listOf(mod("a"), mod("b"), mod("c"), mod("d")),
            edges = listOf(ModuleEdge("a", "b", 1), ModuleEdge("b", "c", 1), ModuleEdge("c", "d", 1)),
        )
        val layout = layeredModuleLayout(graph)
        assertEquals(
            listOf(ModuleLayer.ENTRY, ModuleLayer.FEATURES, ModuleLayer.CORE, ModuleLayer.PLATFORM),
            layout.columns,
        )
        val xs = listOf("a", "b", "c", "d").map { layout.positions.getValue(it).x }
        assertEquals(xs.sorted(), xs)
        assertTrue(xs.toSet().size == 4)
    }

    @Test
    fun emptyLayersDoNotLeaveColumnGaps() {
        val graph = ModuleGraph(
            nodes = listOf(mod("app"), mod("lib", external = true)),
            edges = listOf(ModuleEdge("app", "lib", 1)),
        )
        val layout = layeredModuleLayout(graph)
        assertEquals(listOf(ModuleLayer.ENTRY, ModuleLayer.EXTERNAL), layout.columns)
        assertEquals(0.0, layout.positions.getValue("app").x)
        assertTrue(layout.positions.getValue("lib").x > layout.positions.getValue("app").x)
    }

    @Test
    fun withinLayerStacksByFileCountDescending() {
        val graph = ModuleGraph(
            nodes = listOf(mod("small", fileCount = 2), mod("big", fileCount = 40)),
            edges = emptyList(),
        )
        val layout = layeredModuleLayout(graph)
        val big = layout.positions.getValue("big")
        val small = layout.positions.getValue("small")
        assertEquals(big.x, small.x)
        assertTrue(big.y < small.y)
    }

    @Test
    fun emptyGraphIsEmpty() {
        val layout = layeredModuleLayout(ModuleGraph.EMPTY)
        assertTrue(layout.positions.isEmpty())
        assertTrue(layout.columns.isEmpty())
        assertEquals(0.0, layout.width)
    }
}
