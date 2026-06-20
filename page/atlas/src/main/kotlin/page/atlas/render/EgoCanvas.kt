package page.atlas.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import java.nio.file.Path as FilePath
import page.atlas.graph.GraphInsights
import page.atlas.graph.GraphNode
import page.atlas.graph.GraphSlice
import page.atlas.graph.NodeKind
import page.ui.EditorFontFamily

private const val SPINE_MAX_PER_SIDE = 6
private const val SPINE_HUB_MIN = 8

@Composable
fun EgoCanvas(
    slice: GraphSlice,
    focusId: String,
    onNodeClick: (FilePath) -> Unit,
    view: EgoViewState,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(focusId) { view.onFocusChanged(focusId) }

    val data = remember(slice, focusId) { buildSpine(slice, focusId) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val layout = remember(data, canvasSize) {
        spineLayout(data, canvasSize.width.toFloat(), canvasSize.height.toFloat())
    }

    val measurer = rememberTextMeasurer()
    val labelStyle = remember { mono(12.sp, AtlasInk.label) }
    val subStyle = remember { mono(8.5.sp, AtlasInk.sub) }
    val titleStyle = remember { mono(13.5.sp, AtlasInk.bright, FontWeight.SemiBold) }
    val focusSubStyle = remember { mono(9.5.sp, AtlasInk.dim) }
    val headStyle = remember { mono(10.sp, AtlasInk.sub) }
    val countStyle = remember { mono(10.5.sp, AtlasInk.label) }
    val chipStyle = remember { mono(8.sp, AtlasInk.outgoing) }
    val moreStyle = remember { mono(11.sp, AtlasInk.dim) }
    val hubStyle = remember { mono(9.sp, AtlasInk.hub) }

    Box(modifier.fillMaxSize().background(AtlasInk.canvas)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(layout) {
                    detectTapGestures(
                        onTap = { pos ->
                            val hit = layout.cards.firstOrNull { it.card.node != null && it.rect.contains(pos) }
                            view.selectedId = hit?.card?.node?.id
                                ?: focusId.takeIf { layout.focusRect.contains(pos) }
                        },
                        onDoubleTap = { pos ->
                            val hit = layout.cards.firstOrNull { it.card.node?.path != null && it.rect.contains(pos) }
                            hit?.card?.node?.path?.let(onNodeClick)
                        },
                    )
                },
        ) {
            if (data.focus == null || layout.focusRect == Rect.Zero) return@Canvas

            for (box in layout.cards) {
                if (box.card.node == null) continue
                val color = if (box.side < 0) AtlasInk.incoming else AtlasInk.outgoing
                val start = if (box.side < 0)
                    Offset(box.rect.right, box.rect.center.y) else Offset(layout.focusRect.right, layout.focusRect.center.y)
                val end = if (box.side < 0)
                    Offset(layout.focusRect.left, layout.focusRect.center.y) else Offset(box.rect.left, box.rect.center.y)
                drawSpineConnector(start, end, color)
            }

            for (box in layout.cards) {
                drawSpineCard(box, measurer, labelStyle, subStyle, moreStyle, hubStyle, view.selectedId)
            }

            drawFocusCard(layout.focusRect, data, measurer, titleStyle, focusSubStyle, chipStyle, countStyle)

            data.focus?.let {
                drawText(
                    measurer.measure(AnnotatedString("← IMPORTED BY · ${data.leftTotal}"), headStyle.copy(color = AtlasInk.incoming)),
                    topLeft = Offset(24f, 12f),
                )
                val outLabel = measurer.measure(AnnotatedString("IMPORTS · ${data.rightTotal} →"), headStyle.copy(color = AtlasInk.outgoing))
                drawText(outLabel, topLeft = Offset(size.width - 24f - outLabel.size.width, 12f))
            }
        }
    }
}

private fun DrawScope.drawSpineConnector(start: Offset, end: Offset, color: Color) {
    val midX = (start.x + end.x) / 2f
    val path = Path().apply {
        moveTo(start.x, start.y)
        cubicTo(midX, start.y, midX, end.y, end.x, end.y)
    }
    drawPath(
        path,
        color.copy(alpha = 0.5f),
        style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 7f))),
    )
}

