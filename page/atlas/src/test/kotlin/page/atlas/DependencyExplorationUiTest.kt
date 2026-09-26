package page.atlas

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import page.atlas.graph.GraphEdge
import page.atlas.graph.GraphNode
import page.atlas.graph.GraphSlice
import page.atlas.graph.NodeKind
import page.atlas.graph.SourceEvidence
import page.atlas.interaction.ExplorationHighlight
import page.atlas.render.DependencyExplorationPanel
import page.atlas.render.ExplorationViewState
import page.atlas.render.explorationRect
import page.shared.path.FilePath
import page.ui.GlassTheme

@OptIn(ExperimentalTestApi::class)
class DependencyExplorationUiTest {
    @Test
    fun `trace workflow reads each step without losing the path`() = runComposeUiTest {
        val graph = GraphSlice(listOf("start", "middle", "end").map {
            GraphNode(it, "$it.kt", FilePath.of("/project/$it.kt"), NodeKind.WORKSPACE_FILE)
        }, listOf(GraphEdge("start", "middle", evidence = SourceEvidence(2, "import middle")),
            GraphEdge("middle", "end", evidence = SourceEvidence(5, "import end"))))
        val state = ExplorationViewState().apply { start(graph, "start") }
        setContent { GlassTheme { DependencyExplorationPanel(graph, "start", state, {}, { _, _ -> }) } }
        onNodeWithText("Trace path").assertDoesNotExist()
        onNodeWithContentDescription("Analysis tools").performClick()
        onNodeWithContentDescription("Trace path").performClick()
        onNodeWithContentDescription("Find path destination").performTextInput("end")
        onNodeWithText("end.kt").performClick()
        onNodeWithContentDescription("Read connections").performClick()
        onNodeWithContentDescription("Next connection").performClick()
        onNodeWithText("import end").assertIsDisplayed()
        runOnIdle {
            assertEquals(ExplorationHighlight.PATH, state.exploration.highlight)
            assertEquals(2, state.exploration.highlightedEdges.size)
            assertEquals(graph.edges.last(), state.exploration.selectedEdge)
        }
        onNodeWithContentDescription("Previous connection").performClick()
        onNodeWithText("import middle").assertIsDisplayed()
        onNodeWithContentDescription("Close source preview").performClick()
        onNodeWithContentDescription("Connection source preview").assertDoesNotExist()
        runOnIdle { assertEquals(ExplorationHighlight.PATH, state.exploration.highlight) }
    }

    private val slice = GraphSlice(
        (listOf("root") + (0..6).map { "leaf$it" }).map {
            GraphNode(it, "$it.kt", FilePath.of("/project/$it.kt"), NodeKind.WORKSPACE_FILE)
        },
        (0..6).map { GraphEdge("root", "leaf$it", evidence = SourceEvidence(it + 2, "import leaf$it")) },
    )

    @Test
    fun `card actions expand and collapse without using the inspector`() = runComposeUiTest {
        val state = ExplorationViewState().apply { start(slice, "root") }
        setContent {
            GlassTheme { DependencyExplorationPanel(slice, "root", state, {}, { _, _ -> }, Modifier.fillMaxSize()) }
        }
        var originalPan = Offset.Zero
        var originalScale = 0f
        runOnIdle { originalPan = state.camera.pan; originalScale = state.camera.scale }
        onNodeWithContentDescription("Expand Uses: 4 hidden files").assertIsDisplayed().performClick()
        runOnIdle {
            assertEquals(8, state.exploration.positions.size)
            assertNull(state.revealRequest)
            assertEquals(originalPan, state.camera.pan)
            assertEquals(originalScale, state.camera.scale)
        }
        onNodeWithContentDescription("Collapse Uses").assertIsDisplayed().performClick()
        runOnIdle { assertEquals(setOf("root"), state.exploration.positions.keys) }
        onNodeWithContentDescription("Expand Uses: 7 hidden files").assertIsDisplayed().performClick()
        runOnIdle { assertEquals(5, state.exploration.positions.size) }
    }

    @Test
    fun `hover previews a connection and inspection shows only connection details`() = runComposeUiTest {
        val state = ExplorationViewState().apply { start(slice, "root") }
        var openedLine: Int? = null
        setContent {
            GlassTheme { DependencyExplorationPanel(slice, "root", state, {}, { _, line -> openedLine = line }, Modifier.fillMaxSize()) }
        }
        var point = Offset.Zero
        var originalPan = Offset.Zero
        var originalScale = 0f
        runOnIdle {
            val source = explorationRect(state.exploration.positions.getValue("root"))
            val target = explorationRect(state.exploration.positions.getValue("leaf0"))
            point = Offset((source.right + target.left) / 2f, source.center.y) * state.camera.scale + state.camera.pan
            originalPan = state.camera.pan
            originalScale = state.camera.scale
        }
        val graph = onNodeWithContentDescription("Dependency graph. A to B means A uses B. Arrow keys select files.")
        graph.performMouseInput { moveTo(point) }
        onNodeWithText("root.kt imports leaf0.kt", substring = true).assertIsDisplayed()
        runOnIdle { assertNull(state.exploration.selectedEdge) }
        graph.performMouseInput { click(point) }
        onNodeWithContentDescription("Connection source preview").assertIsDisplayed()
        runOnIdle { assertEquals(originalPan, state.camera.pan); assertEquals(originalScale, state.camera.scale) }
        onNodeWithText("FILE DETAILS").assertDoesNotExist()
        onNodeWithContentDescription("Open connection in editor").performClick()
        runOnIdle { assertEquals(2, openedLine) }
        onNodeWithContentDescription("Close source preview").performClick()
        onNodeWithText("FILE DETAILS").assertDoesNotExist()
        onNodeWithContentDescription("Connection source preview").assertDoesNotExist()
        graph.performMouseInput { click(point) }
        graph.performKeyInput { pressKey(Key.Escape) }
        onNodeWithContentDescription("Connection source preview").assertDoesNotExist()
    }

    @Test
    fun `compact preview explains missing evidence and path search escapes independently`() = runComposeUiTest {
        val graph = slice.copy(edges = listOf(GraphEdge("root", "leaf0")))
        val state = ExplorationViewState().apply {
            start(graph, "root")
            update(exploration.inspect(graph, graph.edges.first()))
        }
        setContent {
            GlassTheme { Box(Modifier.width(600.dp).height(440.dp)) {
                DependencyExplorationPanel(graph, "root", state, {}, { _, _ -> }, Modifier.fillMaxSize())
            } }
        }
        onNodeWithText("No source location is available for this relationship.").assertIsDisplayed()
        onNodeWithContentDescription("Close source preview").performClick()
        onNodeWithContentDescription("Connection source preview").assertDoesNotExist()
        onNodeWithContentDescription("Analysis tools").performClick()
        onNodeWithContentDescription("Trace path").performClick()
        onNodeWithContentDescription("Find path destination").performKeyInput { pressKey(Key.Escape) }
        onNodeWithContentDescription("Find path destination").assertDoesNotExist()
        onNodeWithContentDescription("Change impact").assertIsDisplayed()
    }
}
