package page.atlas.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import page.atlas.graph.ModuleGraph
import page.atlas.graph.ModuleNode
import page.atlas.graph.NodeKind
import page.atlas.graph.modulePath
import page.atlas.interaction.OverviewSelection

private val OutEdgeColor = Color(0xFF6E8BFF)
private val InEdgeColor = Color(0xFF4FD3C7)
private val PathEdgeColor = Color(0xFFE7B45C)

@Composable
internal fun OverviewCanvas(
    graph: ModuleGraph,
    layout: ForceLayoutResult,
    activeModuleId: String?,
    followActive: Boolean,
    view: MapViewState,
    selection: OverviewSelection,
    onSelectionChange: (OverviewSelection) -> Unit,
    onOpenFile: (java.nio.file.Path) -> Unit,
) {
    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)
    val primary = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val edgeColor = labelColor

    val maxFiles = remember(graph) { graph.nodes.maxOfOrNull { it.fileCount }?.coerceAtLeast(1) ?: 1 }
    val hubW = remember(graph) {
        val indeg = HashMap<String, Int>()
        for (e in graph.edges) if (e.from != e.to) indeg.merge(e.to, 1, Int::plus)
        graph.nodes.associate { it.id to hubWeight(indeg[it.id] ?: 0) }
    }
    val cycleKeys = remember(graph) {
        mapCycleEdges(graph.edges.map { MapEdge(it.from, it.to, it.weight) })
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
    var shiftPressed by remember { mutableStateOf(false) }
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

    fun moduleRadius(node: ModuleNode): Float {
        val rel = sqrt(node.fileCount.toFloat() / maxFiles)
        val base = 11f + 27f * rel
        return base * (hubW[node.id] ?: 1f)
    }

    fun fitTransform(): Pair<Offset, Float> {
        val cw = canvasSize.width.toFloat()
        val ch = canvasSize.height.toFloat()
        if (cw <= 0f || ch <= 0f) return Offset.Zero to 1f
        val w = layout.width.toFloat() + 160f
        val h = layout.height.toFloat() + 160f
        val fit = if (w <= 0f || h <= 0f) 1f else min(cw / w, ch / h).coerceIn(0.1f, 1.6f)
        return Offset(cw / 2f, ch / 2f) to fit
    }

    fun viewTransform(): Pair<Offset, Float> = if (scale > 0f) pan to scale else fitTransform()

    fun screenOf(id: String, base: Offset, s: Float): Offset? {
        val p = layout.positions[id] ?: return null
        return Offset(base.x + p.x.toFloat() * s, base.y + p.y.toFloat() * s)
    }

    LaunchedEffect(layout, canvasSize, fitted) {
        if (canvasSize.width <= 0 || layout.positions.isEmpty()) return@LaunchedEffect
        if (fitted) return@LaunchedEffect
        val (p, s) = fitTransform()
        pan = p
        scale = s
        fitted = true
    }

    LaunchedEffect(activeModuleId, followActive, canvasSize, layout) {
        if (!followActive || canvasSize.width <= 0) return@LaunchedEffect
        val id = activeModuleId ?: return@LaunchedEffect
        val world = layout.positions[id] ?: return@LaunchedEffect
        val hood = (adjacency[id].orEmpty() + id).mapNotNull { layout.positions[it] }
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
        val w = (maxX - minX).toFloat() + 360f
        val h = (maxY - minY).toFloat() + 360f
        val s = min(cw / w, ch / h).coerceIn(0.45f, 1.8f)
        scale = s
        pan = Offset(cw / 2f - world.x.toFloat() * s, ch / 2f - world.y.toFloat() * s)
        fitted = true
    }

    fun nodeAt(pos: Offset): String? {
        val (base, s) = viewTransform()
        if (s <= 0f) return null
        var best: String? = null
        var bestDist = Float.MAX_VALUE
        for (node in graph.nodes) {
            val c = screenOf(node.id, base, s) ?: continue
            val d = hypot(pos.x - c.x, pos.y - c.y)
            val r = (moduleRadius(node) * s).coerceAtLeast(12f)
            if (d <= r && d < bestDist) {
                bestDist = d
                best = node.id
            }
        }
        return best
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
                        shiftPressed = event.keyboardModifiers.isShiftPressed
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
                detectTapGestures(
                    onTap = { tap ->
                        val id = nodeAt(tap)
                        val source = selection.moduleId
                        onSelectionChange(
                            when {
                                id == null -> selection.clear()
                                shiftPressed && source != null && id != source ->
                                    if (selection.kind == OverviewSelection.Kind.PATH && id == selection.pathTarget) {
                                        selection.selectModule(id).tracePath(source)
                                    } else {
                                        selection.tracePath(id)
                                    }
                                else -> selection.selectModule(id)
                            },
                        )
                    },
                    onDoubleTap = { tap ->
                        val id = nodeAt(tap) ?: return@detectTapGestures
                        val node = nodeById[id] ?: return@detectTapGestures
                        if (node.splittable) {
                            onSelectionChange(selection.drillInto(id))
                        } else {
                            node.files.firstOrNull()?.let { onOpenFile(it.path) }
                        }
                    },
                )
            },
    ) {
        if (graph.nodes.isEmpty()) return@Canvas
        val (base, s) = viewTransform()
        if (s <= 0f) return@Canvas
        val onPath = pathNodeSet != null
        val focusId = if (onPath) null else selectedId ?: hoverId ?: activeModuleId?.takeIf { followActive }
        val highlighted = if (onPath) pathNodeSet else focusId?.let { adjacency[it].orEmpty() + it }
        val labelBudget = if (s >= 0.9f) 40 else 12

        for (edge in graph.edges) {
            val from = screenOf(edge.from, base, s) ?: continue
            val to = screenOf(edge.to, base, s) ?: continue
            val onPathEdge = pathEdgeKeys != null && (edge.from to edge.to) in pathEdgeKeys
            val out = selectedId != null && edge.from == selectedId
            val incoming = selectedId != null && edge.to == selectedId
            val touchesFocus = focusId != null && (edge.from == focusId || edge.to == focusId)
            val inCycle = (edge.from to edge.to) in cycleKeys
            val color = when {
                pathEdgeKeys != null -> if (onPathEdge) PathEdgeColor.copy(alpha = 0.95f) else edgeColor.copy(alpha = 0.03f)
                out -> OutEdgeColor.copy(alpha = 0.9f)
                incoming -> InEdgeColor.copy(alpha = 0.9f)
                touchesFocus -> primary.copy(alpha = 0.85f)
                focusId != null -> edgeColor.copy(alpha = 0.03f)
                inCycle -> errorColor.copy(alpha = 0.55f)
                else -> edgeColor.copy(alpha = 0.08f)
            }
            val drawHead = onPathEdge || out || incoming || touchesFocus || (inCycle && focusId == null && !onPath)
            val weightStroke = (0.8f + ln(edge.weight.toFloat()) * 0.6f).coerceAtMost(4f)
            val stroke = when {
                onPathEdge -> weightStroke + 1.2f
                touchesFocus -> weightStroke + 0.8f
                else -> weightStroke
            }
            val toNode = nodeById[edge.to]
            val targetRadius = if (toNode != null) moduleRadius(toNode) * s else 0f
            drawOverviewEdge(from, to, color, stroke, targetRadius, drawHead)
        }

        for (node in graph.nodes) {
            val center = screenOf(node.id, base, s) ?: continue
            val r = (moduleRadius(node) * s).coerceAtLeast(3f)
            val dimmed = highlighted != null && node.id !in highlighted
            val active = node.kind == NodeKind.ACTIVE
            val tint = moduleTint(node)
            val territoryAlpha = if (dimmed) 0.05f else 0.16f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(tint.copy(alpha = territoryAlpha), tint.copy(alpha = 0f)),
                    center = center,
                    radius = r * 2.8f,
                ),
                radius = r * 2.8f,
                center = center,
            )
            val fillAlpha = if (dimmed) 0.25f else 1f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(tint.copy(alpha = fillAlpha), tint.copy(alpha = fillAlpha * 0.65f)),
                    center = center + Offset(-r * 0.3f, -r * 0.3f),
                    radius = (r * 1.5f).coerceAtLeast(1f),
                ),
                radius = r,
                center = center,
            )
            val pathEnd = pathIds != null && (node.id == pathIds.first() || node.id == pathIds.last())
            if (pathEnd) {
                drawCircle(
                    color = PathEdgeColor.copy(alpha = 0.95f),
                    radius = r + 3f,
                    center = center,
                    style = Stroke(width = 2f),
                )
            } else if (active) {
                drawCircle(
                    color = primary.copy(alpha = 0.9f),
                    radius = r + 3f,
                    center = center,
                    style = Stroke(width = 2f),
                )
            } else if (node.id == selectedId) {
                drawCircle(
                    color = primary.copy(alpha = 0.8f),
                    radius = r + 3f,
                    center = center,
                    style = Stroke(width = 1.5f),
                )
            }
            val showLabel = active || node.id == focusId ||
                highlighted?.contains(node.id) == true ||
                graph.nodes.size <= labelBudget
            if (showLabel) {
                val short = node.label.substringAfterLast('/')
                val text = when {
                    node.id == focusId -> {
                        val lang = if (node.language.isEmpty()) "" else " · ${node.language}"
                        "${node.label} · ${node.fileCount} files$lang"
                    }
                    s >= 1.1f -> "$short · ${node.fileCount}"
                    else -> short
                }
                val measured = textMeasurer.measure(AnnotatedString(text), labelStyle)
                drawText(
                    textLayoutResult = measured,
                    color = labelColor.copy(alpha = if (dimmed) 0.3f else 1f),
                    topLeft = Offset(center.x - measured.size.width / 2f, center.y + r + 3f),
                )
            }
        }

        val legend = if (onPath) {
            val hops = (pathIds?.size ?: 1) - 1
            "amber = dependency path · $hops hop${if (hops == 1) "" else "s"}"
        } else if (selectedId != null) {
            "blue = depends on · teal = used by · red = cycle"
        } else {
            "circle = folder · size = files × dependents\ncolor = language · arrow = depends on · red = cycle"
        }
        val legendStyle = labelStyle.copy(fontSize = 9.sp, color = labelColor.copy(alpha = 0.6f))
        val legendLayout = textMeasurer.measure(AnnotatedString(legend), legendStyle)
        drawText(legendLayout, topLeft = Offset(14f, 12f))
    }
}

