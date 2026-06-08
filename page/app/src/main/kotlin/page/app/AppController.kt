package page.app

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import page.app.domain.FileOperationsInteractor
import page.app.filetree.FileOpUndoController
import page.app.filetree.FileTreeActionExecutor
import page.app.filetree.FileTreeContextController
import page.app.filetree.FileTreeDropController
import page.app.filetree.RenameRemapController
import page.app.input.ShortcutDispatchController
import page.app.lsp.LspEditorInterconnector
import page.app.lsp.WorkspaceEditController
import page.app.mvi.AppState
import page.app.mvi.IdeEvent
import page.app.run.RunActionsController
import page.app.state.EditorWorkspaceState
import page.app.state.HistoryActionsController
import page.app.state.IdeAppState
import page.app.state.LayoutUiState
import page.app.state.WorkspaceState
import page.app.ui.CodeActionPreviewBinding
import page.app.ui.EditorSearchActions
import page.app.ui.FileTreePanelActions
import page.app.ui.RunPanelBinding
import page.app.ui.SettingsBinding
import page.app.ui.editor.CommandPaletteController
import page.app.ui.editor.EditorHistoryController
import page.app.ui.editor.EditorSearchController
import page.app.ui.editor.EditorTabController
import page.app.ui.editor.FileMenuController
import page.app.ui.editor.TabContextController
import page.app.ui.editor.TabOpenController
import page.app.utils.applyReplaceToBook
import page.editor.FileDocument
import page.editor.FileKinds
import page.editor.OpenTab
import page.editor.ProjectFileIndex
import page.editor.SplitOrientation
import page.editor.UndoGroupTracker
import page.language.LspRouter
import page.perf.PerfRegistry
import page.perf.StartupPhases
import page.runtime.*
import page.ui.GlassPalette
import page.workspace.*
import java.nio.file.Path

