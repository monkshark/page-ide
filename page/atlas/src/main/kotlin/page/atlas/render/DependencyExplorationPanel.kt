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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
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
    onOverview: (() -> Unit)? = null,
) {
    LaunchedEffect(slice) { state.onSliceChanged(slice, initialFocusId) }
    val exploration = state.exploration
    val byId = remember(slice) { slice.nodes.associateBy { it.id } }
    val focus = byId[exploration.selectedId]
    var contextOpen by remember { mutableStateOf(false) }
    var analysisMenuOpen by remember { mutableStateOf(false) }
    var traceMode by remember(focus?.id) { mutableStateOf(false) }
    if (focus == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Choose a file using search to explore its relationships.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val colors = MaterialTheme.colorScheme
    Surface(modifier.fillMaxSize(), color = colors.background, contentColor = colors.onBackground) {
        ProvideTextStyle(TextStyle(fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.sp)) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ExploreAction("←", state.canGoBack, description = "Back") { state.back(slice) }
                        if (onOverview != null) ExploreAction("Map", description = "Project map", onClick = onOverview)
                        Text("/", color = colors.outline)
                        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                            Text(focus.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(focus.path?.parent?.toString() ?: "External dependency", fontSize = 12.sp,
                                color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        focus.path?.let { path ->
                            ExploreAction("↗", description = "Open selected file") { onOpen(path.toNioPath()) }
                        }
                        ExploreAction("Change impact", selected = exploration.highlight == ExplorationHighlight.IMPACT) {
                            traceMode = false
                            state.update(exploration.showImpact(slice))
                        }
                        Box {
                            ExploreAction("•••", description = "Analysis tools") { analysisMenuOpen = true }
                            DropdownMenu(expanded = analysisMenuOpen, onDismissRequest = { analysisMenuOpen = false }) {
                                DropdownMenuItem(text = { Text("Trace path") }, onClick = {
                                    analysisMenuOpen = false
                                    traceMode = true
                                }, modifier = Modifier.semantics { contentDescription = "Trace path" })
                                DropdownMenuItem(text = { Text("Find cycles") }, onClick = {
                                    analysisMenuOpen = false
                                    traceMode = false
                                    state.update(exploration.showCycle(slice))
                                })
                                DropdownMenuItem(text = { Text("Focus here") }, onClick = {
                                    analysisMenuOpen = false
                                    traceMode = false
                                    state.start(slice, focus.id)
                                })
                                Divider()
                                DropdownMenuItem(text = { Text("Code context") }, onClick = {
                                    analysisMenuOpen = false
                                    contextOpen = true
                                }, modifier = Modifier.semantics { contentDescription = "Code context" })
                            }
                        }
                    }
                    Divider()
                    if (state.trail.size > 1) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            for ((index, id) in state.trail.withIndex()) {
                                if (index > 0) Text("›", color = colors.outline)
                                Text(byId[id]?.label ?: id, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = if (index == state.trail.lastIndex) colors.onSurface else colors.onSurfaceVariant,
                                    modifier = Modifier.widthIn(max = 160.dp).clip(RoundedCornerShape(4.dp))
                                        .clickable(enabled = index != state.trail.lastIndex, role = Role.Button) { state.revisit(slice, index) }
                                        .padding(horizontal = 6.dp, vertical = 5.dp))
                            }
                        }
                    }
                    if (traceMode) {
                        TraceDestination(slice, focus, onChoose = {
                            state.update(exploration.traceTo(slice, it))
                            traceMode = false
                        }, onClose = { traceMode = false })
                        Divider()
                    }
                    if (exploration.message != null || exploration.highlightedEdges.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(exploration.message.orEmpty(), fontSize = 12.sp, color = colors.onSurfaceVariant,
                                modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (exploration.highlightedEdges.isNotEmpty()) {
                                ExploreAction("Read connections") {
                                    state.update(exploration.inspect(slice, exploration.highlightedEdges.first(), preserveHighlight = true))
                                }
                            }
                            ExploreAction("×", description = "Clear highlight") { state.update(exploration.clearHighlight()) }
                        }
                    }
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        val previewHeight = minOf(210.dp, maxHeight * .42f)
                        Column(Modifier.fillMaxSize()) {
                            DependencyExplorationCanvas(
                                slice, exploration, state.camera,
                                onSelect = { state.update(state.exploration.select(slice, it)) },
                                onInspect = { state.update(state.exploration.inspect(slice, it, preserveHighlight = true)) },
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                revealRequest = state.revealRequest,
                                onRevealHandled = state::acknowledgeReveal,
                                onExpand = { state.update(state.exploration.expand(slice, it), reveal = false) },
                                onCollapse = { state.update(state.exploration.collapse(it)) },
                                onClear = {
                                    state.update(if (state.exploration.selectedEdge != null) state.exploration.copy(selectedEdge = null)
                                        else state.exploration.clearHighlight())
                                },
                            )
                            exploration.selectedEdge?.let { edge ->
                                Divider()
                                ConnectionPreview(slice, edge, state, onOpen, onOpenLocation,
                                    Modifier.fillMaxWidth().height(previewHeight))
                            }
                        }
                    }
                    Divider()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${exploration.positions.size} / ${slice.nodes.size} files", fontSize = 12.sp,
                            color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Text("Select a connection to read its source", fontSize = 12.sp,
                            color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(2f))
                        ExploreAction("Fit", description = "Fit view") { state.camera.scale = 0f }
                        ExploreAction("−", description = "Zoom out") { zoomExploration(state, .8f) }
                        Text("${(state.camera.scale * 100).toInt()}%", fontSize = 11.sp)
                        ExploreAction("+", description = "Zoom in") { zoomExploration(state, 1.25f) }
                    }
                }
                if (contextOpen) CodeBundlePanel(slice, focus.id, exploration.positions.keys, onClose = { contextOpen = false })
            }
        }
    }
}

