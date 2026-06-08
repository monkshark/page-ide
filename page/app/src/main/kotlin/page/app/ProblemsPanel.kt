package page.app

import page.runtime.*
import page.workspace.*

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.awt.Cursor
import page.lsp.Diagnostic
import page.lsp.DiagnosticSeverity
import page.ui.CompactContextMenuRepresentation
import page.ui.Glass
import java.net.URI
import java.nio.file.Path

data class ProblemEntry(
    val path: Path,
    val diagnostic: Diagnostic,
)

@Composable
fun ProblemsPanel(
    diagnostics: Map<String, List<Diagnostic>>,
    onJump: (Path, Int, Int) -> Unit,
    onClose: () -> Unit,
    height: Dp,
    onResizeDelta: (Dp) -> Unit,
    collapsedKeys: Set<String>,
    onCollapsedKeysChange: (Set<String>) -> Unit,
    fileOrder: List<String>,
    onFileOrderChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = flattenAndSort(diagnostics)
    val errorCount = entries.count { it.diagnostic.severity == DiagnosticSeverity.ERROR }
    val warningCount = entries.count { it.diagnostic.severity == DiagnosticSeverity.WARNING }
    val infoCount = entries.count { it.diagnostic.severity == DiagnosticSeverity.INFO }
    val hintCount = entries.count { it.diagnostic.severity == DiagnosticSeverity.HINT }
    val clipboard = LocalClipboardManager.current

    val groups: List<Pair<Path, List<ProblemEntry>>> = run {
        val byPath = LinkedHashMap<Path, MutableList<ProblemEntry>>()
        for (entry in entries) {
            byPath.getOrPut(entry.path) { mutableListOf() }.add(entry)
        }
        val raw = byPath.map { (k, v) -> k to v.toList() }
        applyFileOrder(raw, fileOrder)
    }
    val displayList: List<ProblemEntry> = groups.flatMap { it.second }

    var selected by remember(diagnostics) { mutableStateOf<Set<Int>>(emptySet()) }
    var anchor by remember(diagnostics) { mutableStateOf<Int?>(null) }
    val focusRequester = remember { FocusRequester() }

    val selectionSnapshot: () -> List<ProblemEntry> = {
        selected.sorted().mapNotNull { displayList.getOrNull(it) }
    }
    val selectOne: (Int) -> Unit = { idx ->
        selected = setOf(idx)
        anchor = idx
        runCatching { focusRequester.requestFocus() }
    }
    val selectRange: (Int) -> Unit = { idx ->
        val a = anchor ?: idx
        val lo = minOf(a, idx); val hi = maxOf(a, idx)
        selected = (lo..hi).toSet()
        runCatching { focusRequester.requestFocus() }
    }
    val toggleOne: (Int) -> Unit = { idx ->
        selected = if (idx in selected) selected - idx else selected + idx
        anchor = idx
        runCatching { focusRequester.requestFocus() }
    }
    val ensureSelected: (Int) -> Unit = { idx ->
        if (idx !in selected) {
            selected = setOf(idx)
            anchor = idx
            runCatching { focusRequester.requestFocus() }
        }
    }
    val copySelected: () -> Unit = {
        val snap = selectionSnapshot()
        if (snap.isNotEmpty()) {
            clipboard.setText(AnnotatedString(snap.joinToString("\n") { formatEntry(it) }))
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth().height(height),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            VerticalResizeHandle(onResizeDelta = onResizeDelta)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Problems",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (errorCount > 0) ProblemBadge(errorCount, "errors", Glass.colors.error)
                if (warningCount > 0) ProblemBadge(warningCount, "warnings", Glass.colors.warn)
                if (infoCount > 0) ProblemBadge(infoCount, "info", MaterialTheme.colorScheme.primary)
                if (hintCount > 0) ProblemBadge(hintCount, "hints", MaterialTheme.colorScheme.tertiary)
                Box(modifier = Modifier.weight(1f))
                if (entries.isNotEmpty()) {
                    Text(
                        text = "Copy all",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clickable {
                                clipboard.setText(AnnotatedString(formatAllEntries(entries)))
                            }
                            .padding(4.dp),
                    )
                }
                Text(
                    text = "Close",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { onClose() }.padding(4.dp),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            if (entries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No problems reported.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val orderKeys = groups.map { it.first.toString() }
                val rowStartByGroup = run {
                    val acc = IntArray(groups.size)
                    var cur = 0
                    for (i in groups.indices) {
                        acc[i] = cur
                        cur += groups[i].second.size
                    }
                    acc
                }
                ReorderableFileColumn(
                    keys = orderKeys,
                    onMove = { newKeys -> onFileOrderChange(newKeys) },
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(focusRequester)
                        .focusable()
                        .onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when {
                                e.isCtrlPressed && e.key == Key.C -> {
                                    copySelected()
                                    true
                                }
                                e.isCtrlPressed && e.key == Key.A -> {
                                    selected = displayList.indices.toSet()
                                    anchor = displayList.lastIndex.takeIf { it >= 0 }
                                    true
                                }
                                else -> false
                            }
                        },
                    onGroupClick = { idx ->
                        val pathKey = groups[idx].first.toString()
                        val collapsed = pathKey in collapsedKeys
                        val next = if (collapsed) collapsedKeys - pathKey else collapsedKeys + pathKey
                        onCollapsedKeysChange(next)
                    },
                    headerContent = { idx ->
                        val (path, list) = groups[idx]
                        val collapsed = path.toString() in collapsedKeys
                        ProblemFileHeader(
                            path = path,
                            list = list,
                            collapsed = collapsed,
                        )
                    },
                    bodyContent = { idx ->
                        val (path, list) = groups[idx]
                        val collapsed = path.toString() in collapsedKeys
                        if (!collapsed) {
                            val baseIdx = rowStartByGroup[idx]
                            Column {
                                list.forEachIndexed { i, entry ->
                                    val rowIdx = baseIdx + i
                                    ProblemRow(
                                        entry = entry,
                                        index = rowIdx,
                                        isSelected = rowIdx in selected,
                                        onJump = onJump,
                                        onSelectOne = selectOne,
                                        onSelectRange = selectRange,
                                        onToggle = toggleOne,
                                        onSecondaryPress = ensureSelected,
                                        selectionSnapshot = selectionSnapshot,
                                    )
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ProblemFileHeader(
    path: Path,
    list: List<ProblemEntry>,
    collapsed: Boolean,
) {
    val errorCount = list.count { it.diagnostic.severity == DiagnosticSeverity.ERROR }
    val warningCount = list.count { it.diagnostic.severity == DiagnosticSeverity.WARNING }
    val infoCount = list.count { it.diagnostic.severity == DiagnosticSeverity.INFO }
    val hintCount = list.count { it.diagnostic.severity == DiagnosticSeverity.HINT }
    val clipboard = LocalClipboardManager.current
    CompositionLocalProvider(LocalContextMenuRepresentation provides CompactContextMenuRepresentation) {
    ContextMenuArea(
        items = {
            listOf(
                ContextMenuItem("Copy diagnostics for this file") {
                    clipboard.setText(AnnotatedString(formatFileEntries(path, list)))
                },
                ContextMenuItem("Copy file path") {
                    clipboard.setText(AnnotatedString(path.toString()))
                },
            )
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (collapsed) "▸" else "▾",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = path.fileName?.toString() ?: path.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val parent = path.parent?.toString().orEmpty()
            if (parent.isNotEmpty()) {
                Text(
                    text = parent,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(modifier = Modifier.weight(1f))
            if (errorCount > 0) ProblemBadge(errorCount, "errors", Glass.colors.error)
            if (warningCount > 0) ProblemBadge(warningCount, "warnings", Glass.colors.warn)
            if (infoCount > 0) ProblemBadge(infoCount, "info", MaterialTheme.colorScheme.primary)
            if (hintCount > 0) ProblemBadge(hintCount, "hints", MaterialTheme.colorScheme.tertiary)
        }
    }
    }
}

@Composable
private fun ProblemRow(
    entry: ProblemEntry,
    index: Int,
    isSelected: Boolean,
    onJump: (Path, Int, Int) -> Unit,
    onSelectOne: (Int) -> Unit,
    onSelectRange: (Int) -> Unit,
    onToggle: (Int) -> Unit,
    onSecondaryPress: (Int) -> Unit,
    selectionSnapshot: () -> List<ProblemEntry>,
) {
    val color = when (entry.diagnostic.severity) {
        DiagnosticSeverity.ERROR -> Glass.colors.error
        DiagnosticSeverity.WARNING -> Glass.colors.warn
        DiagnosticSeverity.INFO -> MaterialTheme.colorScheme.primary
        DiagnosticSeverity.HINT -> MaterialTheme.colorScheme.tertiary
    }
    val clipboard = LocalClipboardManager.current
    val rowBg = if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    } else {
        Color.Transparent
    }
    CompositionLocalProvider(LocalContextMenuRepresentation provides CompactContextMenuRepresentation) {
    ContextMenuArea(
        items = {
            val snap = selectionSnapshot().ifEmpty { listOf(entry) }
            val n = snap.size
            val msgLabel = if (n <= 1) "Copy message" else "Copy $n messages"
            val locLabel = if (n <= 1) "Copy location + message" else "Copy $n locations + messages"
            listOf(
                ContextMenuItem(msgLabel) {
                    val text = snap.joinToString("\n") { it.diagnostic.message }
                    if (text.isNotEmpty()) clipboard.setText(AnnotatedString(text))
                },
                ContextMenuItem(locLabel) {
                    val text = snap.joinToString("\n") { formatEntry(it) }
                    if (text.isNotEmpty()) clipboard.setText(AnnotatedString(text))
                },
            )
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(rowBg)
                .pointerInput(index) {
                    awaitPointerEventScope {
                        var lastTime = 0L
                        while (true) {
                            val e = awaitPointerEvent(PointerEventPass.Main)
                            if (e.type != PointerEventType.Press) continue
                            val change = e.changes.firstOrNull() ?: continue
                            if (e.buttons.isSecondaryPressed) {
                                onSecondaryPress(index)
                                continue
                            }
                            val now = System.currentTimeMillis()
                            val isDouble = now - lastTime < 400
                            lastTime = now
                            if (isDouble) {
                                onJump(
                                    entry.path,
                                    entry.diagnostic.start.line,
                                    entry.diagnostic.start.character,
                                )
                                change.consume()
                                continue
                            }
                            val shift = e.keyboardModifiers.isShiftPressed
                            val ctrl = e.keyboardModifiers.isCtrlPressed
                            when {
                                shift -> onSelectRange(index)
                                ctrl -> onToggle(index)
                                else -> onSelectOne(index)
                            }
                            change.consume()
                        }
                    }
                }
                .padding(start = 28.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
            Text(
                text = "${entry.diagnostic.start.line + 1}:${entry.diagnostic.start.character + 1}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = entry.diagnostic.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
    }
    }
}

@Composable
private fun ProblemBadge(count: Int, label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(7.dp).background(color, CircleShape))
        Text(
            text = "$count $label",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun <T> applyFileOrder(
    raw: List<Pair<Path, T>>,
    order: List<String>,
): List<Pair<Path, T>> {
    if (order.isEmpty()) return raw
    val byKey = raw.associateBy { it.first.toString() }
    val ordered = LinkedHashMap<String, Pair<Path, T>>()
    for (key in order) {
        val hit = byKey[key] ?: continue
        ordered[key] = hit
    }
    for ((key, value) in byKey) {
        ordered.putIfAbsent(key, value)
    }
    return ordered.values.toList()
}

internal fun diagnosticsInScope(
    all: Map<String, List<Diagnostic>>,
    scope: DiagnosticsScope,
    focusedPath: Path?,
    openPaths: Set<Path>,
): Map<String, List<Diagnostic>> {
    fun uriToPath(uri: String): Path? =
        runCatching { Path.of(URI(uri)).toAbsolutePath().normalize() }.getOrNull()
    return when (scope) {
        DiagnosticsScope.CURRENT_FILE -> {
            val target = focusedPath?.toAbsolutePath()?.normalize() ?: return emptyMap()
            all.filterKeys { uriToPath(it) == target }
        }
        DiagnosticsScope.OPEN_TABS -> {
            if (openPaths.isEmpty()) return emptyMap()
            val normalized = openPaths.mapNotNull {
                runCatching { it.toAbsolutePath().normalize() }.getOrNull()
            }.toSet()
            all.filterKeys { val p = uriToPath(it); p != null && p in normalized }
        }
    }
}

private fun flattenAndSort(diagnostics: Map<String, List<Diagnostic>>): List<ProblemEntry> {
    val acc = mutableListOf<ProblemEntry>()
    for ((uri, list) in diagnostics) {
        val path = runCatching { Path.of(URI(uri)) }.getOrNull() ?: continue
        for (d in list) acc.add(ProblemEntry(path, d))
    }
    acc.sortWith(
        compareBy(
            { severityOrder(it.diagnostic.severity) },
            { it.path.fileName?.toString().orEmpty() },
            { it.diagnostic.start.line },
            { it.diagnostic.start.character },
        ),
    )
    return acc
}

private fun severityOrder(s: DiagnosticSeverity): Int = when (s) {
    DiagnosticSeverity.ERROR -> 0
    DiagnosticSeverity.WARNING -> 1
    DiagnosticSeverity.INFO -> 2
    DiagnosticSeverity.HINT -> 3
}

private fun formatEntry(entry: ProblemEntry): String {
    val d = entry.diagnostic
    val sev = d.severity.name
    val codeTag = d.code?.takeIf { it.isNotEmpty() }?.let { " [$it]" }.orEmpty()
    val file = entry.path.fileName?.toString() ?: entry.path.toString()
    return "$file:${d.start.line + 1}:${d.start.character + 1} $sev$codeTag ${d.message}"
}

private fun formatFileEntries(path: Path, list: List<ProblemEntry>): String =
    buildString {
        appendLine(path.toString())
        for (entry in list) {
            val d = entry.diagnostic
            val sev = d.severity.name
            val codeTag = d.code?.takeIf { it.isNotEmpty() }?.let { " [$it]" }.orEmpty()
            appendLine("  ${d.start.line + 1}:${d.start.character + 1} $sev$codeTag ${d.message}")
        }
    }.trimEnd()

private fun formatAllEntries(entries: List<ProblemEntry>): String {
    val byPath = LinkedHashMap<Path, MutableList<ProblemEntry>>()
    for (entry in entries) byPath.getOrPut(entry.path) { mutableListOf() }.add(entry)
    return buildString {
        val iter = byPath.entries.iterator()
        while (iter.hasNext()) {
            val (path, list) = iter.next()
            appendLine(formatFileEntries(path, list))
            if (iter.hasNext()) appendLine()
        }
    }.trimEnd()
}

@Composable
private fun VerticalResizeHandle(onResizeDelta: (Dp) -> Unit) {
    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR)))
            .hoverable(interactionSource)
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dy ->
                    onResizeDelta(with(density) { (-dy).toDp() })
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
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline),
        )
    }
}
