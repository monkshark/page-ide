package page.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import page.atlas.graph.FileNeighbours
import page.atlas.graph.GraphNode
import page.atlas.toNioPath
import page.ui.Glass
import java.nio.file.Path

enum class StructureView { USED_BY, USES, CYCLE }

internal class AboveAnchor(private val gapPx: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = anchorBounds.left.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val y = (anchorBounds.top - popupContentSize.height - gapPx).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}

internal fun groupedFiles(nodes: List<GraphNode>, groupOf: (Path) -> String?): List<Pair<String, List<GraphNode>>> =
    nodes.groupBy { n -> n.path?.toNioPath()?.let { p -> groupOf(p) ?: p.parent?.fileName?.toString() } ?: "" }
        .entries.sortedBy { it.key.lowercase() }
        .map { it.key to it.value }

@Composable
fun StructurePopover(
    view: StructureView,
    fileName: String,
    neighbours: FileNeighbours,
    groupOf: (Path) -> String?,
    onViewChange: (StructureView) -> Unit,
    onOpen: (Path) -> Unit,
    onDismiss: () -> Unit,
) {
    Popup(
        popupPositionProvider = AboveAnchor(6),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
        onKeyEvent = { if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) { onDismiss(); true } else false },
    ) {
        val colors = Glass.colors
        val shape = RoundedCornerShape(10.dp)
        Column(
            Modifier.width(360.dp).shadow(14.dp, shape).clip(shape).background(colors.surfaceOverlay).border(1.dp, colors.outline, shape).testTag("structure-popover"),
        ) {
            val (title, lede) = when (view) {
                StructureView.USED_BY -> "${neighbours.usedBy.size} files use $fileName" to "Change this file and these are what to check."
                StructureView.USES -> "$fileName uses ${neighbours.uses.size} files" to "What this file needs to work."
                StructureView.CYCLE -> "This file is in a loop" to "Each file needs the next one. Removing any one arrow ends the loop."
            }
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = colors.text, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.size(20.dp).clip(RoundedCornerShape(4.dp)).clickable(onClick = onDismiss).testTag("structure-popover-close"),
                        contentAlignment = Alignment.Center,
                    ) {
                        page.ui.CloseMark(tint = colors.faint)
                    }
                }
                Text(lede, color = colors.muted, fontSize = 12.sp)
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 0.dp).heightIn(max = 1.dp).background(colors.outline))
            if (view != StructureView.CYCLE) {
                Row(Modifier.padding(start = 10.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Segment("Used by ${neighbours.usedBy.size}", view == StructureView.USED_BY) { onViewChange(StructureView.USED_BY) }
                    Segment("Uses ${neighbours.uses.size}", view == StructureView.USES) { onViewChange(StructureView.USES) }
                }
            }
            Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()).padding(6.dp)) {
                when (view) {
                    StructureView.CYCLE -> neighbours.cycle.forEachIndexed { i, n ->
                        if (i > 0) Text("↓ needs", color = colors.warn, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(start = 18.dp, top = 2.dp, bottom = 2.dp))
                        FileRow(n, if (i == 0 || i == neighbours.cycle.lastIndex) "this file" else null, onOpen)
                    }
                    else -> {
                        val nodes = if (view == StructureView.USED_BY) neighbours.usedBy else neighbours.uses
                        if (nodes.isEmpty()) {
                            Text("Nothing in this project.", color = colors.faint, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
                        }
                        for ((group, files) in groupedFiles(nodes, groupOf)) {
                            if (group.isNotEmpty()) {
                                Text(group, color = colors.muted, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 3.dp))
                            }
                            files.forEach { FileRow(it, null, onOpen) }
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().heightIn(max = 1.dp).background(colors.outline))
            Text("Click a file to open it · Esc to close", color = colors.faint, fontSize = 11.5.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
    }
}

@Composable
private fun Segment(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = Glass.colors
    val shape = RoundedCornerShape(6.dp)
    Text(
        label,
        color = if (selected) colors.text else colors.muted,
        fontSize = 11.5.sp,
        modifier = Modifier.clip(shape)
            .background(if (selected) colors.primarySoft else colors.surfaceOverlay)
            .border(1.dp, if (selected) colors.primary.copy(alpha = 0.5f) else colors.outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun FileRow(node: GraphNode, note: String?, onOpen: (Path) -> Unit) {
    val colors = Glass.colors
    val path = node.path?.toNioPath()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable(enabled = path != null) { path?.let(onOpen) }.padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(node.label, color = colors.text, fontSize = 12.5.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (note != null) {
            Spacer(Modifier.width(8.dp))
            Text(note, color = colors.faint, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
    }
}
