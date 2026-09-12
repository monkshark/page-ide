package page.atlas

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import page.atlas.render.DependencyExplorationPanel
import page.atlas.render.ExplorationViewState
import page.atlas.render.explorationRect
import page.shared.path.FilePath
import page.ui.GlassTheme

@OptIn(ExperimentalTestApi::class)
class DependencyExplorationUiTest {
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
        onNodeWithContentDescription("Expand Uses: 4 hidden files").assertIsDisplayed().performClick()
        runOnIdle {
            assertEquals(8, state.exploration.positions.size)
            assertNull(state.revealRequest)
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
        runOnIdle {
            val source = explorationRect(state.exploration.positions.getValue("root"))
            val target = explorationRect(state.exploration.positions.getValue("leaf0"))
            point = Offset((source.right + target.left) / 2f, source.center.y) * state.camera.scale + state.camera.pan
        }
        val graph = onNodeWithContentDescription("Dependency graph. A to B means A uses B. Arrow keys select files.")
        graph.performMouseInput { moveTo(point) }
        onNodeWithText("root.kt imports leaf0.kt", substring = true).assertIsDisplayed()
        runOnIdle { assertNull(state.exploration.selectedEdge) }
        graph.performMouseInput { click(point) }
        onNodeWithText("CONNECTION DETAILS").assertIsDisplayed()
        onNodeWithText("FILE DETAILS").assertDoesNotExist()
        onNodeWithContentDescription("Open source · line 3  ↗").performClick()
        runOnIdle { assertEquals(2, openedLine) }
        onNodeWithContentDescription("Clear highlight").performClick()
        onNodeWithText("FILE DETAILS").assertIsDisplayed()
        onNodeWithText("CONNECTION DETAILS").assertDoesNotExist()
        graph.performMouseInput { click(point) }
        graph.performKeyInput { pressKey(Key.Escape) }
        onNodeWithText("FILE DETAILS").assertIsDisplayed()
    }
}
