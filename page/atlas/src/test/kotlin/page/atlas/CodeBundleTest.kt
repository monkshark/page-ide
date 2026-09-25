package page.atlas

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import page.atlas.export.CodeBundles
import page.atlas.graph.GraphEdge
import page.atlas.graph.GraphNode
import page.atlas.graph.GraphSlice
import page.atlas.graph.NodeKind
import page.atlas.graph.SourceEvidence
import page.shared.path.FilePath

class CodeBundleTest {
    private val slice = GraphSlice(
        listOf("screen", "store", "model").map { GraphNode(it, "$it.kt", FilePath.of("/project/src/$it.kt"), NodeKind.WORKSPACE_FILE) } +
            GraphNode("external", "library", null, NodeKind.EXTERNAL),
        listOf(GraphEdge("screen", "store"), GraphEdge("store", "model", evidence = SourceEvidence(7, "import model")),
            GraphEdge("store", "external")),
    )

    @Test
    fun `scopes distinguish dependencies from dependents and exclude externals`() {
        assertEquals(setOf("store", "model"), CodeBundles.candidates(slice, "store", true, false))
        assertEquals(setOf("store", "screen"), CodeBundles.candidates(slice, "store", false, true))
        assertEquals(emptySet(), CodeBundles.candidates(slice, "missing", true, true))
    }

    @Test
    fun `only selected source files are read and exported relationships match their contents`() {
        val read = mutableListOf<String>()
        val bundle = CodeBundles.build(slice, setOf("store", "model", "external", "missing")) { path, _ ->
            read.add(path.fileName.toString())
            "class ${path.fileName} { val title = \"안녕\" }"
        }
        assertEquals(listOf("model.kt", "store.kt"), read)
        assertTrue(bundle.text.contains("store.kt -> model.kt (import) at line 8"))
        assertTrue(bundle.text.contains("안녕"))
        assertFalse(bundle.text.contains("screen.kt"))
        assertFalse(bundle.text.contains("/project"))
        assertEquals(2, bundle.includedCount)
    }

    @Test
    fun `binary missing and oversized sources are reported without stopping export`() {
        val bundle = CodeBundles.build(slice, setOf("screen", "store", "model")) { path, limit ->
            when (path.fileName.toString()) {
                "model.kt" -> "\u0000binary"
                "screen.kt" -> throw java.nio.file.NoSuchFileException(path.toString())
                else -> "x".repeat(limit)
            }
        }
        assertEquals(1, bundle.includedCount)
        assertEquals(CodeBundles.FILE_LIMIT, bundle.files.single { it.id == "store" }.text!!.length)
        assertTrue(bundle.files.all { it.notice != null })
        assertFalse(bundle.text.contains("store.kt -> model.kt"))
    }

    @Test
    fun `total content stays bounded when many files are selected`() {
        val nodes = (0..20).map { GraphNode("$it", "$it.kt", FilePath.of("/repo/$it.kt"), NodeKind.WORKSPACE_FILE) }
        val bundle = CodeBundles.build(GraphSlice(nodes, emptyList()), nodes.mapTo(hashSetOf()) { it.id }) { _, count -> "x".repeat(count) }
        assertEquals(CodeBundles.CONTENT_LIMIT, bundle.files.sumOf { it.text?.length ?: 0 })
        assertTrue(bundle.files.any { it.notice == "Bundle size limit reached" })
    }

    @Test
    fun `reader loads saved UTF-8 content and reports deleted files`() {
        val dir = Files.createTempDirectory("atlas-context")
        val file = dir.resolve("sample.kt")
        try {
            Files.writeString(file, "val name = \"코드\"")
            val graph = GraphSlice(listOf(GraphNode("source", "sample.kt", FilePath.of(file.toString()), NodeKind.WORKSPACE_FILE)), emptyList())
            assertTrue(CodeBundles.build(graph, setOf("source")).text.contains("코드"))
            Files.delete(file)
            assertEquals(0, CodeBundles.build(graph, setOf("source")).includedCount)
        } finally {
            Files.deleteIfExists(file)
            Files.deleteIfExists(dir)
        }
    }
}
