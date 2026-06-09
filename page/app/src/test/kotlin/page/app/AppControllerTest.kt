package page.app

import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import page.app.mvi.IdeDispatcher
import page.app.mvi.IdeEffectHandler
import page.app.mvi.IdeStore
import page.app.state.EditorWorkspaceState
import page.app.state.IdeAppState
import page.app.state.LayoutUiState
import page.app.state.WorkspaceState
import page.editor.OpenTab
import page.editor.TabBook
import page.editor.UndoGroupTracker
import page.language.LspRouter
import page.runtime.CURRENT_FILE_ID
import page.runtime.RunConfig
import page.runtime.RunConfigsState
import page.runtime.RunController
import page.runtime.RunEvent
import page.ui.GlassPalette
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
        val store = IdeStore()
        val editorWorkspace = EditorWorkspaceState(undoTracker = { UndoGroupTracker() }, store = store)
        val workspaceState = WorkspaceState(scope, store)
        val layoutUiState = LayoutUiState(store)
        val appState = IdeAppState(store)
        val effects = IdeEffectHandler()
        val fileOpHistory = FileOpHistory.Stack()
        val runController = RunController(scope) { }
        val outputState = OutputPanelState()
        val todo = TodoController(null, scope)

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
            appScope = scope,
            lspRouterProvider = {
                routerProviderCalls++
                router
            },
            todoProvider = { todo },
            exitApplication = { exitCalled++ },
            frameProvider = { null },
            copyToClipboard = { clipboard.add(it) },
            withFileTreeWatcherClosed = { block -> block() },
            dispatch = IdeDispatcher(store, effects).onEvent,
        )

        init {
            effects.bind(controller::handleEffect)
        }

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
    fun `search seam opens search and applies a query change through dispatch`() {
        val h = Harness()
        h.setPrimaryTabs(cleanTab(Paths.get("/p/A.kt")), editorText = "foo bar foo")

        h.controller.openSearch()
        assertTrue(h.editorWorkspace.primaryPane.search != null, "openSearch must initialize search state")

        h.controller.onQueryChange(PaneSide.PRIMARY, "foo")
        val search = h.editorWorkspace.primaryPane.search
        assertEquals("foo", search?.query)
        assertEquals(2, search?.matches?.size)

        h.controller.closeSearch(PaneSide.PRIMARY)
        assertNull(h.editorWorkspace.primaryPane.search, "closeSearch must clear search state")
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
    fun `toggleFindInFiles is a no-op without a root and toggles open then closed with one`() {
        val h = Harness()
        h.controller.toggleFindInFiles()
        assertFalse(h.appState.findInFiles, "no root: must not open")

        val root = Files.createTempDirectory("appctl-find")
        try {
            Files.writeString(root.resolve("A.kt"), "x")
            h.workspaceState.rootDir = root

            h.controller.toggleFindInFiles()

            assertTrue(h.appState.findInFiles, "with root: opens and walks the index")
            assertTrue(h.appState.findInFilesIndex.isNotEmpty())

            h.controller.toggleFindInFiles()

            assertFalse(h.appState.findInFiles, "toggling again closes")
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `cyclePalette persists to AppSettings when there is no root`() {
        val h = Harness()
        val settingsDir = Files.createTempDirectory("appctl-palette")
        val prior = System.getProperty("page.settings.dir")
        System.setProperty("page.settings.dir", settingsDir.toString())
        try {
            val before = h.appState.palette

            h.controller.cyclePalette()

            val advanced = h.appState.palette
            assertTrue(advanced != before, "palette should advance")
            assertEquals(advanced, AppSettings.loadUi().palette, "no root: palette persisted to AppSettings")
        } finally {
            if (prior != null) System.setProperty("page.settings.dir", prior)
            else System.clearProperty("page.settings.dir")
            settingsDir.toFile().deleteRecursively()
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

    @Test
    fun `settingsBinding onApply updates pageSettings and palette in memory`() {
        val h = Harness()
        val settingsDir = Files.createTempDirectory("appctl-settings")
        val prior = System.getProperty("page.settings.dir")
        System.setProperty("page.settings.dir", settingsDir.toString())
        try {
            val nextPalette = GlassPalette.values().first { it != h.appState.palette }
            val updated = h.appState.pageSettings.copy(
                ui = h.appState.pageSettings.ui.copy(palette = nextPalette),
            )

            h.controller.settingsBinding().onApply(updated)

            assertEquals(updated, h.appState.pageSettings)
            assertEquals(nextPalette, h.appState.palette)
        } finally {
            if (prior != null) System.setProperty("page.settings.dir", prior)
            else System.clearProperty("page.settings.dir")
            settingsDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `settingsBinding onToggle and onPanelClose flip the dialog flag`() {
        val h = Harness()
        assertFalse(h.appState.settingsDialogOpen)

        h.controller.settingsBinding().onToggle()
        assertTrue(h.appState.settingsDialogOpen)

        h.controller.settingsBinding().onPanelClose()
        assertFalse(h.appState.settingsDialogOpen)
    }

    @Test
    fun `runPanelBinding selects a config and mirrors output running state`() {
        val h = Harness()
        h.controller.runPanelBinding().onSelectRunConfig(CURRENT_FILE_ID)
        assertEquals(CURRENT_FILE_ID, h.appState.runState.activeId)

        assertFalse(h.controller.runPanelBinding().runIsRunning)
        h.outputState.onEvent(RunEvent.Started(command = "echo", args = emptyList(), workingDir = null))
        assertTrue(h.controller.runPanelBinding().runIsRunning)
    }

    @Test
    fun `runPanelBinding onStartRun delegates through effect and opens output panel`() {
        val h = Harness()
        h.appState.runState = RunConfigsState(
            configs = listOf(RunConfig(id = "r", name = "r", command = "echo hi")),
            activeId = "r",
        )
        assertFalse(h.layoutUiState.outputOpen)

        h.controller.runPanelBinding().onStartRun()

        assertTrue(h.layoutUiState.outputOpen, "Run.Start effect must open the output panel")
    }

    @Test
    fun `runPanelBinding onOutputClear clears output state`() {
        val h = Harness()
        h.outputState.onEvent(RunEvent.Started(command = "echo", args = emptyList(), workingDir = null))
        h.outputState.onEvent(RunEvent.Exited(code = 0, durationMs = 1))
        assertEquals(0, h.outputState.lastExitCode)

        h.controller.runPanelBinding().onOutputClear()

        assertNull(h.outputState.lastExitCode)
    }

    @Test
    fun `codeActionPreviewBinding onDismiss hides panel and bumps editor focus`() {
        val h = Harness()
        h.appState.codeActionOpen = true
        val before = h.appState.editorFocusVersion

        h.controller.codeActionPreviewBinding().onDismiss()

        assertFalse(h.appState.codeActionOpen)
        assertEquals(before + 1, h.appState.editorFocusVersion)
    }

    @Test
    fun `codeActionPreviewBinding onApply hides panel and bumps editor focus`() {
        val h = Harness()
        h.appState.codeActionOpen = true
        val before = h.appState.editorFocusVersion
        val nonExecutable = page.lsp.CodeActionEntry(
            title = "noop",
            kind = null,
            isPreferred = false,
            edit = page.lsp.RenameWorkspaceEdit.EMPTY,
            command = null,
        )

        h.controller.codeActionPreviewBinding().onApply(nonExecutable)

        assertFalse(h.appState.codeActionOpen)
        assertEquals(before + 1, h.appState.editorFocusVersion)
    }

    @Test
    fun `fileTreePanelActions reflects undo history and tree focus changes`() {
        val h = Harness()
        assertFalse(h.controller.fileTreePanelActions().canUndoFileOp, "empty history: nothing to undo")

        h.fileOpHistory.push(FileOpHistory.RenameOp(Paths.get("/p/A.kt"), Paths.get("/p/B.kt")))
        assertTrue(h.controller.fileTreePanelActions().canUndoFileOp, "after push: undo available")

        h.controller.fileTreePanelActions().onTreeFocusChanged(true)
        assertTrue(h.workspaceState.fileTreeFocused)
    }

    @Test
    fun `onActiveTabChanged syncs the pane editorValue to the active tab`() {
        val h = Harness()
        h.setPrimaryTabs(cleanTab(Paths.get("/p/A.kt")), editorText = "stale")
        assertEquals("stale", h.editorWorkspace.primaryPane.editorValue.text)

        h.controller.onActiveTabChanged(PaneSide.PRIMARY)

        assertEquals("x", h.editorWorkspace.primaryPane.editorValue.text)
    }

    @Test
    fun `onSplitEnabledChanged resets focus to primary only when split is off`() {
        val h = Harness()

        h.editorWorkspace.splitEnabled = true
        h.editorWorkspace.focusedPane = PaneSide.SECONDARY
        h.controller.onSplitEnabledChanged()
        assertEquals(PaneSide.SECONDARY, h.editorWorkspace.focusedPane, "split on: focus untouched")

        h.editorWorkspace.splitEnabled = false
        h.controller.onSplitEnabledChanged()
        assertEquals(PaneSide.PRIMARY, h.editorWorkspace.focusedPane, "split off: focus snaps to primary")
    }

    @Test
    fun `onFileDialogVisibilityChanged bumps tree focus tick once per open-close cycle`() {
        val h = Harness()
        val before = h.appState.pendingTreeFocusTick

        h.controller.onFileDialogVisibilityChanged(false)
        assertEquals(before, h.appState.pendingTreeFocusTick, "close without prior open: no bump")

        h.controller.onFileDialogVisibilityChanged(true)
        assertEquals(before, h.appState.pendingTreeFocusTick, "open: not yet")

        h.controller.onFileDialogVisibilityChanged(false)
        assertEquals(before + 1, h.appState.pendingTreeFocusTick, "close after open: exactly one bump")
    }
}
