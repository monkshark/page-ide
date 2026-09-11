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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.nio.file.Path
import page.atlas.graph.EdgeKind
import page.atlas.graph.GraphEdge
import page.atlas.graph.GraphNode
import page.atlas.graph.GraphSlice
import page.atlas.interaction.ExplorationDirection
import page.atlas.toNioPath
import page.ui.EditorFontFamily

internal fun EdgeKind.explorationLabel(): String = when (this) {
    EdgeKind.IMPORT -> "imports"
    EdgeKind.EXTENDS -> "extends"
    EdgeKind.IMPLEMENTS -> "implements"
    EdgeKind.CALLS -> "calls"
}

@Composable
internal fun DependencyExplorationPanel(
    slice: GraphSlice,
    initialFocusId: String?,
    state: ExplorationViewState,
    onOpen: (Path) -> Unit,
    onOpenLocation: (Path, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(slice) { state.onSliceChanged(slice, initialFocusId) }
    val exploration = state.exploration
    val byId = remember(slice) { slice.nodes.associateBy { it.id } }
    val focus = byId[exploration.selectedId]
    if (focus == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Choose a file using search to explore its relationships.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            ExploreAction("Back", state.canGoBack) { state.back(slice) }
            ExploreAction("Start here") { state.start(slice, focus.id) }
            ExploreAction("Show impact") { state.update(exploration.showImpact(slice)) }
            ExploreAction("Find cycle") { state.update(exploration.showCycle(slice)) }
            ExploreAction("Fit") { state.camera.scale = 0f }
            Text("A → B means A uses B", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 9.dp))
        }
        Divider()
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth >= 660.dp
            val graph: @Composable (Modifier) -> Unit = { graphModifier ->
                Column(graphModifier) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(focus.label, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        val hidden = exploration.neighbors(slice, ExplorationDirection.USES)
                            .plus(exploration.neighbors(slice, ExplorationDirection.USED_BY)).distinct().count { it !in exploration.positions }
                        Text("$hidden direct neighbors hidden", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    exploration.message?.let {
                        Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp,
                            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary.copy(alpha = .1f))
                                .padding(horizontal = 16.dp, vertical = 9.dp))
                    }
                    DependencyExplorationCanvas(
                        slice, exploration, state.camera,
                        onSelect = { state.update(state.exploration.select(slice, it)) },
                        onInspect = { state.update(state.exploration.inspect(slice, it)) },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${exploration.positions.size} / ${slice.nodes.size} analyzed nodes", fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        ExploreAction("−") { zoomExploration(state, .8f) }
                        Text("${(state.camera.scale * 100).toInt()}%", fontSize = 10.sp, fontFamily = EditorFontFamily)
                        ExploreAction("+") { zoomExploration(state, 1.25f) }
                    }
                }
            }
            val inspector: @Composable (Modifier) -> Unit = { inspectorModifier ->
                ExplorationInspector(slice, focus, state, onOpen, onOpenLocation, inspectorModifier)
            }
            if (wide) Row(Modifier.fillMaxSize()) {
                graph(Modifier.weight(1f).fillMaxHeight())
                Divider(vertical = true)
                inspector(Modifier.width(280.dp).fillMaxHeight())
            } else Column(Modifier.fillMaxSize()) {
                graph(Modifier.weight(1f).fillMaxWidth())
                Divider()
                inspector(Modifier.height(230.dp).fillMaxWidth())
            }
        }
    }
    }
}

private fun zoomExploration(state: ExplorationViewState, factor: Float) {
    val old = state.camera.scale
    if (old <= 0f) return
    val next = (old * factor).coerceIn(.12f, 3f)
    val selected = state.exploration.positions[state.exploration.selectedId]?.let(::explorationRect)?.center ?: Offset.Zero
    state.camera.pan += selected * (old - next)
    state.camera.scale = next
}

