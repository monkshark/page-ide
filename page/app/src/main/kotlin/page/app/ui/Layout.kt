package page.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Modifier
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
import page.app.state.EditorWorkspaceState
import page.app.state.LayoutUiState
import page.app.state.WorkspaceState
import page.language.LspRouter
import page.lsp.CodeActionEntry
import page.lsp.RenameWorkspaceEdit
import page.runtime.LspInstallers
import page.runtime.RunConfigsState
import page.runtime.TerminalManager
import page.ui.SplitPane
import page.workspace.FileTreePanel
import page.workspace.TreeDragController
import java.nio.file.Path

@Composable
internal fun IdeMainLayout(
    workspace: WorkspaceState,
    editor: EditorWorkspaceState,
    ui: LayoutUiState,
    lspRouter: LspRouter,
    onCloseTab: (PaneSide, Int) -> Unit,
    onToggle: (Path, Boolean) -> Unit,
    onOpenFile: (Path) -> Unit,
    onCreateFileIn: (Path) -> Unit,
    onCreateFolderIn: (Path) -> Unit,
    onRenameEntry: (Path) -> Unit,
    onDeleteEntry: (Path) -> Unit,
    onDeleteEntries: (Set<Path>) -> Unit,
    onRevealInFiles: (Path) -> Unit,
    onCopyPath: (Path) -> Unit,
    onCopyRelativePath: (Path) -> Unit,
    onPasteInto: (Path) -> Unit,
    onDropPlan: (TreeDragController.DropPlan) -> Unit,
    onExternalDrop: (List<Path>, Path) -> Unit,
    onDropRejected: (String) -> Unit,
    onUndoFileOp: () -> Boolean,
    canUndoFileOp: Boolean,
    onTreeFocusChanged: (Boolean) -> Unit = {},
    pendingTreeFocusTick: Int = 0,
    onQueryChange: (PaneSide, String) -> Unit,
    onReplaceChange: (PaneSide, String) -> Unit,
    onToggleCase: (PaneSide) -> Unit,
    onSearchNext: (PaneSide) -> Unit,
    onSearchPrev: (PaneSide) -> Unit,
    onReplace: (PaneSide) -> Unit,
    onReplaceAll: (PaneSide) -> Unit,
    onSearchClose: (PaneSide) -> Unit,
    onWindowShortcut: (KeyEvent) -> Boolean,
    onJumpToProblem: (Path, Int, Int) -> Unit,
    onApplyRename: (RenameWorkspaceEdit) -> Unit,
    todoItems: List<page.editor.TodoItem>,
    terminalManager: TerminalManager,
    onTerminalToggle: () -> Unit,
    runState: RunConfigsState,
    onSelectRunConfig: (String) -> Unit,
    onStartRun: () -> Unit,
    onStopRun: () -> Unit,
    onOpenRunDialog: () -> Unit,
    runIsRunning: Boolean,
    outputState: OutputPanelState,
    onOutputClear: () -> Unit,
    referencesState: ReferencesQueryState?,
    onRequestReferences: (Path, Int, Int, String) -> Unit,
    onReferencesClose: () -> Unit,
    linePreviewFor: (String, Int) -> String?,
    foldedLinesFor: (Path?) -> Set<Int> = { emptySet() },
    onFoldChange: (Path, Set<Int>) -> Unit = { _, _ -> },
    editorFocusVersion: Int = 0,
    codeActionPreviewVisible: Boolean = false,
    codeActionPreviewActions: List<CodeActionEntry> = emptyList(),
    codeActionPreviewSelected: Int = 0,
    onCodeActionSelectedChange: (Int) -> Unit = {},
    codeActionPreviewUri: String? = null,
    codeActionPreviewText: String? = null,
    onCodeActionApply: (CodeActionEntry) -> Unit = {},
    onCodeActionDismiss: () -> Unit = {},
    editorScrollFor: (Path) -> EditorScrollSnapshot? = { null },
    onEditorScrollChange: (Path, EditorScrollSnapshot) -> Unit = { _, _ -> },
    tabContextActionsFor: (PaneSide) -> TabContextActions? = { null },
    settingsPanelOpen: Boolean = false,
    pageSettings: PageSettings = PageSettings(),
    onSettingsApply: (PageSettings) -> Unit = {},
    onSettingsPanelClose: () -> Unit = {},
) {
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
    Box(modifier = Modifier.fillMaxSize().onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape && installManagerOpen != null) {
            installManagerOpen = null
            true
        } else false
    }) {
    Column(modifier = Modifier.fillMaxSize()) {
        TitleBar(
            path = editor.focused().book.active?.path,
            terminalOpen = ui.terminalOpen,
            onTerminalToggle = onTerminalToggle,
            runState = runState,
            activeFilePath = editor.focused().book.active?.path,
            onSelectRunConfig = onSelectRunConfig,
            runIsRunning = runIsRunning,
            onStartRun = onStartRun,
            onStopRun = onStopRun,
            onOpenRunDialog = onOpenRunDialog,
            outputOpen = ui.outputOpen,
            onOutputToggle = { ui.outputOpen = !ui.outputOpen },
        )
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            FileTreePanel(
                root = workspace.rootDir,
                expanded = workspace.expanded,
                selection = workspace.treeSelection,
                onToggle = onToggle,
                onSelectionChange = { workspace.treeSelection = it },
                onOpenFile = onOpenFile,
                onCreateFile = onCreateFileIn,
                onCreateFolder = onCreateFolderIn,
                onRename = onRenameEntry,
                onDeleteOne = onDeleteEntry,
                onDeleteMany = onDeleteEntries,
                onReveal = onRevealInFiles,
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
                modifier = Modifier.width(ui.sidebarWidth).fillMaxHeight(),
            )
            ResizeHandle(onDeltaDp = { ui.sidebarWidth = (ui.sidebarWidth + it).coerceIn(160.dp, 600.dp) })
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
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
                            // Stop running LSP so its process releases file handles on the install dir
                            lspRouter.shutdownLanguage(id)
                            kotlinx.coroutines.delay(500)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (settingsPanelOpen) {
                    SettingsPanel(
                        settings = pageSettings,
                        onApply = onSettingsApply,
                        onClose = onSettingsPanelClose,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (editor.splitEnabled) {
                    SplitPane(
                        state = editor.splitState,
                        onStateChange = { editor.splitState = it },
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
                                onPaneFocus = { editor.focusedPane = it },
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
                                onProblemsToggle = { ui.problemsOpen = !ui.problemsOpen },
                                onJumpToProblem = onJumpToProblem,
                                onApplyRename = onApplyRename,
                                onRequestReferences = onRequestReferences,
                                todoCount = todoItems.size,
                                onTodoToggle = { ui.todoOpen = !ui.todoOpen },
                                workspaceRoot = workspace.rootDir,
                                editorFocusVersion = if (editor.focusedPane == PaneSide.PRIMARY) editorFocusVersion else 0,
                                initialFoldedStartLines = foldedLinesFor(editor.primaryPane.book.active?.path),
                                onFoldStartLinesChange = onFoldChange,
                                editorScrollFor = editorScrollFor,
                                onEditorScrollChange = onEditorScrollChange,
                                tabContextActions = tabContextActionsFor(PaneSide.PRIMARY),
                                runtimeVersions = runtimeVersions.value,
                                runtimeSources = runtimeSources.value,
                                runtimeBuildFileVersions = runtimeBuildFileVersions.value,
                                onRuntimeClick = { id -> runtimeDialogOpen = id },
                                pageSettings = pageSettings,
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
                                onPaneFocus = { editor.focusedPane = it },
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
                                onProblemsToggle = { ui.problemsOpen = !ui.problemsOpen },
                                onJumpToProblem = onJumpToProblem,
                                onApplyRename = onApplyRename,
                                onRequestReferences = onRequestReferences,
                                todoCount = todoItems.size,
                                onTodoToggle = { ui.todoOpen = !ui.todoOpen },
                                workspaceRoot = workspace.rootDir,
                                editorFocusVersion = if (editor.focusedPane == PaneSide.SECONDARY) editorFocusVersion else 0,
                                initialFoldedStartLines = foldedLinesFor(editor.secondaryPane.book.active?.path),
                                onFoldStartLinesChange = onFoldChange,
                                editorScrollFor = editorScrollFor,
                                onEditorScrollChange = onEditorScrollChange,
                                tabContextActions = tabContextActionsFor(PaneSide.SECONDARY),
                                runtimeVersions = runtimeVersions.value,
                                runtimeSources = runtimeSources.value,
                                runtimeBuildFileVersions = runtimeBuildFileVersions.value,
                                onRuntimeClick = { id -> runtimeDialogOpen = id },
                                pageSettings = pageSettings,
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
                        onPaneFocus = { editor.focusedPane = it },
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
                        onProblemsToggle = { ui.problemsOpen = !ui.problemsOpen },
                        onJumpToProblem = onJumpToProblem,
                        onApplyRename = onApplyRename,
                        onRequestReferences = onRequestReferences,
                        todoCount = todoItems.size,
                        onTodoToggle = { ui.todoOpen = !ui.todoOpen },
                        workspaceRoot = workspace.rootDir,
                        editorFocusVersion = editorFocusVersion,
                        initialFoldedStartLines = foldedLinesFor(editor.primaryPane.book.active?.path),
                        onFoldStartLinesChange = onFoldChange,
                        editorScrollFor = editorScrollFor,
                        onEditorScrollChange = onEditorScrollChange,
                        tabContextActions = tabContextActionsFor(PaneSide.PRIMARY),
                        runtimeVersions = runtimeVersions.value,
                        runtimeSources = runtimeSources.value,
                        runtimeBuildFileVersions = runtimeBuildFileVersions.value,
                        onRuntimeClick = { id -> runtimeDialogOpen = id },
                        pageSettings = pageSettings,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
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
        if (ui.problemsOpen) {
            ProblemsPanel(
                diagnostics = lspRouter.allDiagnosticsByUri,
                onJump = onJumpToProblem,
                onClose = { ui.problemsOpen = false },
                height = ui.problemsHeight,
                onResizeDelta = { ui.problemsHeight = (ui.problemsHeight + it).coerceIn(80.dp, 600.dp) },
                collapsedKeys = ui.problemsCollapsed,
                onCollapsedKeysChange = { ui.problemsCollapsed = it },
                fileOrder = ui.problemsFileOrder,
                onFileOrderChange = { ui.problemsFileOrder = it },
            )
        }
        if (ui.todoOpen) {
            TodoPanel(
                items = todoItems,
                onJump = onJumpToProblem,
                onClose = { ui.todoOpen = false },
                height = ui.todoHeight,
                onResizeDelta = { ui.todoHeight = (ui.todoHeight + it).coerceIn(80.dp, 600.dp) },
                collapsedKeys = ui.todoCollapsed,
                onCollapsedKeysChange = { ui.todoCollapsed = it },
                fileOrder = ui.todoFileOrder,
                onFileOrderChange = { ui.todoFileOrder = it },
            )
        }
        if (referencesState != null) {
            ReferencesPanel(
                state = referencesState,
                onJump = onJumpToProblem,
                onClose = onReferencesClose,
                height = ui.referencesHeight,
                onResizeDelta = { ui.referencesHeight = (ui.referencesHeight + it).coerceIn(80.dp, 600.dp) },
                linePreviewFor = linePreviewFor,
            )
        }
        if (ui.terminalOpen) {
            TerminalPanel(
                manager = terminalManager,
                onPanelClose = { ui.terminalOpen = false },
                height = ui.terminalHeight,
                onResizeDelta = { ui.terminalHeight = (ui.terminalHeight + it).coerceIn(120.dp, 600.dp) },
            )
        }
        if (ui.outputOpen) {
            OutputPanel(
                state = outputState,
                onClose = { ui.outputOpen = false },
                onClear = onOutputClear,
                onStop = onStopRun,
                height = ui.outputHeight,
                onResizeDelta = { ui.outputHeight = (ui.outputHeight + it).coerceIn(120.dp, 1200.dp) },
            )
        }
    }
    if (installGuideOpen && shellCtrl != null) {
        val activeDef = shellActivePath?.let { resolveLanguageForPath(it) }
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
    }
}
