package page.atlas.render

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import page.atlas.graph.ModuleGraph
import page.atlas.graph.ModuleLayer
import page.atlas.graph.ModuleNode
import page.atlas.graph.NodeKind
import page.atlas.graph.modulePath
import page.atlas.interaction.OverviewSelection

private const val CARD_W = 132f
private const val EXTERNAL_W = 156f
private const val CARD_MIN_H = 52f
private const val CARD_MAX_H = 128f
private const val EXTERNAL_H = 50f
private const val CARD_GAP = 14f
private const val MAX_ROWS_PER_COL = 6
private const val SUBCOL_GAP = 16f
private const val LAYER_GAP = 44f
private const val MODULE_CAP = 24
private const val HUB_MIN_DEPENDENTS = 4
private const val BAND_TOP = -40f
private const val BAND_LABEL_Y = -34f
private const val BAND_DIVIDER_Y = -14f
private const val DRILL_TRANSITION_MS = 700

internal data class CardBox(val x: Float, val y: Float, val w: Float, val h: Float) {
    val centerX get() = x + w / 2f
    val centerY get() = y + h / 2f
    val right get() = x + w
    val bottom get() = y + h
}

internal data class OverviewBand(val layer: ModuleLayer, val left: Float, val width: Float) {
    val centerX get() = left + width / 2f
    val right get() = left + width
}

internal data class OverviewCardScene(
    val boxes: Map<String, CardBox>,
    val bands: List<OverviewBand>,
    val visible: Set<String>,
    val hiddenCount: Int,
    val width: Float,
    val top: Float,
    val bottom: Float,
)

private data class DrillTransition(
    val oldScene: OverviewCardScene,
    val oldGraph: ModuleGraph,
    val drilledId: String,
    val capBase: Offset,
    val capScale: Float,
    val capIndeg: Map<String, Int>,
    val capOutdeg: Map<String, Int>,
    val capCycNodes: Set<String>,
    val capCycKeys: Set<Pair<String, String>>,
)

internal data class DrilledModule(
    val node: ModuleNode,
    val isHub: Boolean,
    val inCycle: Boolean,
    val usedBy: Int,
    val uses: Int,
    val aspect: Float,
)

private fun cardHeight(fileCount: Int, maxFiles: Int): Float {
    val rel = sqrt((fileCount.toFloat() / maxFiles).coerceIn(0f, 1f))
    return CARD_MIN_H + rel * (CARD_MAX_H - CARD_MIN_H)
}

internal fun buildOverviewCards(graph: ModuleGraph, layout: ModuleLayerLayout): OverviewCardScene {
    if (graph.nodes.isEmpty()) {
        return OverviewCardScene(emptyMap(), emptyList(), emptySet(), 0, 0f, 0f, 0f)
    }
    val maxFiles = graph.nodes.maxOf { it.fileCount }.coerceAtLeast(1)
    val indeg = HashMap<String, Int>()
    for (e in graph.edges) if (e.from != e.to) indeg.merge(e.to, 1, Int::plus)
    val ranked = graph.nodes.sortedWith(
        compareByDescending<ModuleNode> { it.fileCount + (indeg[it.id] ?: 0) * 2 }.thenBy { it.id },
    )
    val visibleNodes = if (graph.nodes.size <= MODULE_CAP) graph.nodes else ranked.take(MODULE_CAP)
    val visibleSet = visibleNodes.mapTo(HashSet()) { it.id }

    val boxes = HashMap<String, CardBox>()
    val bands = ArrayList<OverviewBand>()
    var maxRight = 0f
    var maxBottom = 0f
    var layerLeft = 0f
    for (layer in layout.columns) {
        val inLayer = visibleNodes
            .filter { layout.layerOf[it.id] == layer }
            .sortedBy { layout.positions[it.id]?.y ?: 0.0 }
        if (inLayer.isEmpty()) continue
        val cardW = inLayer.maxOf { if (it.external) EXTERNAL_W else CARD_W }
        val cols = ((inLayer.size + MAX_ROWS_PER_COL - 1) / MAX_ROWS_PER_COL).coerceAtLeast(1)
        val rows = (inLayer.size + cols - 1) / cols
        val subStep = cardW + SUBCOL_GAP
        val colTop = FloatArray(cols)
        inLayer.forEachIndexed { i, n ->
            val c = i / rows
            val w = if (n.external) EXTERNAL_W else CARD_W
            val h = if (n.external) EXTERNAL_H else cardHeight(n.fileCount, maxFiles)
            val box = CardBox(layerLeft + c * subStep, colTop[c], w, h)
            boxes[n.id] = box
            colTop[c] = box.bottom + CARD_GAP
            maxRight = maxOf(maxRight, box.right)
            maxBottom = maxOf(maxBottom, box.bottom)
        }
        val layerWidth = (cols - 1) * subStep + cardW
        bands.add(OverviewBand(layer, layerLeft, layerWidth))
        layerLeft += layerWidth + LAYER_GAP
    }
    return OverviewCardScene(
        boxes = boxes,
        bands = bands,
        visible = visibleSet,
        hiddenCount = graph.nodes.size - visibleNodes.size,
        width = maxRight,
        top = BAND_TOP,
        bottom = maxBottom,
    )
}

