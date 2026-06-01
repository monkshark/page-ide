package page.app

import page.runtime.*
import page.workspace.*
import page.workspace.sync.PackageSyncEngine
import page.app.input.ShortcutAction
import page.app.input.ShortcutResolver
import page.app.filetree.FileTreeActionExecutor
import page.app.filetree.LargeCopyDialogState
import page.app.domain.FileOperationsInteractor
import page.app.filetree.PasteEntryDialogState
import page.app.lsp.LspEditorInterconnector
import page.app.state.DebouncedSaver
import page.app.state.EditorWorkspaceState
import page.app.state.LayoutUiState
import page.app.state.WorkspaceState
import page.app.ui.IdeMainLayout
import page.app.ui.editor.EditorTabController
import page.app.utils.applyReplaceToBook
import page.app.utils.isKotlinSource
import page.app.utils.offsetToLineChar
import page.app.utils.windowTitle
import page.lsp.GenericLanguageBackend
import page.lsp.LanguageRegistry
import page.lsp.LspBackends

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
import page.lsp.DocumentSymbolEntry
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
    var quickOpen by remember { mutableStateOf(false) }
    var quickOpenIndex by remember { mutableStateOf<List<IndexedFile>>(emptyList()) }
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
    var documentSymbolOpen by remember { mutableStateOf(false) }
    var documentSymbolList by remember { mutableStateOf<List<DocumentSymbolEntry>>(emptyList()) }
    var documentSymbolUri by remember { mutableStateOf("") }
    var workspaceSymbolOpen by remember { mutableStateOf(false) }
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

    val openFile: (java.awt.Frame) -> Unit = { parent ->
        FileDialogs.open(parent)?.let { picked -> openInTab(picked) }
    }
    val saveFile: (java.awt.Frame) -> Unit = { parent ->
        val pane = focused()
        val active = pane.book.active
        if (active != null) {
            if (FileKinds.classify(active.path).isEditableAsText) {
                FileDocument.save(active.path, pane.editorValue.text)
                lspRouter.controllerFor(active.path)?.didSave(active.path, pane.editorValue.text)
                mutateFocused {
                    it.copy(
                        book = it.book
                            .updateActive(it.editorValue.text, it.editorValue.selection.start)
                            .markActiveSaved(),
                    )
                }
            }
        } else {
            val target = FileDialogs.saveAs(parent)
            if (target != null) {
                FileDocument.save(target, pane.editorValue.text)
                mutateFocused { it.copy(book = it.book.openOrFocus(target, pane.editorValue.text)) }
            }
        }
    }
    val saveAllDirty: () -> Int = {
        val pendingByPath = LinkedHashMap<Path, String>()
        for (side in listOf(PaneSide.PRIMARY, PaneSide.SECONDARY)) {
            val pane = paneOf(side)
            val activeIdx = pane.book.activeIndex
            pane.book.tabs.forEachIndexed { idx, tab ->
                if (!FileKinds.classify(tab.path).isEditableAsText) return@forEachIndexed
                val liveText = if (idx == activeIdx) pane.editorValue.text else tab.text
                if (liveText != tab.savedText) {
                    pendingByPath[tab.path] = liveText
                }
            }
        }
        var saved = 0
        for ((path, text) in pendingByPath) {
            try {
                FileDocument.save(path, text)
                lspRouter.controllerFor(path)?.didSave(path, text)
                saved += 1
            } catch (_: java.io.IOException) { }
        }
        if (saved > 0) {
            mutatePane(PaneSide.PRIMARY) { state ->
                var book = state.book
                for ((path, text) in pendingByPath) book = book.markPathSaved(path, text)
                state.copy(book = book)
            }
            mutatePane(PaneSide.SECONDARY) { state ->
                var book = state.book
                for ((path, text) in pendingByPath) book = book.markPathSaved(path, text)
                state.copy(book = book)
            }
        }
        saved
    }
    val openFolder: (java.awt.Frame) -> Unit = { parent ->
        FileDialogs.openDirectory(parent)?.let { picked ->
            PerfRegistry.instance?.begin(StartupPhases.WORKSPACE_OPEN)
            rootDir = picked
            expanded = setOf(picked) + page.editor.FileTree.singleChildChain(picked)
            PerfRegistry.instance?.end(StartupPhases.WORKSPACE_OPEN)
        }
    }
    val openFolderPath: (Path) -> Unit = { picked ->
        PerfRegistry.instance?.begin(StartupPhases.WORKSPACE_OPEN)
        rootDir = picked
        expanded = setOf(picked) + page.editor.FileTree.singleChildChain(picked)
        PerfRegistry.instance?.end(StartupPhases.WORKSPACE_OPEN)
    }
    val newFile: (java.awt.Frame) -> Unit = { parent ->
        val target = FileDialogs.saveAs(parent)
        if (target != null) {
            FileDocument.save(target, "")
            openInTab(target)
        }
    }
    val copyToClipboard: (String) -> Unit = { text ->
        runCatching {
            val selection = java.awt.datatransfer.StringSelection(text)
            java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
        }
    }
    val onCreateFileIn: (Path) -> Unit = { parent ->
        createDialog = CreateEntryDialogState(parent, CreateEntryKind.FILE)
    }
    val onCreateFolderIn: (Path) -> Unit = { parent ->
        createDialog = CreateEntryDialogState(parent, CreateEntryKind.FOLDER)
    }
    val onRevealInFiles: (Path) -> Unit = { path ->
        val target = if (java.nio.file.Files.isDirectory(path)) path else path.parent
        if (target != null) {
            runCatching { java.awt.Desktop.getDesktop().open(target.toFile()) }
        }
    }
    val onCopyPath: (Path) -> Unit = { path ->
        copyToClipboard(path.toAbsolutePath().toString())
    }
    val onCopyRelativePath: (Path) -> Unit = { path ->
        copyToClipboard(FileTreeActions.relativeTo(rootDir, path))
    }
    val onRenameEntry: (Path) -> Unit = { path ->
        renameDialog = RenameEntryDialogState(path)
    }
    val onPasteInto: (Path) -> Unit = { destParent ->
        val content = FileTreeClipboard.read()
        if (content != null && content.paths.isNotEmpty()) {
            val target = if (java.nio.file.Files.isDirectory(destParent)) destParent else destParent.parent
            if (target != null) {
                pasteDialog = PasteEntryDialogState(
                    remaining = content.paths,
                    destParent = target,
                    mode = content.mode,
                )
            }
        }
    }
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
    val moveCaretToActiveMatch: (PaneSide, SearchState) -> Unit = { side, s ->
        val range = s.active
        if (range != null) {
            mutatePane(side) {
                val text = it.editorValue.text
                val start = range.first.coerceIn(0, text.length)
                val end = (range.last + 1).coerceIn(start, text.length)
                it.copy(editorValue = it.editorValue.copy(selection = TextRange(start, end)))
            }
        }
    }
    val openSearch: () -> Unit = {
        val pane = focused()
        val active = pane.book.active
        if (active != null && FileKinds.classify(active.path).isEditableAsText) {
            mutateFocused {
                val current = it.search
                it.copy(
                    search = current?.withReplaceVisible(false)
                        ?: SearchState().withQuery(it.editorValue.text, ""),
                )
            }
        }
    }
    val openReplace: () -> Unit = {
        val pane = focused()
        val active = pane.book.active
        if (active != null && FileKinds.classify(active.path).isEditableAsText) {
            mutateFocused {
                val current = it.search
                it.copy(
                    search = current?.withReplaceVisible(true)
                        ?: SearchState()
                            .withQuery(it.editorValue.text, "")
                            .withReplaceVisible(true),
                )
            }
        }
    }
    val closeSearch: (PaneSide) -> Unit = { side ->
        mutatePane(side) { it.copy(search = null) }
    }
    val onQueryChange: (PaneSide, String) -> Unit = { side, q ->
        val pane = paneOf(side)
        val updated = (pane.search ?: SearchState()).withQuery(pane.editorValue.text, q)
        mutatePane(side) { it.copy(search = updated) }
        moveCaretToActiveMatch(side, updated)
    }
    val onToggleCase: (PaneSide) -> Unit = { side ->
        val pane = paneOf(side)
        val s = pane.search
        if (s != null) {
            val updated = s.withCaseSensitive(pane.editorValue.text, !s.caseSensitive)
            mutatePane(side) { it.copy(search = updated) }
            moveCaretToActiveMatch(side, updated)
        }
    }
    val onSearchNext: (PaneSide) -> Unit = { side ->
        val s = paneOf(side).search
        if (s != null) {
            val updated = s.next()
            mutatePane(side) { it.copy(search = updated) }
            moveCaretToActiveMatch(side, updated)
            addSearchQuery(updated.query)
        }
    }
    val onSearchPrev: (PaneSide) -> Unit = { side ->
        val s = paneOf(side).search
        if (s != null) {
            val updated = s.prev()
            mutatePane(side) { it.copy(search = updated) }
            moveCaretToActiveMatch(side, updated)
        }
    }
    val onReplaceChange: (PaneSide, String) -> Unit = { side, value ->
        mutatePane(side) { it.copy(search = it.search?.withReplace(value)) }
    }
    val onReplace: (PaneSide) -> Unit = { side ->
        val pane = paneOf(side)
        val s = pane.search
        val range = s?.active
        if (s != null && range != null) {
            val text = pane.editorValue.text
            val caret = pane.editorValue.selection.start
            val r = Replace.applyCurrent(text, range, s.replace)
            val retargeted = s.retarget(r.text)
            val nextIdx = retargeted.matches.indexOfFirst { it.first >= r.caret }
            val updatedSearch = retargeted.copy(
                activeMatchIndex = if (nextIdx >= 0) nextIdx
                else if (retargeted.matches.isNotEmpty()) 0 else -1,
            )
            undoTracker(side).markBreak()
            mutatePane(side) {
                it.copy(
                    book = it.book
                        .pushHistoryOnActive(EditSnapshot(text, caret))
                        .updateActive(r.text, r.caret),
                    editorValue = TextFieldValue(r.text, TextRange(r.caret)),
                    search = updatedSearch,
                )
            }
            moveCaretToActiveMatch(side, updatedSearch)
            addSearchQuery(s.query)
            addReplaceText(s.replace)
        }
    }
    val onReplaceAll: (PaneSide) -> Unit = { side ->
        val pane = paneOf(side)
        val s = pane.search
        if (s != null && s.matches.isNotEmpty()) {
            val text = pane.editorValue.text
            val caret = pane.editorValue.selection.start
            val r = Replace.applyAll(text, s.matches, s.replace)
            undoTracker(side).markBreak()
            mutatePane(side) {
                it.copy(
                    book = it.book
                        .pushHistoryOnActive(EditSnapshot(text, caret))
                        .updateActive(r.text, r.caret),
                    editorValue = TextFieldValue(r.text, TextRange(r.caret)),
                    search = s.retarget(r.text),
                )
            }
            addSearchQuery(s.query)
            addReplaceText(s.replace)
        }
    }

    val doUndo: () -> Unit = {
        val side = focusedPane
        val pane = paneOf(side)
        val current = EditSnapshot(pane.editorValue.text, pane.editorValue.selection.start)
        val result = pane.book.undoOnActive(current)
        if (result != null) {
            val (newBook, restored) = result
            val caret = restored.caret.coerceIn(0, restored.text.length)
            undoTracker(side).markBreak()
            val groupId = restored.groupId
            val activeBook = if (groupId != null) newBook.undoGroupOnNonActive(groupId) else newBook
            mutatePane(side) {
                it.copy(
                    book = activeBook,
                    editorValue = TextFieldValue(restored.text, TextRange(caret)),
                    search = it.search?.retarget(restored.text),
                )
            }
            if (groupId != null) {
                val otherSide = if (side == PaneSide.PRIMARY) PaneSide.SECONDARY else PaneSide.PRIMARY
                mutatePane(otherSide) { it.copy(book = it.book.undoGroupOnNonActive(groupId)) }
                for (tab in activeBook.tabs) {
                    if (tab.path != pane.book.tabs.getOrNull(pane.book.activeIndex)?.path) {
                        runCatching { currentLspRouter.applyExternalChange(tab.path.toUri().toString(), tab.text) }
                    }
                }
                for (tab in paneOf(otherSide).book.tabs) {
                    runCatching { currentLspRouter.applyExternalChange(tab.path.toUri().toString(), tab.text) }
                }
            }
        }
    }
    val doRedo: () -> Unit = {
        val side = focusedPane
        val pane = paneOf(side)
        val current = EditSnapshot(pane.editorValue.text, pane.editorValue.selection.start)
        val result = pane.book.redoOnActive(current)
        if (result != null) {
            val (newBook, restored) = result
            val caret = restored.caret.coerceIn(0, restored.text.length)
            undoTracker(side).markBreak()
            val groupId = restored.groupId
            val activeBook = if (groupId != null) newBook.redoGroupOnNonActive(groupId) else newBook
            mutatePane(side) {
                it.copy(
                    book = activeBook,
                    editorValue = TextFieldValue(restored.text, TextRange(caret)),
                    search = it.search?.retarget(restored.text),
                )
            }
            if (groupId != null) {
                val otherSide = if (side == PaneSide.PRIMARY) PaneSide.SECONDARY else PaneSide.PRIMARY
                mutatePane(otherSide) { it.copy(book = it.book.redoGroupOnNonActive(groupId)) }
                for (tab in activeBook.tabs) {
                    if (tab.path != pane.book.tabs.getOrNull(pane.book.activeIndex)?.path) {
                        runCatching { currentLspRouter.applyExternalChange(tab.path.toUri().toString(), tab.text) }
                    }
                }
                for (tab in paneOf(otherSide).book.tabs) {
                    runCatching { currentLspRouter.applyExternalChange(tab.path.toUri().toString(), tab.text) }
                }
            }
        }
    }


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

    val openQuickOpen: () -> Unit = {
        val root = rootDir
        if (root != null) {
            quickOpenIndex = ProjectFileIndex.walk(root)
            quickOpen = true
        }
    }
    val openFindInFiles: () -> Unit = {
        val root = rootDir
        if (root != null) {
            findInFilesIndex = ProjectFileIndex.walk(root)
            findInFiles = true
        }
    }

    val jumpProblemRelative: (Boolean) -> Unit = { forward ->
        val pane = focused()
        val active = pane.book.active
        if (active != null) {
            val activeList = (currentLspRouter.controllerFor(active.path)?.diagnosticsFor(active.path) ?: emptyList()).sortedWith(
                compareBy({ it.start.line }, { it.start.character }),
            )
            if (activeList.isNotEmpty()) {
                val text = pane.editorValue.text
                val caret = pane.editorValue.selection.start.coerceIn(0, text.length)
                var line = 0
                var lastNl = -1
                var i = 0
                while (i < caret) {
                    if (text[i] == '\n') { line++; lastNl = i }
                    i++
                }
                val char = caret - lastNl - 1
                val target = if (forward) {
                    activeList.firstOrNull { d ->
                        d.start.line > line ||
                            (d.start.line == line && d.start.character > char)
                    } ?: activeList.first()
                } else {
                    activeList.lastOrNull { d ->
                        d.start.line < line ||
                            (d.start.line == line && d.start.character < char)
                    } ?: activeList.last()
                }
                jumpToProblem(active.path, target.start.line, target.start.character)
            } else {
                val anyEntry = currentLspRouter.allDiagnosticsByUri.entries
                    .firstOrNull { it.value.isNotEmpty() }
                if (anyEntry != null) {
                    val path = runCatching { Path.of(java.net.URI(anyEntry.key)) }.getOrNull()
                    val first = anyEntry.value.firstOrNull()
                    if (path != null && first != null) {
                        jumpToProblem(path, first.start.line, first.start.character)
                    }
                }
            }
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
    val openDocumentSymbol: () -> Unit = {
        val active = focused().book.active
        val activePath = active?.path
        val ctrl = activePath?.let { currentLspRouter.controllerFor(it) }
        val status = ctrl?.status?.value
        if (activePath != null
            && ctrl != null
            && status == LspController.Status.READY
        ) {
            val uri = activePath.toUri().toString()
            ctrl.documentSymbols(activePath).whenComplete { syms, err ->
                if (err == null && syms != null) {
                    documentSymbolUri = uri
                    documentSymbolList = syms
                    documentSymbolOpen = true
                }
            }
        }
    }
    val openWorkspaceSymbol: () -> Unit = {
        val wsActivePath = focused().book.active?.path
        val status = wsActivePath?.let { currentLspRouter.controllerFor(it)?.status?.value }
        if (status == LspController.Status.READY) {
            workspaceSymbolOpen = true
        }
    }
    val triggerFormat: () -> Unit = {
        val active = focused().book.active
        val activePath = active?.path
        val fmtCtrl = activePath?.let { currentLspRouter.controllerFor(it) }
        if (activePath != null
            && fmtCtrl != null
            && fmtCtrl.status.value == LspController.Status.READY
        ) {
            fmtCtrl.formatting(activePath).whenComplete { edits, err ->
                val list = edits.orEmpty()
                if (err == null && list.isNotEmpty()) {
                    val change = RenameFileChange(activePath.toUri().toString(), list)
                    applyRename(RenameWorkspaceEdit(listOf(change)))
                }
            }
        }
    }
    val triggerCodeAction: () -> Unit = {
        val active = focused().book.active
        val activePath = active?.path
        val caCtrl = activePath?.let { currentLspRouter.controllerFor(it) }
        if (activePath != null
            && caCtrl != null
            && caCtrl.status.value == LspController.Status.READY
        ) {
            val text = focused().editorValue.text
            val caret = focused().editorValue.selection.start.coerceIn(0, text.length)
            val (line, character) = offsetToLineChar(text, caret)
            val snapshotUri = activePath.toUri().toString()
            val snapshotText = text
            val lineLen = run {
                val lineStart = text.lastIndexOf('\n', (caret - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
                val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
                lineEnd - lineStart
            }
            caCtrl.codeActions(activePath, line, 0, line, lineLen).whenComplete { actions, err ->
                if (err == null) {
                    val raw = actions.orEmpty()
                    val list = raw.map { entry ->
                        val normalized = page.lsp.CodeActionNormalize.normalize(entry.edit, snapshotUri, snapshotText)
                        if (normalized !== entry.edit) {
                            println("[lsp] codeAction normalize ▶ \"${entry.title}\" — KLS edit 보정됨 (import \\n 보강)")
                            entry.copy(edit = normalized)
                        } else entry
                    }
                    codeActionList = list
                    codeActionUri = snapshotUri
                    codeActionText = snapshotText
                    codeActionSelected = list.indexOfFirst { it.isPreferred }.coerceAtLeast(0)
                    codeActionOpen = list.isNotEmpty()
                    if (list.isNotEmpty()) {
                        println("[lsp] codeAction debug ▼ $snapshotUri @($line,$character) — ${list.size} action(s)")
                        for ((idx, entry) in list.withIndex()) {
                            println("[lsp] codeAction debug [${idx + 1}/${list.size}]")
                            println(page.lsp.CodeActionDebug.format(entry, snapshotUri, snapshotText))
                        }
                    }
                }
            }
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

    if (quickOpen) {
        QuickOpenDialog(
            files = quickOpenIndex,
            onPick = { f ->
                quickOpen = false
                openInTab(f.path)
            },
            onDismiss = { quickOpen = false },
        )
    }

    if (documentSymbolOpen) {
        DocumentSymbolDialog(
            uri = documentSymbolUri,
            symbols = documentSymbolList,
            onPick = { pick ->
                documentSymbolOpen = false
                val pickedPath = runCatching { Path.of(java.net.URI(pick.uri)) }.getOrNull()
                if (pickedPath != null) {
                    jumpToProblem(pickedPath, pick.startLine, pick.startCharacter)
                }
                frameRef.value?.requestFocus()
                editorFocusVersion += 1
            },
            onDismiss = {
                documentSymbolOpen = false
                frameRef.value?.requestFocus()
            },
        )
    }

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


    val activeCreateDialog = createDialog
    if (activeCreateDialog != null) {
        val isFile = activeCreateDialog.kind == CreateEntryKind.FILE
        val rel = FileTreeActions.relativeTo(rootDir, activeCreateDialog.parent)
        val parentLabel = if (rel.isEmpty() || rel == ".") "/" else rel
        NameInputDialog(
            title = if (isFile) "New file" else "New folder",
            label = "$parentLabel  /  name",
            error = activeCreateDialog.error,
            onSubmit = { name ->
                val result = if (isFile) {
                    FileTreeActions.createFile(activeCreateDialog.parent, name)
                } else {
                    FileTreeActions.createFolder(activeCreateDialog.parent, name)
                }
                when (result) {
                    is FileTreeActions.CreateResult.Ok -> {
                        treeRevision++
                        expanded = expanded + activeCreateDialog.parent
                        if (isFile) openInTab(result.path)
                        fileOpHistory.push(FileOpHistory.CreateOp(result.path, isDirectory = !isFile))
                        fileOpHistoryVersion++
                        createDialog = null
                    }
                    is FileTreeActions.CreateResult.Err -> {
                        createDialog = activeCreateDialog.copy(error = result.message)
                    }
                }
            },
            onDismiss = { createDialog = null },
        )
    }

    val activeRenameDialog = renameDialog
    val impactScope = rememberCoroutineScope()
    val impactScanner = remember(currentLspRouter) {
        ReferenceScanner(
            documentSymbols = { p -> currentLspRouter.controllerFor(p)?.documentSymbols(p) ?: java.util.concurrent.CompletableFuture.completedFuture(emptyList()) },
            references = { p, l, c -> currentLspRouter.controllerFor(p)?.references(p, l, c, includeDeclaration = false) ?: java.util.concurrent.CompletableFuture.completedFuture(emptyList()) },
            ensureOpen = { p ->
                val langId = currentLspRouter.languageIdFor(p)
                if (langId != null) {
                    val text = runCatching { java.nio.file.Files.readString(p) }.getOrNull()
                    if (text != null) currentLspRouter.controllerFor(p)?.didOpen(p, langId, text)
                }
            },
            scope = impactScope,
        )
    }
    val renameImpactPath = activeRenameDialog?.path?.takeUnless { java.nio.file.Files.isDirectory(it) }
    val impactTarget: Path? = renameImpactPath ?: deleteDialog?.primary
    val impactState: ImpactScanState = produceState<ImpactScanState>(ImpactScanState.Idle, impactTarget) {
        val t = impactTarget
        if (t == null) {
            value = ImpactScanState.Idle
            return@produceState
        }
        value = ImpactScanState.Scanning(0, 1)
        val (flow, job) = impactScanner.scan(t)
        try {
            flow.collect { value = it }
        } finally {
            job.cancel()
        }
    }.value

    if (activeRenameDialog != null) {
        val currentName = activeRenameDialog.path.fileName?.toString() ?: ""
        val parentRel = activeRenameDialog.path.parent?.let { FileTreeActions.relativeTo(rootDir, it) } ?: ""
        val parentLabel = if (parentRel.isEmpty() || parentRel == ".") "/" else parentRel
        NameInputDialog(
            title = "Rename",
            label = "$parentLabel  /  name (current: $currentName)",
            initial = currentName,
            error = activeRenameDialog.error,
            impact = impactState,
            rootDir = rootDir,
            onJumpToHit = { hit ->
                renameDialog = null
                jumpToProblem(hit.file, hit.line, hit.column)
            },
            onSubmit = { newName ->
                val oldPath = activeRenameDialog.path
                val oldName = oldPath.fileName?.toString().orEmpty()
                val oldStem = FileSymbolRename.stripKotlinExtension(oldName)
                val newStem = FileSymbolRename.stripKotlinExtension(newName)
                val shouldRenameSymbol = oldStem != null && newStem != null &&
                    oldStem != newStem &&
                    FileSymbolRename.isValidKotlinIdentifier(newStem)

                fun finishFileRename() {
                    val isDir = java.nio.file.Files.isDirectory(oldPath)
                    val packageMap = if (isDir) {
                        FolderPackageRename.computePackageMap(oldPath, newName, readFileTextWithTabs)
                    } else emptyMap()
                    var renameOriginals: List<FileOpHistory.RewriteEntry> = emptyList()
                    val performAndProcess: () -> FileTreeActions.RenameResult = {
                        val txRoot = if (isDir) rootDir else null
                        var r = FileTreeActions.rename(oldPath, newName, txRoot)
                        if (isDir && r is FileTreeActions.RenameResult.Err) {
                            for (i in 1..5) {
                                runCatching { Thread.sleep(250L) }
                                r = FileTreeActions.rename(oldPath, newName, txRoot)
                                if (r is FileTreeActions.RenameResult.Ok) break
                            }
                        }
                        val ok = r as? FileTreeActions.RenameResult.Ok
                        if (ok != null) {
                            remapTabsAfterRename(oldPath, ok.path)
                            remapTreeStateAfterRename(oldPath, ok.path)
                            if (isDir) {
                                renameOriginals = applyFolderPackageSync(oldPath, ok.path, packageMap)
                            }
                        }
                        r
                    }
                    val result: FileTreeActions.RenameResult = if (isDir) {
                        var captured: FileTreeActions.RenameResult? = null
                        val dirCtrl = currentLspRouter.controllerFor(oldPath)
                        val doRename = {
                            withFileTreeWatcherClosed {
                                captured = performAndProcess()
                            }
                        }
                        if (dirCtrl != null) {
                            dirCtrl.runWithClientDown("folder rename: ${oldPath.fileName} → $newName") { doRename() }
                        } else {
                            doRename()
                        }
                        captured ?: FileTreeActions.RenameResult.Err("rename did not run")
                    } else {
                        performAndProcess()
                    }
                    when (result) {
                        is FileTreeActions.RenameResult.Ok -> {
                            treeRevision++
                            val renameOp = FileOpHistory.RenameOp(from = oldPath, to = result.path)
                            val composedOp: FileOpHistory.Op = if (renameOriginals.isEmpty()) renameOp
                                else FileOpHistory.CompositeOp(listOf(FileOpHistory.ReferenceRewriteOp(renameOriginals), renameOp))
                            fileOpHistory.push(composedOp)
                            fileOpHistoryVersion++
                            renameDialog = null
                        }
                        is FileTreeActions.RenameResult.Err -> {
                            renameDialog = activeRenameDialog.copy(error = result.message)
                        }
                    }
                }

                if (shouldRenameSymbol) {
                    impactScope.launch {
                        val symCtrl = currentLspRouter.controllerFor(oldPath)
                        val symLangId = currentLspRouter.languageIdFor(oldPath)
                        val syms = runCatching {
                            symCtrl?.documentSymbols(oldPath)?.await()
                        }.getOrNull().orEmpty()
                        val pick = FileSymbolRename.findRenamableTopLevelSymbol(oldStem!!, syms)
                        if (pick != null && symCtrl != null && symLangId != null) {
                            val openText = paneOf(PaneSide.PRIMARY).book.tabs.firstOrNull { it.path == oldPath }?.text
                                ?: paneOf(PaneSide.SECONDARY).book.tabs.firstOrNull { it.path == oldPath }?.text
                            val fileText = openText ?: runCatching {
                                java.nio.file.Files.readString(oldPath)
                            }.getOrNull()
                            if (fileText != null) {
                                if (!symCtrl.isOpenAt(oldPath)) {
                                    runCatching { symCtrl.didOpen(oldPath, symLangId, fileText) }
                                }
                                val candidatePaths = mutableListOf<java.nio.file.Path>()
                                rootDir?.let { root ->
                                    runCatching {
                                        java.nio.file.Files.walk(root).use { stream ->
                                            stream
                                                .filter { p -> java.nio.file.Files.isRegularFile(p) }
                                                .filter { p ->
                                                    currentLspRouter.languageIdFor(p) == symLangId
                                                }
                                                .forEach { p ->
                                                    val norm = p.toAbsolutePath().normalize()
                                                    if (norm == oldPath.toAbsolutePath().normalize()) return@forEach
                                                    val text = runCatching {
                                                        java.nio.file.Files.readString(norm)
                                                    }.getOrNull() ?: return@forEach
                                                    if (!text.contains(oldStem)) return@forEach
                                                    candidatePaths.add(norm)
                                                    if (!symCtrl.isOpenAt(norm)) {
                                                        runCatching { symCtrl.didOpen(norm, symLangId, text) }
                                                    }
                                                }
                                        }
                                    }
                                }
                                val edit = runCatching {
                                    symCtrl.rename(
                                        oldPath,
                                        fileText,
                                        pick.selectionRange.startLine,
                                        pick.selectionRange.startCharacter,
                                        newStem!!,
                                    ).await()
                                }.getOrNull()
                                val refs = runCatching {
                                    symCtrl.references(
                                        oldPath,
                                        pick.selectionRange.startLine,
                                        pick.selectionRange.startCharacter,
                                        includeDeclaration = false,
                                    ).await()
                                }.getOrDefault(emptyList())
                                val readText: (java.nio.file.Path) -> String? = { p ->
                                    paneOf(PaneSide.PRIMARY).book.tabs.firstOrNull { it.path == p }?.text
                                        ?: paneOf(PaneSide.SECONDARY).book.tabs.firstOrNull { it.path == p }?.text
                                        ?: runCatching { java.nio.file.Files.readString(p) }.getOrNull()
                                }
                                val withRefs = page.lsp.RenameAugment.augment(
                                    edit = edit ?: page.lsp.RenameWorkspaceEdit.EMPTY,
                                    references = refs,
                                    oldName = oldStem,
                                    newName = newStem!!,
                                    readFileText = readText,
                                )
                                val withTextual = page.lsp.RenameAugment.augmentTextually(
                                    edit = withRefs,
                                    oldName = oldStem,
                                    newName = newStem,
                                    readFileText = readText,
                                )
                                val withDecl = page.lsp.RenameAugment.augmentDeclarationFile(
                                    edit = withTextual,
                                    declarationPath = oldPath,
                                    oldName = oldStem,
                                    newName = newStem,
                                    readFileText = readText,
                                )
                                val declarationPackage = FileSymbolRename.readPackageDeclaration(fileText)
                                val augmented = page.lsp.RenameAugment.augmentImports(
                                    edit = withDecl,
                                    candidatePaths = candidatePaths,
                                    oldName = oldStem,
                                    newName = newStem,
                                    readFileText = readText,
                                    declarationPackage = declarationPackage,
                                )
                                if (augmented.changes.isNotEmpty()) {
                                    applyRename(augmented)
                                }
                            }
                        }
                        finishFileRename()
                    }
                } else {
                    finishFileRename()
                }
            },
            onDismiss = { renameDialog = null },
        )
    }

    val activeDeleteDialog = deleteDialog
    if (activeDeleteDialog != null) {
        val multi = activeDeleteDialog.isMulti
        val first = activeDeleteDialog.primary
        val displayName = first.fileName?.toString() ?: first.toString()
        val isDir = java.nio.file.Files.isDirectory(first)
        val rel = FileTreeActions.relativeTo(rootDir, first)
        val message = if (multi) {
            "Delete ${activeDeleteDialog.paths.size} items?"
        } else {
            "Delete ${if (isDir) "folder" else "file"} '$displayName'?"
        }
        val detail = if (multi) {
            val preview = activeDeleteDialog.paths.take(4)
                .joinToString("\n") { FileTreeActions.relativeTo(rootDir, it) }
            if (activeDeleteDialog.paths.size > 4) "$preview\n…" else preview
        } else if (rel.isEmpty()) null else rel
        ConfirmDialog(
            title = "Delete",
            message = message,
            detail = detail,
            impact = if (multi) ImpactScanState.Idle else impactState,
            rootDir = rootDir,
            onJumpToHit = { hit ->
                deleteDialog = null
                jumpToProblem(hit.file, hit.line, hit.column)
            },
            confirmLabel = if (multi) "Delete all" else "Delete",
            danger = true,
            onConfirm = {
                val workspace = rootDir
                if (workspace != null) {
                    val trashed = FileTreeActions.deleteToTrash(activeDeleteDialog.paths, workspace)
                    val entries = when (trashed) {
                        is FileTreeActions.TrashResult.Ok -> trashed.entries
                        is FileTreeActions.TrashResult.Err -> {
                            println("[filetree] trash failed: ${trashed.message}")
                            trashed.partialEntries
                        }
                    }
                    entries.forEach { entry -> closeTabsUnderPath(entry.originalPath) }
                    if (entries.isNotEmpty()) {
                        fileOpHistory.push(FileOpHistory.DeleteOp(entries))
                        fileOpHistoryVersion++
                        treeRevision++
                        FileTreeActions.purgeTrashOlderThan(workspace)
                    }
                } else {
                    val outcome = FileTreeActions.deleteBatch(activeDeleteDialog.paths)
                    outcome.results.forEach { (path, result) ->
                        if (result is FileTreeActions.DeleteResult.Ok) {
                            closeTabsUnderPath(path)
                        } else if (result is FileTreeActions.DeleteResult.Err) {
                            println("[filetree] delete failed for $path: ${result.message}")
                        }
                    }
                    if (outcome.successCount > 0) treeRevision++
                }
                deleteDialog = null
            },
            onDismiss = { deleteDialog = null },
        )
    }

    val activePasteDialog = pasteDialog
    if (activePasteDialog != null && activePasteDialog.remaining.isNotEmpty()) {
        val source = activePasteDialog.remaining.first()
        val sourceName = source.fileName?.toString() ?: source.toString()
        val destRel = FileTreeActions.relativeTo(rootDir, activePasteDialog.destParent)
        val destLabel = if (destRel.isEmpty() || destRel == ".") "/" else destRel
        val verb = if (activePasteDialog.mode == FileTreeClipboard.Mode.Cut) "Move" else "Copy"
        val total = activePasteDialog.remaining.size
        val countSuffix = if (total > 1) "  ($total remaining)" else ""
        val skipOne: (() -> Unit)? = if (total > 1) {
            {
                val cur = activePasteDialog
                val rest = cur.remaining.drop(1)
                pasteDialog = if (rest.isEmpty()) null else cur.copy(remaining = rest, error = null)
                if (rest.isEmpty()) fileTreeActionExecutor.finalizePasteHistory(cur)
            }
        } else null
        val skipAll: (() -> Unit)? = if (total > 1) {
            {
                val cur = activePasteDialog
                pasteDialog = null
                fileTreeActionExecutor.finalizePasteHistory(cur)
            }
        } else null
        val performPaste: (String, Boolean) -> Unit = { newName, overwriteOnce ->
            fileTreeActionExecutor.performPaste(newName, overwriteOnce)
        }
        NameInputDialog(
            title = "$verb into $destLabel$countSuffix",
            label = "$sourceName  →  $destLabel  /  name",
            initial = sourceName,
            error = activePasteDialog.error,
            onSkip = skipOne,
            onSkipRemaining = skipAll,
            onOverwrite = { name -> performPaste(name, true) },
            onOverwriteAll = { name ->
                pasteDialog = activePasteDialog.copy(overwriteForAll = true)
                performPaste(name, true)
            },
            onSubmit = { newName -> performPaste(newName, false) },
            onDismiss = { pasteDialog = null },
        )
    }

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

    if (workspaceSymbolOpen) {
        WorkspaceSymbolDialog(
            queryFor = { q ->
                val wsPath = focused().book.active?.path
                val wsCtrl = wsPath?.let { currentLspRouter.controllerFor(it) }
                if (wsCtrl != null) runCatching { wsCtrl.workspaceSymbolsLocated(q).await() }.getOrDefault(emptyList())
                else emptyList()
            },
            onPick = { pick ->
                workspaceSymbolOpen = false
                val pickedPath = runCatching { Path.of(java.net.URI(pick.uri)) }.getOrNull()
                if (pickedPath != null) {
                    jumpToProblem(pickedPath, pick.startLine, pick.startCharacter)
                }
                frameRef.value?.requestFocus()
                editorFocusVersion += 1
            },
            onDismiss = {
                workspaceSymbolOpen = false
                frameRef.value?.requestFocus()
            },
        )
    }

    val current = pendingClose
    if (current != null) {
        val targets: List<Triple<PaneSide, Int, OpenTab>> = when (current) {
            is PendingClose.Tab -> {
                val tab = paneOf(current.side).book.tabs.getOrNull(current.index)
                if (tab != null) listOf(Triple(current.side, current.index, tab)) else emptyList()
            }
            PendingClose.App -> buildList {
                primaryPane.book.tabs.forEachIndexed { idx, tab ->
                    if (isUnsavedText(tab)) add(Triple(PaneSide.PRIMARY, idx, tab))
                }
                secondaryPane.book.tabs.forEachIndexed { idx, tab ->
                    if (isUnsavedText(tab)) add(Triple(PaneSide.SECONDARY, idx, tab))
                }
            }
            is PendingClose.Batch -> buildList {
                current.targets.forEach { (side, path) ->
                    val idx = paneOf(side).book.tabs.indexOfFirst { it.path == path }
                    val tab = paneOf(side).book.tabs.getOrNull(idx)
                    if (tab != null && isUnsavedText(tab)) add(Triple(side, idx, tab))
                }
            }
        }
        val targetNames = targets.map { (_, _, t) ->
            t.path.fileName?.toString() ?: t.path.toString()
        }
        val finishBatch: (PendingClose.Batch) -> Unit = { batch ->
            val grouped = batch.targets.groupBy({ it.first }) { it.second }
            grouped.forEach { (side, paths) ->
                val pane = paneOf(side)
                val indices = paths.mapNotNull { p ->
                    pane.book.tabs.indexOfFirst { it.path == p }.takeIf { it >= 0 }
                }
                if (indices.isNotEmpty()) {
                    val closedPaths = indices.map { pane.book.tabs[it].path }
                    mutatePane(side) { it.copy(book = it.book.closeMany(indices)) }
                    closedPaths.forEach { p ->
                        val stillOpen = primaryPane.book.tabs.any { it.path == p } ||
                            secondaryPane.book.tabs.any { it.path == p }
                        if (!stillOpen) {
                            editorScrollByPath = EditorScrollMemory.clear(editorScrollByPath, p)
                            currentLspRouter.controllerFor(p)?.didClose(p)
                        }
                    }
                }
            }
        }
        UnsavedChangesDialog(
            fileNames = targetNames,
            isAppExit = current is PendingClose.App,
            onSave = {
                targets.forEach { (_, _, t) -> FileDocument.save(t.path, t.text) }
                pendingClose = null
                when (current) {
                    is PendingClose.Tab -> closeTabAt(current.side, current.index)
                    PendingClose.App -> exitApplication()
                    is PendingClose.Batch -> finishBatch(current)
                }
            },
            onDiscard = {
                pendingClose = null
                when (current) {
                    is PendingClose.Tab -> closeTabAt(current.side, current.index)
                    PendingClose.App -> exitApplication()
                    is PendingClose.Batch -> finishBatch(current)
                }
            },
            onCancel = { pendingClose = null },
        )
    }
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

private var backendsRegistered = false

private val nonRoutingBackendIds = setOf("flutter")

private fun registerAllBackends() {
    if (backendsRegistered) return
    backendsRegistered = true
    for (def in LanguageRegistry.all()) {
        if (def.id in nonRoutingBackendIds) continue
        if (LspBackends.byId(def.id) != null) continue
        LspBackends.register(GenericLanguageBackend(
            definition = def,
            executableFinder = { LspInstallers.forId(def.id)?.executable() },
            envSetup = { env ->
                PageRuntimeEnv.applyTo(env)
                if (def.id == "java") PageRuntimeEnv.pinJavaRuntime(env)
            },
            initializationOptionsProvider = when (def.id) {
                "rust" -> { root -> CargoWorkspaceDetector.linkedProjects(root) }
                "java" -> { _ -> JdtlsInitializationOptions.forWorkspace() }
                else -> null
            },
        ))
    }
}

internal fun resolveLanguageForPath(path: Path): page.lsp.LanguageDefinition? {
    val name = path.fileName?.toString() ?: return null
    val dot = name.lastIndexOf('.')
    val ext = if (dot >= 0 && dot < name.length - 1) name.substring(dot + 1) else name
    return page.lsp.LanguageRegistry.byExtension(ext)
}

@androidx.compose.runtime.Composable
private fun lspStatusLineText(lspRouter: LspRouter, activePath: Path?): String? {
    val definition = activePath?.let(::resolveLanguageForPath)
    val langId = definition?.id
    val displayName = definition?.displayName
    val isKotlin = langId == "kotlin" || (langId == null && activePath?.fileName?.toString()?.endsWith(".kt") != false)
    if (langId == null && !isKotlin) return null
    val resolvedId = langId ?: "kotlin"
    val resolvedName = displayName ?: "Kotlin"
    val ctrl = activePath?.let { lspRouter.controllerFor(it) }
    val installer = LspInstallers.forId(resolvedId) ?: return when {
        isKotlin && ctrl?.status?.value == LspController.Status.MISSING -> "LSP · kotlin-language-server missing"
        isKotlin && ctrl?.status?.value == LspController.Status.FAILED -> "LSP · failed to start"
        else -> null
    }
    val installed = installer.installedVersion()
    val suffix = when (ctrl?.status?.value) {
        LspController.Status.FAILED -> " · failed"
        LspController.Status.STARTING -> " · starting…"
        LspController.Status.MISSING -> " · not installed"
        LspController.Status.IDLE -> " · idle"
        LspController.Status.READY -> ""
        null -> if (installed != null) " · not started" else ""
    }
    val core = if (installed != null) "$resolvedName $installed" else "$resolvedName (not installed)"
    return "$core$suffix"
}

private fun detectRuntimeVersions(projectRoot: java.nio.file.Path? = null): Map<String, String> {
    val vers = mutableMapOf<String, String>()
    val jdk = runCatching { JdkInstaller().activeVersion() }.getOrNull() ?: System.getProperty("java.version")
    if (!jdk.isNullOrBlank()) vers["java"] = jdk
    val node = runCatching { NodeInstaller().activeVersion() }.getOrNull()
        ?: runCatching { captureVersion("node", "--version")?.removePrefix("v") }.getOrNull()
    if (!node.isNullOrBlank()) vers["js"] = node
    val py = runCatching { PythonInstaller().activeVersion() }.getOrNull()
        ?: runCatching { captureVersion("python", "--version")?.substringAfter("Python ")?.trim() }.getOrNull()
    if (!py.isNullOrBlank()) vers["py"] = py
    val go = runCatching { GoSdkInstaller().activeVersion() }.getOrNull()
        ?: runCatching { captureVersion("go", "version")?.let { Regex("go(\\d+\\.\\d+\\.\\d+)").find(it)?.groupValues?.get(1) } }.getOrNull()
    if (!go.isNullOrBlank()) vers["go"] = go
    val cpp = runCatching { CppToolchainInstaller().activeVersion() }.getOrNull()
        ?: runCatching { captureVersion("clang", "--version")?.let { Regex("(\\d+\\.\\d+\\.\\d+)").find(it)?.groupValues?.get(1) } }.getOrNull()
    if (!cpp.isNullOrBlank()) vers["cpp"] = cpp
    val rust = runCatching { RustToolchainInstaller().activeVersion() }.getOrNull()
        ?: runCatching { captureVersion("rustc", "--version")?.let { Regex("(\\d+\\.\\d+\\.\\d+)").find(it)?.groupValues?.get(1) } }.getOrNull()
    if (!rust.isNullOrBlank()) vers["rs"] = rust
    val dotnet = runCatching { DotnetSdkInstaller().activeVersion() }.getOrNull()
        ?: runCatching { captureVersion("dotnet", "--version") }.getOrNull()
    if (!dotnet.isNullOrBlank()) vers["cs"] = dotnet
    if (projectRoot != null) {
        var detected = runCatching { BuildFileVersionDetector.detect(projectRoot) }.getOrDefault(emptyList())
        if (detected.isEmpty()) {
            detected = runCatching {
                java.nio.file.Files.list(projectRoot).use { stream ->
                    stream.filter { java.nio.file.Files.isDirectory(it) }
                        .flatMap { BuildFileVersionDetector.detect(it).stream() }
                        .toList()
                }
            }.getOrDefault(emptyList())
        }
        for (d in detected) {
            val key = when (d.runtime) {
                "jdk" -> "java"
                "node" -> "js"
                "python-runtime" -> "py"
                "go-sdk" -> "go"
                "rust" -> "rs"
                "dotnet" -> "cs"
                else -> continue
            }
            val hasManaged = when (d.runtime) {
                "jdk" -> runCatching { JdkInstaller().activeVersion() }.getOrNull() != null
                "node" -> runCatching { NodeInstaller().activeVersion() }.getOrNull() != null
                "python-runtime" -> runCatching { PythonInstaller().activeVersion() }.getOrNull() != null
                "go-sdk" -> runCatching { GoSdkInstaller().activeVersion() }.getOrNull() != null
                else -> false
            }
            if (!hasManaged) vers[key] = d.version
        }
    }
    return vers
}

internal fun detectRuntimeVersionsWithSources(projectRoot: java.nio.file.Path? = null): Triple<Map<String, String>, Map<String, String>, Map<String, String>> {
    val vers = detectRuntimeVersions(projectRoot)
    val sources = mutableMapOf<String, String>()
    val buildVers = mutableMapOf<String, String>()
    if (projectRoot != null) {
        var detected = runCatching { BuildFileVersionDetector.detect(projectRoot) }.getOrDefault(emptyList())
        if (detected.isEmpty()) {
            detected = runCatching {
                java.nio.file.Files.list(projectRoot).use { stream ->
                    stream.filter { java.nio.file.Files.isDirectory(it) }
                        .flatMap { BuildFileVersionDetector.detect(it).stream() }
                        .toList()
                }
            }.getOrDefault(emptyList())
        }
        for (d in detected) {
            val key = when (d.runtime) {
                "jdk" -> "java"; "node" -> "js"; "python-runtime" -> "py"; "go-sdk" -> "go"; "rust" -> "rs"; "dotnet" -> "cs"; else -> continue
            }
            sources[key] = d.source
            buildVers[key] = d.version
        }
    }
    return Triple(vers, sources, buildVers)
}

private fun captureVersion(cmd: String, vararg args: String): String? {
    val p = ProcessBuilder(cmd, *args).redirectErrorStream(true).start()
    val out = p.inputStream.bufferedReader().use { it.readLine() }
    p.waitFor()
    return out?.trim()?.takeIf { it.isNotBlank() }
}

@Composable
internal fun PaneRegion(
    pane: EditorPaneState,
    side: PaneSide,
    editor: EditorWorkspaceState,
    lspRouter: LspRouter,
    isFocused: Boolean,
    onPaneFocus: (PaneSide) -> Unit,
    onCloseTab: (PaneSide, Int) -> Unit,
    onQueryChange: (PaneSide, String) -> Unit,
    onReplaceChange: (PaneSide, String) -> Unit,
    onToggleCase: (PaneSide) -> Unit,
    onSearchNext: (PaneSide) -> Unit,
    onSearchPrev: (PaneSide) -> Unit,
    onReplace: (PaneSide) -> Unit,
    onReplaceAll: (PaneSide) -> Unit,
    onSearchClose: (PaneSide) -> Unit,
    onWindowShortcut: (KeyEvent) -> Boolean,
    onTabDragStart: () -> Unit = {},
    onTabDragEnd: () -> Unit = {},
    onProblemsToggle: () -> Unit = {},
    onJumpToProblem: (Path, Int, Int) -> Unit = { _, _, _ -> },
    onApplyRename: (RenameWorkspaceEdit) -> Unit = {},
    onRequestReferences: (Path, Int, Int, String) -> Unit = { _, _, _, _ -> },
    todoCount: Int = 0,
    onTodoToggle: () -> Unit = {},
    workspaceRoot: Path? = null,
    editorFocusVersion: Int = 0,
    initialFoldedStartLines: Set<Int> = emptySet(),
    onFoldStartLinesChange: (Path, Set<Int>) -> Unit = { _, _ -> },
    editorScrollFor: (Path) -> EditorScrollSnapshot? = { null },
    onEditorScrollChange: (Path, EditorScrollSnapshot) -> Unit = { _, _ -> },
    tabContextActions: TabContextActions? = null,
    runtimeVersions: Map<String, String> = emptyMap(),
    runtimeSources: Map<String, String> = emptyMap(),
    runtimeBuildFileVersions: Map<String, String> = emptyMap(),
    onRuntimeClick: ((String) -> Unit)? = null,
    pageSettings: PageSettings = PageSettings(),
    modifier: Modifier = Modifier,
) {
    val active = pane.book.active
    val kind = active?.let { FileKinds.classify(it.path) }
    val activeLexer = active?.path?.let { SyntaxLexers.forPath(it) }
    val activeExt = remember(active?.path) {
        active?.path?.fileName?.toString()?.lowercase()?.substringAfterLast('.', "") ?: ""
    }
    val runtimeInfo: Triple<String, String, String?>? = remember(activeExt, runtimeVersions, runtimeSources, runtimeBuildFileVersions) {
        fun build(name: String, key: String, id: String): Triple<String, String, String?> {
            val ver = runtimeVersions[key] ?: "?"
            val bfVer = runtimeBuildFileVersions[key]
            val src = runtimeSources[key]
            val mismatch = bfVer != null && ver != "?" && !ver.startsWith(bfVer)
            val label = if (mismatch) "$name $ver ⚠" else "$name $ver"
            val tooltip = when {
                mismatch -> "Project requires $bfVer ($src), using $ver"
                src != null -> "from $src"
                else -> null
            }
            return Triple(label, id, tooltip)
        }
        when (activeExt) {
            "java" -> build("JDK", "java", "jdk")
            "js", "mjs", "cjs", "ts" -> build("Node", "js", "node")
            "py" -> build("Python", "py", "python-runtime")
            "go" -> build("Go", "go", "go-sdk")
            "c", "cpp", "cc", "cxx", "h", "hpp" -> build("Clang", "cpp", "cpp-toolchain")
            "rs" -> build("Rust", "rs", "rust-runtime")
            "cs" -> build(".NET", "cs", "dotnet-runtime")
            else -> null
        }
    }
    Column(
        modifier = modifier.fillMaxSize()
            .pointerInput(side) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        if (e.type == PointerEventType.Press) onPaneFocus(side)
                    }
                }
            },
    ) {
        TabBar(
            book = pane.book,
            onActivate = { idx ->
                onPaneFocus(side)
                editor.activateTab(side, idx)
            },
            onClose = { idx -> onCloseTab(side, idx) },
            onMove = { from, to -> editor.moveTab(side, from, to) },
            onMoveToOtherPane = if (editor.splitEnabled) {
                { idx -> editor.moveTabAcross(side, idx) }
            } else null,
            crossPaneSide = if (!editor.splitEnabled) null else when (side) {
                PaneSide.PRIMARY -> CrossPaneSide.RIGHT
                PaneSide.SECONDARY -> CrossPaneSide.LEFT
            },
            onDragStart = onTabDragStart,
            onDragEnd = onTabDragEnd,
            contextActions = tabContextActions,
            showCloseButton = pageSettings.ui.showTabCloseButton,
        )
        FocusIndicator(visible = isFocused)
        when (kind) {
            FileKind.IMAGE -> PreviewPanel(
                path = active.path,
                kind = kind,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            FileKind.SVG -> SvgEditPanel(
                path = active.path,
                value = pane.editorValue,
                onValueChange = { v -> editor.handleEditorChange(side, v) },
                search = pane.search,
                onQueryChange = { q -> onQueryChange(side, q) },
                onReplaceChange = { v -> onReplaceChange(side, v) },
                onToggleCase = { onToggleCase(side) },
                onSearchNext = { onSearchNext(side) },
                onSearchPrev = { onSearchPrev(side) },
                onReplace = { onReplace(side) },
                onReplaceAll = { onReplaceAll(side) },
                onSearchClose = { onSearchClose(side) },
                onWindowShortcut = onWindowShortcut,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            else -> if (active == null) {
                EmptyPanePlaceholder(modifier = Modifier.fillMaxWidth().weight(1f))
            } else {
                val activeCtrl = lspRouter.controllerFor(active.path)
                val activeDiagnostics = activeCtrl?.diagnosticsFor(active.path).orEmpty()
                val lspStatusText = lspStatusLineText(lspRouter, active?.path)
                val ctrlActivities = activeCtrl?.activities?.values
                    ?.sortedBy { it.startedAtMs }
                    ?.toList().orEmpty()
                val globalStarting = lspRouter.startingActivities
                val installActivities = InstallProgressRegistry.entries.values.map { e ->
                    val frac = (e.progress as? page.runtime.LspInstaller.Progress.Downloading)
                        ?.takeIf { it.total > 0 }
                        ?.let { (it.bytesRead.toFloat() / it.total.toFloat()).coerceIn(0f, 1f) }
                    page.app.LspController.Activity(
                        kind = "install",
                        label = "${e.displayName} (installing)",
                        startedAtMs = e.startedAtMs,
                        progress = frac,
                        installerId = e.installerId,
                    )
                }
                val lspActivities = (globalStarting + ctrlActivities + installActivities)
                    .distinctBy { it.kind + it.label }
                    .sortedBy { it.startedAtMs }
                EditorPanel(
                    value = pane.editorValue,
                    onValueChange = { v -> editor.handleEditorChange(side, v) },
                    search = pane.search,
                    onQueryChange = { q -> onQueryChange(side, q) },
                    onReplaceChange = { v -> onReplaceChange(side, v) },
                    onToggleCase = { onToggleCase(side) },
                    onSearchNext = { onSearchNext(side) },
                    onSearchPrev = { onSearchPrev(side) },
                    onReplace = { onReplace(side) },
                    onReplaceAll = { onReplaceAll(side) },
                    onSearchClose = { onSearchClose(side) },
                    onWindowShortcut = onWindowShortcut,
                    lexer = activeLexer,
                    activePath = active?.path,
                    diagnostics = activeDiagnostics,
                    lspStatusText = lspStatusText,
                    lspActivities = lspActivities,
                    onLspStatusClick = { activeCtrl?.openInstallGuide() },
                    onActivityClick = { act ->
                        act.installerId?.let { onRuntimeClick?.invoke(it) }
                    },
                    onProblemsToggle = onProblemsToggle,
                    todoCount = todoCount,
                    onTodoToggle = onTodoToggle,
                    onRequestCompletion = active?.path?.let { p ->
                        activeCtrl?.let { ctrl -> { line, ch, trig -> ctrl.completion(p, pane.editorValue.text, line, ch, trig) } }
                    },
                    onRequestHover = active?.path?.let { p ->
                        activeCtrl?.let { ctrl -> { line, ch -> ctrl.hover(p, line, ch) } }
                    },
                    onRequestDefinition = active?.path?.let { p ->
                        activeCtrl?.let { ctrl -> { line, ch -> ctrl.definition(p, line, ch) } }
                    },
                    onRequestSignatureHelp = active?.path?.let { p ->
                        activeCtrl?.let { ctrl -> { line, ch, trig, retrig -> ctrl.signatureHelp(p, pane.editorValue.text, line, ch, trig, retrig) } }
                    },
                    onResolveCompletion = activeCtrl?.let { ctrl -> { token -> ctrl.resolveCompletion(token) } },
                    onGoToDefinition = { target ->
                        val path = runCatching {
                            java.nio.file.Paths.get(java.net.URI(target.uri))
                        }.getOrNull()
                        if (path != null) onJumpToProblem(path, target.startLine, target.startCharacter)
                    },
                    onRequestPrepareRename = active?.path?.let { p ->
                        activeCtrl?.let { ctrl -> { line, ch -> ctrl.prepareRename(p, line, ch) } }
                    },
                    onRequestRename = active?.path?.let { p ->
                        activeCtrl?.let { ctrl -> { line, ch, name -> ctrl.rename(p, pane.editorValue.text, line, ch, name) } }
                    },
                    onApplyRename = onApplyRename,
                    onRequestReferences = active?.path?.let { p ->
                        { line, ch, sym -> onRequestReferences(p, line, ch, sym) }
                    },
                    onRequestInlayHints = active?.path
                        ?.takeIf { activeCtrl?.status?.value == LspController.Status.READY }
                        ?.let { p ->
                            activeCtrl?.let { ctrl -> { sl, sc, el, ec -> ctrl.inlayHints(p, sl, sc, el, ec) } }
                        },
                    workspaceRoot = workspaceRoot,
                    editorFocusVersion = editorFocusVersion,
                    initialFoldedStartLines = initialFoldedStartLines,
                    onFoldStartLinesChange = { lines ->
                        active.path.let { p -> onFoldStartLinesChange(p, lines) }
                    },
                    initialVScroll = editorScrollFor(active.path)?.vertical ?: 0,
                    initialHScroll = editorScrollFor(active.path)?.horizontal ?: 0,
                    onScrollChange = { v, h ->
                        onEditorScrollChange(active.path, EditorScrollSnapshot(v, h))
                    },
                    jdkVersion = runtimeInfo?.first,
                    jdkVersionTooltip = runtimeInfo?.third?.let { "from $it" },
                    onJdkVersionClick = runtimeInfo?.let { (_, id, _) -> { onRuntimeClick?.invoke(id) } },
                    pageSettings = pageSettings,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FocusIndicator(visible: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(
                if (visible) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                else Color.Transparent,
            ),
    )
}

@Composable
private fun EmptyPanePlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No file open",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            fontSize = 12.sp,
        )
    }
}

@Composable
internal fun TitleBar(
    path: Path?,
    terminalOpen: Boolean,
    onTerminalToggle: () -> Unit,
    runState: RunConfigsState,
    activeFilePath: Path?,
    onSelectRunConfig: (String) -> Unit,
    runIsRunning: Boolean,
    onStartRun: () -> Unit,
    onStopRun: () -> Unit,
    onOpenRunDialog: () -> Unit,
    outputOpen: Boolean,
    onOutputToggle: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(36.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = PageIdentity.NAME,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "v${PageIdentity.VERSION}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(20.dp))
            Text(
                text = path?.toString() ?: "untitled",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            val currentFileTemplate = activeFilePath?.let { LanguageRunDefaults.forFile(it) }
            RunDropdown(
                state = runState,
                activeFilePath = activeFilePath,
                currentFileTemplate = currentFileTemplate,
                onSelect = onSelectRunConfig,
                onEdit = onOpenRunDialog,
            )
            Spacer(Modifier.width(4.dp))
            val canStart = !runIsRunning && when {
                runState.isCurrentFileActive -> currentFileTemplate != null
                else -> runState.active != null
            }
            TitleBarAction(
                label = "Run",
                enabled = canStart,
                onClick = onStartRun,
                shortcut = "Shift+F10",
                icon = { tint: Color -> PlayGlyph(tint = tint) },
                enabledIconTint = Color(0xFF4CAF50),
            )
            TitleBarAction(
                label = "Stop",
                enabled = runIsRunning,
                onClick = onStopRun,
                shortcut = "Ctrl+F2",
                icon = { tint: Color -> StopGlyph(tint = tint) },
                enabledIconTint = Color(0xFFE53935),
            )
            Spacer(Modifier.width(8.dp))
            TitleBarToggle(
                label = "Output",
                selected = outputOpen,
                onClick = onOutputToggle,
                icon = { tint: Color -> OutputGlyph(tint = tint) },
            )
            TitleBarToggle(
                label = "Terminal",
                selected = terminalOpen,
                onClick = onTerminalToggle,
                shortcut = "Ctrl+`",
                icon = { tint: Color -> TerminalGlyph(tint = tint) },
            )
        }
    }
}

@Composable
private fun RunDropdown(
    state: RunConfigsState,
    activeFilePath: Path?,
    currentFileTemplate: LanguageRunTemplate?,
    onSelect: (String) -> Unit,
    onEdit: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val active = state.active
    val activeFileName = activeFilePath?.fileName?.toString()
    val label = when {
        state.isCurrentFileActive -> activeFileName?.let { "Current file · $it" } ?: "Current file"
        active != null -> active.name.takeIf { it.isNotBlank() } ?: "Run config"
        else -> "Run config"
    }
    Box {
        Surface(
            color = Color.Transparent,
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(horizontal = 2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "▾",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
        CompactDropdown(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            val currentFileLabel = when {
                activeFileName == null -> "Current file (no file open)"
                currentFileTemplate == null -> "Current file (${activeFileName} — unsupported)"
                else -> "Current file · $activeFileName"
            }
            CompactMenuItem(
                label = currentFileLabel,
                onClick = {
                    expanded = false
                    onSelect(CURRENT_FILE_ID)
                },
            )
            if (state.configs.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                }
                for (cfg in state.configs) {
                    CompactMenuItem(
                        label = cfg.name.ifBlank { cfg.command },
                        onClick = {
                            expanded = false
                            onSelect(cfg.id)
                        },
                    )
                }
            }
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
            CompactMenuItem(
                label = "Edit configurations…",
                onClick = { expanded = false; onEdit() },
            )
        }
    }
}

@Composable
private fun TitleBarAction(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    shortcut: String? = null,
    icon: (@Composable (tint: Color) -> Unit)? = null,
    enabledIconTint: Color? = null,
) {
    val disabledTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    val fg = if (enabled) MaterialTheme.colorScheme.onSurface else disabledTint
    val iconTint = when {
        !enabled -> disabledTint
        enabledIconTint != null -> enabledIconTint
        else -> MaterialTheme.colorScheme.onSurface
    }
    val tooltipText = when {
        icon != null && !shortcut.isNullOrBlank() -> "$label · $shortcut"
        icon != null -> label
        shortcut.isNullOrBlank() -> ""
        else -> "$label · $shortcut"
    }
    GlassTooltip(text = tooltipText) {
        Surface(
            color = Color.Transparent,
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
                .padding(horizontal = 2.dp)
                .let { if (enabled) it.clickable { onClick() } else it },
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    icon(iconTint)
                }
            } else {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = fg,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun TitleBarToggle(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    shortcut: String? = null,
    icon: (@Composable (tint: Color) -> Unit)? = null,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent
    val fg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val tooltipText = when {
        icon != null && !shortcut.isNullOrBlank() -> "$label · $shortcut"
        icon != null -> label
        shortcut.isNullOrBlank() -> ""
        else -> "$label · $shortcut"
    }
    GlassTooltip(text = tooltipText) {
        Surface(
            color = bg,
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
                .clickable { onClick() }
                .padding(horizontal = 2.dp),
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    icon(fg)
                }
            } else {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = fg,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun PlayGlyph(tint: Color, size: Dp = 14.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.22f, h * 0.14f)
            lineTo(w * 0.88f, h * 0.5f)
            lineTo(w * 0.22f, h * 0.86f)
            close()
        }
        drawPath(p, color = tint)
    }
}

@Composable
private fun StopGlyph(tint: Color, size: Dp = 14.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val pad = this.size.width * 0.2f
        drawRoundRect(
            color = tint,
            topLeft = Offset(pad, pad),
            size = Size(this.size.width - pad * 2, this.size.height - pad * 2),
            cornerRadius = CornerRadius(this.size.width * 0.08f),
        )
    }
}

@Composable
private fun TerminalGlyph(tint: Color, size: Dp = 14.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val strokeW = w * 0.14f
        val cx = w * 0.32f
        val cy = h * 0.5f
        val cs = w * 0.22f
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(cx - cs, cy - cs * 0.95f)
            lineTo(cx + cs * 0.35f, cy)
            lineTo(cx - cs, cy + cs * 0.95f)
        }
        drawPath(
            p,
            color = tint,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = strokeW,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            ),
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.55f, h * 0.64f),
            size = Size(w * 0.34f, strokeW),
            cornerRadius = CornerRadius(strokeW / 2),
        )
    }
}

@Composable
private fun OutputGlyph(tint: Color, size: Dp = 14.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val lineH = h * 0.11f
        val gap = h * 0.16f
        val widths = listOf(0.82f, 0.58f, 0.82f)
        var y = h * 0.22f
        widths.forEach { wf ->
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.09f, y),
                size = Size(w * wf, lineH),
                cornerRadius = CornerRadius(lineH / 2),
            )
            y += lineH + gap
        }
    }
}

@Composable
internal fun ResizeHandle(onDeltaDp: (Dp) -> Unit) {
    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(6.dp)
            .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)))
            .hoverable(interactionSource)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dx ->
                    onDeltaDp(with(density) { dx.toDp() })
                }
            }
            .background(
                if (isHovered) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                else Color.Transparent,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(MaterialTheme.colorScheme.outline),
        )
    }
}

@Composable
private fun PaletteToast(palette: GlassPalette, visibleUntilMs: Long) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(visibleUntilMs) {
        while (System.currentTimeMillis() < visibleUntilMs) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(60)
        }
        now = System.currentTimeMillis()
    }
    if (now >= visibleUntilMs) return
    val label = "Glass · ${palette.name}"
    Box(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Surface(
            color = Glass.colors.surfaceRaised,
            contentColor = Glass.colors.text,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Glass.colors.outline),
            tonalElevation = 2.dp,
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                color = Glass.colors.text,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
