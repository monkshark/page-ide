package page.atlas.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import page.atlas.graph.GraphEdge
import page.atlas.graph.GraphInsights
import page.atlas.graph.GraphSlice
import page.atlas.interaction.DependencyExploration
import page.atlas.interaction.ExplorationSlot
import page.atlas.interaction.ExplorationHighlight
import page.ui.EditorFontFamily

private const val CARD_WIDTH = 196f
private const val CARD_HEIGHT = 76f

internal fun explorationRect(slot: ExplorationSlot): Rect = Rect(
    Offset(slot.column * 290f, slot.row * 120f), Size(CARD_WIDTH, CARD_HEIGHT),
)

private data class ExplorationCurve(val start: Offset, val first: Offset, val second: Offset, val end: Offset) {
    fun point(t: Float): Offset {
        val u = 1f - t
        return start * (u * u * u) + first * (3f * u * u * t) + second * (3f * u * t * t) + end * (t * t * t)
    }

    fun distance(point: Offset): Float = (0 until 30).minOf { i ->
        val a = point(i / 30f)
        val b = point((i + 1) / 30f)
        val delta = b - a
        val lengthSquared = delta.x * delta.x + delta.y * delta.y
        val fraction = if (lengthSquared == 0f) 0f else
            (((point.x - a.x) * delta.x + (point.y - a.y) * delta.y) / lengthSquared).coerceIn(0f, 1f)
        (point - (a + delta * fraction)).getDistance()
    }
}

private fun explorationCurve(from: Rect, to: Rect): ExplorationCurve = when {
    from == to -> ExplorationCurve(
        Offset(from.right, from.center.y), Offset(from.right + 62f, from.top - 52f),
        Offset(from.center.x, from.top - 68f), Offset(from.center.x, from.top),
    )
    to.left > from.left -> ExplorationCurve(
        Offset(from.right, from.center.y), Offset(from.right + 52f, from.center.y),
        Offset(to.left - 52f, to.center.y), Offset(to.left, to.center.y),
    )
    to.left < from.left -> ExplorationCurve(
        Offset(from.left, from.center.y - 10f), Offset(from.left - 52f, from.center.y - 10f),
        Offset(to.right + 52f, to.center.y - 10f), Offset(to.right, to.center.y - 10f),
    )
    else -> ExplorationCurve(
        Offset(from.right, from.center.y), Offset(from.right + 64f, from.center.y),
        Offset(to.right + 64f, to.center.y), Offset(to.right, to.center.y),
    )
}

