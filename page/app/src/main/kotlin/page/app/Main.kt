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
import androidx.compose.runtime.rememberUpdatedState
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
import page.editor.EditHistory
import page.editor.EditSnapshot
import page.editor.FileDocument
import page.editor.UndoGroupTracker
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
import page.lsp.RenameApply
import page.lsp.RenameWorkspaceEdit
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
    var findInFiles by remember { mutableStateOf(false) }
    var findInFilesIndex by remember { mutableStateOf<List<IndexedFile>>(emptyList()) }
    var splitEnabled by remember { mutableStateOf(false) }
    var splitOrientation by remember { mutableStateOf(SplitOrientation.HORIZONTAL) }
    var splitState by remember { mutableStateOf(SplitPaneState(ratio = 0.5f)) }
    var problemsOpen by remember { mutableStateOf(false) }
    var problemsHeight: Dp by remember { mutableStateOf(220.dp) }
    var referencesState: ReferencesQueryState? by remember { mutableStateOf(null) }
    var referencesHeight: Dp by remember { mutableStateOf(220.dp) }
    val lsp = rememberLspController(workspaceRoot = rootDir)
    val currentLsp by rememberUpdatedState(lsp)
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

    LaunchedEffect(rootDir) {
        if (rootDir != null) lsp.ensureStarted()
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
                }
            }
        }
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
                referencesState = if (err != null) {
                    ReferencesQueryState(
                        symbolName = symbol,
                        originUri = origin,
                        results = emptyList(),
                        isLoading = false,
                        errorMessage = err.message?.lineSequence()?.firstOrNull()?.take(160)
                            ?: "참조 검색 실패",
                    )
                } else {
                    ReferencesQueryState(
                        symbolName = symbol,
                        originUri = origin,
                        results = results.orEmpty(),
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
            rootDir = picked
            expanded = setOf(picked) + page.editor.FileTree.singleChildChain(picked)
        }
    }
    val newFile: (java.awt.Frame) -> Unit = { parent ->
        val target = FileDialogs.saveAs(parent)
        if (target != null) {
            FileDocument.save(target, "")
            openInTab(target)
        }
    }
    val toggleExpanded: (Path) -> Unit = { p ->
        expanded = if (p in expanded) {
            expanded - setOf(p)
        } else {
            expanded + setOf(p) + page.editor.FileTree.singleChildChain(p)
        }
    }
    val closeTabAt: (PaneSide, Int) -> Unit = { side, idx ->
        val tab = paneOf(side).book.tabs.getOrNull(idx)
        mutatePane(side) { it.copy(book = it.book.close(idx)) }
        if (tab != null && isKotlinSource(tab.path)) {
            val stillOpenAnywhere = primaryPane.book.tabs.any { it.path == tab.path } ||
                secondaryPane.book.tabs.any { it.path == tab.path }
            if (!stillOpenAnywhere) lsp.didClose(tab.path)
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
                event.key == Key.M && event.isShiftPressed -> {
                    problemsOpen = !problemsOpen
                    true
                }
                event.key == Key.F && event.isShiftPressed -> {
                    if (findInFiles) findInFiles = false else openFindInFiles()
                    true
                }
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
        } else if (event.key == Key.F8) {
            jumpProblemRelative(!event.isShiftPressed)
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
        LaunchedEffect(Unit) { frameRef.value = window }
        val showWelcome = rootDir == null &&
            primaryPane.book.tabs.isEmpty() &&
            secondaryPane.book.tabs.isEmpty()
        GlassTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                if (showWelcome) {
                    WelcomeScreen(
                        onOpenFolder = { frameRef.value?.let { openFolder(it) } },
                        onNewFile = { frameRef.value?.let { newFile(it) } },
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
                    problemsOpen = problemsOpen,
                    onProblemsToggle = { problemsOpen = !problemsOpen },
                    onProblemsClose = { problemsOpen = false },
                    onJumpToProblem = jumpToProblem,
                    onApplyRename = applyRename,
                    problemsHeight = problemsHeight,
                    onProblemsResizeDelta = { delta ->
                        problemsHeight = (problemsHeight + delta).coerceIn(80.dp, 600.dp)
                    },
                    referencesState = referencesState,
                    onRequestReferences = requestReferences,
                    onReferencesClose = { referencesState = null },
                    referencesHeight = referencesHeight,
                    onReferencesResizeDelta = { delta ->
                        referencesHeight = (referencesHeight + delta).coerceIn(80.dp, 600.dp)
                    },
                    linePreviewFor = { uri, line -> currentLsp.linePreviewFor(uri, line) },
                )
                if (findInFiles) {
                    FindInFilesDialog(
                        files = findInFilesIndex,
                        onPickAt = { path, offset ->
                            findInFiles = false
                            openInTabAt(path, offset)
                        },
                        onDismiss = { findInFiles = false },
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

private fun isKotlinSource(path: Path): Boolean {
    val name = path.fileName?.toString()?.lowercase() ?: return false
    return name.endsWith(".kt") || name.endsWith(".kts")
}

@androidx.compose.runtime.Composable
private fun lspStatusLineText(lsp: LspController): String? = when (lsp.status.value) {
    LspController.Status.MISSING -> "LSP · kotlin-language-server 누락"
    LspController.Status.FAILED -> "LSP · 시작 실패"
    else -> null
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
    problemsOpen: Boolean,
    onProblemsToggle: () -> Unit,
    onProblemsClose: () -> Unit,
    onJumpToProblem: (Path, Int, Int) -> Unit,
    onApplyRename: (RenameWorkspaceEdit) -> Unit,
    problemsHeight: Dp,
    onProblemsResizeDelta: (Dp) -> Unit,
    referencesState: ReferencesQueryState?,
    onRequestReferences: (Path, Int, Int, String) -> Unit,
    onReferencesClose: () -> Unit,
    referencesHeight: Dp,
    onReferencesResizeDelta: (Dp) -> Unit,
    linePreviewFor: (String, Int) -> String?,
) {
    var dragSourcePane: PaneSide? by remember { mutableStateOf(null) }
    Column(modifier = Modifier.fillMaxSize()) {
        TitleBar(path = paneFor(focusedPane, primary, secondary).book.active?.path)
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        if (problemsOpen) {
            ProblemsPanel(
                diagnostics = lsp.diagnosticsByUri,
                onJump = onJumpToProblem,
                onClose = onProblemsClose,
                height = problemsHeight,
                onResizeDelta = onProblemsResizeDelta,
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
            else -> {
                val activeDiagnostics = active?.path?.let { lsp.diagnosticsFor(it) }.orEmpty()
                val lspStatusText = lspStatusLineText(lsp)
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
                    onProblemsToggle = onProblemsToggle,
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
