package page.app

import page.runtime.*
import page.workspace.*
import page.workspace.sync.PackageSyncEngine
import page.app.input.ShortcutAction
import page.app.input.ShortcutResolver
import page.app.filetree.FileTreeActionExecutor
import page.app.filetree.FileTreeContextController
import page.app.filetree.LargeCopyDialogState
import page.app.domain.FileOperationsInteractor
import page.app.filetree.PasteEntryDialogState
import page.app.lsp.LspEditorInterconnector
import page.app.state.DebouncedSaver
import page.app.state.EditorWorkspaceState
import page.app.state.LayoutUiState
import page.app.state.WorkspaceState
import page.app.ui.IdeMainLayout
import page.app.ui.PaletteToast
import page.app.ui.dialog.FileTreeCreateDialog
import page.app.ui.dialog.FileTreePasteDialog
import page.app.ui.dialog.FileTreeRenameDeleteDialogs
import page.app.ui.dialog.NavigationPickerDialogs
import page.app.ui.dialog.PendingCloseDialog
import page.app.ui.editor.CommandPaletteController
import page.app.ui.editor.EditorHistoryController
import page.app.ui.editor.EditorSearchController
import page.app.ui.editor.EditorTabController
import page.app.ui.editor.FileMenuController
import page.app.utils.applyReplaceToBook
import page.app.utils.isKotlinSource
import page.app.utils.offsetToLineChar
import page.app.utils.windowTitle
import page.lsp.GenericLanguageBackend
import page.lsp.LanguageRegistry
import page.lsp.LspBackends
import page.language.LspController
import page.language.LspRouter
import page.language.rememberLspRouter

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import page.core.PageIdentity
import page.editor.EditHistory
import page.editor.EditSnapshot
import page.editor.FileDocument
import page.editor.UndoGroupTracker
import page.editor.FileKind
import page.editor.FileKinds
import page.editor.IndexedFile
import page.editor.OpenTab
import page.editor.ProjectFileIndex
import page.editor.ProjectGrep
import page.editor.Replace
import page.editor.SearchState
import page.editor.SplitOrientation
import page.editor.SplitPaneState
import page.editor.SyntaxLexers
import page.editor.TabBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import page.lsp.CodeActionEntry
import page.lsp.RenameApply
import page.lsp.RenameFileChange
import page.lsp.RenameWorkspaceEdit
import page.lsp.pickSingleOtherReference
import page.perf.PerfRegistry
import page.perf.StartupKind
import page.perf.StartupPhases
import page.ui.CompactDropdown
import page.ui.CompactMenuItem
import page.ui.Glass
import page.ui.GlassPalette
import page.ui.GlassTheme
import page.ui.GlassTooltip
import page.ui.SplitPane
import page.ui.glassTokensFor
import java.awt.Cursor
import java.nio.file.Path

fun main() {
    PerfRegistry.start(StartupKind.COLD).begin(StartupPhases.COMPOSE_INIT)
    application {
        AppContent()
    }
}

