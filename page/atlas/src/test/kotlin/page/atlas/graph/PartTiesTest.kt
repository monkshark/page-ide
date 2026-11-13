package page.atlas.graph

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import page.shared.path.FilePath

class PartTiesTest {

    private val root = Path.of("/proj").toAbsolutePath()
    private fun file(rel: String) = root.resolve(rel)
    private fun node(rel: String) = GraphNode(file(rel).toString(), rel.substringAfterLast('/'), FilePath.of(file(rel).toString()), NodeKind.WORKSPACE_FILE)
    private fun part(id: String, layer: Int, vararg files: String): MapPart = MapPart(id, id, "", file(id), files.map { file(it) }, layer)

    private val project: ProjectMap = ProjectMap(
        "IDE",
        listOf(
            part("app", 3, "app/InstallGuideDialog.kt"),
            part("language", 2, "language/LspRouter.kt", "language/LspController.kt"),
            part("lsp", 1, "lsp/LspTransport.kt", "lsp/LspClient.kt", "lsp/JsonRpc.kt"),
            part("core", 0, "core/Env.kt"),
        ),
        emptyList(),
    )

    @Test
    fun `each part says how many of its files use the current one and whether it is needed`() {
        val margins = FileMargins(
            needs = listOf(Need(node("core/Env.kt"), 3), Need(node("lsp/JsonRpc.kt"), 7)),
            usedBy = listOf(node("language/LspRouter.kt"), node("language/LspController.kt"), node("app/InstallGuideDialog.kt"), node("lsp/LspClient.kt")),
            cycle = emptyList(),
        )
        val ties = partTies(project, margins)
        assertEquals(PartTie(2, false), ties["language"])
        assertEquals(PartTie(1, false), ties["app"])
        assertEquals(PartTie(0, true), ties["core"])
        assertEquals(PartTie(1, true), ties["lsp"])
    }

    @Test
    fun `files outside every part tie nothing`() {
        val margins = FileMargins(listOf(Need(node("test/FakeTransport.kt"), 2)), listOf(node("scripts/build.kt")), emptyList())
        assertTrue(partTies(project, margins).isEmpty())
    }
}
