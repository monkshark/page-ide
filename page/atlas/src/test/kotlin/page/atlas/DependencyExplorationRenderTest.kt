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
import page.atlas.render.AtlasContent
import page.atlas.render.AtlasViewTab
import page.atlas.render.AtlasViewState
import page.atlas.render.CodeBundlePanel
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
    fun `project map renders in dark light and compact workspaces`() {
        val modules = GraphSlice(nodes.mapIndexed { index, node ->
            node.copy(path = FilePath.of("/northstar/${listOf("interface", "tests", "session", "storage", "session", "session")[index]}/${node.label}"))
        }, slice.edges)
        val output = Path.of("build/reports/atlas-exploration")
        Files.createDirectories(output)
        val sourceRoot = output.resolve("sources")
        Files.createDirectories(sourceRoot)
        val savedSlice = slice.copy(nodes = nodes.map { node ->
            val path = sourceRoot.resolve(node.label)
            Files.writeString(path, "package session\n\nclass ${node.id}\n")
            node.copy(path = FilePath.of(path.toAbsolutePath().toString()))
        })
        for ((name, palette, width) in listOf(
            Triple("map-dark", GlassPalette.Signature, 1240),
            Triple("map-light", GlassPalette.SignatureLight, 1240),
            Triple("map-narrow", GlassPalette.Signature, 600),
            Triple("context", GlassPalette.Signature, 1240),
            Triple("context-narrow", GlassPalette.Signature, 600),
        )) {
            ImageComposeScene(width, 760) {
                GlassTheme(palette) {
                    if (name.startsWith("context")) CodeBundlePanel(savedSlice, "SessionStore", nodes.mapTo(hashSetOf()) { it.id }, {})
                    else AtlasContent(modules, {}, {}, viewTab = AtlasViewTab.MODULES)
                }
            }.use { scene ->
                repeat(8) { scene.render(it * 200_000_000L).close() }
                scene.render(2_000_000_000L).use { image ->
                    image.encodeToData()!!.use { data -> Files.write(output.resolve("$name.png"), data.bytes) }
                }
            }
        }
    }

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
            scene.sendPointerEvent(PointerEventType.Move, edgePoint)
            scene.render(80_000_000L).use { image ->
                val output = Path.of("build/reports/atlas-exploration/hover.png")
                Files.createDirectories(output.parent)
                image.encodeToData()!!.use { data -> Files.write(output, data.bytes) }
            }
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
            Triple("history", GlassPalette.Signature, 1240),
            Triple("workspace-dark", GlassPalette.Signature, 1240),
            Triple("workspace-light", GlassPalette.SignatureLight, 1240),
            Triple("path-dark", GlassPalette.Signature, 1240),
            Triple("path-light", GlassPalette.SignatureLight, 1240),
            Triple("path-narrow", GlassPalette.Signature, 600),
        )) {
            val atlasState = AtlasViewState()
            val state = atlasState.exploration
            state.start(slice, "SessionStore")
            if (name.startsWith("path-")) {
                state.update(state.exploration.traceTo(slice, "RefreshSession"))
                state.update(state.exploration.inspect(slice, state.exploration.highlightedEdges.first(), preserveHighlight = true))
            } else if (name == "history") {
                state.update(state.exploration.select(slice, "Credentials"))
                state.update(state.exploration.select(slice, "SessionEvents"))
            } else if (!name.startsWith("file-") && !name.startsWith("workspace-")) state.update(state.exploration.inspect(slice, slice.edges[3]))
            ImageComposeScene(width, 760) {
                GlassTheme(palette) {
                    if (name.startsWith("workspace-")) AtlasContent(slice, {}, {}, viewTab = AtlasViewTab.FILE, atlasView = atlasState)
                    else DependencyExplorationPanel(slice, "SessionStore", state, {}, { _, _ -> }, Modifier.fillMaxSize())
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
