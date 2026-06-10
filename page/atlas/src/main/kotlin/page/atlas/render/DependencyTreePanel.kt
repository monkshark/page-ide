package page.atlas.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.nio.file.Path as FilePath
import page.atlas.graph.GraphSlice
import page.atlas.graph.NodeKind

private val DepRowHeight = 22.dp
private val DepIndentStep = 14.dp

@Composable
fun DependencyTreePanel(
    slice: GraphSlice,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onOpen: (FilePath) -> Unit,
) {
    val rootId = slice.nodes.firstOrNull { it.kind == NodeKind.ACTIVE }?.id
    var expandedKeys by remember(rootId) { mutableStateOf(emptySet<String>()) }
    var collapsedSections by remember(rootId) { mutableStateOf(emptySet<DepSection>()) }
    val rows = remember(slice, expandedKeys, collapsedSections) {
        buildDependencyRows(slice, expandedKeys, collapsedSections)
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(rows, key = { it.key }) { row ->
            when (row) {
                is DepRow.Header -> DepHeaderRow(row) {
                    collapsedSections =
                        if (row.section in collapsedSections) collapsedSections - row.section
                        else collapsedSections + row.section
                }
                is DepRow.NodeRow -> DepNodeRow(
                    row = row,
                    selected = row.node.id == selectedId,
                    onToggle = {
                        expandedKeys =
                            if (row.key in expandedKeys) expandedKeys - row.key
                            else expandedKeys + row.key
                    },
                    onSelect = { onSelect(row.node.id) },
                    onOpen = { row.node.path?.let(onOpen) },
                )
                is DepRow.CycleRow -> DepCycleRow(row) {
                    expandedKeys =
                        if (row.key in expandedKeys) expandedKeys - row.key
                        else expandedKeys + row.key
                }
                is DepRow.EmptyRow -> DepEmptyRow(row)
            }
        }
    }
}

@Composable
private fun DepHeaderRow(row: DepRow.Header, onToggle: () -> Unit) {
    val title = when (row.section) {
        DepSection.USES -> "사용함"
        DepSection.USED_BY -> "사용됨"
        DepSection.CYCLES -> "순환"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DepRowHeight)
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DepChevron(expanded = !row.collapsed)
        Spacer(Modifier.width(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "${row.count}",
            style = MaterialTheme.typography.labelSmall,
            color = if (row.section == DepSection.CYCLES && row.count > 0) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DepNodeRow(
    row: DepRow.NodeRow,
    selected: Boolean,
    onToggle: () -> Unit,
    onSelect: () -> Unit,
    onOpen: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val rowColor = when {
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        isHovered -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
        else -> Color.Transparent
    }
    val dotColor = when (row.node.kind) {
        NodeKind.ACTIVE -> MaterialTheme.colorScheme.primary
        NodeKind.WORKSPACE_FILE -> MaterialTheme.colorScheme.secondary
        NodeKind.EXTERNAL -> MaterialTheme.colorScheme.outline
    }
    val textColor =
        if (row.node.kind == NodeKind.EXTERNAL) MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DepRowHeight)
            .background(rowColor)
            .hoverable(interactionSource)
            .combinedClickable(onClick = onSelect, onDoubleClick = onOpen)
            .padding(start = 8.dp + DepIndentStep * (row.depth + 1), end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(14.dp), contentAlignment = Alignment.Center) {
            if (row.expandable) {
                Box(modifier = Modifier.clickable(onClick = onToggle)) {
                    DepChevron(expanded = row.expanded)
                }
            }
        }
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(dotColor, shape = androidx.compose.foundation.shape.CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = row.node.label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (row.cyclic || row.revisit) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = "⟳",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun DepCycleRow(row: DepRow.CycleRow, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DepRowHeight)
            .clickable(onClick = onToggle)
            .padding(start = 8.dp + DepIndentStep, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DepChevron(expanded = row.expanded)
        Spacer(Modifier.width(4.dp))
        Text(
            text = "순환 ${row.index + 1}",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "파일 ${row.size}개",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DepEmptyRow(row: DepRow.EmptyRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DepRowHeight)
            .padding(start = 8.dp + DepIndentStep, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.message,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DepChevron(expanded: Boolean) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = Modifier.size(8.dp).rotate(if (expanded) 90f else 0f)) {
        val w = size.width
        val h = size.height
        drawLine(color, Offset(w * 0.3f, h * 0.15f), Offset(w * 0.75f, h * 0.5f), strokeWidth = 1.4f, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.75f, h * 0.5f), Offset(w * 0.3f, h * 0.85f), strokeWidth = 1.4f, cap = StrokeCap.Round)
    }
}
