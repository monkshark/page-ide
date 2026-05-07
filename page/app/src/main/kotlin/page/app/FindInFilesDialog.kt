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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import page.editor.FileDocument
import page.editor.GrepFileResult
import page.editor.GrepHit
import page.editor.GrepReport
import page.editor.GrepStats
import page.editor.IndexedFile
import page.editor.ProjectGrep
import page.ui.GlassTheme
import java.nio.file.Path

private sealed interface ResultRow {
    data class Header(val file: GrepFileResult) : ResultRow
    data class Hit(val file: GrepFileResult, val hit: GrepHit, val indexInFile: Int) : ResultRow
}

@Composable
internal fun FindInFilesDialog(
    files: List<IndexedFile>,
    onPickAt: (Path, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var caseSensitive by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<GrepReport?>(null) }
    var busy by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(0) }

    LaunchedEffect(query, caseSensitive, files) {
        if (query.isEmpty()) {
            report = null
            busy = false
            return@LaunchedEffect
        }
        busy = true
        delay(150)
        val r = withContext(Dispatchers.IO) {
            val job = coroutineContext.job
            ProjectGrep.search(
                files = files,
                query = query,
                caseSensitive = caseSensitive,
                loader = { p -> FileDocument.loadOrNull(p) },
                cancelled = { !job.isActive },
            )
        }
        report = r
        busy = false
        selected = 0
    }

    val rows = remember(report) { flatten(report?.results.orEmpty()) }
    val hitRowIndices = remember(rows) { rows.withIndex().filter { it.value is ResultRow.Hit }.map { it.index } }

    val state = rememberDialogState(width = 880.dp, height = 560.dp)
    val queryFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { queryFocus.requestFocus() }
    LaunchedEffect(selected, hitRowIndices) {
        val rowIdx = hitRowIndices.getOrNull(selected)
        if (rowIdx != null) listState.animateScrollToItem(rowIdx)
    }

    DialogWindow(
        onCloseRequest = onDismiss,
        state = state,
        title = "파일에서 찾기",
        resizable = true,
        undecorated = true,
        onPreviewKeyEvent = handler@{ event ->
            if (event.type != KeyEventType.KeyDown) return@handler false
            when (event.key) {
                Key.Escape -> { onDismiss(); true }
                Key.DirectionDown -> {
                    if (hitRowIndices.isNotEmpty()) {
                        selected = (selected + 1).coerceAtMost(hitRowIndices.lastIndex)
                    }
                    true
                }
                Key.DirectionUp -> {
                    if (hitRowIndices.isNotEmpty()) {
                        selected = (selected - 1).coerceAtLeast(0)
                    }
                    true
                }
                Key.Enter -> {
                    val rowIdx = hitRowIndices.getOrNull(selected)
                    val row = rowIdx?.let { rows.getOrNull(it) } as? ResultRow.Hit
                    if (row != null) onPickAt(row.file.file.path, row.hit.offset)
                    true
                }
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
                    Header(
                        query = query,
                        onQueryChange = { query = it; selected = 0 },
                        caseSensitive = caseSensitive,
                        onToggleCase = { caseSensitive = !caseSensitive; selected = 0 },
                        focus = queryFocus,
                        busy = busy,
                        stats = report?.stats,
                    )
                    Spacer(Modifier.height(8.dp))
                    Body(
                        rows = rows,
                        hitRowIndices = hitRowIndices,
                        selected = selected,
                        listState = listState,
                        onSelect = { selected = it },
                        onPick = { row ->
                            onPickAt(row.file.file.path, row.hit.offset)
                        },
                        empty = report != null && rows.isEmpty(),
                        idle = report == null,
                    )
                }
            }
        }
    }
}

private fun flatten(results: List<GrepFileResult>): List<ResultRow> {
    val out = ArrayList<ResultRow>()
    for (f in results) {
        out += ResultRow.Header(f)
        f.hits.forEachIndexed { i, h -> out += ResultRow.Hit(f, h, i) }
    }
    return out
}

