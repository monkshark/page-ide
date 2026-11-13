package page.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.net.URI
import java.nio.file.Path
import page.lsp.ReferenceLocation
import page.ui.Glass
import page.ui.GlassPopup

private data class ReferenceHit(val path: Path, val ref: ReferenceLocation)

@Composable
fun ReferencesPopup(
    state: ReferencesQueryState,
    linePreviewFor: (String, Int) -> String?,
    onJump: (Path, Int, Int) -> Unit,
    onOpenInPanel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val groups = remember(state.results) { groupHits(state.results) }
    val flat = remember(groups) { groups.flatMap { it.second } }
    var selected by remember(flat) { mutableStateOf(0) }
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(flat) {
        if (flat.isNotEmpty()) focusRequester.requestFocus()
    }
    LaunchedEffect(selected) {
        if (flat.isNotEmpty()) listState.animateScrollToItem(rowIndexOf(groups, selected))
    }

    GlassPopup(
        modifier = Modifier
            .width(520.dp)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown -> {
                        if (flat.isNotEmpty()) selected = (selected + 1) % flat.size
                        true
                    }
                    Key.DirectionUp -> {
                        if (flat.isNotEmpty()) selected = (selected - 1 + flat.size) % flat.size
                        true
                    }
                    Key.Enter, Key.NumPadEnter -> {
                        val hit = flat.getOrNull(selected)
                        if (hit != null) onJump(hit.path, hit.ref.startLine, hit.ref.startCharacter)
                        true
                    }
                    Key.Escape -> {
                        onDismiss()
                        true
                    }
                    else -> false
                }
            },
    ) {
        Column {
            PopupHeader(state, flat.size, groups.size, onOpenInPanel)
            Divider()
            when {
                state.isLoading -> Message("Searching…")
                state.errorMessage != null -> Message(state.errorMessage, isError = true)
                flat.isEmpty() && state.indexing -> Message("Indexing the project — searched open files only")
                flat.isEmpty() -> Message("No usages of ${state.symbolName}")
                else -> LazyColumn(state = listState, modifier = Modifier.heightIn(max = 320.dp)) {
                    for ((path, hits) in groups) {
                        item { FileHeader(path, hits.size) }
                        items(hits.size, key = { i -> hits[i].ref.uri + ":" + hits[i].ref.startLine + ":" + hits[i].ref.startCharacter }) { i ->
                            val hit = hits[i]
                            HitRow(
                                hit = hit,
                                preview = linePreviewFor(hit.ref.uri, hit.ref.startLine),
                                selected = flat.getOrNull(selected) === hit,
                                onClick = { onJump(hit.path, hit.ref.startLine, hit.ref.startCharacter) },
                            )
                        }
                    }
                }
            }
            if (flat.isNotEmpty()) {
                Divider()
                Footer(indexing = state.indexing)
            }
        }
    }
}

@Composable
private fun PopupHeader(
    state: ReferencesQueryState,
    hits: Int,
    files: Int,
    onOpenInPanel: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Usages of",
            style = MaterialTheme.typography.labelSmall,
            color = Glass.colors.muted,
        )
        Text(
            text = state.symbolName,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = Glass.colors.primary,
        )
        Box(modifier = Modifier.weight(1f))
        if (hits > 0) {
            Text(
                text = if (files == 1) "$hits in 1 file" else "$hits in $files files",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = Glass.colors.faint,
            )
            Text(
                text = "Open in panel",
                style = MaterialTheme.typography.labelSmall,
                color = Glass.colors.primary,
                modifier = Modifier.clickable { onOpenInPanel() }.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun FileHeader(path: Path, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Glass.colors.highlightEdge)
            .padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = path.fileName?.toString() ?: path.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = Glass.colors.text,
        )
        Text(
            text = shortenParent(path),
            style = MaterialTheme.typography.labelSmall,
            color = Glass.colors.faint,
            modifier = Modifier.weight(1f, fill = false),
        )
        Box(modifier = Modifier.weight(1f))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = Glass.colors.faint,
        )
    }
}

@Composable
private fun HitRow(
    hit: ReferenceHit,
    preview: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) Glass.colors.primarySoft else Glass.colors.surface)
            .clickable { onClick() }
            .padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = (hit.ref.startLine + 1).toString(),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = Glass.colors.faint,
            modifier = Modifier.width(36.dp),
        )
        Text(
            text = previewText(preview, hit.ref),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = Glass.colors.text,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Footer(indexing: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (hint in listOf("↑↓ move", "Enter open", "Esc close")) {
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = Glass.colors.faint,
            )
        }
        if (indexing) {
            Box(modifier = Modifier.weight(1f))
            Text(
                text = "Indexing — list may grow",
                style = MaterialTheme.typography.labelSmall,
                color = Glass.colors.warn,
            )
        }
    }
}

@Composable
fun CaretBusyPopup(label: String) {
    GlassPopup {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Glass.colors.muted,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Glass.colors.separator),
    )
}

@Composable
private fun Message(text: String, isError: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) Glass.colors.error else Glass.colors.muted,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

private fun previewText(preview: String?, ref: ReferenceLocation): androidx.compose.ui.text.AnnotatedString {
    val raw = preview?.trimEnd()?.take(200)
    if (raw == null) return buildAnnotatedString { append("(no preview)") }
    val leading = raw.length - raw.trimStart().length
    val body = raw.trimStart()
    val start = (ref.startCharacter - leading).coerceIn(0, body.length)
    val end = (start + (ref.endCharacter - ref.startCharacter).coerceAtLeast(0)).coerceIn(start, body.length)
    return buildAnnotatedString {
        append(body.substring(0, start))
        if (end > start) {
            pushStyle(SpanStyle(fontWeight = FontWeight.Medium))
            append(body.substring(start, end))
            pop()
        }
        append(body.substring(end))
    }
}

private fun groupHits(results: List<ReferenceLocation>): List<Pair<Path, List<ReferenceHit>>> {
    val byPath = LinkedHashMap<Path, MutableList<ReferenceHit>>()
    for (r in results) {
        val p = runCatching { Path.of(URI(r.uri)) }.getOrNull() ?: continue
        byPath.getOrPut(p) { mutableListOf() }.add(ReferenceHit(p, r))
    }
    return byPath
        .map { (path, hits) -> path to hits.sortedWith(compareBy({ it.ref.startLine }, { it.ref.startCharacter })) }
        .sortedBy { it.first.fileName?.toString().orEmpty() }
}

private fun rowIndexOf(groups: List<Pair<Path, List<ReferenceHit>>>, flatIndex: Int): Int {
    var row = 0
    var seen = 0
    for ((_, hits) in groups) {
        row++
        if (flatIndex < seen + hits.size) return row + (flatIndex - seen)
        seen += hits.size
        row += hits.size
    }
    return row
}

private fun shortenParent(path: Path): String {
    val parent = path.parent ?: return ""
    val parts = parent.map { it.toString() }
    return if (parts.size <= 3) parts.joinToString("/") else "…/" + parts.takeLast(3).joinToString("/")
}
