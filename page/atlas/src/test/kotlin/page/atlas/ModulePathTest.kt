package page.atlas

import kotlin.test.Test
import kotlin.test.assertEquals
import page.atlas.graph.ModuleEdge
import page.atlas.graph.ModuleGraph
import page.atlas.graph.ModuleNode
import page.atlas.graph.NodeKind
import page.atlas.graph.moduleDependsOn
import page.atlas.graph.modulePath
import page.atlas.graph.moduleUsedBy
import java.nio.file.Path
import kotlin.test.assertNull

class ModulePathTest {

    private fun mod(id: String): ModuleNode =
        ModuleNode(id, id, Path.of(id), 1, NodeKind.WORKSPACE_FILE, "kt")

    private val graph = ModuleGraph(
        nodes = listOf(mod("a"), mod("b"), mod("c"), mod("d")),
        edges = listOf(
            ModuleEdge("a", "b", 3),
            ModuleEdge("a", "c", 1),
            ModuleEdge("d", "a", 5),
            ModuleEdge("a", "a", 9),
        ),
    )

    @Test
    fun `depends on lists outgoing targets sorted by weight`() {
        val out = moduleDependsOn(graph, "a")
        assertEquals(listOf("b", "c"), out.map { it.node.id })
        assertEquals(3, out.first().weight)
    }

    @Test
    fun `used by lists incoming sources`() {
        val incoming = moduleUsedBy(graph, "a")
        assertEquals(listOf("d"), incoming.map { it.node.id })
        assertEquals(5, incoming.single().weight)
    }

    @Test
    fun `self edges are excluded from both directions`() {
        assertEquals(emptyList(), moduleDependsOn(graph, "a").filter { it.node.id == "a" })
        assertEquals(emptyList(), moduleUsedBy(graph, "a").filter { it.node.id == "a" })
    }

    @Test
    fun `unknown module has no links`() {
        assertEquals(emptyList(), moduleDependsOn(graph, "zzz"))
        assertEquals(emptyList(), moduleUsedBy(graph, "zzz"))
    }

    @Test
    fun `dangling edge target without node is dropped`() {
        val g = ModuleGraph(listOf(mod("a")), listOf(ModuleEdge("a", "ghost", 2)))
        assertEquals(emptyList(), moduleDependsOn(g, "a"))
    }

    @Test
    fun `module path follows directed edges by shortest hop`() {
        assertEquals(listOf("a", "b"), modulePath(graph, "a", "b"))
        assertEquals(listOf("d", "a", "b"), modulePath(graph, "d", "b"))
        assertEquals(listOf("a"), modulePath(graph, "a", "a"))
    }

    @Test
    fun `module path returns null when no directed route exists`() {
        assertNull(modulePath(graph, "b", "a"))
        assertNull(modulePath(graph, "b", "zzz"))
    }

    @Test
    fun `module path terminates on cycles`() {
        val cyclic = ModuleGraph(
            nodes = listOf(mod("x"), mod("y"), mod("z")),
            edges = listOf(ModuleEdge("x", "y", 1), ModuleEdge("y", "x", 1)),
        )
        assertEquals(listOf("x", "y"), modulePath(cyclic, "x", "y"))
        assertNull(modulePath(cyclic, "x", "z"))
    }
}
