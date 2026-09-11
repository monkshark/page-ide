package page.atlas

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
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
import page.atlas.render.DependencyExplorationCanvas
import page.atlas.render.DependencyExplorationPanel
import page.atlas.render.ExplorationViewState
import page.atlas.render.explorationRect
import page.shared.path.FilePath
import page.ui.GlassPalette
import page.ui.GlassTheme

class DependencyExplorationRenderTest {
    private val nodes = listOf("AccountMenu", "SessionTest", "SessionStore", "Credentials", "SessionEvents", "RefreshSession")
        .map { GraphNode(it, "$it.kt", FilePath.of("/northstar/session/$it.kt"), NodeKind.WORKSPACE_FILE) }
    private val slice = GraphSlice(nodes, listOf(
        GraphEdge("AccountMenu", "SessionStore", evidence = SourceEvidence(3, "import session.SessionStore")),
        GraphEdge("SessionTest", "SessionStore", evidence = SourceEvidence(2, "import session.SessionStore")),
        GraphEdge("SessionStore", "Credentials", evidence = SourceEvidence(2, "import session.Credentials")),
        GraphEdge("SessionStore", "SessionEvents", evidence = SourceEvidence(4, "import session.SessionEvents")),
        GraphEdge("SessionEvents", "RefreshSession", evidence = SourceEvidence(3, "import session.RefreshSession")),
        GraphEdge("RefreshSession", "SessionStore", evidence = SourceEvidence(3, "import session.SessionStore")),
    ))

    @Test
    fun `canvas hit testing respects the current pan and scale`() {
        val state = ExplorationViewState()
        state.start(slice, "SessionStore")
        state.camera.pan = Offset(300f, 280f)
        state.camera.scale = .85f
        ImageComposeScene(1100, 700) {
            GlassTheme {
                DependencyExplorationCanvas(slice, state.exploration, state.camera,
                    onSelect = { state.update(state.exploration.select(slice, it)) },
                    onInspect = { state.update(state.exploration.inspect(slice, it)) },
                    modifier = Modifier.fillMaxSize())
            }
        }.use { scene ->
            repeat(3) { scene.render(it * 16_000_000L).close() }
            val before = state.exploration.positions
            val target = explorationRect(before.getValue("Credentials")).center * state.camera.scale + state.camera.pan
            scene.sendPointerEvent(PointerEventType.Press, target)
            scene.sendPointerEvent(PointerEventType.Release, target)
            scene.render(64_000_000L).close()
            assertEquals("Credentials", state.exploration.selectedId)
            assertEquals(before, state.exploration.positions)
            assertEquals(Offset(300f, 280f), state.camera.pan)
            val source = explorationRect(before.getValue("SessionStore"))
            val destination = explorationRect(before.getValue("Credentials"))
            val edgePoint = Offset((source.right + destination.left) / 2f, source.center.y) * state.camera.scale + state.camera.pan
            scene.sendPointerEvent(PointerEventType.Press, edgePoint)
            scene.sendPointerEvent(PointerEventType.Release, edgePoint)
            scene.render(96_000_000L).close()
            assertEquals(slice.edges[2], state.exploration.selectedEdge)
            assertEquals("import session.Credentials", state.exploration.selectedEdge?.evidence?.text)
        }
    }

    @Test
    fun `full panel renders source evidence in dark light and narrow layouts`() {
        val output = Path.of("build/reports/atlas-exploration")
        Files.createDirectories(output)
        for ((name, palette, width) in listOf(
            Triple("dark", GlassPalette.Signature, 1240),
            Triple("light", GlassPalette.SignatureLight, 1240),
            Triple("narrow", GlassPalette.Signature, 600),
            Triple("file-dark", GlassPalette.Signature, 1240),
            Triple("file-light", GlassPalette.SignatureLight, 1240),
        )) {
            val state = ExplorationViewState()
            state.start(slice, "SessionStore")
            if (!name.startsWith("file-")) state.update(state.exploration.inspect(slice, slice.edges[3]))
            ImageComposeScene(width, 760) {
                GlassTheme(palette) {
                    DependencyExplorationPanel(slice, "SessionStore", state, {}, { _, _ -> }, Modifier.fillMaxSize())
                }
            }.use { scene ->
                repeat(4) { scene.render(it * 16_000_000L).close() }
                assertTrue(state.camera.scale > 0f)
                scene.render(80_000_000L).use { image ->
                    image.encodeToData()!!.use { data -> Files.write(output.resolve("$name.png"), data.bytes) }
                }
            }
        }
    }
}
