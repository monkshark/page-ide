package page.app

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
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
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import page.core.PageIdentity
import page.editor.EditSnapshot
import page.editor.FileDocument
import page.editor.FileKind
import page.editor.FileKinds
import page.editor.IndexedFile
import page.editor.OpenTab
import page.editor.ProjectFileIndex
import page.editor.Replace
import page.editor.SearchState
import page.editor.SplitOrientation
import page.editor.SplitPaneState
import page.editor.SyntaxLexers
import page.editor.TabBook
import page.ui.GlassTheme
import page.ui.SplitPane
import java.awt.Cursor
import java.nio.file.Path

fun main() = application {
    val windowState = rememberWindowState(width = 1280.dp, height = 800.dp)
    var primaryPane: EditorPaneState by remember { mutableStateOf(EditorPaneState()) }
    var secondaryPane: EditorPaneState by remember { mutableStateOf(EditorPaneState()) }
    var focusedPane: PaneSide by remember { mutableStateOf(PaneSide.PRIMARY) }
    var rootDir: Path? by remember { mutableStateOf(null) }
    var expanded: Set<Path> by remember { mutableStateOf(emptySet()) }
    var sidebarWidth: Dp by remember { mutableStateOf(260.dp) }
    var pendingClose: PendingClose? by remember { mutableStateOf(null) }
    var quickOpen by remember { mutableStateOf(false) }
    var quickOpenIndex by remember { mutableStateOf<List<IndexedFile>>(emptyList()) }
    var splitEnabled by remember { mutableStateOf(false) }
    var splitOrientation by remember { mutableStateOf(SplitOrientation.HORIZONTAL) }
    var splitState by remember { mutableStateOf(SplitPaneState(ratio = 0.5f)) }

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
        secondaryPane = secondaryPane.copy(
            editorValue = newValue,
            search = secondaryPane.search?.retarget(newValue.text),
        )
    }

    LaunchedEffect(splitEnabled) {
        if (!splitEnabled) focusedPane = PaneSide.PRIMARY
    }

    val openInTab: (Path) -> Unit = { picked ->
        val kind = FileKinds.classify(picked)
        if (kind.isEditableAsText) {
            FileDocument.loadOrNull(picked)?.let { text ->
                mutateFocused { it.copy(book = it.book.openOrFocus(picked, text)) }
            }
        } else {
            mutateFocused { it.copy(book = it.book.openOrFocus(picked, "")) }
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
            rootDir = picked
            expanded = setOf(picked)
        }
    }
    val toggleExpanded: (Path) -> Unit = { p ->
        expanded = if (p in expanded) expanded - setOf(p) else expanded + setOf(p)
    }
    val closeTabAt: (PaneSide, Int) -> Unit = { side, idx ->
        mutatePane(side) { it.copy(book = it.book.close(idx)) }
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
    val closeActiveTab: () -> Unit = {
        val side = focusedPane
        val idx = paneOf(side).book.activeIndex
        if (idx in paneOf(side).book.tabs.indices) requestCloseTab(side, idx)
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
        }
    }
    val onReplaceAll: (PaneSide) -> Unit = { side ->
        val pane = paneOf(side)
        val s = pane.search
        if (s != null && s.matches.isNotEmpty()) {
            val text = pane.editorValue.text
            val caret = pane.editorValue.selection.start
            val r = Replace.applyAll(text, s.matches, s.replace)
            mutatePane(side) {
                it.copy(
                    book = it.book
                        .pushHistoryOnActive(EditSnapshot(text, caret))
                        .updateActive(r.text, r.caret),
                    editorValue = TextFieldValue(r.text, TextRange(r.caret)),
                    search = s.retarget(r.text),
                )
            }
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
            mutatePane(side) {
                it.copy(
                    book = newBook,
                    editorValue = TextFieldValue(restored.text, TextRange(caret)),
                    search = it.search?.retarget(restored.text),
                )
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
            mutatePane(side) {
                it.copy(
                    book = newBook,
                    editorValue = TextFieldValue(restored.text, TextRange(caret)),
                    search = it.search?.retarget(restored.text),
                )
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

    val openQuickOpen: () -> Unit = {
        val root = rootDir
        if (root != null) {
            quickOpenIndex = ProjectFileIndex.walk(root)
            quickOpen = true
        }
    }

    val frameRef = remember { mutableStateOf<java.awt.Frame?>(null) }
    val handleShortcut: (KeyEvent) -> Boolean = handler@{ event ->
        if (event.type != KeyEventType.KeyDown) return@handler false
        val frame = frameRef.value
        val focusedSearch = focused().search
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
                event.key == Key.F -> { openSearch(); true }
                event.key == Key.R -> { openReplace(); true }
                event.key == Key.P -> { openQuickOpen(); true }
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
                    if (focusedSearch != null) false else { doRedo(); true }
                }
                event.key == Key.Z -> {
                    if (focusedSearch != null) false else { doUndo(); true }
                }
                event.key == Key.Y -> {
                    if (focusedSearch != null) false else { doRedo(); true }
                }
                else -> false
            }
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
        LaunchedEffect(Unit) { frameRef.value = window }
        GlassTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Shell(
                    primary = primaryPane,
                    secondary = secondaryPane,
                    focusedPane = focusedPane,
                    onPaneFocus = { side -> focusedPane = side },
                    onEditorChange = { side, v ->
                        mutatePane(side) {
                            val textChanged = v.text != it.editorValue.text
                            val nextBook = if (textChanged) {
                                val priorText = it.editorValue.text
                                val priorCaret = it.editorValue.selection.start
                                it.book
                                    .pushHistoryOnActive(EditSnapshot(priorText, priorCaret))
                                    .updateActive(v.text, v.selection.start)
                            } else {
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
                    sidebarWidth = sidebarWidth,
                    onSidebarResize = { delta ->
                        sidebarWidth = (sidebarWidth + delta).coerceIn(160.dp, 600.dp)
                    },
                    onToggle = toggleExpanded,
                    onOpenFile = openInTab,
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
                )
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
        }
        val targetNames = targets.map { (_, _, t) ->
            t.path.fileName?.toString() ?: t.path.toString()
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
                }
            },
            onDiscard = {
                pendingClose = null
                when (current) {
                    is PendingClose.Tab -> closeTabAt(current.side, current.index)
                    PendingClose.App -> exitApplication()
                }
            },
            onCancel = { pendingClose = null },
        )
    }
}

private fun windowTitle(path: Path?): String {
    val name = path?.fileName?.toString() ?: "untitled"
    return "$name — ${PageIdentity.NAME}"
}

@Composable
private fun Shell(
    primary: EditorPaneState,
    secondary: EditorPaneState,
    focusedPane: PaneSide,
    onPaneFocus: (PaneSide) -> Unit,
    onEditorChange: (PaneSide, TextFieldValue) -> Unit,
    onActivateTab: (PaneSide, Int) -> Unit,
    onCloseTab: (PaneSide, Int) -> Unit,
    onMoveTab: (PaneSide, Int, Int) -> Unit,
    onMoveTabAcross: ((PaneSide, Int) -> Unit)?,
    rootDir: Path?,
    expanded: Set<Path>,
    sidebarWidth: Dp,
    onSidebarResize: (Dp) -> Unit,
    onToggle: (Path) -> Unit,
    onOpenFile: (Path) -> Unit,
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
) {
    var dragSourcePane: PaneSide? by remember { mutableStateOf(null) }
    Column(modifier = Modifier.fillMaxSize()) {
        TitleBar(path = paneFor(focusedPane, primary, secondary).book.active?.path)
        Row(modifier = Modifier.fillMaxSize()) {
            FileTreePanel(
                root = rootDir,
                expanded = expanded,
                selectedFile = paneFor(focusedPane, primary, secondary).book.active?.path,
                onToggle = onToggle,
                onOpenFile = onOpenFile,
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
                                modifier = Modifier.fillMaxSize(),
                            )
                        },
                        second = {
                            PaneRegion(
                                pane = secondary,
                                side = PaneSide.SECONDARY,
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
                                modifier = Modifier.fillMaxSize(),
                            )
                        },
                    )
                } else {
                    PaneRegion(
                        pane = primary,
                        side = PaneSide.PRIMARY,
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
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

private fun paneFor(side: PaneSide, primary: EditorPaneState, secondary: EditorPaneState) =
    if (side == PaneSide.PRIMARY) primary else secondary

@Composable
private fun PaneRegion(
    pane: EditorPaneState,
    side: PaneSide,
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
    modifier: Modifier = Modifier,
) {
    val active = pane.book.active
    val kind = active?.let { FileKinds.classify(it.path) }
    val activeLexer = active?.path?.let { SyntaxLexers.forPath(it) }
    Column(
        modifier = modifier
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
            else -> EditorPanel(
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
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
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
private fun TitleBar(path: Path?) {
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
