package page.atlas.render

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.nio.file.Path as FilePath
import page.atlas.graph.CycleGroup
import page.atlas.graph.GraphInsights
import page.atlas.graph.GraphNode
import page.atlas.graph.GraphSlice
import page.ui.Glass

@Composable
fun DependencyInsightPanel(
    slice: GraphSlice,
    focusId: String?,
    onOpen: (FilePath) -> Unit,
    onRefocus: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val impact = remember(slice, focusId) {
        focusId?.let { GraphInsights.impact(slice, it) } ?: emptyList()
    }
    val cycleGroups = remember(slice, focusId) {
        val groups = GraphInsights.cycleGroups(slice)
        if (focusId == null) groups
        else groups.sortedByDescending { group -> group.members.any { it.id == focusId } }
    }
    val hubs = remember(slice) { GraphInsights.hubs(slice) }
    val cycleMembers = remember(cycleGroups) {
        cycleGroups.flatMapTo(HashSet()) { group -> group.members.map { it.id } }
    }
    val focusNode = remember(slice, focusId) { slice.nodes.firstOrNull { it.id == focusId } }
    val direct = impact.count { it.depth == 1 }
    val transitive = impact.size - direct

    Column(modifier.fillMaxSize()) {
        Column(Modifier.weight(1.1f).fillMaxWidth()) {
            ImpactHeader(
                title = focusNode?.label ?: "—",
                total = impact.size,
                direct = direct,
                transitive = transitive,
                inCycle = focusId != null && focusId in cycleMembers,
                hasFocus = focusId != null,
            )
            when {
                focusId == null -> EmptyHint("Open a file to see its impact")
                impact.isEmpty() -> EmptyHint("Nothing depends on this file")
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(impact, key = { it.node.id }) { row ->
                        FileRow(
                            node = row.node,
                            onRefocus = onRefocus,
                            onOpen = onOpen,
                            cycleMember = row.node.id in cycleMembers,
                        ) {
                            DepthTag(row.depth)
                            RiskChip(row.ownDependents)
                        }
                    }
                }
            }
        }
        InsightDivider()
        Column(Modifier.weight(1f).fillMaxWidth()) {
            ProblemsHeader(cycleCount = cycleGroups.size, hubCount = hubs.size)
            if (cycleGroups.isEmpty() && hubs.isEmpty()) {
                EmptyHint("No structural problems found")
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(cycleGroups, key = { "cycle:${it.members.first().id}" }) { group ->
                        CycleGroupRow(group, onRefocus, onOpen)
                    }
                    if (hubs.isNotEmpty()) {
                        item { SubHeader("Hubs") }
                        items(hubs, key = { "hub:${it.node.id}" }) { hub ->
                            FileRow(node = hub.node, onRefocus = onRefocus, onOpen = onOpen) {
                                RiskChip(hub.dependents)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImpactHeader(
    title: String,
    total: Int,
    direct: Int,
    transitive: Int,
    inCycle: Boolean,
    hasFocus: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("IMPACT", style = TextStyle(fontSize = 9.sp, color = Glass.colors.muted, fontWeight = FontWeight.SemiBold))
        Text(
            text = ellipsizeMiddle(title, 44),
            style = TextStyle(fontSize = 14.sp, color = Glass.colors.text, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
        )
        if (hasFocus) {
            Text(
                text = "$total dependents · $direct direct · $transitive indirect",
                style = TextStyle(fontSize = 11.sp, color = Glass.colors.muted),
            )
        }
        if (inCycle) {
            Text(
                text = "⟳ in a cycle",
                style = TextStyle(fontSize = 11.sp, color = Glass.colors.danger, fontWeight = FontWeight.Medium),
            )
        }
    }
}

@Composable
private fun ProblemsHeader(cycleCount: Int, hubCount: Int) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("PROBLEMS", style = TextStyle(fontSize = 9.sp, color = Glass.colors.muted, fontWeight = FontWeight.SemiBold))
        Text(
            text = "$cycleCount cycles · $hubCount hubs",
            style = TextStyle(fontSize = 11.sp, color = Glass.colors.muted),
        )
    }
}

@Composable
private fun CycleGroupRow(
    group: CycleGroup,
    onRefocus: (String) -> Unit,
    onOpen: (FilePath) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = "⟳ Dependency cycle · ${group.members.size} files",
            style = TextStyle(fontSize = 11.sp, color = Glass.colors.danger, fontWeight = FontWeight.Medium),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
        )
        for (member in group.members) {
            FileRow(
                node = member,
                onRefocus = onRefocus,
                onOpen = onOpen,
                cycleMember = true,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun FileRow(
    node: GraphNode,
    onRefocus: (String) -> Unit,
    onOpen: (FilePath) -> Unit,
    modifier: Modifier = Modifier,
    cycleMember: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(node.id) {
                detectTapGestures(
                    onTap = { onRefocus(node.id) },
                    onDoubleTap = { node.path?.let(onOpen) },
                )
            }
            .padding(horizontal = 14.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (cycleMember) {
            Box(modifier = Modifier.size(6.dp).background(Glass.colors.danger, CircleShape))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = ellipsizeMiddle(node.label, 44),
                style = TextStyle(fontSize = 12.sp, color = Glass.colors.text),
                maxLines = 1,
            )
            node.path?.let { path ->
                Text(
                    text = ellipsizeMiddle(path.toString(), 56),
                    style = TextStyle(fontSize = 9.sp, color = Glass.colors.faint),
                    maxLines = 1,
                )
            }
        }
        trailing()
    }
}

@Composable
private fun DepthTag(depth: Int) {
    Text(
        text = if (depth <= 1) "direct" else "indirect",
        style = TextStyle(fontSize = 9.sp, color = Glass.colors.muted),
    )
}

@Composable
private fun RiskChip(count: Int) {
    val color = when {
        count >= 8 -> Glass.colors.danger
        count >= 3 -> Glass.colors.warn
        count > 0 -> Glass.colors.primary
        else -> Glass.colors.faint
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(5.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text("↑$count", style = TextStyle(fontSize = 10.sp, color = color, fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun SubHeader(text: String) {
    Text(
        text = text,
        style = TextStyle(fontSize = 9.sp, color = Glass.colors.muted, fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

@Composable
private fun EmptyHint(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Text(text, style = TextStyle(fontSize = 12.sp, color = Glass.colors.muted))
    }
}

@Composable
private fun InsightDivider() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Glass.colors.separator))
}

private fun ellipsizeMiddle(text: String, max: Int): String {
    if (text.length <= max) return text
    val keep = (max - 1).coerceAtLeast(2)
    val head = (keep + 1) / 2
    val tail = keep / 2
    return text.take(head) + "…" + text.takeLast(tail)
}
