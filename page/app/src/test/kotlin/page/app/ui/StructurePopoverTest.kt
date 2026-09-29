package page.app.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import page.atlas.graph.FileNeighbours
import page.atlas.graph.GraphNode
import page.atlas.graph.MapPart
import page.atlas.graph.NodeKind
import page.atlas.graph.ProjectMap
import page.atlas.graph.ProjectMapBuilder
import page.shared.path.FilePath
import page.ui.GlassTheme
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class StructurePopoverTest {

    private val root = Path.of("/proj").toAbsolutePath()
    private fun file(rel: String) = GraphNode(root.resolve(rel).toString(), rel.substringAfterLast('/'), FilePath.of(root.resolve(rel).toString()), NodeKind.WORKSPACE_FILE)

    private val neighbours = FileNeighbours(
        usedBy = listOf(file("lsp/LspClient.kt"), file("language/LspController.kt")),
        uses = listOf(file("core/Env.kt")),
        cycle = listOf(file("lsp/LspTransport.kt"), file("lsp/LspClient.kt"), file("lsp/LspTransport.kt")),
    )
    private val groupOf: (Path) -> String? = { p -> root.relativize(p).getName(0).toString() }

    @Test
    fun `used by lists each file under the part it belongs to and opens it on click`() = runComposeUiTest {
        var opened: Path? = null
        setContent {
            GlassTheme {
                StructurePopover(StructureView.USED_BY, "LspTransport.kt", neighbours, groupOf, {}, { opened = it }, {})
            }
        }
        onNodeWithText("2 files use LspTransport.kt").assertIsDisplayed()
        onNodeWithText("LSP").assertIsDisplayed()
        onNodeWithText("LANGUAGE").assertIsDisplayed()
        onNodeWithText("LspController.kt").performClick()
        assertEquals(root.resolve("language/LspController.kt"), opened)
    }

    @Test
    fun `the other direction is one click away`() = runComposeUiTest {
        setContent {
            var view by remember { mutableStateOf(StructureView.USED_BY) }
            GlassTheme { StructurePopover(view, "LspTransport.kt", neighbours, groupOf, { view = it }, {}, {}) }
        }
        onNodeWithText("Uses 1").performClick()
        onNodeWithText("LspTransport.kt uses 1 files").assertIsDisplayed()
        onNodeWithText("Env.kt").assertIsDisplayed()
    }

    @Test
    fun `a loop is shown as a chain back to the same file`() = runComposeUiTest {
        setContent { GlassTheme { StructurePopover(StructureView.CYCLE, "LspTransport.kt", neighbours, groupOf, {}, {}, {}) } }
        onNodeWithText("This file is in a loop").assertIsDisplayed()
        onNodeWithText("LspClient.kt").assertIsDisplayed()
    }

    @Test
    fun `the close button dismisses it`() = runComposeUiTest {
        var dismissed = false
        setContent { GlassTheme { StructurePopover(StructureView.USED_BY, "X.kt", neighbours, groupOf, {}, {}, { dismissed = true }) } }
        onNodeWithText("✕").performClick()
        assertTrue(dismissed)
    }

    private fun part(id: String, layer: Int, files: Int, desc: String = "") =
        MapPart(id, id, desc, null, List(files) { Path.of("$id$it.kt") }, layer)

    @Test
    fun `the summary leads with what sits on top and leaves Other out`() {
        val map = ProjectMap("IDE", listOf(part("core", 0, 2), part("app", 2, 100), part(ProjectMapBuilder.OTHER_ID, 0, 40), part("ui", 1, 18)), emptyList())
        assertEquals(listOf("app", "ui", "core"), summaryParts(map).map { it.id })
    }

    @Test
    fun `a one-part project is not worth a summary`() {
        assertFalse(worthSummarising(ProjectMap("x", listOf(part("a", 0, 3)), emptyList())))
        assertFalse(worthSummarising(null))
        assertTrue(worthSummarising(ProjectMap("x", listOf(part("a", 1, 3), part("b", 0, 1)), emptyList())))
    }

    @Test
    fun `the summary card names the project and can be dismissed`() = runComposeUiTest {
        var dismissed = false
        val map = ProjectMap("IDE", listOf(part("app", 1, 10, "window and panels"), part("core", 0, 2, "shared basics")), emptyList())
        setContent { GlassTheme { ProjectSummaryCard(map, onDismiss = { dismissed = true }) } }
        onNodeWithText("IDE, in 2 parts").assertIsDisplayed()
        onNodeWithText("window and panels").assertIsDisplayed()
        onNodeWithTag("project-summary-close").performClick()
        assertTrue(dismissed)
    }
}