@Composable
private fun TraceDestination(
    slice: GraphSlice,
    focus: GraphNode,
    onChoose: (String) -> Unit,
    onClose: () -> Unit,
) {
    var query by remember(focus.id) { mutableStateOf("") }
    val colors = MaterialTheme.colorScheme
    val inputFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { inputFocus.requestFocus() }
    Column(Modifier.fillMaxWidth().background(colors.surface).onPreviewKeyEvent {
        if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) { onClose(); true } else false
    }.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Trace path", fontWeight = FontWeight.Medium)
            Box(Modifier.weight(1f).clip(RoundedCornerShape(5.dp)).background(colors.background)
                .border(1.dp, colors.outline.copy(alpha = .5f), RoundedCornerShape(5.dp)).padding(10.dp)) {
                if (query.isEmpty()) Text("Destination file or path", color = colors.onSurfaceVariant)
                BasicTextField(query, onValueChange = { query = it }, singleLine = true,
                    textStyle = TextStyle(fontSize = 12.sp, color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    modifier = Modifier.fillMaxWidth().focusRequester(inputFocus).semantics { contentDescription = "Find path destination" })
            }
            ExploreAction("×", description = "Cancel path selection", onClick = onClose)
        }
        val candidates = (if (query.isBlank()) slice.nodes else atlasSearchMatches(slice.nodes, query)).filter { it.id != focus.id }
        Column(Modifier.fillMaxWidth().height(132.dp).verticalScroll(rememberScrollState())) {
            if (candidates.isEmpty()) Text("No matching destination.", modifier = Modifier.padding(vertical = 12.dp))
            for (node in candidates.take(12)) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).clickable(role = Role.Button) { onChoose(node.id) }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(node.label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(node.path?.parent?.toString().orEmpty(), color = colors.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
            if (candidates.size > 12) Text("More matches. Refine your search.", color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun ConnectionPreview(
    slice: GraphSlice,
    edge: GraphEdge,
    state: ExplorationViewState,
    onOpen: (Path) -> Unit,
    onOpenLocation: (Path, Int) -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val source = slice.nodes.firstOrNull { it.id == edge.from }
    val target = slice.nodes.firstOrNull { it.id == edge.to }
    val evidence = edge.evidence
    val steps = state.exploration.highlightedEdges.toList().ifEmpty {
        slice.edges.filter { it.from in state.exploration.positions && it.to in state.exploration.positions }
    }
    val index = steps.indexOf(edge)
    val scroll = rememberScrollState()
    LaunchedEffect(edge) { scroll.scrollTo(0) }
    Column(modifier.background(colors.surface).semantics { contentDescription = "Connection source preview" }.onPreviewKeyEvent {
        if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) {
            state.update(state.exploration.copy(selectedEdge = null)); true
        } else false
    }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.weight(1f)) {
                Text("${source?.label ?: edge.from} → ${target?.label ?: edge.to}",
                    fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${edge.kind.explorationLabel()} · ${evidence?.let { "line ${it.line + 1}" } ?: "source location unavailable"}",
                    fontSize = 12.sp, color = colors.onSurfaceVariant)
            }
            ExploreAction("←", index > 0, description = "Previous connection") {
                state.update(state.exploration.inspect(slice, steps[index - 1], preserveHighlight = true))
            }
            Text("${index + 1} / ${steps.size}", color = colors.onSurfaceVariant, fontSize = 11.sp)
            ExploreAction("→", index >= 0 && index < steps.lastIndex, description = "Next connection") {
                state.update(state.exploration.inspect(slice, steps[index + 1], preserveHighlight = true))
            }
            source?.path?.let { path ->
                ExploreAction("Open ↗", description = "Open connection in editor") {
                    if (evidence != null) onOpenLocation(path.toNioPath(), evidence.line)
                    else onOpen(path.toNioPath())
                }
            }
            ExploreAction("×", description = "Close source preview") {
                state.update(state.exploration.copy(selectedEdge = null))
            }
        }
        Divider()
        Box(Modifier.weight(1f).fillMaxWidth().background(colors.background)) {
            androidx.compose.foundation.text.selection.SelectionContainer {
                Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (evidence == null) {
                        Text("No source location is available for this relationship.", color = colors.onSurfaceVariant)
                    } else {
                        Text(evidence.text.take(2400), fontFamily = EditorFontFamily, fontSize = 13.sp, lineHeight = 21.sp)
                        if (evidence.text.length > 2400) Text("Excerpt shortened. Open the file to read more.", color = colors.onSurfaceVariant)
                    }
                    source?.path?.let { Text(it.toString(), fontSize = 12.sp, color = colors.onSurfaceVariant) }
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
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
        else -> Color.Transparent
    }
    val foreground = if (accent) colors.onPrimary else if (selected) colors.primary else colors.onSurface
    Box(modifier.defaultMinSize(minWidth = 30.dp, minHeight = 30.dp).clip(RoundedCornerShape(6.dp))
        .background(fill.copy(alpha = fill.alpha * if (enabled) 1f else .45f))
        .border(1.dp, if (selected) colors.primary.copy(alpha = .25f) else Color.Transparent, RoundedCornerShape(6.dp))
        .semantics { contentDescription = description; this.selected = selected }
        .hoverable(interaction, enabled).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(label, style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
            color = foreground.copy(alpha = if (enabled) 1f else .4f))
    }
}