private fun DrawScope.drawSpineCard(
    box: SpineBox,
    measurer: TextMeasurer,
    labelStyle: TextStyle,
    subStyle: TextStyle,
    moreStyle: TextStyle,
    hubStyle: TextStyle,
    selectedId: String?,
) {
    val rect = box.rect
    val radius = CornerRadius(11f, 11f)
    val node = box.card.node
    if (node == null) {
        drawRoundRect(
            color = AtlasInk.nodeStroke.copy(alpha = 0.55f),
            topLeft = rect.topLeft,
            size = rect.size,
            cornerRadius = radius,
            style = Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f))),
        )
        val more = measurer.measure(AnnotatedString("+${box.card.overflow} more"), moreStyle)
        drawText(more, topLeft = Offset(rect.center.x - more.size.width / 2f, rect.center.y - more.size.height / 2f))
        return
    }
    drawRoundRect(color = AtlasInk.nodeFill, topLeft = rect.topLeft, size = rect.size, cornerRadius = radius)
    val selected = node.id == selectedId
    drawRoundRect(
        color = if (selected) AtlasInk.label.copy(alpha = 0.5f) else AtlasInk.nodeStroke,
        topLeft = rect.topLeft,
        size = rect.size,
        cornerRadius = radius,
        style = Stroke(width = if (selected) 1.4f else 1f),
    )
    val dotColor = if (box.side < 0) AtlasInk.incoming else AtlasInk.outgoing
    drawRoundRect(
        color = dotColor,
        topLeft = Offset(rect.left + 13f, rect.top + 19f),
        size = Size(8f, 8f),
        cornerRadius = CornerRadius(3f, 3f),
    )
    val label = measurer.measure(AnnotatedString(ellipsize(node.label, 20)), labelStyle)
    drawText(label, topLeft = Offset(rect.left + 30f, rect.top + 8f))
    val role = measurer.measure(AnnotatedString(roleLabel(node)), subStyle)
    drawText(role, topLeft = Offset(rect.left + 30f, rect.top + 27f))
    if (box.card.isHub) {
        val hub = measurer.measure(AnnotatedString("hub"), hubStyle)
        drawText(hub, topLeft = Offset(rect.right - 12f - hub.size.width, rect.top + 10f))
    }
}

private fun DrawScope.drawFocusCard(
    rect: Rect,
    data: SpineData,
    measurer: TextMeasurer,
    titleStyle: TextStyle,
    subStyle: TextStyle,
    chipStyle: TextStyle,
    countStyle: TextStyle,
) {
    val focus = data.focus ?: return
    val radius = CornerRadius(14f, 14f)
    drawRoundRect(
        brush = Brush.linearGradient(
            listOf(AtlasInk.focusTop, AtlasInk.focusBottom),
            start = rect.topLeft,
            end = Offset(rect.right, rect.bottom),
        ),
        topLeft = rect.topLeft,
        size = rect.size,
        cornerRadius = radius,
    )
    drawRoundRect(
        color = AtlasInk.focusStroke,
        topLeft = rect.topLeft,
        size = rect.size,
        cornerRadius = radius,
        style = Stroke(width = 1.2f),
    )
    val chipW = 48f
    val chipRect = Rect(Offset(rect.right - 10f - chipW, rect.top + 10f), Size(chipW, 16f))
    drawRoundRect(
        color = AtlasInk.outgoing.copy(alpha = 0.3f),
        topLeft = chipRect.topLeft,
        size = chipRect.size,
        cornerRadius = CornerRadius(5f, 5f),
        style = Stroke(width = 1f),
    )
    val chip = measurer.measure(AnnotatedString("FOCUS"), chipStyle.copy(letterSpacing = 1.5.sp))
    drawText(chip, topLeft = Offset(chipRect.center.x - chip.size.width / 2f, chipRect.center.y - chip.size.height / 2f))

    val title = measurer.measure(AnnotatedString(ellipsize(focus.label, 22)), titleStyle)
    drawText(title, topLeft = Offset(rect.left + 16f, rect.top + 26f))
    val sub = measurer.measure(AnnotatedString(roleLabel(focus).lowercase()), subStyle)
    drawText(sub, topLeft = Offset(rect.left + 16f, rect.top + 48f))
    val inText = measurer.measure(AnnotatedString("↑ ${data.leftTotal} in"), countStyle.copy(color = AtlasInk.incoming))
    drawText(inText, topLeft = Offset(rect.left + 16f, rect.bottom - 22f))
    val outText = measurer.measure(AnnotatedString("↓ ${data.rightTotal} out"), countStyle.copy(color = AtlasInk.outgoing))
    drawText(outText, topLeft = Offset(rect.left + 84f, rect.bottom - 22f))
}

