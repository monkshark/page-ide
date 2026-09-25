package page.atlas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.use
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import page.atlas.graph.GraphEdge
import page.atlas.graph.GraphNode
import page.atlas.graph.GraphSlice
import page.atlas.graph.NodeKind
import page.atlas.graph.SourceEvidence
import page.atlas.interaction.ExplorationHighlight
import page.atlas.render.AtlasContent
import page.atlas.render.AtlasProblemsPanel
import page.atlas.render.AtlasViewState
import page.atlas.render.AtlasViewTab
import page.shared.path.FilePath
import page.ui.GlassPalette
import page.ui.GlassTheme

@OptIn(ExperimentalTestApi::class)
class AtlasProblemsUiTest {
    private val names = listOf("SessionStore", "SessionEvents", "RefreshSession", "Credentials") + (1..8).map { "Feature$it" }
    private val slice = GraphSlice(names.map {
        GraphNode(it, "$it.kt", FilePath.of("/northstar/session/$it.kt"), NodeKind.WORKSPACE_FILE)
    }, listOf(
        GraphEdge("SessionStore", "SessionEvents", evidence = SourceEvidence(4, "import session.SessionEvents")), GraphEdge("SessionEvents", "RefreshSession"),
        GraphEdge("RefreshSession", "SessionStore"), GraphEdge("SessionStore", "Credentials"),
    ) + (1..8).map { GraphEdge("Feature$it", "Credentials") })

    @Test
    fun `review actions open exploration with the relevant relationships highlighted`() = runComposeUiTest {
        val state = AtlasViewState()
        setContent {
            var tab by remember { mutableStateOf(AtlasViewTab.PROBLEMS) }
            GlassTheme { AtlasContent(slice, {}, {}, viewTab = tab, onViewTabChange = { tab = it }, atlasView = state) }
        }
        onNodeWithContentDescription("Explore cycle").performClick()
        runOnIdle { assertEquals(ExplorationHighlight.CYCLE, state.exploration.exploration.highlight) }
        onNodeWithText("Problems").performClick()
        onNodeWithContentDescription("Shared files 1").performClick()
        onNodeWithText("CONNECTION MAP").assertIsDisplayed()
        onNodeWithContentDescription("Review change impact").performClick()
        runOnIdle {
            assertEquals("Credentials", state.exploration.exploration.selectedId)
            assertEquals(ExplorationHighlight.IMPACT, state.exploration.exploration.highlight)
        }
    }

    @Test
    fun `file actions open the editor and close atlas`() = runComposeUiTest {
        var opened: Path? = null
        var closed = false
        setContent {
            GlassTheme { AtlasContent(slice, { opened = it }, { closed = true }, viewTab = AtlasViewTab.PROBLEMS) }
        }
        onNodeWithContentDescription("Finding details").performScrollToNode(hasContentDescription("Open RefreshSession.kt"))
        onNodeWithContentDescription("Open RefreshSession.kt").performClick()
        runOnIdle { assertEquals("RefreshSession.kt", opened?.fileName.toString()); assertTrue(closed) }
    }

    @Test
    fun `review graph filters real connections and opens their exact source line`() = runComposeUiTest {
        var opened: Path? = null
        var line: Int? = null
        var closed = false
        setContent {
            GlassTheme {
                AtlasContent(slice, {}, { closed = true }, viewTab = AtlasViewTab.PROBLEMS,
                    onOpenLocation = { path, sourceLine -> opened = path; line = sourceLine })
            }
        }
        onNodeWithContentDescription("Filter connections for SessionStore.kt").performClick()
        onNodeWithContentDescription("Finding details").performScrollToNode(hasText("2 CONNECTIONS · SELECT TO READ SOURCE"))
        onNodeWithContentDescription("Inspect SessionStore.kt imports SessionEvents.kt").performClick()
        onNodeWithContentDescription("Finding details").performScrollToNode(hasContentDescription("Open source · line 5  ↗"))
        onNodeWithContentDescription("Open source · line 5  ↗").performClick()
        runOnIdle {
            assertEquals("SessionStore.kt", opened?.fileName.toString())
            assertEquals(4, line)
            assertTrue(closed)
        }
    }

    @Test
    fun `empty category can return to all findings`() = runComposeUiTest {
        val graph = slice.copy(edges = slice.edges.take(3))
        setContent { GlassTheme { AtlasProblemsPanel(graph, null, {}, {}) } }
        onNodeWithContentDescription("Shared files 0").performClick()
        onNodeWithText("No findings in this category").assertIsDisplayed()
        onNodeWithContentDescription("Show all findings").performClick()
        onNodeWithContentDescription("Explore cycle").assertIsDisplayed()
        onNodeWithText("HIGH").assertDoesNotExist()
    }

    @Test
    fun `problems renders themes narrow layout shared details and empty state`() {
        val output = Path.of("build/reports/atlas-exploration")
        Files.createDirectories(output)
        for ((name, palette, width) in listOf(
            Triple("problems-dark", GlassPalette.Signature, 1240),
            Triple("problems-light", GlassPalette.SignatureLight, 1240),
            Triple("problems-narrow", GlassPalette.Signature, 600),
            Triple("problems-shared", GlassPalette.Signature, 1240),
            Triple("problems-empty", GlassPalette.SignatureLight, 1000),
        )) {
            val graph = if (name == "problems-empty") slice.copy(edges = emptyList()) else slice
            ImageComposeScene(width, 760) {
                GlassTheme(palette) {
                    AtlasContent(graph, {}, {}, viewTab = AtlasViewTab.PROBLEMS,
                        activeFileId = if (name == "problems-shared") "Credentials" else null)
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
