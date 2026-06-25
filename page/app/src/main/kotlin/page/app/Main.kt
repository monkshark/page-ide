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
import page.atlas.analyzer.ImportGraphProvider
import page.atlas.graph.GraphSlice
import page.app.mvi.IdeDispatcher
import page.app.mvi.IdeEffectHandler
import page.app.mvi.IdeEvent
import page.app.mvi.IdeStore
import page.app.ui.IdeMainLayout
import page.app.ui.PaletteToast
import page.app.ui.dialog.AppDialogs
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
import androidx.compose.runtime.SideEffect
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import page.lsp.CodeActionEntry
import page.lsp.RenameApply
import page.lsp.RenameFileChange
import page.lsp.RenameWorkspaceEdit
import page.lsp.pickSingleOtherReference
import page.perf.PerfRegistry
import page.perf.StartupKind
import page.perf.StartupPhases
import page.perf.UiFreezeWatchdog
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
    UiFreezeWatchdog.start()
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
    val ideStore = remember { IdeStore() }
    val editorWorkspace = remember { EditorWorkspaceState(undoTracker = ::undoTracker, store = ideStore) }
    var primaryPane by editorWorkspace::primaryPane
    var secondaryPane by editorWorkspace::secondaryPane
    var focusedPane by editorWorkspace::focusedPane
    val appScope = rememberCoroutineScope()
    val workspaceState = remember { WorkspaceState(appScope, ideStore) }
    var rootDir by workspaceState::rootDir
    var expanded by workspaceState::expanded
    var treeSelection by workspaceState::treeSelection
    var treeRevision by workspaceState::treeRevision
    var editorScrollByPath by editorWorkspace::editorScrollByPath
    val layoutUiState = remember { LayoutUiState(ideStore) }
    val ideEffectHandler = remember { IdeEffectHandler() }
    val onIdeEvent = remember { IdeDispatcher(ideStore, ideEffectHandler).onEvent }
    val appState = remember { IdeAppState(ideStore) }
    var sidebarWidth: Dp by layoutUiState::sidebarWidth
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
            appScope = appScope,
            lspRouterProvider = { currentLspRouter },
            todoProvider = { currentTodo },
            exitApplication = { exitApplication() },
            frameProvider = { frameRef.value },
            copyToClipboard = copyToClipboard,
            withFileTreeWatcherClosed = withFileTreeWatcherClosed,
            dispatch = onIdeEvent,
        ).also { ideEffectHandler.bind(it::handleEffect) }
    }
    val atlasMapView = remember { page.atlas.render.MapViewState() }
    val atlasView = remember { page.atlas.render.AtlasViewState() }
    val atlasOverviewState = remember { page.atlas.render.OverviewViewState() }
    val sessionCoordinator = remember {
        SessionCoordinator(
            editorWorkspace = editorWorkspace,
            layoutUiState = layoutUiState,
            workspaceState = workspaceState,
            terminalManagerProvider = { currentTerminalManager },
            atlasMapView = atlasMapView,
            atlasView = atlasView,
            atlasOverviewState = atlasOverviewState,
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
        onIdeEvent(IdeEvent.EditorLayout.FoldChanged(path.toString(), lines))
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
                    val loaded = if (root == null) RunConfigsState()
                    else runCatching { RunConfigStore.load(root) }.getOrDefault(RunConfigsState())
                    onIdeEvent(IdeEvent.Internal.RunConfigsChanged(loaded))
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

    val atlasProvider = remember(rootDir) { rootDir?.let { ImportGraphProvider(it) } }
    var atlasSlice by remember { mutableStateOf(GraphSlice.EMPTY) }
    var atlasLoadProgress by remember { mutableStateOf<Float?>(null) }
    val atlasOpen = layoutUiState.atlasOpen
    val atlasProjectMode = layoutUiState.atlasProjectMode
    val atlasViewTab = layoutUiState.atlasViewTab
    val atlasExpanded = layoutUiState.expandedPanel == page.app.mvi.ExpandedPanel.ATLAS
    LaunchedEffect(atlasOpen, atlasExpanded, atlasProjectMode, atlasViewTab, atlasProvider, focusedActivePath, focusedActiveText) {
        val projectScope = atlasViewTab == page.atlas.render.AtlasViewTab.RELATIONS ||
            atlasViewTab == page.atlas.render.AtlasViewTab.ANALYSIS
        if ((!atlasOpen && !atlasExpanded) || atlasProvider == null ||
            (!projectScope && !atlasProjectMode && focusedActivePath == null)
        ) {
            atlasSlice = GraphSlice.EMPTY
            atlasLoadProgress = null
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(300)
        val reportProgress = atlasSlice.nodes.isEmpty()
        val onProgress: (Int, Int) -> Unit = { done, total ->
            if (reportProgress && total > 0) atlasLoadProgress = done.toFloat() / total
        }
        atlasSlice = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                when {
                    projectScope -> {
                        val project = atlasProvider.nodesForProject(focusedActivePath, focusedActiveText, onProgress)
                        val file = focusedActivePath
                            ?.let { atlasProvider.nodesForFile(it, focusedActiveText) }
                            ?: GraphSlice.EMPTY
                        page.atlas.graph.GraphQueries.merge(project, file)
                    }
                    atlasProjectMode -> atlasProvider.nodesForProject(focusedActivePath, focusedActiveText, onProgress)
                    else -> atlasProvider.nodesForFile(focusedActivePath!!, focusedActiveText)
                }
            }.getOrElse { t ->
                println("[atlas] slice computation failed: ${t::class.simpleName}: ${t.message}")
                GraphSlice.EMPTY
            }
        }
        atlasLoadProgress = null
    }
    val vcsProvider = remember(rootDir) { rootDir?.let { page.git.GitStatusProvider(it) } }
    var atlasVcsMarks by remember { mutableStateOf<Map<String, page.atlas.render.VcsMark>>(emptyMap()) }
    LaunchedEffect(vcsProvider, atlasOpen, atlasExpanded, atlasSlice) {
        if ((!atlasOpen && !atlasExpanded) || vcsProvider == null || atlasSlice.nodes.isEmpty()) {
            atlasVcsMarks = emptyMap()
            return@LaunchedEffect
        }
        atlasVcsMarks = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { vcsMarksFrom(vcsProvider.statuses()) }.getOrDefault(emptyMap())
        }
    }
    var atlasFileRole by remember { mutableStateOf<page.atlas.graph.FileRole?>(null) }
    LaunchedEffect(atlasProvider, focusedActivePath) {
        atlasFileRole = null
        val path = focusedActivePath ?: return@LaunchedEffect
        if (atlasProvider == null) return@LaunchedEffect
        kotlinx.coroutines.delay(700)
        atlasFileRole = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { atlasProvider.fileRole(path) }.getOrNull()
        }
    }
    var atlasProjectCycles by remember { mutableStateOf<List<List<page.atlas.graph.GraphNode>>>(emptyList()) }
    LaunchedEffect(atlasProvider, focusedActivePath) {
        if (atlasProvider == null) {
            atlasProjectCycles = emptyList()
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(700)
        atlasProjectCycles = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { atlasProvider.projectCycles() }.getOrDefault(emptyList())
        }
    }
    val atlasActiveId = remember(focusedActivePath) {
        focusedActivePath?.toAbsolutePath()?.normalize()?.toString()
    }
    LaunchedEffect(layoutUiState.atlasFollowActive, atlasOpen, atlasExpanded, atlasActiveId) {
        if (!layoutUiState.atlasFollowActive || (!atlasOpen && !atlasExpanded)) return@LaunchedEffect
        val id = atlasActiveId ?: return@LaunchedEffect
        atlasMapView.focusCenterId = id
    }
    val focusInAtlas: (java.nio.file.Path) -> Unit = { path ->
        val id = path.toAbsolutePath().normalize().toString()
        atlasView.pendingFocusId = id
        atlasMapView.focusCenterId = id
        onIdeEvent(IdeEvent.Panel.FocusInAtlas)
    }
    val focusActiveInAtlas: () -> Unit = focusActiveInAtlas@{
        val path = focusedActivePath ?: return@focusActiveInAtlas
        focusInAtlas(path)
    }
    SideEffect { app.onFocusActiveInAtlas = focusActiveInAtlas }

    val openInTab = app.openInTab
    val openInTabAt = app.openInTabAt
    val onReplaceInFiles = app.onReplaceInFiles
    val jumpToProblem = app.jumpToProblem
    val atlasCallsView = remember { page.atlas.render.AtlasViewState() }
    var atlasCallsSlice by remember { mutableStateOf(GraphSlice.EMPTY) }
    var atlasCallsSession by remember { mutableStateOf<page.atlas.graph.SymbolGraphSession?>(null) }
    val atlasCallsMutex = remember { Mutex() }
    val showCallGraph: (java.nio.file.Path, Int, Int) -> Unit = { path, line, character ->
        val ctrl = currentLspRouter.controllerFor(path)
        if (ctrl != null) appScope.launch {
            val started = withContext(Dispatchers.IO) {
                atlasCallsMutex.withLock {
                    runCatching {
                        val item = ctrl.prepareCallHierarchy(path, line, character)
                            .get(10, java.util.concurrent.TimeUnit.SECONDS)
                            .firstOrNull() ?: return@runCatching null
                        val session = page.atlas.graph.SymbolGraphSession(LspCallHierarchySource(ctrl))
                        session to session.start(item.toSymbolSpec())
                    }.getOrElse { t ->
                        println("[atlas] call graph failed: ${t::class.simpleName}: ${t.message}")
                        null
                    }
                }
            }
            if (started != null) {
                val (session, slice) = started
                atlasCallsSession = session
                atlasCallsView.pendingFocusId = session.rootId
                atlasCallsSlice = slice
                onIdeEvent(IdeEvent.Panel.ShowAtlasCalls)
            }
        }
    }
    val onAtlasCallsExpand: (String) -> Unit = { nodeId ->
        val session = atlasCallsSession
        if (session != null && session.canExpand(nodeId)) appScope.launch {
            val slice = withContext(Dispatchers.IO) {
                atlasCallsMutex.withLock { runCatching { session.expand(nodeId) }.getOrNull() }
            }
            if (slice != null && atlasCallsSession === session) {
                atlasCallsView.pendingFocusId = nodeId
                atlasCallsSlice = slice
            }
        }
    }
    val onAtlasCallsOpen: (String) -> Unit = { nodeId ->
        atlasCallsSession?.symbolAt(nodeId)?.let { spec ->
            runCatching { java.nio.file.Paths.get(java.net.URI(spec.uri)) }.getOrNull()
                ?.let { p -> jumpToProblem(p, spec.line, spec.character) }
        }
    }
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
                    fileTree = app.fileTreePanelActions().copy(onOpenInAtlas = focusInAtlas),
                    search = app.editorSearchActions(),
                    onWindowShortcut = handleShortcut,
                    onJumpToProblem = { path, line, character ->
                        onIdeEvent(IdeEvent.Lsp.JumpToProblem(path, line, character))
                    },
                    onApplyRename = { edit -> onIdeEvent(IdeEvent.Lsp.ApplyRename(edit)) },
                    todoItems = todoItems,
                    terminalManager = terminalManager,
                    onTerminalToggle = toggleTerminal,
                    run = app.runPanelBinding(),
                    referencesState = referencesState,
                    onRequestReferences = { path, line, character, symbol ->
                        onIdeEvent(IdeEvent.Lsp.RequestReferences(path, line, character, symbol))
                    },
                    onReferencesClose = { onIdeEvent(IdeEvent.Lsp.ReferencesClose) },
                    linePreviewFor = { uri, line -> currentLspRouter.controllerForUri(uri)?.linePreviewFor(uri, line) },
                    foldedLinesFor = foldedLinesFor,
                    onFoldChange = onFoldChange,
                    editorFocusVersion = editorFocusVersion,
                    codeAction = app.codeActionPreviewBinding(),
                    editorScrollFor = { p -> editorScrollByPath[p] },
                    onEditorScrollChange = { p, snap ->
                        onIdeEvent(IdeEvent.EditorScroll.Changed(p, snap))
                    },
                    tabContextActionsFor = { side -> tabContextActionsFor(side).copy(onOpenInAtlas = focusInAtlas) },
                    settings = app.settingsBinding(),
                    atlasSlice = atlasSlice,
                    atlasMapView = atlasMapView,
                    atlasView = atlasView,
                    atlasOverviewState = atlasOverviewState,
                    atlasLoadProgress = atlasLoadProgress,
                    atlasFileRole = atlasFileRole,
                    atlasProjectCycles = atlasProjectCycles,
                    onAtlasFocusActive = focusActiveInAtlas,
                    atlasVcsMarks = atlasVcsMarks,
                    atlasActiveId = atlasActiveId,
                    atlasCallsSlice = atlasCallsSlice,
                    atlasCallsView = atlasCallsView,
                    onAtlasCallsExpand = onAtlasCallsExpand,
                    onAtlasCallsOpen = onAtlasCallsOpen,
                    onShowCallGraph = showCallGraph,
                    onShowInAtlas = focusInAtlas,
                    palette = palette,
                    onSelectPalette = { palette = it },
                  )
                }
                if (findInFiles) {
                    FindInFilesDialog(
                        files = findInFilesIndex,
                        onPickAt = { path, offset ->
                            onIdeEvent(IdeEvent.Dialog.CloseFindInFiles)
                            openInTabAt(path, offset)
                        },
                        onReplace = onReplaceInFiles,
                        onDismiss = { onIdeEvent(IdeEvent.Dialog.CloseFindInFiles) },
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

    AppDialogs(
        appState = appState,
        layoutUiState = layoutUiState,
        workspaceState = workspaceState,
        editorWorkspace = editorWorkspace,
        fileOpHistory = fileOpHistory,
        lspRouter = lspRouter,
        fileTreeActionExecutor = fileTreeActionExecutor,
        onEvent = onIdeEvent,
        openInTab = openInTab,
        jumpToProblem = jumpToProblem,
        workspaceSymbolQuery = { q ->
            val wsPath = focused().book.active?.path
            val wsCtrl = wsPath?.let { currentLspRouter.controllerFor(it) }
            if (wsCtrl != null) runCatching { wsCtrl.workspaceSymbolsLocated(q).await() }.getOrDefault(emptyList())
            else emptyList()
        },
        requestFrameFocus = { frameRef.value?.requestFocus() },
        applyRename = applyRename,
        remapTabsAfterRename = remapTabsAfterRename,
        remapTreeStateAfterRename = remapTreeStateAfterRename,
        applyFolderPackageSync = applyFolderPackageSync,
        withFileTreeWatcherClosed = withFileTreeWatcherClosed,
        readFileText = readFileTextWithTabs,
        closeTabsUnderPath = closeTabsUnderPath,
        onFileOpHistoryChanged = { fileOpHistoryVersion++ },
        onUndoFileOp = onUndoFileOp,
        onRedoFileOp = onRedoFileOp,
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