private data class SpineCard(val node: GraphNode?, val isHub: Boolean, val overflow: Int = 0)

private data class SpineBox(val card: SpineCard, val rect: Rect, val side: Int)

private data class SpineData(
    val focus: GraphNode?,
    val left: List<SpineCard>,
    val right: List<SpineCard>,
    val leftTotal: Int,
    val rightTotal: Int,
)

private data class SpineLayout(val focusRect: Rect, val cards: List<SpineBox>)

private fun buildSpine(slice: GraphSlice, focusId: String): SpineData {
    val focus = slice.nodes.firstOrNull { it.id == focusId }
        ?: return SpineData(null, emptyList(), emptyList(), 0, 0)
    val byId = slice.nodes.associateBy { it.id }
    val indeg = GraphInsights.indegrees(slice.edges)
    val incomingIds = LinkedHashSet<String>()
    val outgoingIds = LinkedHashSet<String>()
    for (e in slice.edges) {
        if (e.from == e.to) continue
        if (e.to == focusId) incomingIds += e.from
        if (e.from == focusId) outgoingIds += e.to
    }
    fun cards(ids: Set<String>): Pair<List<SpineCard>, Int> {
        val nodes = ids.mapNotNull { byId[it] }
            .sortedWith(compareByDescending<GraphNode> { indeg[it.id] ?: 0 }.thenBy { it.label.lowercase() }.thenBy { it.id })
        val shown = nodes.take(SPINE_MAX_PER_SIDE).map { SpineCard(it, (indeg[it.id] ?: 0) >= SPINE_HUB_MIN) }
        return shown to nodes.size
    }
    val (left, leftTotal) = cards(incomingIds)
    val (right, rightTotal) = cards(outgoingIds)
    return SpineData(focus, left, right, leftTotal, rightTotal)
}

private fun spineLayout(data: SpineData, w: Float, h: Float): SpineLayout {
    if (w <= 0f || h <= 0f || data.focus == null) return SpineLayout(Rect.Zero, emptyList())
    val cardW = 168f
    val cardH = 46f
    val focusW = 180f
    val focusH = 92f
    val gapY = 14f
    val focusRect = Rect(Offset((w - focusW) / 2f, (h - focusH) / 2f), Size(focusW, focusH))

    fun column(cards: List<SpineCard>, total: Int, side: Int): List<SpineBox> {
        val overflow = total - cards.size
        val count = cards.size + if (overflow > 0) 1 else 0
        if (count == 0) return emptyList()
        val totalH = count * cardH + (count - 1) * gapY
        val top = (h - totalH) / 2f
        val x = if (side < 0) 24f else w - 24f - cardW
        val out = ArrayList<SpineBox>(count)
        for (i in 0 until count) {
            val y = top + i * (cardH + gapY)
            val rect = Rect(Offset(x, y), Size(cardW, cardH))
            val card = if (i < cards.size) cards[i] else SpineCard(null, false, overflow)
            out += SpineBox(card, rect, side)
        }
        return out
    }

    return SpineLayout(focusRect, column(data.left, data.leftTotal, -1) + column(data.right, data.rightTotal, 1))
}

private fun roleLabel(node: GraphNode): String = when (node.kind) {
    NodeKind.EXTERNAL -> "EXTERNAL"
    NodeKind.SYMBOL -> "SYMBOL"
    else -> node.path?.parent?.fileName?.toString()?.uppercase() ?: "FILE"
}

private fun mono(size: TextUnit, color: Color, weight: FontWeight = FontWeight.Normal): TextStyle =
    TextStyle(fontSize = size, color = color, fontWeight = weight, fontFamily = EditorFontFamily)

private fun ellipsize(text: String, max: Int): String =
    if (text.length <= max) text else text.take(max - 1) + "…"
