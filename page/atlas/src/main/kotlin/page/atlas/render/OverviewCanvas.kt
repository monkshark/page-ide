package page.atlas.render

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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sqrt
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
private const val MODULE_CAP = 24
private const val HUB_MIN_DEPENDENTS = 4
private const val BAND_TOP = -40f
private const val BAND_LABEL_Y = -34f
private const val BAND_DIVIDER_Y = -14f

internal data class CardBox(val x: Float, val y: Float, val w: Float, val h: Float) {
    val centerX get() = x + w / 2f
    val centerY get() = y + h / 2f
    val right get() = x + w
    val bottom get() = y + h
}

internal data class OverviewCardScene(
    val boxes: Map<String, CardBox>,
    val bands: List<Pair<ModuleLayer, Float>>,
    val visible: Set<String>,
    val hiddenCount: Int,
    val width: Float,
    val top: Float,
    val bottom: Float,
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
    val bands = ArrayList<Pair<ModuleLayer, Float>>()
    var maxRight = 0f
    var maxBottom = 0f
    for (layer in layout.columns) {
        val x = (layout.columnX[layer] ?: continue).toFloat()
        val inLayer = visibleNodes
            .filter { layout.layerOf[it.id] == layer }
            .sortedBy { layout.positions[it.id]?.y ?: 0.0 }
        if (inLayer.isEmpty()) continue
        bands.add(layer to x)
        var y = 0f
        for (n in inLayer) {
            val w = if (n.external) EXTERNAL_W else CARD_W
            val h = if (n.external) EXTERNAL_H else cardHeight(n.fileCount, maxFiles)
            val box = CardBox(x, y, w, h)
            boxes[n.id] = box
            y += h + CARD_GAP
            maxRight = maxOf(maxRight, box.right)
            maxBottom = maxOf(maxBottom, box.bottom)
        }
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
) {
    val textMeasurer = rememberTextMeasurer()
    val surface = MaterialTheme.colorScheme.surface
    val cardFill = MaterialTheme.colorScheme.surfaceVariant
    val outline = MaterialTheme.colorScheme.outline
    val onSurface = MaterialTheme.colorScheme.onSurface
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val roles = atlasRoleColors()
    val titleStyle = TextStyle(fontSize = 11.sp, color = onSurface)
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

    fun fitTransform(): Pair<Offset, Float> {
        val cw = canvasSize.width.toFloat()
        val ch = canvasSize.height.toFloat()
        if (cw <= 0f || ch <= 0f || scene.boxes.isEmpty()) return Offset.Zero to 1f
        val w = scene.width + 160f
        val h = (scene.bottom - scene.top) + 160f
        val fit = if (w <= 0f || h <= 0f) 1f else min(cw / w, ch / h).coerceIn(0.1f, 1.6f)
        val cx = scene.width / 2f
        val cy = (scene.top + scene.bottom) / 2f
        return Offset(cw / 2f - cx * fit, ch / 2f - cy * fit) to fit
    }

    fun viewTransform(): Pair<Offset, Float> = if (scale > 0f) pan to scale else fitTransform()

    fun centerOf(id: String): Offset? = scene.boxes[id]?.let { Offset(it.centerX, it.centerY) }

    LaunchedEffect(scene, canvasSize, fitted) {
        if (canvasSize.width <= 0 || scene.boxes.isEmpty()) return@LaunchedEffect
        if (fitted) return@LaunchedEffect
        val (p, s) = fitTransform()
        pan = p
        scale = s
        fitted = true
    }

    LaunchedEffect(activeModuleId, followActive, canvasSize, scene) {
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
                            onSelectionChange(selection.drillInto(id))
                        } else if (node.files.size == 1) {
                            onOpenFile(node.files.first().path)
                        } else {
                            onSelectionChange(selection.selectModule(id))
                        }
                    }
                }
            },
    ) {
        if (scene.boxes.isEmpty()) return@Canvas
        val (base, s) = viewTransform()
        if (s <= 0f) return@Canvas
        val onPath = pathNodeSet != null
        val focusId = if (onPath) null else selectedId ?: hoverId ?: activeModuleId?.takeIf { followActive }
        val highlighted = if (onPath) pathNodeSet else focusId?.let { adjacency[it].orEmpty() + it }

        for ((layer, colX) in scene.bands) {
            val measured = textMeasurer.measure(AnnotatedString(layerLabel(layer)), bandStyle)
            val cx = base.x + (colX + CARD_W / 2f) * s
            val cy = base.y + BAND_LABEL_Y * s
            drawText(
                textLayoutResult = measured,
                color = labelColor.copy(alpha = 0.65f),
                topLeft = Offset(cx - measured.size.width / 2f, cy),
            )
        }
        if (scene.bands.isNotEmpty()) {
            val firstX = base.x + scene.bands.first().second * s
            val lastX = base.x + (scene.bands.last().second + CARD_W) * s
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
            drawCardEdge(from, to, color, stroke, drawHead)
        }

        for (node in graph.nodes) {
            if (node.id !in scene.visible) continue
            val b = scene.boxes[node.id] ?: continue
            val left = base.x + b.x * s
            val top = base.y + b.y * s
            val w = b.w * s
            val h = b.h * s
            val dimmed = highlighted != null && node.id !in highlighted
            val dimAlpha = if (dimmed) 0.4f else 1f
            val active = node.kind == NodeKind.ACTIVE
            val selected = node.id == selectedId
            val isHub = (indeg[node.id] ?: 0) >= HUB_MIN_DEPENDENTS
            val inCycle = node.id in cycleNodes
            val pathEnd = pathIds != null && (node.id == pathIds.first() || node.id == pathIds.last())
            val topLeft = Offset(left, top)
            val size = Size(w, h)
            val corner = CornerRadius(8f * s)

            if (!node.external) {
                val fill = when {
                    selected || active -> primary.copy(alpha = 0.12f * dimAlpha)
                    else -> cardFill.copy(alpha = (if (dimmed) 0.4f else 1f))
                }
                drawRoundRect(color = surface.copy(alpha = if (dimmed) 0.4f else 1f), topLeft = topLeft, size = size, cornerRadius = corner)
                drawRoundRect(color = fill, topLeft = topLeft, size = size, cornerRadius = corner)
            }

            val borderColor = when {
                node.external -> roles.neutral.copy(alpha = if (dimmed) 0.3f else 0.7f)
                selected || active || pathEnd -> (if (pathEnd) roles.path else primary).copy(alpha = dimAlpha)
                isHub -> roles.hub.copy(alpha = if (dimmed) 0.25f else 0.55f)
                inCycle -> roles.cycle.copy(alpha = if (dimmed) 0.3f else 0.7f)
                else -> outline.copy(alpha = if (dimmed) 0.4f else 0.9f)
            }
            val borderStyle = when {
                node.external -> Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f)))
                selected || active || pathEnd -> Stroke(width = 1.6f)
                else -> Stroke(width = 1.2f)
            }
            drawRoundRect(color = borderColor, topLeft = topLeft, size = size, cornerRadius = corner, style = borderStyle)

            var titleX = left + 14f
            if (!node.external && isHub) {
                drawCircle(color = roles.hub.copy(alpha = dimAlpha), radius = 5f, center = Offset(left + 18f, top + 20f))
                titleX = left + 34f
            } else if (!node.external && inCycle) {
                drawCircle(
                    color = roles.cycle.copy(alpha = dimAlpha),
                    radius = 6f,
                    center = Offset(left + 18f, top + 18f),
                    style = Stroke(width = 2f),
                )
                titleX = left + 34f
            }

            if (w < 56f) continue
            val short = node.label.substringAfterLast('/')
            val titleMeasured = textMeasurer.measure(AnnotatedString(short), titleStyle)
            drawText(
                textLayoutResult = titleMeasured,
                color = onSurface.copy(alpha = if (dimmed) 0.45f else 1f),
                topLeft = Offset(titleX, top + 8f),
            )

            val files = node.fileCount
            val i = indeg[node.id] ?: 0
            val o = outdeg[node.id] ?: 0
            val faint = labelColor.copy(alpha = if (dimmed) 0.3f else 0.7f)
            when {
                node.external -> drawCardLine(textMeasurer, "external", bodyStyle, faint, left + 14f, top + h - 22f)
                selected || node.id == focusId -> {
                    drawCardLine(textMeasurer, "$files files", bodyStyle, faint, left + 14f, top + h - 38f)
                    drawCardLine(textMeasurer, "used by $i · uses $o", bodyStyle, roles.dependency.copy(alpha = dimAlpha), left + 14f, top + h - 22f)
                }
                isHub -> drawCardLine(textMeasurer, "$files files · used by $i", bodyStyle, roles.hub.copy(alpha = dimAlpha), left + 14f, top + h - 22f)
                else -> drawCardLine(textMeasurer, "$files files", bodyStyle, faint, left + 14f, top + h - 22f)
            }
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
        drawText(textMeasurer.measure(AnnotatedString(legend), legendStyle), topLeft = Offset(14f, 12f))

        if (scene.hiddenCount > 0) {
            val footer = "Showing largest ${scene.visible.size} modules · ${scene.hiddenCount} smaller hidden"
            val footerLayout = textMeasurer.measure(AnnotatedString(footer), legendStyle)
            drawText(footerLayout, topLeft = Offset(14f, size.height - 22f))
        }
    }
}

private fun DrawScope.drawCardLine(
    measurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    style: TextStyle,
    color: Color,
    x: Float,
    y: Float,
) {
    drawText(measurer.measure(AnnotatedString(text), style), color = color, topLeft = Offset(x, y))
}

private fun DrawScope.drawCardEdge(from: Offset, to: Offset, color: Color, stroke: Float, drawHead: Boolean) {
    val midX = (from.x + to.x) / 2f
    val curve = Path().apply {
        moveTo(from.x, from.y)
        cubicTo(midX, from.y, midX, to.y, to.x, to.y)
    }
    drawPath(curve, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
    if (!drawHead) return
    val dir = to - Offset(midX, to.y)
    val len = hypot(dir.x, dir.y)
    val unit = if (len < 0.001f) Offset(1f, 0f) else Offset(dir.x / len, dir.y / len)
    val headLen = 8f
    val headBase = to - unit * headLen
    val normal = Offset(-unit.y, unit.x) * (headLen * 0.5f)
    val head = Path().apply {
        moveTo(to.x, to.y)
        lineTo(headBase.x + normal.x, headBase.y + normal.y)
        lineTo(headBase.x - normal.x, headBase.y - normal.y)
        close()
    }
    drawPath(head, color)
}
