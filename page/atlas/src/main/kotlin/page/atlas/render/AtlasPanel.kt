package page.atlas.render

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
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
import page.atlas.graph.drillPathInSlice
import page.atlas.interaction.OverviewSelection

@Composable
fun AtlasPanel(
    slice: GraphSlice,
    onNodeClick: (FilePath) -> Unit,
    onClose: () -> Unit,
    width: Dp,
    projectMode: Boolean = false,
    onProjectModeChange: (Boolean) -> Unit = {},
    viewTab: AtlasViewTab = AtlasViewTab.RELATIONS,
    onViewTabChange: (AtlasViewTab) -> Unit = {},
    showExpand: Boolean = false,
    onExpand: () -> Unit = {},
    mapView: MapViewState = remember { MapViewState() },
    atlasView: AtlasViewState = remember { AtlasViewState() },
    overviewState: OverviewViewState = remember { OverviewViewState() },
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
            overviewState = overviewState,
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
    viewTab: AtlasViewTab = AtlasViewTab.RELATIONS,
    onViewTabChange: (AtlasViewTab) -> Unit = {},
    showExpand: Boolean = false,
    onExpand: () -> Unit = {},
    showDock: Boolean = false,
    onDock: () -> Unit = {},
    mapView: MapViewState = remember { MapViewState() },
    atlasView: AtlasViewState = remember { AtlasViewState() },
    overviewState: OverviewViewState = remember { OverviewViewState() },
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
    val overviewView = overviewState.camera
    var overviewSelection by overviewState.selectionState
    LaunchedEffect(slice) {
        val path = overviewSelection.drillPath
        if (path.isNotEmpty()) {
            val valid = drillPathInSlice(slice, path)
            if (valid.size != path.size) overviewSelection = OverviewSelection(drillPath = valid)
        }
    }
    var drillFrom by remember(slice) { mutableStateOf<Pair<String, Rect>?>(null) }
    var drillRects by remember(slice) { mutableStateOf<Map<String, Rect>>(emptyMap()) }
    var drilledInfo by remember(slice) { mutableStateOf<Map<String, DrilledModule>>(emptyMap()) }
    var drillOutTo by remember(slice) { mutableStateOf<Int?>(null) }
    var drillOutFinal by remember(slice) { mutableStateOf<Int?>(null) }
    var drillOutFrom by remember(slice) { mutableStateOf(0) }
    var drillStepMillis by remember(slice) { mutableStateOf(DRILL_OUT_TOTAL_MS) }
    var drillingIn by remember(slice) { mutableStateOf(false) }
    fun requestDrillOut(target: Int) {
        if (drillingIn || drillOutTo != null) return
        val depth = overviewSelection.drillPath.size
        if (target < 0 || target >= depth) return
        val hops = depth - target
        drillStepMillis = (DRILL_OUT_TOTAL_MS / hops).coerceAtLeast(1)
        drillOutFrom = depth
        drillOutFinal = target
        drillOutTo = depth - 1
    }
    val drillOutEasing: Easing = when (val d = drillOutTo) {
        null -> FastOutSlowInEasing
        else -> {
            val first = d == drillOutFrom - 1
            val last = d == drillOutFinal
            when {
                first && last -> FastOutSlowInEasing
                first -> FastOutLinearInEasing
                last -> LinearOutSlowInEasing
                else -> LinearEasing
            }
        }
    }
    val drillScope = remember(overviewSelection.drillPath) {
        overviewSelection.drillPath.lastOrNull()?.let { FilePath.of(it) }
    }
    val moduleGraph = remember(slice, drillScope) { aggregateModules(slice, scopeRoot = drillScope) }
    val overviewLayout = remember(moduleGraph) { layeredModuleLayout(moduleGraph) }
    val drillingOutId = if (drillOutTo != null) overviewSelection.drillPath.lastOrNull() else null
    val parentDrillScope = remember(overviewSelection.drillPath, drillOutTo) {
        drillOutTo?.let { t -> overviewSelection.drillPath.getOrNull(t - 1)?.let { FilePath.of(it) } }
    }
    val parentModuleGraph = remember(slice, parentDrillScope, drillOutTo != null) {
        if (drillOutTo != null) aggregateModules(slice, scopeRoot = parentDrillScope) else null
    }
    val parentOverviewLayout = remember(parentModuleGraph) { parentModuleGraph?.let { layeredModuleLayout(it) } }
    val activeModuleId = remember(moduleGraph) {
        moduleGraph.nodes.firstOrNull { it.kind == NodeKind.ACTIVE }?.id
    }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchIndex by remember { mutableStateOf(-1) }
    val egoView = remember { EgoViewState() }
    var insightFocusOverride by remember(activeFileId) { mutableStateOf<String?>(null) }
    var relationsDefocused by remember(activeFileId) { mutableStateOf(false) }
    val relationsFocus = remember(slice, activeFileId, selectedId, relationsDefocused) {
        if (relationsDefocused) null
        else listOf(activeFileId, selectedId).firstOrNull { id -> id != null && slice.nodes.any { it.id == id } }
    }
    val searchSlice = when (viewTab) {
        AtlasViewTab.RELATIONS -> slice
        AtlasViewTab.ANALYSIS -> slice
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
                    viewTab == AtlasViewTab.RELATIONS && relationsFocus != null
                ) {
                    relationsDefocused = true
                    true
                } else if (event.type == KeyEventType.KeyDown && event.key == Key.Escape &&
                    viewTab == AtlasViewTab.RELATIONS &&
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
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "ATLAS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Box(modifier = Modifier.weight(1f))
            if (showExpand) HeaderAction("Expand", onClick = onExpand)
            if (showDock) HeaderAction("Dock", onClick = onDock)
            HeaderAction("Close", accent = true, onClick = onClose)
        }
        Divider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            ModeChip("Relations", viewTab == AtlasViewTab.RELATIONS) { onViewTabChange(AtlasViewTab.RELATIONS) }
            ModeChip("Analysis", viewTab == AtlasViewTab.ANALYSIS) { onViewTabChange(AtlasViewTab.ANALYSIS) }
            if (callsSlice.nodes.isNotEmpty() || viewTab == AtlasViewTab.CALLS) {
                ModeChip("Calls", viewTab == AtlasViewTab.CALLS) { onViewTabChange(AtlasViewTab.CALLS) }
            }
            Box(modifier = Modifier.weight(1f))
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
                        else -> "No source files"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (viewTab == AtlasViewTab.RELATIONS && relationsFocus != null) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                EgoCanvas(
                    slice = slice,
                    focusId = relationsFocus,
                    onNodeClick = onNodeClick,
                    view = egoView,
                )
            }
        } else if (viewTab == AtlasViewTab.RELATIONS) {
            var overviewBoxPos by remember { mutableStateOf(Offset.Zero) }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned { overviewBoxPos = it.positionInRoot() },
            ) {
                OverviewCanvas(
                    graph = moduleGraph,
                    layout = overviewLayout,
                    activeModuleId = activeModuleId,
                    followActive = followActive,
                    view = overviewView,
                    selection = overviewSelection,
                    onSelectionChange = { overviewSelection = it },
                    onOpenFile = onNodeClick,
                    onDrillFrom = { rect, drilled ->
                        drillFrom = drilled.node.id to rect
                        drillRects = drillRects + (drilled.node.id to rect)
                        drilledInfo = drilledInfo + (drilled.node.id to drilled)
                    },
                    drillingOutId = drillingOutId,
                    drillOutFromRect = drillingOutId?.let { drillRects[it] },
                    parentGraph = parentModuleGraph,
                    parentLayout = parentOverviewLayout,
                    onDrillingInChange = { drillingIn = it },
                    drillOutMillis = drillStepMillis,
                    drillOutEasing = drillOutEasing,
                )
                if (overviewSelection.drillPath.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.93f),
                                    RoundedCornerShape(8.dp),
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            OverviewBreadcrumb(
                                drillPath = overviewSelection.drillPath,
                                onNavigate = { depth -> requestDrillOut(depth) },
                            )
                        }
                        val lastIndex = overviewSelection.drillPath.lastIndex
                        overviewSelection.drillPath.forEachIndexed { i, id ->
                            val isDeepest = i == lastIndex
                            DrilledCard(
                                animKey = id,
                                drilled = drilledInfo[id],
                                fallbackLabel = crumbLabel(id),
                                flyFrom = if (isDeepest) drillRects[id] else null,
                                boxPos = overviewBoxPos,
                                exiting = drillOutTo != null && i >= drillOutTo!!,
                                exitToHole = drillOutTo == lastIndex,
                                exitMillis = drillStepMillis,
                                exitEasing = drillOutEasing,
                                onClick = { requestDrillOut(i) },
                                onExited = {
                                    if (drillOutTo == i) {
                                        overviewSelection = overviewSelection.drillUpTo(i)
                                        if (drillOutFinal != null && i > drillOutFinal!!) {
                                            drillOutTo = i - 1
                                        } else {
                                            drillOutTo = null
                                            drillOutFinal = null
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
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
        } else {
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
        }
    }
}

@Composable
private fun OverviewBreadcrumb(
    drillPath: List<String>,
    onNavigate: (Int) -> Unit,
) {
    Row(
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

private const val DRILL_OUT_TOTAL_MS = 700

@Composable
private fun DrilledCard(
    animKey: String,
    drilled: DrilledModule?,
    fallbackLabel: String,
    flyFrom: Rect?,
    boxPos: Offset,
    exiting: Boolean = false,
    exitToHole: Boolean = false,
    exitMillis: Int = DRILL_OUT_TOTAL_MS,
    exitEasing: Easing = FastOutSlowInEasing,
    onClick: () -> Unit = {},
    onExited: () -> Unit = {},
) {
    val roles = atlasRoleColors()
    val surface = MaterialTheme.colorScheme.surface
    val cardFill = MaterialTheme.colorScheme.surfaceVariant
    val outline = MaterialTheme.colorScheme.outline
    val onSurface = MaterialTheme.colorScheme.onSurface
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val measurer = rememberTextMeasurer(cacheSize = 64)
    val node = drilled?.node
    val title = node?.label?.substringAfterLast('/')?.ifEmpty { fallbackLabel } ?: fallbackLabel
    val fileCount = node?.fileCount ?: 0
    val isHub = drilled?.isHub == true
    val inCycle = drilled?.inCycle == true
    val usedBy = drilled?.usedBy ?: 0
    val uses = drilled?.uses ?: 0
    val aspect = (drilled?.aspect ?: 0.5f).coerceIn(0.35f, 1f)

    val enter = remember(animKey) { Animatable(0f) }
    var slotPos by remember { mutableStateOf(Offset.Zero) }
    var slotWidth by remember { mutableStateOf(0) }
    val fly = remember(animKey) { flyFrom }
    val ready = slotWidth > 0
    var arrived by remember(animKey) { mutableStateOf(fly == null) }
    LaunchedEffect(animKey, fly != null, ready, exiting) {
        if (exiting) {
            enter.animateTo(0f, tween(exitMillis, easing = exitEasing))
            onExited()
        } else if (fly == null || ready) {
            enter.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
            arrived = true
        }
    }

    val cardWidth = 132.dp
    val cardHeight = cardWidth * aspect

    Box(
        modifier = Modifier
            .onGloballyPositioned {
                slotPos = it.positionInRoot()
                slotWidth = it.size.width
            }
            .graphicsLayer {
                val p = enter.value
                when {
                    exiting && !exitToHole -> {
                        alpha = p
                        translationX = (1f - p) * 8.dp.toPx()
                        translationY = (1f - p) * 8.dp.toPx()
                    }
                    fly != null && ready -> {
                        transformOrigin = TransformOrigin(0f, 0f)
                        translationX = (boxPos.x + fly.left - slotPos.x) * (1f - p)
                        translationY = (boxPos.y + fly.top - slotPos.y) * (1f - p)
                        val startScale = fly.width / slotWidth
                        val sc = startScale + (1f - startScale) * p
                        scaleX = sc
                        scaleY = sc
                        alpha = 1f
                    }
                    fly != null -> alpha = 0f
                    else -> {
                        alpha = p
                        translationX = (1f - p) * 28.dp.toPx()
                        translationY = (1f - p) * 14.dp.toPx()
                    }
                }
            }
            .size(cardWidth, cardHeight)
            .clickable(enabled = arrived && !exiting) { onClick() },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawOverviewCard(
                measurer = measurer,
                roles = roles,
                surface = surface,
                cardFill = cardFill,
                outline = outline,
                onSurface = onSurface,
                labelColor = labelColor,
                primary = primary,
                left = 0f,
                top = 0f,
                w = size.width,
                h = size.height,
                s = size.width / 132f,
                title = title,
                external = false,
                isHub = isHub,
                inCycle = inCycle,
                selected = false,
                pathEnd = false,
                files = fileCount,
                usedBy = usedBy,
                uses = uses,
                showStats = false,
                alpha = 1f,
                dim = false,
            )
        }
    }
}

@Composable
private fun HeaderAction(label: String, accent: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val tint = if (accent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (hovered) tint.copy(alpha = 0.12f) else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (hovered) tint else tint.copy(alpha = 0.78f),
        )
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg = when {
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        hovered -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
        else -> Color.Transparent
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(bg)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
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

