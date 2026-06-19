package page.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import page.app.state.EditorWorkspaceState
import page.app.state.LayoutUiState
import page.app.state.WorkspaceState
import page.atlas.interaction.OverviewSelection
import page.atlas.render.AtlasViewState
import page.atlas.render.AtlasViewTab
import page.atlas.render.MapFilterState
import page.atlas.render.MapViewState
import page.atlas.render.OverviewViewState
import page.editor.SplitOrientation
import page.editor.UndoGroupTracker
import page.runtime.TerminalManager
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionCoordinatorTest {

    private val scope = CoroutineScope(SupervisorJob())
    private val editorWorkspace = EditorWorkspaceState(undoTracker = { UndoGroupTracker() })
    private val layoutUiState = LayoutUiState()
    private val workspaceState = WorkspaceState(scope)

    private fun newWorkspace(): Path {
        val dir = Files.createTempDirectory("page-ide-session-coord-")
        Runtime.getRuntime().addShutdownHook(Thread {
            runCatching { Files.walk(dir).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
        })
        return dir
    }

    private fun coordinator(
        root: Path,
        atlasMapView: MapViewState = MapViewState(),
        atlasView: AtlasViewState = AtlasViewState(),
        atlasOverviewState: OverviewViewState = OverviewViewState(),
    ): SessionCoordinator =
        SessionCoordinator(
            editorWorkspace = editorWorkspace,
            layoutUiState = layoutUiState,
            workspaceState = workspaceState,
            terminalManagerProvider = { TerminalManager(root, scope) },
            atlasMapView = atlasMapView,
            atlasView = atlasView,
            atlasOverviewState = atlasOverviewState,
        )

    @Test
    fun `restore populates holders from a saved session`() {
        val ws = newWorkspace()
        val sub = Files.createDirectory(ws.resolve("src"))
        SessionStore.save(
            ws,
            SessionFile(
                focusedPane = "SECONDARY",
                splitEnabled = true,
                splitOrientation = "VERTICAL",
                splitRatio = 0.4f,
                sidebarWidth = 300f,
                problemsOpen = true,
                problemsHeight = 250f,
                todoOpen = true,
                outputOpen = true,
                expandedDirs = listOf(sub.toString()),
                foldedStartLinesByPath = mapOf("X:/a.kt" to listOf(2, 5)),
            ),
        )

        runBlocking { coordinator(ws).restore(ws) }

        assertEquals(PaneSide.SECONDARY, editorWorkspace.focusedPane)
        assertTrue(editorWorkspace.splitEnabled)
        assertEquals(SplitOrientation.VERTICAL, editorWorkspace.splitOrientation)
        assertEquals(0.4f, editorWorkspace.splitState.ratio)
        assertEquals(300f, layoutUiState.sidebarWidth.value)
        assertTrue(layoutUiState.problemsOpen)
        assertEquals(250f, layoutUiState.problemsHeight.value)
        assertTrue(layoutUiState.todoOpen)
        assertTrue(layoutUiState.outputOpen)
        assertTrue(sub in workspaceState.expanded)
        assertEquals(setOf(2, 5), editorWorkspace.foldByPath["X:/a.kt"])
    }

    @Test
    fun `restore on a fresh workspace clears foldByPath and leaves defaults`() {
        val ws = newWorkspace()
        editorWorkspace.foldByPath = mapOf("stale" to setOf(1))

        runBlocking { coordinator(ws).restore(ws) }

        assertTrue(editorWorkspace.foldByPath.isEmpty())
        assertFalse(editorWorkspace.splitEnabled)
        assertEquals(PaneSide.PRIMARY, editorWorkspace.focusedPane)
    }

    @Test
    fun `snapshot reflects current holder state`() {
        val ws = newWorkspace()
        editorWorkspace.splitEnabled = true
        editorWorkspace.focusedPane = PaneSide.SECONDARY
        editorWorkspace.splitOrientation = SplitOrientation.VERTICAL
        layoutUiState.sidebarWidth = 280f.dp
        layoutUiState.problemsOpen = true
        layoutUiState.todoOpen = true

        val snap = coordinator(ws).snapshot()

        assertTrue(snap.splitEnabled)
        assertEquals("SECONDARY", snap.focusedPane)
        assertEquals("VERTICAL", snap.splitOrientation)
        assertEquals(280f, snap.sidebarWidth)
        assertTrue(snap.problemsOpen)
        assertTrue(snap.todoOpen)
    }

    @Test
    fun `atlas map view round-trips through save and restore`() {
        val ws = newWorkspace()
        val view = MapViewState()
        view.pan = Offset(33f, -7f)
        view.scale = 1.4f
        view.userOffsets["box"] = Offset(5f, 6f)
        view.expandOrder.add("dir")
        view.expandedDirs = setOf("dir")
        view.filter = MapFilterState(focusDir = "dir", hiddenDirs = setOf("h"), mutedDirs = setOf("m"))
        view.pinnedIds = setOf("p1", "p2")
        SessionStore.save(ws, coordinator(ws, view).snapshot())

        val restored = MapViewState()
        runBlocking { coordinator(ws, restored).restore(ws) }

        assertEquals(Offset(33f, -7f), restored.pan)
        assertEquals(1.4f, restored.scale)
        assertTrue(restored.fitted)
        assertEquals(Offset(5f, 6f), restored.userOffsets["box"])
        assertEquals(listOf("dir"), restored.expandOrder.toList())
        assertEquals(setOf("dir"), restored.expandedDirs)
        assertEquals("dir", restored.filter.focusDir)
        assertEquals(setOf("h"), restored.filter.hiddenDirs)
        assertEquals(setOf("m"), restored.filter.mutedDirs)
        assertEquals(setOf("p1", "p2"), restored.pinnedIds)
    }

    @Test
    fun `atlas overview drill and camera round-trip`() {
        val ws = newWorkspace()
        val overview = OverviewViewState()
        overview.selection = OverviewSelection(drillPath = listOf("ws/app", "ws/app/ui"))
        overview.camera.savedViews["ws/app"] = Offset(10f, 20f) to 1.2f
        overview.camera.pan = Offset(-4f, 8f)
        overview.camera.scale = 1.5f
        SessionStore.save(ws, coordinator(ws, atlasOverviewState = overview).snapshot())

        val restored = OverviewViewState()
        runBlocking { coordinator(ws, atlasOverviewState = restored).restore(ws) }

        assertEquals(listOf("ws/app", "ws/app/ui"), restored.selection.drillPath)
        assertEquals(Offset(10f, 20f) to 1.2f, restored.camera.savedViews["ws/app"])
        val scopeKey = listOf("ws/app", "ws/app/ui").joinToString(" ")
        assertEquals(Offset(-4f, 8f) to 1.5f, restored.camera.savedViews[scopeKey])
        assertEquals(Offset(-4f, 8f), restored.camera.pan)
        assertEquals(1.5f, restored.camera.scale)
        assertTrue(restored.camera.fitted)
    }

    @Test
    fun `atlas graph camera and view tab round-trip`() {
        val ws = newWorkspace()
        val view = AtlasViewState()
        view.yaw = 1.1f
        view.pitch = 0.3f
        view.zoomUser = 2f
        layoutUiState.atlasViewTab = AtlasViewTab.CALLS
        SessionStore.save(ws, coordinator(ws, atlasView = view).snapshot())

        layoutUiState.atlasViewTab = AtlasViewTab.RELATIONS
        val restored = AtlasViewState()
        runBlocking { coordinator(ws, atlasView = restored).restore(ws) }

        assertEquals(1.1f, restored.yaw)
        assertEquals(0.3f, restored.pitch)
        assertEquals(2f, restored.zoomUser)
        assertEquals(AtlasViewTab.CALLS, layoutUiState.atlasViewTab)
    }

    @Test
    fun `restore without atlas map keeps the view unfitted`() {
        val ws = newWorkspace()
        SessionStore.save(ws, SessionFile())
        val restored = MapViewState()
        runBlocking { coordinator(ws, restored).restore(ws) }
        assertFalse(restored.fitted)
        assertEquals(0f, restored.scale)
    }

    @Test
    fun `save then restore then snapshot round-trips core fields`() {
        val ws = newWorkspace()
        SessionStore.save(
            ws,
            SessionFile(
                splitEnabled = true,
                splitOrientation = "VERTICAL",
                splitRatio = 0.35f,
                sidebarWidth = 320f,
                problemsOpen = true,
            ),
        )

        val coord = coordinator(ws)
        runBlocking { coord.restore(ws) }
        val snap = coord.snapshot()

        assertTrue(snap.splitEnabled)
        assertEquals("VERTICAL", snap.splitOrientation)
        assertEquals(0.35f, snap.splitRatio)
        assertEquals(320f, snap.sidebarWidth)
        assertTrue(snap.problemsOpen)
    }
}