internal class AppController(
    private val editorWorkspace: EditorWorkspaceState,
    private val workspaceState: WorkspaceState,
    private val layoutUiState: LayoutUiState,
    private val appState: IdeAppState,
    private val fileOpHistory: FileOpHistory.Stack,
    private val terminalManagerProvider: () -> TerminalManager,
    private val runController: RunController,
    private val outputState: OutputPanelState,
    private val undoTracker: (PaneSide) -> UndoGroupTracker,
    private val appScope: CoroutineScope,
    private val lspRouterProvider: () -> LspRouter,
    private val todoProvider: () -> TodoController,
    private val exitApplication: () -> Unit,
    private val frameProvider: () -> java.awt.Frame?,
    private val copyToClipboard: (String) -> Unit,
    private val withFileTreeWatcherClosed: (() -> Unit) -> Unit,
    private val dispatch: (IdeEvent) -> Unit,
) {
    private val router: LspRouter get() = lspRouterProvider()
    private val todo: TodoController get() = todoProvider()

    private fun paneOf(side: PaneSide): EditorPaneState = editorWorkspace.paneOf(side)

    private fun setPane(side: PaneSide, value: EditorPaneState) = editorWorkspace.setPane(side, value)

    private fun mutatePane(side: PaneSide, transform: (EditorPaneState) -> EditorPaneState) =
        editorWorkspace.mutatePane(side, transform)

    private fun mutateFocused(transform: (EditorPaneState) -> EditorPaneState) =
        editorWorkspace.mutateFocused(transform)

    private fun focused(): EditorPaneState = editorWorkspace.focused()

    private val historyActionsController = HistoryActionsController(
        history = { appState.historyFile },
        setHistory = { appState.historyFile = it },
    )
    private val addRecentFile: (Path) -> Unit = { p -> historyActionsController.addRecentFile(p) }
    private val addSearchQuery: (String) -> Unit = { q -> historyActionsController.addSearchQuery(q) }
    private val addReplaceText: (String) -> Unit = { r -> historyActionsController.addReplaceText(r) }

    private val tabOpenController = TabOpenController(
        focused = { focused() },
        mutateFocused = { transform -> mutateFocused(transform) },
        addRecentFile = addRecentFile,
        scope = appScope,
    )
    val openInTab = tabOpenController::openInTab
    val openInTabAt = tabOpenController::openInTabAt

    private val fileOperationsInteractor = FileOperationsInteractor(
        readFileText = { p -> FileDocument.loadOrNull(p) },
        applyTextReplace = { p, text -> FileDocument.save(p, text) },
    )
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

    private val lspEditorInterconnector = LspEditorInterconnector(
        focused = { focused() },
        paneOf = { side -> paneOf(side) },
        setPane = { side, value -> setPane(side, value) },
        mutatePane = { side, transform -> mutatePane(side, transform) },
        openInTabAt = { picked, offset -> openInTabAt(picked, offset) },
        controllerFor = { p -> router.controllerFor(p) },
        applyExternalChange = { uri, text -> router.applyExternalChange(uri, text) },
        getReferences = { appState.referencesState },
        setReferences = { appState.referencesState = it },
    )
    val jumpToProblem = lspEditorInterconnector::jumpToProblem
    val requestReferences = lspEditorInterconnector::requestReferences
    val applyRename = lspEditorInterconnector::applyRename

    private val openWorkspaceFolder: (Path) -> Unit = { picked ->
        PerfRegistry.instance?.begin(StartupPhases.WORKSPACE_OPEN)
        workspaceState.rootDir = picked
        workspaceState.expanded = setOf(picked)
        appScope.launch {
            val chain = withContext(Dispatchers.IO) { page.editor.FileTree.singleChildChain(picked) }
            if (workspaceState.rootDir == picked) {
                workspaceState.expanded = setOf(picked) + chain
            }
            PerfRegistry.instance?.end(StartupPhases.WORKSPACE_OPEN)
        }
    }
    private val fileMenuController = FileMenuController(
        openInTab = openInTab,
        focused = { focused() },
        mutateFocused = { transform -> mutateFocused(transform) },
        paneOf = { side -> paneOf(side) },
        mutatePane = { side, transform -> mutatePane(side, transform) },
        didSave = { path, text -> router.controllerFor(path)?.didSave(path, text) },
        openWorkspaceFolder = openWorkspaceFolder,
    )
    val openFile = fileMenuController::openFile
    val saveFile = fileMenuController::saveFile
    val saveAllDirty = fileMenuController::saveAllDirty
    val openFolder = fileMenuController::openFolder
    val openFolderPath = fileMenuController::openFolderPath
    val newFile = fileMenuController::newFile

    private val fileTreeContextController = FileTreeContextController(
        ui = layoutUiState,
        rootDir = { workspaceState.rootDir },
        copyToClipboard = copyToClipboard,
    )
    val onCreateFileIn = fileTreeContextController::onCreateFileIn
    val onCreateFolderIn = fileTreeContextController::onCreateFolderIn
    val onRevealInFiles = fileTreeContextController::onRevealInFiles
    val onCopyPath = fileTreeContextController::onCopyPath
    val onCopyRelativePath = fileTreeContextController::onCopyRelativePath
    val onRenameEntry = fileTreeContextController::onRenameEntry
    val onPasteInto = fileTreeContextController::onPasteInto

    private val fileTreeDropController = FileTreeDropController(
        ui = layoutUiState,
        setDropResultToast = { appState.dropResultToast = it },
    )
    val showDropResultToast = fileTreeDropController::showDropResultToast
    val onDropPlanReceived = fileTreeDropController::onDropPlanReceived
    val onExternalDropReceived = fileTreeDropController::onExternalDropReceived
    val onDeleteEntry = fileTreeDropController::onDeleteEntry
    val onDeleteEntries = fileTreeDropController::onDeleteEntries

    private val renameRemapController = RenameRemapController(
        getExpanded = { workspaceState.expanded },
        setExpanded = { workspaceState.expanded = it },
        getTreeSelection = { workspaceState.treeSelection },
        setTreeSelection = { workspaceState.treeSelection = it },
        mutatePane = { side, transform -> mutatePane(side, transform) },
        primaryPane = { editorWorkspace.primaryPane },
        secondaryPane = { editorWorkspace.secondaryPane },
        controllerFor = { path -> router.controllerFor(path) },
        languageIdFor = { path -> router.languageIdFor(path) },
    )
    val remapTreeStateAfterRename = renameRemapController::remapTreeStateAfterRename
    val remapTabsAfterRename = renameRemapController::remapTabsAfterRename

    private val fileOpUndoController = FileOpUndoController(
        fileOpHistory = fileOpHistory,
        paneOf = { side -> paneOf(side) },
        setPane = { side, value -> setPane(side, value) },
        applyExternalChange = { uri, text -> router.applyExternalChange(uri, text) },
        remapTabsAfterRename = remapTabsAfterRename,
        remapTreeStateAfterRename = remapTreeStateAfterRename,
        onFileOpHistoryChanged = { appState.fileOpHistoryVersion++ },
        onTreeRevision = { workspaceState.treeRevision++ },
    )
    val readFileTextWithTabs = fileOpUndoController::readFileTextWithTabs
    val applyTextReplace = fileOpUndoController::applyTextReplace
    val onUndoFileOp = fileOpUndoController::onUndoFileOp
    val onRedoFileOp = fileOpUndoController::onRedoFileOp

    private val workspaceEditController = WorkspaceEditController(
        applyRename = applyRename,
        codeActionUri = { appState.codeActionUri },
        controllerForUri = { uri -> router.controllerForUri(uri) },
        rootDir = { workspaceState.rootDir },
        readFileText = readFileTextWithTabs,
        applyTextReplace = applyTextReplace,
    )
    val applyCodeAction = workspaceEditController::applyCodeAction
    val applyFolderPackageSync: (Path, Path, Map<String, String>) -> List<FileOpHistory.RewriteEntry> = { _, newFolder, packageMap ->
        workspaceEditController.applyFolderPackageSync(newFolder, packageMap)
    }
    val applySingleFileMoveSync = workspaceEditController::applySingleFileMoveSync

    val fileTreeActionExecutor = FileTreeActionExecutor(
        scope = appScope,
        getPasteDialog = { layoutUiState.pasteDialog },
        setPasteDialog = { layoutUiState.pasteDialog = it },
        getLargeCopyState = { layoutUiState.largeCopyState },
        setLargeCopyState = { layoutUiState.largeCopyState = it },
        rootDir = { workspaceState.rootDir },
        readFileText = readFileTextWithTabs,
        applyFolderPackageSync = applyFolderPackageSync,
        applySingleFileMoveSync = applySingleFileMoveSync,
        remapTabsAfterRename = { old, new -> remapTabsAfterRename(old, new) },
        remapTreeStateAfterRename = { old, new -> remapTreeStateAfterRename(old, new) },
        controllerFor = { p -> router.controllerFor(p) },
        withFileTreeWatcherClosed = { block -> withFileTreeWatcherClosed(block) },
        fileOpHistory = fileOpHistory,
        bumpHistoryVersion = { appState.fileOpHistoryVersion++ },
        bumpTreeRevision = { workspaceState.treeRevision++ },
        showInfoToast = { msg, undo -> showDropResultToast(msg, DropResultToastTone.Info, undo) },
        onUndoFileOp = { onUndoFileOp() },
    )

    val toggleExpanded: (Path, Boolean) -> Unit = { p, recursive ->
        when {
            p in workspaceState.expanded && !recursive -> {
                workspaceState.expanded = workspaceState.expanded - setOf(p)
            }
            else -> {
                workspaceState.expanded = workspaceState.expanded + setOf(p)
                appScope.launch {
                    val extra = withContext(Dispatchers.IO) {
                        if (recursive) page.editor.FileTree.descendantDirs(p)
                        else page.editor.FileTree.singleChildChain(p)
                    }
                    if (p in workspaceState.expanded) {
                        workspaceState.expanded = workspaceState.expanded + extra
                    }
                }
            }
        }
    }
    val isUnsavedText: (OpenTab) -> Boolean = { tab ->
        tab.dirty && FileKinds.classify(tab.path).isEditableAsText
    }
    private val saveTabAt: (PaneSide, Int) -> Unit = { side, idx ->
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

    private val tabController = EditorTabController(
        paneOf = { side -> paneOf(side) },
        mutatePane = { side, transform -> mutatePane(side, transform) },
        isOpenAnywhere = { p ->
            editorWorkspace.primaryPane.book.tabs.any { it.path == p } ||
                editorWorkspace.secondaryPane.book.tabs.any { it.path == p }
        },
        forgetScroll = { p -> editorWorkspace.editorScrollByPath = EditorScrollMemory.clear(editorWorkspace.editorScrollByPath, p) },
        didClose = { p -> router.controllerFor(p)?.didClose(p) },
        isUnsavedText = { tab -> isUnsavedText(tab) },
        setPendingClose = { appState.pendingClose = it },
        autoSaveOnClose = { appState.pageSettings.autoSave.onClose },
        saveTabAt = { side, idx -> saveTabAt(side, idx) },
        mergeSplitIfEmptyPane = { editorWorkspace.mergeSplitIfEmptyPane() },
    )
    val closeTabsUnderPath: (Path) -> Unit = { path -> tabController.closeTabsUnderPath(path) }
    val closeTabAt: (PaneSide, Int) -> Unit = { side, idx -> tabController.closeTabAt(side, idx) }
    val requestCloseTab: (PaneSide, Int) -> Unit = { side, idx -> tabController.requestCloseTab(side, idx) }
    private val closeManyOnPane: (PaneSide, List<Int>) -> Unit = { side, indices -> tabController.closeManyOnPane(side, indices) }
    private val requestBatchClose: (PaneSide, List<Int>) -> Unit = { side, indices -> tabController.requestBatchClose(side, indices) }

    private val tabContextController = TabContextController(
        paneOf = { side -> paneOf(side) },
        mutatePane = { side, transform -> mutatePane(side, transform) },
        focusedPane = { editorWorkspace.focusedPane },
        splitEnabled = { editorWorkspace.splitEnabled },
        setSplitEnabled = { editorWorkspace.splitEnabled = it },
        copyToClipboard = copyToClipboard,
        relativeTo = { path -> FileTreeActions.relativeTo(workspaceState.rootDir, path) },
        onRevealInFiles = onRevealInFiles,
        requestRename = { path -> layoutUiState.renameDialog = RenameEntryDialogState(path) },
        requestCloseTab = { side, idx -> requestCloseTab(side, idx) },
        requestBatchClose = { side, indices -> requestBatchClose(side, indices) },
        closeManyOnPane = { side, indices -> closeManyOnPane(side, indices) },
        moveTabAcross = { side, idx -> editorWorkspace.moveTabAcross(side, idx) },
    )
    val closeActiveTab: () -> Unit = { tabContextController.closeActiveTab() }
    val tabContextActionsFor = tabContextController::actionsFor

    private val anyDirty: () -> Boolean = {
        editorWorkspace.primaryPane.book.tabs.any(isUnsavedText) ||
            editorWorkspace.secondaryPane.book.tabs.any(isUnsavedText)
    }
    val requestExit: () -> Unit = {
        when {
            !anyDirty() -> exitApplication()
            appState.pageSettings.autoSave.onClose -> {
                saveAllDirty()
                exitApplication()
            }
            else -> appState.pendingClose = PendingClose.App
        }
    }

    private val searchController = EditorSearchController(
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

    private val historyController = EditorHistoryController(
        focusedPane = { editorWorkspace.focusedPane },
        paneOf = { side -> paneOf(side) },
        mutatePane = { side, transform -> mutatePane(side, transform) },
        undoTracker = { side -> undoTracker(side) },
        applyExternalChange = { uri, text -> router.applyExternalChange(uri, text) },
    )
    val doUndo = historyController::doUndo
    val doRedo = historyController::doRedo

    private val paletteController = CommandPaletteController(
        ui = layoutUiState,
        rootDir = { workspaceState.rootDir },
        focused = { focused() },
        controllerFor = { path -> router.controllerFor(path) },
        allDiagnosticsByUri = { router.allDiagnosticsByUri },
        jumpToProblem = jumpToProblem,
        applyRename = applyRename,
        onCodeActions = { list, uri, text, selected, open ->
            dispatch(IdeEvent.Internal.CodeActionsResult(list, uri, text, selected, open))
        },
    )
    val openQuickOpen = paletteController::openQuickOpen
    val jumpProblemRelative = paletteController::jumpProblemRelative
    val openDocumentSymbol = paletteController::openDocumentSymbol
    val openWorkspaceSymbol = paletteController::openWorkspaceSymbol
    val triggerFormat = paletteController::triggerFormat
    val triggerCodeAction = paletteController::triggerCodeAction

    val openFindInFiles: () -> Unit = {
        val root = workspaceState.rootDir
        if (root != null) {
            appState.findInFilesIndex = ProjectFileIndex.walk(root)
            appState.findInFiles = true
        }
    }
    val cyclePalette: () -> Unit = {
        val all = GlassPalette.values()
        appState.palette = all[(all.indexOf(appState.palette) + 1) % all.size]
        appState.paletteToastUntil = System.currentTimeMillis() + 1600L
        val root = workspaceState.rootDir
        if (root != null) {
            appState.workspaceFile = appState.workspaceFile.copy(palette = appState.palette.name)
            runCatching { WorkspaceStore.save(root, appState.workspaceFile) }
        } else {
            AppSettings.savePalette(appState.palette)
        }
    }

    private val runActionsController = RunActionsController(
        isRunning = { runController.isRunning },
        isCurrentFileActive = { appState.runState.isCurrentFileActive },
        buildConfigForActiveFile = {
            focused().book.active?.path?.let { LanguageRunDefaults.buildConfig(it, workspaceState.rootDir) }
        },
        activeRunConfig = { appState.runState.active },
        startRun = { cfg -> runController.start(cfg) },
        stopRun = { runController.stop() },
        autoSaveBeforeRun = { appState.pageSettings.autoSave.beforeRun },
        saveAllDirty = { saveAllDirty() },
        clearOutputOnRun = { appState.pageSettings.run.clearOutputOnRun },
        clearOutput = { runCatching { outputState.clear() } },
        openTerminalOnRun = { appState.pageSettings.run.openTerminalOnRun },
        terminalOpen = { layoutUiState.terminalOpen },
        setTerminalOpen = { layoutUiState.terminalOpen = it },
        ensureTerminalTab = {
            val tm = terminalManagerProvider()
            if (tm.tabs.isEmpty()) tm.newTab()
        },
        setOutputOpen = { layoutUiState.outputOpen = it },
        setRunDialogOpen = { appState.runDialogOpen = it },
    )
    val toggleTerminal: () -> Unit = { runActionsController.toggleTerminal() }
    val startActiveRun: () -> Unit = { runActionsController.startActiveRun() }
    val stopActiveRun: () -> Unit = { runActionsController.stopActiveRun() }
    val openRunDialog: () -> Unit = { runActionsController.openRunDialog() }
    val openSettings: () -> Unit = { appState.settingsDialogOpen = true }

    private val shortcutDispatchController = ShortcutDispatchController(
        hasSearch = { focused().search != null },
        cyclePalette = cyclePalette,
        openFolder = { frameProvider()?.let { openFolder(it) } },
        openFile = { frameProvider()?.let { openFile(it) } },
        openSettings = openSettings,
        saveFile = { frameProvider()?.let { saveFile(it) } },
        closeActiveTab = closeActiveTab,
        toggleProblems = { layoutUiState.problemsOpen = !layoutUiState.problemsOpen },
        toggleTodo = { layoutUiState.todoOpen = !layoutUiState.todoOpen },
        toggleFindInFiles = { if (appState.findInFiles) appState.findInFiles = false else openFindInFiles() },
        openSearch = openSearch,
        openReplace = openReplace,
        openQuickOpen = openQuickOpen,
        openWorkspaceSymbol = openWorkspaceSymbol,
        openDocumentSymbol = openDocumentSymbol,
        toggleSplitOrientation = {
            editorWorkspace.splitOrientation = if (editorWorkspace.splitOrientation == SplitOrientation.HORIZONTAL)
                SplitOrientation.VERTICAL else SplitOrientation.HORIZONTAL
        },
        toggleSplit = { editorWorkspace.splitEnabled = !editorWorkspace.splitEnabled },
        requestUndo = {
            val undoOp = fileOpHistory.peek()
            if (workspaceState.fileTreeFocused && undoOp != null) {
                appState.fileOpConfirm = FileOpConfirmState(isRedo = false, op = undoOp)
            } else {
                doUndo()
            }
        },
        requestRedo = {
            val redoOp = fileOpHistory.peekRedo()
            if (workspaceState.fileTreeFocused && redoOp != null) {
                appState.fileOpConfirm = FileOpConfirmState(isRedo = true, op = redoOp)
            } else {
                doRedo()
            }
        },
        triggerFormat = triggerFormat,
        triggerCodeAction = triggerCodeAction,
        activateAdjacentTab = { delta -> editorWorkspace.activateAdjacentTab(delta) },
        jumpProblemRelative = jumpProblemRelative,
        refreshTree = { workspaceState.treeRevision++ },
        closeSearch = { closeSearch(editorWorkspace.focusedPane) },
    )
    val handleShortcut: (KeyEvent) -> Boolean = { event -> shortcutDispatchController.handle(event) }

    fun fileTreePanelActions(): FileTreePanelActions = FileTreePanelActions(
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
        onDropRejected = { msg -> showDropResultToast(msg, DropResultToastTone.Warning, null) },
        onUndoFileOp = onUndoFileOp,
        canUndoFileOp = run {
            appState.fileOpHistoryVersion
            fileOpHistory.peek() != null
        },
        onTreeFocusChanged = { workspaceState.fileTreeFocused = it },
        pendingTreeFocusTick = appState.pendingTreeFocusTick,
    )

    fun editorSearchActions(): EditorSearchActions = EditorSearchActions(
        onQueryChange = onQueryChange,
        onReplaceChange = onReplaceChange,
        onToggleCase = onToggleCase,
        onSearchNext = onSearchNext,
        onSearchPrev = onSearchPrev,
        onReplace = onReplace,
        onReplaceAll = onReplaceAll,
        onSearchClose = closeSearch,
    )

    fun runPanelBinding(): RunPanelBinding = RunPanelBinding(
        runState = appState.runState,
        onSelectRunConfig = { id -> appState.runState = appState.runState.select(id) },
        onStartRun = startActiveRun,
        onStopRun = stopActiveRun,
        onOpenRunDialog = openRunDialog,
        runIsRunning = outputState.running,
        outputState = outputState,
        onOutputClear = { outputState.clear() },
    )

    fun codeActionPreviewBinding(): CodeActionPreviewBinding = CodeActionPreviewBinding(
        visible = appState.codeActionOpen,
        actions = appState.codeActionList,
        selected = appState.codeActionSelected,
        onSelectedChange = { dispatch(IdeEvent.CodeAction.SelectedChange(it)) },
        uri = appState.codeActionUri,
        text = appState.codeActionText,
        onApply = { action -> dispatch(IdeEvent.CodeAction.Apply(action)) },
        onDismiss = { dispatch(IdeEvent.CodeAction.Dismiss) },
    )

    fun handleEffect(event: IdeEvent, @Suppress("UNUSED_PARAMETER") prev: AppState, @Suppress("UNUSED_PARAMETER") next: AppState) {
        when (event) {
            is IdeEvent.CodeAction.Apply -> {
                if (event.action.isExecutable) applyCodeAction(event.action)
                frameProvider()?.requestFocus()
            }
            IdeEvent.CodeAction.Dismiss -> frameProvider()?.requestFocus()
            else -> Unit
        }
    }

    fun settingsBinding(): SettingsBinding = SettingsBinding(
        panelOpen = appState.settingsDialogOpen,
        onApply = { updated ->
            appState.pageSettings = updated
            AppSettings.saveAutoSave(updated.autoSave)
            AppSettings.saveEditor(updated.editor)
            AppSettings.saveLsp(updated.lsp)
            AppSettings.saveAutoInput(updated.autoInput)
            AppSettings.saveUi(updated.ui)
            AppSettings.saveRun(updated.run)
            appState.palette = updated.ui.palette
        },
        onPanelClose = { appState.settingsDialogOpen = false },
        onToggle = { appState.settingsDialogOpen = !appState.settingsDialogOpen },
    )

    fun onActiveTabChanged(side: PaneSide) {
        val pane = paneOf(side)
        val active = pane.book.active
        val newValue = if (active != null) {
            val caret = active.caret.coerceIn(0, active.text.length)
            TextFieldValue(active.text, TextRange(caret))
        } else TextFieldValue("")
        undoTracker(side).reset()
        setPane(
            side,
            pane.copy(
                editorValue = newValue,
                search = pane.search?.retarget(newValue.text),
            ),
        )
    }

    fun onSplitEnabledChanged() {
        if (!editorWorkspace.splitEnabled) editorWorkspace.focusedPane = PaneSide.PRIMARY
    }

    fun onFileDialogVisibilityChanged(anyOpen: Boolean) {
        if (anyOpen) {
            appState.hadFileDialog = true
        } else if (appState.hadFileDialog) {
            appState.hadFileDialog = false
            appState.pendingTreeFocusTick++
        }
    }

    fun onActivePathChanged(path: Path?) {
        if (path == null) return
        val ctrl = router.controllerFor(path)
        val langId = router.languageIdFor(path)
        if (ctrl != null && langId != null) {
            ctrl.didOpen(path, langId, focused().editorValue.text)
        }
    }

    fun onActiveTextChanged(path: Path?, text: String) {
        if (path == null) return
        router.controllerFor(path)?.didChange(path, text)
        todo.updateFile(path, text)
    }

    fun installApplyEditHandler(post: (() -> Unit) -> Unit) {
        router.applyEditHandler = { edit ->
            post { applyRename(edit) }
            true
        }
    }
}
