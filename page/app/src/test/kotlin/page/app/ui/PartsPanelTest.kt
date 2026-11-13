package page.app.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
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
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class PartsPanelTest {

    private val root = Path.of("/proj").toAbsolutePath()
    private fun file(rel: String): Path = root.resolve(rel)
    private fun part(id: String, layer: Int, vararg files: String): MapPart =
        MapPart(id, id, "Descriptions are not shown", root.resolve("page/$id"), files.map { file(it) }, layer)

    private val project: ProjectMap = ProjectMap(
        "IDE",
        listOf(
            part("app", 2, "app/Main.kt"),
            part("language", 1, "language/LspRouter.kt"),
            part("lsp", 1, "lsp/LspTransport.kt", "lsp/LspClient.kt", "lsp/JsonRpc.kt"),
            part("core", 0, "core/Env.kt"),
            MapPart(ProjectMapBuilder.OTHER_ID, "Other", "", null, listOf(file("tools/gen.kt")), 0),
        ),
        emptyList(),
    )

    @Test
    fun `tiers run from the top layer down and leave other out`() {
        assertEquals(listOf(listOf("app"), listOf("lsp", "language"), listOf("core")), partTiers(project).map { tier -> tier.map { it.id } })
    }

    @Test
    fun `relations name the open file in plain words`() {
        assertEquals("1 file uses LspTransport", partRelation(PartTie(1, false), "LspTransport.kt"))
        assertEquals("2 files use LspTransport", partRelation(PartTie(2, false), "LspTransport.kt"))
        assertEquals("Used by LspTransport", partRelation(PartTie(0, true), "LspTransport.kt"))
        assertEquals("2 files use LspTransport · Used by LspTransport", partRelation(PartTie(2, true), "LspTransport.kt"))
        assertNull(partRelation(null, "LspTransport.kt"))
        assertNull(partRelation(PartTie(0, false), "LspTransport.kt"))
    }

    @Test
    fun `file rows show their users as a number or the loop`() {
        assertEquals("loop", factCaption(FileFacts(4, true)))
        assertEquals("6", factCaption(FileFacts(6, false)))
        assertNull(factCaption(null))
    }

    @Test
    fun `a part is described by its folder`() {
        assertEquals("page/lsp", partFolder(project.part("lsp")!!, root))
        assertNull(partFolder(project.part(ProjectMapBuilder.OTHER_ID)!!, root))
    }

    @Test
    fun `the open file's part is listed and another part can be picked`() = runComposeUiTest {
        var opened: Path? = null
        setContent {
            GlassTheme {
                PartsPanel(
                    map = project,
                    currentFile = file("lsp/LspTransport.kt"),
                    ties = mapOf("app" to PartTie(1, false), "core" to PartTie(0, true), "lsp" to PartTie(1, true)),
                    loadFacts = { files -> files.associateWith { FileFacts(2, it.fileName.toString() == "LspClient.kt") } },
                    onOpenFile = { opened = it },
                    root = root,
                )
            }
        }
        onNodeWithText("Highest layer first").assertIsDisplayed()
        onNodeWithText("1 file uses LspTransport").assertIsDisplayed()
        onNodeWithText("Used by LspTransport").assertIsDisplayed()
        onNodeWithText("Other files").assertIsDisplayed()
        assertEquals(0, onAllNodes(hasText("Descriptions are not shown")).fetchSemanticsNodes().size)
        waitUntil(timeoutMillis = 3_000) { onAllNodes(hasText("loop")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("JsonRpc.kt").performClick()
        assertEquals(file("lsp/JsonRpc.kt"), opened)
        onNodeWithTag("part-core").performClick()
        onNodeWithText("page/core").assertIsDisplayed()
        onNodeWithText("Env.kt").assertIsDisplayed()
    }

    @Test
    fun `an empty map points to the files view`() = runComposeUiTest {
        var browsed = false
        setContent {
            GlassTheme {
                PartsPanel(map = ProjectMap.EMPTY, currentFile = null, ties = emptyMap(), loadFacts = { emptyMap() }, onOpenFile = {}, onBrowseFiles = { browsed = true })
            }
        }
        onNodeWithText("No parts found").assertIsDisplayed()
        onNodeWithText("Browse in Files").performClick()
        assertEquals(true, browsed)
    }
}
