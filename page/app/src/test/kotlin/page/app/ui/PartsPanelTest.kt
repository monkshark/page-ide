package page.app.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import page.atlas.graph.FileFacts
import page.atlas.graph.MapPart
import page.atlas.graph.PartTie
import page.atlas.graph.ProjectMap
import page.atlas.graph.ProjectMapBuilder
import page.ui.GlassTheme
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class PartsPanelTest {

    private val root = Path.of("/proj").toAbsolutePath()
    private fun file(rel: String): Path = root.resolve(rel)
    private fun part(id: String, layer: Int, desc: String, vararg files: String): MapPart =
        MapPart(id, id, desc, file(id), files.map { file(it) }, layer)

    private val project: ProjectMap = ProjectMap(
        "IDE",
        listOf(
            part("app", 2, "Window, panels and dialogs", "app/Main.kt"),
            part("language", 1, "Routes each file to its server", "language/LspRouter.kt"),
            part("lsp", 1, "Language Server Protocol client", "lsp/LspTransport.kt", "lsp/LspClient.kt", "lsp/JsonRpc.kt"),
            part("core", 0, "Paths, settings, shared basics", "core/Env.kt"),
            part(ProjectMapBuilder.OTHER_ID, 0, "", "tools/gen.kt"),
        ),
        emptyList(),
    )

    @Test
    fun `tiers run from the top layer down and leave other out`() {
        assertEquals(listOf(listOf("app"), listOf("lsp", "language"), listOf("core")), partTiers(project).map { tier -> tier.map { it.id } })
    }

    @Test
    fun `boxes say how they relate to the open file in words`() {
        val lsp = project.part("lsp")!!
        assertEquals("here", partCaption(lsp, here = true, tie = PartTie(1, true)))
        assertEquals("3 files", partCaption(lsp, here = false, tie = null))
        assertEquals("1 uses it", partCaption(lsp, here = false, tie = PartTie(1, false)))
        assertEquals("2 use it", partCaption(lsp, here = false, tie = PartTie(2, false)))
        assertEquals("this file needs it", partCaption(lsp, here = false, tie = PartTie(0, true)))
        assertEquals("2 use it, needed", partCaption(lsp, here = false, tie = PartTie(2, true)))
    }

    @Test
    fun `file rows show their users or the loop`() {
        assertEquals("loop", factCaption(FileFacts(4, true)))
        assertEquals("used by 6", factCaption(FileFacts(6, false)))
        assertEquals(null, factCaption(null))
    }

    @Test
    fun `the open file's part is listed and another part can be picked`() = runComposeUiTest {
        var opened: Path? = null
        setContent {
            GlassTheme {
                PartsPanel(
                    map = project,
                    currentFile = file("lsp/LspTransport.kt"),
                    ties = mapOf("language" to PartTie(1, false), "core" to PartTie(0, true)),
                    loadFacts = { files -> files.associateWith { FileFacts(2, it.fileName.toString() == "LspClient.kt") } },
                    onOpenFile = { opened = it },
                )
            }
        }
        onNodeWithText("Language Server Protocol client").assertIsDisplayed()
        onNodeWithText("this file needs it").assertIsDisplayed()
        onNodeWithText("1 uses it").assertIsDisplayed()
        waitUntil(timeoutMillis = 3_000) { onAllNodes(androidx.compose.ui.test.hasText("loop")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("JsonRpc.kt").performClick()
        assertEquals(file("lsp/JsonRpc.kt"), opened)
        onNodeWithTag("part-core").performClick()
        onNodeWithText("Paths, settings, shared basics").assertIsDisplayed()
        onNodeWithText("Env.kt").assertIsDisplayed()
    }
}
