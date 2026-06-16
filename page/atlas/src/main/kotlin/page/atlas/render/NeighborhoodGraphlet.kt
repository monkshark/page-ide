package page.atlas.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import java.nio.file.Path as FilePath
import kotlin.math.hypot
import kotlinx.coroutines.withTimeoutOrNull
import page.atlas.graph.Neighbor
import page.atlas.graph.Neighborhood
import page.ui.Glass

private data class GraphletSlot(val neighbor: Neighbor, val rect: Rect, val incoming: Boolean)

private const val FocusRadius = 22f

@Composable
internal fun NeighborhoodGraphlet(
    neighborhood: Neighborhood,
    onRefocus: (String) -> Unit,
    onOpen: (FilePath) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val colors = Glass.colors
    val nameStyle = TextStyle(fontSize = 10.sp, color = colors.text)
    val capStyle = TextStyle(fontSize = 9.sp, color = colors.muted)
    val focusStyle = TextStyle(fontSize = 11.sp, color = colors.text, fontWeight = FontWeight.SemiBold)

    var size by remember { mutableStateOf(IntSize.Zero) }
    val slots = remember(neighborhood, size) { layoutSlots(neighborhood, size) }

    fun hit(pos: Offset): GraphletSlot? = slots.firstOrNull { it.rect.contains(pos) }

    Canvas(
        modifier
            .onSizeChanged { size = it }
            .pointerInput(neighborhood) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                    val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                        awaitFirstDown(requireUnconsumed = false)
                    }
                    if (second == null) {
                        hit(up.position)?.let { onRefocus(it.neighbor.node.id) }
                    } else {
                        val up2 = waitForUpOrCancellation()
                        val pos = up2?.position ?: second.position
                        hit(pos)?.neighbor?.node?.path?.let(onOpen)
                    }
                }
            },
    ) {
        val focus = neighborhood.focus ?: return@Canvas
        val w = this.size.width
        val h = this.size.height
        if (neighborhood.incoming.isEmpty() && neighborhood.outgoing.isEmpty()) {
            val msg = textMeasurer.measure(AnnotatedString("No direct dependencies"), capStyle)
            drawText(msg, topLeft = Offset(w / 2f - msg.size.width / 2f, h / 2f - msg.size.height / 2f))
            return@Canvas
        }
        val center = Offset(w / 2f, h / 2f)

        for (slot in slots) {
            val color = (if (slot.incoming) colors.danger else colors.primary).copy(alpha = 0.45f)
            if (slot.incoming) {
                drawGraphletEdge(
                    from = Offset(slot.rect.right, slot.rect.center.y),
                    to = Offset(center.x - FocusRadius - 2f, center.y),
                    color = color,
                )
            } else {
                drawGraphletEdge(
                    from = Offset(center.x + FocusRadius + 2f, center.y),
                    to = Offset(slot.rect.left, slot.rect.center.y),
                    color = color,
                )
            }
        }

        for (slot in slots) {
            val side = if (slot.incoming) colors.danger else colors.primary
            drawRoundRect(
                color = colors.surfaceOverlay,
                topLeft = slot.rect.topLeft,
                size = slot.rect.size,
                cornerRadius = CornerRadius(5f, 5f),
            )
            drawRoundRect(
                color = side.copy(alpha = 0.5f),
                topLeft = slot.rect.topLeft,
                size = slot.rect.size,
                cornerRadius = CornerRadius(5f, 5f),
                style = Stroke(width = 1f),
            )
            drawCircle(
                color = tierColor(colors, slot.neighbor.weight),
                radius = 3f,
                center = Offset(slot.rect.left + 9f, slot.rect.center.y),
            )
            val label = shorten(slot.neighbor.node.label, ((slot.rect.width - 24f) / 5.4f).toInt().coerceAtLeast(6))
            val measured = textMeasurer.measure(AnnotatedString(label), nameStyle)
            drawText(
                measured,
                topLeft = Offset(slot.rect.left + 16f, slot.rect.center.y - measured.size.height / 2f),
            )
        }

        drawCircle(color = colors.primary.copy(alpha = 0.12f), radius = FocusRadius, center = center)
        drawCircle(color = colors.primary.copy(alpha = 0.9f), radius = FocusRadius, center = center, style = Stroke(width = 1.5f))
        val focusLabel = shorten(focus.label.substringBeforeLast('.'), 8)
        val focusMeasured = textMeasurer.measure(AnnotatedString(focusLabel), focusStyle)
        drawText(
            focusMeasured,
            topLeft = Offset(center.x - focusMeasured.size.width / 2f, center.y - focusMeasured.size.height / 2f),
        )
        val caption = "${neighborhood.incomingTotal} in · ${neighborhood.outgoingTotal} out"
        val capMeasured = textMeasurer.measure(AnnotatedString(caption), capStyle)
        drawText(capMeasured, topLeft = Offset(center.x - capMeasured.size.width / 2f, center.y + FocusRadius + 4f))
        if (neighborhood.inCycle) {
            val cyc = textMeasurer.measure(AnnotatedString("⟳ in cycle"), capStyle.copy(color = colors.danger))
            drawText(cyc, topLeft = Offset(center.x - cyc.size.width / 2f, center.y + FocusRadius + 18f))
        }

        drawColumnCaption(textMeasurer, "DEPENDENTS ↑", colors.danger, Offset(14f, 8f))
        val importsCap = textMeasurer.measure(AnnotatedString("IMPORTS ↓"), capStyle.copy(color = colors.primary))
        drawText(importsCap, topLeft = Offset(w - 14f - importsCap.size.width, 8f))

        val overflowStyle = capStyle.copy(color = colors.faint)
        drawOverflow(textMeasurer, overflowStyle, neighborhood.incomingTotal - neighborhood.incoming.size, slots.filter { it.incoming })
        drawOverflow(textMeasurer, overflowStyle, neighborhood.outgoingTotal - neighborhood.outgoing.size, slots.filterNot { it.incoming })
    }
}

