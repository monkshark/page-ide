package page.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import kotlinx.coroutines.delay
import org.eclipse.lsp4j.SymbolKind
import page.editor.FuzzyMatcher
import page.lsp.DocumentSymbolEntry
import page.lsp.WorkspaceSymbolLocated
import page.ui.GlassTheme

data class SymbolPick(
    val uri: String,
    val startLine: Int,
    val startCharacter: Int,
)

@Composable
internal fun DocumentSymbolDialog(
    uri: String,
    symbols: List<DocumentSymbolEntry>,
    onPick: (SymbolPick) -> Unit,
    onDismiss: () -> Unit,
) {
    val flattened = remember(symbols) {
        symbols.flatMap { it.flattenOutline(depth = 0) }
    }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(0) }
    val results = remember(query, flattened) { rankDocumentSymbols(query, flattened) }

    LaunchedEffect(results) {
        if (selected >= results.size) selected = if (results.isEmpty()) 0 else results.size - 1
    }

    val showTree = query.isBlank()

    SymbolDialogShell(
        title = "Symbols in file",
        placeholder = "Symbol name…",
        query = query,
        onQueryChange = { query = it; selected = 0 },
        selected = selected,
        onSelectedChange = { selected = it },
        resultsCount = results.size,
        onEnter = {
            results.getOrNull(selected)?.let { r ->
                onPick(SymbolPick(uri, r.entry.selectionRange.startLine, r.entry.selectionRange.startCharacter))
            }
        },
        onDismiss = onDismiss,
    ) { listState, onHover ->
        if (results.isEmpty()) {
            EmptyState(if (flattened.isEmpty()) "No symbols" else "No results")
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(results, key = { idx, _ -> idx }) { idx, r ->
                    DocumentSymbolRow(
                        result = r,
                        depth = if (showTree) r.depth else 0,
                        showContainer = !showTree,
                        isSelected = idx == selected,
                        onHover = { onHover(idx) },
                        onClick = {
                            onPick(SymbolPick(uri, r.entry.selectionRange.startLine, r.entry.selectionRange.startCharacter))
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun WorkspaceSymbolDialog(
    queryFor: suspend (String) -> List<WorkspaceSymbolLocated>,
    onPick: (SymbolPick) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(0) }
    var symbols by remember { mutableStateOf<List<WorkspaceSymbolLocated>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            symbols = emptyList()
            isLoading = false
            return@LaunchedEffect
        }
        delay(180)
        isLoading = true
        val fetched = runCatching { queryFor(query) }.getOrDefault(emptyList())
        symbols = fetched.filter { it.location != null }
        isLoading = false
        selected = 0
    }

    SymbolDialogShell(
        title = "Workspace symbols",
        placeholder = "Symbol name… (LSP search)",
        query = query,
        onQueryChange = { query = it; selected = 0 },
        selected = selected,
        onSelectedChange = { selected = it },
        resultsCount = symbols.size,
        onEnter = {
            symbols.getOrNull(selected)?.let { sym ->
                val loc = sym.location ?: return@let
                onPick(SymbolPick(loc.uri, loc.range.startLine, loc.range.startCharacter))
            }
        },
        onDismiss = onDismiss,
    ) { listState, onHover ->
        when {
            query.isBlank() -> EmptyState("Type to search across the workspace")
            isLoading && symbols.isEmpty() -> EmptyState("Searching…")
            symbols.isEmpty() -> EmptyState("No results")
            else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(symbols, key = { idx, _ -> idx }) { idx, sym ->
                    WorkspaceSymbolRow(
                        sym = sym,
                        isSelected = idx == selected,
                        onHover = { onHover(idx) },
                        onClick = {
                            val loc = sym.location ?: return@WorkspaceSymbolRow
                            onPick(SymbolPick(loc.uri, loc.range.startLine, loc.range.startCharacter))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SymbolDialogShell(
    title: String,
    placeholder: String,
    query: String,
    onQueryChange: (String) -> Unit,
    selected: Int,
    onSelectedChange: (Int) -> Unit,
    resultsCount: Int,
    onEnter: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable (
        listState: androidx.compose.foundation.lazy.LazyListState,
        onHover: (Int) -> Unit,
    ) -> Unit,
) {
    val state = rememberDialogState(width = 720.dp, height = 460.dp)
    val queryFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        queryFocus.requestFocus()
    }
    LaunchedEffect(selected) {
        if (selected in 0 until resultsCount) listState.animateScrollToItem(selected)
    }

    DialogWindow(
        onCloseRequest = onDismiss,
        state = state,
        title = title,
        resizable = false,
        undecorated = true,
        onPreviewKeyEvent = { event ->
            if (event.type != KeyEventType.KeyDown) false
            else when (event.key) {
                Key.Escape -> { onDismiss(); true }
                Key.DirectionDown -> {
                    if (resultsCount > 0) onSelectedChange((selected + 1).coerceAtMost(resultsCount - 1))
                    true
                }
                Key.DirectionUp -> {
                    if (resultsCount > 0) onSelectedChange((selected - 1).coerceAtLeast(0))
                    true
                }
                Key.Enter -> { onEnter(); true }
                else -> false
            }
        },
    ) {
        GlassTheme {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                color = MaterialTheme.colorScheme.background,
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    SymbolQueryInput(
                        value = query,
                        onChange = onQueryChange,
                        focus = queryFocus,
                        placeholder = placeholder,
                    )
                    Spacer(Modifier.height(8.dp))
                    content(listState, onSelectedChange)
                }
            }
        }
    }
}

@Composable
private fun SymbolQueryInput(
    value: String,
    onChange: (String) -> Unit,
    focus: FocusRequester,
    placeholder: String,
) {
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = fieldValue,
            onValueChange = { tfv ->
                fieldValue = tfv
                if (tfv.text != value) onChange(tfv.text)
            },
            singleLine = true,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onBackground,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
            ),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = LocalTextStyle.current.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                ),
            )
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
    }
}

private fun DocumentSymbolEntry.flattenOutline(
    depth: Int,
    parent: String? = null,
): List<DocumentSymbolDepthEntry> {
    val acc = mutableListOf<DocumentSymbolDepthEntry>()
    val display = if (parent.isNullOrBlank()) this else copy(containerName = parent)
    acc.add(DocumentSymbolDepthEntry(display, depth))
    val isFunctionLike = kind == SymbolKind.Function
        || kind == SymbolKind.Method
        || kind == SymbolKind.Constructor
    if (isFunctionLike) return acc
    val nextParent = if (parent.isNullOrBlank()) name else "$parent.$name"
    for (child in children) acc += child.flattenOutline(depth + 1, nextParent)
    return acc
}

internal data class DocumentSymbolDepthEntry(
    val entry: DocumentSymbolEntry,
    val depth: Int,
)

internal data class DocumentSymbolResult(
    val entry: DocumentSymbolEntry,
    val depth: Int,
    val score: Int,
)

internal fun rankDocumentSymbols(
    query: String,
    symbols: List<DocumentSymbolDepthEntry>,
): List<DocumentSymbolResult> {
    if (query.isBlank()) return symbols.map { DocumentSymbolResult(it.entry, it.depth, 0) }
    val q = query.trim()
    val results = ArrayList<DocumentSymbolResult>(symbols.size)
    for (s in symbols) {
        val entry = s.entry
        val nameMatch = FuzzyMatcher.match(q, entry.name)
        val containerMatch = entry.containerName?.let { FuzzyMatcher.match(q, it) }
        if (nameMatch == null && containerMatch == null) continue
        val score = (nameMatch?.score ?: 0) * 4 + (containerMatch?.score ?: 0)
        results += DocumentSymbolResult(entry, s.depth, score)
    }
    results.sortByDescending { it.score }
    return results
}

@Composable
private fun DocumentSymbolRow(
    result: DocumentSymbolResult,
    depth: Int,
    showContainer: Boolean,
    isSelected: Boolean,
    onHover: () -> Unit,
    onClick: () -> Unit,
) {
    val rowBg = if (isSelected)
        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(rowBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (depth > 0) Spacer(Modifier.width((depth * 16).dp))
        SymbolKindBadge(result.entry.kind)
        Spacer(Modifier.width(8.dp))
        Text(
            text = result.entry.name,
            color = MaterialTheme.colorScheme.onSurface,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val container = result.entry.containerName
        if (showContainer && !container.isNullOrBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = container,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "L${result.entry.selectionRange.startLine + 1}",
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
        )
    }
    LaunchedEffect(isSelected) { if (isSelected) onHover() }
}

@Composable
private fun WorkspaceSymbolRow(
    sym: WorkspaceSymbolLocated,
    isSelected: Boolean,
    onHover: () -> Unit,
    onClick: () -> Unit,
) {
    val rowBg = if (isSelected)
        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    else Color.Transparent
    val fileLabel = sym.location?.let { loc ->
        val tail = loc.uri.substringAfterLast('/')
        val line = loc.range.startLine + 1
        "$tail:L$line"
    } ?: ""
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(rowBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SymbolKindBadge(sym.kind)
        Spacer(Modifier.width(8.dp))
        Text(
            text = sym.name,
            color = MaterialTheme.colorScheme.onSurface,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val container = sym.containerName
        if (!container.isNullOrBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = container,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = fileLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    LaunchedEffect(isSelected) { if (isSelected) onHover() }
}

@Composable
private fun SymbolKindBadge(kind: SymbolKind?) {
    val (letter, tint) = symbolKindBadge(kind)
    Box(
        modifier = Modifier
            .size(width = 18.dp, height = 16.dp)
            .background(tint.copy(alpha = 0.18f))
            .border(1.dp, tint.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            color = tint,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

@Composable
private fun symbolKindBadge(kind: SymbolKind?): Pair<String, Color> {
    val cs = MaterialTheme.colorScheme
    return when (kind) {
        SymbolKind.Class -> "C" to cs.primary
        SymbolKind.Interface -> "I" to cs.tertiary
        SymbolKind.Object -> "O" to cs.secondary
        SymbolKind.Enum, SymbolKind.EnumMember -> "E" to cs.tertiary
        SymbolKind.Struct -> "S" to cs.primary
        SymbolKind.Method, SymbolKind.Function, SymbolKind.Constructor -> "f" to cs.secondary
        SymbolKind.Property, SymbolKind.Field -> "p" to cs.primary
        SymbolKind.Variable, SymbolKind.Constant -> "v" to cs.onSurfaceVariant
        SymbolKind.Package, SymbolKind.Namespace, SymbolKind.Module -> "P" to cs.onSurfaceVariant
        SymbolKind.TypeParameter -> "T" to cs.tertiary
        else -> "·" to cs.onSurfaceVariant
    }
}
