package page.app

import page.runtime.*
import page.workspace.*
import page.app.input.GlobalKeyDispatcher
import page.app.input.ShortcutDispatchController
import page.app.filetree.FileOpUndoController
import page.app.filetree.FileTreeActionExecutor
import page.app.filetree.FileTreeContextController
import page.app.filetree.FileTreeDropController
import page.app.filetree.LargeCopyDialogState
import page.app.filetree.RenameRemapController
import page.app.domain.FileOperationsInteractor
import page.app.filetree.PasteEntryDialogState
import page.app.lsp.LspEditorInterconnector
import page.app.lsp.WorkspaceEditController
import page.app.run.RunActionsController
import page.app.state.DebouncedSaver
import page.app.state.EditorWorkspaceState
import page.app.state.HistoryActionsController
import page.app.state.IdeAppState
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
import page.app.ui.editor.TabContextController
import page.app.ui.editor.TabOpenController
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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.input.key.KeyEvent
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
    val appState = remember { IdeAppState() }
    var sidebarWidth: Dp by layoutUiState::sidebarWidth
    var pendingClose: PendingClose? by appState::pendingClose
    var quickOpen by layoutUiState::quickOpen
    var quickOpenIndex by layoutUiState::quickOpenIndex
    var findInFiles by appState::findInFiles
    var findInFilesIndex by appState::findInFilesIndex
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
    var runState: RunConfigsState by appState::runState
    var runDialogOpen by appState::runDialogOpen
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
    var referencesState: ReferencesQueryState? by appState::referencesState
    var referencesHeight: Dp by layoutUiState::referencesHeight
    var createDialog: CreateEntryDialogState? by layoutUiState::createDialog
    var renameDialog: RenameEntryDialogState? by layoutUiState::renameDialog
    var deleteDialog: DeleteEntryDialogState? by layoutUiState::deleteDialog
    var pasteDialog: PasteEntryDialogState? by layoutUiState::pasteDialog
    var largeCopyState: LargeCopyDialogState? by layoutUiState::largeCopyState
    val largeCopyScope = rememberCoroutineScope()
    val fileOpHistory = remember { FileOpHistory.Stack() }
    var fileOpHistoryVersion by appState::fileOpHistoryVersion
    var fileTreeFocused by workspaceState::fileTreeFocused
    var fileOpConfirm: FileOpConfirmState? by appState::fileOpConfirm
    var pendingTreeFocusTick by appState::pendingTreeFocusTick
    var pageSettings by appState::pageSettings
    var palette by appState::palette
    LaunchedEffect(pageSettings.ui.sidebarWidth) {
        sidebarWidth = pageSettings.ui.sidebarWidth.dp
    }
    var paletteToastUntil by appState::paletteToastUntil
    val autoSaveOptions: AutoSaveOptions = pageSettings.autoSave
    var settingsDialogOpen by appState::settingsDialogOpen
    var dropResultToast: DropResultToastState? by appState::dropResultToast
    var documentSymbolOpen by layoutUiState::documentSymbolOpen
    var documentSymbolList by layoutUiState::documentSymbolList
    var documentSymbolUri by layoutUiState::documentSymbolUri
    var workspaceSymbolOpen by layoutUiState::workspaceSymbolOpen
    var codeActionOpen by appState::codeActionOpen
    var codeActionList by appState::codeActionList
    var codeActionUri by appState::codeActionUri
    var codeActionText by appState::codeActionText
    var codeActionSelected by appState::codeActionSelected
    var editorFocusVersion by appState::editorFocusVersion
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
    var hadFileDialog by appState::hadFileDialog
    LaunchedEffect(anyFileDialogOpen) {
        if (anyFileDialogOpen) {
            hadFileDialog = true
        } else if (hadFileDialog) {
            hadFileDialog = false
            pendingTreeFocusTick++
        }
    }

    var sessionLoaded by appState::sessionLoaded
    var foldByPath by editorWorkspace::foldByPath
    var historyFile by appState::historyFile
    var historyLoaded by appState::historyLoaded
    var workspaceFile by appState::workspaceFile
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
    var fileTreeWatcherEpoch by appState::fileTreeWatcherEpoch
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
    val historyActionsController = HistoryActionsController(
        history = { historyFile },
        setHistory = { historyFile = it },
    )
    val addRecentFile: (Path) -> Unit = { p -> historyActionsController.addRecentFile(p) }
    val addSearchQuery: (String) -> Unit = { q -> historyActionsController.addSearchQuery(q) }
    val addReplaceText: (String) -> Unit = { r -> historyActionsController.addReplaceText(r) }

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

    val tabOpenController = TabOpenController(
        focused = { focused() },
        mutateFocused = { transform -> mutateFocused(transform) },
        addRecentFile = addRecentFile,
    )
    val openInTab = tabOpenController::openInTab
    val openInTabAt = tabOpenController::openInTabAt

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
    val jumpToProblem = lspEditorInterconnector::jumpToProblem
    val requestReferences = lspEditorInterconnector::requestReferences
    val applyRename = lspEditorInterconnector::applyRename
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
    val openFile = fileMenuController::openFile
    val saveFile = fileMenuController::saveFile
    val saveAllDirty = fileMenuController::saveAllDirty
    val openFolder = fileMenuController::openFolder
    val openFolderPath = fileMenuController::openFolderPath
    val newFile = fileMenuController::newFile
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
    val onCreateFileIn = fileTreeContextController::onCreateFileIn
    val onCreateFolderIn = fileTreeContextController::onCreateFolderIn
    val onRevealInFiles = fileTreeContextController::onRevealInFiles
    val onCopyPath = fileTreeContextController::onCopyPath
    val onCopyRelativePath = fileTreeContextController::onCopyRelativePath
    val onRenameEntry = fileTreeContextController::onRenameEntry
    val onPasteInto = fileTreeContextController::onPasteInto
    val fileTreeDropController = FileTreeDropController(
        ui = layoutUiState,
        setDropResultToast = { dropResultToast = it },
    )
    val showDropResultToast = fileTreeDropController::showDropResultToast
    val onDropPlanReceived = fileTreeDropController::onDropPlanReceived
    val onExternalDropReceived = fileTreeDropController::onExternalDropReceived
    val onDeleteEntry = fileTreeDropController::onDeleteEntry
    val onDeleteEntries = fileTreeDropController::onDeleteEntries
    val renameRemapController = RenameRemapController(
        getExpanded = { expanded },
        setExpanded = { expanded = it },
        getTreeSelection = { treeSelection },
        setTreeSelection = { treeSelection = it },
        mutatePane = { side, transform -> mutatePane(side, transform) },
        primaryPane = { primaryPane },
        secondaryPane = { secondaryPane },
        controllerFor = { path -> currentLspRouter.controllerFor(path) },
        languageIdFor = { path -> currentLspRouter.languageIdFor(path) },
    )
    val remapTreeStateAfterRename = renameRemapController::remapTreeStateAfterRename
    val remapTabsAfterRename = renameRemapController::remapTabsAfterRename
    val fileOpUndoController = FileOpUndoController(
        fileOpHistory = fileOpHistory,
        paneOf = { side -> paneOf(side) },
        setPane = { side, value -> setPane(side, value) },
        applyExternalChange = { uri, text -> currentLspRouter.applyExternalChange(uri, text) },
        remapTabsAfterRename = remapTabsAfterRename,
        remapTreeStateAfterRename = remapTreeStateAfterRename,
        onFileOpHistoryChanged = { fileOpHistoryVersion++ },
        onTreeRevision = { treeRevision++ },
    )
    val readFileTextWithTabs = fileOpUndoController::readFileTextWithTabs
    val applyTextReplace = fileOpUndoController::applyTextReplace
    val onUndoFileOp = fileOpUndoController::onUndoFileOp
    val onRedoFileOp = fileOpUndoController::onRedoFileOp
    val workspaceEditController = WorkspaceEditController(
        applyRename = applyRename,
        codeActionUri = { codeActionUri },
        controllerForUri = { uri -> currentLspRouter.controllerForUri(uri) },
        rootDir = { rootDir },
        readFileText = readFileTextWithTabs,
        applyTextReplace = applyTextReplace,
    )
    val applyCodeAction = workspaceEditController::applyCodeAction
    val applyFolderPackageSync: (Path, Path, Map<String, String>) -> List<FileOpHistory.RewriteEntry> = { _, newFolder, packageMap ->
        workspaceEditController.applyFolderPackageSync(newFolder, packageMap)
    }
    val applySingleFileMoveSync = workspaceEditController::applySingleFileMoveSync
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
    val tabContextController = TabContextController(
        paneOf = { side -> paneOf(side) },
        mutatePane = { side, transform -> mutatePane(side, transform) },
        focusedPane = { focusedPane },
        setFocusedPane = { focusedPane = it },
        splitEnabled = { splitEnabled },
        setSplitEnabled = { splitEnabled = it },
        copyToClipboard = copyToClipboard,
        relativeTo = { path -> FileTreeActions.relativeTo(rootDir, path) },
        onRevealInFiles = onRevealInFiles,
        requestRename = { path -> renameDialog = RenameEntryDialogState(path) },
        requestCloseTab = { side, idx -> requestCloseTab(side, idx) },
        requestBatchClose = { side, indices -> requestBatchClose(side, indices) },
        closeManyOnPane = { side, indices -> closeManyOnPane(side, indices) },
        moveTabAcross = { side, idx -> editorWorkspace.moveTabAcross(side, idx) },
    )
    val closeActiveTab: () -> Unit = { tabContextController.closeActiveTab() }
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
    val openSearch = searchController::openSearch
    val openReplace = searchController::openReplace
    val closeSearch = searchController::closeSearch
    val onQueryChange = searchController::onQueryChange
    val onToggleCase = searchController::onToggleCase
    val onSearchNext = searchController::onSearchNext
    val onSearchPrev = searchController::onSearchPrev
    val onReplaceChange = searchController::onReplaceChange
    val onReplace = searchController::onReplace
    val onReplaceAll = searchController::onReplaceAll

    val historyController = EditorHistoryController(
        focusedPane = { focusedPane },
        paneOf = { side -> paneOf(side) },
        mutatePane = { side, transform -> mutatePane(side, transform) },
        undoTracker = { side -> undoTracker(side) },
        applyExternalChange = { uri, text -> currentLspRouter.applyExternalChange(uri, text) },
    )
    val doUndo = historyController::doUndo
    val doRedo = historyController::doRedo


    val tabContextActionsFor = tabContextController::actionsFor

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
    val openQuickOpen = paletteController::openQuickOpen
    val jumpProblemRelative = paletteController::jumpProblemRelative
    val openDocumentSymbol = paletteController::openDocumentSymbol
    val openWorkspaceSymbol = paletteController::openWorkspaceSymbol
    val triggerFormat = paletteController::triggerFormat
    val triggerCodeAction = paletteController::triggerCodeAction
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
    val shortcutDispatchController = ShortcutDispatchController(
        hasSearch = { focused().search != null },
        cyclePalette = cyclePalette,
        openFolder = { frameRef.value?.let { openFolder(it) } },
        openFile = { frameRef.value?.let { openFile(it) } },
        openSettings = { settingsDialogOpen = true },
        saveFile = { frameRef.value?.let { saveFile(it) } },
        closeActiveTab = closeActiveTab,
        toggleProblems = { problemsOpen = !problemsOpen },
        toggleTodo = { todoOpen = !todoOpen },
        toggleFindInFiles = { if (findInFiles) findInFiles = false else openFindInFiles() },
        openSearch = openSearch,
        openReplace = openReplace,
        openQuickOpen = openQuickOpen,
        openWorkspaceSymbol = openWorkspaceSymbol,
        openDocumentSymbol = openDocumentSymbol,
        toggleSplitOrientation = {
            splitOrientation = if (splitOrientation == SplitOrientation.HORIZONTAL)
                SplitOrientation.VERTICAL else SplitOrientation.HORIZONTAL
        },
        toggleSplit = { splitEnabled = !splitEnabled },
        requestUndo = {
            val undoOp = fileOpHistory.peek()
            if (fileTreeFocused && undoOp != null) {
                fileOpConfirm = FileOpConfirmState(isRedo = false, op = undoOp)
            } else {
                doUndo()
            }
        },
        requestRedo = {
            val redoOp = fileOpHistory.peekRedo()
            if (fileTreeFocused && redoOp != null) {
                fileOpConfirm = FileOpConfirmState(isRedo = true, op = redoOp)
            } else {
                doRedo()
            }
        },
        triggerFormat = triggerFormat,
        triggerCodeAction = triggerCodeAction,
        activateAdjacentTab = { delta -> editorWorkspace.activateAdjacentTab(delta) },
        jumpProblemRelative = jumpProblemRelative,
        refreshTree = { treeRevision++ },
        closeSearch = { closeSearch(focusedPane) },
    )
    val handleShortcut: (KeyEvent) -> Boolean = { event -> shortcutDispatchController.handle(event) }
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
        val runActionsController = RunActionsController(
            isRunning = { runController.isRunning },
            isCurrentFileActive = { runState.isCurrentFileActive },
            buildConfigForActiveFile = {
                focused().book.active?.path?.let { LanguageRunDefaults.buildConfig(it, rootDir) }
            },
            activeRunConfig = { runState.active },
            startRun = { cfg -> runController.start(cfg) },
            stopRun = { runController.stop() },
            autoSaveBeforeRun = { autoSaveOptions.beforeRun },
            saveAllDirty = { saveAllDirty() },
            clearOutputOnRun = { pageSettings.run.clearOutputOnRun },
            clearOutput = { runCatching { outputState.clear() } },
            openTerminalOnRun = { pageSettings.run.openTerminalOnRun },
            terminalOpen = { terminalOpen },
            setTerminalOpen = { terminalOpen = it },
            ensureTerminalTab = { if (terminalManager.tabs.isEmpty()) terminalManager.newTab() },
            setOutputOpen = { outputOpen = it },
            setRunDialogOpen = { runDialogOpen = it },
        )
        val toggleTerminal: () -> Unit = { runActionsController.toggleTerminal() }
        val toggleTerminalRef = rememberUpdatedState(toggleTerminal)
        val startActiveRun: () -> Unit = { runActionsController.startActiveRun() }
        val stopActiveRun: () -> Unit = { runActionsController.stopActiveRun() }
        val openRunDialog: () -> Unit = { runActionsController.openRunDialog() }
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
            val dispatcher = GlobalKeyDispatcher(
                isWindowFocused = { frame.isFocused },
                codeActionOpen = { codeActionOpenRef.value },
                codeActionList = { codeActionListRef.value },
                codeActionSelected = { codeActionSelectedRef.value },
                setCodeActionOpen = { codeActionOpen = it },
                setCodeActionSelected = { codeActionSelected = it },
                applyCodeAction = { action -> applyCodeAction(action) },
                requestEditorRefocus = {
                    frameRef.value?.requestFocus()
                    editorFocusVersion += 1
                },
                openSettings = { openSettingsRef.value() },
                toggleTerminal = { toggleTerminalRef.value() },
                openWorkspaceSymbol = { openWsRef.value() },
                openDocumentSymbol = { openDocRef.value() },
                triggerFormat = { triggerFormatRef.value() },
                triggerCodeAction = { triggerCodeActionRef.value() },
                startActiveRun = { startActiveRunRef.value() },
                stopActiveRun = { stopActiveRunRef.value() },
                openRunDialog = { openRunDialogRef.value() },
            )
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
                } else CompositionLocalProvider(LocalPageSettings provides pageSettings) {
                  IdeMainLayout(
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
                    onToggleSettings = { settingsDialogOpen = !settingsDialogOpen },
                  )
                }
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

internal data class FileOpConfirmState(
    val isRedo: Boolean,
    val op: FileOpHistory.Op,
)
