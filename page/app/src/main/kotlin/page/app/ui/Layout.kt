package page.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import page.app.*
import page.app.mvi.ExpandedPanel
import page.app.mvi.IdeEvent
import page.atlas.graph.GraphSlice
import page.atlas.render.AtlasContent
import page.atlas.render.AtlasPanel
import page.atlas.render.VcsMark
import page.atlas.render.AtlasViewState
import page.atlas.render.MapViewState
import page.app.state.EditorWorkspaceState
import page.app.state.LayoutUiState
import page.app.state.WorkspaceState
import page.language.LspRouter
import page.lsp.CodeActionEntry
import page.lsp.RenameWorkspaceEdit
import page.runtime.LspInstallers
import page.runtime.RunConfigsState
import page.runtime.TerminalManager
import page.ui.Glass
import page.ui.GlassPalette
import page.ui.GlassSurface
import page.ui.GlassSurfaceLevel
import page.ui.SplitPane
import page.workspace.FileTreePanel
import page.workspace.TreeDragController
import java.nio.file.Path

internal data class FileTreePanelActions(
    val onToggle: (Path, Boolean) -> Unit,
    val onOpenFile: (Path) -> Unit,
    val onCreateFileIn: (Path) -> Unit,
    val onCreateFolderIn: (Path) -> Unit,
    val onRenameEntry: (Path) -> Unit,
    val onDeleteEntry: (Path) -> Unit,
    val onDeleteEntries: (Set<Path>) -> Unit,
    val onRevealInFiles: (Path) -> Unit,
    val onOpenInAtlas: ((Path) -> Unit)? = null,
    val onCopyPath: (Path) -> Unit,
    val onCopyRelativePath: (Path) -> Unit,
    val onPasteInto: (Path) -> Unit,
    val onDropPlan: (TreeDragController.DropPlan) -> Unit,
    val onExternalDrop: (List<Path>, Path) -> Unit,
    val onDropRejected: (String) -> Unit,
    val onUndoFileOp: () -> Boolean,
    val canUndoFileOp: Boolean,
    val onTreeFocusChanged: (Boolean) -> Unit = {},
    val pendingTreeFocusTick: Int = 0,
)

internal data class EditorSearchActions(
    val onQueryChange: (PaneSide, String) -> Unit,
    val onReplaceChange: (PaneSide, String) -> Unit,
    val onToggleCase: (PaneSide) -> Unit,
    val onSearchNext: (PaneSide) -> Unit,
    val onSearchPrev: (PaneSide) -> Unit,
    val onReplace: (PaneSide) -> Unit,
    val onReplaceAll: (PaneSide) -> Unit,
    val onSearchClose: (PaneSide) -> Unit,
)

internal data class RunPanelBinding(
    val runState: RunConfigsState,
    val onSelectRunConfig: (String) -> Unit,
    val onStartRun: () -> Unit,
    val onStopRun: () -> Unit,
    val onOpenRunDialog: () -> Unit,
    val runIsRunning: Boolean,
    val outputState: OutputPanelState,
    val onOutputClear: () -> Unit,
)

internal data class CodeActionPreviewBinding(
    val visible: Boolean = false,
    val actions: List<CodeActionEntry> = emptyList(),
    val selected: Int = 0,
    val onSelectedChange: (Int) -> Unit = {},
    val uri: String? = null,
    val text: String? = null,
    val onApply: (CodeActionEntry) -> Unit = {},
    val onDismiss: () -> Unit = {},
)

internal data class SettingsBinding(
    val panelOpen: Boolean = false,
    val onApply: (PageSettings) -> Unit = {},
    val onPanelClose: () -> Unit = {},
    val onToggle: () -> Unit = {},
)

