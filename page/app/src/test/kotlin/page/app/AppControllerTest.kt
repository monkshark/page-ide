package page.app

import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import page.app.state.EditorWorkspaceState
import page.app.state.IdeAppState
import page.app.state.LayoutUiState
import page.app.state.WorkspaceState
import page.editor.OpenTab
import page.editor.TabBook
import page.editor.UndoGroupTracker
import page.language.LspRouter
import page.runtime.RunController
import page.workspace.FileOpHistory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertIs

class AppControllerTest {

    private class Harness {
        val scope = CoroutineScope(SupervisorJob())
        val editorWorkspace = EditorWorkspaceState(undoTracker = { UndoGroupTracker() })
        val workspaceState = WorkspaceState(scope)
        val layoutUiState = LayoutUiState()
        val appState = IdeAppState()
        val fileOpHistory = FileOpHistory.Stack()
        val runController = RunController(scope) { }
        val outputState = OutputPanelState()

        var router: LspRouter = LspRouter(null, scope)
        var routerProviderCalls = 0

        var exitCalled = 0
        val clipboard = mutableListOf<String>()

        val controller = AppController(
            editorWorkspace = editorWorkspace,
            workspaceState = workspaceState,
            layoutUiState = layoutUiState,
            appState = appState,
            fileOpHistory = fileOpHistory,
            terminalManagerProvider = { error("terminalManager must not be used in these tests") },
            runController = runController,
            outputState = outputState,
            undoTracker = { UndoGroupTracker() },
            largeCopyScope = scope,
            lspRouterProvider = {
                routerProviderCalls++
                router
            },
            exitApplication = { exitCalled++ },
            frameProvider = { null },
            copyToClipboard = { clipboard.add(it) },
            withFileTreeWatcherClosed = { block -> block() },
        )

        fun setPrimaryTabs(vararg tabs: OpenTab, editorText: String = "") {
            editorWorkspace.primaryPane = editorWorkspace.primaryPane.copy(
                book = TabBook(tabs = tabs.toList(), activeIndex = if (tabs.isEmpty()) -1 else 0),
                editorValue = TextFieldValue(editorText),
            )
        }

        fun setOnClose(value: Boolean) {
            appState.pageSettings = appState.pageSettings.copy(
                autoSave = appState.pageSettings.autoSave.copy(onClose = value),
            )
        }
    }

    private fun cleanTab(path: Path): OpenTab = OpenTab(path = path, text = "x", savedText = "x")

    private fun dirtyTab(path: Path): OpenTab = OpenTab(path = path, text = "new", savedText = "old")

    @Test
    fun `requestExit with no dirty tabs exits immediately`() {
        val h = Harness()
        h.setPrimaryTabs(cleanTab(Paths.get("/p/A.kt")))

        h.controller.requestExit()

        assertEquals(1, h.exitCalled)
        assertNull(h.appState.pendingClose)
    }

    @Test
    fun `requestExit with dirty tabs and autoSave onClose saves and exits`() {
        val h = Harness()
        h.setOnClose(true)
        val tmp = Files.createTempFile("appctl", ".kt")
        try {
            h.setPrimaryTabs(dirtyTab(tmp), editorText = "new")

            h.controller.requestExit()

            assertEquals(1, h.exitCalled)
            assertNull(h.appState.pendingClose)
            assertEquals("new", Files.readString(tmp))
        } finally {
            Files.deleteIfExists(tmp)
        }
    }

    @Test
    fun `requestExit with dirty tabs and no autoSave onClose defers to pendingClose`() {
        val h = Harness()
        h.setOnClose(false)
        h.setPrimaryTabs(dirtyTab(Paths.get("/p/A.kt")), editorText = "new")

        h.controller.requestExit()

        assertEquals(0, h.exitCalled)
        assertIs<PendingClose.App>(h.appState.pendingClose)
    }

    @Test
    fun `toggleExpanded adds then removes the directory`() {
        val h = Harness()
        val dir = Files.createTempDirectory("appctl-tree")
        try {
            assertFalse(dir in h.workspaceState.expanded)

            h.controller.toggleExpanded(dir, false)
            assertTrue(dir in h.workspaceState.expanded)

            h.controller.toggleExpanded(dir, false)
            assertFalse(dir in h.workspaceState.expanded)
        } finally {
            Files.deleteIfExists(dir)
        }
    }

    @Test
    fun `cyclePalette advances palette and sets a future toast deadline`() {
        val h = Harness()
        val root = Files.createTempDirectory("appctl-ws")
        try {
            h.workspaceState.rootDir = root
            val before = h.appState.palette
            val now = System.currentTimeMillis()

            h.controller.cyclePalette()

            assertTrue(h.appState.palette != before, "palette should advance")
            assertTrue(h.appState.paletteToastUntil > now, "toast deadline should be in the future")
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `openFindInFiles is a no-op without a root and opens with one`() {
        val h = Harness()
        h.controller.openFindInFiles()
        assertFalse(h.appState.findInFiles, "no root: must not open")

        val root = Files.createTempDirectory("appctl-find")
        try {
            Files.writeString(root.resolve("A.kt"), "x")
            h.workspaceState.rootDir = root

            h.controller.openFindInFiles()

            assertTrue(h.appState.findInFiles)
            assertTrue(h.appState.findInFilesIndex.isNotEmpty())
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `lspRouterProvider is read lazily per call, never captured at construction`() {
        val h = Harness()
        assertEquals(0, h.routerProviderCalls, "router must not be captured eagerly at construction")

        val path = Paths.get("/p/note.unknownext")
        h.controller.requestReferences(path, 0, 0, "sym")
        val afterFirst = h.routerProviderCalls
        assertTrue(afterFirst > 0, "first call must consult the provider")
        assertIs<ReferencesQueryState>(h.appState.referencesState)

        h.router = LspRouter(null, h.scope)
        h.controller.requestReferences(path, 0, 0, "sym")
        assertTrue(h.routerProviderCalls > afterFirst, "second call must re-consult the provider (swapped router)")
    }
}
