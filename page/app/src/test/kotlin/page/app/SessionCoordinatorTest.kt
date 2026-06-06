package page.app

import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import page.app.state.EditorWorkspaceState
import page.app.state.LayoutUiState
import page.app.state.WorkspaceState
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

    private fun coordinator(root: Path): SessionCoordinator = SessionCoordinator(
        editorWorkspace = editorWorkspace,
        layoutUiState = layoutUiState,
        workspaceState = workspaceState,
        terminalManagerProvider = { TerminalManager(root, scope) },
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

        coordinator(ws).restore(ws)

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

        coordinator(ws).restore(ws)

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
        coord.restore(ws)
        val snap = coord.snapshot()

        assertTrue(snap.splitEnabled)
        assertEquals("VERTICAL", snap.splitOrientation)
        assertEquals(0.35f, snap.splitRatio)
        assertEquals(320f, snap.sidebarWidth)
        assertTrue(snap.problemsOpen)
    }
}
