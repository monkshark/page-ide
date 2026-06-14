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
import androidx.compose.ui.graphics.PathEffect
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
    view: MapViewState,
    vcsMarks: Map<String, VcsMark> = emptyMap(),
    vcsImpacted: Map<String, Int> = emptyMap(),
    activeId: String? = null,
    tracePath: List<String> = emptyList(),
    onTracePath: ((String) -> Unit)? = null,
) {
    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)
    val weightStyle = TextStyle(fontSize = 9.sp, color = labelColor)
    val filter = view.filter
    val effectiveExpanded = view.expandedDirs ?: remember(slice) { defaultExpandedDirs(slice) }
    val measureWidth: (String) -> Float =
        { textMeasurer.measure(AnnotatedString(it), labelStyle).size.width.toFloat() }
    val userOffsets = view.userOffsets
    val expandOrder = view.expandOrder
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
    var pan by view::pan
    var scale by view::scale
    var fitScale by remember { mutableStateOf(1f) }
    var fitted by view::fitted

    fun fitTransform(): Pair<Offset, Float> {
        val cw = canvasSize.width.toFloat()
        val ch = canvasSize.height.toFloat()
        if (cw <= 0f || ch <= 0f || map.width <= 0f || map.height <= 0f) return Offset.Zero to 1f
        val fit = min(cw / (map.width + 48f), ch / (map.height + 48f)).coerceAtMost(1.4f)
        return Offset((cw - map.width * fit) / 2f, (ch - map.height * fit) / 2f) to fit
    }

    fun viewTransform(): Pair<Offset, Float> = if (scale > 0f) pan to scale else fitTransform()

    LaunchedEffect(slice, canvasSize, fitted) {
        if (canvasSize.width <= 0 || map.width <= 0f) return@LaunchedEffect
        val (p, s) = fitTransform()
        fitScale = s
        if (fitted) return@LaunchedEffect
        pan = p
        scale = s
        fitted = true
    }

    LaunchedEffect(map, view.focusCenterId, canvasSize) {
        val focusId = view.focusCenterId ?: return@LaunchedEffect
        if (canvasSize.width <= 0 || map.width <= 0f) return@LaunchedEffect
        val box = applyUserOffsets(map.boxes, userOffsets).firstOrNull { it.id == focusId }
        if (box == null) {
            val next = effectiveExpanded + ancestorDirIds(focusId)
            if (next != effectiveExpanded) view.expandedDirs = next else view.focusCenterId = null
            return@LaunchedEffect
        }
        val (_, curScale) = viewTransform()
        pan = Offset(
            canvasSize.width / 2f - (box.x + box.w / 2f) * curScale,
            canvasSize.height / 2f - (box.y + box.h / 2f) * curScale,
        )
        if (scale <= 0f) scale = curScale
        fitted = true
        view.focusCenterId = null
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
    val errorColor = MaterialTheme.colorScheme.error
    val cycleKeys = remember(toMap) { mapCycleEdges(toMap.edges) }
    val traceKeys = remember(toMap, tracePath) { traceEdgeKeys(toMap.boxes, tracePath) }
    val vcsCounts = remember(map, vcsMarks) {
        vcsFolderCounts(vcsMarks, map.boxes.filter { it.folder }.map { it.id })
    }
    val vcsBadgeStyle = TextStyle(fontSize = 9.sp)
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
                                panTarget = minimalPanInto(curPan, nextBox, curScale, canvasSize)
                            }
                            view.expandedDirs = next
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
        val neighbors = mapNeighbors(toMap.edges, selectedId)
        val focusActive = selectedId != null && neighbors.any
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
                val inCycle = (edge.from to edge.to) in cycleKeys
                val onTrace = (edge.from to edge.to) in traceKeys
                val lineColor = when {
                    onTrace -> tertiary.copy(alpha = 0.95f)
                    inCycle && !focusActive -> errorColor.copy(alpha = 0.85f)
                    !focusActive -> edgeColor.copy(alpha = baseAlpha)
                    edge.from == selectedId -> primary.copy(alpha = 0.9f)
                    edge.to == selectedId -> tertiary.copy(alpha = 0.9f)
                    inCycle -> errorColor.copy(alpha = 0.35f)
                    else -> edgeColor.copy(alpha = 0.15f)
                }
                val curve = Path().apply {
                    moveTo(start.x, start.y)
                    quadraticTo(control.x, control.y, end.x, end.y)
                }
                drawPath(
                    curve,
                    lineColor.fade(a),
                    style = Stroke(
                        width = if (onTrace) maxOf(stroke, 3f * inv) else stroke,
                        cap = StrokeCap.Round,
                        pathEffect = if (inCycle) {
                            PathEffect.dashPathEffect(floatArrayOf(7f * inv, 5f * inv))
                        } else {
                            null
                        },
                    ),
                )
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
                val dimmed = focusActive && !(box.folder && box.expanded) &&
                    box.id !in view.pinnedIds &&
                    isMapBoxDimmed(box.id, selectedId, neighbors)
                val boxA = if (dimmed) a * 0.35f else a
                val emphasis = when {
                    box.id in neighbors.dependents -> tertiary
                    box.id in neighbors.dependencies -> primary
                    else -> null
                }
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
                        drawRoundRect(secondary.copy(alpha = 0.16f * boxA), topLeft, size, CornerRadius(6f))
                        drawRoundRect(
                            color = emphasis?.copy(alpha = 0.8f * boxA)
                                ?: if (box.activeTrail) primary.copy(alpha = 0.6f * boxA) else secondary.copy(alpha = 0.4f * boxA),
                            topLeft = topLeft,
                            size = size,
                            cornerRadius = CornerRadius(6f),
                            style = Stroke(width = if (emphasis != null || box.activeTrail) 1.5f else 1f),
                        )
                        val text = textMeasurer.measure(AnnotatedString("${box.label} (${box.fileCount})"), labelStyle)
                        drawText(
                            textLayoutResult = text,
                            color = labelColor.fade(boxA),
                            topLeft = Offset(
                                box.x + (box.w - text.size.width) / 2f,
                                box.y + (box.h - text.size.height) / 2f,
                            ),
                        )
                    }
                    else -> {
                        drawRoundRect(
                            color = if (box.active) primary.copy(alpha = 0.18f * boxA) else folderFill.copy(alpha = 0.35f * boxA),
                            topLeft = topLeft,
                            size = size,
                            cornerRadius = CornerRadius(5f),
                        )
                        drawRoundRect(
                            color = (emphasis ?: if (box.active) primary else outlineVariant).fade(boxA),
                            topLeft = topLeft,
                            size = size,
                            cornerRadius = CornerRadius(5f),
                            style = Stroke(width = if (emphasis != null || box.active) 1.5f else 1f),
                        )
                        val text = textMeasurer.measure(AnnotatedString(box.label), labelStyle)
                        drawText(
                            textLayoutResult = text,
                            color = labelColor.fade(boxA),
                            topLeft = Offset(
                                box.x + (box.w - text.size.width) / 2f,
                                box.y + (box.h - text.size.height) / 2f,
                            ),
                        )
                    }
                }
                if (!box.folder && box.id in view.pinnedIds) {
                    drawCircle(
                        color = tertiary.copy(alpha = boxA),
                        radius = 2.5f,
                        center = Offset(box.x + 6f, box.y + 6f),
                    )
                }
                val impactDepth = if (box.folder) null else vcsImpacted[box.id]
                if (impactDepth != null) {
                    drawCircle(
                        color = vcsImpactColor.copy(alpha = boxA * if (impactDepth <= 1) 0.9f else 0.45f),
                        radius = 3f,
                        center = Offset(box.x + box.w - 6f, box.y + 6f),
                        style = Stroke(width = 1.5f),
                    )
                }
                val mark = if (box.folder) null else vcsMarks[box.id]
                if (mark != null) {
                    drawCircle(
                        color = vcsColor(mark).copy(alpha = boxA),
                        radius = 3f,
                        center = Offset(box.x + box.w - 6f, box.y + 6f),
                    )
                }
                val changed = if (box.folder) vcsCounts[box.id] ?: 0 else 0
                if (changed > 0) {
                    val text = textMeasurer.measure(AnnotatedString("$changed"), vcsBadgeStyle)
                    val badgeW = text.size.width + 8f
                    val badgeH = text.size.height + 2f
                    val badgeTopLeft = Offset(box.x + box.w - badgeW - 4f, box.y + 2f)
                    drawRoundRect(
                        color = vcsColor(VcsMark.MODIFIED).copy(alpha = 0.85f * boxA),
                        topLeft = badgeTopLeft,
                        size = Size(badgeW, badgeH),
                        cornerRadius = CornerRadius(badgeH / 2f),
                    )
                    drawText(
                        textLayoutResult = text,
                        color = Color.White.copy(alpha = boxA),
                        topLeft = Offset(badgeTopLeft.x + 4f, badgeTopLeft.y + 1f),
                    )
                }
                if (box.id == selectedId) {
                    drawRoundRect(
                        color = primary.copy(alpha = 0.9f * a),
                        topLeft = topLeft - Offset(2f, 2f),
                        size = Size(box.w + 4f, box.h + 4f),
                        cornerRadius = CornerRadius(8f),
                        style = Stroke(width = 1.5f),
                    )
                } else if (!box.folder && box.id == activeId) {
                    drawRoundRect(
                        color = tertiary.copy(alpha = 0.8f * a),
                        topLeft = topLeft - Offset(2f, 2f),
                        size = Size(box.w + 4f, box.h + 4f),
                        cornerRadius = CornerRadius(8f),
                        style = Stroke(
                            width = 1.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f)),
                        ),
                    )
                }
            }
        }
        if (focusActive) {
            val selBox = drawBoxes.firstOrNull { it.first.id == selectedId }?.first
            if (selBox != null) {
                val title = textMeasurer.measure(AnnotatedString(selBox.label), labelStyle)
                val inText = textMeasurer.measure(
                    AnnotatedString("used by ${neighbors.dependents.size}"),
                    labelStyle,
                )
                val outText = textMeasurer.measure(
                    AnnotatedString("uses ${neighbors.dependencies.size}"),
                    labelStyle,
                )
                val gap = 10f
                val padX = 8f
                val padY = 3f
                val w = title.size.width + inText.size.width + outText.size.width + gap * 2 + padX * 2
                val h = maxOf(title.size.height, inText.size.height, outText.size.height) + padY * 2
                drawRoundRect(badgeFill, Offset(8f, 8f), Size(w, h), CornerRadius(h / 2f))
                var tx = 8f + padX
                drawText(title, labelColor, Offset(tx, 8f + padY))
                tx += title.size.width + gap
                drawText(inText, tertiary, Offset(tx, 8f + padY))
                tx += inText.size.width + gap
                drawText(outText, primary, Offset(tx, 8f + padY))
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
                if (onTracePath != null && box != null && !box.folder &&
                    selectedId != null && box.id != selectedId
                ) {
                    CompactMenuItem("Trace path from selection", onClick = {
                        menu = null
                        onTracePath(box.id)
                    })
                }
                if (box != null && !box.folder) {
                    CompactMenuItem(
                        if (box.id in view.pinnedIds) "Unpin node" else "Pin node",
                        onClick = {
                            menu = null
                            view.togglePin(box.id)
                        },
                    )
                }
                if (box != null && box.folder) {
                    CompactMenuItem("Show only this folder", onClick = {
                        menu = null
                        view.filter = filter.copy(focusDir = box.id)
                    })
                    CompactMenuItem("Hide this folder", onClick = {
                        menu = null
                        view.filter = filter.copy(hiddenDirs = filter.hiddenDirs + box.id)
                    })
                    CompactMenuItem("Hide this folder's dependencies", onClick = {
                        menu = null
                        view.filter = filter.copy(mutedDirs = filter.mutedDirs + box.id)
                    })
                }
                if (filter.active) {
                    CompactMenuItem("Show everything", onClick = {
                        menu = null
                        view.filter = MapFilterState()
                    })
                }
                CompactMenuItem("Reset view", onClick = {
                    menu = null
                    justExpandedId = null
                    onSelect(null)
                    view.reset()
                })
            }
        }
    }
}

