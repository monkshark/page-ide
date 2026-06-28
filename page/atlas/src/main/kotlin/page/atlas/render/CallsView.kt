package page.atlas.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import page.atlas.graph.GraphInsights
import page.atlas.graph.GraphNode
import page.atlas.graph.GraphSlice
import page.atlas.graph.Neighbor
import page.atlas.graph.Neighborhood
import page.ui.EditorFontFamily

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CallsView(
    slice: GraphSlice,
    focusId: String?,
    onSelect: (String) -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = focusId?.takeIf { id -> slice.nodes.any { it.id == id } }
        ?: slice.nodes.firstOrNull()?.id
        ?: return
    val neighborhood = remember(slice, focus) { GraphInsights.neighborhood(slice, focus, limit = 64) }
    val focusNode = neighborhood.focus ?: return
    var bandOpen by remember { mutableStateOf(true) }

    Column(modifier.fillMaxSize()) {
        FocusHeader(focusNode, neighborhood, onOpen)
        ThinDivider()
        BandToggle(bandOpen) { bandOpen = !bandOpen }
        if (bandOpen) {
            EgoBand(focusNode, neighborhood, onSelect)
            ThinDivider()
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 4.dp),
        ) {
            GroupHeader("CALLERS", neighborhood.incomingTotal, AtlasInk.incoming)
            if (neighborhood.incoming.isEmpty()) {
                EmptyRow("No callers in view")
            } else {
                neighborhood.incoming.forEach { CallRow(it, AtlasInk.incoming, onSelect, onOpen) }
                MoreRow(neighborhood.incomingTotal - neighborhood.incoming.size)
            }
            Spacer(Modifier.height(8.dp))
            GroupHeader("CALLS", neighborhood.outgoingTotal, AtlasInk.outgoing)
            if (neighborhood.outgoing.isEmpty()) {
                EmptyRow("No calls in view")
            } else {
                neighborhood.outgoing.forEach { CallRow(it, AtlasInk.outgoing, onSelect, onOpen) }
                MoreRow(neighborhood.outgoingTotal - neighborhood.outgoing.size)
            }
        }
        ThinDivider()
        Text(
            text = "depth ≤ 2 · click to refocus · double-click to open",
            style = TextStyle(fontSize = 9.5.sp, color = AtlasInk.dim, fontFamily = EditorFontFamily),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun FocusHeader(focus: GraphNode, neighborhood: Neighborhood, onOpen: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable { onOpen(focus.id) }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(AtlasInk.label.copy(alpha = 0.6f)),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = signature(focus),
                style = TextStyle(
                    fontSize = 14.sp,
                    color = AtlasInk.bright,
                    fontFamily = EditorFontFamily,
                    fontWeight = FontWeight.SemiBold,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = location(focus),
                style = TextStyle(fontSize = 11.sp, color = AtlasInk.dim, fontFamily = EditorFontFamily),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (neighborhood.inCycle) HeaderChip("⟳ cycle", AtlasInk.cycle)
        Spacer(Modifier.width(6.dp))
        CountChip("↑", neighborhood.incomingTotal, AtlasInk.incoming)
        Spacer(Modifier.width(4.dp))
        CountChip("↓", neighborhood.outgoingTotal, AtlasInk.outgoing)
    }
}

@Composable
private fun HeaderChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(text = text, style = TextStyle(fontSize = 10.sp, color = color, fontFamily = EditorFontFamily))
    }
}

@Composable
private fun CountChip(arrow: String, count: Int, color: Color) {
    Text(
        text = "$arrow$count",
        style = TextStyle(fontSize = 11.sp, color = color, fontFamily = EditorFontFamily),
    )
}

@Composable
private fun BandToggle(open: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .clickable { onToggle() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (open) "▾  DIAGRAM" else "▸  DIAGRAM",
            style = TextStyle(
                fontSize = 11.sp,
                color = AtlasInk.dim,
                fontFamily = EditorFontFamily,
                fontWeight = FontWeight.SemiBold,
            ),
        )
        Box(Modifier.weight(1f))
        Text(
            text = if (open) "collapse" else "expand",
            style = TextStyle(fontSize = 9.5.sp, color = AtlasInk.sub, fontFamily = EditorFontFamily),
        )
    }
}