private fun DrawScope.drawColumnCaption(
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    color: Color,
    topLeft: Offset,
) {
    val measured = textMeasurer.measure(AnnotatedString(text), TextStyle(fontSize = 9.sp, color = color, fontWeight = FontWeight.SemiBold))
    drawText(measured, topLeft = topLeft)
}

private fun DrawScope.drawOverflow(
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    style: TextStyle,
    hidden: Int,
    column: List<GraphletSlot>,
) {
    if (hidden <= 0 || column.isEmpty()) return
    val last = column.maxByOrNull { it.rect.bottom } ?: return
    val measured = textMeasurer.measure(AnnotatedString("+$hidden more"), style)
    drawText(measured, topLeft = Offset(last.rect.left + 16f, last.rect.bottom + 6f))
}

private fun DrawScope.drawGraphletEdge(from: Offset, to: Offset, color: Color) {
    val chord = to - from
    val len = hypot(chord.x, chord.y)
    if (len < 1f) return
    val unit = Offset(chord.x / len, chord.y / len)
    val bow = (len * 0.12f).coerceAtMost(18f)
    val control = (from + to) / 2f + Offset(-unit.y, unit.x) * bow
    val curve = Path().apply {
        moveTo(from.x, from.y)
        quadraticTo(control.x, control.y, to.x, to.y)
    }
    drawPath(curve, color, style = Stroke(width = 1.4f, cap = StrokeCap.Round))
    val headLen = 6f
    val headBase = to - unit * headLen
    val normal = Offset(-unit.y, unit.x) * headLen * 0.5f
    val head = Path().apply {
        moveTo(to.x, to.y)
        lineTo(headBase.x + normal.x, headBase.y + normal.y)
        lineTo(headBase.x - normal.x, headBase.y - normal.y)
        close()
    }
    drawPath(head, color)
}

private fun tierColor(colors: page.ui.GlassColors, weight: Int): Color = when {
    weight >= 8 -> colors.danger
    weight >= 3 -> colors.warn
    weight > 0 -> colors.primary
    else -> colors.faint
}

private fun layoutSlots(hood: Neighborhood, size: IntSize): List<GraphletSlot> {
    if (size.width <= 0 || size.height <= 0) return emptyList()
    val w = size.width.toFloat()
    val h = size.height.toFloat()
    val margin = 14f
    val pillH = 22f
    val gap = 8f
    val pillW = (w * 0.32f).coerceIn(70f, 150f)
    val cy = h / 2f
    fun column(neighbors: List<Neighbor>, incoming: Boolean): List<GraphletSlot> {
        if (neighbors.isEmpty()) return emptyList()
        val n = neighbors.size
        val totalH = n * pillH + (n - 1) * gap
        val top = (cy - totalH / 2f).coerceAtLeast(margin + 14f)
        val left = if (incoming) margin else w - margin - pillW
        return neighbors.mapIndexed { i, nb ->
            val y = top + i * (pillH + gap)
            GraphletSlot(nb, Rect(left, y, left + pillW, y + pillH), incoming)
        }
    }
    return column(hood.incoming, true) + column(hood.outgoing, false)
}

private fun shorten(text: String, max: Int): String =
    if (text.length <= max) text else text.take((max - 1).coerceAtLeast(1)) + "…"
