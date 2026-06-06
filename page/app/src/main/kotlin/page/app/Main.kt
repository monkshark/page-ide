package page.app

import page.runtime.*
import page.workspace.*
import page.app.input.GlobalKeyDispatcher
import page.app.filetree.LargeCopyDialogState
import page.app.filetree.rememberFileTreeWatcherController
import page.app.filetree.PasteEntryDialogState
import page.app.state.DebouncedSaver
import page.app.state.EditorWorkspaceState
import page.app.state.IdeAppState
import page.app.state.LayoutUiState
import page.app.state.WorkspaceState
import page.app.mvi.IdeDispatcher
import page.app.mvi.IdeEffectHandler
import page.app.mvi.IdeEvent
import page.app.mvi.IdeStore
import page.app.ui.IdeMainLayout
import page.app.ui.PaletteToast
import page.app.ui.dialog.FileTreeCreateDialog
import page.app.ui.dialog.FileTreePasteDialog
import page.app.ui.dialog.FileTreeRenameDeleteDialogs
import page.app.ui.dialog.NavigationPickerDialogs
import page.app.ui.dialog.PendingCloseDialog
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
    val ideStore = remember { IdeStore() }
    val workspaceState = remember { WorkspaceState(appScope, ideStore) }
    var rootDir by workspaceState::rootDir
    var expanded by workspaceState::expanded
    var treeSelection by workspaceState::treeSelection
    var treeRevision by workspaceState::treeRevision
    var editorScrollByPath by editorWorkspace::editorScrollByPath
    val layoutUiState = remember { LayoutUiState(ideStore) }
    val onIdeEvent = remember { IdeDispatcher(ideStore, IdeEffectHandler()).onEvent }
    val appState = remember { IdeAppState(ideStore) }
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
    val currentTerminalManager by rememberUpdatedState(terminalManager)
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
    val currentTodo by rememberUpdatedState(todo)
    val todoItems by todo.items

    fun paneOf(side: PaneSide): EditorPaneState = editorWorkspace.paneOf(side)

    fun setPane(side: PaneSide, value: EditorPaneState) = editorWorkspace.setPane(side, value)

    fun mutatePane(side: PaneSide, transform: (EditorPaneState) -> EditorPaneState) =
        editorWorkspace.mutatePane(side, transform)

    fun mutateFocused(transform: (EditorPaneState) -> EditorPaneState) =
        editorWorkspace.mutateFocused(transform)

    fun focused(): EditorPaneState = editorWorkspace.focused()

    val fileTreeWatcher = rememberFileTreeWatcherController()
    fileTreeWatcher.WatchLoop(rootDir = rootDir, expanded = expanded, onTreeChanged = { onIdeEvent(IdeEvent.Tree.BumpRevision) })
    val withFileTreeWatcherClosed: (() -> Unit) -> Unit = { block -> fileTreeWatcher.withClosed(block) }
    val copyToClipboard: (String) -> Unit = { text ->
        runCatching {
            val selection = java.awt.datatransfer.StringSelection(text)
            java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
        }
    }
    val frameRef = remember { mutableStateOf<java.awt.Frame?>(null) }
    val app = remember {
        AppController(
            editorWorkspace = editorWorkspace,
            workspaceState = workspaceState,
            layoutUiState = layoutUiState,
            appState = appState,
            fileOpHistory = fileOpHistory,
            terminalManagerProvider = { currentTerminalManager },
            runController = runController,
            outputState = outputState,
            undoTracker = ::undoTracker,
            largeCopyScope = largeCopyScope,
            lspRouterProvider = { currentLspRouter },
            todoProvider = { currentTodo },
            exitApplication = { exitApplication() },
            frameProvider = { frameRef.value },
            copyToClipboard = copyToClipboard,
            withFileTreeWatcherClosed = withFileTreeWatcherClosed,
        )
    }
    val sessionCoordinator = remember {
        SessionCoordinator(
            editorWorkspace = editorWorkspace,
            layoutUiState = layoutUiState,
            workspaceState = workspaceState,
            terminalManagerProvider = { currentTerminalManager },
        )
    }

    LaunchedEffect(primaryPane.book.activeIndex, primaryPane.book.tabs.size) {
        app.onActiveTabChanged(PaneSide.PRIMARY)
    }

    LaunchedEffect(secondaryPane.book.activeIndex, secondaryPane.book.tabs.size) {
        app.onActiveTabChanged(PaneSide.SECONDARY)
    }

    LaunchedEffect(splitEnabled) {
        app.onSplitEnabledChanged()
    }

    val anyFileDialogOpen = createDialog != null || renameDialog != null || deleteDialog != null ||
        pasteDialog != null || largeCopyState != null || fileOpConfirm != null
    LaunchedEffect(anyFileDialogOpen) {
        app.onFileDialogVisibilityChanged(anyFileDialogOpen)
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
        sessionCoordinator.restore(root)
        sessionLoaded = true
    }

    val onFoldChange: (Path, Set<Int>) -> Unit = { path, lines ->
        val key = path.toString()
        foldByPath = if (lines.isEmpty()) foldByPath - key else foldByPath + (key to lines)
    }
    val foldedLinesFor: (Path?) -> Set<Int> = { p ->
        p?.let { foldByPath[it.toString()] } ?: emptySet()
    }

    val sessionSnapshot = sessionCoordinator.snapshot()
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
    val focusedActivePath = focused().book.active?.path
    val focusedActiveText = focused().editorValue.text
    LaunchedEffect(focusedActivePath) {
        app.onActivePathChanged(focusedActivePath)
    }
    LaunchedEffect(focusedActivePath, focusedActiveText) {
        app.onActiveTextChanged(focusedActivePath, focusedActiveText)
    }

    val openInTab = app.openInTab
    val openInTabAt = app.openInTabAt
    val onReplaceInFiles = app.onReplaceInFiles
    val jumpToProblem = app.jumpToProblem
    val requestReferences = app.requestReferences
    val applyRename = app.applyRename
    LaunchedEffect(lspRouter) {
        app.installApplyEditHandler { block -> java.awt.EventQueue.invokeLater(block) }
    }

    val saveAllDirty = app.saveAllDirty
    val openFolder = app.openFolder
    val openFolderPath = app.openFolderPath
    val newFile = app.newFile
    val remapTreeStateAfterRename = app.remapTreeStateAfterRename
    val remapTabsAfterRename = app.remapTabsAfterRename
    val readFileTextWithTabs = app.readFileTextWithTabs
    val onUndoFileOp = app.onUndoFileOp
    val onRedoFileOp = app.onRedoFileOp
    val applyCodeAction = app.applyCodeAction
    val applyFolderPackageSync = app.applyFolderPackageSync
    val fileTreeActionExecutor = app.fileTreeActionExecutor
    val isUnsavedText = app.isUnsavedText
    val closeTabsUnderPath = app.closeTabsUnderPath
    val closeTabAt = app.closeTabAt
    val requestCloseTab = app.requestCloseTab
    val requestExit = app.requestExit
    val tabContextActionsFor = app.tabContextActionsFor
    val openDocumentSymbol = app.openDocumentSymbol
    val openWorkspaceSymbol = app.openWorkspaceSymbol
    val triggerFormat = app.triggerFormat
    val triggerCodeAction = app.triggerCodeAction
    val toggleTerminal = app.toggleTerminal
    val startActiveRun = app.startActiveRun
    val stopActiveRun = app.stopActiveRun
    val openRunDialog = app.openRunDialog
    val openSettings = app.openSettings
    val handleShortcut = app.handleShortcut
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
        val toggleTerminalRef = rememberUpdatedState(toggleTerminal)
        val startActiveRunRef = rememberUpdatedState(startActiveRun)
        val stopActiveRunRef = rememberUpdatedState(stopActiveRun)
        val openRunDialogRef = rememberUpdatedState(openRunDialog)
        val saveAllDirtyRef = rememberUpdatedState(saveAllDirty)
        val autoSaveOptionsRef = rememberUpdatedState(autoSaveOptions)
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
                    onIdeEvent(IdeEvent.Chrome.BumpEditorFocus)
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
                    onEvent = onIdeEvent,
                    lspRouter = currentLspRouter,
                    onCloseTab = { side, index -> requestCloseTab(side, index) },
                    fileTree = app.fileTreePanelActions(),
                    search = app.editorSearchActions(),
                    onWindowShortcut = handleShortcut,
                    onJumpToProblem = jumpToProblem,
                    onApplyRename = applyRename,
                    todoItems = todoItems,
                    terminalManager = terminalManager,
                    onTerminalToggle = toggleTerminal,
                    run = app.runPanelBinding(),
                    referencesState = referencesState,
                    onRequestReferences = requestReferences,
                    onReferencesClose = { referencesState = null },
                    linePreviewFor = { uri, line -> currentLspRouter.controllerForUri(uri)?.linePreviewFor(uri, line) },
                    foldedLinesFor = foldedLinesFor,
                    onFoldChange = onFoldChange,
                    editorFocusVersion = editorFocusVersion,
                    codeAction = app.codeActionPreviewBinding(),
                    editorScrollFor = { p -> editorScrollByPath[p] },
                    onEditorScrollChange = { p, snap ->
                        editorScrollByPath = EditorScrollMemory.put(editorScrollByPath, p, snap)
                    },
                    tabContextActionsFor = { side -> tabContextActionsFor(side) },
                    settings = app.settingsBinding(),
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
        onEditorFocusBump = { onIdeEvent(IdeEvent.Chrome.BumpEditorFocus) },
    )

    if (runDialogOpen) {
        RunConfigDialog(
            state = runState,
            workspaceRoot = rootDir,
            onSave = { saved ->
                runState = saved
                onIdeEvent(IdeEvent.Chrome.CloseRunDialog)
            },
            onDismiss = { onIdeEvent(IdeEvent.Chrome.CloseRunDialog) },
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
