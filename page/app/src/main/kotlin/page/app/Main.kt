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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import page.editor.FileDocument
import page.editor.FileKind
import page.editor.FileKinds
import page.editor.SearchState
import page.editor.SyntaxLexers
import page.editor.TabBook
import page.ui.GlassTheme
import java.awt.Cursor
import java.nio.file.Path

fun main() = application {
    val windowState = rememberWindowState(width = 1280.dp, height = 800.dp)
    var book: TabBook by remember { mutableStateOf(TabBook()) }
    var editorValue by remember { mutableStateOf(TextFieldValue("")) }
    var rootDir: Path? by remember { mutableStateOf(null) }
    var expanded: Set<Path> by remember { mutableStateOf(emptySet()) }
    var sidebarWidth: Dp by remember { mutableStateOf(260.dp) }
    var search: SearchState? by remember { mutableStateOf(null) }

    LaunchedEffect(book.activeIndex, book.tabs.size) {
        val active = book.active
        editorValue = if (active != null) {
            val caret = active.caret.coerceIn(0, active.text.length)
            TextFieldValue(active.text, TextRange(caret))
        } else {
            TextFieldValue("")
        }
        search = search?.retarget(editorValue.text)
    }

    val openInTab: (Path) -> Unit = { picked ->
        when (FileKinds.classify(picked)) {
            FileKind.TEXT -> FileDocument.loadOrNull(picked)?.let { text ->
                book = book.openOrFocus(picked, text)
            }
            FileKind.IMAGE, FileKind.SVG -> {
                book = book.openOrFocus(picked, "")
            }
        }
    }

    val openFile: (java.awt.Frame) -> Unit = { parent ->
        FileDialogs.open(parent)?.let { picked -> openInTab(picked) }
    }
    val saveFile: (java.awt.Frame) -> Unit = { parent ->
        val active = book.active
        if (active != null) {
            if (FileKinds.classify(active.path) == FileKind.TEXT) {
                FileDocument.save(active.path, editorValue.text)
                book = book
                    .updateActive(editorValue.text, editorValue.selection.start)
                    .markActiveSaved()
            }
        } else {
            val target = FileDialogs.saveAs(parent)
            if (target != null) {
                FileDocument.save(target, editorValue.text)
                book = book.openOrFocus(target, editorValue.text)
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
    val closeActiveTab: () -> Unit = {
        book = book.closeActive()
    }
    val moveCaretToActiveMatch: (SearchState) -> Unit = { s ->
        val range = s.active
        if (range != null) {
            val start = range.first.coerceIn(0, editorValue.text.length)
            val end = (range.last + 1).coerceIn(start, editorValue.text.length)
            editorValue = editorValue.copy(selection = TextRange(start, end))
        }
    }
    val openSearch: () -> Unit = {
        if (book.active != null && FileKinds.classify(book.active!!.path) == FileKind.TEXT) {
            val initial = SearchState().withQuery(editorValue.text, "")
            search = initial
        }
    }
    val closeSearch: () -> Unit = { search = null }
    val onQueryChange: (String) -> Unit = { q ->
        val updated = (search ?: SearchState()).withQuery(editorValue.text, q)
        search = updated
        moveCaretToActiveMatch(updated)
    }
    val onToggleCase: () -> Unit = {
        val s = search
        if (s != null) {
            val updated = s.withCaseSensitive(editorValue.text, !s.caseSensitive)
            search = updated
            moveCaretToActiveMatch(updated)
        }
    }
    val onSearchNext: () -> Unit = {
        val s = search
        if (s != null) {
            val updated = s.next()
            search = updated
            moveCaretToActiveMatch(updated)
        }
    }
    val onSearchPrev: () -> Unit = {
        val s = search
        if (s != null) {
            val updated = s.prev()
            search = updated
            moveCaretToActiveMatch(updated)
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = windowTitle(book.active?.path),
    ) {
        val frame = window
        GlassTheme {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        if (event.isCtrlPressed) {
                            when {
                                event.key == Key.O && event.isShiftPressed -> { openFolder(frame); true }
                                event.key == Key.O -> { openFile(frame); true }
                                event.key == Key.S -> { saveFile(frame); true }
                                event.key == Key.W -> { closeActiveTab(); true }
                                event.key == Key.F -> { openSearch(); true }
                                else -> false
                            }
                        } else if (event.key == Key.Escape && search != null) {
                            closeSearch(); true
                        } else false
                    },
                color = MaterialTheme.colorScheme.background,
            ) {
                Shell(
                    book = book,
                    activePath = book.active?.path,
                    editorValue = editorValue,
                    onEditorChange = { v ->
                        val textChanged = v.text != editorValue.text
                        editorValue = v
                        book = book.updateActive(v.text, v.selection.start)
                        if (textChanged) {
                            search = search?.retarget(v.text)
                        }
                    },
                    onActivateTab = { index -> book = book.activate(index) },
                    onCloseTab = { index -> book = book.close(index) },
                    onMoveTab = { from, to -> book = book.move(from, to) },
                    rootDir = rootDir,
                    expanded = expanded,
                    sidebarWidth = sidebarWidth,
                    onSidebarResize = { delta ->
                        sidebarWidth = (sidebarWidth + delta).coerceIn(160.dp, 600.dp)
                    },
                    onToggle = toggleExpanded,
                    onOpenFile = openInTab,
                    search = search,
                    onQueryChange = onQueryChange,
                    onToggleCase = onToggleCase,
                    onSearchNext = onSearchNext,
                    onSearchPrev = onSearchPrev,
                    onSearchClose = closeSearch,
                )
            }
        }
    }
}

private fun windowTitle(path: Path?): String {
    val name = path?.fileName?.toString() ?: "untitled"
    return "$name — ${PageIdentity.NAME}"
}

@Composable
private fun Shell(
    book: TabBook,
    activePath: Path?,
    editorValue: TextFieldValue,
    onEditorChange: (TextFieldValue) -> Unit,
    onActivateTab: (Int) -> Unit,
    onCloseTab: (Int) -> Unit,
    onMoveTab: (Int, Int) -> Unit,
    rootDir: Path?,
    expanded: Set<Path>,
    sidebarWidth: Dp,
    onSidebarResize: (Dp) -> Unit,
    onToggle: (Path) -> Unit,
    onOpenFile: (Path) -> Unit,
    search: SearchState?,
    onQueryChange: (String) -> Unit,
    onToggleCase: () -> Unit,
    onSearchNext: () -> Unit,
    onSearchPrev: () -> Unit,
    onSearchClose: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TitleBar(path = activePath)
        Row(modifier = Modifier.fillMaxSize()) {
            FileTreePanel(
                root = rootDir,
                expanded = expanded,
                selectedFile = activePath,
                onToggle = onToggle,
                onOpenFile = onOpenFile,
                modifier = Modifier.width(sidebarWidth).fillMaxHeight(),
            )
            ResizeHandle(onSidebarResize)
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                TabBar(
                    book = book,
                    onActivate = onActivateTab,
                    onClose = onCloseTab,
                    onMove = onMoveTab,
                )
                val active = book.active
                val kind = active?.let { FileKinds.classify(it.path) }
                when (kind) {
                    FileKind.IMAGE, FileKind.SVG -> PreviewPanel(
                        path = active.path,
                        kind = kind,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                    else -> EditorPanel(
                        value = editorValue,
                        onValueChange = onEditorChange,
                        search = search,
                        onQueryChange = onQueryChange,
                        onToggleCase = onToggleCase,
                        onSearchNext = onSearchNext,
                        onSearchPrev = onSearchPrev,
                        onSearchClose = onSearchClose,
                        lexer = active?.path?.let { SyntaxLexers.forPath(it) },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
            }
        }
    }
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
