package page.atlas.render

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.nio.file.Path as FilePath
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.delay
import page.atlas.graph.EdgeKind
import page.atlas.graph.GraphQueries
import page.atlas.graph.GraphSlice
import page.atlas.graph.NodeKind
import page.atlas.graph.aggregateModules
import page.atlas.interaction.OverviewSelection

@Composable
fun AtlasPanel(
    slice: GraphSlice,
    onNodeClick: (FilePath) -> Unit,
    onClose: () -> Unit,
    width: Dp,
    projectMode: Boolean = false,
    onProjectModeChange: (Boolean) -> Unit = {},
    viewTab: AtlasViewTab = AtlasViewTab.GRAPH,
    onViewTabChange: (AtlasViewTab) -> Unit = {},
    showExpand: Boolean = false,
    onExpand: () -> Unit = {},
    mapView: MapViewState = remember { MapViewState() },
    atlasView: AtlasViewState = remember { AtlasViewState() },
    loadProgress: Float? = null,
    vcsMarks: Map<String, VcsMark> = emptyMap(),
    vcsEnabled: Boolean = true,
    onVcsEnabledChange: (Boolean) -> Unit = {},
    activeFileId: String? = null,
    followActive: Boolean = false,
    onFollowActiveChange: (Boolean) -> Unit = {},
    callsSlice: GraphSlice = GraphSlice.EMPTY,
    callsView: AtlasViewState = remember { AtlasViewState() },
    onCallsExpand: (String) -> Unit = {},
    onCallsOpen: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.width(width).fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        AtlasContent(
            slice = slice,
            onNodeClick = onNodeClick,
            onClose = onClose,
            projectMode = projectMode,
            onProjectModeChange = onProjectModeChange,
            viewTab = viewTab,
            onViewTabChange = onViewTabChange,
            showExpand = showExpand,
            onExpand = onExpand,
            mapView = mapView,
            atlasView = atlasView,
            loadProgress = loadProgress,
            vcsMarks = vcsMarks,
            vcsEnabled = vcsEnabled,
            onVcsEnabledChange = onVcsEnabledChange,
            activeFileId = activeFileId,
            followActive = followActive,
            onFollowActiveChange = onFollowActiveChange,
            callsSlice = callsSlice,
            callsView = callsView,
            onCallsExpand = onCallsExpand,
            onCallsOpen = onCallsOpen,
        )
    }
}

