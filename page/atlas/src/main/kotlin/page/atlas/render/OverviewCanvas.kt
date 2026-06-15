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

@Composable
internal fun OverviewCanvas(
    graph: ModuleGraph,
    layout: ForceLayoutResult,
    activeModuleId: String?,
    followActive: Boolean,
    view: MapViewState,
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
    var selectedId by remember(graph) { mutableStateOf<String?>(null) }
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
        val s = if (scale > 0f) scale else fitTransform().second
        scale = s
        pan = Offset(
            canvasSize.width / 2f - world.x.toFloat() * s,
            canvasSize.height / 2f - world.y.toFloat() * s,
        )
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
                    onTap = { tap -> selectedId = nodeAt(tap) },
                    onDoubleTap = { tap ->
                        val id = nodeAt(tap) ?: return@detectTapGestures
                        val world = layout.positions[id] ?: return@detectTapGestures
                        val (_, s) = viewTransform()
                        scale = s
                        pan = Offset(size.width / 2f - world.x.toFloat() * s, size.height / 2f - world.y.toFloat() * s)
                    },
                )
            },
    ) {
        if (graph.nodes.isEmpty()) return@Canvas
        val (base, s) = viewTransform()
        if (s <= 0f) return@Canvas
        val focusId = selectedId ?: hoverId
        val highlighted = focusId?.let { adjacency[it].orEmpty() + it }
        val labelBudget = if (s >= 0.9f) 40 else 12

        for (edge in graph.edges) {
            val from = screenOf(edge.from, base, s) ?: continue
            val to = screenOf(edge.to, base, s) ?: continue
            val dimmed = highlighted != null && (edge.from !in highlighted || edge.to !in highlighted)
            val inCycle = (edge.from to edge.to) in cycleKeys
            val color = when {
                inCycle -> errorColor.copy(alpha = if (dimmed) 0.18f else 0.8f)
                dimmed -> edgeColor.copy(alpha = 0.06f)
                else -> edgeColor.copy(alpha = 0.4f)
            }
            val stroke = (1.2f + ln(edge.weight.toFloat())).coerceAtMost(5f)
            val toNode = nodeById[edge.to]
            val targetRadius = if (toNode != null) moduleRadius(toNode) * s else 0f
            drawOverviewEdge(from, to, color, stroke, targetRadius)
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
            if (active) {
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
                val lang = if (node.language.isEmpty()) "" else " · ${node.language}"
                val text = "${node.label} · ${node.fileCount} files$lang"
                val measured = textMeasurer.measure(AnnotatedString(text), labelStyle)
                drawText(
                    textLayoutResult = measured,
                    color = labelColor.copy(alpha = if (dimmed) 0.3f else 1f),
                    topLeft = Offset(center.x - measured.size.width / 2f, center.y + r + 3f),
                )
            }
        }
    }
}

private fun DrawScope.drawOverviewEdge(
    from: Offset,
    to: Offset,
    color: Color,
    stroke: Float,
    targetRadius: Float,
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
