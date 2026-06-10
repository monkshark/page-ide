package page.atlas.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import java.nio.file.Path as FilePath
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.min
import page.atlas.graph.GraphSlice

@Composable
internal fun MapCanvas(
    slice: GraphSlice,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onNodeClick: (FilePath) -> Unit,
    expandedDirs: Set<String>?,
    onExpandedDirsChange: (Set<String>) -> Unit,
) {
    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)
    val weightStyle = TextStyle(fontSize = 9.sp, color = labelColor)
    val effectiveExpanded = expandedDirs ?: remember(slice) { defaultExpandedDirs(slice) }
    val map = remember(slice, effectiveExpanded) {
        buildMap(slice, effectiveExpanded) { textMeasurer.measure(AnnotatedString(it), labelStyle).size.width.toFloat() }
    }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var zoomUser by remember { mutableStateOf(1f) }

    fun viewTransform(): Pair<Offset, Float> {
        val cw = canvasSize.width.toFloat()
        val ch = canvasSize.height.toFloat()
        if (cw <= 0f || ch <= 0f || map.width <= 0f || map.height <= 0f) return Offset.Zero to 1f
        val fit = min(cw / (map.width + 48f), ch / (map.height + 48f)).coerceAtMost(1.4f)
        val scale = fit * zoomUser
        val base = Offset((cw - map.width * scale) / 2f, (ch - map.height * scale) / 2f) + pan
        return base to scale
    }

    fun boxAt(pos: Offset): MapBox? {
        val (base, scale) = viewTransform()
        if (scale <= 0f) return null
        val p = Offset((pos.x - base.x) / scale, (pos.y - base.y) / scale)
        return map.boxes
            .filter { p.x >= it.x && p.x <= it.x + it.w && p.y >= it.y && p.y <= it.y + it.h }
            .maxByOrNull { it.depth * 2 + if (it.folder) 0 else 1 }
    }

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant
    val folderFill = MaterialTheme.colorScheme.surfaceVariant
    val edgeColor = labelColor.copy(alpha = 0.5f)

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    pan += dragAmount
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                            if (delta != 0f) {
                                zoomUser = (zoomUser * if (delta > 0f) 0.9f else 1.1f).coerceIn(0.3f, 5f)
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
                            onExpandedDirsChange(
                                if (hit.expanded) effectiveExpanded - hit.id else effectiveExpanded + hit.id,
                            )
                        } else {
                            hit.path?.let(onNodeClick)
                        }
                    },
                )
            },
    ) {
        if (map.boxes.isEmpty()) return@Canvas
        val (base, scale) = viewTransform()
        withTransform({
            translate(base.x, base.y)
            scale(scale, scale, Offset.Zero)
        }) {
            for (box in map.boxes) {
                val topLeft = Offset(box.x, box.y)
                val size = Size(box.w, box.h)
                when {
                    box.folder && box.expanded -> {
                        drawRoundRect(folderFill.copy(alpha = 0.16f), topLeft, size, CornerRadius(8f))
                        drawRoundRect(
                            color = if (box.activeTrail) primary.copy(alpha = 0.5f) else outlineVariant,
                            topLeft = topLeft,
                            size = size,
                            cornerRadius = CornerRadius(8f),
                            style = Stroke(width = 1f),
                        )
                        val header = textMeasurer.measure(AnnotatedString(box.label), labelStyle)
                        drawText(header, labelColor, Offset(box.x + 8f, box.y + 2f))
                    }
                    box.folder -> {
                        drawRoundRect(secondary.copy(alpha = 0.16f), topLeft, size, CornerRadius(6f))
                        drawRoundRect(
                            color = if (box.activeTrail) primary.copy(alpha = 0.6f) else secondary.copy(alpha = 0.4f),
                            topLeft = topLeft,
                            size = size,
                            cornerRadius = CornerRadius(6f),
                            style = Stroke(width = if (box.activeTrail) 1.5f else 1f),
                        )
                        val text = textMeasurer.measure(AnnotatedString("${box.label} (${box.fileCount})"), labelStyle)
                        drawText(
                            textLayoutResult = text,
                            color = labelColor,
                            topLeft = Offset(
                                box.x + (box.w - text.size.width) / 2f,
                                box.y + (box.h - text.size.height) / 2f,
                            ),
                        )
                    }
                    else -> {
                        drawRoundRect(
                            color = if (box.active) primary.copy(alpha = 0.18f) else folderFill.copy(alpha = 0.35f),
                            topLeft = topLeft,
                            size = size,
                            cornerRadius = CornerRadius(5f),
                        )
                        drawRoundRect(
                            color = if (box.active) primary else outlineVariant,
                            topLeft = topLeft,
                            size = size,
                            cornerRadius = CornerRadius(5f),
                            style = Stroke(width = if (box.active) 1.5f else 1f),
                        )
                        val text = textMeasurer.measure(AnnotatedString(box.label), labelStyle)
                        drawText(
                            textLayoutResult = text,
                            color = labelColor,
                            topLeft = Offset(
                                box.x + (box.w - text.size.width) / 2f,
                                box.y + (box.h - text.size.height) / 2f,
                            ),
                        )
                    }
                }
                if (box.id == selectedId) {
                    drawRoundRect(
                        color = primary.copy(alpha = 0.9f),
                        topLeft = topLeft - Offset(2f, 2f),
                        size = Size(box.w + 4f, box.h + 4f),
                        cornerRadius = CornerRadius(8f),
                        style = Stroke(width = 1.5f),
                    )
                }
            }
            val byId = map.boxes.associateBy { it.id }
            for (edge in map.edges) {
                val from = byId[edge.from] ?: continue
                val to = byId[edge.to] ?: continue
                val centerFrom = Offset(from.x + from.w / 2f, from.y + from.h / 2f)
                val centerTo = Offset(to.x + to.w / 2f, to.y + to.h / 2f)
                val start = borderPoint(from, centerTo)
                val end = borderPoint(to, centerFrom)
                val stroke = (1f + ln(edge.weight.toFloat())).coerceAtMost(3.5f)
                drawLine(edgeColor, start, end, strokeWidth = stroke)
                drawArrowHead(start, end, 0f, edgeColor, filled = true)
                if (edge.weight > 1) {
                    val mid = (start + end) / 2f
                    val text = textMeasurer.measure(AnnotatedString("${edge.weight}"), weightStyle)
                    drawText(
                        textLayoutResult = text,
                        color = labelColor,
                        topLeft = mid - Offset(text.size.width / 2f, text.size.height / 2f),
                    )
                }
            }
        }
    }
}

private fun borderPoint(box: MapBox, towards: Offset): Offset {
    val center = Offset(box.x + box.w / 2f, box.y + box.h / 2f)
    val d = towards - center
    if (abs(d.x) < 0.001f && abs(d.y) < 0.001f) return center
    val tx = if (d.x != 0f) (box.w / 2f) / abs(d.x) else Float.MAX_VALUE
    val ty = if (d.y != 0f) (box.h / 2f) / abs(d.y) else Float.MAX_VALUE
    return center + d * min(tx, ty)
}
