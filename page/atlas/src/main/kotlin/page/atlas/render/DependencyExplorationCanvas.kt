package page.atlas.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
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
import page.atlas.interaction.ExplorationDirection

private const val CARD_WIDTH = 236f
private const val CARD_HEIGHT = 54f

internal fun explorationRect(slot: ExplorationSlot): Rect = Rect(
    Offset(slot.column * 364f, slot.row * 66f), Size(CARD_WIDTH, CARD_HEIGHT),
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
    revealRequest: ExplorationReveal? = null,
    onRevealHandled: (ExplorationReveal) -> Unit = {},
    onExpand: ((ExplorationDirection) -> Unit)? = null,
    onCollapse: ((ExplorationDirection) -> Unit)? = null,
    onClear: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val accent = colors.primary
    val roles = atlasRoleColors()
    val measurer = rememberTextMeasurer()
    val graphFocus = remember { FocusRequester() }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var hoverPosition by remember { mutableStateOf<Offset?>(null) }
    val nodes = remember(slice, exploration.positions) { slice.nodes.filter { it.id in exploration.positions } }
    val rects = remember(exploration.positions) { exploration.positions.mapValues { explorationRect(it.value) } }
    val directionGroups = remember(slice, exploration.positions, exploration.selectedId) {
        val selectedSlot = exploration.positions[exploration.selectedId]
        ExplorationDirection.entries.associateWith { direction ->
            val column = selectedSlot?.column?.plus(if (direction == ExplorationDirection.USES) 1 else -1)
            val visible = exploration.neighbors(slice, direction).mapNotNull { id ->
                exploration.positions[id]?.let { slot -> slot to rects.getValue(id) }
            }
            val columnSize = exploration.positions.values.count { it.column == column }
            if (visible.size == columnSize && visible.all { it.first.column == column }) visible.map { it.second } else emptyList()
        }
    }
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
    val hoverWorld = hoverPosition?.let { (it - camera.pan) / camera.scale.coerceAtLeast(.01f) }
    val hoveredNode = hoverWorld?.let { point -> nodes.lastOrNull { point in rects.getValue(it.id) } }
    val hoveredEdge = if (hoveredNode != null) null else hoverWorld?.let { point ->
        curves.entries.minByOrNull { it.value.distance(point) }
            ?.takeIf { it.value.distance(point) <= 12f / camera.scale.coerceAtLeast(.01f) }?.key
    }
    LaunchedEffect(revealRequest, viewport) {
        if (revealRequest != null && viewport.width > 0 && viewport.height > 0) {
            revealExploration(camera, revealRequest.ids.mapNotNull { rects[it] }, viewport)
            onRevealHandled(revealRequest)
        }
    }
    LaunchedEffect(viewport, camera.scale, rects) {
        if (camera.scale > 0f || viewport.width == 0 || viewport.height == 0 || rects.isEmpty()) return@LaunchedEffect
        val left = rects.values.minOf { it.left } - 32f
        val top = rects.values.minOf { it.top } - 88f
        val right = rects.values.maxOf { it.right } + 32f
        val bottom = rects.values.maxOf { it.bottom } + 32f
        camera.scale = min(viewport.width / (right - left), viewport.height / (bottom - top)).coerceIn(.15f, 1.05f)
        camera.pan = Offset(viewport.width / 2f, viewport.height / 2f) -
            Offset((left + right) / 2f, (top + bottom) / 2f) * camera.scale
    }
    val titleStyle = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
    val pathStyle = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)
    val labels = remember(nodes, colors, measurer, exploration.selectedId) {
        nodes.associate { node -> node.id to (
            measurer.measure(node.label, titleStyle.copy(color = colors.onSurface,
                fontWeight = if (node.id == exploration.selectedId) FontWeight.SemiBold else FontWeight.Normal), maxLines = 1,
                overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = (CARD_WIDTH - 50f).toInt())) to
                measurer.measure(node.path?.parent?.fileName?.toString() ?: "External dependency",
                    pathStyle.copy(color = colors.onSurfaceVariant), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = (CARD_WIDTH - 50f).toInt()))
            )
        }
    }
    Box(modifier.clipToBounds().onSizeChanged { viewport = it }) {
    Canvas(
        Modifier.fillMaxSize()
            .semantics {
                contentDescription = "Dependency graph. A to B means A uses B. Arrow keys select files."
                customActions = nodes.map { node -> CustomAccessibilityAction("Explore ${node.label}") { onSelect(node.id); true } } +
                    ExplorationDirection.entries.flatMap { direction ->
                        listOfNotNull(
                            onExpand?.let { action -> CustomAccessibilityAction("Expand ${direction.name}") { action(direction); true } },
                            onCollapse?.takeIf { exploration.canCollapse(direction) }?.let { action ->
                                CustomAccessibilityAction("Collapse ${direction.name}") { action(direction); true }
                            },
                        )
                    }
            }
            .onKeyEvent {
                if (it.type != KeyEventType.KeyDown || currentNodes.isEmpty()) false
                else when (it.key) {
                    Key.Escape -> {
                        val hasSelection = exploration.selectedEdge != null || exploration.highlight != null || exploration.message != null
                        if (hasSelection) onClear?.invoke()
                        hasSelection && onClear != null
                    }
                    Key.DirectionRight, Key.DirectionDown, Key.DirectionLeft, Key.DirectionUp -> {
                        val direction = if (it.key == Key.DirectionLeft || it.key == Key.DirectionUp) -1 else 1
                        val index = currentNodes.indexOfFirst { node -> node.id == currentSelection }
                        currentSelect(currentNodes[(index + direction).mod(currentNodes.size)].id)
                        true
                    }
                    else -> false
                }
            }.focusRequester(graphFocus).focusable()
            .pointerInput(camera) {
                detectTapGestures { position ->
                    graphFocus.requestFocus()
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
                detectDragGestures { change, amount -> hoverPosition = null; change.consume(); camera.pan += amount }
            }
            .pointerInput(camera) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Exit) hoverPosition = null
                        if (event.type == PointerEventType.Move || event.type == PointerEventType.Enter) {
                            hoverPosition = event.changes.firstOrNull()?.takeIf { !it.pressed }?.position
                        }
                        if (event.type != PointerEventType.Scroll || camera.scale <= 0f) continue
                        hoverPosition = null
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
            for ((direction, neighbors) in directionGroups) {
                if (neighbors.isNotEmpty()) {
                    val left = neighbors.minOf { it.left } - 12f
                    val right = neighbors.maxOf { it.right } + 12f
                    val top = neighbors.minOf { it.top } - 64f
                    val bottom = neighbors.maxOf { it.bottom } + 12f
                    drawRoundRect(lerp(colors.background, colors.surface, .5f), Offset(left, top),
                        Size(right - left, bottom - top), CornerRadius(8f))
                    if (camera.scale >= .8f) {
                    val label = measurer.measure(if (direction == ExplorationDirection.USES) "Dependencies" else "Dependents",
                        titleStyle.copy(color = colors.onSurface, fontWeight = FontWeight.SemiBold))
                    drawText(label, topLeft = Offset(left + 16f, top + 12f))
                    val hint = measurer.measure(if (direction == ExplorationDirection.USES) "Files this file uses" else "Files that use this file",
                        pathStyle.copy(color = colors.onSurfaceVariant))
                    drawText(hint, topLeft = Offset(left + 16f, top + 34f))
                    }
                    drawLine(colors.outline.copy(alpha = .25f), Offset(left + 12f, top + 60f),
                        Offset(right - 12f, top + 60f), 1f)
                }
            }
            for ((edge, curve) in curves) {
                val selected = edge == exploration.selectedEdge || edge == hoveredEdge
                val emphasized = selected || if (highlight.isNotEmpty()) edge in highlight
                else edge.from == exploration.selectedId || edge.to == exploration.selectedId
                val color = when {
                    selected -> accent
                    edge in highlight -> highlightColor
                    highlight.isNotEmpty() -> colors.onSurfaceVariant.copy(alpha = .18f)
                    edge.to == exploration.selectedId -> colors.onSurfaceVariant.copy(alpha = .55f)
                    edge.from == exploration.selectedId -> colors.onSurfaceVariant.copy(alpha = .55f)
                    else -> colors.onSurfaceVariant.copy(alpha = .1f)
                }
                val path = Path().apply {
                    moveTo(curve.start.x, curve.start.y)
                    cubicTo(curve.first.x, curve.first.y, curve.second.x, curve.second.y, curve.end.x, curve.end.y)
                }
                drawPath(path, color, style = Stroke(
                    width = if (selected) 2f else if (emphasized) 1.35f else 1f,
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
                if (selected || edge in highlight) {
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
                val nodeAccent = when {
                    selected -> accent
                    inHighlight -> highlightColor
                    else -> colors.onSurfaceVariant
                }
                if (selected) {
                    drawRoundRect(lerp(colors.background, accent, .12f), rect.topLeft - Offset(0f, 9f),
                        Size(rect.width, rect.height + 18f), CornerRadius(7f))
                    drawRoundRect(accent, Offset(rect.left, rect.top + 4f), Size(3f, rect.height - 8f), CornerRadius(1.5f))
                } else if (node == hoveredNode || inHighlight) {
                    drawRoundRect(lerp(colors.background, nodeAccent, .08f), rect.topLeft, rect.size, CornerRadius(5f))
                } else {
                    drawLine(colors.outline.copy(alpha = .14f), Offset(rect.left + 38f, rect.bottom),
                        Offset(rect.right - 8f, rect.bottom), 1f)
                }
                if (camera.scale >= .8f) {
                val type = node.label.substringAfterLast('.', "").take(3).uppercase().ifEmpty { "·" }
                val typeLabel = measurer.measure(type, TextStyle(fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = nodeAccent))
                val typeBox = Rect(Offset(rect.left + 10f, rect.center.y - 12f), Size(24f, 24f))
                drawRoundRect(nodeAccent.copy(alpha = .09f), typeBox.topLeft, typeBox.size, CornerRadius(4f))
                drawText(typeLabel, topLeft = typeBox.center - Offset(typeLabel.size.width / 2f, typeLabel.size.height / 2f))
                val (title, path) = labels.getValue(node.id)
                val textGap = 2f
                val textHeight = title.size.height + textGap + path.size.height
                val textTop = rect.top + (rect.height - textHeight) / 2f
                drawText(title, topLeft = Offset(rect.left + 44f, textTop), alpha = if (emphasized) 1f else .75f)
                drawText(path, topLeft = Offset(rect.left + 44f, textTop + title.size.height + textGap), alpha = if (emphasized) 1f else .65f)
                }
                if (selected && camera.scale >= .8f) {
                    val selectedLabel = measurer.measure("Exploring", pathStyle.copy(color = accent, fontWeight = FontWeight.Medium))
                    drawText(selectedLabel, topLeft = Offset(rect.left + 2f, rect.top - selectedLabel.size.height - 20f))
                }
                impactDepths[node.id]?.let { depth ->
                    drawText(measurer.measure(if (depth == 1) "Direct dependent" else "$depth hops away",
                        pathStyle.copy(color = highlightColor)), topLeft = Offset(rect.left + 8f, rect.bottom + 5f))
                }
            }
        }
        if (camera.scale < .8f) {
            for ((direction, neighbors) in directionGroups) {
                if (neighbors.isEmpty()) continue
                val label = measurer.measure(if (direction == ExplorationDirection.USES) "Dependencies" else "Dependents",
                    TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.onSurfaceVariant))
                val origin = Offset(neighbors.minOf { it.left } + 4f, neighbors.minOf { it.top } - 45f)
                drawText(label, topLeft = origin * camera.scale + camera.pan)
            }
            for (node in nodes) {
                val rect = rects.getValue(node.id)
                val topLeft = rect.topLeft * camera.scale + camera.pan
                val width = rect.width * camera.scale
                if (width < 26f) continue
                val label = measurer.measure(node.label, titleStyle.copy(fontSize = 12.sp, lineHeight = 16.sp, color = colors.onSurface),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = (width - 12f).toInt().coerceAtLeast(1)))
                drawText(label, topLeft = topLeft + Offset(6f, (rect.height * camera.scale - label.size.height) / 2f))
            }
        }
    }
    val density = LocalDensity.current
    val selectedRect = rects[exploration.selectedId]
    if (selectedRect != null && onExpand != null && onCollapse != null && exploration.selectedEdge == null) {
        Surface(Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            color = colors.surface, shape = RoundedCornerShape(8.dp)) {
            Row(Modifier.padding(horizontal = 6.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (direction in listOf(ExplorationDirection.USED_BY, ExplorationDirection.USES)) {
                    val hidden = exploration.neighbors(slice, direction).count { it !in exploration.positions }
                    val name = if (direction == ExplorationDirection.USES) "Uses" else "Used by"
                    val collapsible = exploration.canCollapse(direction)
                    if (hidden > 0) ExploreAction("${if (direction == ExplorationDirection.USES) "Dependencies" else "Dependents"} +${minOf(4, hidden)}", enabled = exploration.positions.size < DependencyExploration.MAX_VISIBLE,
                        description = "Expand $name: $hidden hidden files") { onExpand(direction) }
                    if (collapsible) ExploreAction(if (hidden > 0) "−" else if (direction == ExplorationDirection.USES) "− Dependencies" else "− Dependents",
                        description = "Collapse $name") { onCollapse(direction) }
                }
            }
        }
    }
    val tooltip = hoveredNode?.let { "${it.label}\n${it.path ?: "External dependency"}" } ?: hoveredEdge?.let { edge ->
        val from = nodes.firstOrNull { it.id == edge.from }?.label ?: edge.from
        val to = nodes.firstOrNull { it.id == edge.to }?.label ?: edge.to
        "$from ${edge.kind.explorationLabel()} $to\nClick to inspect source"
    }
    if (tooltip != null && hoverPosition != null) {
        val position = hoverPosition!!
        val width = with(density) { 320.dp.toPx() }
        val height = with(density) { 96.dp.toPx() }
        Surface(Modifier.offset { IntOffset(position.x.coerceIn(0f, (viewport.width - width).coerceAtLeast(0f)).toInt(),
            (position.y - height).coerceAtLeast(0f).toInt()) }.widthIn(max = 320.dp), shape = RoundedCornerShape(8.dp),
            color = colors.surface, shadowElevation = 6.dp) {
            Text(tooltip, modifier = Modifier.padding(12.dp), fontSize = 12.sp, lineHeight = 18.sp, color = colors.onSurface)
        }
    }
    }
}
