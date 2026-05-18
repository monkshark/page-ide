package page.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.awt.Cursor
import java.net.URI
import java.nio.file.Path
import page.lsp.ReferenceLocation

data class ReferencesQueryState(
    val symbolName: String,
    val originUri: String,
    val results: List<ReferenceLocation>,
    val isLoading: Boolean,
    val errorMessage: String? = null,
)

@Composable
fun ReferencesPanel(
    state: ReferencesQueryState,
    onJump: (Path, Int, Int) -> Unit,
    onClose: () -> Unit,
    height: Dp,
    onResizeDelta: (Dp) -> Unit,
    linePreviewFor: (String, Int) -> String?,
    modifier: Modifier = Modifier,
) {
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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "References",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (state.symbolName.isNotEmpty()) {
                    Text(
                        text = state.symbolName,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                CountBadge(state.results.size)
                Box(modifier = Modifier.weight(1f))
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
            when {
                state.isLoading -> CenteredMessage("Searching…")
                state.errorMessage != null -> CenteredMessage(state.errorMessage, isError = true)
                state.results.isEmpty() -> CenteredMessage(
                    "No references found for '${state.symbolName}'."
                )
                else -> ReferenceList(
                    results = state.results,
                    onJump = onJump,
                    linePreviewFor = linePreviewFor,
                )
            }
        }
    }
}

@Composable
private fun CenteredMessage(text: String, isError: Boolean = false) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReferenceList(
    results: List<ReferenceLocation>,
    onJump: (Path, Int, Int) -> Unit,
    linePreviewFor: (String, Int) -> String?,
) {
    val groups: List<Pair<Path, List<ReferenceLocation>>> = remember(results) {
        val byPath = LinkedHashMap<Path, MutableList<ReferenceLocation>>()
        for (r in results) {
            val p = runCatching { Path.of(URI(r.uri)) }.getOrNull() ?: continue
            byPath.getOrPut(p) { mutableListOf() }.add(r)
        }
        byPath.map { (k, v) ->
            k to v.sortedWith(compareBy({ it.startLine }, { it.startCharacter }))
        }.sortedBy { it.first.fileName?.toString().orEmpty() }
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        for ((path, list) in groups) {
            item { ReferenceFileHeader(path, list.size) }
            items(list) { ref ->
                ReferenceRow(
                    path = path,
                    ref = ref,
                    preview = linePreviewFor(ref.uri, ref.startLine),
                    onJump = onJump,
                )
            }
        }
    }
}

@Composable
private fun ReferenceFileHeader(path: Path, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
        CountBadge(count, compact = true)
    }
}

@Composable
private fun ReferenceRow(
    path: Path,
    ref: ReferenceLocation,
    preview: String?,
    onJump: (Path, Int, Int) -> Unit,
) {
    val trimmedPreview = preview?.trimEnd()?.take(180)
    val highlight = trimmedPreview?.let {
        val rawStart = ref.startCharacter - (preview.length - preview.trimStart().length)
        val s = rawStart.coerceIn(0, it.length)
        val e = (s + (ref.endCharacter - ref.startCharacter).coerceAtLeast(0)).coerceIn(s, it.length)
        s to e
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onJump(path, ref.startLine, ref.startCharacter) }
            .padding(start = 28.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "${ref.startLine + 1}:${ref.startCharacter + 1}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
        )
        if (trimmedPreview == null) {
            Text(
                text = "(no preview)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
            )
        } else {
            ReferencePreviewText(
                text = trimmedPreview.trimStart(),
                highlight = highlight,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ReferencePreviewText(
    text: String,
    highlight: Pair<Int, Int>?,
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val annotated = buildAnnotatedString {
        if (highlight == null || highlight.second <= highlight.first) {
            append(text)
        } else {
            val (s, e) = highlight
            val sClamped = s.coerceIn(0, text.length)
            val eClamped = e.coerceIn(sClamped, text.length)
            append(text.substring(0, sClamped))
            withStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.SemiBold)) {
                append(text.substring(sClamped, eClamped))
            }
            append(text.substring(eClamped))
        }
    }
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
        fontFamily = FontFamily.Monospace,
        maxLines = 1,
        modifier = modifier,
    )
}

@Composable
private fun CountBadge(count: Int, compact: Boolean = false) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = if (compact) "$count" else "$count refs",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
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

