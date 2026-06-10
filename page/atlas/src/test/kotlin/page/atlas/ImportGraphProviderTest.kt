package page.atlas

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import page.atlas.analyzer.ImportGraphProvider
import page.atlas.graph.GraphSlice
import page.atlas.graph.NodeKind

class ImportGraphProviderTest {

    @Test
    fun `slice has active center with workspace and external nodes`(@TempDir root: Path) {
        val helper = root.resolve("src/com/example/util/Helper.kt")
        Files.createDirectories(helper.parent)
        Files.writeString(helper, "package com.example.util")
        val active = root.resolve("src/com/example/Main.kt")
        val text = """
            package com.example

            import com.example.util.Helper
            import java.util.List
        """.trimIndent()
        Files.writeString(active, text)
        val slice = ImportGraphProvider(root).nodesForFile(active, text)
        assertEquals(3, slice.nodes.size)
        assertEquals(2, slice.edges.size)
        val activeNode = slice.nodes.single { it.kind == NodeKind.ACTIVE }
        assertEquals("Main.kt", activeNode.label)
        val workspaceNode = slice.nodes.single { it.kind == NodeKind.WORKSPACE_FILE }
        assertEquals(helper.toAbsolutePath().normalize(), workspaceNode.path)
        val external = slice.nodes.single { it.kind == NodeKind.EXTERNAL }
        assertEquals("java.util.List", external.label)
        assertEquals(null, external.path)
        assertTrue(slice.edges.all { it.from == activeNode.id })
    }

    @Test
    fun `duplicate imports are deduplicated`(@TempDir root: Path) {
        val active = root.resolve("main.py")
        val text = """
            import json
            import json
        """.trimIndent()
        Files.writeString(active, text)
        val slice = ImportGraphProvider(root).nodesForFile(active, text)
        assertEquals(2, slice.nodes.size)
        assertEquals(1, slice.edges.size)
    }

    @Test
    fun `same input yields equal slices`(@TempDir root: Path) {
        val active = root.resolve("main.py")
        val text = "import json"
        Files.writeString(active, text)
        val provider = ImportGraphProvider(root)
        assertEquals(provider.nodesForFile(active, text), provider.nodesForFile(active, text))
    }

    @Test
    fun `node count is capped at 100`(@TempDir root: Path) {
        val active = root.resolve("main.py")
        val text = (1..101).joinToString("\n") { "import module$it" }
        Files.writeString(active, text)
        val slice = ImportGraphProvider(root).nodesForFile(active, text)
        assertEquals(100, slice.nodes.size)
        assertEquals(99, slice.edges.size)
    }

    @Test
    fun `unsupported extension yields empty slice`(@TempDir root: Path) {
        val active = root.resolve("notes.txt")
        Files.writeString(active, "import x")
        assertEquals(GraphSlice.EMPTY, ImportGraphProvider(root).nodesForFile(active, "import x"))
    }
}
