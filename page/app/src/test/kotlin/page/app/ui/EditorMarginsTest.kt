package page.app.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import page.atlas.graph.FileMargins
import page.atlas.graph.GraphNode
import page.atlas.graph.Need
import page.atlas.graph.NodeKind
import page.shared.path.FilePath
import page.ui.GlassTheme
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class EditorMarginsTest {

    private val root = Path.of("/proj").toAbsolutePath()
    private fun node(rel: String) = GraphNode(root.resolve(rel).toString(), rel.substringAfterLast('/'), FilePath.of(root.resolve(rel).toString()), NodeKind.WORKSPACE_FILE)
    private val self = node("lsp/LspTransport.kt")
    private val client = node("lsp/LspClient.kt")

    @Test
    fun `a folded line hands its notes to the visible line above it`() {
        val visible = listOf(0, 1, 2, 9, 10)
        assertEquals(2, visualRow(2, visible))
        assertEquals(2, visualRow(5, visible))
        assertEquals(3, visualRow(9, visible))
        assertEquals(7, visualRow(7, emptyList()))
    }

    @Test
    fun `used by lines up with the declaration named after the file`() {
        val text = "package page.lsp\n\nimport page.core.Env\n\nclass LspTransport(\n    val x: Int,\n)"
        assertEquals(4, anchorLine(text, "LspTransport.kt"))
        assertEquals(0, anchorLine("val x = 1", "Other.kt"))
    }

    @Test
    fun `a loop explains itself by its length`() {
        assertEquals(
            "This line needs LspClient.kt, and LspClient.kt needs this file back. Drop either one to end the loop",
            loopExplanation(listOf(self, client, self)),
        )
        assertEquals(
            "This line needs LspClient.kt, which leads back here through 2 files. Drop any step to end the loop",
            loopExplanation(listOf(self, client, node("lsp/JsonRpc.kt"), self)),
        )
    }

    @Test
    fun `needs and used by sit in their margins and open on click`() = runComposeUiTest {
        var opened: Path? = null
        val users = (1..10).map { node("app/User$it.kt") }
        val binding = MarginsBinding(
            path = root.resolve("lsp/LspTransport.kt"),
            margins = FileMargins(
                needs = listOf(Need(node("core/Env.kt"), 2), Need(client, 9)),
                usedBy = users,
                cycle = listOf(self, client, self),
            ),
            groupOf = { p -> root.relativize(p).getName(0).toString() },
            onOpen = { opened = it },
        )
        setContent {
            GlassTheme {
                Row {
                    NeedsMargin(binding, rowOf = { it }, lineHeight = 20.dp, topPadding = 16.dp, minHeight = 0.dp)
                    UsedByMargin(binding, anchorRow = 4, lineHeight = 20.dp, topPadding = 16.dp, minHeight = 0.dp)
                }
            }
        }
        onNodeWithText("Env.kt").assertIsDisplayed()
        onNodeWithTag("margin-loop").assertIsDisplayed()
        onNodeWithText("2 more").assertIsDisplayed()
        onNodeWithTag("margin-used-by-more").performClick()
        onNodeWithText("User10.kt").assertIsDisplayed()
        onNodeWithText("Env.kt").performClick()
        assertEquals(root.resolve("core/Env.kt"), opened)
    }
}