@Composable
internal fun IdeMainLayout(
    workspace: WorkspaceState,
    editor: EditorWorkspaceState,
    ui: LayoutUiState,
    onEvent: (IdeEvent) -> Unit,
    lspRouter: LspRouter,
    onCloseTab: (PaneSide, Int) -> Unit,
    fileTree: FileTreePanelActions,
    search: EditorSearchActions,
    onWindowShortcut: (KeyEvent) -> Boolean,
    onJumpToProblem: (Path, Int, Int) -> Unit,
    onApplyRename: (RenameWorkspaceEdit) -> Unit,
    todoItems: List<page.editor.TodoItem>,
    terminalManager: TerminalManager,
    onTerminalToggle: () -> Unit,
    run: RunPanelBinding,
    referencesState: ReferencesQueryState?,
    onRequestReferences: (Path, Int, Int, String) -> Unit,
    onReferencesClose: () -> Unit,
    linePreviewFor: (String, Int) -> String?,
    foldedLinesFor: (Path?) -> Set<Int> = { emptySet() },
    onFoldChange: (Path, Set<Int>) -> Unit = { _, _ -> },
    editorFocusVersion: Int = 0,
    codeAction: CodeActionPreviewBinding = CodeActionPreviewBinding(),
    editorScrollFor: (Path) -> EditorScrollSnapshot? = { null },
    onEditorScrollChange: (Path, EditorScrollSnapshot) -> Unit = { _, _ -> },
    tabContextActionsFor: (PaneSide) -> TabContextActions? = { null },
    settings: SettingsBinding = SettingsBinding(),
    atlasSlice: GraphSlice = GraphSlice.EMPTY,
    atlasMapView: MapViewState = remember { MapViewState() },
    atlasView: AtlasViewState = remember { AtlasViewState() },
    atlasLoadProgress: Float? = null,
    atlasUsedByCount: Int? = null,
    onAtlasFocusActive: (() -> Unit)? = null,
    atlasVcsMarks: Map<String, VcsMark> = emptyMap(),
    atlasActiveId: String? = null,
    atlasCallsSlice: GraphSlice = GraphSlice.EMPTY,
    atlasCallsView: AtlasViewState = remember { AtlasViewState() },
    onAtlasCallsExpand: (String) -> Unit = {},
    onAtlasCallsOpen: (String) -> Unit = {},
    onShowCallGraph: (Path, Int, Int) -> Unit = { _, _, _ -> },
    palette: GlassPalette = GlassPalette.Signature,
    onSelectPalette: (GlassPalette) -> Unit = {},
) {
    val onToggle = fileTree.onToggle
    val onOpenFile = fileTree.onOpenFile
    val onCreateFileIn = fileTree.onCreateFileIn
    val onCreateFolderIn = fileTree.onCreateFolderIn
    val onRenameEntry = fileTree.onRenameEntry
    val onDeleteEntry = fileTree.onDeleteEntry
    val onDeleteEntries = fileTree.onDeleteEntries
    val onRevealInFiles = fileTree.onRevealInFiles
    val onOpenInAtlas = fileTree.onOpenInAtlas
    val onCopyPath = fileTree.onCopyPath
    val onCopyRelativePath = fileTree.onCopyRelativePath
    val onPasteInto = fileTree.onPasteInto
    val onDropPlan = fileTree.onDropPlan
    val onExternalDrop = fileTree.onExternalDrop
    val onDropRejected = fileTree.onDropRejected
    val onUndoFileOp = fileTree.onUndoFileOp
    val canUndoFileOp = fileTree.canUndoFileOp
    val onTreeFocusChanged = fileTree.onTreeFocusChanged
    val pendingTreeFocusTick = fileTree.pendingTreeFocusTick
    val onQueryChange = search.onQueryChange
    val onReplaceChange = search.onReplaceChange
    val onToggleCase = search.onToggleCase
    val onSearchNext = search.onSearchNext
    val onSearchPrev = search.onSearchPrev
    val onReplace = search.onReplace
    val onReplaceAll = search.onReplaceAll
    val onSearchClose = search.onSearchClose
    val runState = run.runState
    val onSelectRunConfig = run.onSelectRunConfig
    val onStartRun = run.onStartRun
    val onStopRun = run.onStopRun
    val onOpenRunDialog = run.onOpenRunDialog
    val runIsRunning = run.runIsRunning
    val outputState = run.outputState
    val onOutputClear = run.onOutputClear
    val codeActionPreviewVisible = codeAction.visible
    val codeActionPreviewActions = codeAction.actions
    val codeActionPreviewSelected = codeAction.selected
    val onCodeActionSelectedChange = codeAction.onSelectedChange
    val codeActionPreviewUri = codeAction.uri
    val codeActionPreviewText = codeAction.text
    val onCodeActionApply = codeAction.onApply
    val onCodeActionDismiss = codeAction.onDismiss
    val settingsPanelOpen = settings.panelOpen
    val onSettingsApply = settings.onApply
    val onSettingsPanelClose = settings.onPanelClose
    val onToggleSettings = settings.onToggle
    var dragSourcePane: PaneSide? by remember { mutableStateOf(null) }
    val shellActivePath = editor.focused().book.active?.path
    val shellCtrl = shellActivePath?.let { lspRouter.controllerFor(it) }
    val installGuideOpen by (shellCtrl?.installGuideOpen ?: kotlinx.coroutines.flow.MutableStateFlow(false)).collectAsState()
    var runtimeDialogOpen by remember { mutableStateOf<String?>(null) }
    var installManagerOpen by remember { mutableStateOf<String?>(null) }
    val runtimeVersions = remember { mutableStateOf(mapOf<String, String>()) }
    val runtimeSources = remember { mutableStateOf(mapOf<String, String>()) }
    val runtimeBuildFileVersions = remember { mutableStateOf(mapOf<String, String>()) }
    val runtimeScope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val (vers, srcs, bvs) = detectRuntimeVersionsWithSources(workspace.rootDir)
            runtimeVersions.value = vers
            runtimeSources.value = srcs
            runtimeBuildFileVersions.value = bvs
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { InstallProgressRegistry.completed.firstOrNull() }
            .collect { id ->
                if (id == null) return@collect
                InstallProgressRegistry.consumeCompleted(id)
                if (!installGuideOpen && runtimeDialogOpen == null) {
                    runtimeDialogOpen = id
                }
            }
    }
    val scopedDiagnostics = diagnosticsInScope(
        all = lspRouter.allDiagnosticsByUri,
        scope = LocalPageSettings.current.lsp.diagnosticsScope,
        focusedPath = editor.focused().book.active?.path,
        openPaths = (editor.primaryPane.book.tabs + editor.secondaryPane.book.tabs).map { it.path }.toSet(),
    )
    val scopedProblemsCount = scopedDiagnostics.values.sumOf { it.size }
    Box(modifier = Modifier.fillMaxSize().onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape && installManagerOpen != null) {
            installManagerOpen = null
            true
        } else false
    }) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .background(
                Brush.verticalGradient(
                    0f to Glass.colors.primary.copy(alpha = 0.12f),
                    1f to androidx.compose.ui.graphics.Color.Transparent,
                ),
            ),
    )
    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            path = editor.focused().book.active?.path,
            workspaceRoot = workspace.rootDir,
            runState = runState,
            activeFilePath = editor.focused().book.active?.path,
            onSelectRunConfig = onSelectRunConfig,
            runIsRunning = runIsRunning,
            onStartRun = onStartRun,
            onStopRun = onStopRun,
            onOpenRunDialog = onOpenRunDialog,
        )
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            ActivityRail(
                activeSideView = ui.activeSideView,
                onSelectSideView = { onEvent(IdeEvent.Panel.SelectSideView(it)) },
                problemsOpen = ui.problemsOpen,
                problemsCount = scopedProblemsCount,
                onProblemsToggle = { onEvent(IdeEvent.Panel.ToggleProblems) },
                terminalOpen = ui.terminalOpen,
                onTerminalToggle = onTerminalToggle,
                outputOpen = ui.outputOpen,
                onOutputToggle = { onEvent(IdeEvent.Panel.ToggleOutput) },
                settingsOpen = settingsPanelOpen,
                onSettingsToggle = onToggleSettings,
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 6.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
            ) {
            when (ui.activeSideView) {
                page.app.mvi.SideView.FILES -> {
                    GlassSurface(
                        level = GlassSurfaceLevel.Flat,
                        modifier = Modifier.width(ui.sidebarWidth).fillMaxHeight(),
                    ) {
                    FileTreePanel(
                        root = workspace.rootDir,
                        expanded = workspace.expanded,
                        selection = workspace.treeSelection,
                        onToggle = onToggle,
                        onSelectionChange = { onEvent(IdeEvent.Tree.SelectionChanged(it)) },
                        onOpenFile = onOpenFile,
                        onCreateFile = onCreateFileIn,
                        onCreateFolder = onCreateFolderIn,
                        onRename = onRenameEntry,
                        onDeleteOne = onDeleteEntry,
                        onDeleteMany = onDeleteEntries,
                        onReveal = onRevealInFiles,
                        onOpenInAtlas = onOpenInAtlas,
                        onCopyPath = onCopyPath,
                        onCopyRelativePath = onCopyRelativePath,
                        onPasteInto = onPasteInto,
                        onUndo = onUndoFileOp,
                        canUndo = canUndoFileOp,
                        onDropPlan = onDropPlan,
                        onExternalDrop = onExternalDrop,
                        onDropRejected = onDropRejected,
                        onPanelFocusChanged = onTreeFocusChanged,
                        pendingFocusTick = pendingTreeFocusTick,
                        revision = workspace.treeRevision,
                        modifier = Modifier.fillMaxSize(),
                    )
                    }
                    ResizeHandle(onDeltaDp = { onEvent(IdeEvent.Panel.ResizeSidebar(it)) })
                }
                page.app.mvi.SideView.SEARCH -> {
                    GlassSurface(
                        level = GlassSurfaceLevel.Flat,
                        modifier = Modifier.width(ui.sidebarWidth).fillMaxHeight(),
                    ) {
                        SideViewPlaceholder(title = "Search", modifier = Modifier.fillMaxSize())
                    }
                    ResizeHandle(onDeltaDp = { onEvent(IdeEvent.Panel.ResizeSidebar(it)) })
                }
                page.app.mvi.SideView.SOURCE_CONTROL -> {
                    GlassSurface(
                        level = GlassSurfaceLevel.Flat,
                        modifier = Modifier.width(ui.sidebarWidth).fillMaxHeight(),
                    ) {
                        SideViewPlaceholder(title = "Source Control", modifier = Modifier.fillMaxSize())
                    }
                    ResizeHandle(onDeltaDp = { onEvent(IdeEvent.Panel.ResizeSidebar(it)) })
                }
                null -> Unit
            }
            GlassSurface(
                level = GlassSurfaceLevel.Flat,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (installManagerOpen != null) {
                    InstallManagerPanel(
                        initialSelection = installManagerOpen,
                        onClose = { installManagerOpen = null },
                        onInstallRequested = { id ->
                            installManagerOpen = null
                            runtimeDialogOpen = id
                        },
                        onVersionChanged = {
                            runtimeScope.launch {
                                withContext(Dispatchers.IO) {
                                    val (vers, srcs, bvs) = detectRuntimeVersionsWithSources(workspace.rootDir)
                                    runtimeVersions.value = vers
                                    runtimeSources.value = srcs
                                    runtimeBuildFileVersions.value = bvs
                                }
                            }
                        },
                        onBeforeDelete = { id ->
                            lspRouter.shutdownLanguage(id)
                            kotlinx.coroutines.delay(500)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (settingsPanelOpen) {
                    SettingsPanel(
                        settings = LocalPageSettings.current,
                        onApply = onSettingsApply,
                        onClose = onSettingsPanelClose,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (editor.splitEnabled) {
                    SplitPane(
                        state = editor.splitState,
                        onStateChange = { onEvent(IdeEvent.EditorLayout.SplitStateChanged(it)) },
                        orientation = editor.splitOrientation,
                        modifier = Modifier.fillMaxSize(),
                        firstZIndex = if (dragSourcePane == PaneSide.PRIMARY) 1f else 0f,
                        secondZIndex = if (dragSourcePane == PaneSide.SECONDARY) 1f else 0f,
                        first = {
                            PaneRegion(
                                pane = editor.primaryPane,
                                side = PaneSide.PRIMARY,
                                editor = editor,
                                lspRouter = lspRouter,
                                isFocused = editor.focusedPane == PaneSide.PRIMARY,
                                onPaneFocus = { onEvent(IdeEvent.EditorLayout.FocusPane(it)) },
                                onCloseTab = onCloseTab,
                                onQueryChange = onQueryChange,
                                onReplaceChange = onReplaceChange,
                                onToggleCase = onToggleCase,
                                onSearchNext = onSearchNext,
                                onSearchPrev = onSearchPrev,
                                onReplace = onReplace,
                                onReplaceAll = onReplaceAll,
                                onSearchClose = onSearchClose,
                                onWindowShortcut = onWindowShortcut,
                                onTabDragStart = { dragSourcePane = PaneSide.PRIMARY },
                                onTabDragEnd = { dragSourcePane = null },
                                onJumpToProblem = onJumpToProblem,
                                onApplyRename = onApplyRename,
                                onRequestReferences = onRequestReferences,
                                onShowCallGraph = onShowCallGraph,
                                workspaceRoot = workspace.rootDir,
                                editorFocusVersion = if (editor.focusedPane == PaneSide.PRIMARY) editorFocusVersion else 0,
                                initialFoldedStartLines = foldedLinesFor(editor.primaryPane.book.active?.path),
                                onFoldStartLinesChange = onFoldChange,
                                editorScrollFor = editorScrollFor,
                                onEditorScrollChange = onEditorScrollChange,
                                tabContextActions = tabContextActionsFor(PaneSide.PRIMARY),
                                modifier = Modifier.fillMaxSize(),
                            )
                        },
                        second = {
                            PaneRegion(
                                pane = editor.secondaryPane,
                                side = PaneSide.SECONDARY,
                                editor = editor,
                                lspRouter = lspRouter,
                                isFocused = editor.focusedPane == PaneSide.SECONDARY,
                                onPaneFocus = { onEvent(IdeEvent.EditorLayout.FocusPane(it)) },
                                onCloseTab = onCloseTab,
                                onQueryChange = onQueryChange,
                                onReplaceChange = onReplaceChange,
                                onToggleCase = onToggleCase,
                                onSearchNext = onSearchNext,
                                onSearchPrev = onSearchPrev,
                                onReplace = onReplace,
                                onReplaceAll = onReplaceAll,
                                onSearchClose = onSearchClose,
                                onWindowShortcut = onWindowShortcut,
                                onTabDragStart = { dragSourcePane = PaneSide.SECONDARY },
                                onTabDragEnd = { dragSourcePane = null },
                                onJumpToProblem = onJumpToProblem,
                                onApplyRename = onApplyRename,
                                onRequestReferences = onRequestReferences,
                                onShowCallGraph = onShowCallGraph,
                                workspaceRoot = workspace.rootDir,
                                editorFocusVersion = if (editor.focusedPane == PaneSide.SECONDARY) editorFocusVersion else 0,
                                initialFoldedStartLines = foldedLinesFor(editor.secondaryPane.book.active?.path),
                                onFoldStartLinesChange = onFoldChange,
                                editorScrollFor = editorScrollFor,
                                onEditorScrollChange = onEditorScrollChange,
                                tabContextActions = tabContextActionsFor(PaneSide.SECONDARY),
                                modifier = Modifier.fillMaxSize(),
                            )
                        },
                    )
                } else {
                    PaneRegion(
                        pane = editor.primaryPane,
                        side = PaneSide.PRIMARY,
                        editor = editor,
                        lspRouter = lspRouter,
                        isFocused = true,
                        onPaneFocus = { onEvent(IdeEvent.EditorLayout.FocusPane(it)) },
                        onCloseTab = onCloseTab,
                        onQueryChange = onQueryChange,
                        onReplaceChange = onReplaceChange,
                        onToggleCase = onToggleCase,
                        onSearchNext = onSearchNext,
                        onSearchPrev = onSearchPrev,
                        onReplace = onReplace,
                        onReplaceAll = onReplaceAll,
                        onSearchClose = onSearchClose,
                        onWindowShortcut = onWindowShortcut,
                        onJumpToProblem = onJumpToProblem,
                        onApplyRename = onApplyRename,
                        onRequestReferences = onRequestReferences,
                        onShowCallGraph = onShowCallGraph,
                        workspaceRoot = workspace.rootDir,
                        editorFocusVersion = editorFocusVersion,
                        initialFoldedStartLines = foldedLinesFor(editor.primaryPane.book.active?.path),
                        onFoldStartLinesChange = onFoldChange,
                        editorScrollFor = editorScrollFor,
                        onEditorScrollChange = onEditorScrollChange,
                        tabContextActions = tabContextActionsFor(PaneSide.PRIMARY),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                PillarPill(
                    atlasActive = ui.atlasOpen,
                    onAtlasToggle = { onEvent(IdeEvent.Panel.ToggleAtlas) },
                    currentPalette = palette,
                    onSelectPalette = onSelectPalette,
                    onCommandPalette = { onEvent(IdeEvent.Palette.QuickOpen) },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                )
            }
            }
            if (ui.atlasOpen) {
                ResizeHandle(onDeltaDp = { onEvent(IdeEvent.Panel.ResizeAtlas(it)) })
                AtlasPanel(
                    slice = atlasSlice,
                    onNodeClick = onOpenFile,
                    onClose = { onEvent(IdeEvent.Panel.CloseAtlas) },
                    width = ui.atlasWidth,
                    projectMode = ui.atlasProjectMode,
                    onProjectModeChange = { onEvent(IdeEvent.Panel.AtlasProjectModeChanged(it)) },
                    viewTab = ui.atlasViewTab,
                    onViewTabChange = { onEvent(IdeEvent.Panel.AtlasViewTabChanged(it)) },
                    showExpand = true,
                    onExpand = { onEvent(IdeEvent.Panel.ExpandPanel(ExpandedPanel.ATLAS)) },
                    mapView = atlasMapView,
                    atlasView = atlasView,
                    loadProgress = atlasLoadProgress,
                    vcsMarks = atlasVcsMarks,
                    vcsEnabled = ui.atlasVcsOverlay,
                    onVcsEnabledChange = { onEvent(IdeEvent.Panel.AtlasVcsOverlayChanged(it)) },
                    activeFileId = atlasActiveId,
                    followActive = ui.atlasFollowActive,
                    onFollowActiveChange = { onEvent(IdeEvent.Panel.AtlasFollowActiveChanged(it)) },
                    callsSlice = atlasCallsSlice,
                    callsView = atlasCallsView,
                    onCallsExpand = onAtlasCallsExpand,
                    onCallsOpen = onAtlasCallsOpen,
                )
            }
            if (codeActionPreviewVisible) {
                CodeActionPreviewPanel(
                    actions = codeActionPreviewActions,
                    selected = codeActionPreviewSelected,
                    onSelectedChange = onCodeActionSelectedChange,
                    currentUri = codeActionPreviewUri,
                    currentText = codeActionPreviewText,
                    onApply = onCodeActionApply,
                    onDismiss = onCodeActionDismiss,
                    width = 420.dp,
                )
            }
            }
        }
        if (ui.problemsOpen) {
            ProblemsPanel(
                diagnostics = scopedDiagnostics,
                onJump = onJumpToProblem,
                onClose = { onEvent(IdeEvent.Panel.CloseProblems) },
                height = ui.problemsHeight,
                onResizeDelta = { onEvent(IdeEvent.Panel.ResizeProblems(it)) },
                collapsedKeys = ui.problemsCollapsed,
                onCollapsedKeysChange = { onEvent(IdeEvent.Panel.ProblemsCollapsedChanged(it)) },
                fileOrder = ui.problemsFileOrder,
                onFileOrderChange = { onEvent(IdeEvent.Panel.ProblemsFileOrderChanged(it)) },
            )
        }
        if (ui.todoOpen) {
            TodoPanel(
                items = todoItems,
                onJump = onJumpToProblem,
                onClose = { onEvent(IdeEvent.Panel.CloseTodo) },
                height = ui.todoHeight,
                onResizeDelta = { onEvent(IdeEvent.Panel.ResizeTodo(it)) },
                collapsedKeys = ui.todoCollapsed,
                onCollapsedKeysChange = { onEvent(IdeEvent.Panel.TodoCollapsedChanged(it)) },
                fileOrder = ui.todoFileOrder,
                onFileOrderChange = { onEvent(IdeEvent.Panel.TodoFileOrderChanged(it)) },
            )
        }
        if (referencesState != null) {
            ReferencesPanel(
                state = referencesState,
                onJump = onJumpToProblem,
                onClose = onReferencesClose,
                height = ui.referencesHeight,
                onResizeDelta = { onEvent(IdeEvent.Panel.ResizeReferences(it)) },
                linePreviewFor = linePreviewFor,
            )
        }
        if (ui.terminalOpen) {
            TerminalPanel(
                manager = terminalManager,
                onPanelClose = { onEvent(IdeEvent.Panel.CloseTerminal) },
                height = ui.terminalHeight,
                onResizeDelta = { onEvent(IdeEvent.Panel.ResizeTerminal(it)) },
            )
        }
        if (ui.outputOpen) {
            OutputPanel(
                state = outputState,
                onClose = { onEvent(IdeEvent.Panel.CloseOutput) },
                onClear = onOutputClear,
                onStop = onStopRun,
                height = ui.outputHeight,
                onResizeDelta = { onEvent(IdeEvent.Panel.ResizeOutput(it)) },
            )
        }
        GlobalStatusBar(
            editor = editor,
            lspRouter = lspRouter,
            todoCount = todoItems.size,
            runtimeVersions = runtimeVersions.value,
            runtimeSources = runtimeSources.value,
            runtimeBuildFileVersions = runtimeBuildFileVersions.value,
            onProblemsToggle = { onEvent(IdeEvent.Panel.ToggleProblems) },
            onTodoToggle = { onEvent(IdeEvent.Panel.ToggleTodo) },
            onRuntimeClick = { id -> runtimeDialogOpen = id },
            usedByCount = atlasUsedByCount,
            onUsedByClick = onAtlasFocusActive,
        )
    }
    if (installGuideOpen && shellCtrl != null) {
        val activeDef = shellActivePath?.let { p ->
            lspRouter.backendFor(p)?.let { page.lsp.LanguageRegistry.byId(it.id) } ?: resolveLanguageForPath(p)
        }
        val def = activeDef
            ?: shellCtrl.missingDefinition.value
            ?: page.lsp.LanguageRegistry.byId("kotlin")
        if (def != null) {
            InstallGuideDialog(
                definition = def,
                attempted = shellCtrl.missingAttempted.value,
                onDismiss = { shellCtrl.closeInstallGuide() },
                onInstalled = { shellCtrl.retry() },
                onOpenManager = {
                    val id = def.id
                    shellCtrl.closeInstallGuide()
                    installManagerOpen = id
                },
                installScope = runtimeScope,
                onMinimize = { shellCtrl.closeInstallGuide() },
            )
        } else {
            shellCtrl.closeInstallGuide()
        }
    }
    val runtimeDialogId = runtimeDialogOpen
    if (runtimeDialogId != null) {
        val runtimeDefs = mapOf(
            "jdk" to page.lsp.LanguageDefinition("jdk", "Eclipse Temurin JDK", listOf("java"), emptyList(), emptyList(), "https://adoptium.net/", emptyMap(), null),
            "node" to page.lsp.LanguageDefinition("node", "Node.js", listOf("js"), emptyList(), emptyList(), "https://nodejs.org/", emptyMap(), null),
            "python-runtime" to page.lsp.LanguageDefinition("python-runtime", "Python", listOf("py"), emptyList(), emptyList(), "https://python.org/", emptyMap(), null),
            "go-sdk" to page.lsp.LanguageDefinition("go-sdk", "Go SDK", listOf("go"), emptyList(), emptyList(), "https://go.dev/", emptyMap(), null),
            "cpp-toolchain" to page.lsp.LanguageDefinition("cpp-toolchain", "LLVM/Clang Toolchain", listOf("c", "cpp"), emptyList(), emptyList(), "https://llvm.org/", emptyMap(), null),
            "rust-runtime" to page.lsp.LanguageDefinition("rust-runtime", "Rust Toolchain", listOf("rs"), emptyList(), emptyList(), "https://rustup.rs/", emptyMap(), null),
            "dotnet-runtime" to page.lsp.LanguageDefinition("dotnet-runtime", ".NET SDK", listOf("cs"), emptyList(), emptyList(), "https://dotnet.microsoft.com/download", emptyMap(), null),
            "windows-sdk" to page.lsp.LanguageDefinition("windows-sdk", "Windows SDK (MSVC, xwin)", listOf("swift"), emptyList(), emptyList(), "https://github.com/Jake-Shadle/xwin", emptyMap(), null),
            "mingw-toolchain" to page.lsp.LanguageDefinition("mingw-toolchain", "MinGW-w64 (UCRT64)", listOf("c", "cpp"), emptyList(), emptyList(), "https://www.mingw-w64.org/", emptyMap(), null),
        )
        val def = runtimeDefs[runtimeDialogId] ?: page.lsp.LanguageRegistry.byId(runtimeDialogId)
        val buildFileKey = when (runtimeDialogId) {
            "jdk" -> "java"; "node" -> "js"; "python-runtime" -> "py"
            "go-sdk" -> "go"; "rust-runtime" -> "rs"; "dotnet-runtime" -> "cs"
            else -> null
        }
        val suggested = buildFileKey?.let { runtimeBuildFileVersions.value[it] }
        if (def != null) {
            InstallGuideDialog(
                definition = def,
                attempted = emptyList(),
                onDismiss = { runtimeDialogOpen = null },
                onInstalled = {
                    lspRouter.restartForExtensions(def.extensions, "$runtimeDialogId installed")
                    runtimeScope.launch {
                        withContext(Dispatchers.IO) {
                            val (vers, srcs, bvs) = detectRuntimeVersionsWithSources(workspace.rootDir)
                            runtimeVersions.value = vers
                            runtimeSources.value = srcs
                            runtimeBuildFileVersions.value = bvs
                        }
                    }
                },
                installer = LspInstallers.forId(runtimeDialogId),
                suggestedVersion = suggested,
                onOpenManager = {
                    val id = runtimeDialogOpen
                    runtimeDialogOpen = null
                    installManagerOpen = id
                },
                installScope = runtimeScope,
                onMinimize = { runtimeDialogOpen = null },
            )
        } else {
            runtimeDialogOpen = null
        }
    }
    when (ui.expandedPanel) {
        ExpandedPanel.ATLAS -> ExpandedPanelOverlay(
            onClose = { onEvent(IdeEvent.Panel.CollapsePanel) },
        ) {
            AtlasContent(
                slice = atlasSlice,
                onNodeClick = onOpenFile,
                onClose = { onEvent(IdeEvent.Panel.CollapsePanel) },
                projectMode = ui.atlasProjectMode,
                onProjectModeChange = { onEvent(IdeEvent.Panel.AtlasProjectModeChanged(it)) },
                viewTab = ui.atlasViewTab,
                onViewTabChange = { onEvent(IdeEvent.Panel.AtlasViewTabChanged(it)) },
                mapView = atlasMapView,
                atlasView = atlasView,
                loadProgress = atlasLoadProgress,
                vcsMarks = atlasVcsMarks,
                vcsEnabled = ui.atlasVcsOverlay,
                onVcsEnabledChange = { onEvent(IdeEvent.Panel.AtlasVcsOverlayChanged(it)) },
                activeFileId = atlasActiveId,
                followActive = ui.atlasFollowActive,
                onFollowActiveChange = { onEvent(IdeEvent.Panel.AtlasFollowActiveChanged(it)) },
                callsSlice = atlasCallsSlice,
                callsView = atlasCallsView,
                onCallsExpand = onAtlasCallsExpand,
                onCallsOpen = onAtlasCallsOpen,
            )
        }
        ExpandedPanel.NONE -> Unit
    }
    }
}