private fun layerLabel(layer: ModuleLayer): String = when (layer) {
    ModuleLayer.ENTRY -> "ENTRY"
    ModuleLayer.FEATURES -> "FEATURES"
    ModuleLayer.CORE -> "CORE"
    ModuleLayer.PLATFORM -> "PLATFORM"
    ModuleLayer.EXTERNAL -> "EXTERNAL"
}

@Composable
internal fun OverviewCanvas(
    graph: ModuleGraph,
    layout: ModuleLayerLayout,
    activeModuleId: String?,
    followActive: Boolean,
    view: MapViewState,
    selection: OverviewSelection,
    onSelectionChange: (OverviewSelection) -> Unit,
    onOpenFile: (java.nio.file.Path) -> Unit,
    onDrillFrom: (Rect, DrilledModule) -> Unit = { _, _ -> },
    drillingOutId: String? = null,
    drillOutFromRect: Rect? = null,
    parentGraph: ModuleGraph? = null,
    parentLayout: ModuleLayerLayout? = null,
    onDrillingInChange: (Boolean) -> Unit = {},
    drillOutMillis: Int = DRILL_TRANSITION_MS,
    drillOutEasing: Easing = FastOutSlowInEasing,
) {
    val textMeasurer = rememberTextMeasurer(cacheSize = 256)
    val surface = MaterialTheme.colorScheme.surface
    val cardFill = MaterialTheme.colorScheme.surfaceVariant
    val outline = MaterialTheme.colorScheme.outline
    val onSurface = MaterialTheme.colorScheme.onSurface
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val roles = atlasRoleColors()
    val bodyStyle = TextStyle(fontSize = 10.sp, color = labelColor)
    val bandStyle = TextStyle(fontSize = 9.sp, color = labelColor)

    val scene = remember(graph, layout) { buildOverviewCards(graph, layout) }
    val degree = remember(graph) {
        val inn = HashMap<String, Int>()
        val out = HashMap<String, Int>()
        for (e in graph.edges) {
            if (e.from == e.to) continue
            inn.merge(e.to, 1, Int::plus)
            out.merge(e.from, 1, Int::plus)
        }
        inn to out
    }
    val indeg = degree.first
    val outdeg = degree.second
    val cycleKeys = remember(graph) {
        mapCycleEdges(graph.edges.map { MapEdge(it.from, it.to, it.weight) })
    }
    val cycleNodes = remember(cycleKeys) {
        cycleKeys.flatMapTo(HashSet()) { listOf(it.first, it.second) }
    }
    val adjacency = remember(graph) {
        val map = HashMap<String, MutableSet<String>>()
        for (e in graph.edges) {
            map.getOrPut(e.from) { mutableSetOf() }.add(e.to)
            map.getOrPut(e.to) { mutableSetOf() }.add(e.from)
        }
        map
    }
    val nodeById = remember(graph) { graph.nodes.associateBy { it.id } }

    val parentScene = remember(parentGraph, parentLayout) {
        if (parentGraph != null && parentLayout != null) buildOverviewCards(parentGraph, parentLayout) else null
    }
    val parentIndeg = remember(parentGraph) {
        val inn = HashMap<String, Int>()
        parentGraph?.edges?.forEach { if (it.from != it.to) inn.merge(it.to, 1, Int::plus) }
        inn
    }
    val parentOutdeg = remember(parentGraph) {
        val out = HashMap<String, Int>()
        parentGraph?.edges?.forEach { if (it.from != it.to) out.merge(it.from, 1, Int::plus) }
        out
    }
    val parentCycleKeys = remember(parentGraph) {
        if (parentGraph != null) mapCycleEdges(parentGraph.edges.map { MapEdge(it.from, it.to, it.weight) }) else emptySet()
    }
    val parentCycleNodes = remember(parentCycleKeys) {
        parentCycleKeys.flatMapTo(HashSet()) { listOf(it.first, it.second) }
    }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var hoverId by remember(graph) { mutableStateOf<String?>(null) }
    val selectedId = selection.moduleId?.takeIf { selection.kind == OverviewSelection.Kind.MODULE }
    val pathIds = remember(graph, selection.kind, selection.moduleId, selection.pathTarget) {
        if (selection.kind == OverviewSelection.Kind.PATH && selection.moduleId != null && selection.pathTarget != null) {
            modulePath(graph, selection.moduleId, selection.pathTarget)
        } else {
            null
        }
    }
    val pathNodeSet = pathIds?.toSet()
    val pathEdgeKeys = pathIds?.zipWithNext()?.toSet()
    var pan by view::pan
    var scale by view::scale
    var fitted by view::fitted

    fun fitTransformFor(s: OverviewCardScene): Pair<Offset, Float> {
        val cw = canvasSize.width.toFloat()
        val ch = canvasSize.height.toFloat()
        if (cw <= 0f || ch <= 0f || s.boxes.isEmpty()) return Offset.Zero to 1f
        val w = s.width + 160f
        val h = (s.bottom - s.top) + 160f
        val fit = if (w <= 0f || h <= 0f) 1f else min(cw / w, ch / h).coerceIn(0.1f, 1.6f)
        val cx = s.width / 2f
        val cy = (s.top + s.bottom) / 2f
        return Offset(cw / 2f - cx * fit, ch / 2f - cy * fit) to fit
    }

    fun fitTransform(): Pair<Offset, Float> = fitTransformFor(scene)

    fun outTransformFor(s: OverviewCardScene): Pair<Offset, Float> {
        val rect = drillOutFromRect
        val box = drillingOutId?.let { s.boxes[it] }
        if (rect != null && box != null && box.w > 0f) {
            val sc = rect.width / box.w
            return Offset(rect.left - box.x * sc, rect.top - box.y * sc) to sc
        }
        return fitTransformFor(s)
    }

    fun scopeKey(): String = selection.drillPath.joinToString(" ")

    fun saveScopeView() {
        if (scale > 0f) view.savedViews[scopeKey()] = pan to scale
    }

    fun savedOrFit(): Pair<Offset, Float> = view.savedViews[scopeKey()] ?: fitTransform()

    fun viewTransform(): Pair<Offset, Float> = if (scale > 0f) pan to scale else fitTransform()

    fun centerOf(id: String): Offset? = scene.boxes[id]?.let { Offset(it.centerX, it.centerY) }

    val anim = remember { Animatable(0f) }
    var transition by remember { mutableStateOf<DrillTransition?>(null) }
    val transitioning = transition != null
    val outAnim = remember { Animatable(0f) }

    LaunchedEffect(transitioning) { onDrillingInChange(transitioning) }

    LaunchedEffect(scene, canvasSize, fitted, transitioning) {
        if (transitioning || drillingOutId != null) return@LaunchedEffect
        if (canvasSize.width <= 0 || scene.boxes.isEmpty()) return@LaunchedEffect
        if (fitted) return@LaunchedEffect
        val (p, s) = fitTransform()
        pan = p
        scale = s
        fitted = true
    }

    var prevDrillDepth by remember { mutableStateOf(selection.drillPath.size) }
    LaunchedEffect(selection.drillPath.size) {
        val depth = selection.drillPath.size
        if (depth > prevDrillDepth && transition != null) {
            try {
                anim.snapTo(0f)
                anim.animateTo(1f, tween(DRILL_TRANSITION_MS, easing = FastOutSlowInEasing))
                val (fp, fs) = savedOrFit()
                pan = fp
                scale = fs
                fitted = true
            } finally {
                transition = null
                withContext(NonCancellable) { anim.snapTo(0f) }
            }
        } else if (depth != prevDrillDepth) {
            transition = null
            val (fp, fs) = savedOrFit()
            pan = fp
            scale = fs
            fitted = true
        }
        prevDrillDepth = depth
    }

    LaunchedEffect(drillingOutId, parentScene != null, canvasSize) {
        if (drillingOutId != null && parentScene != null && canvasSize.width > 0) {
            saveScopeView()
            val (pp, ps) = outTransformFor(parentScene)
            pan = pp
            scale = ps
            fitted = true
            outAnim.snapTo(0f)
            outAnim.animateTo(1f, tween(drillOutMillis, easing = drillOutEasing))
        } else {
            outAnim.snapTo(0f)
        }
    }

    LaunchedEffect(activeModuleId, followActive, canvasSize) {
        if (transition != null || drillingOutId != null) return@LaunchedEffect
        if (!followActive || canvasSize.width <= 0) return@LaunchedEffect
        val id = activeModuleId ?: return@LaunchedEffect
        val world = centerOf(id) ?: return@LaunchedEffect
        val hood = (adjacency[id].orEmpty() + id).mapNotNull { centerOf(it) }
        var minX = world.x
        var maxX = world.x
        var minY = world.y
        var maxY = world.y
        for (p in hood) {
            minX = minOf(minX, p.x)
            maxX = maxOf(maxX, p.x)
            minY = minOf(minY, p.y)
            maxY = maxOf(maxY, p.y)
        }
        val cw = canvasSize.width.toFloat()
        val ch = canvasSize.height.toFloat()
        val w = (maxX - minX) + 360f
        val h = (maxY - minY) + 360f
        val s = min(cw / w, ch / h).coerceIn(0.45f, 1.8f)
        scale = s
        pan = Offset(cw / 2f - world.x * s, ch / 2f - world.y * s)
        fitted = true
    }

    fun nodeAt(pos: Offset): String? {
        if (transition != null || drillingOutId != null) return null
        val (base, s) = viewTransform()
        if (s <= 0f) return null
        for (node in graph.nodes) {
            if (node.id !in scene.visible) continue
            val b = scene.boxes[node.id] ?: continue
            val left = base.x + b.x * s
            val top = base.y + b.y * s
            val right = left + b.w * s
            val bottom = top + b.h * s
            if (pos.x in left..right && pos.y in top..bottom) return node.id
        }
        return null
    }

    fun drawLayer(
        scope: DrawScope,
        layerScene: OverviewCardScene,
        layerGraph: ModuleGraph,
        base: Offset,
        s: Float,
        alpha: Float,
        offset: Offset,
        drawBands: Boolean,
        exclude: String?,
        inDeg: Map<String, Int>,
        outDeg: Map<String, Int>,
        cycNodes: Set<String>,
        cycKeys: Set<Pair<String, String>>,
    ) = with(scope) {
        if (alpha <= 0.01f || s <= 0f) return@with
        fun lx(v: Float) = base.x + v * s + offset.x
        fun ly(v: Float) = base.y + v * s + offset.y
        if (drawBands) {
            for (band in layerScene.bands) {
                val measured = textMeasurer.measure(AnnotatedString(layerLabel(band.layer)), bandStyle)
                drawText(
                    textLayoutResult = measured,
                    color = labelColor.copy(alpha = 0.65f * alpha),
                    topLeft = Offset(lx(band.centerX) - measured.size.width / 2f, ly(BAND_LABEL_Y)),
                )
            }
            if (layerScene.bands.isNotEmpty()) {
                drawLine(
                    color = outline.copy(alpha = 0.5f * alpha),
                    start = Offset(lx(layerScene.bands.first().left), ly(BAND_DIVIDER_Y)),
                    end = Offset(lx(layerScene.bands.last().right), ly(BAND_DIVIDER_Y)),
                    strokeWidth = 1f,
                )
            }
        }
        val curvePath = Path()
        val headPath = Path()
        for (edge in layerGraph.edges) {
            if (edge.from !in layerScene.visible || edge.to !in layerScene.visible) continue
            if (exclude != null && (edge.from == exclude || edge.to == exclude)) continue
            val sb = layerScene.boxes[edge.from] ?: continue
            val tb = layerScene.boxes[edge.to] ?: continue
            val inCyc = (edge.from to edge.to) in cycKeys
            val color = if (inCyc) roles.cycle.copy(alpha = 0.55f * alpha) else roles.dependency.copy(alpha = 0.22f * alpha)
            val stroke = (0.8f + ln(edge.weight.toFloat()) * 0.6f).coerceAtMost(4f)
            val leftToRight = tb.centerX >= sb.centerX
            val from = Offset(lx(if (leftToRight) sb.right else sb.x), ly(sb.centerY))
            val to = Offset(lx(if (leftToRight) tb.x else tb.right), ly(tb.centerY))
            drawCardEdge(from, to, color, stroke, inCyc, curvePath, headPath)
        }
        for (node in layerGraph.nodes) {
            if (node.id !in layerScene.visible) continue
            if (exclude != null && node.id == exclude) continue
            val b = layerScene.boxes[node.id] ?: continue
            drawOverviewCard(
                measurer = textMeasurer,
                roles = roles,
                surface = surface,
                cardFill = cardFill,
                outline = outline,
                onSurface = onSurface,
                labelColor = labelColor,
                primary = primary,
                left = lx(b.x),
                top = ly(b.y),
                w = b.w * s,
                h = b.h * s,
                s = s,
                title = node.label.substringAfterLast('/'),
                external = node.external,
                isHub = (inDeg[node.id] ?: 0) >= HUB_MIN_DEPENDENTS,
                inCycle = node.id in cycNodes,
                selected = false,
                pathEnd = false,
                files = node.fileCount,
                usedBy = inDeg[node.id] ?: 0,
                uses = outDeg[node.id] ?: 0,
                showStats = false,
                alpha = alpha,
                dim = false,
            )
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
                    if (scale <= 0f) {
                        val (p, s) = fitTransform()
                        pan = p
                        scale = s
                    }
                    pan += dragAmount
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Scroll -> {
                                val change = event.changes.firstOrNull()
                                val delta = change?.scrollDelta?.y ?: 0f
                                if (delta != 0f) {
                                    val (curPan, curScale) = viewTransform()
                                    val next = (curScale * if (delta > 0f) 0.9f else 1.1f).coerceIn(0.1f, 4f)
                                    pan = change!!.position - (change.position - curPan) * (next / curScale)
                                    scale = next
                                }
                            }
                            PointerEventType.Move -> {
                                hoverId = event.changes.firstOrNull()?.position?.let { nodeAt(it) }
                            }
                            PointerEventType.Exit -> hoverId = null
                        }
                    }
                }
            }
            .pointerInput(graph) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val shift = down.let { currentEvent.keyboardModifiers.isShiftPressed }
                    val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                    val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                        awaitFirstDown(requireUnconsumed = false)
                    }
                    if (second == null) {
                        val id = nodeAt(up.position)
                        val source = selection.moduleId
                        onSelectionChange(
                            when {
                                id == null -> selection.clear()
                                shift && source != null && id != source ->
                                    if (selection.kind == OverviewSelection.Kind.PATH && id == selection.pathTarget) {
                                        selection.selectModule(id).tracePath(source)
                                    } else {
                                        selection.tracePath(id)
                                    }
                                else -> selection.selectModule(id)
                            },
                        )
                    } else {
                        val up2 = waitForUpOrCancellation()
                        val pos = up2?.position ?: second.position
                        val id = nodeAt(pos) ?: return@awaitEachGesture
                        val node = nodeById[id] ?: return@awaitEachGesture
                        if (node.splittable) {
                            val box = scene.boxes[id]
                            if (box != null) {
                                val (capBase, capScale) = viewTransform()
                                onDrillFrom(
                                    Rect(
                                        capBase.x + box.x * capScale,
                                        capBase.y + box.y * capScale,
                                        capBase.x + box.right * capScale,
                                        capBase.y + box.bottom * capScale,
                                    ),
                                    DrilledModule(
                                        node = node,
                                        isHub = (indeg[id] ?: 0) >= HUB_MIN_DEPENDENTS,
                                        inCycle = id in cycleNodes,
                                        usedBy = indeg[id] ?: 0,
                                        uses = outdeg[id] ?: 0,
                                        aspect = box.h / box.w,
                                    ),
                                )
                                transition = DrillTransition(scene, graph, id, capBase, capScale, indeg, outdeg, cycleNodes, cycleKeys)
                                saveScopeView()
                                onSelectionChange(selection.drillInto(id))
                            }
                        } else if (node.files.size == 1) {
                            onOpenFile(node.files.first().path)
                        } else {
                            onSelectionChange(selection.selectModule(id))
                        }
                    }
                }
            },
    ) {
        val activeTransition = transition
        if (activeTransition != null) {
            val p = anim.value
            val aA = smoothstep(p, 0f, 0.40f)
            val aC = smoothstep(p, 0.35f, 0.85f)

            if (scene.boxes.isNotEmpty()) {
                val (childBase, childScale) = savedOrFit()
                val enterX = maxOf(120f, size.width * 0.18f) * (1f - aC)
                val enterY = maxOf(160f, size.height * 0.22f) * (1f - aC)
                drawLayer(this, scene, graph, childBase, childScale, aC, Offset(enterX, enterY), false, null, indeg, outdeg, cycleNodes, cycleKeys)
            }
            drawLayer(
                this, activeTransition.oldScene, activeTransition.oldGraph,
                activeTransition.capBase, activeTransition.capScale,
                1f - aA, Offset.Zero, true, activeTransition.drilledId,
                activeTransition.capIndeg, activeTransition.capOutdeg, activeTransition.capCycNodes, activeTransition.capCycKeys,
            )

            val legendStyle = bodyStyle.copy(fontSize = 9.sp, color = labelColor.copy(alpha = 0.6f))
            val legend = "columns = dependency depth · size = files · red = hub · amber = cycle"
            val legendY = if (scene.hiddenCount > 0) size.height - 38f else size.height - 22f
            drawText(textMeasurer.measure(AnnotatedString(legend), legendStyle), topLeft = Offset(14f, legendY))
            if (scene.hiddenCount > 0) {
                val footer = "Showing largest ${scene.visible.size} modules · ${scene.hiddenCount} smaller hidden"
                drawText(textMeasurer.measure(AnnotatedString(footer), legendStyle), topLeft = Offset(14f, size.height - 22f))
            }
            return@Canvas
        }

        val outId = drillingOutId
        if (outId != null && parentScene != null && parentGraph != null && parentScene.boxes.isNotEmpty()) {
            val p = outAnim.value
            val q = 1f - p
            val aA = smoothstep(q, 0f, 0.40f)
            val aC = smoothstep(q, 0.35f, 0.85f)
            if (scene.boxes.isNotEmpty()) {
                val (childBase, childScale) = fitTransform()
                val exitX = maxOf(120f, size.width * 0.18f) * (1f - aC)
                val exitY = maxOf(160f, size.height * 0.22f) * (1f - aC)
                drawLayer(this, scene, graph, childBase, childScale, aC, Offset(exitX, exitY), false, null, indeg, outdeg, cycleNodes, cycleKeys)
            }
            val (pBase, pScale) = outTransformFor(parentScene)
            drawLayer(
                this, parentScene, parentGraph, pBase, pScale,
                1f - aA, Offset.Zero, true, outId,
                parentIndeg, parentOutdeg, parentCycleNodes, parentCycleKeys,
            )
            return@Canvas
        }

        if (scene.boxes.isEmpty()) return@Canvas
        val (fitBase, fitScale) = viewTransform()
        if (fitScale <= 0f) return@Canvas
        val base = fitBase
        val s = fitScale
        val onPath = pathNodeSet != null
        val focusId = if (onPath) null else selectedId ?: hoverId ?: activeModuleId?.takeIf { followActive }
        val highlighted = if (onPath) pathNodeSet else focusId?.let { adjacency[it].orEmpty() + it }

        for (band in scene.bands) {
            val measured = textMeasurer.measure(AnnotatedString(layerLabel(band.layer)), bandStyle)
            val cx = base.x + band.centerX * s
            val cy = base.y + BAND_LABEL_Y * s
            drawText(
                textLayoutResult = measured,
                color = labelColor.copy(alpha = 0.65f),
                topLeft = Offset(cx - measured.size.width / 2f, cy),
            )
        }
        if (scene.bands.isNotEmpty()) {
            val firstX = base.x + scene.bands.first().left * s
            val lastX = base.x + scene.bands.last().right * s
            val dy = base.y + BAND_DIVIDER_Y * s
            drawLine(
                color = outline.copy(alpha = 0.5f),
                start = Offset(firstX, dy),
                end = Offset(lastX, dy),
                strokeWidth = 1f,
            )
        }

        fun sx(v: Float) = base.x + v * s
        fun sy(v: Float) = base.y + v * s

        val curvePath = Path()
        val headPath = Path()
        for (edge in graph.edges) {
            if (edge.from !in scene.visible || edge.to !in scene.visible) continue
            val sb = scene.boxes[edge.from] ?: continue
            val tb = scene.boxes[edge.to] ?: continue
            val onPathEdge = pathEdgeKeys != null && (edge.from to edge.to) in pathEdgeKeys
            val out = selectedId != null && edge.from == selectedId
            val incoming = selectedId != null && edge.to == selectedId
            val touchesFocus = focusId != null && (edge.from == focusId || edge.to == focusId)
            val inCycle = (edge.from to edge.to) in cycleKeys
            val color = when {
                pathEdgeKeys != null -> if (onPathEdge) roles.path.copy(alpha = 0.95f) else labelColor.copy(alpha = 0.04f)
                out -> roles.dependency.copy(alpha = 0.9f)
                incoming -> roles.usedBy.copy(alpha = 0.9f)
                touchesFocus -> roles.dependency.copy(alpha = 0.85f)
                focusId != null -> labelColor.copy(alpha = 0.05f)
                inCycle -> roles.cycle.copy(alpha = 0.55f)
                else -> roles.dependency.copy(alpha = 0.22f)
            }
            val drawHead = onPathEdge || out || incoming || touchesFocus || (inCycle && focusId == null && !onPath)
            val weightStroke = (0.8f + ln(edge.weight.toFloat()) * 0.6f).coerceAtMost(4f)
            val stroke = when {
                onPathEdge -> weightStroke + 1.2f
                touchesFocus -> weightStroke + 0.8f
                else -> weightStroke
            }
            val leftToRight = tb.centerX >= sb.centerX
            val from = Offset(sx(if (leftToRight) sb.right else sb.x), sy(sb.centerY))
            val to = Offset(sx(if (leftToRight) tb.x else tb.right), sy(tb.centerY))
            drawCardEdge(from, to, color, stroke, drawHead, curvePath, headPath)
        }

        for (node in graph.nodes) {
            if (node.id !in scene.visible) continue
            val b = scene.boxes[node.id] ?: continue
            val dimmed = highlighted != null && node.id !in highlighted
            val active = node.kind == NodeKind.ACTIVE
            val selected = node.id == selectedId
            val isHub = (indeg[node.id] ?: 0) >= HUB_MIN_DEPENDENTS
            val inCycle = node.id in cycleNodes
            val pathEnd = pathIds != null && (node.id == pathIds.first() || node.id == pathIds.last())
            drawOverviewCard(
                measurer = textMeasurer,
                roles = roles,
                surface = surface,
                cardFill = cardFill,
                outline = outline,
                onSurface = onSurface,
                labelColor = labelColor,
                primary = primary,
                left = base.x + b.x * s,
                top = base.y + b.y * s,
                w = b.w * s,
                h = b.h * s,
                s = s,
                title = node.label.substringAfterLast('/'),
                external = node.external,
                isHub = isHub,
                inCycle = inCycle,
                selected = selected || active,
                pathEnd = pathEnd,
                files = node.fileCount,
                usedBy = indeg[node.id] ?: 0,
                uses = outdeg[node.id] ?: 0,
                showStats = selected || node.id == focusId,
                alpha = 1f,
                dim = dimmed,
            )
        }

        val legend = if (onPath) {
            val hops = (pathIds?.size ?: 1) - 1
            "amber = dependency path · $hops hop${if (hops == 1) "" else "s"}"
        } else if (selectedId != null) {
            "blue = uses · teal = used by · red = hub · amber = cycle"
        } else {
            "columns = dependency depth · size = files · red = hub · amber = cycle"
        }
        val legendStyle = bodyStyle.copy(fontSize = 9.sp, color = labelColor.copy(alpha = 0.6f))
        val legendY = when {
            selection.drillPath.isEmpty() -> 12f
            scene.hiddenCount > 0 -> size.height - 38f
            else -> size.height - 22f
        }
        drawText(textMeasurer.measure(AnnotatedString(legend), legendStyle), topLeft = Offset(14f, legendY))

        if (scene.hiddenCount > 0) {
            val footer = "Showing largest ${scene.visible.size} modules · ${scene.hiddenCount} smaller hidden"
            val footerLayout = textMeasurer.measure(AnnotatedString(footer), legendStyle)
            drawText(footerLayout, topLeft = Offset(14f, size.height - 22f))
        }
    }
}