@Composable
private fun EgoBand(focus: GraphNode, neighborhood: Neighborhood, onSelect: (String) -> Unit) {
    val measurer = rememberTextMeasurer()
    val cardStyle = remember { TextStyle(fontSize = 10.5.sp, color = AtlasInk.label, fontFamily = EditorFontFamily) }
    val focusStyle = remember {
        TextStyle(fontSize = 11.sp, color = AtlasInk.bright, fontFamily = EditorFontFamily, fontWeight = FontWeight.SemiBold)
    }
    val moreStyle = remember { TextStyle(fontSize = 9.sp, color = AtlasInk.dim, fontFamily = EditorFontFamily) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val layout = remember(canvasSize, neighborhood) {
        buildCallsBand(canvasSize.width.toFloat(), canvasSize.height.toFloat(), neighborhood)
    }
    Box(Modifier.fillMaxWidth().height(156.dp).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(layout) {
                    detectTapGestures(
                        onTap = { pos ->
                            layout.cards.firstOrNull { it.node != null && it.rect.contains(pos) }
                                ?.node?.let { onSelect(it.id) }
                        },
                    )
                },
        ) {
            if (layout.focus == Rect.Zero) return@Canvas
            val panel = CornerRadius(8f, 8f)
            drawRoundRect(AtlasInk.boxFill, size = size, cornerRadius = panel)
            drawRoundRect(AtlasInk.nodeStroke, size = size, cornerRadius = panel, style = Stroke(1f))

            val fLeft = Offset(layout.focus.left, layout.focus.center.y)
            val fRight = Offset(layout.focus.right, layout.focus.center.y)
            var hasCaller = false
            for (card in layout.cards) {
                if (card.node == null) continue
                if (card.side < 0) {
                    drawBandConnector(Offset(card.rect.right, card.rect.center.y), fLeft, AtlasInk.incoming)
                    hasCaller = true
                } else {
                    val end = Offset(card.rect.left, card.rect.center.y)
                    drawBandConnector(fRight, end, AtlasInk.outgoing)
                    drawBandArrow(end, AtlasInk.outgoing)
                }
            }
            if (hasCaller) drawBandArrow(fLeft, AtlasInk.incoming)

            for (card in layout.cards) drawBandCard(card, measurer, cardStyle, moreStyle)
            drawBandFocus(layout.focus, focus, neighborhood.inCycle, measurer, focusStyle)
        }
    }
}

