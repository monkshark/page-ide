package page.app

import page.runtime.*

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.awt.Cursor
import java.net.URI
import java.nio.file.Path
import page.editor.TodoItem
import page.ui.Glass

@Composable
fun TodoPanel(
    items: List<TodoItem>,
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
    var selectedFilters by remember { mutableStateOf(emptySet<String>()) }

    val countByKeyword = remember(items) {
        items.groupingBy { it.keyword }.eachCount()
    }

    val visibleItems = remember(items, selectedFilters) {
        if (selectedFilters.isEmpty()) items
        else items.filter { it.keyword in selectedFilters }
    }

    val groups: List<Pair<Path, List<TodoItem>>> = remember(visibleItems, fileOrder) {
        val byPath = LinkedHashMap<Path, MutableList<TodoItem>>()
        for (item in visibleItems) {
            val path = runCatching { Path.of(URI(item.uri)) }.getOrNull() ?: continue
            byPath.getOrPut(path) { mutableListOf() }.add(item)
        }
        val raw = byPath.map { (k, v) -> k to v.toList() }
        applyFileOrder(raw, fileOrder)
    }

    Surface(
        modifier = modifier.fillMaxWidth().height(height),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            VerticalResizeBar(onResizeDelta = onResizeDelta)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "TODO",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                for (kw in todoOrder) {
                    val n = countByKeyword[kw] ?: 0
                    if (n == 0) continue
                    val active = kw in selectedFilters
                    TodoFilterChip(
                        count = n,
                        label = kw,
                        color = todoKeywordColor(kw),
                        active = active,
                        onClick = {
                            selectedFilters = if (active) selectedFilters - kw else selectedFilters + kw
                        },
                    )
                }
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
            if (visibleItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (items.isEmpty()) "No TODOs to display."
                        else "No items match the selected filters.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val orderKeys = groups.map { it.first.toString() }
                ReorderableFileColumn(
                    keys = orderKeys,
                    onMove = { newKeys -> onFileOrderChange(newKeys) },
                    modifier = Modifier.fillMaxSize(),
                    onGroupClick = { idx ->
                        val pathKey = groups[idx].first.toString()
                        val collapsed = pathKey in collapsedKeys
                        val next = if (collapsed) collapsedKeys - pathKey else collapsedKeys + pathKey
                        onCollapsedKeysChange(next)
                    },
                    headerContent = { idx ->
                        val (path, list) = groups[idx]
                        val collapsed = path.toString() in collapsedKeys
                        TodoFileHeader(path = path, list = list, collapsed = collapsed)
                    },
                    bodyContent = { idx ->
                        val (path, list) = groups[idx]
                        val collapsed = path.toString() in collapsedKeys
                        if (!collapsed) {
                            Column {
                                for (entry in list) {
                                    TodoRow(path, entry, onJump)
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
private fun TodoFileHeader(
    path: Path,
    list: List<TodoItem>,
    collapsed: Boolean,
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
        Text(
            text = "${list.size}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TodoRow(
    path: Path,
    entry: TodoItem,
    onJump: (Path, Int, Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onJump(path, entry.line, entry.column) }
            .padding(start = 28.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(modifier = Modifier.size(8.dp).background(todoKeywordColor(entry.keyword), CircleShape))
        Text(
            text = entry.keyword,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = todoKeywordColor(entry.keyword),
        )
        Text(
            text = "${entry.line + 1}:${entry.column + 1}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = entry.message.ifEmpty { entry.rawLine.trim() },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TodoFilterChip(
    count: Int,
    label: String,
    color: Color,
    active: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (active) color.copy(alpha = 0.18f) else Color.Transparent
    val textColor = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .clickable { onClick() }
            .background(bg, RoundedCornerShape(10.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(modifier = Modifier.size(7.dp).background(color, CircleShape))
        Text(
            text = "$count $label",
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
internal fun todoKeywordColor(keyword: String): Color = when (keyword) {
    "FIXME", "XXX" -> Glass.colors.error
    "HACK" -> Glass.colors.warn
    "NOTE" -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.secondary
}

private val todoOrder = listOf("TODO", "FIXME", "HACK", "XXX", "NOTE")

@Composable
private fun VerticalResizeBar(onResizeDelta: (Dp) -> Unit) {
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
