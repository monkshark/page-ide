package page.atlas.render

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
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
import page.atlas.interaction.ExplorationHighlight
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
    ProvideTextStyle(TextStyle(fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.sp)) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            ExploreAction("← Back", state.canGoBack) { state.back(slice) }
            ExploreAction("Focus on file") { state.start(slice, focus.id) }
            ExploreAction("Change impact", selected = exploration.highlight == ExplorationHighlight.IMPACT) {
                state.update(exploration.showImpact(slice))
            }
            ExploreAction("Find cycles", selected = exploration.highlight == ExplorationHighlight.CYCLE) {
                state.update(exploration.showCycle(slice))
            }
            if (exploration.highlight != null || exploration.selectedEdge != null || exploration.message != null) {
                ExploreAction("Clear highlight") { state.update(exploration.clearHighlight()) }
            }
        }
        Divider()
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth >= 880.dp
            val graph: @Composable (Modifier) -> Unit = { graphModifier ->
                Column(graphModifier) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = if (wide) 18.dp else 12.dp),
                        verticalArrangement = Arrangement.spacedBy(if (wide) 8.dp else 6.dp)) {
                        if (wide) Text("DEPENDENCY EXPLORER", fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(focus.label, fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Select a file to explore. Select a connection to see the source.", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val hidden = exploration.neighbors(slice, ExplorationDirection.USES)
                            .plus(exploration.neighbors(slice, ExplorationDirection.USED_BY)).distinct().count { it !in exploration.positions }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val roles = atlasRoleColors()
                            ExplorationBadge("Used by ${exploration.neighbors(slice, ExplorationDirection.USED_BY).size}", roles.usedBy)
                            ExplorationBadge("Uses ${exploration.neighbors(slice, ExplorationDirection.USES).size}", roles.dependency)
                            if (hidden > 0) ExplorationBadge("$hidden more to explore", MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (state.trail.size > 1) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Visited", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            for ((index, id) in state.trail.withIndex()) {
                                if (index > 0) Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(byId[id]?.label ?: id, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = if (index == state.trail.lastIndex) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.widthIn(max = 140.dp).clip(RoundedCornerShape(4.dp))
                                        .clickable(enabled = index != state.trail.lastIndex, role = Role.Button) { state.revisit(slice, index) }
                                        .padding(horizontal = 4.dp, vertical = 5.dp))
                            }
                        }
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
                        revealRequest = state.revealRequest,
                        onRevealHandled = state::acknowledgeReveal,
                        onExpand = { state.update(state.exploration.expand(slice, it)) },
                        onCollapse = { state.update(state.exploration.collapse(it)) },
                        onClear = { state.update(state.exploration.clearHighlight()) },
                    )
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${exploration.positions.size} of ${slice.nodes.size} files shown", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        ExploreAction("Fit view") { state.camera.scale = 0f }
                        ExploreAction("−", description = "Zoom out") { zoomExploration(state, .8f) }
                        Text("${(state.camera.scale * 100).toInt()}%", fontSize = 10.sp, fontFamily = EditorFontFamily)
                        ExploreAction("+", description = "Zoom in") { zoomExploration(state, 1.25f) }
                    }
                }
            }
            val inspector: @Composable (Modifier) -> Unit = { inspectorModifier ->
                ExplorationInspector(slice, focus, state, onOpen, onOpenLocation, inspectorModifier)
            }
            if (wide) Row(Modifier.fillMaxSize()) {
                graph(Modifier.weight(1f).fillMaxHeight())
                Divider(vertical = true)
                inspector(Modifier.width(320.dp).fillMaxHeight())
            } else Column(Modifier.fillMaxSize()) {
                graph(Modifier.weight(1f).fillMaxWidth())
                Divider()
                inspector(Modifier.height(260.dp).fillMaxWidth())
            }
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
    val colors = MaterialTheme.colorScheme
    val scroll = rememberScrollState()
    Box(modifier.background(colors.surface)) {
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(if (edge == null) "FILE DETAILS" else "CONNECTION DETAILS", fontSize = 10.sp,
            letterSpacing = 1.sp, fontWeight = FontWeight.Medium, color = colors.onSurfaceVariant)
        if (edge != null) {
            val source = byId[edge.from]
            val target = byId[edge.to]
            Column(Modifier.fillMaxWidth().background(colors.primary.copy(alpha = .06f), RoundedCornerShape(12.dp))
                .border(1.dp, colors.primary.copy(alpha = .18f), RoundedCornerShape(12.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(source?.label ?: edge.from, fontFamily = EditorFontFamily, fontSize = 13.sp,
                    fontWeight = FontWeight.Medium)
                Text("↓  ${edge.kind.explorationLabel()}", fontSize = 12.sp, color = colors.primary)
                Text(target?.label ?: edge.to, fontFamily = EditorFontFamily, fontSize = 13.sp,
                    fontWeight = FontWeight.Medium)
            }
            val evidence = edge.evidence
            if (evidence != null) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(colors.background).border(1.dp, colors.outline.copy(alpha = .3f), RoundedCornerShape(10.dp))) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Source preview", fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text("L${evidence.line + 1}", fontSize = 11.sp, color = colors.onSurfaceVariant)
                    }
                    Divider()
                    SelectionContainer {
                        Text(evidence.text.take(2400), fontFamily = EditorFontFamily, fontSize = 12.sp, lineHeight = 19.sp,
                            modifier = Modifier.fillMaxWidth().padding(12.dp))
                    }
                }
                if (evidence.text.length > 2400) Text("Source excerpt shortened. Open the file to read more.", fontSize = 10.sp)
                source?.path?.let { path ->
                    Text(path.toString(), fontSize = 11.sp, color = colors.onSurfaceVariant)
                    ExploreAction("Open source · line ${evidence.line + 1}  ↗", accent = true, modifier = Modifier.fillMaxWidth()) {
                        onOpenLocation(path.toNioPath(), evidence.line)
                    }
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
        }
        if (edge == null) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(focus.label, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
            Text(focus.path?.toString() ?: "External dependency · source unavailable", fontSize = 11.sp,
                color = colors.onSurfaceVariant)
        }
        focus.path?.let { path -> ExploreAction("Open file  ↗", accent = true,
            modifier = Modifier.fillMaxWidth()) { onOpen(path.toNioPath()) } }
        ExploreAction(if (traceMode) "Cancel path selection" else "Trace a dependency path", selected = traceMode,
            modifier = Modifier.fillMaxWidth()) { traceMode = !traceMode }
        if (traceMode) {
            Text("Find a destination. The path follows dependency direction.", fontSize = 12.sp, color = colors.onSurfaceVariant)
            Box(Modifier.fillMaxWidth().background(colors.background, RoundedCornerShape(8.dp))
                .border(1.dp, colors.primary.copy(alpha = .5f), RoundedCornerShape(8.dp)).padding(12.dp)) {
                if (traceQuery.isEmpty()) Text("Destination file or path", fontSize = 12.sp, color = colors.onSurfaceVariant)
                BasicTextField(traceQuery, onValueChange = { traceQuery = it }, singleLine = true,
                    textStyle = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, color = colors.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), modifier = Modifier.fillMaxWidth())
            }
            val candidates = if (traceQuery.isBlank()) exploration.positions.keys.mapNotNull { byId[it] }
            else atlasSearchMatches(slice.nodes, traceQuery)
            val destinations = candidates.filter { it.id != focus.id }
            if (destinations.isEmpty()) Text("No matching destination. Try another file name or path.", fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            for (node in destinations.take(12)) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) {
                    state.update(exploration.traceTo(slice, node.id)); traceMode = false
                }.padding(10.dp)) {
                    Text(node.label, fontSize = 12.sp, color = colors.primary)
                    Text(node.path?.parent?.toString().orEmpty(), fontSize = 11.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, color = colors.onSurfaceVariant)
                }
            }
            if (destinations.size > 12) Text("${destinations.size} matches. Refine the destination search.", fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        for (direction in ExplorationDirection.entries) {
            val ids = exploration.neighbors(slice, direction)
            val hidden = ids.count { it !in exploration.positions }
            Divider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (direction == ExplorationDirection.USES) "Uses" else "Used by", fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                ExplorationBadge(ids.size.toString(), if (direction == ExplorationDirection.USES) atlasRoleColors().dependency else atlasRoleColors().usedBy)
            }
            if (ids.isEmpty()) Text("No relationships found in this analysis.", fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            for (id in ids.take(listLimit)) {
                val node = byId.getValue(id)
                Column(Modifier.fillMaxWidth().background(colors.background.copy(alpha = .65f), RoundedCornerShape(10.dp))
                    .padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(node.label, fontFamily = EditorFontFamily, fontSize = 12.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).clickable(role = Role.Button) {
                            state.update(exploration.select(slice, id))
                        }.padding(vertical = 4.dp))
                    val relationships = slice.edges.filter {
                        if (direction == ExplorationDirection.USES) it.from == focus.id && it.to == id
                        else it.to == focus.id && it.from == id
                    }
                    for (relationship in relationships) {
                        Text("${relationship.kind.explorationLabel()} · view source  ↗", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).clickable(role = Role.Button) {
                                val visible = exploration.select(slice, id).copy(selectedId = focus.id)
                                state.update(visible.inspect(slice, relationship))
                            }.padding(vertical = 6.dp))
                    }
                }
            }
            if (ids.size > listLimit) ExploreAction("Show ${minOf(8, ids.size - listLimit)} more in list") { listLimit += 8 }
            if (hidden > 0) ExploreAction("Add ${minOf(4, hidden)} to graph · $hidden hidden",
                enabled = exploration.positions.size < page.atlas.interaction.DependencyExploration.MAX_VISIBLE) {
                state.update(exploration.expand(slice, direction))
            }
            if (exploration.canCollapse(direction)) ExploreAction("Collapse this branch") {
                state.update(exploration.collapse(direction))
            }
        }
        }
        Divider()
        Text("Static relationships from the analyzed files. Unresolved or unsupported relationships may be missing.",
            fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 8.dp))
    }
}

@Composable
internal fun ExploreAction(
    label: String,
    enabled: Boolean = true,
    accent: Boolean = false,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    description: String = label,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fill = when {
        accent -> colors.primary
        selected -> lerp(colors.surface, colors.primary, .14f)
        hovered && enabled -> lerp(colors.surface, colors.onSurface, .07f)
        else -> colors.surface
    }
    val foreground = if (accent) colors.onPrimary else if (selected) colors.primary else colors.onSurface
    Box(modifier.defaultMinSize(minWidth = 34.dp, minHeight = 34.dp).clip(RoundedCornerShape(8.dp))
        .background(fill.copy(alpha = if (enabled) 1f else .45f))
        .border(1.dp, if (accent || selected) colors.primary.copy(alpha = .5f) else colors.outline.copy(alpha = .35f), RoundedCornerShape(8.dp))
        .semantics { contentDescription = description; this.selected = selected }
        .hoverable(interaction, enabled).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(label, style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
            color = foreground.copy(alpha = if (enabled) 1f else .4f))
    }
}

@Composable
private fun ExplorationBadge(label: String, color: Color) {
    Text(label, style = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium), color = color,
        modifier = Modifier.background(color.copy(alpha = .1f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp))
}