private fun DrawScope.drawOverviewEdge(
    from: Offset,
    to: Offset,
    color: Color,
    stroke: Float,
    targetRadius: Float,
    drawHead: Boolean,
) {
    val chord = to - from
    val len = hypot(chord.x, chord.y)
    if (len < 1f) return
    val unit = Offset(chord.x / len, chord.y / len)
    val bow = min(len * 0.16f, 36f)
    val control = (from + to) / 2f + Offset(-unit.y, unit.x) * bow
    val end = to - unit * (targetRadius + 2f)
    val curve = Path().apply {
        moveTo(from.x, from.y)
        quadraticTo(control.x, control.y, end.x, end.y)
    }
    drawPath(curve, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
    if (!drawHead) return
    val headLen = min(9f, len * 0.4f)
    val headBase = end - unit * headLen
    val normal = Offset(-unit.y, unit.x) * headLen * 0.5f
    val head = Path().apply {
        moveTo(end.x, end.y)
        lineTo(headBase.x + normal.x, headBase.y + normal.y)
        lineTo(headBase.x - normal.x, headBase.y - normal.y)
        close()
    }
    drawPath(head, color)
}

private fun moduleTint(node: ModuleNode): Color {
    val seed = (node.language.ifEmpty { node.label }).hashCode()
    val hue = ((seed % 360) + 360) % 360
    return Color.hsv(hue.toFloat(), 0.5f, 0.92f)
}
