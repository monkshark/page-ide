package page.atlas

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import page.atlas.graph.GraphEdge
import page.atlas.graph.GraphNode
import page.atlas.graph.GraphSlice
import page.atlas.graph.MODULE_MAX
import page.atlas.graph.NodeKind
import page.atlas.graph.aggregateModules

class ModuleGraphTest {

    private fun id(p: String): String = Path.of(p).toString()

    private fun node(p: String, kind: NodeKind = NodeKind.WORKSPACE_FILE): GraphNode {
        val path = Path.of(p)
        return GraphNode(path.toString(), path.fileName.toString(), path, kind)
    }

    private fun edge(from: String, to: String): GraphEdge = GraphEdge(id(from), id(to))

    @Test
    fun `top level directories collapse into module nodes with file counts`() {
        val nodes = ArrayList<GraphNode>()
        repeat(1500) { nodes += node("ws/core/File$it.kt") }
        repeat(800) { nodes += node("ws/ui/View$it.kt") }
        repeat(200) { nodes += node("ws/data/Repo$it.kt") }
        val graph = aggregateModules(GraphSlice(nodes, emptyList()))

        assertEquals(3, graph.nodes.size, "thousands of files collapse to three modules")
        assertEquals(1500, graph.nodes.first { it.id == id("ws/core") }.fileCount)
        assertEquals(800, graph.nodes.first { it.id == id("ws/ui") }.fileCount)
        assertEquals(200, graph.nodes.first { it.id == id("ws/data") }.fileCount)
        assertEquals(setOf("core", "ui", "data"), graph.nodes.mapTo(HashSet()) { it.label })
    }

    @Test
    fun `cross module edges aggregate into weighted module edges`() {
        val slice = GraphSlice(
            listOf(
                node("ws/a/x.kt"),
                node("ws/a/y.kt"),
                node("ws/b/z.kt"),
            ),
            listOf(
                edge("ws/a/x.kt", "ws/b/z.kt"),
                edge("ws/a/y.kt", "ws/b/z.kt"),
                edge("ws/a/x.kt", "ws/a/y.kt"),
            ),
        )
        val graph = aggregateModules(slice)
        assertEquals(1, graph.edges.size, "same module edge dropped, cross module edges merged")
        val merged = graph.edges.single()
        assertEquals(id("ws/a"), merged.from)
        assertEquals(id("ws/b"), merged.to)
        assertEquals(2, merged.weight)
    }

    @Test
    fun `module language is the dominant file extension`() {
        val slice = GraphSlice(
            listOf(
                node("ws/svc/A.kt"),
                node("ws/svc/B.kt"),
                node("ws/svc/legacy.java"),
            ),
            emptyList(),
        )
        val graph = aggregateModules(slice)
        assertEquals("kt", graph.nodes.single().language)
    }

    @Test
    fun `active path marks its owning module`() {
        val slice = GraphSlice(
            listOf(node("ws/a/x.kt"), node("ws/a/y.kt"), node("ws/b/z.kt")),
            emptyList(),
        )
        val graph = aggregateModules(slice, activePath = Path.of("ws/a/y.kt"))
        assertEquals(NodeKind.ACTIVE, graph.nodes.first { it.id == id("ws/a") }.kind)
        assertEquals(NodeKind.WORKSPACE_FILE, graph.nodes.first { it.id == id("ws/b") }.kind)
    }

    @Test
    fun `module cap drops smallest modules and conserves the file total`() {
        val nodes = ArrayList<GraphNode>()
        var total = 0
        for (m in 0 until MODULE_MAX + 10) {
            val files = if (m < MODULE_MAX) 5 else 1
            repeat(files) { nodes += node("ws/m%03d/F%d.kt".format(m, it)) }
            total += files
        }
        val graph = aggregateModules(GraphSlice(nodes, emptyList()))
        assertEquals(MODULE_MAX, graph.nodes.size)
        assertEquals(10, graph.droppedModules)
        assertEquals(10, graph.droppedFiles)
        assertEquals(total, graph.nodes.sumOf { it.fileCount } + graph.droppedFiles, "no files lost silently")
    }

    @Test
    fun `empty slice yields empty graph`() {
        val external = GraphNode("ext", "ext", null, NodeKind.EXTERNAL)
        assertEquals(0, aggregateModules(GraphSlice(listOf(external), emptyList())).nodes.size)
    }

    @Test
    fun `same input produces identical aggregation`() {
        val slice = GraphSlice(
            listOf(node("ws/a/x.kt"), node("ws/b/y.kt")),
            listOf(edge("ws/a/x.kt", "ws/b/y.kt")),
        )
        val a = aggregateModules(slice)
        val b = aggregateModules(slice)
        assertEquals(a.nodes.toSet(), b.nodes.toSet())
        assertEquals(a.edges.toSet(), b.edges.toSet())
        assertTrue(a.edges.isNotEmpty())
    }
}
