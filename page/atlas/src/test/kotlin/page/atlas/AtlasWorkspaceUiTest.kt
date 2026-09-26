package page.atlas

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import page.atlas.graph.GraphEdge
import page.atlas.graph.GraphNode
import page.atlas.graph.GraphSlice
import page.atlas.graph.NodeKind
import page.atlas.render.AtlasContent
import page.atlas.render.AtlasViewState
import page.atlas.render.AtlasViewTab
import page.atlas.render.CodeBundlePanel
import page.shared.path.FilePath
import page.ui.GlassTheme

@OptIn(ExperimentalTestApi::class)
class AtlasWorkspaceUiTest {
    @Test
    fun `compact context switches between full height selection and preview`() = runComposeUiTest {
        val graph = GraphSlice(listOf(GraphNode("missing", "Missing.kt", FilePath.of("/atlas-missing-file/Missing.kt"), NodeKind.WORKSPACE_FILE)), emptyList())
        setContent {
            GlassTheme { Box(Modifier.width(600.dp)) { CodeBundlePanel(graph, "missing", setOf("missing"), {}) } }
        }
        onNodeWithContentDescription("Find files to include").assertIsDisplayed()
        onNodeWithContentDescription("Review context").performClick()
        onNodeWithText("2  REVIEW CONTEXT · SAVED CONTENTS").assertIsDisplayed()
        onNodeWithContentDescription("Find files to include").assertDoesNotExist()
        onNodeWithContentDescription("Choose files").performClick()
        onNodeWithContentDescription("Find files to include").assertIsDisplayed()
        onNodeWithText("1 files selected").assertIsDisplayed()
    }

    @Test
    fun `map search opens exploration and returns to the map without opening an editor`() = runComposeUiTest {
        val graph = GraphSlice(listOf(GraphNode("store", "Store.kt", FilePath.of("/project/session/Store.kt"), NodeKind.WORKSPACE_FILE)), emptyList())
        val state = AtlasViewState()
        var opened = false
        setContent {
            var tab by remember { mutableStateOf(AtlasViewTab.MODULES) }
            GlassTheme {
                AtlasContent(graph, { opened = true }, {}, viewTab = tab, onViewTabChange = { tab = it }, atlasView = state)
            }
        }
        onNodeWithText("Project structure").assertDoesNotExist()
        onNodeWithContentDescription("Search files in Atlas").performTextInput("Store")
        onNodeWithContentDescription("Search files in Atlas").performKeyInput { pressKey(Key.Enter) }
        onNodeWithContentDescription("Change impact").assertIsDisplayed()
        runOnIdle { assertEquals("store", state.exploration.exploration.selectedId); assertFalse(opened) }
        onNodeWithContentDescription("Project map").performClick()
        onNodeWithText("Architecture").assertIsDisplayed()
        onNodeWithText("Project structure").assertDoesNotExist()
        onNodeWithContentDescription("Clear file search").performClick()
        onNodeWithContentDescription("Clear file search").assertDoesNotExist()
        onNodeWithText("Search files…").assertIsDisplayed()
    }

    @Test
    fun `context preview copies selected saved files and excludes unchecked dependents`() {
        val dir = Files.createTempDirectory("atlas-context-ui")
        val names = listOf("Screen.kt", "Store.kt", "Model.kt")
        try {
            names.forEach { Files.writeString(dir.resolve(it), "class ${it.removeSuffix(".kt")}") }
            val graph = GraphSlice(names.map { GraphNode(it, it, FilePath.of(dir.resolve(it).toString()), NodeKind.WORKSPACE_FILE) },
                listOf(GraphEdge("Screen.kt", "Store.kt"), GraphEdge("Store.kt", "Model.kt")))
            var copied: AnnotatedString? = null
            val clipboard = object : ClipboardManager {
                override fun getText(): AnnotatedString? = copied
                override fun setText(annotatedString: AnnotatedString) { copied = annotatedString }
            }
            runComposeUiTest {
                val state = AtlasViewState().apply { exploration.start(graph, "Store.kt") }
                setContent {
                    CompositionLocalProvider(LocalClipboardManager provides clipboard) {
                        GlassTheme { AtlasContent(graph, {}, {}, viewTab = AtlasViewTab.FILE, atlasView = state) }
                    }
                }
                onNodeWithContentDescription("Analysis tools").performClick()
                onNodeWithContentDescription("Code context").performClick()
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("2 readable files", substring = true).fetchSemanticsNodes().isNotEmpty() }
                onNodeWithContentDescription("Copy context").performClick()
                runOnIdle {
                    assertTrue(copied!!.text.contains("class Store"))
                    assertTrue(copied!!.text.contains("class Model"))
                    assertFalse(copied!!.text.contains("class Screen"))
                }
                onNodeWithContentDescription("Selected file").performClick()
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("1 readable file", substring = true).fetchSemanticsNodes().isNotEmpty() }
                onNodeWithContentDescription("Copy context").performClick()
                runOnIdle { assertFalse(copied!!.text.contains("class Model")) }
                onNodeWithContentDescription("Find files to include").performTextInput("Screen")
                onNodeWithContentDescription("Show selected files only").performClick()
                onNodeWithText("No matching files").assertIsDisplayed()
                onNodeWithText("1 files selected").assertIsDisplayed()
                onNodeWithContentDescription("Close").performClick()
                onNodeWithContentDescription("Dependency graph. A to B means A uses B. Arrow keys select files.").assertIsDisplayed()
            }
        } finally {
            names.forEach { Files.deleteIfExists(dir.resolve(it)) }
            Files.deleteIfExists(dir)
        }
    }
}
