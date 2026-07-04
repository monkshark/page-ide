package page.atlas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import page.atlas.graph.ModuleEdge
import page.atlas.graph.ModuleGraph
import page.atlas.graph.ModuleLayer
import page.atlas.graph.ModuleNode
import page.atlas.graph.NodeKind
import page.atlas.graph.classifyModuleLayers
import page.shared.path.FilePath

class ModuleLayersTest {
    private fun mod(id: String, external: Boolean = false) =
        ModuleNode(id, id, FilePath.of(id), 1, NodeKind.WORKSPACE_FILE, "kotlin", external = external)

    @Test
    fun chainSpreadsAcrossFourInternalLayers() {
        val graph = ModuleGraph(
            nodes = listOf(mod("a"), mod("b"), mod("c"), mod("d")),
            edges = listOf(ModuleEdge("a", "b", 1), ModuleEdge("b", "c", 1), ModuleEdge("c", "d", 1)),
        )
        val layers = classifyModuleLayers(graph)
        assertEquals(ModuleLayer.ENTRY, layers["a"])
        assertEquals(ModuleLayer.FEATURES, layers["b"])
        assertEquals(ModuleLayer.CORE, layers["c"])
        assertEquals(ModuleLayer.PLATFORM, layers["d"])
    }

    @Test
    fun sourceIsEntryAndDeepestIsPlatform() {
        val graph = ModuleGraph(
            nodes = listOf(mod("app"), mod("feat"), mod("core")),
            edges = listOf(ModuleEdge("app", "feat", 1), ModuleEdge("feat", "core", 1)),
        )
        val layers = classifyModuleLayers(graph)
        assertEquals(ModuleLayer.ENTRY, layers["app"])
        assertEquals(ModuleLayer.PLATFORM, layers["core"])
    }

    @Test
    fun externalModulesGoToExternalLayer() {
        val graph = ModuleGraph(
            nodes = listOf(mod("app"), mod("stdlib", external = true)),
            edges = listOf(ModuleEdge("app", "stdlib", 1)),
        )
        val layers = classifyModuleLayers(graph)
        assertEquals(ModuleLayer.EXTERNAL, layers["stdlib"])
        assertEquals(ModuleLayer.ENTRY, layers["app"])
    }

    @Test
    fun singleNodeIsEntry() {
        val graph = ModuleGraph(nodes = listOf(mod("solo")), edges = emptyList())
        assertEquals(ModuleLayer.ENTRY, classifyModuleLayers(graph)["solo"])
    }

    @Test
    fun cyclesStayBoundedAndInternal() {
        val graph = ModuleGraph(
            nodes = listOf(mod("x"), mod("y")),
            edges = listOf(ModuleEdge("x", "y", 1), ModuleEdge("y", "x", 1)),
        )
        val layers = classifyModuleLayers(graph)
        assertEquals(2, layers.size)
        assertTrue(layers.values.all { it != ModuleLayer.EXTERNAL })
    }
}