@Composable
private fun ExplorationInspector(
    slice: GraphSlice,
    focus: GraphNode,
    state: ExplorationViewState,
    onOpen: (Path) -> Unit,
    onOpenLocation: (Path, Int) -> Unit,
    modifier: Modifier,
) {
    val exploration = state.exploration
    val edge = exploration.selectedEdge
    val byId = remember(slice) { slice.nodes.associateBy { it.id } }
    var listLimit by remember(focus.id) { mutableStateOf(8) }
    var traceMode by remember(focus.id) { mutableStateOf(false) }
    var traceQuery by remember(focus.id) { mutableStateOf("") }
    Column(modifier.background(MaterialTheme.colorScheme.surface).verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (edge == null) "EXPLORE A FILE" else "RELATIONSHIP EVIDENCE", fontSize = 9.sp,
            letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (edge != null) {
            val source = byId[edge.from]
            val target = byId[edge.to]
            Text("${source?.label ?: edge.from}  →  ${target?.label ?: edge.to}", fontFamily = EditorFontFamily,
                fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            Text(edge.kind.explorationLabel().replaceFirstChar { it.uppercase() }, fontSize = 11.sp)
            val evidence = edge.evidence
            if (evidence != null) {
                Text("Analyzed source · line ${evidence.line + 1}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SelectionContainer {
                    Text(evidence.text.take(2400), fontFamily = EditorFontFamily, fontSize = 11.sp,
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background, RoundedCornerShape(6.dp)).padding(10.dp))
                }
                if (evidence.text.length > 2400) Text("Source excerpt shortened. Open the file to read more.", fontSize = 10.sp)
                source?.path?.let { path ->
                    Text(path.toString(), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ExploreAction("Open source at line ${evidence.line + 1}", accent = true) { onOpenLocation(path.toNioPath(), evidence.line) }
                }
            } else {
                Text("This relationship has no source location in the current analysis.", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                source?.path?.let { path -> ExploreAction("Open source file") { onOpen(path.toNioPath()) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExploreAction("Explore source") { state.update(exploration.select(slice, edge.from)) }
                ExploreAction("Explore target") { state.update(exploration.select(slice, edge.to)) }
            }
            Divider()
        }
        Text(focus.label, fontFamily = EditorFontFamily, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        Text(focus.path?.toString() ?: "External dependency · source unavailable", fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        focus.path?.let { path -> ExploreAction("Open file", accent = true) { onOpen(path.toNioPath()) } }
        ExploreAction(if (traceMode) "Cancel path selection" else "Trace a dependency path") { traceMode = !traceMode }
        if (traceMode) {
            Text("Find a destination. The path follows dependency direction.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background, RoundedCornerShape(6.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)).padding(9.dp)) {
                if (traceQuery.isEmpty()) Text("Destination file or path", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BasicTextField(traceQuery, onValueChange = { traceQuery = it }, singleLine = true,
                    textStyle = TextStyle(fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), modifier = Modifier.fillMaxWidth())
            }
            val candidates = if (traceQuery.isBlank()) exploration.positions.keys.mapNotNull { byId[it] }
            else atlasSearchMatches(slice.nodes, traceQuery)
            val destinations = candidates.filter { it.id != focus.id }
            if (destinations.isEmpty()) Text("No matching destination. Try another file name or path.", fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            for (node in destinations.take(12)) {
                Text(node.label, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().clickable { state.update(exploration.traceTo(slice, node.id)); traceMode = false }.padding(vertical = 5.dp))
                Text(node.path?.parent?.toString().orEmpty(), fontSize = 9.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (destinations.size > 12) Text("${destinations.size} matches. Refine the destination search.", fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        for (direction in ExplorationDirection.entries) {
            val ids = exploration.neighbors(slice, direction)
            val hidden = ids.count { it !in exploration.positions }
            Divider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (direction == ExplorationDirection.USES) "DEPENDS ON" else "USED BY", fontSize = 10.sp,
                    fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text(ids.size.toString(), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (ids.isEmpty()) Text("No relationships found in this analysis.", fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            for (id in ids.take(listLimit)) {
                val node = byId.getValue(id)
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(node.label, fontFamily = EditorFontFamily, fontSize = 11.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().clickable {
                            state.update(exploration.select(slice, id))
                        }.padding(vertical = 4.dp))
                    val relationships = slice.edges.filter {
                        if (direction == ExplorationDirection.USES) it.from == focus.id && it.to == id
                        else it.to == focus.id && it.from == id
                    }
                    for (relationship in relationships) {
                        Text("${relationship.kind.explorationLabel()} · inspect source", fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable {
                                val visible = exploration.select(slice, id).copy(selectedId = focus.id)
                                state.update(visible.inspect(slice, relationship))
                            }.padding(vertical = 3.dp))
                    }
                }
            }
            if (ids.size > listLimit) ExploreAction("Show ${minOf(8, ids.size - listLimit)} more in list") { listLimit += 8 }
            if (hidden > 0) ExploreAction("Add ${minOf(4, hidden)} to graph · $hidden hidden",
                enabled = exploration.positions.size < page.atlas.interaction.DependencyExploration.MAX_VISIBLE) {
                state.update(exploration.expand(slice, direction))
            }
        }
        Divider()
        Text("Static relationships from the analyzed files. Unresolved or unsupported relationships may be missing.",
            fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun ExploreAction(label: String, enabled: Boolean = true, accent: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text(label, fontSize = 10.sp, color = (if (accent) colors.primary else colors.onSurface).copy(alpha = if (enabled) 1f else .4f),
        modifier = Modifier.background(if (accent) colors.primary.copy(alpha = .1f) else colors.surface, RoundedCornerShape(6.dp))
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(6.dp)).clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp))
}
