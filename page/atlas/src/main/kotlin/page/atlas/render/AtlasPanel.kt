package page.atlas.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.nio.file.Path as FilePath
import kotlin.math.hypot
import kotlin.math.max
import page.atlas.graph.EdgeKind
import page.atlas.graph.GraphSlice
import page.atlas.graph.NodeKind

@Composable
fun AtlasPanel(
    slice: GraphSlice,
    onNodeClick: (FilePath) -> Unit,
    onClose: () -> Unit,
    width: Dp,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.width(width).fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "ATLAS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Box(modifier = Modifier.weight(1f))
                Text(
                    text = "Close",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { onClose() }.padding(4.dp),
                )
            }
            Divider()
            if (slice.nodes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "import 없음",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    AtlasCanvas(slice = slice, onNodeClick = onNodeClick)
                }
                Divider()
                LegendRow()
            }
        }
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

@Composable
private fun LegendRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LegendItem("import", EdgeKind.IMPORT)
        LegendItem("extends", EdgeKind.EXTENDS)
        LegendItem("implements", EdgeKind.IMPLEMENTS)
    }
}

@Composable
private fun LegendItem(label: String, kind: EdgeKind) {
    val importColor = MaterialTheme.colorScheme.outlineVariant
    val relationColor = MaterialTheme.colorScheme.tertiary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Canvas(modifier = Modifier.width(22.dp).height(10.dp)) {
            val y = size.height / 2f
            val from = Offset(0f, y)
            val to = Offset(size.width, y)
            when (kind) {
                EdgeKind.IMPORT -> drawLine(importColor, from, to, strokeWidth = 1f)
                EdgeKind.EXTENDS -> {
                    drawLine(relationColor, from, to, strokeWidth = 2f)
                    drawArrowHead(from, to, 0f, relationColor, filled = true)
                }
                EdgeKind.IMPLEMENTS -> {
                    drawLine(relationColor, from, to, strokeWidth = 1.5f, pathEffect = dashEffect())
                    drawArrowHead(from, to, 0f, relationColor, filled = false)
                }
            }
        }
        Text(
            text = label,
            style = TextStyle(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant),
        )
    }
}

@Composable
private fun AtlasCanvas(
    slice: GraphSlice,
    onNodeClick: (FilePath) -> Unit,
) {
    var offset by remember { mutableStateOf(Offset.Zero) }
    var scale by remember { mutableStateOf(1f) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val activeId = slice.nodes.firstOrNull { it.kind == NodeKind.ACTIVE }?.id
    LaunchedEffect(activeId) {
        offset = Offset.Zero
        scale = 1f
    }
    val layout = remember(slice, canvasSize) {
        if (canvasSize.width <= 0 || canvasSize.height <= 0) emptyMap()
        else layeredLayout(slice, canvasSize.width.toFloat(), canvasSize.height.toFloat())
    }
    val kindById = remember(slice) { slice.nodes.associate { it.id to it.kind } }
    val activeColor = MaterialTheme.colorScheme.primary
    val workspaceColor = MaterialTheme.colorScheme.secondary
    val externalColor = MaterialTheme.colorScheme.outline
    val edgeColor = MaterialTheme.colorScheme.outlineVariant
    val relationColor = MaterialTheme.colorScheme.tertiary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)
    val textMeasurer = rememberTextMeasurer()

    fun screenPos(id: String): Offset? = layout[id]?.let { Offset(it.x * scale, it.y * scale) + offset }

    fun nodeRadius(kind: NodeKind): Float = if (kind == NodeKind.ACTIVE) 10f else 7f

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offset += dragAmount
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                            if (delta != 0f) {
                                scale = (scale * if (delta > 0f) 0.9f else 1.1f).coerceIn(0.25f, 3f)
                            }
                        }
                    }
                }
            }
            .pointerInput(slice) {
                detectTapGestures { tap ->
                    val hit = slice.nodes.firstOrNull { node ->
                        val pos = screenPos(node.id) ?: return@firstOrNull false
                        hypot(tap.x - pos.x, tap.y - pos.y) <= max(nodeRadius(node.kind) * scale, 12f)
                    }
                    hit?.path?.let(onNodeClick)
                }
            },
    ) {
        for (edge in slice.edges) {
            val from = screenPos(edge.from) ?: continue
            val to = screenPos(edge.to) ?: continue
            val targetRadius = (kindById[edge.to]?.let(::nodeRadius) ?: 7f) * scale
            when (edge.kind) {
                EdgeKind.IMPORT -> drawLine(color = edgeColor, start = from, end = to, strokeWidth = 1f)
                EdgeKind.EXTENDS -> {
                    drawLine(color = relationColor, start = from, end = to, strokeWidth = 2f)
                    drawArrowHead(from, to, targetRadius, relationColor, filled = true)
                }
                EdgeKind.IMPLEMENTS -> {
                    drawLine(
                        color = relationColor,
                        start = from,
                        end = to,
                        strokeWidth = 1.5f,
                        pathEffect = dashEffect(),
                    )
                    drawArrowHead(from, to, targetRadius, relationColor, filled = false)
                }
            }
        }
        for (node in slice.nodes) {
            val pos = screenPos(node.id) ?: continue
            val color = when (node.kind) {
                NodeKind.ACTIVE -> activeColor
                NodeKind.WORKSPACE_FILE -> workspaceColor
                NodeKind.EXTERNAL -> externalColor
            }
            drawCircle(color = color, radius = nodeRadius(node.kind) * scale, center = pos)
            val label = if (node.label.length > 28) node.label.take(27) + "…" else node.label
            val measured = textMeasurer.measure(AnnotatedString(label), labelStyle)
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(
                    pos.x - measured.size.width / 2f,
                    pos.y + nodeRadius(node.kind) * scale + 3f,
                ),
            )
        }
    }
}

private fun dashEffect(): PathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))

private fun DrawScope.drawArrowHead(
    from: Offset,
    to: Offset,
    targetRadius: Float,
    color: Color,
    filled: Boolean,
) {
    val direction = to - from
    val length = hypot(direction.x, direction.y)
    if (length < 1f) return
    val unit = Offset(direction.x / length, direction.y / length)
    val tip = to - unit * (targetRadius + 2f)
    val base = tip - unit * 9f
    val normal = Offset(-unit.y, unit.x) * 4.5f
    val head = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(base.x + normal.x, base.y + normal.y)
        lineTo(base.x - normal.x, base.y - normal.y)
        close()
    }
    if (filled) drawPath(head, color) else drawPath(head, color, style = Stroke(width = 1.5f))
}