private fun smoothstep(p: Float, a: Float, b: Float): Float =
    ((p - a) / (b - a)).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }

internal fun DrawScope.drawOverviewCard(
    measurer: TextMeasurer,
    roles: AtlasRoleColors,
    surface: Color,
    cardFill: Color,
    outline: Color,
    onSurface: Color,
    labelColor: Color,
    primary: Color,
    left: Float,
    top: Float,
    w: Float,
    h: Float,
    s: Float,
    title: String,
    external: Boolean,
    isHub: Boolean,
    inCycle: Boolean,
    selected: Boolean,
    pathEnd: Boolean,
    files: Int,
    usedBy: Int,
    uses: Int,
    showStats: Boolean,
    alpha: Float,
    dim: Boolean,
) {
    val a = alpha * (if (dim) 0.4f else 1f)
    if (a <= 0.01f || s <= 0f) return
    val k = s
    val corner = CornerRadius(8f * k)
    val topLeft = Offset(left, top)
    val sz = Size(w, h)

    if (!external) {
        drawRoundRect(color = surface.copy(alpha = a), topLeft = topLeft, size = sz, cornerRadius = corner)
        val fillBase = if (selected) lerp(cardFill, primary, 0.12f) else cardFill
        val brush = Brush.verticalGradient(
            colors = listOf(lerp(fillBase, Color.White, 0.06f), lerp(fillBase, Color.Black, 0.04f)),
            startY = top,
            endY = top + h,
        )
        drawRoundRect(brush = brush, alpha = a, topLeft = topLeft, size = sz, cornerRadius = corner)
    }

    val borderColor: Color
    val borderW: Float
    val dashed: Boolean
    when {
        external -> { borderColor = roles.neutral.copy(alpha = 0.6f * a); borderW = 1f * k; dashed = true }
        selected -> { borderColor = primary.copy(alpha = a); borderW = 1.6f * k; dashed = false }
        pathEnd -> { borderColor = roles.path.copy(alpha = 0.7f * a); borderW = 1.4f * k; dashed = false }
        isHub -> { borderColor = roles.hub.copy(alpha = 0.55f * a); borderW = 1.3f * k; dashed = false }
        inCycle -> { borderColor = roles.cycle.copy(alpha = 0.7f * a); borderW = 1.3f * k; dashed = false }
        else -> { borderColor = outline.copy(alpha = a); borderW = 1.2f * k; dashed = false }
    }
    val inset = borderW / 2f
    val borderStyle = if (dashed) {
        Stroke(width = borderW, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * k, 3f * k)))
    } else {
        Stroke(width = borderW)
    }
    drawRoundRect(
        color = borderColor,
        topLeft = Offset(left + inset, top + inset),
        size = Size(w - borderW, h - borderW),
        cornerRadius = CornerRadius((8f * k - inset).coerceAtLeast(2f * k)),
        style = borderStyle,
    )

    if (!external) {
        val gx = left + w - 15f * k
        val gy = top + 15f * k
        val gr = 4.4f * k
        when {
            isHub -> drawCircle(color = roles.hub.copy(alpha = a), radius = gr, center = Offset(gx, gy))
            inCycle -> drawCircle(color = roles.cycle.copy(alpha = a), radius = gr, center = Offset(gx, gy), style = Stroke(width = 1.8f * k))
            pathEnd -> drawLine(
                color = roles.path.copy(alpha = a),
                start = Offset(gx - 4.5f * k, gy),
                end = Offset(gx + 4.5f * k, gy),
                strokeWidth = 2.4f * k,
                cap = StrokeCap.Round,
            )
        }
    }

    if (w < 56f) return

    val fontK = s / density
    val titleStyle = TextStyle(fontSize = (12f * fontK).sp, fontWeight = FontWeight.W600, color = onSurface)
    val bodyStyle = TextStyle(fontSize = (9f * fontK).sp, color = labelColor)
    val titleMax = (w - 39f * k).coerceAtLeast(1f)
    val titleLayout = measurer.measure(
        text = AnnotatedString(title),
        style = titleStyle,
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        constraints = Constraints(maxWidth = titleMax.toInt()),
    )
    drawText(titleLayout, color = onSurface.copy(alpha = a), topLeft = Offset(left + 13f * k, top + 7f * k))

    if (!external) {
        val bandTop = top + 31f * k
        val bandBot = top + h - 31f * k
        if (bandBot - bandTop > 13f * k) {
            val trackW = w - 24f * k
            val bh = 3.6f * k
            val gap = 6.5f * k
            val barX = left + 13f * k
            val cy = (bandTop + bandBot) / 2f
            val trackColor = labelColor.copy(alpha = 0.16f * a)
            fun bar(y: Float, ratio: Float, color: Color) {
                drawRoundRect(color = trackColor, topLeft = Offset(barX, y), size = Size(trackW, bh), cornerRadius = CornerRadius(bh / 2f))
                if (ratio > 0f) {
                    val len = (trackW * ratio).coerceAtLeast(bh)
                    drawRoundRect(color = color.copy(alpha = a), topLeft = Offset(barX, y), size = Size(len, bh), cornerRadius = CornerRadius(bh / 2f))
                }
            }
            bar(cy - bh - gap / 2f, min(1f, usedBy / 12f), roles.usedBy)
            bar(cy + gap / 2f, min(1f, uses / 12f), primary)
        }
    }

    val faint = labelColor.copy(alpha = 0.7f * a)
    fun drawSegs(segs: List<Pair<String, Color>>, baseY: Float) {
        var x = left + 13f * k
        for ((text, color) in segs) {
            val l = measurer.measure(AnnotatedString(text), bodyStyle)
            drawText(l, color = color, topLeft = Offset(x, baseY))
            x += l.size.width
        }
    }
    val line1Y = top + h - 16f * k
    when {
        external -> drawSegs(listOf("external" to roles.neutral.copy(alpha = a)), line1Y)
        showStats -> {
            drawSegs(listOf("$files files" to faint), top + h - 28f * k)
            drawSegs(
                listOf(
                    "used by $usedBy" to roles.usedBy.copy(alpha = a),
                    " · " to faint,
                    "uses $uses" to primary.copy(alpha = a),
                ),
                line1Y,
            )
        }
        isHub -> drawSegs(
            listOf(
                "$files files" to faint,
                "  ·  " to faint,
                "used by $usedBy" to roles.hub.copy(alpha = a),
            ),
            line1Y,
        )
        else -> drawSegs(listOf("$files files" to faint), line1Y)
    }
}

private fun DrawScope.drawCardEdge(from: Offset, to: Offset, color: Color, stroke: Float, drawHead: Boolean, curve: Path, head: Path) {
    val midX = (from.x + to.x) / 2f
    curve.reset()
    curve.moveTo(from.x, from.y)
    curve.cubicTo(midX, from.y, midX, to.y, to.x, to.y)
    drawPath(curve, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
    if (!drawHead) return
    val dir = to - Offset(midX, to.y)
    val len = hypot(dir.x, dir.y)
    val unit = if (len < 0.001f) Offset(1f, 0f) else Offset(dir.x / len, dir.y / len)
    val headLen = 8f
    val headBase = to - unit * headLen
    val normal = Offset(-unit.y, unit.x) * (headLen * 0.5f)
    head.reset()
    head.moveTo(to.x, to.y)
    head.lineTo(headBase.x + normal.x, headBase.y + normal.y)
    head.lineTo(headBase.x - normal.x, headBase.y - normal.y)
    head.close()
    drawPath(head, color)
}
