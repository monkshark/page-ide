package page.atlas.render

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import java.nio.file.Path as FilePath
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.roundToInt
import page.atlas.graph.GraphSlice
import page.ui.CompactDropdown
import page.ui.CompactMenuItem

@Composable
internal fun MapCanvas(
    slice: GraphSlice,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onNodeClick: (FilePath) -> Unit,
    expandedDirs: Set<String>?,
    onExpandedDirsChange: (Set<String>) -> Unit,
    filter: MapFilterState,
    onFilterChange: (MapFilterState) -> Unit,
) {
    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)
    val weightStyle = TextStyle(fontSize = 9.sp, color = labelColor)
    val effectiveExpanded = expandedDirs ?: remember(slice) { defaultExpandedDirs(slice) }
    val measureWidth: (String) -> Float =
        { textMeasurer.measure(AnnotatedString(it), labelStyle).size.width.toFloat() }
    val userOffsets = remember { mutableStateMapOf<String, Offset>() }
    val expandOrder = remember { mutableStateListOf<String>() }
    var justExpandedId by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf<MapMenuTarget?>(null) }
    val map = remember(slice, effectiveExpanded) {
        buildMap(slice, effectiveExpanded, measureWidth, userOffsets, expandOrder.toList(), justExpandedId)
    }
    val anim = remember { Animatable(1f) }
    var fromMap by remember { mutableStateOf(map) }
    var toMap by remember { mutableStateOf(map) }
    var panTarget by remember { mutableStateOf<Offset?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var scale by remember { mutableStateOf(0f) }
    var fitScale by remember { mutableStateOf(1f) }
    var fitted by remember { mutableStateOf(false) }

    fun fitTransform(): Pair<Offset, Float> {
        val cw = canvasSize.width.toFloat()
        val ch = canvasSize.height.toFloat()
        if (cw <= 0f || ch <= 0f || map.width <= 0f || map.height <= 0f) return Offset.Zero to 1f
        val fit = min(cw / (map.width + 48f), ch / (map.height + 48f)).coerceAtMost(1.4f)
        return Offset((cw - map.width * fit) / 2f, (ch - map.height * fit) / 2f) to fit
    }

    fun viewTransform(): Pair<Offset, Float> = if (scale > 0f) pan to scale else fitTransform()

    LaunchedEffect(slice, canvasSize) {
        if (fitted || canvasSize.width <= 0 || map.width <= 0f) return@LaunchedEffect
        val (p, s) = fitTransform()
        pan = p
        scale = s
        fitScale = s
        fitted = true
    }

    LaunchedEffect(map) {
        if (toMap == map) return@LaunchedEffect
        val prevById = toMap.boxes.associateBy { it.id }
        val nextById = map.boxes.associateBy { it.id }
        var base = lerpModel(fromMap, toMap, anim.value)
        fun absorb(id: String, dx: Float, dy: Float) {
            userOffsets[id] = (userOffsets[id] ?: Offset.Zero) + Offset(dx, dy)
            base = MapModel(
                base.boxes.map { if (belongsTo(it.id, id)) it.copy(x = it.x - dx, y = it.y - dy) else it },
                base.edges,
                base.width,
                base.height,
            )
        }
        for ((id, push) in map.pushes) {
            if (id in userOffsets) absorb(id, push.x, push.y)
        }
        for (id in userOffsets.keys.toList()) {
            if (id in map.pushes) continue
            val prev = prevById[id] ?: continue
            val next = nextById[id] ?: continue
            val dx = prev.x - next.x
            val dy = prev.y - next.y
            if (dx == 0f && dy == 0f) continue
            absorb(id, dx, dy)
        }
        fromMap = base
        toMap = map
        val startPan = pan
        val endPan = panTarget ?: startPan
        panTarget = null
        anim.snapTo(0f)
        anim.animateTo(1f, tween(260, easing = FastOutSlowInEasing)) {
            pan = lerp(startPan, endPan, value)
        }
    }

    fun boxAt(pos: Offset): MapBox? {
        val (base, scale) = viewTransform()
        if (scale <= 0f) return null
        val p = Offset((pos.x - base.x) / scale, (pos.y - base.y) / scale)
        return applyUserOffsets(map.boxes, userOffsets)
            .filter { p.x >= it.x && p.x <= it.x + it.w && p.y >= it.y && p.y <= it.y + it.h }
            .maxByOrNull { it.depth * 2 + if (it.folder) 0 else 1 }
    }

    fun dragTargetAt(pos: Offset): MapBox? {
        val (base, scale) = viewTransform()
        if (scale <= 0f) return null
        val p = Offset((pos.x - base.x) / scale, (pos.y - base.y) / scale)
        val hit = applyUserOffsets(toMap.boxes, userOffsets)
            .filter { p.x >= it.x && p.x <= it.x + it.w && p.y >= it.y && p.y <= it.y + it.h }
            .maxByOrNull { it.depth * 2 + if (it.folder) 0 else 1 }
            ?: return null
        if (!hit.folder || !hit.expanded) return hit
        val band = 8f / scale
        val onHandle = p.y < hit.y + MAP_HEADER_H ||
            p.x < hit.x + band || p.x > hit.x + hit.w - band ||
            p.y > hit.y + hit.h - band
        return if (onHandle) hit else null
    }

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant
    val folderFill = MaterialTheme.colorScheme.surfaceVariant
    val badgeFill = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
    val edgeColor = labelColor

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                var dragId: String? = null
                detectDragGestures(
                    onDragStart = { pos -> dragId = dragTargetAt(pos)?.id },
                    onDragEnd = { dragId = null },
                    onDragCancel = { dragId = null },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val id = dragId
                        if (id == null) {
                            pan += dragAmount
                        } else {
                            val (_, curScale) = viewTransform()
                            userOffsets[id] = (userOffsets[id] ?: Offset.Zero) + dragAmount / curScale
                        }
                    },
                )
            }
            .pointerInput(map, filter) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                            val pos = event.changes.firstOrNull()?.position
                            if (pos != null) menu = MapMenuTarget(boxAt(pos), pos)
                        }
                        if (event.type == PointerEventType.Scroll) {
                            val change = event.changes.firstOrNull()
                            val delta = change?.scrollDelta?.y ?: 0f
                            if (delta != 0f) {
                                val (curPan, curScale) = viewTransform()
                                val next = (curScale * if (delta > 0f) 0.9f else 1.1f)
                                    .coerceIn(fitScale * 0.3f, fitScale * 5f)
                                pan = change!!.position - (change.position - curPan) * (next / curScale)
                                scale = next
                            }
                        }
                    }
                }
            }
            .pointerInput(map, effectiveExpanded) {
                detectTapGestures(
                    onTap = { tap -> onSelect(boxAt(tap)?.id) },
                    onDoubleTap = { tap ->
                        val hit = boxAt(tap) ?: return@detectTapGestures
                        if (hit.folder) {
                            val next =
                                if (hit.expanded) effectiveExpanded - hit.id else effectiveExpanded + hit.id
                            expandOrder.remove(hit.id)
                            if (!hit.expanded) expandOrder.add(hit.id)
                            justExpandedId = if (hit.expanded) null else hit.id
                            val nextBox = applyUserOffsets(
                                buildMap(slice, next, measureWidth, userOffsets, expandOrder.toList(), justExpandedId).boxes,
                                userOffsets,
                            ).firstOrNull { it.id == hit.id }
                            if (nextBox != null) {
                                val (curPan, curScale) = viewTransform()
                                if (scale <= 0f) scale = curScale
                                panTarget = curPan + Offset(hit.x - nextBox.x, hit.y - nextBox.y) * curScale
                            }
                            onExpandedDirsChange(next)
                        } else {
                            hit.path?.let(onNodeClick)
                        }
                    },
                )
            },
    ) {
        if (toMap.boxes.isEmpty() && map.boxes.isEmpty()) return@Canvas
        val (base, viewScale) = viewTransform()
        val t = anim.value
        val fromById = fromMap.boxes.associateBy { it.id }
        val toIds = HashSet<String>(toMap.boxes.size)
        val interp = ArrayList<MapBox>(toMap.boxes.size)
        val alphas = ArrayList<Float>(toMap.boxes.size)
        for (box in toMap.boxes) {
            toIds += box.id
            val prev = fromById[box.id]
            when {
                prev == null -> {
                    interp += box
                    alphas += t
                }
                t >= 1f -> {
                    interp += box
                    alphas += 1f
                }
                else -> {
                    interp += box.copy(
                        x = prev.x + (box.x - prev.x) * t,
                        y = prev.y + (box.y - prev.y) * t,
                        w = prev.w + (box.w - prev.w) * t,
                        h = prev.h + (box.h - prev.h) * t,
                    )
                    alphas += 1f
                }
            }
        }
        if (t < 1f) {
            for (prev in fromMap.boxes) {
                if (prev.id !in toIds) {
                    interp += prev
                    alphas += 1f - t
                }
            }
        }
        val drawBoxes = applyUserOffsets(interp, userOffsets).zip(alphas)
        withTransform({
            translate(base.x, base.y)
            scale(viewScale, viewScale, Offset.Zero)
        }) {
            val byId = HashMap<String, MapBox>(drawBoxes.size)
            for ((box, _) in drawBoxes) byId[box.id] = box
            val inv = 1f / viewScale
            val fromKeys = fromMap.edges.mapTo(HashSet()) { it.from to it.to }
            val edgesToDraw = ArrayList<Pair<MapEdge, Float>>(toMap.edges.size)
            for (edge in toMap.edges) {
                edgesToDraw += edge to (if (t >= 1f || (edge.from to edge.to) in fromKeys) 1f else t)
            }
            if (t < 1f) {
                val toKeys = toMap.edges.mapTo(HashSet()) { it.from to it.to }
                for (edge in fromMap.edges) {
                    if ((edge.from to edge.to) !in toKeys) edgesToDraw += edge to (1f - t)
                }
            }
            val selectionHasEdges = selectedId != null &&
                edgesToDraw.any { (edge, _) -> edge.from == selectedId || edge.to == selectedId }
            val baseAlpha = mapEdgeBaseAlpha(edgesToDraw.size)
            val showAllBadges = edgesToDraw.size <= MAP_EDGE_BADGE_LIMIT
            for ((edge, a) in edgesToDraw) {
                val from = byId[edge.from] ?: continue
                val to = byId[edge.to] ?: continue
                val centerFrom = Offset(from.x + from.w / 2f, from.y + from.h / 2f)
                val centerTo = Offset(to.x + to.w / 2f, to.y + to.h / 2f)
                val chord = borderPoint(to, centerFrom) - borderPoint(from, centerTo)
                val chordLen = hypot(chord.x, chord.y)
                if (chordLen < 1f) continue
                val unit = Offset(chord.x / chordLen, chord.y / chordLen)
                val bow = min(chordLen * 0.16f, 40f)
                val control = (centerFrom + centerTo) / 2f + Offset(-unit.y, unit.x) * bow
                val start = borderPoint(from, control)
                val end = borderPoint(to, control)
                val stroke = (1.5f + ln(edge.weight.toFloat())).coerceAtMost(4f) * inv
                val touchesSelection = edge.from == selectedId || edge.to == selectedId
                val lineColor = when {
                    !selectionHasEdges -> edgeColor.copy(alpha = baseAlpha)
                    edge.from == selectedId -> primary.copy(alpha = 0.9f)
                    edge.to == selectedId -> tertiary.copy(alpha = 0.9f)
                    else -> edgeColor.copy(alpha = 0.15f)
                }
                val curve = Path().apply {
                    moveTo(start.x, start.y)
                    quadraticBezierTo(control.x, control.y, end.x, end.y)
                }
                drawPath(curve, lineColor.fade(a), style = Stroke(width = stroke, cap = StrokeCap.Round))
                drawMapArrow(control, end, lineColor.fade(a), inv)
                if (edge.weight > 1 && (showAllBadges || touchesSelection)) {
                    val mid = (start + end) / 4f + control / 2f
                    val text = textMeasurer.measure(AnnotatedString("${edge.weight}"), weightStyle)
                    val halfW = text.size.width / 2f + 3f
                    val halfH = text.size.height / 2f + 1f
                    drawRoundRect(
                        color = badgeFill.fade(a),
                        topLeft = mid - Offset(halfW, halfH),
                        size = Size(halfW * 2f, halfH * 2f),
                        cornerRadius = CornerRadius(halfH),
                    )
                    drawText(
                        textLayoutResult = text,
                        color = labelColor.fade(a),
                        topLeft = mid - Offset(text.size.width / 2f, text.size.height / 2f),
                    )
                }
            }
            for ((box, a) in drawBoxes) {
                val topLeft = Offset(box.x, box.y)
                val size = Size(box.w, box.h)
                when {
                    box.folder && box.expanded -> {
                        drawRoundRect(folderFill.copy(alpha = 0.16f * a), topLeft, size, CornerRadius(8f))
                        drawRoundRect(
                            color = (if (box.activeTrail) primary.copy(alpha = 0.5f) else outlineVariant).fade(a),
                            topLeft = topLeft,
                            size = size,
                            cornerRadius = CornerRadius(8f),
                            style = Stroke(width = 1f),
                        )
                        val header = textMeasurer.measure(AnnotatedString(box.label), labelStyle)
                        drawText(header, labelColor.fade(a), Offset(box.x + 8f, box.y + 2f))
                    }
                    box.folder -> {
                        drawRoundRect(secondary.copy(alpha = 0.16f * a), topLeft, size, CornerRadius(6f))
                        drawRoundRect(
                            color = if (box.activeTrail) primary.copy(alpha = 0.6f * a) else secondary.copy(alpha = 0.4f * a),
                            topLeft = topLeft,
                            size = size,
                            cornerRadius = CornerRadius(6f),
                            style = Stroke(width = if (box.activeTrail) 1.5f else 1f),
                        )
                        val text = textMeasurer.measure(AnnotatedString("${box.label} (${box.fileCount})"), labelStyle)
                        drawText(
                            textLayoutResult = text,
                            color = labelColor.fade(a),
                            topLeft = Offset(
                                box.x + (box.w - text.size.width) / 2f,
                                box.y + (box.h - text.size.height) / 2f,
                            ),
                        )
                    }
                    else -> {
                        drawRoundRect(
                            color = if (box.active) primary.copy(alpha = 0.18f * a) else folderFill.copy(alpha = 0.35f * a),
                            topLeft = topLeft,
                            size = size,
                            cornerRadius = CornerRadius(5f),
                        )
                        drawRoundRect(
                            color = (if (box.active) primary else outlineVariant).fade(a),
                            topLeft = topLeft,
                            size = size,
                            cornerRadius = CornerRadius(5f),
                            style = Stroke(width = if (box.active) 1.5f else 1f),
                        )
                        val text = textMeasurer.measure(AnnotatedString(box.label), labelStyle)
                        drawText(
                            textLayoutResult = text,
                            color = labelColor.fade(a),
                            topLeft = Offset(
                                box.x + (box.w - text.size.width) / 2f,
                                box.y + (box.h - text.size.height) / 2f,
                            ),
                        )
                    }
                }
                if (box.id == selectedId) {
                    drawRoundRect(
                        color = primary.copy(alpha = 0.9f * a),
                        topLeft = topLeft - Offset(2f, 2f),
                        size = Size(box.w + 4f, box.h + 4f),
                        cornerRadius = CornerRadius(8f),
                        style = Stroke(width = 1.5f),
                    )
                }
            }
        }
    }
    val menuTarget = menu
    if (menuTarget != null) {
        Box(
            modifier = Modifier.offset {
                IntOffset(menuTarget.pos.x.roundToInt(), menuTarget.pos.y.roundToInt())
            },
        ) {
            CompactDropdown(expanded = true, onDismissRequest = { menu = null }) {
                val box = menuTarget.box
                if (box != null && box.folder) {
                    CompactMenuItem("Show only this folder", onClick = {
                        menu = null
                        onFilterChange(filter.copy(focusDir = box.id))
                    })
                    CompactMenuItem("Hide this folder", onClick = {
                        menu = null
                        onFilterChange(filter.copy(hiddenDirs = filter.hiddenDirs + box.id))
                    })
                    CompactMenuItem("Hide this folder's dependencies", onClick = {
                        menu = null
                        onFilterChange(filter.copy(mutedDirs = filter.mutedDirs + box.id))
                    })
                }
                if (filter.active) {
                    CompactMenuItem("Show everything", onClick = {
                        menu = null
                        onFilterChange(MapFilterState())
                    })
                }
            }
        }
    }
}