@Composable
internal fun DependencyExplorationCanvas(
    slice: GraphSlice,
    exploration: DependencyExploration,
    camera: MapViewState,
    onSelect: (String) -> Unit,
    onInspect: (GraphEdge) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val accent = colors.primary
    val roles = atlasRoleColors()
    val measurer = rememberTextMeasurer()
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val nodes = remember(slice, exploration.positions) { slice.nodes.filter { it.id in exploration.positions } }
    val rects = remember(exploration.positions) { exploration.positions.mapValues { explorationRect(it.value) } }
    val edges = remember(slice, rects) { slice.edges.filter { it.from in rects && it.to in rects } }
    val curves = remember(edges, rects) {
        edges.associateWith { explorationCurve(rects.getValue(it.from), rects.getValue(it.to)) }
    }
    val impactDepths = remember(slice, exploration.selectedId, exploration.highlight) {
        if (exploration.highlight == ExplorationHighlight.IMPACT) exploration.selectedId?.let { id ->
            GraphInsights.impact(slice, id).associate { it.node.id to it.depth }
        }.orEmpty() else emptyMap()
    }
    val highlightColor = when (exploration.highlight) {
        ExplorationHighlight.CYCLE -> roles.cycle
        ExplorationHighlight.IMPACT -> roles.usedBy
        else -> roles.path
    }
    val currentSelect by rememberUpdatedState(onSelect)
    val currentInspect by rememberUpdatedState(onInspect)
    val currentNodes by rememberUpdatedState(nodes)
    val currentRects by rememberUpdatedState(rects)
    val currentCurves by rememberUpdatedState(curves)
    val currentSelection by rememberUpdatedState(exploration.selectedId)
    LaunchedEffect(viewport, camera.scale, rects) {
        if (camera.scale > 0f || viewport.width == 0 || viewport.height == 0 || rects.isEmpty()) return@LaunchedEffect
        val left = rects.values.minOf { it.left } - 90f
        val top = rects.values.minOf { it.top } - 90f
        val right = rects.values.maxOf { it.right } + 90f
        val bottom = rects.values.maxOf { it.bottom } + 90f
        camera.scale = min(viewport.width / (right - left), viewport.height / (bottom - top)).coerceIn(.15f, 2f)
        camera.pan = Offset(viewport.width / 2f, viewport.height / 2f) -
            Offset((left + right) / 2f, (top + bottom) / 2f) * camera.scale
    }
    val titleStyle = TextStyle(fontFamily = EditorFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    val pathStyle = TextStyle(fontFamily = EditorFontFamily, fontSize = 9.sp)
    val labels = remember(nodes, colors, measurer) {
        nodes.associate { node -> node.id to (
            measurer.measure(node.label, titleStyle.copy(color = colors.onSurface), maxLines = 1,
                overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = 168)) to
                measurer.measure(node.path?.parent?.fileName?.toString() ?: "External dependency",
                    pathStyle.copy(color = colors.onSurfaceVariant), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = 168))
            )
        }
    }
    Canvas(
        modifier.clipToBounds().onSizeChanged { viewport = it }
            .semantics {
                contentDescription = "Dependency graph. A to B means A uses B. Arrow keys select files."
                customActions = nodes.map { node -> CustomAccessibilityAction("Explore ${node.label}") { onSelect(node.id); true } }
            }
            .onKeyEvent {
                if (it.type != KeyEventType.KeyDown || currentNodes.isEmpty()) false
                else when (it.key) {
                    Key.DirectionRight, Key.DirectionDown, Key.DirectionLeft, Key.DirectionUp -> {
                        val direction = if (it.key == Key.DirectionLeft || it.key == Key.DirectionUp) -1 else 1
                        val index = currentNodes.indexOfFirst { node -> node.id == currentSelection }
                        currentSelect(currentNodes[(index + direction).mod(currentNodes.size)].id)
                        true
                    }
                    else -> false
                }
            }.focusable()
            .pointerInput(camera) {
                detectTapGestures { position ->
                    val scale = camera.scale.takeIf { it > 0f } ?: return@detectTapGestures
                    val world = (position - camera.pan) / scale
                    val node = currentNodes.lastOrNull { world in currentRects.getValue(it.id) }
                    if (node != null) currentSelect(node.id)
                    else currentCurves.entries.minByOrNull { it.value.distance(world) }
                        ?.takeIf { it.value.distance(world) <= 9f / scale }
                        ?.let { currentInspect(it.key) }
                }
            }
            .pointerInput(camera) {
                detectDragGestures { change, amount -> change.consume(); camera.pan += amount }
            }
            .pointerInput(camera) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type != PointerEventType.Scroll || camera.scale <= 0f) continue
                        val change = event.changes.firstOrNull() ?: continue
                        val old = camera.scale
                        val next = (old * 1.12f.pow(-change.scrollDelta.y)).coerceIn(.12f, 3f)
                        camera.pan = change.position - (change.position - camera.pan) * (next / old)
                        camera.scale = next
                        change.consume()
                    }
                }
            },
    ) {
        drawRect(colors.background)
        val relatedIds = edges.filter { it.from == exploration.selectedId || it.to == exploration.selectedId }
            .flatMapTo(HashSet()) { listOf(it.from, it.to) }
        val highlight = exploration.highlightedEdges
        withTransform({ translate(camera.pan.x, camera.pan.y); scale(camera.scale.coerceAtLeast(.01f), camera.scale.coerceAtLeast(.01f), Offset.Zero) }) {
            for ((edge, curve) in curves) {
                val selected = edge == exploration.selectedEdge
                val emphasized = selected || if (highlight.isNotEmpty()) edge in highlight
                else edge.from == exploration.selectedId || edge.to == exploration.selectedId
                val color = when {
                    selected -> accent
                    edge in highlight -> highlightColor
                    emphasized -> accent.copy(alpha = .7f)
                    else -> colors.outlineVariant.copy(alpha = .4f)
                }
                val path = Path().apply {
                    moveTo(curve.start.x, curve.start.y)
                    cubicTo(curve.first.x, curve.first.y, curve.second.x, curve.second.y, curve.end.x, curve.end.y)
                }
                drawPath(path, color, style = Stroke(
                    width = if (selected) 2.8f else if (emphasized) 1.8f else 1f,
                    pathEffect = if ((impactDepths[edge.from] ?: 0) > 1) PathEffect.dashPathEffect(floatArrayOf(6f, 4f)) else null,
                ))
                val tangent = curve.end - curve.point(.96f)
                val length = hypot(tangent.x, tangent.y).coerceAtLeast(.01f)
                val unit = tangent / length
                val side = Offset(-unit.y, unit.x)
                drawPath(Path().apply {
                    moveTo(curve.end.x, curve.end.y)
                    val left = curve.end - unit * 8f + side * 3.5f
                    val right = curve.end - unit * 8f - side * 3.5f
                    lineTo(left.x, left.y); lineTo(right.x, right.y); close()
                }, color)
                if (emphasized) {
                    val label = measurer.measure(edge.kind.explorationLabel(), pathStyle.copy(color = color))
                    val at = curve.point(.5f) - Offset(label.size.width / 2f, label.size.height + 5f)
                    drawRoundRect(colors.background, at - Offset(3f, 1f), Size(label.size.width + 6f, label.size.height + 2f), CornerRadius(3f))
                    drawText(label, topLeft = at)
                }
            }
            for (node in nodes) {
                val rect = rects.getValue(node.id)
                val selected = node.id == exploration.selectedId
                val inHighlight = highlight.any { it.from == node.id || it.to == node.id }
                val emphasized = selected || inHighlight || node.id in relatedIds
                drawRoundRect(if (selected) lerp(colors.surface, accent, .14f) else colors.surface, rect.topLeft, rect.size, CornerRadius(10f))
                drawRoundRect(
                    when { selected -> accent; inHighlight -> highlightColor; else -> colors.outlineVariant },
                    rect.topLeft, rect.size, CornerRadius(10f), style = Stroke(if (selected) 1.8f else 1f),
                )
                val (title, path) = labels.getValue(node.id)
                drawText(title, topLeft = rect.topLeft + Offset(14f, 17f), alpha = if (emphasized) 1f else .6f)
                drawText(path, topLeft = rect.topLeft + Offset(14f, 43f), alpha = if (emphasized) 1f else .6f)
                impactDepths[node.id]?.let { depth ->
                    drawText(measurer.measure(if (depth == 1) "Direct dependent" else "$depth hops away",
                        pathStyle.copy(color = highlightColor)), topLeft = Offset(rect.left + 8f, rect.bottom + 5f))
                }
            }
        }
    }
}