@Composable
private fun androidx.compose.ui.window.ApplicationScope.AppContent() {
    val windowState = rememberWindowState(
        placement = androidx.compose.ui.window.WindowPlacement.Maximized,
        width = 1280.dp,
        height = 800.dp,
    )
    val undoTrackerPrimary = remember { UndoGroupTracker() }
    val undoTrackerSecondary = remember { UndoGroupTracker() }
    fun undoTracker(side: PaneSide): UndoGroupTracker = when (side) {
        PaneSide.PRIMARY -> undoTrackerPrimary
        PaneSide.SECONDARY -> undoTrackerSecondary
    }
    val editorWorkspace = remember { EditorWorkspaceState(undoTracker = ::undoTracker) }
    var primaryPane by editorWorkspace::primaryPane
    var secondaryPane by editorWorkspace::secondaryPane
    var focusedPane by editorWorkspace::focusedPane
    val appScope = rememberCoroutineScope()
    val workspaceState = remember { WorkspaceState(appScope) }
    var rootDir by workspaceState::rootDir
    var expanded by workspaceState::expanded
    var treeSelection by workspaceState::treeSelection
    var treeRevision by workspaceState::treeRevision
    var editorScrollByPath by editorWorkspace::editorScrollByPath
    val layoutUiState = remember { LayoutUiState() }
    var sidebarWidth: Dp by layoutUiState::sidebarWidth
    var pendingClose: PendingClose? by remember { mutableStateOf(null) }
    var quickOpen by layoutUiState::quickOpen
    var quickOpenIndex by layoutUiState::quickOpenIndex
    var findInFiles by remember { mutableStateOf(false) }
    var findInFilesIndex by remember { mutableStateOf<List<IndexedFile>>(emptyList()) }
    var splitEnabled by editorWorkspace::splitEnabled
    var splitOrientation by editorWorkspace::splitOrientation
    var splitState by editorWorkspace::splitState
    var problemsOpen by layoutUiState::problemsOpen
    var problemsHeight: Dp by layoutUiState::problemsHeight
    var problemsCollapsed by layoutUiState::problemsCollapsed
    var problemsFileOrder by layoutUiState::problemsFileOrder
    var todoOpen by layoutUiState::todoOpen
    var todoHeight: Dp by layoutUiState::todoHeight
    var todoCollapsed by layoutUiState::todoCollapsed
    var todoFileOrder by layoutUiState::todoFileOrder
    var terminalOpen by layoutUiState::terminalOpen
    var terminalHeight: Dp by layoutUiState::terminalHeight
    val terminalScope = rememberCoroutineScope()
    val terminalManager = remember(rootDir) {
        val dir = rootDir ?: Path.of(System.getProperty("user.home"))
        TerminalManager(dir, terminalScope)
    }
    DisposableEffect(terminalManager) {
        onDispose { terminalManager.closeAll() }
    }
    var runState: RunConfigsState by remember { mutableStateOf(RunConfigsState()) }
    var runDialogOpen by remember { mutableStateOf(false) }
    var outputOpen by layoutUiState::outputOpen
    var outputHeight: Dp by layoutUiState::outputHeight
    val outputState = remember { OutputPanelState() }
    val runScope = rememberCoroutineScope()
    val runController = remember {
        RunController(runScope) { event -> outputState.onEvent(event) }
    }
    DisposableEffect(runController) {
        onDispose { runController.stop() }
    }
    var referencesState: ReferencesQueryState? by remember { mutableStateOf(null) }
    var referencesHeight: Dp by layoutUiState::referencesHeight
    var createDialog: CreateEntryDialogState? by layoutUiState::createDialog
    var renameDialog: RenameEntryDialogState? by layoutUiState::renameDialog
    var deleteDialog: DeleteEntryDialogState? by layoutUiState::deleteDialog
    var pasteDialog: PasteEntryDialogState? by layoutUiState::pasteDialog
    var largeCopyState: LargeCopyDialogState? by layoutUiState::largeCopyState
    val largeCopyScope = rememberCoroutineScope()
    val fileOpHistory = remember { FileOpHistory.Stack() }
    var fileOpHistoryVersion by remember { mutableStateOf(0) }
    var fileTreeFocused by workspaceState::fileTreeFocused
    var fileOpConfirm: FileOpConfirmState? by remember { mutableStateOf(null) }
    var pendingTreeFocusTick by remember { mutableStateOf(0) }
    var pageSettings: PageSettings by remember {
        mutableStateOf(
            PageSettings(
                autoSave = AppSettings.loadAutoSave(),
                editor = AppSettings.loadEditor(),
                lsp = AppSettings.loadLsp(),
                autoInput = AppSettings.loadAutoInput(),
                ui = AppSettings.loadUi(),
                run = AppSettings.loadRun(),
            )
        )
    }
    var palette: GlassPalette by remember { mutableStateOf(pageSettings.ui.palette) }
    LaunchedEffect(pageSettings.ui.sidebarWidth) {
        sidebarWidth = pageSettings.ui.sidebarWidth.dp
    }
    var paletteToastUntil: Long by remember { mutableStateOf(0L) }
    val autoSaveOptions: AutoSaveOptions = pageSettings.autoSave
    var settingsDialogOpen by remember { mutableStateOf(false) }
    var dropResultToast: DropResultToastState? by remember { mutableStateOf(null) }
    var documentSymbolOpen by layoutUiState::documentSymbolOpen
    var documentSymbolList by layoutUiState::documentSymbolList
    var documentSymbolUri by layoutUiState::documentSymbolUri
    var workspaceSymbolOpen by layoutUiState::workspaceSymbolOpen
    var codeActionOpen by remember { mutableStateOf(false) }
    var codeActionList by remember { mutableStateOf<List<CodeActionEntry>>(emptyList()) }
    var codeActionUri by remember { mutableStateOf<String?>(null) }
    var codeActionText by remember { mutableStateOf<String?>(null) }
    var codeActionSelected by remember { mutableStateOf(0) }
    var editorFocusVersion by remember { mutableStateOf(0) }
    val lspRouter = rememberLspRouter(workspaceRoot = rootDir)
    val currentLspRouter by rememberUpdatedState(lspRouter)
    registerAllBackends()
    val todo = rememberTodoController(workspaceRoot = rootDir)
    val todoItems by todo.items

    fun paneOf(side: PaneSide): EditorPaneState = editorWorkspace.paneOf(side)

    fun setPane(side: PaneSide, value: EditorPaneState) = editorWorkspace.setPane(side, value)

    fun mutatePane(side: PaneSide, transform: (EditorPaneState) -> EditorPaneState) =
        editorWorkspace.mutatePane(side, transform)

    fun mutateFocused(transform: (EditorPaneState) -> EditorPaneState) =
        editorWorkspace.mutateFocused(transform)

    fun focused(): EditorPaneState = editorWorkspace.focused()

    LaunchedEffect(primaryPane.book.activeIndex, primaryPane.book.tabs.size) {
        val active = primaryPane.book.active
        val newValue = if (active != null) {
            val caret = active.caret.coerceIn(0, active.text.length)
            TextFieldValue(active.text, TextRange(caret))
        } else TextFieldValue("")
        undoTrackerPrimary.reset()
        primaryPane = primaryPane.copy(
            editorValue = newValue,
            search = primaryPane.search?.retarget(newValue.text),
        )
    }

    LaunchedEffect(secondaryPane.book.activeIndex, secondaryPane.book.tabs.size) {
        val active = secondaryPane.book.active
        val newValue = if (active != null) {
            val caret = active.caret.coerceIn(0, active.text.length)
            TextFieldValue(active.text, TextRange(caret))
        } else TextFieldValue("")
        undoTrackerSecondary.reset()
        secondaryPane = secondaryPane.copy(
            editorValue = newValue,
            search = secondaryPane.search?.retarget(newValue.text),
        )
    }

    LaunchedEffect(splitEnabled) {
        if (!splitEnabled) focusedPane = PaneSide.PRIMARY
    }

    val anyFileDialogOpen = createDialog != null || renameDialog != null || deleteDialog != null ||
        pasteDialog != null || largeCopyState != null || fileOpConfirm != null
    var hadFileDialog by remember { mutableStateOf(false) }
    LaunchedEffect(anyFileDialogOpen) {
        if (anyFileDialogOpen) {
            hadFileDialog = true
        } else if (hadFileDialog) {
            hadFileDialog = false
            pendingTreeFocusTick++
        }
    }

    var sessionLoaded by remember { mutableStateOf(false) }
    var foldByPath by editorWorkspace::foldByPath
    var historyFile by remember { mutableStateOf(HistoryFile()) }
    var historyLoaded by remember { mutableStateOf(false) }
    var workspaceFile by remember { mutableStateOf(WorkspaceFile()) }
    LaunchedEffect(rootDir) {
        sessionLoaded = false
        val root = rootDir
        if (root == null) {
            sessionLoaded = true
            return@LaunchedEffect
        }
        val session = runCatching { SessionStore.load(root) }.getOrNull()
        if (session != null) {
            primaryPane = primaryPane.copy(book = restoreTabBook(session.primary))
            secondaryPane = secondaryPane.copy(book = restoreTabBook(session.secondary))
            focusedPane = runCatching { PaneSide.valueOf(session.focusedPane) }
                .getOrDefault(PaneSide.PRIMARY)
            splitEnabled = session.splitEnabled
            splitOrientation = runCatching { SplitOrientation.valueOf(session.splitOrientation) }
                .getOrDefault(SplitOrientation.HORIZONTAL)
            splitState = SplitPaneState(ratio = session.splitRatio.coerceIn(0.1f, 0.9f))
            sidebarWidth = session.sidebarWidth.coerceIn(160f, 600f).dp
            problemsOpen = session.problemsOpen
            problemsHeight = session.problemsHeight.coerceIn(120f, 600f).dp
            problemsCollapsed = session.problemsCollapsed.toSet()
            problemsFileOrder = session.problemsFileOrder
            todoOpen = session.todoOpen
            todoHeight = session.todoHeight.coerceIn(120f, 600f).dp
            todoCollapsed = session.todoCollapsed.toSet()
            todoFileOrder = session.todoFileOrder
            terminalOpen = session.terminalOpen
            terminalHeight = session.terminalHeight.coerceIn(120f, 600f).dp
            outputOpen = session.outputOpen
            outputHeight = session.outputHeight.coerceIn(120f, 1200f).dp
            if (session.terminalTabs.isNotEmpty()) {
                terminalManager.restoreFrom(
                    names = session.terminalTabs.map { it.name },
                    activeIndex = session.terminalActiveIndex,
                    autoStart = true,
                )
            }
            foldByPath = session.foldedStartLinesByPath.mapValues { it.value.toSet() }
            val restoredExpanded = restoreExpandedDirs(session.expandedDirs)
            if (restoredExpanded.isNotEmpty()) expanded = restoredExpanded
            editorScrollByPath = session.editorScrollByPath
                .mapNotNull { (s, snap) ->
                    val p = runCatching { java.nio.file.Path.of(s) }.getOrNull() ?: return@mapNotNull null
                    p to EditorScrollSnapshot(vertical = snap.vertical, horizontal = snap.horizontal)
                }
                .toMap()
        } else {
            foldByPath = emptyMap()
        }
        sessionLoaded = true
    }

    val onFoldChange: (Path, Set<Int>) -> Unit = { path, lines ->
        val key = path.toString()
        foldByPath = if (lines.isEmpty()) foldByPath - key else foldByPath + (key to lines)
    }
    val foldedLinesFor: (Path?) -> Set<Int> = { p ->
        p?.let { foldByPath[it.toString()] } ?: emptySet()
    }

    val sessionSnapshot = SessionFile(
        primary = paneSnapshot(primaryPane),
        secondary = paneSnapshot(secondaryPane),
        focusedPane = focusedPane.name,
        splitEnabled = splitEnabled,
        splitOrientation = splitOrientation.name,
        splitRatio = splitState.ratio,
        sidebarWidth = sidebarWidth.value,
        problemsOpen = problemsOpen,
        problemsHeight = problemsHeight.value,
        problemsCollapsed = problemsCollapsed.toList().sorted(),
        problemsFileOrder = problemsFileOrder,
        todoOpen = todoOpen,
        todoHeight = todoHeight.value,
        todoCollapsed = todoCollapsed.toList().sorted(),
        todoFileOrder = todoFileOrder,
        terminalOpen = terminalOpen,
        terminalHeight = terminalHeight.value,
        terminalTabs = terminalManager.snapshotNames().map { SessionTerminalTab(name = it) },
        terminalActiveIndex = terminalManager.activeIndex(),
        outputOpen = outputOpen,
        outputHeight = outputHeight.value,
        foldedStartLinesByPath = foldByPath.mapValues { it.value.toList().sorted() },
        expandedDirs = expanded.map { it.toString() }.sorted(),
        editorScrollByPath = editorScrollByPath
            .mapKeys { it.key.toString() }
            .mapValues { SessionScrollSnapshot(vertical = it.value.vertical, horizontal = it.value.horizontal) },
    )
    LaunchedEffect(rootDir, sessionLoaded, sessionSnapshot) {
        if (!sessionLoaded) return@LaunchedEffect
        val root = rootDir ?: return@LaunchedEffect
        kotlinx.coroutines.delay(500)
        runCatching { SessionStore.save(root, sessionSnapshot) }
    }

    LaunchedEffect(Unit) {
        workspaceState.launchPersistence(
            loaders = listOf(
                { root ->
                    if (root != null) {
                        val recovery = runCatching { RenameTransaction.recover(root) }.getOrNull()
                        val logHint = "see ${RenameTransaction.logFile(root)}"
                        when (recovery) {
                            is RenameTransaction.RecoveryResult.Resumed ->
                                println("[rename] recovered: resumed ${recovery.marker.from.fileName} → ${recovery.marker.to.fileName} ($logHint)")
                            is RenameTransaction.RecoveryResult.RolledBack ->
                                println("[rename] recovered: rolled back partial ${recovery.marker.to.fileName} ($logHint)")
                            is RenameTransaction.RecoveryResult.Skipped ->
                                println("[rename] recovered: skipped (${recovery.reason}) ($logHint)")
                            is RenameTransaction.RecoveryResult.Failed ->
                                println("[rename] recovery failed: ${recovery.message} ($logHint)")
                            else -> {}
                        }
                    }
                    todo.scanWorkspaceAsync()
                },
                { root ->
                    runState = if (root == null) RunConfigsState()
                    else runCatching { RunConfigStore.load(root) }.getOrDefault(RunConfigsState())
                },
                { root ->
                    historyLoaded = false
                    if (root == null) {
                        historyFile = HistoryFile()
                    } else {
                        historyFile = runCatching { HistoryStore.load(root) }.getOrDefault(HistoryFile())
                    }
                    historyLoaded = true
                },
                { root ->
                    if (root == null) {
                        workspaceFile = WorkspaceFile()
                    } else {
                        val ws = runCatching { WorkspaceStore.load(root) }.getOrDefault(WorkspaceFile())
                        workspaceFile = ws
                        val name = ws.palette
                        if (name != null) {
                            val resolved = GlassPalette.values().firstOrNull { it.name.equals(name, ignoreCase = true) }
                            if (resolved != null) palette = resolved
                        }
                    }
                },
            ),
            savers = listOf(
                DebouncedSaver(
                    debounceMs = 400,
                    revision = { runState },
                    save = { root -> runCatching { RunConfigStore.save(root, runState) } },
                ),
                DebouncedSaver(
                    debounceMs = 500,
                    revision = { historyLoaded to historyFile },
                    save = { root -> if (historyLoaded) runCatching { HistoryStore.save(root, historyFile) } },
                ),
            ),
        )
    }
    val fileTreeWatcherHolder = remember { java.util.concurrent.atomic.AtomicReference<FileTreeWatcher?>(null) }
    var fileTreeWatcherEpoch by remember { mutableStateOf(0) }
    LaunchedEffect(rootDir, expanded, fileTreeWatcherEpoch) {
        val dirs = watchableDirs(rootDir, expanded)
        if (dirs.isEmpty()) return@LaunchedEffect
        val watcher = FileTreeWatcher(dirs)
        if (!watcher.active) { watcher.close(); return@LaunchedEffect }
        fileTreeWatcherHolder.set(watcher)
        try {
            watcher.runLoop { treeRevision++ }
        } finally {
            watcher.close()
            fileTreeWatcherHolder.compareAndSet(watcher, null)
        }
    }
    val withFileTreeWatcherClosed: (() -> Unit) -> Unit = { block ->
        val w = fileTreeWatcherHolder.getAndSet(null)
        runCatching { w?.close() }
        if (w != null) {
            runCatching { Thread.sleep(200L) }
        }
        try {
            block()
        } finally {
            fileTreeWatcherEpoch++
        }
    }
    val addRecentFile: (Path) -> Unit = { p ->
        historyFile = historyFile.copy(
            recentFiles = pushMru(historyFile.recentFiles, p.toString(), HistoryStore.MAX_RECENT_FILES),
        )
    }
    val addSearchQuery: (String) -> Unit = { q ->
        if (q.isNotBlank()) historyFile = historyFile.copy(
            searchHistory = pushMru(historyFile.searchHistory, q, HistoryStore.MAX_SEARCH_HISTORY),
        )
    }
    val addReplaceText: (String) -> Unit = { r ->
        if (r.isNotEmpty()) historyFile = historyFile.copy(
            replaceHistory = pushMru(historyFile.replaceHistory, r, HistoryStore.MAX_REPLACE_HISTORY),
        )
    }

    val focusedActivePath = focused().book.active?.path
    val focusedActiveText = focused().editorValue.text
    LaunchedEffect(focusedActivePath) {
        val path = focusedActivePath
        if (path != null) {
            val ctrl = currentLspRouter.controllerFor(path)
            val langId = currentLspRouter.languageIdFor(path)
            if (ctrl != null && langId != null) {
                ctrl.didOpen(path, langId, focused().editorValue.text)
            }
        }
    }
    LaunchedEffect(focusedActivePath, focusedActiveText) {
        val path = focusedActivePath
        if (path != null) {
            currentLspRouter.controllerFor(path)?.didChange(path, focusedActiveText)
        }
        if (path != null) todo.updateFile(path, focusedActiveText)
    }

    val openInTab: (Path) -> Unit = { picked ->
        val kind = FileKinds.classify(picked)
        if (kind.isEditableAsText) {
            FileDocument.loadOrNull(picked)?.let { text ->
                mutateFocused { it.copy(book = it.book.openOrFocus(picked, text)) }
                addRecentFile(picked)
            }
        } else {
            mutateFocused { it.copy(book = it.book.openOrFocus(picked, "")) }
            addRecentFile(picked)
        }
    }
    val openInTabAt: (Path, Int) -> Unit = { picked, offset ->
        if (FileKinds.classify(picked).isEditableAsText) {
            val pane = focused()
            val existing = pane.book.tabs.indexOfFirst { it.path == picked }
            if (existing >= 0) {
                val tab = pane.book.tabs[existing]
                val caret = offset.coerceIn(0, tab.text.length)
                mutateFocused {
                    it.copy(
                        book = it.book.activate(existing).updateActive(tab.text, caret),
                        editorValue = TextFieldValue(tab.text, TextRange(caret)),
                    )
                }
                addRecentFile(picked)
            } else {
                FileDocument.loadOrNull(picked)?.let { text ->
                    val caret = offset.coerceIn(0, text.length)
                    mutateFocused {
                        val opened = it.book.openOrFocus(picked, text)
                        it.copy(
                            book = opened.updateActive(text, caret),
                            editorValue = TextFieldValue(text, TextRange(caret)),
                        )
                    }
                    addRecentFile(picked)
                }
            }
        }
    }

    val fileOperationsInteractor = remember {
        FileOperationsInteractor(
            readFileText = { p -> FileDocument.loadOrNull(p) },
            applyTextReplace = { p, text -> FileDocument.save(p, text) },
        )
    }
    val onReplaceInFiles: suspend (ReplaceRequest) -> ReplaceOutcome = { req ->
        val result = fileOperationsInteractor.replaceInFiles(req)
        if (result.updates.isNotEmpty()) {
            mutatePane(PaneSide.PRIMARY) { pane ->
                val newBook = applyReplaceToBook(pane.book, result.updates)
                if (newBook === pane.book) pane else pane.copy(book = newBook)
            }
            mutatePane(PaneSide.SECONDARY) { pane ->
                val newBook = applyReplaceToBook(pane.book, result.updates)
                if (newBook === pane.book) pane else pane.copy(book = newBook)
            }
            for (side in listOf(PaneSide.PRIMARY, PaneSide.SECONDARY)) {
                val pane = paneOf(side)
                val active = pane.book.active ?: continue
                if (!result.updates.containsKey(active.path)) continue
                val caret = active.caret.coerceAtMost(active.text.length)
                setPane(side, pane.copy(editorValue = TextFieldValue(active.text, TextRange(caret))))
            }
        }
        ReplaceOutcome(filesChanged = result.filesChanged, replacements = result.replacements)
    }

    val lspEditorInterconnector = LspEditorInterconnector(
        focused = { focused() },
        paneOf = { side -> paneOf(side) },
        setPane = { side, value -> setPane(side, value) },
        mutatePane = { side, transform -> mutatePane(side, transform) },
        openInTabAt = { picked, offset -> openInTabAt(picked, offset) },
        controllerFor = { p -> currentLspRouter.controllerFor(p) },
        applyExternalChange = { uri, text -> currentLspRouter.applyExternalChange(uri, text) },
        getReferences = { referencesState },
        setReferences = { referencesState = it },
    )
    val jumpToProblem: (Path, Int, Int) -> Unit = { picked, line, character ->
        lspEditorInterconnector.jumpToProblem(picked, line, character)
    }
    val requestReferences: (Path, Int, Int, String) -> Unit = { p, line, char, symbol ->
        lspEditorInterconnector.requestReferences(p, line, char, symbol)
    }
    val applyRename: (RenameWorkspaceEdit) -> Unit = { edit ->
        lspEditorInterconnector.applyRename(edit)
    }
    val applyCodeAction: (CodeActionEntry) -> Unit = { action ->
        if (action.hasEdit) {
            println("[lsp] codeAction apply ▶ \"${action.title}\" — ${action.edit.changes.sumOf { it.edits.size }} edit(s)")
            applyRename(action.edit)
        }
        if (action.hasCommand) {
            val ctrl = codeActionUri?.let { currentLspRouter.controllerForUri(it) }
            val command = action.command
            if (ctrl != null && command != null) {
                println("[lsp] codeAction command ▶ \"${action.title}\" → $command")
                ctrl.executeCommand(command, action.commandArguments)
            }
        }
    }
    LaunchedEffect(lspRouter) {
        lspRouter.applyEditHandler = { edit ->
            java.awt.EventQueue.invokeLater { applyRename(edit) }
            true
        }
    }

    val openWorkspaceFolder: (Path) -> Unit = { picked ->
        PerfRegistry.instance?.begin(StartupPhases.WORKSPACE_OPEN)
        rootDir = picked
        expanded = setOf(picked) + page.editor.FileTree.singleChildChain(picked)
        PerfRegistry.instance?.end(StartupPhases.WORKSPACE_OPEN)
    }
    val fileMenuController = FileMenuController(
        openInTab = openInTab,
        focused = { focused() },
        mutateFocused = { transform -> mutateFocused(transform) },
        paneOf = { side -> paneOf(side) },
        mutatePane = { side, transform -> mutatePane(side, transform) },
        didSave = { path, text -> lspRouter.controllerFor(path)?.didSave(path, text) },
        openWorkspaceFolder = openWorkspaceFolder,
    )
    val openFile: (java.awt.Frame) -> Unit = { parent -> fileMenuController.openFile(parent) }
    val saveFile: (java.awt.Frame) -> Unit = { parent -> fileMenuController.saveFile(parent) }
    val saveAllDirty: () -> Int = { fileMenuController.saveAllDirty() }
    val openFolder: (java.awt.Frame) -> Unit = { parent -> fileMenuController.openFolder(parent) }
    val openFolderPath: (Path) -> Unit = { picked -> fileMenuController.openFolderPath(picked) }
    val newFile: (java.awt.Frame) -> Unit = { parent -> fileMenuController.newFile(parent) }
    val copyToClipboard: (String) -> Unit = { text ->
        runCatching {
            val selection = java.awt.datatransfer.StringSelection(text)
            java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
        }
    }
    val fileTreeContextController = FileTreeContextController(
        ui = layoutUiState,
        rootDir = { rootDir },
        copyToClipboard = copyToClipboard,
    )
    val onCreateFileIn: (Path) -> Unit = { parent -> fileTreeContextController.onCreateFileIn(parent) }
    val onCreateFolderIn: (Path) -> Unit = { parent -> fileTreeContextController.onCreateFolderIn(parent) }
    val onRevealInFiles: (Path) -> Unit = { path -> fileTreeContextController.onRevealInFiles(path) }
    val onCopyPath: (Path) -> Unit = { path -> fileTreeContextController.onCopyPath(path) }
    val onCopyRelativePath: (Path) -> Unit = { path -> fileTreeContextController.onCopyRelativePath(path) }
    val onRenameEntry: (Path) -> Unit = { path -> fileTreeContextController.onRenameEntry(path) }
    val onPasteInto: (Path) -> Unit = { destParent -> fileTreeContextController.onPasteInto(destParent) }
    val showDropResultToast: (String, DropResultToastTone, (() -> Unit)?) -> Unit = { msg, tone, undo ->
        dropResultToast = DropResultToastState(
            message = msg,
            visibleUntilMs = System.currentTimeMillis() + 5000L,
            tone = tone,
            undo = undo,
        )
    }
    val onDropPlanReceived: (TreeDragController.DropPlan) -> Unit = { plan ->
        val mode = when (plan.mode) {
            TreeDragController.Mode.Move -> FileTreeClipboard.Mode.Cut
            TreeDragController.Mode.Copy -> FileTreeClipboard.Mode.Copy
        }
        pasteDialog = PasteEntryDialogState(
            remaining = plan.sources,
            destParent = plan.target,
            mode = mode,
        )
    }
    val onExternalDropReceived: (List<Path>, Path) -> Unit = { sources, target ->
        if (sources.isNotEmpty() && java.nio.file.Files.isDirectory(target)) {
            pasteDialog = PasteEntryDialogState(
                remaining = sources,
                destParent = target,
                mode = FileTreeClipboard.Mode.Copy,
            )
        }
    }
    val onDeleteEntry: (Path) -> Unit = { path ->
        deleteDialog = DeleteEntryDialogState(listOf(path))
    }
    val onDeleteEntries: (Set<Path>) -> Unit = { paths ->
        val pruned = FileTreeActions.pruneRedundantDescendants(paths)
        if (pruned.isNotEmpty()) {
            deleteDialog = DeleteEntryDialogState(pruned)
        }
    }
    val remapPathSet: (Set<Path>, Path, Path) -> Set<Path> = { paths, old, new ->
        if (paths.isEmpty()) paths
        else paths.map { p ->
            when {
                p == old -> new
                p.startsWith(old) -> new.resolve(old.relativize(p))
                else -> p
            }
        }.toSet()
    }
    val remapTreeStateAfterRename: (Path, Path) -> Unit = { old, new ->
        expanded = remapPathSet(expanded, old, new)
        treeSelection = remapPathSet(treeSelection, old, new)
    }
    val remapTabsAfterRename: (Path, Path) -> Unit = { old, new ->
        listOf(PaneSide.PRIMARY, PaneSide.SECONDARY).forEach { side ->
            mutatePane(side) { pane ->
                val mapped = pane.book.tabs.map { tab ->
                    val newPath = when {
                        tab.path == old -> new
                        tab.path.startsWith(old) -> new.resolve(old.relativize(tab.path))
                        else -> null
                    }
                    if (newPath != null) tab.copy(path = newPath) else tab
                }
                if (mapped == pane.book.tabs) pane
                else pane.copy(book = pane.book.copy(tabs = mapped))
            }
        }
        val affectedOldPaths = mutableListOf<Path>()
        val affectedNewPaths = mutableListOf<Pair<Path, String>>()
        listOf(primaryPane, secondaryPane).forEach { pane ->
            pane.book.tabs.forEach { tab ->
                if (tab.path == new || tab.path.startsWith(new)) {
                    val origin = if (tab.path == new) old else old.resolve(new.relativize(tab.path))
                    affectedOldPaths.add(origin)
                    affectedNewPaths.add(tab.path to tab.text)
                }
            }
        }
        affectedOldPaths.distinct().forEach { currentLspRouter.controllerFor(it)?.didClose(it) }
        affectedNewPaths.distinctBy { it.first }.forEach { (p, text) ->
            currentLspRouter.languageIdFor(p)?.let { langId -> currentLspRouter.controllerFor(p)?.didOpen(p, langId, text) }
        }
    }
    val readFileTextWithTabs: (Path) -> String? = { p ->
        paneOf(PaneSide.PRIMARY).book.tabs.firstOrNull { it.path == p }?.text
            ?: paneOf(PaneSide.SECONDARY).book.tabs.firstOrNull { it.path == p }?.text
            ?: runCatching { java.nio.file.Files.readString(p) }.getOrNull()
    }
    val applyTextReplace: (Path, String) -> Unit = { path, newText ->
        var inTab = false
        for (side in listOf(PaneSide.PRIMARY, PaneSide.SECONDARY)) {
            val p = paneOf(side)
            val idx = p.book.tabs.indexOfFirst { it.path == path }
            if (idx < 0) continue
            inTab = true
            val tab = p.book.tabs[idx]
            if (tab.text == newText) continue
            val isActive = idx == p.book.activeIndex
            val updatedTabs = p.book.tabs.toMutableList().also { it[idx] = tab.copy(text = newText) }
            val newEditorValue = if (isActive) {
                val caret = p.editorValue.selection.start.coerceAtMost(newText.length)
                TextFieldValue(newText, TextRange(caret))
            } else p.editorValue
            setPane(side, p.copy(book = p.book.copy(tabs = updatedTabs), editorValue = newEditorValue))
        }
        if (!inTab) {
            runCatching { java.nio.file.Files.writeString(path, newText) }
        }
        runCatching { currentLspRouter.applyExternalChange(path.toUri().toString(), newText) }
    }
    fun postUndoRemapForOp(op: FileOpHistory.Op) {
        when (op) {
            is FileOpHistory.PasteCutOp -> op.moves.forEach { (origin, current) ->
                remapTabsAfterRename(current, origin)
                remapTreeStateAfterRename(current, origin)
            }
            is FileOpHistory.RenameOp -> {
                remapTabsAfterRename(op.to, op.from)
                remapTreeStateAfterRename(op.to, op.from)
            }
            is FileOpHistory.ReferenceRewriteOp -> op.rewrites.forEach { entry ->
                applyTextReplace(entry.path, entry.original)
            }
            is FileOpHistory.CompositeOp -> op.parts.asReversed().forEach { postUndoRemapForOp(it) }
            else -> Unit
        }
    }
    val onUndoFileOp: () -> Boolean = {
        val op = fileOpHistory.peek()
        if (op == null) false
        else when (val result = op.undo()) {
            is FileOpHistory.UndoResult.Ok -> {
                fileOpHistory.popForUndo()
                fileOpHistoryVersion++
                postUndoRemapForOp(op)
                treeRevision++
                true
            }
            is FileOpHistory.UndoResult.Err -> {
                println("[filetree] undo failed: ${result.message}")
                false
            }
        }
    }
    fun postRedoRemapForOp(op: FileOpHistory.Op) {
        when (op) {
            is FileOpHistory.PasteCutOp -> op.moves.forEach { (origin, current) ->
                remapTabsAfterRename(origin, current)
                remapTreeStateAfterRename(origin, current)
            }
            is FileOpHistory.RenameOp -> {
                remapTabsAfterRename(op.from, op.to)
                remapTreeStateAfterRename(op.from, op.to)
            }
            is FileOpHistory.ReferenceRewriteOp -> op.rewrites.forEach { entry ->
                applyTextReplace(entry.path, entry.rewritten)
            }
            is FileOpHistory.CompositeOp -> op.parts.forEach { postRedoRemapForOp(it) }
            else -> Unit
        }
    }
    val onRedoFileOp: () -> Boolean = {
        val op = fileOpHistory.peekRedo()
        if (op == null) false
        else when (val result = op.redo()) {
            is FileOpHistory.UndoResult.Ok -> {
                fileOpHistory.popForRedo()
                fileOpHistoryVersion++
                postRedoRemapForOp(op)
                treeRevision++
                true
            }
            is FileOpHistory.UndoResult.Err -> {
                println("[filetree] redo failed: ${result.message}")
                false
            }
        }
    }
    val applyFolderPackageSync: (Path, Path, Map<String, String>) -> List<FileOpHistory.RewriteEntry> = { _, newFolder, packageMap ->
        val entries = PackageSyncEngine.folderRewrites(newFolder, packageMap, rootDir, readFileTextWithTabs)
        entries.forEach { applyTextReplace(it.path, it.rewritten) }
        entries
    }
    val applySingleFileMoveSync: (Path, FolderPackageRename.SingleFileMovePlan) -> List<FileOpHistory.RewriteEntry> = { newPath, plan ->
        val entries = PackageSyncEngine.singleFileMoveRewrites(newPath, plan, rootDir, readFileTextWithTabs)
        entries.forEach { applyTextReplace(it.path, it.rewritten) }
        entries
    }
    val fileTreeActionExecutor = FileTreeActionExecutor(
        scope = largeCopyScope,
        getPasteDialog = { pasteDialog },
        setPasteDialog = { pasteDialog = it },
        getLargeCopyState = { largeCopyState },
        setLargeCopyState = { largeCopyState = it },
        rootDir = { rootDir },
        readFileText = readFileTextWithTabs,
        applyFolderPackageSync = applyFolderPackageSync,
        applySingleFileMoveSync = applySingleFileMoveSync,
        remapTabsAfterRename = { old, new -> remapTabsAfterRename(old, new) },
        remapTreeStateAfterRename = { old, new -> remapTreeStateAfterRename(old, new) },
        controllerFor = { p -> currentLspRouter.controllerFor(p) },
        withFileTreeWatcherClosed = { block -> withFileTreeWatcherClosed(block) },
        fileOpHistory = fileOpHistory,
        bumpHistoryVersion = { fileOpHistoryVersion++ },
        bumpTreeRevision = { treeRevision++ },
        showInfoToast = { msg, undo -> showDropResultToast(msg, DropResultToastTone.Info, undo) },
        onUndoFileOp = { onUndoFileOp() },
    )
    val toggleExpanded: (Path, Boolean) -> Unit = { p, recursive ->
        expanded = when {
            recursive -> expanded + setOf(p) + page.editor.FileTree.descendantDirs(p)
            p in expanded -> expanded - setOf(p)
            else -> expanded + setOf(p) + page.editor.FileTree.singleChildChain(p)
        }
    }
    val isUnsavedText: (OpenTab) -> Boolean = { tab ->
        tab.dirty && FileKinds.classify(tab.path).isEditableAsText
    }
    val saveTabAt: (PaneSide, Int) -> Unit = { side, idx ->
        val pane = paneOf(side)
        val tab = pane.book.tabs.getOrNull(idx)
        if (tab != null && FileKinds.classify(tab.path).isEditableAsText) {
            val liveText = if (idx == pane.book.activeIndex) pane.editorValue.text else tab.text
            try {
                FileDocument.save(tab.path, liveText)
                for (s in listOf(PaneSide.PRIMARY, PaneSide.SECONDARY)) {
                    mutatePane(s) { it.copy(book = it.book.markPathSaved(tab.path, liveText)) }
                }
            } catch (_: java.io.IOException) { }
        }
    }
    val tabController = EditorTabController(
        paneOf = { side -> paneOf(side) },
        mutatePane = { side, transform -> mutatePane(side, transform) },
        isOpenAnywhere = { p ->
            primaryPane.book.tabs.any { it.path == p } || secondaryPane.book.tabs.any { it.path == p }
        },
        forgetScroll = { p -> editorScrollByPath = EditorScrollMemory.clear(editorScrollByPath, p) },
        didClose = { p -> currentLspRouter.controllerFor(p)?.didClose(p) },
        isUnsavedText = { tab -> isUnsavedText(tab) },
        setPendingClose = { pendingClose = it },
        autoSaveOnClose = { pageSettings.autoSave.onClose },
        saveTabAt = { side, idx -> saveTabAt(side, idx) },
    )
    val closeTabsUnderPath: (Path) -> Unit = { path -> tabController.closeTabsUnderPath(path) }
    val closeTabAt: (PaneSide, Int) -> Unit = { side, idx -> tabController.closeTabAt(side, idx) }
    val requestCloseTab: (PaneSide, Int) -> Unit = { side, idx -> tabController.requestCloseTab(side, idx) }
    val closeManyOnPane: (PaneSide, List<Int>) -> Unit = { side, indices -> tabController.closeManyOnPane(side, indices) }
    val requestBatchClose: (PaneSide, List<Int>) -> Unit = { side, indices -> tabController.requestBatchClose(side, indices) }
    val togglePin: (PaneSide, Int) -> Unit = { side, idx ->
        mutatePane(side) { it.copy(book = it.book.togglePinned(idx)) }
    }
    val copyAbsolutePathOfTab: (PaneSide, Int) -> Unit = { side, idx ->
        paneOf(side).book.tabs.getOrNull(idx)?.let { copyToClipboard(it.path.toAbsolutePath().toString()) }
    }
    val copyRelativePathOfTab: (PaneSide, Int) -> Unit = { side, idx ->
        paneOf(side).book.tabs.getOrNull(idx)?.let {
            copyToClipboard(FileTreeActions.relativeTo(rootDir, it.path))
        }
    }
    val showInExplorerOfTab: (PaneSide, Int) -> Unit = { side, idx ->
        paneOf(side).book.tabs.getOrNull(idx)?.let { onRevealInFiles(it.path) }
    }
    val renameTabFile: (PaneSide, Int) -> Unit = { side, idx ->
        paneOf(side).book.tabs.getOrNull(idx)?.let { renameDialog = RenameEntryDialogState(it.path) }
    }
    val splitWithTab: (PaneSide, Int) -> Unit = { side, idx ->
        val tab = paneOf(side).book.tabs.getOrNull(idx)
        if (tab != null) {
            if (!splitEnabled) splitEnabled = true
            val target = if (side == PaneSide.PRIMARY) PaneSide.SECONDARY else PaneSide.PRIMARY
            mutatePane(target) {
                it.copy(book = it.book.appendTab(
                    OpenTab(path = tab.path, text = tab.text, savedText = tab.savedText, caret = tab.caret)
                ))
            }
            focusedPane = target
        }
    }
    val closeActiveTab: () -> Unit = {
        val side = focusedPane
        val idx = paneOf(side).book.activeIndex
        if (idx in paneOf(side).book.tabs.indices) requestCloseTab(side, idx)
    }
    val anyDirty: () -> Boolean = {
        primaryPane.book.tabs.any(isUnsavedText) || secondaryPane.book.tabs.any(isUnsavedText)
    }
    val requestExit: () -> Unit = {
        when {
            !anyDirty() -> exitApplication()
            pageSettings.autoSave.onClose -> {
                saveAllDirty()
                exitApplication()
            }
            else -> pendingClose = PendingClose.App
        }
    }
    val searchController = EditorSearchController(
        paneOf = { side -> paneOf(side) },
        mutatePane = { side, transform -> mutatePane(side, transform) },
        mutateFocused = { transform -> mutateFocused(transform) },
        focused = { focused() },
        undoTracker = { side -> undoTracker(side) },
        addSearchQuery = addSearchQuery,
        addReplaceText = addReplaceText,
    )
    val openSearch: () -> Unit = { searchController.openSearch() }
    val openReplace: () -> Unit = { searchController.openReplace() }
    val closeSearch: (PaneSide) -> Unit = { side -> searchController.closeSearch(side) }
    val onQueryChange: (PaneSide, String) -> Unit = { side, q -> searchController.onQueryChange(side, q) }
    val onToggleCase: (PaneSide) -> Unit = { side -> searchController.onToggleCase(side) }
    val onSearchNext: (PaneSide) -> Unit = { side -> searchController.onSearchNext(side) }
    val onSearchPrev: (PaneSide) -> Unit = { side -> searchController.onSearchPrev(side) }
    val onReplaceChange: (PaneSide, String) -> Unit = { side, value -> searchController.onReplaceChange(side, value) }
    val onReplace: (PaneSide) -> Unit = { side -> searchController.onReplace(side) }
    val onReplaceAll: (PaneSide) -> Unit = { side -> searchController.onReplaceAll(side) }

    val historyController = EditorHistoryController(
        focusedPane = { focusedPane },
        paneOf = { side -> paneOf(side) },
        mutatePane = { side, transform -> mutatePane(side, transform) },
        undoTracker = { side -> undoTracker(side) },
        applyExternalChange = { uri, text -> currentLspRouter.applyExternalChange(uri, text) },
    )
    val doUndo: () -> Unit = { historyController.doUndo() }
    val doRedo: () -> Unit = { historyController.doRedo() }


    val tabContextActionsFor: (PaneSide) -> TabContextActions = { side ->
        TabContextActions(
            onClose = { idx -> requestCloseTab(side, idx) },
            onCloseOthers = { idx ->
                val pane = paneOf(side)
                val toClose = pane.book.tabs.indices.filter { i ->
                    i != idx && !pane.book.tabs[i].isPinned
                }
                requestBatchClose(side, toClose)
            },
            onCloseToLeft = { idx ->
                val pane = paneOf(side)
                val toClose = (0 until idx).filter { i -> !pane.book.tabs[i].isPinned }
                requestBatchClose(side, toClose)
            },
            onCloseToRight = { idx ->
                val pane = paneOf(side)
                val toClose = ((idx + 1) until pane.book.tabs.size).filter { i -> !pane.book.tabs[i].isPinned }
                requestBatchClose(side, toClose)
            },
            onCloseAll = {
                val pane = paneOf(side)
                val toClose = pane.book.tabs.indices.filter { i -> !pane.book.tabs[i].isPinned }
                requestBatchClose(side, toClose)
            },
            onCloseUnmodified = {
                val pane = paneOf(side)
                val toClose = pane.book.tabs.indices.filter { i ->
                    !pane.book.tabs[i].dirty && !pane.book.tabs[i].isPinned
                }
                closeManyOnPane(side, toClose)
            },
            onCopyAbsolutePath = { idx -> copyAbsolutePathOfTab(side, idx) },
            onCopyRelativePath = { idx -> copyRelativePathOfTab(side, idx) },
            onShowInExplorer = { idx -> showInExplorerOfTab(side, idx) },
            onTogglePin = { idx -> togglePin(side, idx) },
            onMoveToOtherPane = if (editorWorkspace.splitEnabled) {
                { idx -> editorWorkspace.moveTabAcross(side, idx) }
            } else null,
            onSplit = { idx -> splitWithTab(side, idx) },
            onRename = { idx -> renameTabFile(side, idx) },
        )
    }

    val paletteController = CommandPaletteController(
        ui = layoutUiState,
        rootDir = { rootDir },
        focused = { focused() },
        controllerFor = { path -> currentLspRouter.controllerFor(path) },
        allDiagnosticsByUri = { currentLspRouter.allDiagnosticsByUri },
        jumpToProblem = jumpToProblem,
        applyRename = applyRename,
        onCodeActions = { list, uri, text, selected, open ->
            codeActionList = list
            codeActionUri = uri
            codeActionText = text
            codeActionSelected = selected
            codeActionOpen = open
        },
    )
    val openQuickOpen: () -> Unit = { paletteController.openQuickOpen() }
    val jumpProblemRelative: (Boolean) -> Unit = { forward -> paletteController.jumpProblemRelative(forward) }
    val openDocumentSymbol: () -> Unit = { paletteController.openDocumentSymbol() }
    val openWorkspaceSymbol: () -> Unit = { paletteController.openWorkspaceSymbol() }
    val triggerFormat: () -> Unit = { paletteController.triggerFormat() }
    val triggerCodeAction: () -> Unit = { paletteController.triggerCodeAction() }
    val openFindInFiles: () -> Unit = {
        val root = rootDir
        if (root != null) {
            findInFilesIndex = ProjectFileIndex.walk(root)
            findInFiles = true
        }
    }

    val cyclePalette: () -> Unit = {
        val all = GlassPalette.values()
        palette = all[(all.indexOf(palette) + 1) % all.size]
        paletteToastUntil = System.currentTimeMillis() + 1600L
        val root = rootDir
        if (root != null) {
            workspaceFile = workspaceFile.copy(palette = palette.name)
            runCatching { WorkspaceStore.save(root, workspaceFile) }
        } else {
            AppSettings.savePalette(palette)
        }
    }
    val frameRef = remember { mutableStateOf<java.awt.Frame?>(null) }
    val handleShortcut: (KeyEvent) -> Boolean = handler@{ event ->
        if (event.type != KeyEventType.KeyDown) return@handler false
        val frame = frameRef.value
        val focusedSearch = focused().search
        when (ShortcutResolver.resolve(
            key = event.key,
            ctrl = event.isCtrlPressed,
            alt = event.isAltPressed,
            shift = event.isShiftPressed,
            hasSearch = focusedSearch != null,
        )) {
            ShortcutAction.CYCLE_PALETTE -> { cyclePalette(); true }
            ShortcutAction.OPEN_FOLDER -> { if (frame != null) openFolder(frame); true }
            ShortcutAction.OPEN_FILE -> { if (frame != null) openFile(frame); true }
            ShortcutAction.OPEN_SETTINGS -> { settingsDialogOpen = true; true }
            ShortcutAction.SAVE -> { if (frame != null) saveFile(frame); true }
            ShortcutAction.CLOSE_TAB -> { closeActiveTab(); true }
            ShortcutAction.TOGGLE_PROBLEMS -> { problemsOpen = !problemsOpen; true }
            ShortcutAction.TOGGLE_TODO -> { todoOpen = !todoOpen; true }
            ShortcutAction.TOGGLE_FIND_IN_FILES -> {
                if (findInFiles) findInFiles = false else openFindInFiles()
                true
            }
            ShortcutAction.OPEN_SEARCH -> { openSearch(); true }
            ShortcutAction.OPEN_REPLACE -> { openReplace(); true }
            ShortcutAction.OPEN_QUICK_OPEN -> { openQuickOpen(); true }
            ShortcutAction.OPEN_WORKSPACE_SYMBOL -> { openWorkspaceSymbol(); true }
            ShortcutAction.OPEN_DOCUMENT_SYMBOL -> { openDocumentSymbol(); true }
            ShortcutAction.TOGGLE_SPLIT_ORIENTATION -> {
                splitOrientation = if (splitOrientation == SplitOrientation.HORIZONTAL)
                    SplitOrientation.VERTICAL else SplitOrientation.HORIZONTAL
                true
            }
            ShortcutAction.TOGGLE_SPLIT -> { splitEnabled = !splitEnabled; true }
            ShortcutAction.UNDO -> {
                val undoOp = fileOpHistory.peek()
                if (fileTreeFocused && undoOp != null) {
                    fileOpConfirm = FileOpConfirmState(isRedo = false, op = undoOp)
                } else {
                    doUndo()
                }
                true
            }
            ShortcutAction.REDO -> {
                val redoOp = fileOpHistory.peekRedo()
                if (fileTreeFocused && redoOp != null) {
                    fileOpConfirm = FileOpConfirmState(isRedo = true, op = redoOp)
                } else {
                    doRedo()
                }
                true
            }
            ShortcutAction.FORMAT -> { triggerFormat(); true }
            ShortcutAction.CODE_ACTION -> { triggerCodeAction(); true }
            ShortcutAction.PREV_TAB -> { editorWorkspace.activateAdjacentTab(-1); true }
            ShortcutAction.NEXT_TAB -> { editorWorkspace.activateAdjacentTab(1); true }
            ShortcutAction.JUMP_PROBLEM_NEXT -> { jumpProblemRelative(true); true }
            ShortcutAction.JUMP_PROBLEM_PREV -> { jumpProblemRelative(false); true }
            ShortcutAction.REFRESH_TREE -> { treeRevision++; true }
            ShortcutAction.CLOSE_SEARCH -> { closeSearch(focusedPane); true }
            ShortcutAction.NONE -> false
        }
    }
    Window(
        onCloseRequest = requestExit,
        state = windowState,
        title = windowTitle(focused().book.active?.path),
        onPreviewKeyEvent = handleShortcut,
        onKeyEvent = handleShortcut,
    ) {
        LaunchedEffect(Unit) {
            frameRef.value = window
            val perf = PerfRegistry.instance
            perf?.end(StartupPhases.COMPOSE_INIT)
            perf?.begin(StartupPhases.WINDOW_SHOWN)
            androidx.compose.runtime.withFrameNanos { }
            perf?.end(StartupPhases.WINDOW_SHOWN)
            perf?.begin(StartupPhases.FIRST_FRAME)
            androidx.compose.runtime.withFrameNanos { }
            perf?.end(StartupPhases.FIRST_FRAME)
            println(perf?.summary())
        }
        LaunchedEffect(palette) {
            val c = glassTokensFor(palette).color.background
            val awt = java.awt.Color(
                (c.red * 255).toInt().coerceIn(0, 255),
                (c.green * 255).toInt().coerceIn(0, 255),
                (c.blue * 255).toInt().coerceIn(0, 255),
            )
            window.background = awt
            window.contentPane.background = awt
        }
        val openWsRef = rememberUpdatedState(openWorkspaceSymbol)
        val openDocRef = rememberUpdatedState(openDocumentSymbol)
        val triggerFormatRef = rememberUpdatedState(triggerFormat)
        val triggerCodeActionRef = rememberUpdatedState(triggerCodeAction)
        val codeActionOpenRef = rememberUpdatedState(codeActionOpen)
        val codeActionListRef = rememberUpdatedState(codeActionList)
        val codeActionSelectedRef = rememberUpdatedState(codeActionSelected)
        val toggleTerminal: () -> Unit = {
            terminalOpen = !terminalOpen
            if (terminalOpen && terminalManager.tabs.isEmpty()) terminalManager.newTab()
        }
        val toggleTerminalRef = rememberUpdatedState(toggleTerminal)
        val startActiveRun: () -> Unit = run@{
            if (runController.isRunning) return@run
            val cfg = if (runState.isCurrentFileActive) {
                val file = focused().book.active?.path ?: return@run
                LanguageRunDefaults.buildConfig(file, rootDir) ?: return@run
            } else {
                runState.active ?: return@run
            }
            if (autoSaveOptions.beforeRun) saveAllDirty()
            if (pageSettings.run.clearOutputOnRun) runCatching { outputState.clear() }
            if (pageSettings.run.openTerminalOnRun) {
                terminalOpen = true
                if (terminalManager.tabs.isEmpty()) terminalManager.newTab()
            }
            outputOpen = true
            runController.start(cfg)
        }
        val stopActiveRun: () -> Unit = {
            runController.stop()
        }
        val openRunDialog: () -> Unit = { runDialogOpen = true }
        val startActiveRunRef = rememberUpdatedState(startActiveRun)
        val stopActiveRunRef = rememberUpdatedState(stopActiveRun)
        val openRunDialogRef = rememberUpdatedState(openRunDialog)
        val saveAllDirtyRef = rememberUpdatedState(saveAllDirty)
        val autoSaveOptionsRef = rememberUpdatedState(autoSaveOptions)
        val openSettings: () -> Unit = { settingsDialogOpen = true }
        val openSettingsRef = rememberUpdatedState(openSettings)
        DisposableEffect(window) {
            val frame = window
            val focusListener = object : java.awt.event.WindowFocusListener {
                override fun windowGainedFocus(e: java.awt.event.WindowEvent?) {}
                override fun windowLostFocus(e: java.awt.event.WindowEvent?) {
                    if (autoSaveOptionsRef.value.onFocusLost) saveAllDirtyRef.value()
                }
            }
            frame.addWindowFocusListener(focusListener)
            onDispose { frame.removeWindowFocusListener(focusListener) }
        }
        LaunchedEffect(
            primaryPane.editorValue.text,
            secondaryPane.editorValue.text,
            autoSaveOptions.idleSeconds,
        ) {
            val sec = autoSaveOptions.idleSeconds
            if (sec <= 0) return@LaunchedEffect
            kotlinx.coroutines.delay(sec * 1000L)
            saveAllDirty()
        }
        DisposableEffect(window) {
            val frame = window
            val dispatcher = java.awt.KeyEventDispatcher { e ->
                if (e.id != java.awt.event.KeyEvent.KEY_PRESSED) return@KeyEventDispatcher false
                if (!frame.isFocused) return@KeyEventDispatcher false
                val ctrl = (e.modifiersEx and java.awt.event.InputEvent.CTRL_DOWN_MASK) != 0
                val alt = (e.modifiersEx and java.awt.event.InputEvent.ALT_DOWN_MASK) != 0
                val shift = (e.modifiersEx and java.awt.event.InputEvent.SHIFT_DOWN_MASK) != 0
                if (codeActionOpenRef.value && !ctrl && !alt && !shift) {
                    val list = codeActionListRef.value
                    val sel = codeActionSelectedRef.value
                    when (e.keyCode) {
                        java.awt.event.KeyEvent.VK_ESCAPE -> {
                            codeActionOpen = false
                            frameRef.value?.requestFocus()
                            editorFocusVersion += 1
                            return@KeyEventDispatcher true
                        }
                        java.awt.event.KeyEvent.VK_UP -> {
                            if (list.isNotEmpty()) {
                                codeActionSelected = ((sel - 1) + list.size) % list.size
                            }
                            return@KeyEventDispatcher true
                        }
                        java.awt.event.KeyEvent.VK_DOWN -> {
                            if (list.isNotEmpty()) {
                                codeActionSelected = (sel + 1) % list.size
                            }
                            return@KeyEventDispatcher true
                        }
                        java.awt.event.KeyEvent.VK_ENTER -> {
                            val pick = list.getOrNull(sel)
                            codeActionOpen = false
                            if (pick != null && pick.isExecutable) {
                                applyCodeAction(pick)
                            }
                            frameRef.value?.requestFocus()
                            editorFocusVersion += 1
                            return@KeyEventDispatcher true
                        }
                    }
                }
                when {
                    ctrl && alt && !shift && e.keyCode == java.awt.event.KeyEvent.VK_S -> {
                        openSettingsRef.value()
                        true
                    }
                    ctrl && !alt && shift && e.keyCode == java.awt.event.KeyEvent.VK_T -> {
                        toggleTerminalRef.value()
                        true
                    }
                    ctrl && !alt && !shift && e.keyCode == java.awt.event.KeyEvent.VK_BACK_QUOTE -> {
                        toggleTerminalRef.value()
                        true
                    }
                    ctrl && !alt && !shift && e.keyCode == java.awt.event.KeyEvent.VK_T -> {
                        openWsRef.value()
                        true
                    }
                    ctrl && e.keyCode == java.awt.event.KeyEvent.VK_F12 -> {
                        openDocRef.value()
                        true
                    }
                    !ctrl && alt && shift && e.keyCode == java.awt.event.KeyEvent.VK_F -> {
                        triggerFormatRef.value()
                        true
                    }
                    !ctrl && alt && !shift && e.keyCode == java.awt.event.KeyEvent.VK_ENTER -> {
                        triggerCodeActionRef.value()
                        true
                    }
                    !ctrl && !alt && shift && e.keyCode == java.awt.event.KeyEvent.VK_F10 -> {
                        startActiveRunRef.value()
                        true
                    }
                    ctrl && !alt && !shift && e.keyCode == java.awt.event.KeyEvent.VK_F2 -> {
                        stopActiveRunRef.value()
                        true
                    }
                    ctrl && alt && !shift && e.keyCode == java.awt.event.KeyEvent.VK_R -> {
                        openRunDialogRef.value()
                        true
                    }
                    else -> false
                }
            }
            val fm = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
            fm.addKeyEventDispatcher(dispatcher)
            onDispose { fm.removeKeyEventDispatcher(dispatcher) }
        }
        val showWelcome = rootDir == null &&
            primaryPane.book.tabs.isEmpty() &&
            secondaryPane.book.tabs.isEmpty()
        GlassTheme(palette = palette) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                if (showWelcome) {
                    WelcomeScreen(
                        onOpenFolder = { frameRef.value?.let { openFolder(it) } },
                        onNewFile = { frameRef.value?.let { newFile(it) } },
                        onDropPaths = { dropped ->
                            val folder = dropped.firstOrNull { java.nio.file.Files.isDirectory(it) }
                            if (folder != null) {
                                openFolderPath(folder)
                            } else {
                                val file = dropped.firstOrNull { java.nio.file.Files.isRegularFile(it) }
                                if (file != null) openInTab(file)
                            }
                        },
                    )
                } else IdeMainLayout(
                    workspace = workspaceState,
                    editor = editorWorkspace,
                    ui = layoutUiState,
                    lspRouter = currentLspRouter,
                    onCloseTab = { side, index -> requestCloseTab(side, index) },
                    onToggle = toggleExpanded,
                    onOpenFile = openInTab,
                    onCreateFileIn = onCreateFileIn,
                    onCreateFolderIn = onCreateFolderIn,
                    onRenameEntry = onRenameEntry,
                    onDeleteEntry = onDeleteEntry,
                    onDeleteEntries = onDeleteEntries,
                    onRevealInFiles = onRevealInFiles,
                    onCopyPath = onCopyPath,
                    onCopyRelativePath = onCopyRelativePath,
                    onPasteInto = onPasteInto,
                    onDropPlan = onDropPlanReceived,
                    onExternalDrop = onExternalDropReceived,
                    onDropRejected = { msg ->
                        showDropResultToast(msg, DropResultToastTone.Warning, null)
                    },
                    onUndoFileOp = onUndoFileOp,
                    canUndoFileOp = run {
                        fileOpHistoryVersion
                        fileOpHistory.peek() != null
                    },
                    onTreeFocusChanged = { fileTreeFocused = it },
                    pendingTreeFocusTick = pendingTreeFocusTick,
                    onQueryChange = onQueryChange,
                    onReplaceChange = onReplaceChange,
                    onToggleCase = onToggleCase,
                    onSearchNext = onSearchNext,
                    onSearchPrev = onSearchPrev,
                    onReplace = onReplace,
                    onReplaceAll = onReplaceAll,
                    onSearchClose = closeSearch,
                    onWindowShortcut = handleShortcut,
                    onJumpToProblem = jumpToProblem,
                    onApplyRename = applyRename,
                    todoItems = todoItems,
                    terminalManager = terminalManager,
                    onTerminalToggle = toggleTerminal,
                    runState = runState,
                    onSelectRunConfig = { id -> runState = runState.select(id) },
                    onStartRun = startActiveRun,
                    onStopRun = stopActiveRun,
                    onOpenRunDialog = openRunDialog,
                    runIsRunning = outputState.running,
                    outputState = outputState,
                    onOutputClear = { outputState.clear() },
                    referencesState = referencesState,
                    onRequestReferences = requestReferences,
                    onReferencesClose = { referencesState = null },
                    linePreviewFor = { uri, line -> currentLspRouter.controllerForUri(uri)?.linePreviewFor(uri, line) },
                    foldedLinesFor = foldedLinesFor,
                    onFoldChange = onFoldChange,
                    editorFocusVersion = editorFocusVersion,
                    codeActionPreviewVisible = codeActionOpen,
                    codeActionPreviewActions = codeActionList,
                    codeActionPreviewSelected = codeActionSelected,
                    onCodeActionSelectedChange = {
                        codeActionSelected = it.coerceIn(0, codeActionList.lastIndex.coerceAtLeast(0))
                    },
                    codeActionPreviewUri = codeActionUri,
                    codeActionPreviewText = codeActionText,
                    onCodeActionApply = { action ->
                        codeActionOpen = false
                        if (action.isExecutable) {
                            applyCodeAction(action)
                        }
                        frameRef.value?.requestFocus()
                        editorFocusVersion += 1
                    },
                    onCodeActionDismiss = {
                        codeActionOpen = false
                        frameRef.value?.requestFocus()
                        editorFocusVersion += 1
                    },
                    editorScrollFor = { p -> editorScrollByPath[p] },
                    onEditorScrollChange = { p, snap ->
                        editorScrollByPath = EditorScrollMemory.put(editorScrollByPath, p, snap)
                    },
                    tabContextActionsFor = { side -> tabContextActionsFor(side) },
                    settingsPanelOpen = settingsDialogOpen,
                    pageSettings = pageSettings,
                    onSettingsApply = { updated ->
                        pageSettings = updated
                        AppSettings.saveAutoSave(updated.autoSave)
                        AppSettings.saveEditor(updated.editor)
                        AppSettings.saveLsp(updated.lsp)
                        AppSettings.saveAutoInput(updated.autoInput)
                        AppSettings.saveUi(updated.ui)
                        AppSettings.saveRun(updated.run)
                        palette = updated.ui.palette
                    },
                    onSettingsPanelClose = { settingsDialogOpen = false },
                )
                if (findInFiles) {
                    FindInFilesDialog(
                        files = findInFilesIndex,
                        onPickAt = { path, offset ->
                            findInFiles = false
                            openInTabAt(path, offset)
                        },
                        onReplace = onReplaceInFiles,
                        onDismiss = { findInFiles = false },
                    )
                }
                PaletteToast(palette = palette, visibleUntilMs = paletteToastUntil)
                val activeDropToast = dropResultToast
                if (activeDropToast != null) {
                    DropResultToast(
                        state = activeDropToast,
                        onDismiss = { dropResultToast = null },
                    )
                }
                }
            }
        }
    }

    NavigationPickerDialogs(
        ui = layoutUiState,
        openInTab = openInTab,
        jumpToProblem = jumpToProblem,
        workspaceSymbolQuery = { q ->
            val wsPath = focused().book.active?.path
            val wsCtrl = wsPath?.let { currentLspRouter.controllerFor(it) }
            if (wsCtrl != null) runCatching { wsCtrl.workspaceSymbolsLocated(q).await() }.getOrDefault(emptyList())
            else emptyList()
        },
        requestFrameFocus = { frameRef.value?.requestFocus() },
        onEditorFocusBump = { editorFocusVersion += 1 },
    )

    if (runDialogOpen) {
        RunConfigDialog(
            state = runState,
            workspaceRoot = rootDir,
            onSave = { saved ->
                runState = saved
                runDialogOpen = false
            },
            onDismiss = { runDialogOpen = false },
        )
    }


    FileTreeCreateDialog(
        workspace = workspaceState,
        ui = layoutUiState,
        fileOpHistory = fileOpHistory,
        openInTab = openInTab,
        onFileOpHistoryChanged = { fileOpHistoryVersion++ },
    )

    FileTreeRenameDeleteDialogs(
        workspace = workspaceState,
        ui = layoutUiState,
        lspRouter = lspRouter,
        fileOpHistory = fileOpHistory,
        jumpToProblem = jumpToProblem,
        applyRename = applyRename,
        remapTabsAfterRename = remapTabsAfterRename,
        remapTreeStateAfterRename = remapTreeStateAfterRename,
        applyFolderPackageSync = applyFolderPackageSync,
        withFileTreeWatcherClosed = withFileTreeWatcherClosed,
        readFileText = readFileTextWithTabs,
        closeTabsUnderPath = closeTabsUnderPath,
        onFileOpHistoryChanged = { fileOpHistoryVersion++ },
    )

    FileTreePasteDialog(
        workspace = workspaceState,
        ui = layoutUiState,
        fileTreeActionExecutor = fileTreeActionExecutor,
    )

    val activeLargeCopy = largeCopyState
    if (activeLargeCopy != null) {
        LargeCopyDialog(
            sourceName = activeLargeCopy.sourceName,
            destName = activeLargeCopy.destName,
            totalBytes = activeLargeCopy.totalBytes,
            fileCount = activeLargeCopy.fileCount,
            bytesCopied = activeLargeCopy.bytesCopied,
            filesCopied = activeLargeCopy.filesCopied,
            onCancel = { activeLargeCopy.cancelToken.set(true) },
        )
    }

    val activeFileOpConfirm = fileOpConfirm
    if (activeFileOpConfirm != null) {
        val verb = if (activeFileOpConfirm.isRedo) "Redo" else "Undo"
        ConfirmDialog(
            title = "$verb file operation",
            message = "$verb '${activeFileOpConfirm.op.describe()}'?",
            confirmLabel = verb,
            danger = false,
            onConfirm = {
                fileOpConfirm = null
                if (activeFileOpConfirm.isRedo) onRedoFileOp() else onUndoFileOp()
            },
            onDismiss = { fileOpConfirm = null },
        )
    }

    PendingCloseDialog(
        editorWorkspace = editorWorkspace,
        pendingClose = pendingClose,
        setPendingClose = { pendingClose = it },
        isUnsavedText = isUnsavedText,
        closeTabAt = closeTabAt,
        didClose = { p -> currentLspRouter.controllerFor(p)?.didClose(p) },
        onExitApplication = { exitApplication() },
    )
}

internal enum class CreateEntryKind { FILE, FOLDER }

internal data class CreateEntryDialogState(
    val parent: Path,
    val kind: CreateEntryKind,
    val error: String? = null,
)

internal data class RenameEntryDialogState(
    val path: Path,
    val error: String? = null,
)

internal data class DeleteEntryDialogState(
    val paths: List<Path>,
) {
    val primary: Path get() = paths.first()
    val isMulti: Boolean get() = paths.size > 1
}

private data class FileOpConfirmState(
    val isRedo: Boolean,
    val op: FileOpHistory.Op,
)