internal const val MAP_EDGE_BADGE_LIMIT = 24

internal fun traceEdgeKeys(boxes: List<MapBox>, path: List<String>): Set<Pair<String, String>> {
    if (path.size < 2) return emptySet()
    fun representative(id: String): String? =
        boxes.filter { belongsTo(id, it.id) }.maxByOrNull { it.depth }?.id
    val keys = HashSet<Pair<String, String>>()
    for (i in 0 until path.size - 1) {
        val from = representative(path[i]) ?: continue
        val to = representative(path[i + 1]) ?: continue
        if (from != to) keys += from to to
    }
    return keys
}

internal fun mapEdgeBaseAlpha(edgeCount: Int): Float =
    0.8f - 0.5f * ((edgeCount - 12f) / 48f).coerceIn(0f, 1f)

internal fun minimalPanInto(pan: Offset, box: MapBox, scale: Float, canvas: IntSize): Offset? {
    if (canvas.width <= 0 || canvas.height <= 0 || scale <= 0f) return null
    val margin = 24f
    fun axis(start: Float, size: Float, view: Float): Float = when {
        size >= view - margin * 2f -> margin - start
        start < margin -> margin - start
        start + size > view - margin -> (view - margin) - (start + size)
        else -> 0f
    }
    val dx = axis(pan.x + box.x * scale, box.w * scale, canvas.width.toFloat())
    val dy = axis(pan.y + box.y * scale, box.h * scale, canvas.height.toFloat())
    return if (dx == 0f && dy == 0f) null else pan + Offset(dx, dy)
}

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
