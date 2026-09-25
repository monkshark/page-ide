package page.atlas.render

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.nio.file.Path
import page.atlas.graph.GraphInsights
import page.atlas.graph.GraphNode
import page.atlas.graph.GraphEdge
import page.atlas.graph.GraphSlice
import page.atlas.toNioPath

private const val HUB_MIN_DEPENDENTS = 8
private enum class FindingKind { CYCLE, SHARED }
private data class StructureFinding(
    val id: String,
    val kind: FindingKind,
    val members: List<GraphNode>,
    val dependents: Int = 0,
) {
    val anchor: GraphNode get() = members.first()
    val title: String get() = if (kind == FindingKind.CYCLE) "Dependency cycle" else anchor.label
    val summary: String get() = when {
        kind != FindingKind.CYCLE -> "$dependents files depend on this file"
        members.size == 1 -> "This file depends on itself"
        else -> "${members.size} files connected in a cycle"
    }
}

@Composable
fun AtlasProblemsPanel(
    slice: GraphSlice,
    focusId: String?,
    onOpen: (Path) -> Unit,
    onRefocus: (String) -> Unit,
    modifier: Modifier = Modifier,
    onExploreCycle: (String) -> Unit = onRefocus,
    onExploreImpact: (String) -> Unit = onRefocus,
    onOpenLocation: (Path, Int) -> Unit = { path, _ -> onOpen(path) },
) {
    val findings = remember(slice, focusId) {
        val cycles = GraphInsights.cycleGroups(slice).map {
            StructureFinding("cycle:${it.members.first().id}", FindingKind.CYCLE, it.members)
        }
        val shared = GraphInsights.hubs(slice).filter { it.dependents >= HUB_MIN_DEPENDENTS }.map {
            StructureFinding("shared:${it.node.id}", FindingKind.SHARED, listOf(it.node), it.dependents)
        }
        (cycles + shared).sortedByDescending { finding -> finding.members.any { it.id == focusId } }
    }
    var filter by remember { mutableStateOf<FindingKind?>(null) }
    var selectedId by remember(focusId) { mutableStateOf<String?>(null) }
    val filtered = findings.filter { filter == null || it.kind == filter }
    val selected = filtered.firstOrNull { it.id == selectedId } ?: filtered.firstOrNull()
    val colors = MaterialTheme.colorScheme
    val cycles = findings.count { it.kind == FindingKind.CYCLE }
    val shared = findings.size - cycles
    Surface(modifier.fillMaxSize(), color = colors.background, contentColor = colors.onSurface) {
        ProvideTextStyle(TextStyle(fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.sp)) {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Structure review", fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
                    Text("Find tightly coupled code and understand what a change could affect.", color = colors.onSurfaceVariant)
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExploreAction("All ${findings.size}", selected = filter == null) { filter = null }
                    ExploreAction("Cycles $cycles", selected = filter == FindingKind.CYCLE) { filter = FindingKind.CYCLE }
                    ExploreAction("Shared files $shared", selected = filter == FindingKind.SHARED) { filter = FindingKind.SHARED }
                }
                Divider()
                if (selected == null) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (findings.isEmpty()) "No structural findings" else "No findings in this category", fontSize = 17.sp,
                                fontWeight = FontWeight.Medium)
                            Text(if (findings.isEmpty()) "No cycles or widely shared files were found in the analyzed graph."
                                else "Choose another category to continue reviewing.", color = colors.onSurfaceVariant)
                            if (findings.isNotEmpty()) ExploreAction("Show all findings") { filter = null }
                        }
                    }
                } else BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val list: @Composable (Modifier) -> Unit = { pane ->
                        FindingList(filtered, selected.id, onSelect = { selectedId = it }, modifier = pane)
                    }
                    val details: @Composable (Modifier) -> Unit = { pane ->
                        androidx.compose.runtime.key(selected.id) {
                            FindingDetails(selected, slice, onOpen, onOpenLocation, onRefocus, onExploreCycle, onExploreImpact, pane)
                        }
                    }
                    if (maxWidth >= 820.dp) Row(Modifier.fillMaxSize()) {
                        list(Modifier.width(270.dp).fillMaxHeight())
                        Divider(vertical = true)
                        details(Modifier.weight(1f).fillMaxHeight())
                    } else Column(Modifier.fillMaxSize()) {
                        LazyRow(Modifier.fillMaxWidth().height(82.dp).background(colors.surface).padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(filtered, key = { it.id }) { finding ->
                                Column(Modifier.width(230.dp).fillMaxHeight().clip(RoundedCornerShape(8.dp))
                                    .background(if (finding.id == selected.id) colors.primary.copy(alpha = .1f) else colors.background)
                                    .border(1.dp, if (finding.id == selected.id) colors.primary.copy(alpha = .35f) else colors.outlineVariant.copy(alpha = .3f), RoundedCornerShape(8.dp))
                                    .semantics { this.selected = finding.id == selected.id }
                                    .clickable(role = Role.Button) { selectedId = finding.id }.padding(12.dp),
                                    verticalArrangement = Arrangement.Center) {
                                    Text(finding.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(finding.summary, fontSize = 11.sp, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        Divider()
                        details(Modifier.fillMaxWidth().weight(1f))
                    }
                }
                Divider()
                Text("Static analysis · These findings are review signals, not compiler errors.", fontSize = 11.sp,
                    color = colors.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp))
            }
        }
    }
}

