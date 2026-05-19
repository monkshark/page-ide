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
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import page.editor.IndexedFile
import page.editor.QuickOpen
import page.editor.QuickOpenResult
import page.ui.GlassTheme

@Composable
internal fun QuickOpenDialog(
    files: List<IndexedFile>,
    onPick: (IndexedFile) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(0) }
    val results = remember(query, files) { QuickOpen.rank(query, files) }

    LaunchedEffect(results) {
        if (selected >= results.size) selected = if (results.isEmpty()) 0 else results.size - 1
    }

    val state = rememberDialogState(
        position = WindowPosition.Aligned(Alignment.Center),
        width = 640.dp,
        height = 420.dp,
    )
    val queryFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { queryFocus.requestFocus() }
    LaunchedEffect(selected) {
        if (selected in results.indices) listState.animateScrollToItem(selected)
    }

    DialogWindow(
        onCloseRequest = onDismiss,
        state = state,
        title = "Quick open",
        resizable = false,
        undecorated = true,
        onPreviewKeyEvent = { event ->
            if (event.type != KeyEventType.KeyDown) false
            else when (event.key) {
                Key.Escape -> { onDismiss(); true }
                Key.DirectionDown -> {
                    if (results.isNotEmpty()) selected = (selected + 1).coerceAtMost(results.size - 1)
                    true
                }
                Key.DirectionUp -> {
                    if (results.isNotEmpty()) selected = (selected - 1).coerceAtLeast(0)
                    true
                }
                Key.Enter -> {
                    results.getOrNull(selected)?.let { onPick(it.file) }
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
                    QueryInput(
                        value = query,
                        onChange = { q ->
                            query = q
                            selected = 0
                        },
                        focus = queryFocus,
                    )
                    Spacer(Modifier.height(8.dp))
                    ResultList(
                        results = results,
                        selected = selected,
                        listState = listState,
                        onHover = { selected = it },
                        onPick = { idx ->
                            results.getOrNull(idx)?.let { onPick(it.file) }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun QueryInput(value: String, onChange: (String) -> Unit, focus: FocusRequester) {
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
            value = value,
            onValueChange = onChange,
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
                text = "File name…",
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
private fun ResultList(
    results: List<QuickOpenResult>,
    selected: Int,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onHover: (Int) -> Unit,
    onPick: (Int) -> Unit,
) {
    if (results.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No results",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
        return
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(results, key = { _, r -> r.file.relative }) { idx, r ->
            ResultRow(
                result = r,
                isSelected = idx == selected,
                onHover = { onHover(idx) },
                onClick = { onPick(idx) },
            )
        }
    }
}

@Composable
private fun ResultRow(
    result: QuickOpenResult,
    isSelected: Boolean,
    onHover: () -> Unit,
    onClick: () -> Unit,
) {
    val rowBg =
        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        else Color.Transparent
    val name = QuickOpen.nameOf(result.file.relative)
    val parent = result.file.relative.dropLast(name.length).trimEnd('/')
    val nameStyle = MaterialTheme.colorScheme.onSurface
    val pathStyle = MaterialTheme.colorScheme.onSurfaceVariant
    val highlight = MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(rowBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = highlightedName(name, result.nameIndices, nameStyle, highlight),
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (parent.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = parent,
                color = pathStyle,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    LaunchedEffect(isSelected) { if (isSelected) onHover() }
}

private fun highlightedName(
    text: String,
    indices: IntArray,
    base: Color,
    highlight: Color,
): AnnotatedString {
    if (indices.isEmpty()) {
        return AnnotatedString(text, SpanStyle(color = base))
    }
    val set = indices.toHashSet()
    return buildAnnotatedString {
        text.forEachIndexed { i, ch ->
            if (i in set) {
                withStyle(SpanStyle(color = highlight, fontWeight = FontWeight.Bold)) {
                    append(ch)
                }
            } else {
                withStyle(SpanStyle(color = base)) {
                    append(ch)
                }
            }
        }
    }
}