@Composable
private fun GroupHeader(label: String, count: Int, color: Color) {
    Text(
        text = "$label · $count",
        style = TextStyle(
            fontSize = 11.sp,
            color = color,
            fontFamily = EditorFontFamily,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.7.sp,
        ),
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CallRow(neighbor: Neighbor, accent: Color, onSelect: (String) -> Unit, onOpen: (String) -> Unit) {
    val node = neighbor.node
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (hovered) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            )
            .hoverable(interaction)
            .combinedClickable(onClick = { onSelect(node.id) }, onDoubleClick = { onOpen(node.id) }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .padding(start = 0.dp)
                .width(3.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(accent),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = signature(node),
            style = TextStyle(fontSize = 13.sp, color = AtlasInk.label, fontFamily = EditorFontFamily),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = location(node),
            style = TextStyle(fontSize = 11.sp, color = AtlasInk.dim, fontFamily = EditorFontFamily),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
private fun MoreRow(overflow: Int) {
    if (overflow <= 0) return
    Text(
        text = "+ $overflow more",
        style = TextStyle(fontSize = 11.sp, color = AtlasInk.dim, fontFamily = EditorFontFamily),
        modifier = Modifier.padding(start = 21.dp, top = 4.dp),
    )
}

@Composable
private fun EmptyRow(text: String) {
    Text(
        text = text,
        style = TextStyle(fontSize = 11.sp, color = AtlasInk.sub, fontFamily = EditorFontFamily),
        modifier = Modifier.padding(start = 21.dp, top = 2.dp, bottom = 2.dp),
    )
}

@Composable
private fun ThinDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    )
}

data class CallsBandCard(val node: GraphNode?, val rect: Rect, val side: Int, val overflow: Int)

data class CallsBand(val cards: List<CallsBandCard>, val focus: Rect) {
    companion object {
        val EMPTY = CallsBand(emptyList(), Rect.Zero)
    }
}

const val CALLS_BAND_MAX_PER_SIDE = 3

fun buildCallsBand(
    width: Float,
    height: Float,
    neighborhood: Neighborhood,
    maxPerSide: Int = CALLS_BAND_MAX_PER_SIDE,
): CallsBand {
    if (width <= 0f || height <= 0f) return CallsBand.EMPTY
    val pad = 12f
    val cardH = 26f
    val gap = 10f
    val sideW = 96f
    val focusW = 84f
    val focusH = 46f
    val cx = width / 2f
    val cy = height / 2f
    val focusRect = Rect(Offset(cx - focusW / 2f, cy - focusH / 2f), Size(focusW, focusH))

    fun column(list: List<Neighbor>, total: Int, side: Int): List<CallsBandCard> {
        val shown = list.take(maxPerSide)
        val overflow = total - shown.size
        val count = shown.size + if (overflow > 0) 1 else 0
        if (count == 0) return emptyList()
        val totalH = count * cardH + (count - 1) * gap
        val top = cy - totalH / 2f
        val x = if (side < 0) pad else width - pad - sideW
        return (0 until count).map { i ->
            val rect = Rect(Offset(x, top + i * (cardH + gap)), Size(sideW, cardH))
            val node = shown.getOrNull(i)?.node
            CallsBandCard(node, rect, side, if (node == null) overflow else 0)
        }
    }

    val left = column(neighborhood.incoming, neighborhood.incomingTotal, -1)
    val right = column(neighborhood.outgoing, neighborhood.outgoingTotal, 1)
    return CallsBand(left + right, focusRect)
}

private fun DrawScope.drawBandConnector(start: Offset, end: Offset, color: Color) {
    val midX = (start.x + end.x) / 2f
    val path = Path().apply {
        moveTo(start.x, start.y)
        cubicTo(midX, start.y, midX, end.y, end.x, end.y)
    }
    drawPath(path, color.copy(alpha = 0.45f), style = Stroke(width = 1.5f))
}

private fun DrawScope.drawBandArrow(tip: Offset, color: Color) {
    val s = 6f
    val head = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(tip.x - s, tip.y - s * 0.5f)
        lineTo(tip.x - s, tip.y + s * 0.5f)
        close()
    }
    drawPath(head, color.copy(alpha = 0.85f))
}

private fun DrawScope.drawBandCard(card: CallsBandCard, measurer: TextMeasurer, textStyle: TextStyle, moreStyle: TextStyle) {
    val rect = card.rect
    val corner = CornerRadius(7f, 7f)
    val node = card.node
    if (node == null) {
        drawRoundRect(
            color = AtlasInk.nodeStroke.copy(alpha = 0.5f),
            topLeft = rect.topLeft,
            size = rect.size,
            cornerRadius = corner,
            style = Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f))),
        )
        val more = measurer.measure(AnnotatedString("+${card.overflow}"), moreStyle)
        drawText(more, topLeft = Offset(rect.center.x - more.size.width / 2f, rect.center.y - more.size.height / 2f))
        return
    }
    drawRoundRect(AtlasInk.nodeFill, topLeft = rect.topLeft, size = rect.size, cornerRadius = corner)
    drawRoundRect(AtlasInk.nodeStroke, topLeft = rect.topLeft, size = rect.size, cornerRadius = corner, style = Stroke(1f))
    val accent = if (card.side < 0) AtlasInk.incoming else AtlasInk.outgoing
    val barX = if (card.side < 0) rect.left else rect.right - 3f
    drawRoundRect(accent, topLeft = Offset(barX, rect.top + 3f), size = Size(3f, rect.height - 6f), cornerRadius = CornerRadius(1.5f, 1.5f))
    val label = measurer.measure(AnnotatedString(ellipsize(node.label, 11)), textStyle)
    drawText(label, topLeft = Offset(rect.left + 10f, rect.center.y - label.size.height / 2f))
}

private fun DrawScope.drawBandFocus(rect: Rect, focus: GraphNode, inCycle: Boolean, measurer: TextMeasurer, style: TextStyle) {
    if (inCycle) {
        drawRoundRect(
            color = AtlasInk.cycle.copy(alpha = 0.35f),
            topLeft = Offset(rect.left - 3f, rect.top - 3f),
            size = Size(rect.width + 6f, rect.height + 6f),
            cornerRadius = CornerRadius(13f, 13f),
            style = Stroke(width = 3f),
        )
    }
    drawRoundRect(AtlasInk.focusTop, topLeft = rect.topLeft, size = rect.size, cornerRadius = CornerRadius(10f, 10f))
    drawRoundRect(AtlasInk.focusStroke, topLeft = rect.topLeft, size = rect.size, cornerRadius = CornerRadius(10f, 10f), style = Stroke(1.5f))
    val label = measurer.measure(AnnotatedString(ellipsize(focus.label, 9)), style)
    drawText(label, topLeft = Offset(rect.center.x - label.size.width / 2f, rect.center.y - label.size.height / 2f))
}

private fun signature(node: GraphNode): String = node.label + "()"

private fun location(node: GraphNode): String {
    val file = node.path?.fileName?.toString() ?: node.label
    val line = symbolDisplayLine(node.id)
    return if (line != null) "$file:$line" else file
}

internal fun symbolDisplayLine(id: String): Int? =
    id.substringAfterLast('@', "").toIntOrNull()?.let { if (it >= 0) it + 1 else null }

private fun ellipsize(text: String, max: Int): String =
    if (text.length <= max) text else text.take(max - 1) + "…"
