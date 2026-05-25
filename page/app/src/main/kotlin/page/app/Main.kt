package page.app

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
    var primaryPane: EditorPaneState by remember { mutableStateOf(EditorPaneState()) }
    var secondaryPane: EditorPaneState by remember { mutableStateOf(EditorPaneState()) }
    var focusedPane: PaneSide by remember { mutableStateOf(PaneSide.PRIMARY) }
    var rootDir: Path? by remember { mutableStateOf(null) }
    var expanded: Set<Path> by remember { mutableStateOf(emptySet()) }
    var treeSelection: Set<Path> by remember { mutableStateOf(emptySet()) }
    var treeRevision by remember { mutableStateOf(0) }
    var editorScrollByPath: Map<Path, EditorScrollSnapshot> by remember { mutableStateOf(emptyMap()) }
    var sidebarWidth: Dp by remember { mutableStateOf(260.dp) }
    var pendingClose: PendingClose? by remember { mutableStateOf(null) }
    var quickOpen by remember { mutableStateOf(false) }
    var quickOpenIndex by remember { mutableStateOf<List<IndexedFile>>(emptyList()) }
    var findInFiles by remember { mutableStateOf(false) }
    var findInFilesIndex by remember { mutableStateOf<List<IndexedFile>>(emptyList()) }
    var splitEnabled by remember { mutableStateOf(false) }
    var splitOrientation by remember { mutableStateOf(SplitOrientation.HORIZONTAL) }
    var splitState by remember { mutableStateOf(SplitPaneState(ratio = 0.5f)) }
    var problemsOpen by remember { mutableStateOf(false) }
    var problemsHeight: Dp by remember { mutableStateOf(220.dp) }
    var problemsCollapsed by remember { mutableStateOf(emptySet<String>()) }
    var problemsFileOrder by remember { mutableStateOf(emptyList<String>()) }
    var todoOpen by remember { mutableStateOf(false) }
    var todoHeight: Dp by remember { mutableStateOf(220.dp) }
    var todoCollapsed by remember { mutableStateOf(emptySet<String>()) }
    var todoFileOrder by remember { mutableStateOf(emptyList<String>()) }
    var terminalOpen by remember { mutableStateOf(false) }
    var terminalHeight: Dp by remember { mutableStateOf(240.dp) }
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
    var outputOpen by remember { mutableStateOf(false) }
    var outputHeight: Dp by remember {
        mutableStateOf(
            (java.awt.Toolkit.getDefaultToolkit().screenSize.height / 2f)
                .coerceIn(240f, 1200f)
                .dp,
        )
    }
    val outputState = remember { OutputPanelState() }
    val runScope = rememberCoroutineScope()
    val runController = remember {
        RunController(runScope) { event -> outputState.onEvent(event) }
    }
    DisposableEffect(runController) {
        onDispose { runController.stop() }
    }
    var referencesState: ReferencesQueryState? by remember { mutableStateOf(null) }
    var referencesHeight: Dp by remember { mutableStateOf(220.dp) }
    var createDialog: CreateEntryDialogState? by remember { mutableStateOf(null) }
    var renameDialog: RenameEntryDialogState? by remember { mutableStateOf(null) }
    var deleteDialog: DeleteEntryDialogState? by remember { mutableStateOf(null) }
    var pasteDialog: PasteEntryDialogState? by remember { mutableStateOf(null) }
    var largeCopyState: LargeCopyDialogState? by remember { mutableStateOf(null) }
    val largeCopyScope = rememberCoroutineScope()
    val fileOpHistory = remember { FileOpHistory.Stack() }
    var fileOpHistoryVersion by remember { mutableStateOf(0) }
    var fileTreeFocused by remember { mutableStateOf(false) }
    var fileOpConfirm: FileOpConfirmState? by remember { mutableStateOf(null) }
    var pendingTreeFocusTick by remember { mutableStateOf(0) }
    var palette: GlassPalette by remember { mutableStateOf(AppSettings.loadPalette()) }
    var paletteToastUntil: Long by remember { mutableStateOf(0L) }
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
    val lsp = rememberLspController(workspaceRoot = rootDir)
    val currentLsp by rememberUpdatedState(lsp)
    val todo = rememberTodoController(workspaceRoot = rootDir)
    val todoItems by todo.items
    val undoTrackerPrimary = remember { UndoGroupTracker() }
    val undoTrackerSecondary = remember { UndoGroupTracker() }
    fun undoTracker(side: PaneSide): UndoGroupTracker = when (side) {
        PaneSide.PRIMARY -> undoTrackerPrimary
        PaneSide.SECONDARY -> undoTrackerSecondary
    }

    fun paneOf(side: PaneSide): EditorPaneState = when (side) {
        PaneSide.PRIMARY -> primaryPane
        PaneSide.SECONDARY -> secondaryPane
    }

    fun setPane(side: PaneSide, value: EditorPaneState) {
        when (side) {
            PaneSide.PRIMARY -> primaryPane = value
            PaneSide.SECONDARY -> secondaryPane = value
        }
    }

    fun mutatePane(side: PaneSide, transform: (EditorPaneState) -> EditorPaneState) {
        setPane(side, transform(paneOf(side)))
    }

    fun mutateFocused(transform: (EditorPaneState) -> EditorPaneState) {
        mutatePane(focusedPane, transform)
    }

    fun focused(): EditorPaneState = paneOf(focusedPane)

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

    LaunchedEffect(rootDir) {
        val root = rootDir
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
            lsp.ensureStarted()
        }
        todo.scanWorkspaceAsync()
    }

    LaunchedEffect(rootDir) {
        val root = rootDir ?: run {
            runState = RunConfigsState()
            return@LaunchedEffect
        }
        runState = runCatching { RunConfigStore.load(root) }.getOrDefault(RunConfigsState())
    }

    LaunchedEffect(rootDir, runState) {
        val root = rootDir ?: return@LaunchedEffect
        kotlinx.coroutines.delay(400)
        runCatching { RunConfigStore.save(root, runState) }
    }

    var sessionLoaded by remember { mutableStateOf(false) }
    var foldByPath by remember { mutableStateOf<Map<String, Set<Int>>>(emptyMap()) }
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

    LaunchedEffect(rootDir) {
        historyLoaded = false
        val root = rootDir
        if (root == null) {
            historyFile = HistoryFile()
            historyLoaded = true
            return@LaunchedEffect
        }
        historyFile = runCatching { HistoryStore.load(root) }.getOrDefault(HistoryFile())
        historyLoaded = true
    }
    LaunchedEffect(rootDir) {
        val root = rootDir
        if (root == null) {
            workspaceFile = WorkspaceFile()
            return@LaunchedEffect
        }
        val ws = runCatching { WorkspaceStore.load(root) }.getOrDefault(WorkspaceFile())
        workspaceFile = ws
        val name = ws.palette
        if (name != null) {
            val resolved = GlassPalette.values().firstOrNull { it.name.equals(name, ignoreCase = true) }
            if (resolved != null) palette = resolved
        }
    }
    LaunchedEffect(rootDir, historyLoaded, historyFile) {
        if (!historyLoaded) return@LaunchedEffect
        val root = rootDir ?: return@LaunchedEffect
        kotlinx.coroutines.delay(500)
        runCatching { HistoryStore.save(root, historyFile) }
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
        if (path != null && isKotlinSource(path)) {
            lsp.ensureStarted()
            lsp.didOpen(path, "kotlin", focused().editorValue.text)
        }
    }
    LaunchedEffect(focusedActivePath, focusedActiveText) {
        val path = focusedActivePath
        if (path != null && isKotlinSource(path)) {
            lsp.didChange(path, focusedActiveText)
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

    val onReplaceInFiles: suspend (ReplaceRequest) -> ReplaceOutcome = { req ->
        data class Outcome(val filesChanged: Int, val replacements: Int, val updates: Map<Path, String>)
        val result = withContext(Dispatchers.IO) {
            var filesChanged = 0
            var replacements = 0
            val updates = HashMap<Path, String>()
            for (target in req.targets) {
                val original = FileDocument.loadOrNull(target) ?: continue
                val (newText, count) = ProjectGrep.applyReplace(
                    text = original,
                    query = req.query,
                    replacement = req.replacement,
                    caseSensitive = req.caseSensitive,
                    regex = req.regex,
                    wholeWord = req.wholeWord,
                )
                if (count == 0 || newText == original) continue
                try {
                    FileDocument.save(target, newText)
                } catch (_: java.io.IOException) {
                    continue
                }
                updates[target] = newText
                filesChanged += 1
                replacements += count
            }
            Outcome(filesChanged, replacements, updates)
        }
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

    val jumpToProblem: (Path, Int, Int) -> Unit = { picked, line, character ->
        val pane = focused()
        val text = pane.book.tabs.firstOrNull { it.path == picked }?.text
            ?: FileDocument.loadOrNull(picked)
        if (text != null) {
            var lineIdx = 0
            var i = 0
            while (i < text.length && lineIdx < line) {
                if (text[i] == '\n') lineIdx++
                i++
            }
            val offset = (i + character.coerceAtLeast(0)).coerceAtMost(text.length)
            openInTabAt(picked, offset)
        }
    }

    val requestReferences: (Path, Int, Int, String) -> Unit = { p, line, char, symbol ->
        val origin = p.toUri().toString()
        referencesState = ReferencesQueryState(
            symbolName = symbol,
            originUri = origin,
            results = emptyList(),
            isLoading = true,
        )
        lsp.references(p, line, char, includeDeclaration = true, symbolName = symbol)
            .whenComplete { results, err ->
                if (err != null) {
                    referencesState = ReferencesQueryState(
                        symbolName = symbol,
                        originUri = origin,
                        results = emptyList(),
                        isLoading = false,
                        errorMessage = err.message?.lineSequence()?.firstOrNull()?.take(160)
                            ?: "Find references failed",
                    )
                    return@whenComplete
                }
                val list = results.orEmpty()
                val autoJump = pickSingleOtherReference(list, origin, line, char)
                if (autoJump != null) {
                    referencesState = null
                    val target = runCatching {
                        java.nio.file.Paths.get(java.net.URI(autoJump.uri))
                    }.getOrNull()
                    if (target != null) {
                        jumpToProblem(target, autoJump.startLine, autoJump.startCharacter)
                    } else {
                        referencesState = ReferencesQueryState(
                            symbolName = symbol,
                            originUri = origin,
                            results = list,
                            isLoading = false,
                        )
                    }
                } else {
                    referencesState = ReferencesQueryState(
                        symbolName = symbol,
                        originUri = origin,
                        results = list,
                        isLoading = false,
                    )
                }
            }
    }

    val applyRename: (RenameWorkspaceEdit) -> Unit = { edit ->
        val groupId = System.nanoTime()
        for (change in edit.changes) {
            val path = runCatching {
                java.nio.file.Paths.get(java.net.URI(change.uri))
            }.getOrNull() ?: continue
            var handled = false
            for (side in listOf(PaneSide.PRIMARY, PaneSide.SECONDARY)) {
                val p = paneOf(side)
                val idx = p.book.tabs.indexOfFirst { it.path == path }
                if (idx < 0) continue
                handled = true
                val tab = p.book.tabs[idx]
                val newText = RenameApply.applyToText(tab.text, change.edits)
                if (newText == tab.text) continue
                val isActive = idx == p.book.activeIndex
                val priorCaret = if (isActive) p.editorValue.selection.start else tab.caret
                val priorSnapshot = EditSnapshot(tab.text, priorCaret, groupId)
                val newCaretInTab = priorCaret.coerceAtMost(newText.length)
                val updatedTabs = p.book.tabs.toMutableList().also {
                    it[idx] = tab.copy(
                        text = newText,
                        caret = newCaretInTab,
                        history = tab.history.pushBeforeChange(priorSnapshot),
                    )
                }
                val newEditorValue = if (isActive) {
                    TextFieldValue(newText, TextRange(newCaretInTab))
                } else p.editorValue
                setPane(
                    side,
                    p.copy(
                        book = p.book.copy(tabs = updatedTabs),
                        editorValue = newEditorValue,
                    ),
                )
                lsp.applyExternalChange(change.uri, newText)
            }
            if (!handled) {
                val onDisk = FileDocument.loadOrNull(path) ?: continue
                val newText = RenameApply.applyToText(onDisk, change.edits)
                if (newText == onDisk) continue
                val priorSnapshot = EditSnapshot(onDisk, 0, groupId)
                val newTab = OpenTab(
                    path = path,
                    text = newText,
                    savedText = onDisk,
                    caret = 0,
                    history = EditHistory().pushBeforeChange(priorSnapshot),
                )
                mutatePane(PaneSide.PRIMARY) {
                    val savedActive = it.book.activeIndex
                    val appended = it.book.appendTab(newTab)
                    val restored = if (savedActive in appended.tabs.indices) appended.copy(activeIndex = savedActive) else appended
                    it.copy(book = restored)
                }
                lsp.applyExternalChange(change.uri, newText)
            }
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
                    if (isKotlinSource(origin)) affectedOldPaths.add(origin)
                    if (isKotlinSource(tab.path)) affectedNewPaths.add(tab.path to tab.text)
                }
            }
        }
        affectedOldPaths.distinct().forEach { lsp.didClose(it) }
        affectedNewPaths.distinctBy { it.first }.forEach { (p, text) -> lsp.didOpen(p, "kotlin", text) }
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
        runCatching { lsp.applyExternalChange(path.toUri().toString(), newText) }
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
        val newFolderAbs = newFolder.toAbsolutePath().normalize()
        val isKtFile: (Path) -> Boolean = { p ->
            val name = p.fileName?.toString().orEmpty()
            name.endsWith(".kt") || name.endsWith(".kts")
        }
        val rewriteList = mutableListOf<FileOpHistory.RewriteEntry>()
        if (packageMap.isNotEmpty()) {
            val rewrites = LinkedHashMap<Path, Pair<String, String>>()
            runCatching {
                java.nio.file.Files.walk(newFolder).use { stream ->
                    stream.filter { java.nio.file.Files.isRegularFile(it) && isKtFile(it) }
                        .forEach { p ->
                            val abs = p.toAbsolutePath().normalize()
                            val text = readFileTextWithTabs(abs) ?: return@forEach
                            val rewritten = FolderPackageRename.rewriteFileInRenamedFolder(text, packageMap) ?: return@forEach
                            rewrites[abs] = text to rewritten
                        }
                }
            }
            rootDir?.let { root ->
                runCatching {
                    java.nio.file.Files.walk(root).use { stream ->
                        stream.filter { java.nio.file.Files.isRegularFile(it) && isKtFile(it) }
                            .forEach { p ->
                                val abs = p.toAbsolutePath().normalize()
                                if (abs.startsWith(newFolderAbs)) return@forEach
                                if (abs in rewrites) return@forEach
                                val text = readFileTextWithTabs(abs) ?: return@forEach
                                val rewritten = FolderPackageRename.rewriteImports(text, packageMap) ?: return@forEach
                                rewrites[abs] = text to rewritten
                            }
                    }
                }
            }
            for ((path, pair) in rewrites) {
                val (original, rewritten) = pair
                applyTextReplace(path, rewritten)
                rewriteList.add(FileOpHistory.RewriteEntry(path, original, rewritten))
            }
        }
        rewriteList
    }
    val applySingleFileMoveSync: (Path, FolderPackageRename.SingleFileMovePlan) -> List<FileOpHistory.RewriteEntry> = { newPath, plan ->
        val isKtFile: (Path) -> Boolean = { p ->
            val name = p.fileName?.toString().orEmpty()
            name.endsWith(".kt") || name.endsWith(".kts")
        }
        val rewrites = LinkedHashMap<Path, Pair<String, String>>()
        if (plan.newSelfText != null) {
            val abs = newPath.toAbsolutePath().normalize()
            val current = readFileTextWithTabs(abs)
            if (current != null) rewrites[abs] = current to plan.newSelfText
        }
        rootDir?.let { root ->
            val newPathAbs = newPath.toAbsolutePath().normalize()
            runCatching {
                java.nio.file.Files.walk(root).use { stream ->
                    stream.filter { java.nio.file.Files.isRegularFile(it) && isKtFile(it) }
                        .forEach { p ->
                            val abs = p.toAbsolutePath().normalize()
                            if (abs == newPathAbs) return@forEach
                            if (abs in rewrites) return@forEach
                            val text = readFileTextWithTabs(abs) ?: return@forEach
                            val rewritten = FolderPackageRename.rewriteImports(text, plan.importRewriteMap)
                                ?: return@forEach
                            rewrites[abs] = text to rewritten
                        }
                }
            }
        }
        val rewriteList = mutableListOf<FileOpHistory.RewriteEntry>()
        for ((path, pair) in rewrites) {
            val (original, rewritten) = pair
            applyTextReplace(path, rewritten)
            rewriteList.add(FileOpHistory.RewriteEntry(path, original, rewritten))
        }
        rewriteList
    }
    val closeTabsUnderPath: (Path) -> Unit = { path ->
        listOf(PaneSide.PRIMARY, PaneSide.SECONDARY).forEach { side ->
            val pane = paneOf(side)
            val victims = pane.book.tabs.withIndex()
                .filter { (_, tab) -> tab.path == path || tab.path.startsWith(path) }
                .map { it.index }
            val closedPaths = victims.map { pane.book.tabs[it].path }
            if (victims.isNotEmpty()) {
                val newBook = victims.sortedDescending().fold(pane.book) { acc, idx -> acc.close(idx) }
                mutatePane(side) { it.copy(book = newBook) }
            }
            closedPaths.forEach { p ->
                val stillOpenAnywhere = primaryPane.book.tabs.any { it.path == p } ||
                    secondaryPane.book.tabs.any { it.path == p }
                if (!stillOpenAnywhere) {
                    editorScrollByPath = EditorScrollMemory.clear(editorScrollByPath, p)
                    if (isKotlinSource(p)) lsp.didClose(p)
                }
            }
        }
    }
    val toggleExpanded: (Path, Boolean) -> Unit = { p, recursive ->
        expanded = when {
            recursive -> expanded + setOf(p) + page.editor.FileTree.descendantDirs(p)
            p in expanded -> expanded - setOf(p)
            else -> expanded + setOf(p) + page.editor.FileTree.singleChildChain(p)
        }
    }
    val closeTabAt: (PaneSide, Int) -> Unit = { side, idx ->
        val tab = paneOf(side).book.tabs.getOrNull(idx)
        mutatePane(side) { it.copy(book = it.book.close(idx)) }
        if (tab != null) {
            val stillOpenAnywhere = primaryPane.book.tabs.any { it.path == tab.path } ||
                secondaryPane.book.tabs.any { it.path == tab.path }
            if (!stillOpenAnywhere) {
                editorScrollByPath = EditorScrollMemory.clear(editorScrollByPath, tab.path)
                if (isKotlinSource(tab.path)) lsp.didClose(tab.path)
            }
        }
    }
    val isUnsavedText: (OpenTab) -> Boolean = { tab ->
        tab.dirty && FileKinds.classify(tab.path).isEditableAsText
    }
    val requestCloseTab: (PaneSide, Int) -> Unit = { side, idx ->
        val tab = paneOf(side).book.tabs.getOrNull(idx)
        if (tab != null && isUnsavedText(tab)) {
            pendingClose = PendingClose.Tab(side, idx)
        } else {
            closeTabAt(side, idx)
        }
    }
    val closeManyOnPane: (PaneSide, List<Int>) -> Unit = { side, indices ->
        if (indices.isNotEmpty()) {
            val pane = paneOf(side)
            val closedPaths = indices.mapNotNull { i -> pane.book.tabs.getOrNull(i)?.path }
            mutatePane(side) { it.copy(book = it.book.closeMany(indices)) }
            closedPaths.forEach { p ->
                val stillOpen = primaryPane.book.tabs.any { it.path == p } ||
                    secondaryPane.book.tabs.any { it.path == p }
                if (!stillOpen) {
                    editorScrollByPath = EditorScrollMemory.clear(editorScrollByPath, p)
                    if (isKotlinSource(p)) lsp.didClose(p)
                }
            }
        }
    }
    val requestBatchClose: (PaneSide, List<Int>) -> Unit = { side, indices ->
        val pane = paneOf(side)
        val valid = indices.filter { it in pane.book.tabs.indices }
        val dirtyPairs = valid.mapNotNull { i ->
            val t = pane.book.tabs[i]
            if (isUnsavedText(t)) (side to t.path) else null
        }
        if (dirtyPairs.isEmpty()) {
            closeManyOnPane(side, valid)
        } else {
            val cleanIndices = valid.filter { i ->
                val t = pane.book.tabs[i]
                !isUnsavedText(t)
            }
            val allPaths = valid.map { side to pane.book.tabs[it].path }
            closeManyOnPane(side, cleanIndices)
            pendingClose = PendingClose.Batch(allPaths.filter { (s, p) ->
                val tab = paneOf(s).book.tabs.firstOrNull { it.path == p }
                tab != null && isUnsavedText(tab)
            })
        }
    }
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
    val activateAdjacentTab: (Int) -> Unit = { delta ->
        val side = focusedPane
        val book = paneOf(side).book
        val n = book.tabs.size
        if (n > 1) {
            val next = ((book.activeIndex + delta) % n + n) % n
            if (next != book.activeIndex) {
                mutatePane(side) { it.copy(book = it.book.activate(next)) }
            }
        }
    }
    val anyDirty: () -> Boolean = {
        primaryPane.book.tabs.any(isUnsavedText) || secondaryPane.book.tabs.any(isUnsavedText)
    }
    val requestExit: () -> Unit = {
        if (anyDirty()) pendingClose = PendingClose.App else exitApplication()
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
                        runCatching { lsp.applyExternalChange(tab.path.toUri().toString(), tab.text) }
                    }
                }
                for (tab in paneOf(otherSide).book.tabs) {
                    runCatching { lsp.applyExternalChange(tab.path.toUri().toString(), tab.text) }
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
                        runCatching { lsp.applyExternalChange(tab.path.toUri().toString(), tab.text) }
                    }
                }
                for (tab in paneOf(otherSide).book.tabs) {
                    runCatching { lsp.applyExternalChange(tab.path.toUri().toString(), tab.text) }
                }
            }
        }
    }

    val moveTabAcross: ((PaneSide, Int) -> Unit)? = if (splitEnabled) {
        { source, index ->
            val sourcePane = paneOf(source)
            val tab = sourcePane.book.tabs.getOrNull(index)
            if (tab != null) {
                val target = if (source == PaneSide.PRIMARY)
                    PaneSide.SECONDARY else PaneSide.PRIMARY
                mutatePane(source) { it.copy(book = it.book.close(index)) }
                mutatePane(target) { it.copy(book = it.book.appendTab(tab)) }
                focusedPane = target
            }
        }
    } else null

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
            onMoveToOtherPane = moveTabAcross?.let { fn -> { idx -> fn(side, idx) } },
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
            val activeList = currentLsp.diagnosticsFor(active.path).sortedWith(
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
                val anyEntry = currentLsp.diagnosticsByUri.entries
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
        val isKt = activePath?.let(::isKotlinSource) == true
        val status = currentLsp.status.value
        if (activePath != null
            && isKt
            && status == LspController.Status.READY
        ) {
            val uri = activePath.toUri().toString()
            currentLsp.documentSymbols(activePath).whenComplete { syms, err ->
                if (err == null && syms != null) {
                    documentSymbolUri = uri
                    documentSymbolList = syms
                    documentSymbolOpen = true
                }
            }
        }
    }
    val openWorkspaceSymbol: () -> Unit = {
        val status = currentLsp.status.value
        if (status == LspController.Status.READY) {
            workspaceSymbolOpen = true
        }
    }
    val triggerFormat: () -> Unit = {
        val active = focused().book.active
        val activePath = active?.path
        val isKt = activePath?.let(::isKotlinSource) == true
        if (activePath != null
            && isKt
            && currentLsp.status.value == LspController.Status.READY
        ) {
            currentLsp.formatting(activePath).whenComplete { edits, err ->
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
        val isKt = activePath?.let(::isKotlinSource) == true
        if (activePath != null
            && isKt
            && currentLsp.status.value == LspController.Status.READY
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
            currentLsp.codeActions(activePath, line, 0, line, lineLen).whenComplete { actions, err ->
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
        if (event.isCtrlPressed && event.isAltPressed && event.key == Key.T) {
            cyclePalette()
            return@handler true
        }
        if (event.isCtrlPressed) {
            when {
                event.key == Key.O && event.isShiftPressed -> {
                    if (frame != null) openFolder(frame); true
                }
                event.key == Key.O -> {
                    if (frame != null) openFile(frame); true
                }
                event.key == Key.S -> {
                    if (frame != null) saveFile(frame); true
                }
                event.key == Key.W -> { closeActiveTab(); true }
                event.key == Key.M && event.isShiftPressed -> {
                    problemsOpen = !problemsOpen
                    true
                }
                event.key == Key.Six && event.isShiftPressed -> {
                    todoOpen = !todoOpen
                    true
                }
                event.key == Key.F && event.isShiftPressed -> {
                    if (findInFiles) findInFiles = false else openFindInFiles()
                    true
                }
                event.key == Key.F -> { openSearch(); true }
                event.key == Key.R -> { openReplace(); true }
                event.key == Key.P -> { openQuickOpen(); true }
                event.key == Key.T -> { openWorkspaceSymbol(); true }
                event.key == Key.F12 -> { openDocumentSymbol(); true }
                event.key == Key.Backslash && event.isShiftPressed -> {
                    splitOrientation = if (splitOrientation == SplitOrientation.HORIZONTAL)
                        SplitOrientation.VERTICAL else SplitOrientation.HORIZONTAL
                    true
                }
                event.key == Key.Backslash -> {
                    splitEnabled = !splitEnabled
                    true
                }
                event.key == Key.Z && event.isShiftPressed -> {
                    if (focusedSearch != null) false
                    else {
                        val redoOp = fileOpHistory.peekRedo()
                        if (fileTreeFocused && redoOp != null) {
                            fileOpConfirm = FileOpConfirmState(isRedo = true, op = redoOp)
                        } else {
                            doRedo()
                        }
                        true
                    }
                }
                event.key == Key.Z -> {
                    if (focusedSearch != null) false
                    else {
                        val undoOp = fileOpHistory.peek()
                        if (fileTreeFocused && undoOp != null) {
                            fileOpConfirm = FileOpConfirmState(isRedo = false, op = undoOp)
                        } else {
                            doUndo()
                        }
                        true
                    }
                }
                event.key == Key.Y -> {
                    if (focusedSearch != null) false
                    else {
                        val redoOp = fileOpHistory.peekRedo()
                        if (fileTreeFocused && redoOp != null) {
                            fileOpConfirm = FileOpConfirmState(isRedo = true, op = redoOp)
                        } else {
                            doRedo()
                        }
                        true
                    }
                }
                else -> false
            }
        } else if (event.isAltPressed && event.isShiftPressed && !event.isCtrlPressed
            && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
            triggerFormat(); true
        } else if (event.isAltPressed && !event.isShiftPressed && !event.isCtrlPressed
            && focusedSearch == null
            && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
            triggerCodeAction(); true
        } else if (event.isAltPressed && !event.isShiftPressed && !event.isCtrlPressed
            && focusedSearch == null && event.key == Key.DirectionLeft) {
            activateAdjacentTab(-1); true
        } else if (event.isAltPressed && !event.isShiftPressed && !event.isCtrlPressed
            && focusedSearch == null && event.key == Key.DirectionRight) {
            activateAdjacentTab(1); true
        } else if (event.key == Key.F8) {
            jumpProblemRelative(!event.isShiftPressed)
            true
        } else if (event.key == Key.F5) {
            treeRevision++
            true
        } else if (event.key == Key.Escape && focusedSearch != null) {
            closeSearch(focusedPane); true
        } else false
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
                                println("[lsp] codeAction apply ▶ \"${pick.title}\" — ${pick.edit.changes.sumOf { it.edits.size }} edit(s)")
                                applyRename(pick.edit)
                            }
                            frameRef.value?.requestFocus()
                            editorFocusVersion += 1
                            return@KeyEventDispatcher true
                        }
                    }
                }
                when {
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
                } else Shell(
                    primary = primaryPane,
                    secondary = secondaryPane,
                    focusedPane = focusedPane,
                    lsp = currentLsp,
                    onPaneFocus = { side -> focusedPane = side },
                    onEditorChange = { side, v ->
                        mutatePane(side) {
                            val priorText = it.editorValue.text
                            val priorSelection = it.editorValue.selection
                            val textChanged = v.text != priorText
                            val tracker = undoTracker(side)
                            val nextBook = if (textChanged) {
                                val priorCaret = priorSelection.start
                                val shouldPush = tracker.onTextChange(priorText, v.text)
                                val withPush = if (shouldPush) {
                                    it.book.pushHistoryOnActive(EditSnapshot(priorText, priorCaret))
                                } else it.book
                                withPush.updateActive(v.text, v.selection.start)
                            } else {
                                if (v.selection != priorSelection) tracker.markBreak()
                                it.book.updateActive(v.text, v.selection.start)
                            }
                            val nextSearch = if (textChanged) {
                                it.search?.retarget(v.text)
                            } else it.search
                            it.copy(
                                editorValue = v,
                                book = nextBook,
                                search = nextSearch,
                            )
                        }
                    },
                    onActivateTab = { side, index ->
                        mutatePane(side) { it.copy(book = it.book.activate(index)) }
                    },
                    onCloseTab = { side, index -> requestCloseTab(side, index) },
                    onMoveTab = { side, from, to ->
                        mutatePane(side) { it.copy(book = it.book.move(from, to)) }
                    },
                    onMoveTabAcross = moveTabAcross,
                    rootDir = rootDir,
                    expanded = expanded,
                    treeSelection = treeSelection,
                    onTreeSelectionChange = { treeSelection = it },
                    treeRevision = treeRevision,
                    sidebarWidth = sidebarWidth,
                    onSidebarResize = { delta ->
                        sidebarWidth = (sidebarWidth + delta).coerceIn(160.dp, 600.dp)
                    },
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
                    splitEnabled = splitEnabled,
                    splitOrientation = splitOrientation,
                    splitState = splitState,
                    onSplitStateChange = { splitState = it },
                    problemsOpen = problemsOpen,
                    onProblemsToggle = { problemsOpen = !problemsOpen },
                    onProblemsClose = { problemsOpen = false },
                    onJumpToProblem = jumpToProblem,
                    onApplyRename = applyRename,
                    problemsHeight = problemsHeight,
                    onProblemsResizeDelta = { delta ->
                        problemsHeight = (problemsHeight + delta).coerceIn(80.dp, 600.dp)
                    },
                    problemsCollapsed = problemsCollapsed,
                    onProblemsCollapsedChange = { problemsCollapsed = it },
                    problemsFileOrder = problemsFileOrder,
                    onProblemsFileOrderChange = { problemsFileOrder = it },
                    todoOpen = todoOpen,
                    todoItems = todoItems,
                    onTodoToggle = { todoOpen = !todoOpen },
                    onTodoClose = { todoOpen = false },
                    todoHeight = todoHeight,
                    onTodoResizeDelta = { delta ->
                        todoHeight = (todoHeight + delta).coerceIn(80.dp, 600.dp)
                    },
                    todoCollapsed = todoCollapsed,
                    onTodoCollapsedChange = { todoCollapsed = it },
                    todoFileOrder = todoFileOrder,
                    onTodoFileOrderChange = { todoFileOrder = it },
                    terminalOpen = terminalOpen,
                    terminalManager = terminalManager,
                    onTerminalToggle = toggleTerminal,
                    onTerminalClose = { terminalOpen = false },
                    terminalHeight = terminalHeight,
                    onTerminalResizeDelta = { delta ->
                        terminalHeight = (terminalHeight + delta).coerceIn(120.dp, 600.dp)
                    },
                    runState = runState,
                    onSelectRunConfig = { id -> runState = runState.select(id) },
                    onStartRun = startActiveRun,
                    onStopRun = stopActiveRun,
                    onOpenRunDialog = openRunDialog,
                    runIsRunning = outputState.running,
                    outputOpen = outputOpen,
                    outputState = outputState,
                    onOutputToggle = { outputOpen = !outputOpen },
                    onOutputClose = { outputOpen = false },
                    onOutputClear = { outputState.clear() },
                    outputHeight = outputHeight,
                    onOutputResizeDelta = { delta ->
                        outputHeight = (outputHeight + delta).coerceIn(120.dp, 1200.dp)
                    },
                    referencesState = referencesState,
                    onRequestReferences = requestReferences,
                    onReferencesClose = { referencesState = null },
                    referencesHeight = referencesHeight,
                    onReferencesResizeDelta = { delta ->
                        referencesHeight = (referencesHeight + delta).coerceIn(80.dp, 600.dp)
                    },
                    linePreviewFor = { uri, line -> currentLsp.linePreviewFor(uri, line) },
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
                            println("[lsp] codeAction apply ▶ \"${action.title}\" — ${action.edit.changes.sumOf { it.edits.size }} edit(s)")
                            applyRename(action.edit)
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
    val impactScanner = remember(currentLsp) {
        ReferenceScanner(
            documentSymbols = { p -> currentLsp.documentSymbols(p) },
            references = { p, l, c -> currentLsp.references(p, l, c, includeDeclaration = false) },
            ensureOpen = { p ->
                val name = p.fileName?.toString().orEmpty()
                if (name.endsWith(".kt") || name.endsWith(".kts")) {
                    val text = runCatching { java.nio.file.Files.readString(p) }.getOrNull()
                    if (text != null) currentLsp.didOpen(p, "kotlin", text)
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
                        lsp.runWithClientDown("folder rename: ${oldPath.fileName} → $newName") {
                            withFileTreeWatcherClosed {
                                captured = performAndProcess()
                            }
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
                        val syms = runCatching {
                            currentLsp.documentSymbols(oldPath).await()
                        }.getOrDefault(emptyList())
                        val pick = FileSymbolRename.findRenamableTopLevelSymbol(oldStem!!, syms)
                        if (pick != null) {
                            val openText = paneOf(PaneSide.PRIMARY).book.tabs.firstOrNull { it.path == oldPath }?.text
                                ?: paneOf(PaneSide.SECONDARY).book.tabs.firstOrNull { it.path == oldPath }?.text
                            val fileText = openText ?: runCatching {
                                java.nio.file.Files.readString(oldPath)
                            }.getOrNull()
                            if (fileText != null) {
                                if (!currentLsp.isOpenAt(oldPath)) {
                                    runCatching { currentLsp.didOpen(oldPath, "kotlin", fileText) }
                                }
                                val candidatePaths = mutableListOf<java.nio.file.Path>()
                                rootDir?.let { root ->
                                    runCatching {
                                        java.nio.file.Files.walk(root).use { stream ->
                                            stream
                                                .filter { p -> java.nio.file.Files.isRegularFile(p) }
                                                .filter { p ->
                                                    val name = p.fileName?.toString().orEmpty()
                                                    name.endsWith(".kt") || name.endsWith(".kts")
                                                }
                                                .forEach { p ->
                                                    val norm = p.toAbsolutePath().normalize()
                                                    if (norm == oldPath.toAbsolutePath().normalize()) return@forEach
                                                    val text = runCatching {
                                                        java.nio.file.Files.readString(norm)
                                                    }.getOrNull() ?: return@forEach
                                                    if (!text.contains(oldStem)) return@forEach
                                                    candidatePaths.add(norm)
                                                    if (!currentLsp.isOpenAt(norm)) {
                                                        runCatching { currentLsp.didOpen(norm, "kotlin", text) }
                                                    }
                                                }
                                        }
                                    }
                                }
                                val edit = runCatching {
                                    currentLsp.rename(
                                        oldPath,
                                        fileText,
                                        pick.selectionRange.startLine,
                                        pick.selectionRange.startCharacter,
                                        newStem!!,
                                    ).await()
                                }.getOrNull()
                                val refs = runCatching {
                                    currentLsp.references(
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
                if (rest.isEmpty()) {
                    val pushedMoves = cur.movesSoFar
                    val pushedCopies = cur.createdSoFar
                    if (pushedMoves.isNotEmpty()) {
                        fileOpHistory.push(FileOpHistory.PasteCutOp(pushedMoves))
                        fileOpHistoryVersion++
                        FileTreeClipboard.clearCutMarking()
                        showDropResultToast(
                            dropResultMessage("Moved", pushedMoves.size, dropDestLabel(rootDir, cur.destParent)),
                            DropResultToastTone.Info,
                        ) { onUndoFileOp() }
                    } else if (pushedCopies.isNotEmpty()) {
                        fileOpHistory.push(FileOpHistory.PasteCopyOp(pushedCopies))
                        fileOpHistoryVersion++
                        showDropResultToast(
                            dropResultMessage("Copied", pushedCopies.size, dropDestLabel(rootDir, cur.destParent)),
                            DropResultToastTone.Info,
                        ) { onUndoFileOp() }
                    }
                }
            }
        } else null
        val skipAll: (() -> Unit)? = if (total > 1) {
            {
                val cur = activePasteDialog
                pasteDialog = null
                val pushedMoves = cur.movesSoFar
                val pushedCopies = cur.createdSoFar
                if (pushedMoves.isNotEmpty()) {
                    fileOpHistory.push(FileOpHistory.PasteCutOp(pushedMoves))
                    fileOpHistoryVersion++
                    FileTreeClipboard.clearCutMarking()
                    showDropResultToast(
                        dropResultMessage("Moved", pushedMoves.size, dropDestLabel(rootDir, cur.destParent)),
                        DropResultToastTone.Info,
                    ) { onUndoFileOp() }
                } else if (pushedCopies.isNotEmpty()) {
                    fileOpHistory.push(FileOpHistory.PasteCopyOp(pushedCopies))
                    fileOpHistoryVersion++
                    showDropResultToast(
                        dropResultMessage("Copied", pushedCopies.size, dropDestLabel(rootDir, cur.destParent)),
                        DropResultToastTone.Info,
                    ) { onUndoFileOp() }
                }
            }
        } else null
        val performPaste: (String, Boolean) -> Unit = performPaste@ { newName, overwriteOnce ->
            val current = pasteDialog ?: return@performPaste
            val src = current.remaining.first()
            val rest = current.remaining.drop(1)
            val overwriteFlag = current.overwriteForAll || overwriteOnce
            when (current.mode) {
                FileTreeClipboard.Mode.Copy -> {
                    val estimate = LargeCopyExecutor.estimate(src)
                    if (estimate.isLarge) {
                        val cancelToken = java.util.concurrent.atomic.AtomicBoolean(false)
                        val destLabel = current.destParent.fileName?.toString()
                            ?: current.destParent.toString()
                        largeCopyState = LargeCopyDialogState(
                            sourceName = src.fileName?.toString() ?: src.toString(),
                            destName = destLabel,
                            totalBytes = estimate.totalBytes,
                            fileCount = estimate.fileCount,
                            bytesCopied = 0L,
                            filesCopied = 0,
                            cancelToken = cancelToken,
                        )
                        pasteDialog = null
                        val capturedCurrent = current
                        val capturedRest = rest
                        val capturedName = newName
                        val capturedOverwrite = overwriteFlag
                        val bytesAcc = java.util.concurrent.atomic.AtomicLong(0L)
                        val filesAcc = java.util.concurrent.atomic.AtomicInteger(0)
                        largeCopyScope.launch {
                            val outcome = withContext(Dispatchers.IO) {
                                LargeCopyExecutor.copyWithProgress(
                                    source = src,
                                    destParent = capturedCurrent.destParent,
                                    newName = capturedName,
                                    isCancelled = { cancelToken.get() },
                                    onProgress = { deltaBytes, deltaFiles ->
                                        val nb = bytesAcc.addAndGet(deltaBytes)
                                        val nf = filesAcc.addAndGet(deltaFiles)
                                        largeCopyState?.let { snap ->
                                            largeCopyState = snap.copy(
                                                bytesCopied = nb,
                                                filesCopied = nf,
                                            )
                                        }
                                    },
                                    overwriteExisting = capturedOverwrite,
                                )
                            }
                            largeCopyState = null
                                when (outcome) {
                                    is LargeCopyExecutor.CopyOutcome.Ok -> {
                                        treeRevision++
                                        val nextCreated = capturedCurrent.createdSoFar +
                                            FileOpHistory.CopyEntry(source = src, dest = outcome.target)
                                        if (capturedRest.isEmpty()) {
                                            fileOpHistory.push(FileOpHistory.PasteCopyOp(nextCreated))
                                            fileOpHistoryVersion++
                                            val destLbl = dropDestLabel(rootDir, capturedCurrent.destParent)
                                            showDropResultToast(
                                                dropResultMessage("Copied", nextCreated.size, destLbl),
                                                DropResultToastTone.Info,
                                            ) { onUndoFileOp() }
                                        } else {
                                            pasteDialog = capturedCurrent.copy(
                                                remaining = capturedRest,
                                                error = null,
                                                createdSoFar = nextCreated,
                                            )
                                        }
                                    }
                                    is LargeCopyExecutor.CopyOutcome.Cancelled -> {
                                        treeRevision++
                                        if (capturedCurrent.createdSoFar.isNotEmpty()) {
                                            fileOpHistory.push(
                                                FileOpHistory.PasteCopyOp(capturedCurrent.createdSoFar)
                                            )
                                            fileOpHistoryVersion++
                                        }
                                    }
                                    is LargeCopyExecutor.CopyOutcome.Err -> {
                                        pasteDialog = capturedCurrent.copy(error = outcome.message)
                                    }
                                }
                            }
                        } else {
                            when (val result = FileTreeActions.copyFile(src, current.destParent, newName, overwriteFlag)) {
                                is FileTreeActions.CopyResult.Ok -> {
                                    treeRevision++
                                    val nextCreated = current.createdSoFar +
                                        FileOpHistory.CopyEntry(source = src, dest = result.path)
                                    if (rest.isEmpty()) {
                                        fileOpHistory.push(FileOpHistory.PasteCopyOp(nextCreated))
                                        fileOpHistoryVersion++
                                        pasteDialog = null
                                        val destLbl = dropDestLabel(rootDir, current.destParent)
                                        showDropResultToast(
                                            dropResultMessage("Copied", nextCreated.size, destLbl),
                                            DropResultToastTone.Info,
                                        ) { onUndoFileOp() }
                                    } else {
                                        pasteDialog = current.copy(
                                            remaining = rest,
                                            error = null,
                                            createdSoFar = nextCreated,
                                        )
                                    }
                                }
                                is FileTreeActions.CopyResult.Err -> {
                                    pasteDialog = current.copy(error = result.message)
                                }
                            }
                        }
                    }
                    FileTreeClipboard.Mode.Cut -> {
                        val srcIsDir = java.nio.file.Files.isDirectory(src)
                        val intendedNewPath = current.destParent.resolve(newName)
                        val packageMap = if (srcIsDir) {
                            val movingToDifferentParent = src.parent?.toAbsolutePath()?.normalize() !=
                                current.destParent.toAbsolutePath().normalize()
                            if (movingToDifferentParent) {
                                FolderPackageRename.computePackageMapForMove(
                                    oldFolder = src,
                                    newFolder = intendedNewPath,
                                    workspaceRoot = rootDir,
                                    readText = readFileTextWithTabs,
                                )
                            } else {
                                FolderPackageRename.computePackageMap(src, newName, readFileTextWithTabs)
                            }
                        } else emptyMap()
                        val singleFilePlan = if (!srcIsDir && isKotlinSource(src) &&
                            src.parent?.toAbsolutePath()?.normalize() !=
                                current.destParent.toAbsolutePath().normalize()) {
                            FolderPackageRename.planSingleFileMove(
                                oldFile = src,
                                newParent = current.destParent,
                                workspaceRoot = rootDir,
                                readText = readFileTextWithTabs,
                            )
                        } else null
                        var moveOriginals: List<FileOpHistory.RewriteEntry> = emptyList()
                        val performMove: () -> FileTreeActions.MoveResult = {
                            val txRoot = if (srcIsDir) rootDir else null
                            val r = FileTreeActions.moveFile(src, current.destParent, newName, txRoot, overwriteFlag)
                            if (r is FileTreeActions.MoveResult.Ok) {
                                remapTabsAfterRename(src, r.path)
                                remapTreeStateAfterRename(src, r.path)
                                if (srcIsDir) {
                                    moveOriginals = applyFolderPackageSync(src, r.path, packageMap)
                                } else if (singleFilePlan != null) {
                                    moveOriginals = applySingleFileMoveSync(r.path, singleFilePlan)
                                }
                            }
                            r
                        }
                        val result: FileTreeActions.MoveResult = if (srcIsDir) {
                            var captured: FileTreeActions.MoveResult? = null
                            lsp.runWithClientDown("folder move: ${src.fileName} → ${current.destParent.fileName}/$newName") {
                                withFileTreeWatcherClosed {
                                    captured = performMove()
                                }
                            }
                            captured ?: FileTreeActions.MoveResult.Err("move did not run")
                        } else {
                            performMove()
                        }
                        when (result) {
                            is FileTreeActions.MoveResult.Ok -> {
                                treeRevision++
                                val nextMoves = current.movesSoFar + (src to result.path)
                                val nextRewriteOriginals = current.rewriteOriginalsSoFar + moveOriginals
                                if (rest.isEmpty()) {
                                    val cutOp = FileOpHistory.PasteCutOp(nextMoves)
                                    val composedOp: FileOpHistory.Op = if (nextRewriteOriginals.isEmpty()) cutOp
                                        else FileOpHistory.CompositeOp(listOf(FileOpHistory.ReferenceRewriteOp(nextRewriteOriginals), cutOp))
                                    fileOpHistory.push(composedOp)
                                    fileOpHistoryVersion++
                                    FileTreeClipboard.clearCutMarking()
                                    pasteDialog = null
                                    val destLbl = dropDestLabel(rootDir, current.destParent)
                                    showDropResultToast(
                                        dropResultMessage("Moved", nextMoves.size, destLbl),
                                        DropResultToastTone.Info,
                                    ) { onUndoFileOp() }
                                } else {
                                    pasteDialog = current.copy(
                                        remaining = rest,
                                        error = null,
                                        movesSoFar = nextMoves,
                                        rewriteOriginalsSoFar = nextRewriteOriginals,
                                    )
                                }
                            }
                            is FileTreeActions.MoveResult.Err -> {
                                pasteDialog = current.copy(error = result.message)
                            }
                        }
                    }
                }
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
                runCatching { currentLsp.workspaceSymbolsLocated(q).await() }
                    .getOrDefault(emptyList())
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
                            if (isKotlinSource(p)) lsp.didClose(p)
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

private enum class CreateEntryKind { FILE, FOLDER }

private data class CreateEntryDialogState(
    val parent: Path,
    val kind: CreateEntryKind,
    val error: String? = null,
)

private data class RenameEntryDialogState(
    val path: Path,
    val error: String? = null,
)

private data class DeleteEntryDialogState(
    val paths: List<Path>,
) {
    val primary: Path get() = paths.first()
    val isMulti: Boolean get() = paths.size > 1
}

private data class PasteEntryDialogState(
    val remaining: List<Path>,
    val destParent: Path,
    val mode: FileTreeClipboard.Mode,
    val error: String? = null,
    val createdSoFar: List<FileOpHistory.CopyEntry> = emptyList(),
    val movesSoFar: List<Pair<Path, Path>> = emptyList(),
    val rewriteOriginalsSoFar: List<FileOpHistory.RewriteEntry> = emptyList(),
    val overwriteForAll: Boolean = false,
)

private data class LargeCopyDialogState(
    val sourceName: String,
    val destName: String,
    val totalBytes: Long,
    val fileCount: Int,
    val bytesCopied: Long,
    val filesCopied: Int,
    val cancelToken: java.util.concurrent.atomic.AtomicBoolean,
)

private data class FileOpConfirmState(
    val isRedo: Boolean,
    val op: FileOpHistory.Op,
)

private fun dropDestLabel(rootDir: Path?, dest: Path): String {
    val rel = FileTreeActions.relativeTo(rootDir, dest)
    return if (rel.isEmpty() || rel == ".") (dest.fileName?.toString() ?: dest.toString()) else rel
}

private fun dropResultMessage(verb: String, count: Int, destLabel: String): String =
    if (count == 1) "$verb 1 item into $destLabel"
    else "$verb $count items into $destLabel"

private fun windowTitle(path: Path?): String {
    val name = path?.fileName?.toString() ?: "untitled"
    return "$name — ${PageIdentity.NAME}"
}

private fun applyReplaceToBook(book: TabBook, updates: Map<Path, String>): TabBook {
    if (book.tabs.none { updates.containsKey(it.path) }) return book
    val newTabs = book.tabs.map { tab ->
        val newText = updates[tab.path] ?: return@map tab
        val caret = tab.caret.coerceAtMost(newText.length)
        tab.copy(text = newText, savedText = newText, caret = caret)
    }
    return book.copy(tabs = newTabs)
}

private fun offsetToLineChar(text: String, offset: Int): Pair<Int, Int> {
    val end = offset.coerceIn(0, text.length)
    var line = 0
    var col = 0
    for (i in 0 until end) {
        if (text[i] == '\n') { line += 1; col = 0 } else col += 1
    }
    return line to col
}

private fun isKotlinSource(path: Path): Boolean {
    val name = path.fileName?.toString()?.lowercase() ?: return false
    return name.endsWith(".kt") || name.endsWith(".kts")
}

private fun resolveLanguageForPath(path: Path): page.lsp.LanguageDefinition? {
    val name = path.fileName?.toString() ?: return null
    val dot = name.lastIndexOf('.')
    val ext = if (dot >= 0 && dot < name.length - 1) name.substring(dot + 1) else name
    return page.lsp.LanguageRegistry.byExtension(ext)
}

@androidx.compose.runtime.Composable
private fun lspStatusLineText(lsp: LspController, activePath: Path?): String? {
    val definition = activePath?.let(::resolveLanguageForPath)
    val langId = definition?.id ?: "kotlin"
    val displayName = definition?.displayName ?: "Kotlin"
    val isKotlin = langId == "kotlin"
    val installer = LspInstallers.forId(langId) ?: return when {
        isKotlin && lsp.status.value == LspController.Status.MISSING -> "LSP · kotlin-language-server missing"
        isKotlin && lsp.status.value == LspController.Status.FAILED -> "LSP · failed to start"
        else -> null
    }
    val installed = installer.installedVersion()
    val suffix = if (isKotlin) when (lsp.status.value) {
        LspController.Status.FAILED -> " · failed"
        LspController.Status.STARTING -> " · starting"
        LspController.Status.MISSING -> " · not installed"
        else -> ""
    } else ""
    val core = if (installed != null) "$displayName $installed" else "$displayName (not installed)"
    return "$core$suffix"
}

@Composable
private fun Shell(
    primary: EditorPaneState,
    secondary: EditorPaneState,
    focusedPane: PaneSide,
    lsp: LspController,
    onPaneFocus: (PaneSide) -> Unit,
    onEditorChange: (PaneSide, TextFieldValue) -> Unit,
    onActivateTab: (PaneSide, Int) -> Unit,
    onCloseTab: (PaneSide, Int) -> Unit,
    onMoveTab: (PaneSide, Int, Int) -> Unit,
    onMoveTabAcross: ((PaneSide, Int) -> Unit)?,
    rootDir: Path?,
    expanded: Set<Path>,
    treeSelection: Set<Path>,
    onTreeSelectionChange: (Set<Path>) -> Unit,
    treeRevision: Int,
    sidebarWidth: Dp,
    onSidebarResize: (Dp) -> Unit,
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
    splitEnabled: Boolean,
    splitOrientation: SplitOrientation,
    splitState: SplitPaneState,
    onSplitStateChange: (SplitPaneState) -> Unit,
    problemsOpen: Boolean,
    onProblemsToggle: () -> Unit,
    onProblemsClose: () -> Unit,
    onJumpToProblem: (Path, Int, Int) -> Unit,
    onApplyRename: (RenameWorkspaceEdit) -> Unit,
    problemsHeight: Dp,
    onProblemsResizeDelta: (Dp) -> Unit,
    problemsCollapsed: Set<String>,
    onProblemsCollapsedChange: (Set<String>) -> Unit,
    problemsFileOrder: List<String>,
    onProblemsFileOrderChange: (List<String>) -> Unit,
    todoOpen: Boolean,
    todoItems: List<page.editor.TodoItem>,
    onTodoToggle: () -> Unit,
    onTodoClose: () -> Unit,
    todoHeight: Dp,
    onTodoResizeDelta: (Dp) -> Unit,
    todoCollapsed: Set<String>,
    onTodoCollapsedChange: (Set<String>) -> Unit,
    todoFileOrder: List<String>,
    onTodoFileOrderChange: (List<String>) -> Unit,
    terminalOpen: Boolean,
    terminalManager: TerminalManager,
    onTerminalToggle: () -> Unit,
    onTerminalClose: () -> Unit,
    terminalHeight: Dp,
    onTerminalResizeDelta: (Dp) -> Unit,
    runState: RunConfigsState,
    onSelectRunConfig: (String) -> Unit,
    onStartRun: () -> Unit,
    onStopRun: () -> Unit,
    onOpenRunDialog: () -> Unit,
    runIsRunning: Boolean,
    outputOpen: Boolean,
    outputState: OutputPanelState,
    onOutputToggle: () -> Unit,
    onOutputClose: () -> Unit,
    onOutputClear: () -> Unit,
    outputHeight: Dp,
    onOutputResizeDelta: (Dp) -> Unit,
    referencesState: ReferencesQueryState?,
    onRequestReferences: (Path, Int, Int, String) -> Unit,
    onReferencesClose: () -> Unit,
    referencesHeight: Dp,
    onReferencesResizeDelta: (Dp) -> Unit,
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
) {
    var dragSourcePane: PaneSide? by remember { mutableStateOf(null) }
    val installGuideOpen by lsp.installGuideOpen.collectAsState()
    var jdkDialogOpen by remember { mutableStateOf(false) }
    val jdkActiveVersion = remember { mutableStateOf<String?>(null) }
    val jdkScope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            jdkActiveVersion.value = runCatching { JdkInstaller().activeVersion() }.getOrNull()
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
        TitleBar(
            path = paneFor(focusedPane, primary, secondary).book.active?.path,
            terminalOpen = terminalOpen,
            onTerminalToggle = onTerminalToggle,
            runState = runState,
            activeFilePath = paneFor(focusedPane, primary, secondary).book.active?.path,
            onSelectRunConfig = onSelectRunConfig,
            runIsRunning = runIsRunning,
            onStartRun = onStartRun,
            onStopRun = onStopRun,
            onOpenRunDialog = onOpenRunDialog,
            outputOpen = outputOpen,
            onOutputToggle = onOutputToggle,
        )
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            FileTreePanel(
                root = rootDir,
                expanded = expanded,
                selection = treeSelection,
                onToggle = onToggle,
                onSelectionChange = onTreeSelectionChange,
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
                revision = treeRevision,
                modifier = Modifier.width(sidebarWidth).fillMaxHeight(),
            )
            ResizeHandle(onSidebarResize)
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                if (splitEnabled) {
                    SplitPane(
                        state = splitState,
                        onStateChange = onSplitStateChange,
                        orientation = splitOrientation,
                        modifier = Modifier.fillMaxSize(),
                        firstZIndex = if (dragSourcePane == PaneSide.PRIMARY) 1f else 0f,
                        secondZIndex = if (dragSourcePane == PaneSide.SECONDARY) 1f else 0f,
                        first = {
                            PaneRegion(
                                pane = primary,
                                side = PaneSide.PRIMARY,
                                lsp = lsp,
                                isFocused = focusedPane == PaneSide.PRIMARY,
                                onPaneFocus = onPaneFocus,
                                onEditorChange = onEditorChange,
                                onActivateTab = onActivateTab,
                                onCloseTab = onCloseTab,
                                onMoveTab = onMoveTab,
                                onMoveTabAcross = onMoveTabAcross,
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
                                onProblemsToggle = onProblemsToggle,
                                onJumpToProblem = onJumpToProblem,
                                onApplyRename = onApplyRename,
                                onRequestReferences = onRequestReferences,
                                todoCount = todoItems.size,
                                onTodoToggle = onTodoToggle,
                                workspaceRoot = rootDir,
                                editorFocusVersion = if (focusedPane == PaneSide.PRIMARY) editorFocusVersion else 0,
                                initialFoldedStartLines = foldedLinesFor(primary.book.active?.path),
                                onFoldStartLinesChange = onFoldChange,
                                editorScrollFor = editorScrollFor,
                                onEditorScrollChange = onEditorScrollChange,
                                tabContextActions = tabContextActionsFor(PaneSide.PRIMARY),
                                jdkVersion = jdkActiveVersion.value,
                                onJdkVersionClick = { jdkDialogOpen = true },
                                modifier = Modifier.fillMaxSize(),
                            )
                        },
                        second = {
                            PaneRegion(
                                pane = secondary,
                                side = PaneSide.SECONDARY,
                                lsp = lsp,
                                isFocused = focusedPane == PaneSide.SECONDARY,
                                onPaneFocus = onPaneFocus,
                                onEditorChange = onEditorChange,
                                onActivateTab = onActivateTab,
                                onCloseTab = onCloseTab,
                                onMoveTab = onMoveTab,
                                onMoveTabAcross = onMoveTabAcross,
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
                                onProblemsToggle = onProblemsToggle,
                                onJumpToProblem = onJumpToProblem,
                                onApplyRename = onApplyRename,
                                onRequestReferences = onRequestReferences,
                                todoCount = todoItems.size,
                                onTodoToggle = onTodoToggle,
                                workspaceRoot = rootDir,
                                editorFocusVersion = if (focusedPane == PaneSide.SECONDARY) editorFocusVersion else 0,
                                initialFoldedStartLines = foldedLinesFor(secondary.book.active?.path),
                                onFoldStartLinesChange = onFoldChange,
                                editorScrollFor = editorScrollFor,
                                onEditorScrollChange = onEditorScrollChange,
                                tabContextActions = tabContextActionsFor(PaneSide.SECONDARY),
                                jdkVersion = jdkActiveVersion.value,
                                onJdkVersionClick = { jdkDialogOpen = true },
                                modifier = Modifier.fillMaxSize(),
                            )
                        },
                    )
                } else {
                    PaneRegion(
                        pane = primary,
                        side = PaneSide.PRIMARY,
                        lsp = lsp,
                        isFocused = true,
                        onPaneFocus = onPaneFocus,
                        onEditorChange = onEditorChange,
                        onActivateTab = onActivateTab,
                        onCloseTab = onCloseTab,
                        onMoveTab = onMoveTab,
                        onMoveTabAcross = null,
                        onQueryChange = onQueryChange,
                        onReplaceChange = onReplaceChange,
                        onToggleCase = onToggleCase,
                        onSearchNext = onSearchNext,
                        onSearchPrev = onSearchPrev,
                        onReplace = onReplace,
                        onReplaceAll = onReplaceAll,
                        onSearchClose = onSearchClose,
                        onWindowShortcut = onWindowShortcut,
                        onProblemsToggle = onProblemsToggle,
                        onJumpToProblem = onJumpToProblem,
                        onApplyRename = onApplyRename,
                        onRequestReferences = onRequestReferences,
                        todoCount = todoItems.size,
                        onTodoToggle = onTodoToggle,
                        workspaceRoot = rootDir,
                        editorFocusVersion = editorFocusVersion,
                        initialFoldedStartLines = foldedLinesFor(primary.book.active?.path),
                        onFoldStartLinesChange = onFoldChange,
                        editorScrollFor = editorScrollFor,
                        onEditorScrollChange = onEditorScrollChange,
                        tabContextActions = tabContextActionsFor(PaneSide.PRIMARY),
                        jdkVersion = jdkActiveVersion.value,
                        onJdkVersionClick = { jdkDialogOpen = true },
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
        if (problemsOpen) {
            ProblemsPanel(
                diagnostics = lsp.diagnosticsByUri,
                onJump = onJumpToProblem,
                onClose = onProblemsClose,
                height = problemsHeight,
                onResizeDelta = onProblemsResizeDelta,
                collapsedKeys = problemsCollapsed,
                onCollapsedKeysChange = onProblemsCollapsedChange,
                fileOrder = problemsFileOrder,
                onFileOrderChange = onProblemsFileOrderChange,
            )
        }
        if (todoOpen) {
            TodoPanel(
                items = todoItems,
                onJump = onJumpToProblem,
                onClose = onTodoClose,
                height = todoHeight,
                onResizeDelta = onTodoResizeDelta,
                collapsedKeys = todoCollapsed,
                onCollapsedKeysChange = onTodoCollapsedChange,
                fileOrder = todoFileOrder,
                onFileOrderChange = onTodoFileOrderChange,
            )
        }
        if (referencesState != null) {
            ReferencesPanel(
                state = referencesState,
                onJump = onJumpToProblem,
                onClose = onReferencesClose,
                height = referencesHeight,
                onResizeDelta = onReferencesResizeDelta,
                linePreviewFor = linePreviewFor,
            )
        }
        if (terminalOpen) {
            TerminalPanel(
                manager = terminalManager,
                onPanelClose = onTerminalClose,
                height = terminalHeight,
                onResizeDelta = onTerminalResizeDelta,
            )
        }
        if (outputOpen) {
            OutputPanel(
                state = outputState,
                onClose = onOutputClose,
                onClear = onOutputClear,
                onStop = onStopRun,
                height = outputHeight,
                onResizeDelta = onOutputResizeDelta,
            )
        }
    }
    if (installGuideOpen) {
        val activeDef = paneFor(focusedPane, primary, secondary).book.active?.path
            ?.let { resolveLanguageForPath(it) }
        val def = activeDef
            ?: lsp.missingDefinition.value
            ?: page.lsp.LanguageRegistry.byId("kotlin")
        if (def != null) {
            InstallGuideDialog(
                definition = def,
                attempted = lsp.missingAttempted.value,
                onDismiss = { lsp.closeInstallGuide() },
                onInstalled = { lsp.retry() },
            )
        } else {
            lsp.closeInstallGuide()
        }
    }
    if (jdkDialogOpen) {
        val jdkDef = page.lsp.LanguageDefinition(
            id = "jdk",
            displayName = "Eclipse Temurin JDK",
            extensions = listOf("java"),
            lspBinaries = emptyList(),
            lspWindowsBinaries = emptyList(),
            installGuideUrl = "https://adoptium.net/",
            install = emptyMap(),
            runCommand = null,
        )
        InstallGuideDialog(
            definition = jdkDef,
            attempted = emptyList(),
            onDismiss = { jdkDialogOpen = false },
            onInstalled = {
                jdkScope.launch {
                    withContext(Dispatchers.IO) {
                        jdkActiveVersion.value = runCatching { JdkInstaller().activeVersion() }.getOrNull()
                    }
                }
            },
        )
    }
    }
}

private fun paneFor(side: PaneSide, primary: EditorPaneState, secondary: EditorPaneState) =
    if (side == PaneSide.PRIMARY) primary else secondary

@Composable
private fun PaneRegion(
    pane: EditorPaneState,
    side: PaneSide,
    lsp: LspController,
    isFocused: Boolean,
    onPaneFocus: (PaneSide) -> Unit,
    onEditorChange: (PaneSide, TextFieldValue) -> Unit,
    onActivateTab: (PaneSide, Int) -> Unit,
    onCloseTab: (PaneSide, Int) -> Unit,
    onMoveTab: (PaneSide, Int, Int) -> Unit,
    onMoveTabAcross: ((PaneSide, Int) -> Unit)?,
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
    jdkVersion: String? = null,
    onJdkVersionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val active = pane.book.active
    val kind = active?.let { FileKinds.classify(it.path) }
    val activeLexer = active?.path?.let { SyntaxLexers.forPath(it) }
    val isJavaFile = remember(active?.path) {
        active?.path?.fileName?.toString()?.lowercase()?.endsWith(".java") == true
    }
    val effectiveJdkVersion = if (isJavaFile) jdkVersion else null
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
                onActivateTab(side, idx)
            },
            onClose = { idx -> onCloseTab(side, idx) },
            onMove = { from, to -> onMoveTab(side, from, to) },
            onMoveToOtherPane = onMoveTabAcross?.let { fn -> { idx -> fn(side, idx) } },
            crossPaneSide = if (onMoveTabAcross == null) null else when (side) {
                PaneSide.PRIMARY -> CrossPaneSide.RIGHT
                PaneSide.SECONDARY -> CrossPaneSide.LEFT
            },
            onDragStart = onTabDragStart,
            onDragEnd = onTabDragEnd,
            contextActions = tabContextActions,
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
                onValueChange = { v -> onEditorChange(side, v) },
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
                val activeDiagnostics = active.path.let { lsp.diagnosticsFor(it) }
                val lspStatusText = lspStatusLineText(lsp, active?.path)
                val lspActivities = lsp.activities.values
                    .sortedBy { it.startedAtMs }
                    .toList()
                EditorPanel(
                    value = pane.editorValue,
                    onValueChange = { v -> onEditorChange(side, v) },
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
                    onLspStatusClick = { lsp.openInstallGuide() },
                    onProblemsToggle = onProblemsToggle,
                    todoCount = todoCount,
                    onTodoToggle = onTodoToggle,
                    onRequestCompletion = active?.path?.let { p ->
                        { line, ch, trig -> lsp.completion(p, pane.editorValue.text, line, ch, trig) }
                    },
                    onRequestHover = active?.path?.let { p ->
                        { line, ch -> lsp.hover(p, line, ch) }
                    },
                    onRequestDefinition = active?.path?.let { p ->
                        { line, ch -> lsp.definition(p, line, ch) }
                    },
                    onRequestSignatureHelp = active?.path?.let { p ->
                        { line, ch, trig, retrig -> lsp.signatureHelp(p, pane.editorValue.text, line, ch, trig, retrig) }
                    },
                    onGoToDefinition = { target ->
                        val path = runCatching {
                            java.nio.file.Paths.get(java.net.URI(target.uri))
                        }.getOrNull()
                        if (path != null) onJumpToProblem(path, target.startLine, target.startCharacter)
                    },
                    onRequestPrepareRename = active?.path?.let { p ->
                        { line, ch -> lsp.prepareRename(p, line, ch) }
                    },
                    onRequestRename = active?.path?.let { p ->
                        { line, ch, name -> lsp.rename(p, pane.editorValue.text, line, ch, name) }
                    },
                    onApplyRename = onApplyRename,
                    onRequestReferences = active?.path?.let { p ->
                        { line, ch, sym -> onRequestReferences(p, line, ch, sym) }
                    },
                    onRequestInlayHints = active?.path
                        ?.takeIf { lsp.status.value == LspController.Status.READY }
                        ?.let { p ->
                            { sl, sc, el, ec -> lsp.inlayHints(p, sl, sc, el, ec) }
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
                    jdkVersion = jdkVersion,
                    onJdkVersionClick = onJdkVersionClick,
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
private fun TitleBar(
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
private fun ResizeHandle(onDeltaDp: (Dp) -> Unit) {
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
