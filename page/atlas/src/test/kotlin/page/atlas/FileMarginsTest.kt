package page.atlas

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import page.atlas.analyzer.ImportGraphProvider

class FileMarginsTest {

    private fun write(root: Path, rel: String, text: String): Path {
        val file = root.resolve(rel)
        Files.createDirectories(file.parent)
        Files.writeString(file, text)
        return file
    }

    private val transport = """
        package page.lsp

        import kotlinx.coroutines.CoroutineScope
        import page.core.Env

        class LspTransport(
            private val process: ProcessTransport,
            private val scope: CoroutineScope,
        ) {
            var client: LspClient? = null
            fun start() = process.start(Env.serverEnv())
        }
    """.trimIndent()

    private fun project(root: Path): Path {
        write(root, "src/page/core/Env.kt", "package page.core\n\nobject Env { fun serverEnv() = 1 }")
        write(root, "src/page/lsp/ProcessTransport.kt", "package page.lsp\n\nclass ProcessTransport { fun start(x: Int) {} }")
        write(root, "src/page/lsp/LspClient.kt", "package page.lsp\n\nclass LspClient(val transport: LspTransport)")
        write(root, "src/page/language/LspRouter.kt", "package page.language\n\nimport page.lsp.LspTransport\n\nclass LspRouter(val t: LspTransport)")
        write(root, "src/page/app/Notes.kt", "package page.app\n\nclass Notes {\n    val title = \"LspTransport\"\n}")
        return write(root, "src/page/lsp/LspTransport.kt", transport)
    }

    @Test
    fun `needs sit on the line each relation starts and leave libraries out`(@TempDir root: Path) {
        val active = project(root)
        val needs = ImportGraphProvider(root).margins(active, transport).needs
        assertEquals(listOf("Env.kt" to 3, "ProcessTransport.kt" to 6, "LspClient.kt" to 9), needs.map { it.node.label to it.line })
    }

    @Test
    fun `used by finds importers and same package users but not files that only mention the name`(@TempDir root: Path) {
        val active = project(root)
        val usedBy = ImportGraphProvider(root).margins(active, transport).usedBy
        assertEquals(listOf("LspClient.kt", "LspRouter.kt"), usedBy.map { it.label })
    }

    @Test
    fun `a loop names its files and the line that starts it`(@TempDir root: Path) {
        val active = project(root)
        val margins = ImportGraphProvider(root).margins(active, transport)
        assertEquals(listOf("LspTransport.kt", "LspClient.kt", "LspTransport.kt"), margins.cycle.map { it.label })
        assertEquals(9, margins.cycleLine)
    }

    @Test
    fun `files outside the supported languages get empty margins`(@TempDir root: Path) {
        val readme = write(root, "README.md", "# LspTransport")
        val margins = ImportGraphProvider(root).margins(readme, "# LspTransport")
        assertTrue(margins.needs.isEmpty() && margins.usedBy.isEmpty() && margins.cycle.isEmpty())
    }
}
