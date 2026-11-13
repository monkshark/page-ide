package page.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import page.atlas.graph.FileMargins
import page.atlas.graph.GraphNode
import page.atlas.graph.Need
import page.atlas.toNioPath
import page.ui.EditorFontFamily
import page.ui.Glass
import java.nio.file.Path

internal val NeedsMarginWidth = 216.dp
internal val UsedByMarginWidth = 240.dp
internal val MarginsMinEditorWidth = 900.dp
internal const val USED_BY_SHOWN = 8

data class MarginsBinding(
    val path: Path,
    val margins: FileMargins,
    val groupOf: (Path) -> String?,
    val onOpen: (Path) -> Unit,
)

internal fun visualRow(originalLine: Int, visibleLines: List<Int>): Int {
    if (visibleLines.isEmpty()) return originalLine
    var lo = 0
    var hi = visibleLines.lastIndex
    var best = 0
    while (lo <= hi) {
        val mid = (lo + hi) / 2
        if (visibleLines[mid] <= originalLine) {
            best = mid
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    return best
}

internal fun anchorLine(text: String, fileName: String): Int {
    val base = Regex.escape(fileName.substringBeforeLast('.'))
    val declaration = Regex("""\b(class|object|interface|enum|record|struct|trait|fun|def|func|function)\s+$base\b""")
    val lines = text.lineSequence().toList()
    return lines.indexOfFirst { declaration.containsMatchIn(it) }.takeIf { it >= 0 } ?: 0
}

internal fun loopExplanation(cycle: List<GraphNode>): String {
    val next = cycle.getOrNull(1)?.label ?: return ""
    val steps = cycle.size - 2
    return if (steps <= 1) "This line needs $next, and $next needs this file back. Drop either one to end the loop"
    else "This line needs $next, which leads back here through $steps files. Drop any step to end the loop"
}

@Composable
internal fun MarginHeaders(margins: FileMargins, modifier: Modifier = Modifier) {
    val colors = Glass.colors
    Row(modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.width(NeedsMarginWidth).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Needs", color = colors.muted, fontSize = 12.sp)
            Text(margins.needs.size.toString(), color = colors.text, fontSize = 12.sp, fontFamily = EditorFontFamily, modifier = Modifier.weight(1f))
            Text("libraries not listed", color = colors.faint, fontSize = 11.sp)
        }
        Box(Modifier.weight(1f))
        Row(Modifier.width(UsedByMarginWidth).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Used by", color = colors.muted, fontSize = 12.sp)
            Text(margins.usedBy.size.toString(), color = colors.text, fontSize = 12.sp, fontFamily = EditorFontFamily)
        }
    }
}

@Composable
internal fun NeedsMargin(
    binding: MarginsBinding,
    rowOf: (Int) -> Int,
    lineHeight: Dp,
    topPadding: Dp,
    minHeight: Dp,
) {
    val colors = Glass.colors
    val margins = binding.margins
    val loopTarget = margins.cycle.getOrNull(1)?.id
    val ordered = margins.needs.sortedBy { it.line ?: Int.MAX_VALUE }
    val lastRow = ordered.mapNotNull { it.line }.maxOfOrNull(rowOf) ?: 0
    val desired = ordered.map { need -> topPadding + lineHeight * (need.line?.let(rowOf) ?: (lastRow + 2)) }
    AlignedColumn(
        desiredTop = if (ordered.isEmpty()) listOf(topPadding) else desired,
        modifier = Modifier.width(NeedsMarginWidth).padding(horizontal = 16.dp).testTag("margin-needs"),
        minHeight = minHeight,
    ) {
        if (ordered.isEmpty()) {
            Text("Uses nothing in this project", color = colors.faint, fontSize = 12.sp, modifier = Modifier.height(lineHeight))
        }
        for (need in ordered) {
            val loop = need.node.id == loopTarget
            Column {
                NeedRow(need, binding, loop, lineHeight)
                if (loop) {
                    Text(
                        loopExplanation(margins.cycle),
                        color = colors.text,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier
                            .padding(top = 4.dp, bottom = 4.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
                            .background(colors.warn.copy(alpha = 0.10f))
                            .loopRule(colors.warn.copy(alpha = 0.45f))
                            .padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)
                            .testTag("margin-loop"),
                    )
                }
            }
        }
    }
}

@Composable
private fun NeedRow(need: Need, binding: MarginsBinding, loop: Boolean, lineHeight: Dp) {
    val colors = Glass.colors
    val path = need.node.path?.toNioPath()
    Row(
        Modifier
            .height(lineHeight)
            .fillMaxWidth()
            .clickable(enabled = path != null) { path?.let(binding.onOpen) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            need.node.label,
            color = if (loop) colors.warn else colors.text,
            fontSize = 12.sp,
            fontFamily = EditorFontFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (loop) "loop" else path?.let(binding.groupOf).orEmpty(),
            color = if (loop) colors.warn else colors.faint,
            fontSize = 11.sp,
            maxLines = 1,
        )
    }
}

@Composable
internal fun UsedByMargin(
    binding: MarginsBinding,
    anchorRow: Int,
    lineHeight: Dp,
    topPadding: Dp,
    minHeight: Dp,
) {
    val colors = Glass.colors
    val margins = binding.margins
    val loopIds = margins.cycle.map { it.id }.toSet()
    var expanded by remember(binding.path) { mutableStateOf(false) }
    val shown = if (expanded) margins.usedBy else margins.usedBy.take(USED_BY_SHOWN)
    val groups = shown.groupBy { node -> node.path?.toNioPath()?.let(binding.groupOf) ?: "" }
    AlignedColumn(
        desiredTop = listOf(topPadding + lineHeight * anchorRow),
        modifier = Modifier.width(UsedByMarginWidth).padding(horizontal = 16.dp).testTag("margin-used-by"),
        minHeight = minHeight,
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (margins.usedBy.isEmpty()) {
                Text("Nothing in this project uses it", color = colors.faint, fontSize = 12.sp, modifier = Modifier.height(lineHeight))
            }
            for ((group, nodes) in groups) {
                if (group.isNotEmpty()) {
                    Text(group, color = colors.faint, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
                for (node in nodes) {
                    val path = node.path?.toNioPath()
                    Text(
                        node.label,
                        color = if (node.id in loopIds) colors.warn else colors.text,
                        fontSize = 12.sp,
                        fontFamily = EditorFontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.height(lineHeight).fillMaxWidth()
                            .clickable(enabled = path != null) { path?.let(binding.onOpen) }
                            .padding(top = 2.dp),
                    )
                }
            }
            val hidden = margins.usedBy.size - shown.size
            if (hidden > 0) {
                Text(
                    "$hidden more",
                    color = colors.muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp).clickable { expanded = true }.testTag("margin-used-by-more"),
                )
            }
            if (margins.usedBy.isNotEmpty()) {
                Text("Change this file and check these", color = colors.muted, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

private fun Modifier.loopRule(color: androidx.compose.ui.graphics.Color): Modifier =
    drawBehind { drawRect(color, size = androidx.compose.ui.geometry.Size(2.dp.toPx(), size.height)) }

@Composable
private fun AlignedColumn(
    desiredTop: List<Dp>,
    modifier: Modifier,
    minHeight: Dp,
    content: @Composable () -> Unit,
) {
    Layout(content, modifier) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        var bottom = 0
        val tops = placeables.mapIndexed { i, p ->
            val want = desiredTop.getOrNull(i)?.roundToPx() ?: bottom
            val top = maxOf(want, bottom)
            bottom = top + p.height
            top
        }
        val height = maxOf(bottom, minHeight.roundToPx())
        layout(constraints.maxWidth.takeIf { it != androidx.compose.ui.unit.Constraints.Infinity } ?: placeables.maxOfOrNull { it.width } ?: 0, height) {
            placeables.forEachIndexed { i, p -> p.place(0, tops[i]) }
        }
    }
}
