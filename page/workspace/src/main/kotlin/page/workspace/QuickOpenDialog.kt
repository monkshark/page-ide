package page.workspace

import page.runtime.*

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import page.editor.IndexedFile
import page.editor.QuickOpen
import page.editor.QuickOpenResult
import page.ui.Glass
import page.ui.GlassSurface
import page.ui.GlassSurfaceLevel
import page.ui.GlassTheme

@Composable
fun QuickOpenDialog(
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
        width = 660.dp,
        height = 440.dp,
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
        transparent = true,
        alwaysOnTop = true,
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
            Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                GlassSurface(
                    level = GlassSurfaceLevel.Overlay,
                    shape = RoundedCornerShape(Glass.radius.lg),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                        QueryInput(
                            value = query,
                            onChange = { q ->
                                query = q
                                selected = 0
                            },
                            focus = queryFocus,
                        )
                        Spacer(Modifier.height(10.dp))
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
                        Spacer(Modifier.height(10.dp))
                        HintBar(count = results.size)
                    }
                }
            }
        }
    }
}

@Composable
private fun QueryInput(value: String, onChange: (String) -> Unit, focus: FocusRequester) {
    val colors = Glass.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(Glass.radius.sm))
            .background(colors.surfaceL2)
            .border(1.dp, colors.outline, RoundedCornerShape(Glass.radius.sm))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchGlyph(tint = colors.muted, size = 15.dp)
        Spacer(Modifier.width(9.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                cursorBrush = SolidColor(colors.primary),
                textStyle = TextStyle(
                    color = colors.text,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            if (value.isEmpty()) {
                Text(
                    text = "Search files…",
                    style = LocalTextStyle.current.copy(
                        color = colors.muted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                    ),
                )
            }
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
    val colors = Glass.colors
    if (results.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No matching files",
                color = colors.muted,
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
    val colors = Glass.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val rowBg = when {
        isSelected -> colors.primarySoft
        hovered -> colors.surfaceL2.copy(alpha = colors.surfaceL2.alpha * 0.6f)
        else -> Color.Transparent
    }
    val name = QuickOpen.nameOf(result.file.relative)
    val parent = result.file.relative.dropLast(name.length).trimEnd('/')

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .height(30.dp)
            .clip(RoundedCornerShape(Glass.radius.xs))
            .background(rowBg)
            .hoverable(interaction)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = highlightedName(name, result.nameIndices, colors.text, colors.primary),
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
                color = colors.muted,
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

@Composable
private fun HintBar(count: Int) {
    val colors = Glass.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (count == 1) "1 result" else "$count results",
            color = colors.faint,
            fontSize = 11.sp,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "↑↓ navigate   ↵ open   esc close",
            color = colors.faint,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun SearchGlyph(tint: Color, size: Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val r = w * 0.3f
        val cx = w * 0.42f
        val cy = w * 0.42f
        val stroke = w * 0.12f
        drawCircle(
            color = tint,
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = stroke),
        )
        drawLine(
            color = tint,
            start = Offset(cx + r * 0.72f, cy + r * 0.72f),
            end = Offset(w * 0.9f, w * 0.9f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
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