internal const val MAP_EDGE_BADGE_LIMIT = 24

internal fun mapEdgeBaseAlpha(edgeCount: Int): Float =
    0.8f - 0.5f * ((edgeCount - 12f) / 48f).coerceIn(0f, 1f)

private data class MapMenuTarget(val box: MapBox?, val pos: Offset)

private fun lerpModel(from: MapModel, to: MapModel, t: Float): MapModel {
    if (t >= 1f) return to
    val fromById = from.boxes.associateBy { it.id }
    val boxes = to.boxes.map { box ->
        val prev = fromById[box.id] ?: return@map box
        box.copy(
            x = prev.x + (box.x - prev.x) * t,
            y = prev.y + (box.y - prev.y) * t,
            w = prev.w + (box.w - prev.w) * t,
            h = prev.h + (box.h - prev.h) * t,
        )
    }
    return MapModel(boxes, to.edges, to.width, to.height)
}

private fun Color.fade(f: Float): Color = if (f >= 1f) this else copy(alpha = alpha * f)

private fun DrawScope.drawMapArrow(from: Offset, to: Offset, color: Color, inv: Float) {
    val direction = to - from
    val length = hypot(direction.x, direction.y)
    if (length < 0.5f) return
    val unit = Offset(direction.x / length, direction.y / length)
    val headLen = min(11f * inv, length * 0.45f)
    val base = to - unit * headLen
    val normal = Offset(-unit.y, unit.x) * headLen * 0.45f
    val head = Path().apply {
        moveTo(to.x, to.y)
        lineTo(base.x + normal.x, base.y + normal.y)
        lineTo(base.x - normal.x, base.y - normal.y)
        close()
    }
    drawPath(head, color)
}

private fun borderPoint(box: MapBox, towards: Offset): Offset {
    val center = Offset(box.x + box.w / 2f, box.y + box.h / 2f)
    val d = towards - center
    if (abs(d.x) < 0.001f && abs(d.y) < 0.001f) return center
    val tx = if (d.x != 0f) (box.w / 2f) / abs(d.x) else Float.MAX_VALUE
    val ty = if (d.y != 0f) (box.h / 2f) / abs(d.y) else Float.MAX_VALUE
    return center + d * min(tx, ty)
}