@Composable
private fun Header(
    query: String,
    onQueryChange: (String) -> Unit,
    caseSensitive: Boolean,
    onToggleCase: () -> Unit,
    focus: FocusRequester,
    busy: Boolean,
    stats: GrepStats?,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(32.dp)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onBackground,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            if (query.isEmpty()) {
                Text(
                    text = "프로젝트에서 찾을 텍스트…",
                    style = LocalTextStyle.current.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                    ),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        CaseChip(active = caseSensitive, onClick = onToggleCase)
        Spacer(Modifier.width(10.dp))
        StatusLabel(busy = busy, stats = stats, hasQuery = query.isNotEmpty())
    }
}

@Composable
private fun CaseChip(active: Boolean, onClick: () -> Unit) {
    val bg = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f) else Color.Transparent
    val fg = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .height(28.dp)
            .width(36.dp)
            .background(bg)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Aa",
            style = LocalTextStyle.current.copy(
                color = fg,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Composable
private fun StatusLabel(busy: Boolean, stats: GrepStats?, hasQuery: Boolean) {
    val text = when {
        busy -> "검색 중…"
        !hasQuery -> ""
        stats == null -> ""
        stats.hits == 0 -> "결과 없음"
        else -> {
            val base = "${stats.hits}건 / 파일 ${stats.files}개"
            if (stats.truncated) "$base (잘림)" else base
        }
    }
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = LocalTextStyle.current.copy(
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        ),
        modifier = Modifier.width(180.dp),
    )
}

@Composable
private fun Body(
    rows: List<ResultRow>,
    hitRowIndices: List<Int>,
    selected: Int,
    listState: LazyListState,
    onSelect: (Int) -> Unit,
    onPick: (ResultRow.Hit) -> Unit,
    empty: Boolean,
    idle: Boolean,
) {
    if (idle) {
        EmptyHint("검색어를 입력하세요")
        return
    }
    if (empty) {
        EmptyHint("결과 없음")
        return
    }
    val selectedRowIdx = hitRowIndices.getOrNull(selected)
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(rows.size) { idx ->
            when (val row = rows[idx]) {
                is ResultRow.Header -> FileHeaderRow(row.file)
                is ResultRow.Hit -> HitRow(
                    row = row,
                    isSelected = idx == selectedRowIdx,
                    onHover = {
                        val pos = hitRowIndices.indexOf(idx)
                        if (pos >= 0) onSelect(pos)
                    },
                    onClick = { onPick(row) },
                )
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun FileHeaderRow(file: GrepFileResult) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = file.file.relative,
            color = MaterialTheme.colorScheme.onSurface,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${file.hits.size}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            ),
        )
    }
}

@Composable
private fun HitRow(
    row: ResultRow.Hit,
    isSelected: Boolean,
    onHover: () -> Unit,
    onClick: () -> Unit,
) {
    val bg = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent
    val baseColor = MaterialTheme.colorScheme.onSurface
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val highlightColor = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(start = 24.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${row.hit.line + 1}:${row.hit.col + 1}",
            color = mutedColor,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            ),
            modifier = Modifier.width(80.dp),
        )
        Text(
            text = highlightedSnippet(
                line = row.hit.lineText,
                start = row.hit.matchStart,
                end = row.hit.matchEnd,
                base = baseColor,
                highlight = highlightColor,
            ),
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
    LaunchedEffect(isSelected) { if (isSelected) onHover() }
}

private fun highlightedSnippet(
    line: String,
    start: Int,
    end: Int,
    base: Color,
    highlight: Color,
): AnnotatedString {
    val s = start.coerceIn(0, line.length)
    val e = end.coerceIn(s, line.length)
    return buildAnnotatedString {
        if (s > 0) {
            withStyle(SpanStyle(color = base)) { append(line.substring(0, s)) }
        }
        if (e > s) {
            withStyle(SpanStyle(color = highlight, fontWeight = FontWeight.Bold)) {
                append(line.substring(s, e))
            }
        }
        if (e < line.length) {
            withStyle(SpanStyle(color = base)) { append(line.substring(e)) }
        }
    }
}
