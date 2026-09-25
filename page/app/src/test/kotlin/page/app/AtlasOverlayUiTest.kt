package page.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.use
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import page.app.ui.ExpandedPanelOverlay
import page.atlas.graph.GraphEdge
import page.atlas.graph.GraphNode
import page.atlas.graph.GraphSlice
import page.atlas.graph.NodeKind
import page.atlas.graph.SourceEvidence
import page.atlas.render.AtlasContent
import page.atlas.render.AtlasViewState
import page.atlas.render.AtlasViewTab
import page.shared.path.FilePath
import page.ui.GlassPalette
import page.ui.GlassTheme

@OptIn(ExperimentalTestApi::class)
class AtlasOverlayUiTest {
    private val names = listOf("AccountMenu", "SessionTest", "SessionStore", "Credentials", "SessionEvents", "RefreshSession")
    private val slice = GraphSlice(names.mapIndexed { index, name ->
        GraphNode(name, "$name.kt", FilePath.of("/northstar/${listOf("interface", "tests", "session", "storage", "session", "session")[index]}/$name.kt"), NodeKind.WORKSPACE_FILE)
    }, listOf(
        GraphEdge("AccountMenu", "SessionStore"), GraphEdge("SessionTest", "SessionStore"),
        GraphEdge("SessionStore", "Credentials"),
        GraphEdge("SessionStore", "SessionEvents", evidence = SourceEvidence(4, "import session.SessionEvents")),
        GraphEdge("SessionEvents", "RefreshSession"), GraphEdge("RefreshSession", "SessionStore"),
    ))

    @Test
    fun `escape leaves code context before closing the atlas window`() = runComposeUiTest {
        var closed = false
        val state = AtlasViewState().apply {
            exploration.start(slice, "SessionStore")
            exploration.update(exploration.exploration.inspect(slice, slice.edges[3]))
        }
        setContent {
            GlassTheme {
                ExpandedPanelOverlay(onClose = { closed = true }) {
                    AtlasContent(slice, {}, { closed = true }, viewTab = AtlasViewTab.FILE, atlasView = state)
                }
            }
        }
        onNodeWithContentDescription("Analysis tools").performClick()
        onNodeWithContentDescription("Code context").performClick()
        onNodeWithContentDescription("Close").performKeyInput { pressKey(Key.Escape) }
        runOnIdle { assertFalse(closed) }
        val graph = onNodeWithContentDescription("Dependency graph. A to B means A uses B. Arrow keys select files.")
        graph.assertIsDisplayed().performMouseInput { click(Offset(8f, 8f)) }
        graph.assertIsFocused()
        graph.performKeyInput { pressKey(Key.Escape) }
        runOnIdle { assertFalse(closed) }
        graph.performKeyInput { pressKey(Key.Escape) }
        runOnIdle { assertTrue(closed) }
    }

    @Test
    fun `complete atlas window renders themes compact layout and all views`() {
        val output = Path.of("build/reports/atlas-window")
        Files.createDirectories(output)
        for ((name, palette, width) in listOf(
            Triple("explore-dark", GlassPalette.Signature, 1280),
            Triple("explore-light", GlassPalette.SignatureLight, 1280),
            Triple("explore-compact", GlassPalette.Signature, 680),
            Triple("map-dark", GlassPalette.Signature, 1280),
            Triple("problems-dark", GlassPalette.Signature, 1280),
            Triple("connection-dark", GlassPalette.Signature, 1280),
            Triple("connection-light", GlassPalette.SignatureLight, 1280),
            Triple("connection-compact", GlassPalette.Signature, 680),
        )) {
            val state = AtlasViewState().apply {
                exploration.start(slice, "SessionStore")
                if (name.startsWith("connection")) exploration.update(exploration.exploration.inspect(slice, slice.edges[3]))
            }
            val tab = when {
                name.startsWith("map") -> AtlasViewTab.MODULES
                name.startsWith("problems") -> AtlasViewTab.PROBLEMS
                else -> AtlasViewTab.FILE
            }
            ImageComposeScene(width, 820) {
                GlassTheme(palette) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                        ExpandedPanelOverlay(onClose = {}) {
                            AtlasContent(slice, {}, {}, viewTab = tab, atlasView = state)
                        }
                    }
                }
            }.use { scene ->
                repeat(4) { scene.render(it * 16_000_000L).close() }
                scene.render(80_000_000L).use { image ->
                    image.encodeToData()!!.use { data -> Files.write(output.resolve("$name.png"), data.bytes) }
                }
            }
        }
    }
}