fun atlasProblemCount(slice: GraphSlice): Int =
    GraphInsights.cycleGroups(slice).size + GraphInsights.hubs(slice).count { it.dependents >= HUB_MIN_DEPENDENTS }

@Composable
private fun FindingList(findings: List<StructureFinding>, selectedId: String, onSelect: (String) -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val scroll = rememberLazyListState()
    Box(modifier.background(colors.surface)) {
        LazyColumn(Modifier.fillMaxSize().padding(10.dp), state = scroll, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(findings, key = { it.id }) { finding ->
                val selected = finding.id == selectedId
                val tint = if (finding.kind == FindingKind.CYCLE) atlasRoleColors().cycle else colors.primary
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(if (selected) colors.primary.copy(alpha = .09f) else Color.Transparent)
                    .semantics { this.selected = selected }.clickable(role = Role.Button) { onSelect(finding.id) }.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (finding.kind == FindingKind.CYCLE) "↻" else "◇", color = tint, fontSize = 20.sp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(finding.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(finding.summary, fontSize = 11.sp, color = colors.onSurfaceVariant)
                        if (finding.kind == FindingKind.CYCLE) Text(finding.members.joinToString(" · ") { it.label },
                            fontSize = 11.sp, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

@Composable
private fun FindingDetails(
    finding: StructureFinding,
    slice: GraphSlice,
    onOpen: (Path) -> Unit,
    onOpenLocation: (Path, Int) -> Unit,
    onRefocus: (String) -> Unit,
    onExploreCycle: (String) -> Unit,
    onExploreImpact: (String) -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val cycle = finding.kind == FindingKind.CYCLE
    val tint = if (cycle) atlasRoleColors().cycle else colors.primary
    val members = remember(slice, finding) {
        if (cycle) finding.members else {
            val incoming = slice.edges.filter { it.to == finding.anchor.id && it.from != finding.anchor.id }.mapTo(hashSetOf()) { it.from }
            slice.nodes.filter { it.id in incoming }.sortedBy { it.label }
        }
    }
    val byId = remember(slice) { slice.nodes.associateBy { it.id } }
    val relevantEdges = remember(slice, finding) {
        val ids = finding.members.mapTo(hashSetOf()) { it.id }
        slice.edges.filter { if (cycle) it.from in ids && it.to in ids else it.to == finding.anchor.id && it.from != it.to }
    }
    var selectedFile by remember { mutableStateOf<String?>(null) }
    var selectedEdge by remember { mutableStateOf<GraphEdge?>(null) }
    var edgeLimit by remember(selectedFile) { mutableStateOf(6) }
    val connections = relevantEdges.filter { selectedFile == null || it.from == selectedFile || it.to == selectedFile }
    val graphNodes = if (cycle) members else listOf(finding.anchor) + members
    val scroll = rememberLazyListState()
    Box(modifier) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp).semantics { contentDescription = "Finding details" }, state = scroll,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (cycle) "DEPENDENCY CYCLE" else "SHARED DEPENDENCY", color = tint, fontSize = 10.sp, letterSpacing = 1.sp)
                    Text(when {
                        !cycle -> finding.anchor.label
                        finding.members.size == 1 -> "This file depends on itself"
                        else -> "${finding.members.size} files depend on each other"
                    },
                        fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (cycle) "A closed dependency chain connects these files. Explore the cycle to see the connections and their source."
                        else "${finding.dependents} files depend directly on this file. Review its dependents before changing shared behavior.",
                        color = colors.onSurfaceVariant, lineHeight = 20.sp)
                    if (!cycle) Text(finding.anchor.path?.toString() ?: "External dependency", fontSize = 11.sp, color = colors.onSurfaceVariant)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExploreAction(if (cycle) "Explore cycle" else "Review change impact", accent = true) {
                            if (cycle) onExploreCycle(finding.anchor.id) else onExploreImpact(finding.anchor.id)
                        }
                        if (!cycle) finding.anchor.path?.let { path -> ExploreAction("Open file") { onOpen(path.toNioPath()) } }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("CONNECTION MAP", fontSize = 10.sp, letterSpacing = 1.sp, color = colors.onSurfaceVariant,
                            modifier = Modifier.weight(1f))
                        Text("${minOf(6, graphNodes.size)} of ${graphNodes.size} files", fontSize = 11.sp, color = colors.onSurfaceVariant)
                    }
                    FindingRelationshipMap(graphNodes, relevantEdges, selectedFile, onSelect = {
                        selectedFile = if (selectedFile == it) null else it
                        selectedEdge = null
                    })
                    Text("A → B means A uses B. Select a file to filter its connections.", fontSize = 11.sp, color = colors.onSurfaceVariant)
                    if (graphNodes.size > 6) Text("Map preview shows 6 files. Open the full exploration to follow more connections.",
                        fontSize = 11.sp, color = colors.onSurfaceVariant)
                    selectedFile?.let { id ->
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ExploreAction("All connections") { selectedFile = null; selectedEdge = null }
                            byId[id]?.path?.let { path -> ExploreAction("Open ${byId[id]?.label}", description = "Open selected file") { onOpen(path.toNioPath()) } }
                        }
                    }
                    Divider()
                    Text("${connections.size} CONNECTIONS · SELECT TO READ SOURCE", fontSize = 10.sp, letterSpacing = .6.sp,
                        color = colors.onSurfaceVariant)
                }
            }
            items(connections.take(edgeLimit)) { edge ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    RelationshipRow(edge, byId, selectedEdge == edge, onSelect = {
                        selectedEdge = if (selectedEdge == edge) null else edge
                    })
                    if (selectedEdge == edge) RelationshipEvidence(edge, byId[edge.from], onOpen, onOpenLocation)
                }
            }
            if (connections.size > edgeLimit) item {
                ExploreAction("Show more connections · ${connections.size - edgeLimit} remaining") { edgeLimit += 12 }
            }
            item {
                Text(if (cycle) "FILES IN THIS CYCLE" else "DIRECT DEPENDENTS", fontSize = 10.sp, letterSpacing = 1.sp,
                    color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
            }
            items(members, key = { it.id }) { node ->
                Column {
                Row(Modifier.fillMaxWidth().heightIn(min = 54.dp)
                    .padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(node.label, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(node.path?.toString() ?: "External dependency", fontSize = 11.sp, color = colors.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    ExploreAction("Explore", description = "Explore ${node.label}") { onRefocus(node.id) }
                    node.path?.let { path -> ExploreAction("↗", description = "Open ${node.label}") { onOpen(path.toNioPath()) } }
                }
                Divider()
                }
            }
            item { Box(Modifier.size(12.dp)) }
        }
        VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}
