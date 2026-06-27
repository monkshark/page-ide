package page.atlas

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import page.atlas.analyzer.DeclarationIndex
import page.atlas.analyzer.ImportExtractor
import page.atlas.analyzer.SymbolDecl
import page.atlas.analyzer.WorkspaceIndex

class DeclarationIndexTest {

    private fun write(root: Path, relative: String, text: String): Path {
        val file = root.resolve(relative)
        Files.createDirectories(file.parent)
        Files.writeString(file, text)
        return file.toAbsolutePath().normalize()
    }

    private fun realAnalyzer(): (Path) -> page.atlas.analyzer.FileAnalysis? =
        { file -> ImportExtractor.analyze(file, Files.readString(file)) }

    @Test
    fun `every symbol of a multi-symbol file resolves to that file`(@TempDir root: Path) {
        val model = write(
            root, "src/page/atlas/graph/GraphModel.kt",
            """
                package page.atlas.graph

                data class GraphSlice(val nodes: List<GraphNode>)
                class GraphNode(val id: String)
                enum class NodeKind { ACTIVE, EXTERNAL }
            """.trimIndent(),
        )
        val index = DeclarationIndex(WorkspaceIndex(root), realAnalyzer())
        index.refreshIfStale()
        assertEquals(model, index.fileForFqn("page.atlas.graph.GraphSlice"))
        assertEquals(model, index.fileForFqn("page.atlas.graph.GraphNode"))
        assertEquals(model, index.fileForFqn("page.atlas.graph.NodeKind"))
        assertNull(index.fileForFqn("page.atlas.graph.Missing"))
    }

    @Test
    fun `same fqn declared in two files is a collision`(@TempDir root: Path) {
        val a = write(root, "module-a/Config.kt", "package app\nclass Config")
        val b = write(root, "module-b/Other.kt", "package app\nclass Config")
        val index = DeclarationIndex(WorkspaceIndex(root), realAnalyzer())
        index.refreshIfStale()
        assertNull(index.fileForFqn("app.Config"))
        assertEquals(setOf(a, b), index.candidatesForFqn("app.Config").toSet())
    }

    @Test
    fun `files in package are grouped`(@TempDir root: Path) {
        val a = write(root, "a/A.kt", "package shared\nclass A")
        val b = write(root, "b/Bee.kt", "package shared\nobject Bee")
        write(root, "c/C.kt", "package other\nclass C")
        val index = DeclarationIndex(WorkspaceIndex(root), realAnalyzer())
        index.refreshIfStale()
        assertEquals(setOf(a, b), index.filesInPackage("shared").toSet())
        assertEquals(1, index.filesInPackage("other").size)
    }

    @Test
    fun `declarationFor resolves an unambiguous fqn to file and line`(@TempDir root: Path) {
        val model = write(
            root, "src/page/atlas/graph/GraphModel.kt",
            "package page.atlas.graph\n\nclass GraphSlice\n\nclass GraphNode\n",
        )
        val index = DeclarationIndex(WorkspaceIndex(root), realAnalyzer())
        index.refreshIfStale()
        assertEquals(model to 2, index.declarationFor("page.atlas.graph.GraphSlice"))
        assertEquals(model to 4, index.declarationFor("page.atlas.graph.GraphNode"))
        assertNull(index.declarationFor("page.atlas.graph.Missing"))
    }

    @Test
    fun `declarationFor is null on collision`(@TempDir root: Path) {
        write(root, "a/Config.kt", "package app\nclass Config")
        write(root, "b/Other.kt", "package app\nclass Config")
        val index = DeclarationIndex(WorkspaceIndex(root), realAnalyzer())
        index.refreshIfStale()
        assertNull(index.declarationFor("app.Config"))
    }

    @Test
    fun `declarationsInFile lists symbols with lines`(@TempDir root: Path) {
        val file = write(root, "p/A.kt", "package p\n\nclass A\n\nfun b() {}\n")
        val index = DeclarationIndex(WorkspaceIndex(root), realAnalyzer())
        index.refreshIfStale()
        assertEquals(
            listOf(SymbolDecl("A", 2), SymbolDecl("b", 4)),
            index.declarationsInFile(file),
        )
    }

    @Test
    fun `index does not rebuild while workspace revision is stable`(@TempDir root: Path) {
        write(root, "A.kt", "package p\nclass A")
        write(root, "B.kt", "package p\nclass B")
        val calls = AtomicInteger(0)
        val index = DeclarationIndex(WorkspaceIndex(root)) { file ->
            calls.incrementAndGet()
            ImportExtractor.analyze(file, Files.readString(file))
        }
        index.refreshIfStale()
        val afterFirst = calls.get()
        assertTrue(afterFirst >= 2)
        index.refreshIfStale()
        assertEquals(afterFirst, calls.get())
        assertEquals(root.resolve("A.kt").toAbsolutePath().normalize(), index.fileForFqn("p.A"))
    }
}
