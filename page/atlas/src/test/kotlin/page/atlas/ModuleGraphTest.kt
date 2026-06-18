package page.atlas

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun `dominant subtree splits into balanced submodules instead of one blob`() {
        val nodes = ArrayList<GraphNode>()
        nodes += node("ws/build.gradle.kts")
        repeat(40) { nodes += node("ws/page/app/A$it.kt") }
        repeat(30) { nodes += node("ws/page/atlas/B$it.kt") }
        repeat(20) { nodes += node("ws/page/editor/C$it.kt") }
        val graph = aggregateModules(GraphSlice(nodes, emptyList()))

        val labels = graph.nodes.mapTo(HashSet()) { it.label }
        assertTrue("page/app" in labels, "dominant page subtree drilled into gradle modules: $labels")
        assertTrue("page/atlas" in labels, "dominant page subtree drilled into gradle modules: $labels")
        assertTrue("page/editor" in labels, "dominant page subtree drilled into gradle modules: $labels")
        assertEquals(40, graph.nodes.first { it.label == "page/app" }.fileCount)
        assertEquals(91, graph.nodes.sumOf { it.fileCount }, "every file owned by exactly one module")
    }

    @Test
    fun `module retains its owned files sorted by name`() {
        val slice = GraphSlice(
            listOf(node("ws/a/zeta.kt"), node("ws/a/alpha.kt"), node("ws/b/only.kt")),
            emptyList(),
        )
        val graph = aggregateModules(slice)
        val a = graph.nodes.first { it.id == id("ws/a") }
        assertEquals(listOf("alpha.kt", "zeta.kt"), a.files.map { it.name })
        assertEquals(a.fileCount, a.files.size)
        assertEquals(Path.of("ws/a/alpha.kt"), a.files.first().path)
    }

    @Test
    fun `scope root re-aggregates only that subtree into deeper modules`() {
        val nodes = ArrayList<GraphNode>()
        repeat(10) { nodes += node("ws/page/app/service/S$it.kt") }
        repeat(8) { nodes += node("ws/page/app/ui/U$it.kt") }
        repeat(5) { nodes += node("ws/page/atlas/A$it.kt") }
        val graph = aggregateModules(GraphSlice(nodes, emptyList()), scopeRoot = Path.of("ws/page/app"))

        val labels = graph.nodes.mapTo(HashSet()) { it.label }
        assertTrue("service" in labels, "drilled into app subtree: $labels")
        assertTrue("ui" in labels, "drilled into app subtree: $labels")
        assertEquals(18, graph.nodes.sumOf { it.fileCount }, "atlas subtree excluded by scope")
    }

    @Test
    fun `module is splittable when it has subdirs or many files but a lone file is not`() {
        val nodes = ArrayList<GraphNode>()
        repeat(180) { nodes += node("ws/flat/Z$it.kt") }
        nodes += node("ws/lib/a/X.kt")
        nodes += node("ws/lib/b/Y.kt")
        nodes += node("ws/solo/Only.kt")
        val graph = aggregateModules(GraphSlice(nodes, emptyList()))

        assertTrue(graph.nodes.first { it.label == "lib" }.splittable, "lib has subdirs a,b")
        assertTrue(graph.nodes.first { it.label == "flat" }.splittable, "flat holds many files, drillable to file level")
        assertFalse(graph.nodes.first { it.label == "solo" }.splittable, "solo holds a single file")
    }

    @Test
    fun `big folder split into a loose remainder stays splittable when it owns subdirs`() {
        val nodes = ArrayList<GraphNode>()
        nodes += node("ws/app/Main.kt")
        nodes += node("ws/app/Boot.kt")
        repeat(10) { nodes += node("ws/app/state/S$it.kt") }
        repeat(10) { nodes += node("ws/app/ui/U$it.kt") }
        repeat(5) { nodes += node("ws/lib/L$it.kt") }
        val graph = aggregateModules(GraphSlice(nodes, emptyList()))

        val app = graph.nodes.first { it.label == "app" }
        assertTrue(app.splittable, "app is a loose remainder but still owns subdirs state,ui")
        assertEquals(2, app.fileCount, "loose remainder owns only its direct files")
    }

    @Test
    fun `scope into a flat folder yields one node per file`() {
        val nodes = ArrayList<GraphNode>()
        repeat(6) { nodes += node("ws/pkg/F$it.kt") }
        val graph = aggregateModules(GraphSlice(nodes, emptyList()), scopeRoot = Path.of("ws/pkg"))

        assertEquals(6, graph.nodes.size, "each file becomes its own node")
        assertTrue(graph.nodes.all { it.fileCount == 1 }, "file-level nodes hold a single file")
        assertTrue(graph.nodes.none { it.splittable }, "single-file nodes are leaves")
        assertTrue(graph.nodes.any { it.label == "F0.kt" }, "labeled by file name: ${graph.nodes.map { it.label }}")
    }

    @Test
    fun `scope surfaces external dependencies as ghost nodes`() {
        val nodes = ArrayList<GraphNode>()
        repeat(3) { nodes += node("ws/pkg/F$it.kt") }
        nodes += node("ws/util/Helper.kt")
        val edges = listOf(
            edge("ws/pkg/F0.kt", "ws/util/Helper.kt"),
            edge("ws/util/Helper.kt", "ws/pkg/F1.kt"),
        )
        val graph = aggregateModules(GraphSlice(nodes, edges), scopeRoot = Path.of("ws/pkg"))

        val ghost = graph.nodes.firstOrNull { it.external }
        assertTrue(ghost != null, "util surfaces as ghost: ${graph.nodes.map { it.label to it.external }}")
        assertEquals("util", ghost!!.label)
        assertFalse(ghost.splittable, "ghost is not drillable")
        assertTrue(graph.edges.any { it.to == ghost.id }, "in-scope file depends on ghost")
        assertTrue(graph.edges.any { it.from == ghost.id }, "ghost uses in-scope file")
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