@Composable
fun AtlasContent(
    slice: GraphSlice,
    onNodeClick: (FilePath) -> Unit,
    onClose: () -> Unit,
    projectMode: Boolean = false,
    onProjectModeChange: (Boolean) -> Unit = {},
    viewTab: AtlasViewTab = AtlasViewTab.GRAPH,
    onViewTabChange: (AtlasViewTab) -> Unit = {},
    showExpand: Boolean = false,
    onExpand: () -> Unit = {},
    showDock: Boolean = false,
    onDock: () -> Unit = {},
    mapView: MapViewState = remember { MapViewState() },
    atlasView: AtlasViewState = remember { AtlasViewState() },
    loadProgress: Float? = null,
    vcsMarks: Map<String, VcsMark> = emptyMap(),
    vcsEnabled: Boolean = true,
    onVcsEnabledChange: (Boolean) -> Unit = {},
    activeFileId: String? = null,
    followActive: Boolean = false,
    onFollowActiveChange: (Boolean) -> Unit = {},
    callsSlice: GraphSlice = GraphSlice.EMPTY,
    callsView: AtlasViewState = remember { AtlasViewState() },
    onCallsExpand: (String) -> Unit = {},
    onCallsOpen: (String) -> Unit = {},
) {
    LaunchedEffect(slice, atlasView.pendingFocusId) { atlasView.onSliceChanged(slice) }
    LaunchedEffect(callsSlice, callsView.pendingFocusId) { callsView.onSliceChanged(callsSlice) }
    val selectedId = atlasView.selectedId
    val mapSlice = remember(slice, mapView.filter, mapView.pinnedIds) {
        filterForMap(slice, mapView.filter, mapView.pinnedIds)
    }
    val overviewView = remember { MapViewState() }
    var overviewSelection by remember(slice) { mutableStateOf(OverviewSelection.NONE) }
    val drillScope = remember(overviewSelection.drillPath) {
        overviewSelection.drillPath.lastOrNull()?.let { FilePath.of(it) }
    }
    val moduleGraph = remember(slice, drillScope) { aggregateModules(slice, scopeRoot = drillScope) }
    val overviewLayout = remember(moduleGraph) { forceLayout(moduleGraph) }
    LaunchedEffect(drillScope) { overviewView.fitted = false }
    val activeModuleId = remember(moduleGraph) {
        moduleGraph.nodes.firstOrNull { it.kind == NodeKind.ACTIVE }?.id
    }
    val effectiveMarks = if (vcsEnabled) vcsMarks else emptyMap()
    val impacted = remember(slice, effectiveMarks) {
        vcsImpacted(slice.edges, effectiveMarks.keys)
    }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchIndex by remember { mutableStateOf(-1) }
    var tracePath by remember(slice) { mutableStateOf<List<String>>(emptyList()) }
    var traceMessage by remember { mutableStateOf<String?>(null) }
    var depFocused by remember { mutableStateOf(true) }
    var insightFocusOverride by remember(activeFileId) { mutableStateOf<String?>(null) }
    LaunchedEffect(traceMessage) {
        if (traceMessage != null) {
            delay(2500)
            traceMessage = null
        }
    }
    LaunchedEffect(selectedId) {
        if (selectedId == null) tracePath = emptyList()
    }
    val searchSlice = when (viewTab) {
        AtlasViewTab.OVERVIEW -> slice
        AtlasViewTab.DEPENDENCY -> mapSlice
        AtlasViewTab.GRAPH -> slice
        AtlasViewTab.CALLS -> callsSlice
    }
    val searchMatches = remember(searchSlice, searchQuery) {
        atlasSearchMatches(searchSlice.nodes, searchQuery)
    }
    val contentFocus = remember { FocusRequester() }
    fun focusSearchMatch(delta: Int) {
        if (searchMatches.isEmpty()) return
        searchIndex = (searchIndex + delta).mod(searchMatches.size)
        val node = searchMatches[searchIndex]
        if (viewTab == AtlasViewTab.CALLS) {
            callsView.selectedId = node.id
            callsView.pendingFocusId = node.id
        } else {
            atlasView.selectedId = node.id
            atlasView.pendingFocusId = node.id
            mapView.focusCenterId = node.id
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(contentFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.isCtrlPressed && event.key == Key.F) {
                    searchOpen = true
                    true
                } else if (event.type == KeyEventType.KeyDown && event.key == Key.Escape &&
                    viewTab == AtlasViewTab.OVERVIEW &&
                    (overviewSelection.kind != OverviewSelection.Kind.NONE || overviewSelection.drillPath.isNotEmpty())
                ) {
                    overviewSelection = when {
                        overviewSelection.kind != OverviewSelection.Kind.NONE -> overviewSelection.clear()
                        else -> overviewSelection.drillUp()
                    }
                    true
                } else {
                    false
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press) {
                            runCatching { contentFocus.requestFocus() }
                        }
                    }
                }
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "ATLAS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Box(modifier = Modifier.weight(1f))
            if (showExpand) {
                Text(
                    text = "Expand",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { onExpand() }.padding(4.dp),
                )
            }
            if (showDock) {
                Text(
                    text = "Dock",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { onDock() }.padding(4.dp),
                )
            }
            Text(
                text = "Close",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { onClose() }.padding(4.dp),
            )
        }
        Divider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ModeChip("Overview", viewTab == AtlasViewTab.OVERVIEW) { onViewTabChange(AtlasViewTab.OVERVIEW) }
            ModeChip("Dependencies", viewTab == AtlasViewTab.DEPENDENCY) { onViewTabChange(AtlasViewTab.DEPENDENCY) }
            ModeChip("Graph", viewTab == AtlasViewTab.GRAPH) { onViewTabChange(AtlasViewTab.GRAPH) }
            if (callsSlice.nodes.isNotEmpty() || viewTab == AtlasViewTab.CALLS) {
                ModeChip("Calls", viewTab == AtlasViewTab.CALLS) { onViewTabChange(AtlasViewTab.CALLS) }
            }
            Box(modifier = Modifier.weight(1f))
            if (vcsMarks.isNotEmpty()) {
                ModeChip("Changes", vcsEnabled) { onVcsEnabledChange(!vcsEnabled) }
            }
            if (viewTab == AtlasViewTab.DEPENDENCY) {
                ModeChip("Insight", depFocused) { depFocused = true }
                ModeChip("Map", !depFocused) { depFocused = false }
            }
            if ((viewTab == AtlasViewTab.DEPENDENCY && !depFocused) || viewTab == AtlasViewTab.OVERVIEW) {
                ModeChip("Follow", followActive) { onFollowActiveChange(!followActive) }
            }
            if (viewTab == AtlasViewTab.GRAPH) {
                ModeChip("File", !projectMode) { onProjectModeChange(false) }
                ModeChip("Project", projectMode) { onProjectModeChange(true) }
            }
        }
        Divider()
        if (searchOpen) {
            AtlasSearchBar(
                query = searchQuery,
                onQueryChange = {
                    searchQuery = it
                    searchIndex = -1
                },
                matchIndex = if (searchIndex < 0) 0 else searchIndex + 1,
                matchCount = searchMatches.size,
                onNext = { focusSearchMatch(1) },
                onPrev = { focusSearchMatch(-1) },
                onClose = {
                    searchOpen = false
                    searchQuery = ""
                    searchIndex = -1
                    runCatching { contentFocus.requestFocus() }
                },
            )
            Divider()
        }
        if (viewTab == AtlasViewTab.CALLS) {
            if (callsSlice.nodes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Right-click a symbol and choose Show Call Graph in Atlas",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    AtlasCanvas(
                        slice = callsSlice,
                        projectMode = false,
                        selectedId = callsView.selectedId,
                        onSelect = { id ->
                            callsView.selectedId = id
                            if (id != null) onCallsExpand(id)
                        },
                        onNodeClick = onNodeClick,
                        onNodeOpen = onCallsOpen,
                        view = callsView,
                    )
                }
                Divider()
                LegendRow(listOf("calls" to EdgeKind.CALLS))
            }
        } else if (slice.nodes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val progress = loadProgress
                Text(
                    text = when {
                        progress != null -> "Analyzing project… ${(progress * 100).toInt()}%"
                        projectMode || viewTab == AtlasViewTab.DEPENDENCY || viewTab == AtlasViewTab.OVERVIEW -> "No source files"
                        else -> "No imports"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (viewTab == AtlasViewTab.OVERVIEW) {
            if (overviewSelection.drillPath.isNotEmpty()) {
                OverviewBreadcrumb(
                    drillPath = overviewSelection.drillPath,
                    onNavigate = { depth -> overviewSelection = overviewSelection.drillUpTo(depth) },
                )
                Divider()
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                OverviewCanvas(
                    graph = moduleGraph,
                    layout = overviewLayout,
                    activeModuleId = activeModuleId,
                    followActive = followActive,
                    view = overviewView,
                    selection = overviewSelection,
                    onSelectionChange = { overviewSelection = it },
                    onOpenFile = onNodeClick,
                )
                val selectedModule = overviewSelection.moduleId
                    ?.takeIf { overviewSelection.kind == OverviewSelection.Kind.MODULE }
                    ?.let { id -> moduleGraph.nodes.firstOrNull { it.id == id } }
                if (selectedModule != null) {
                    OverviewInspector(
                        graph = moduleGraph,
                        module = selectedModule,
                        onSelectModule = { overviewSelection = overviewSelection.selectModule(it) },
                        onOpenFile = onNodeClick,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    )
                }
                val pathFrom = overviewSelection.moduleId
                    ?.takeIf { overviewSelection.kind == OverviewSelection.Kind.PATH }
                val pathTo = overviewSelection.pathTarget
                    ?.takeIf { overviewSelection.kind == OverviewSelection.Kind.PATH }
                if (pathFrom != null && pathTo != null) {
                    OverviewPathPanel(
                        graph = moduleGraph,
                        from = pathFrom,
                        to = pathTo,
                        onSelectModule = { overviewSelection = overviewSelection.selectModule(it) },
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    )
                }
                if (moduleGraph.droppedModules > 0) {
                    Text(
                        text = "Showing largest ${moduleGraph.nodes.size} modules · ${moduleGraph.droppedModules} smaller hidden",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                                RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        } else if (viewTab == AtlasViewTab.DEPENDENCY) {
            if (depFocused) {
                val insightFocus = remember(slice, activeFileId, selectedId, insightFocusOverride) {
                    insightFocusOverride?.takeIf { id -> slice.nodes.any { it.id == id } }
                        ?: listOf(activeFileId, selectedId)
                            .firstOrNull { id -> id != null && slice.nodes.any { it.id == id } }
                }
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    DependencyInsightPanel(
                        slice = slice,
                        focusId = insightFocus,
                        onOpen = onNodeClick,
                        onRefocus = { insightFocusOverride = it },
                    )
                }
            } else {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                MapCanvas(
                    slice = mapSlice,
                    selectedId = selectedId,
                    onSelect = { atlasView.selectedId = it },
                    onNodeClick = onNodeClick,
                    view = mapView,
                    vcsMarks = effectiveMarks,
                    vcsImpacted = impacted,
                    activeId = activeFileId,
                    tracePath = tracePath,
                    onTracePath = { targetId ->
                        val from = atlasView.selectedId
                        if (from != null) {
                            val found = GraphQueries.findPath(mapSlice.edges, from, targetId)
                            if (found.isNullOrEmpty()) {
                                tracePath = emptyList()
                                traceMessage = "No dependency path found"
                            } else {
                                tracePath = listOf(from) + found.map { it.to }
                            }
                        }
                    },
                )
                val drill = remember(mapSlice, selectedId) {
                    mapDrilldown(mapSlice.nodes, mapSlice.edges, selectedId)
                }
                val impactEntries = remember(mapSlice, impacted) {
                    vcsImpactEntries(mapSlice.nodes, impacted)
                }
                val sel = selectedId
                if ((sel != null && drill.any) || impactEntries.isNotEmpty()) {
                    val selectedIsFolder = remember(mapSlice, sel) {
                        sel != null && mapSlice.nodes.any { it.id != sel && belongsTo(it.id, sel) }
                    }
                    MapDrilldownPanel(
                        drill = if (sel != null) drill else MapDrilldown.EMPTY,
                        showCounterparts = selectedIsFolder,
                        onOpen = onNodeClick,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                        impacted = impactEntries,
                    )
                }
                traceMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(12.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(10.dp),
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            if (slice.nodes.size >= 300) {
                Divider()
                Row(
                    modifier = Modifier.fillMaxWidth().height(24.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Only the first 300 workspace files are analyzed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            }
        } else {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AtlasCanvas(
                    slice = slice,
                    projectMode = projectMode,
                    selectedId = selectedId,
                    onSelect = { atlasView.selectedId = it },
                    onNodeClick = onNodeClick,
                    view = atlasView,
                    vcsMarks = effectiveMarks,
                    vcsImpacted = impacted,
                )
            }
            Divider()
            LegendRow()
        }
    }
}

@Composable
private fun OverviewBreadcrumb(
    drillPath: List<String>,
    onNavigate: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BreadcrumbSegment("root", current = false) { onNavigate(0) }
        drillPath.forEachIndexed { index, id ->
            Text(
                text = "/",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val last = index == drillPath.lastIndex
            BreadcrumbSegment(crumbLabel(id), current = last) { onNavigate(index + 1) }
        }
    }
}

@Composable
private fun BreadcrumbSegment(label: String, current: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
        color = if (current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
        modifier = if (current) Modifier else Modifier.clickable { onClick() },
    )
}

private fun crumbLabel(id: String): String =
    id.substringAfterLast('/').substringAfterLast('\\').ifEmpty { id }

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clickable { onClick() }.padding(horizontal = 2.dp, vertical = 4.dp),
    )
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

@Composable
private fun LegendRow(
    items: List<Pair<String, EdgeKind>> = listOf(
        "import" to EdgeKind.IMPORT,
        "extends" to EdgeKind.EXTENDS,
        "implements" to EdgeKind.IMPLEMENTS,
    ),
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        for ((label, kind) in items) {
            LegendItem(label, kind)
        }
    }
}

@Composable
private fun LegendItem(label: String, kind: EdgeKind) {
    val atlas = rememberAtlasTheme()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Canvas(modifier = Modifier.width(22.dp).height(10.dp)) {
            val y = size.height / 2f
            val from = Offset(0f, y)
            val to = Offset(size.width, y)
            drawAtlasEdge(atlas, kind, from, to, targetRadius = 0f, alpha = 1f)
        }
        Text(
            text = label,
            style = TextStyle(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant),
        )
    }
}

@Composable
private fun AtlasCanvas(
    slice: GraphSlice,
    projectMode: Boolean,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onNodeClick: (FilePath) -> Unit,
    view: AtlasViewState,
    onNodeOpen: ((String) -> Unit)? = null,
    vcsMarks: Map<String, VcsMark> = emptyMap(),
    vcsImpacted: Map<String, Int> = emptyMap(),
) {
    var yaw by view::yaw
    var pitch by view::pitch
    var zoomUser by view::zoomUser
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var hoverPos by remember { mutableStateOf<Offset?>(null) }
    val activeId = slice.nodes.firstOrNull { it.kind == NodeKind.ACTIVE }?.id
    LaunchedEffect(activeId, projectMode) {
        view.onCameraSubject(activeId, projectMode)
    }
    val scene = remember(slice) { buildScene(slice) }
    var fromScene by remember { mutableStateOf(scene) }
    var toScene by remember { mutableStateOf(scene) }
    val morph = remember { Animatable(1f) }
    LaunchedEffect(scene) {
        if (toScene != scene) {
            fromScene = toScene
            toScene = scene
            morph.snapTo(0f)
            morph.animateTo(1f, tween(550, easing = FastOutSlowInEasing))
        }
    }
    val morphT = morph.value
    val blended = remember(fromScene, toScene, morphT) { blendScenes(fromScene, toScene, morphT) }
    val freshIds = remember(fromScene, toScene) {
        if (fromScene === toScene) emptySet()
        else toScene.nodes.map { it.id }.toSet() - fromScene.nodes.map { it.id }.toSet()
    }
    val radius = remember(toScene) { sceneRadius(toScene) }
    val zoom = run {
        val side = min(canvasSize.width, canvasSize.height).toFloat()
        if (side <= 0f) zoomUser else side * 0.42f / radius * zoomUser
    }
    val kindById = remember(slice) { slice.nodes.associate { it.id to it.kind } }
    val weightById = remember(slice) { hubWeights(slice) }
    val labelById = remember(slice) { slice.nodes.associate { it.id to it.label } }
    val neighborsByHover = remember(slice) {
        val map = HashMap<String, MutableSet<String>>()
        for (edge in slice.edges) {
            map.getOrPut(edge.from) { mutableSetOf() }.add(edge.to)
            map.getOrPut(edge.to) { mutableSetOf() }.add(edge.from)
        }
        map
    }
    val atlas = rememberAtlasTheme()
    val labelStyle = TextStyle(fontSize = 10.sp, color = atlas.label)
    val textMeasurer = rememberTextMeasurer()

    fun nodeRadius(id: String): Float {
        val base = if (kindById[id] == NodeKind.ACTIVE) 11f else 8f
        return base * (weightById[id] ?: 1f)
    }

    fun projected(): List<ProjectedNode> {
        if (canvasSize.width <= 0 || canvasSize.height <= 0) return emptyList()
        return projectScene(blended, yaw, pitch, zoom, canvasSize.width.toFloat(), canvasSize.height.toFloat())
    }

    fun nodeAt(pos: Offset): ProjectedNode? = projected()
        .filter { hypot(pos.x - it.x, pos.y - it.y) <= max(nodeRadius(it.id) * it.scale, 14f) }
        .minByOrNull { hypot(pos.x - it.x, pos.y - it.y) }

    val hoverId = hoverPos?.let { nodeAt(it)?.id }
    val focusId = selectedId ?: hoverId
    val highlighted = focusId?.let { (neighborsByHover[it].orEmpty() + it) }
    val rotationPaused = hoverId != null || selectedId != null
    val rotationOwner = remember { Any() }
    SideEffect { view.holdRotation(rotationOwner, rotationPaused) }
    DisposableEffect(view) {
        onDispose { view.releaseRotation(rotationOwner) }
    }
    LaunchedEffect(view) {
        while (true) {
            withFrameNanos { now -> view.autoRotateTick(now) }
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    yaw += dragAmount.x * 0.008f
                    pitch = (pitch + dragAmount.y * 0.008f).coerceIn(-0.2f, 1.25f)
                    view.lastInteractNanos = System.nanoTime()
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Scroll -> {
                                val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                if (delta != 0f) {
                                    zoomUser = (zoomUser * if (delta > 0f) 0.9f else 1.1f).coerceIn(0.2f, 5f)
                                    view.lastInteractNanos = System.nanoTime()
                                }
                            }
                            PointerEventType.Move -> {
                                hoverPos = event.changes.firstOrNull()?.position
                            }
                            PointerEventType.Exit -> {
                                hoverPos = null
                            }
                        }
                    }
                }
            }
            .pointerInput(slice) {
                detectTapGestures(
                    onTap = { tap ->
                        onSelect(nodeAt(tap)?.id)
                        view.lastInteractNanos = System.nanoTime()
                    },
                    onDoubleTap = { tap ->
                        val hit = nodeAt(tap)
                        if (hit != null) {
                            val open = onNodeOpen
                            if (open != null) open(hit.id)
                            else slice.nodes.firstOrNull { it.id == hit.id }?.path?.let(onNodeClick)
                        }
                        view.lastInteractNanos = System.nanoTime()
                    },
                )
            },
    ) {
        val projectedNodes = projectScene(blended, yaw, pitch, zoom, size.width, size.height)
        if (projectedNodes.isEmpty()) return@Canvas
        val byId = projectedNodes.associateBy { it.id }
        val minDepth = projectedNodes.minOf { it.depth }
        val maxDepth = projectedNodes.maxOf { it.depth }
        val depthRange = (maxDepth - minDepth).coerceAtLeast(1f)
        fun depthAlpha(depth: Float): Float = 0.5f + 0.5f * ((maxDepth - depth) / depthRange)
        val labelBudget = if (zoomUser >= 1.5f) 24 else 8

        for (ring in blended.rings) {
            val c = projectPoint(0f, ring.y, 0f, yaw, pitch, zoom, size.width, size.height)
            val rx = ring.radius * c.scale
            val ry = rx * abs(sin(pitch))
            drawAtlasRing(atlas, Offset(c.x, c.y), rx, ry)
        }

        for (edge in slice.edges) {
            val from = byId[edge.from] ?: continue
            val to = byId[edge.to] ?: continue
            val dimmed = highlighted != null && (edge.from !in highlighted || edge.to !in highlighted)
            val alpha = if (dimmed) 0.08f else depthAlpha((from.depth + to.depth) / 2f)
            val start = Offset(from.x, from.y)
            val end = Offset(to.x, to.y)
            val targetRadius = nodeRadius(edge.to) * to.scale
            drawAtlasEdge(atlas, edge.kind, start, end, targetRadius, alpha)
        }
        for (p in projectedNodes) {
            val kind = kindById[p.id] ?: continue
            val pos = Offset(p.x, p.y)
            val r = (nodeRadius(p.id) * p.scale).coerceAtLeast(2f)
            var alpha = depthAlpha(p.depth)
            if (p.id in freshIds) alpha *= morphT
            if (highlighted != null && p.id !in highlighted) alpha *= 0.18f
            alpha = alpha.coerceIn(0f, 1f)
            drawAtlasNode(atlas, kind, pos, r, alpha)
            val mark = vcsMarks[p.id]
            if (mark != null) {
                drawAtlasVcsMark(mark, pos, r, alpha)
            } else {
                val impactDepth = vcsImpacted[p.id]
                if (impactDepth != null) drawAtlasVcsImpact(impactDepth, pos, r, alpha)
            }
            if (p.id == selectedId) {
                drawAtlasSelectionRing(atlas, pos, r, hasMark = mark != null, alpha = alpha)
            }
            val showLabel = p.id == hoverId ||
                p.id == selectedId ||
                highlighted?.contains(p.id) == true ||
                kind == NodeKind.ACTIVE ||
                projectedNodes.size <= labelBudget
            if (showLabel) {
                val raw = labelById[p.id] ?: continue
                val label = if (raw.length > 28) raw.take(27) + "…" else raw
                drawAtlasLabel(atlas, textMeasurer, labelStyle, label, pos, r, alpha)
            }
        }
    }
}

private fun blendScenes(from: SceneModel, to: SceneModel, t: Float): SceneModel {
    if (t >= 1f || from === to) return to
    val fromById = from.nodes.associateBy { it.id }
    return SceneModel(
        to.nodes.map { n ->
            val f = fromById[n.id] ?: return@map n
            Node3D(n.id, f.x + (n.x - f.x) * t, f.y + (n.y - f.y) * t, f.z + (n.z - f.z) * t)
        },
        to.rings,
    )
}

