package page.atlas.render

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.lerp
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
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import page.atlas.graph.EdgeKind
import page.atlas.graph.GraphSlice
import page.atlas.graph.NodeKind

@Composable
fun AtlasPanel(
    slice: GraphSlice,
    onNodeClick: (FilePath) -> Unit,
    onClose: () -> Unit,
    width: Dp,
    projectMode: Boolean = false,
    onProjectModeChange: (Boolean) -> Unit = {},
    showExpand: Boolean = false,
    onExpand: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.width(width).fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        AtlasContent(
            slice = slice,
            onNodeClick = onNodeClick,
            onClose = onClose,
            projectMode = projectMode,
            onProjectModeChange = onProjectModeChange,
            showExpand = showExpand,
            onExpand = onExpand,
        )
    }
}

@Composable
fun AtlasContent(
    slice: GraphSlice,
    onNodeClick: (FilePath) -> Unit,
    onClose: () -> Unit,
    projectMode: Boolean = false,
    onProjectModeChange: (Boolean) -> Unit = {},
    showExpand: Boolean = false,
    onExpand: () -> Unit = {},
) {
    var selectedId by remember(slice) { mutableStateOf<String?>(null) }
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
            ModeChip("파일", !projectMode) { onProjectModeChange(false) }
            ModeChip("프로젝트", projectMode) { onProjectModeChange(true) }
            Box(modifier = Modifier.weight(1f))
            if (showExpand) {
                Text(
                    text = "확대",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { onExpand() }.padding(4.dp),
                )
            }
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
                    text = if (projectMode) "소스 파일 없음" else "import 없음",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AtlasCanvas(
                    slice = slice,
                    projectMode = projectMode,
                    selectedId = selectedId,
                    onSelect = { selectedId = it },
                    onNodeClick = onNodeClick,
                )
            }
            Divider()
            LegendRow()
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clickable { onClick() }.padding(horizontal = 2.dp, vertical = 4.dp),
    )
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
    projectMode: Boolean,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onNodeClick: (FilePath) -> Unit,
) {
    var yaw by remember { mutableStateOf(0.6f) }
    var pitch by remember { mutableStateOf(0.5f) }
    var zoomUser by remember { mutableStateOf(1f) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var hoverPos by remember { mutableStateOf<Offset?>(null) }
    var lastInteract by remember { mutableStateOf(0L) }
    val activeId = slice.nodes.firstOrNull { it.kind == NodeKind.ACTIVE }?.id
    LaunchedEffect(activeId, projectMode) {
        zoomUser = 1f
        pitch = 0.5f
    }
    val scene = remember(slice) { buildScene(slice) }
    var fromScene by remember { mutableStateOf(scene) }
    var toScene by remember { mutableStateOf(scene) }
    val morph = remember { Animatable(1f) }
    LaunchedEffect(scene) {
        if (toScene != scene) {
            fromScene = toScene
            toScene = scene
            morph.snapTo(0f)
            morph.animateTo(1f, tween(550, easing = FastOutSlowInEasing))
        }
    }
    val morphT = morph.value
    val blended = remember(fromScene, toScene, morphT) { blendScenes(fromScene, toScene, morphT) }
    val freshIds = remember(fromScene, toScene) {
        if (fromScene === toScene) emptySet()
        else toScene.nodes.map { it.id }.toSet() - fromScene.nodes.map { it.id }.toSet()
    }
    val radius = remember(toScene) { sceneRadius(toScene) }
    val zoom = run {
        val side = min(canvasSize.width, canvasSize.height).toFloat()
        if (side <= 0f) zoomUser else side * 0.42f / radius * zoomUser
    }
    val kindById = remember(slice) { slice.nodes.associate { it.id to it.kind } }
    val labelById = remember(slice) { slice.nodes.associate { it.id to it.label } }
    val neighborsByHover = remember(slice) {
        val map = HashMap<String, MutableSet<String>>()
        for (edge in slice.edges) {
            map.getOrPut(edge.from) { mutableSetOf() }.add(edge.to)
            map.getOrPut(edge.to) { mutableSetOf() }.add(edge.from)
        }
        map
    }
    val activeColor = MaterialTheme.colorScheme.primary
    val workspaceColor = MaterialTheme.colorScheme.secondary
    val externalColor = MaterialTheme.colorScheme.outline
    val edgeColor = MaterialTheme.colorScheme.outlineVariant
    val relationColor = MaterialTheme.colorScheme.tertiary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)
    val textMeasurer = rememberTextMeasurer()

    fun nodeRadius(kind: NodeKind?): Float = if (kind == NodeKind.ACTIVE) 11f else 8f

    fun projected(): List<ProjectedNode> {
        if (canvasSize.width <= 0 || canvasSize.height <= 0) return emptyList()
        return projectScene(blended, yaw, pitch, zoom, canvasSize.width.toFloat(), canvasSize.height.toFloat())
    }

    fun nodeAt(pos: Offset): ProjectedNode? = projected()
        .filter { hypot(pos.x - it.x, pos.y - it.y) <= max(nodeRadius(kindById[it.id]) * it.scale, 14f) }
        .minByOrNull { hypot(pos.x - it.x, pos.y - it.y) }

    val hoverId = hoverPos?.let { nodeAt(it)?.id }
    val focusId = selectedId ?: hoverId
    val highlighted = focusId?.let { (neighborsByHover[it].orEmpty() + it) }
    val rotationPaused by rememberUpdatedState(hoverId != null || selectedId != null)
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L && now - lastInteract > 3_000_000_000L && !rotationPaused) {
                    yaw += (now - last) / 1_000_000_000f * 0.1f
                }
                last = now
            }
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    yaw += dragAmount.x * 0.008f
                    pitch = (pitch + dragAmount.y * 0.008f).coerceIn(-0.2f, 1.25f)
                    lastInteract = System.nanoTime()
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Scroll -> {
                                val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                if (delta != 0f) {
                                    zoomUser = (zoomUser * if (delta > 0f) 0.9f else 1.1f).coerceIn(0.2f, 5f)
                                    lastInteract = System.nanoTime()
                                }
                            }
                            PointerEventType.Move -> {
                                hoverPos = event.changes.firstOrNull()?.position
                            }
                            PointerEventType.Exit -> {
                                hoverPos = null
                            }
                        }
                    }
                }
            }
            .pointerInput(slice) {
                detectTapGestures(
                    onTap = { tap ->
                        onSelect(nodeAt(tap)?.id)
                        lastInteract = System.nanoTime()
                    },
                    onDoubleTap = { tap ->
                        val hit = nodeAt(tap)
                        if (hit != null) {
                            slice.nodes.firstOrNull { it.id == hit.id }?.path?.let(onNodeClick)
                        }
                        lastInteract = System.nanoTime()
                    },
                )
            },
    ) {
        val projectedNodes = projectScene(blended, yaw, pitch, zoom, size.width, size.height)
        if (projectedNodes.isEmpty()) return@Canvas
        val byId = projectedNodes.associateBy { it.id }
        val minDepth = projectedNodes.minOf { it.depth }
        val maxDepth = projectedNodes.maxOf { it.depth }
        val depthRange = (maxDepth - minDepth).coerceAtLeast(1f)
        fun depthAlpha(depth: Float): Float = 0.5f + 0.5f * ((maxDepth - depth) / depthRange)
        val labelBudget = if (zoomUser >= 1.5f) 24 else 8

        for (ring in blended.rings) {
            val c = projectPoint(0f, ring.y, 0f, yaw, pitch, zoom, size.width, size.height)
            val rx = ring.radius * c.scale
            val ry = rx * abs(sin(pitch))
            drawOval(
                color = edgeColor.copy(alpha = 0.1f),
                topLeft = Offset(c.x - rx, c.y - ry),
                size = Size(rx * 2f, ry * 2f),
                style = Stroke(width = 1f),
            )
        }

        for (edge in slice.edges) {
            val from = byId[edge.from] ?: continue
            val to = byId[edge.to] ?: continue
            val dimmed = highlighted != null && (edge.from !in highlighted || edge.to !in highlighted)
            val alpha = if (dimmed) 0.08f else depthAlpha((from.depth + to.depth) / 2f)
            val start = Offset(from.x, from.y)
            val end = Offset(to.x, to.y)
            val targetRadius = nodeRadius(kindById[edge.to]) * to.scale
            when (edge.kind) {
                EdgeKind.IMPORT -> drawLine(
                    color = edgeColor.copy(alpha = alpha),
                    start = start,
                    end = end,
                    strokeWidth = 1f,
                )
                EdgeKind.EXTENDS -> {
                    drawLine(relationColor.copy(alpha = alpha), start, end, strokeWidth = 2f)
                    drawArrowHead(start, end, targetRadius, relationColor.copy(alpha = alpha), filled = true)
                }
                EdgeKind.IMPLEMENTS -> {
                    drawLine(
                        color = relationColor.copy(alpha = alpha),
                        start = start,
                        end = end,
                        strokeWidth = 1.5f,
                        pathEffect = dashEffect(),
                    )
                    drawArrowHead(start, end, targetRadius, relationColor.copy(alpha = alpha), filled = false)
                }
            }
        }
        for (p in projectedNodes) {
            val kind = kindById[p.id] ?: continue
            val base = when (kind) {
                NodeKind.ACTIVE -> activeColor
                NodeKind.WORKSPACE_FILE -> workspaceColor
                NodeKind.EXTERNAL -> externalColor
            }
            val pos = Offset(p.x, p.y)
            val r = (nodeRadius(kind) * p.scale).coerceAtLeast(2f)
            var alpha = depthAlpha(p.depth)
            if (p.id in freshIds) alpha *= morphT
            if (highlighted != null && p.id !in highlighted) alpha *= 0.18f
            alpha = alpha.coerceIn(0f, 1f)
            if (kind == NodeKind.ACTIVE) {
                drawCircle(base.copy(alpha = alpha * 0.18f), radius = r * 2.4f, center = pos)
                drawCircle(base.copy(alpha = alpha * 0.25f), radius = r * 1.6f, center = pos)
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        lerp(base, Color.White, 0.55f).copy(alpha = alpha),
                        base.copy(alpha = alpha),
                        lerp(base, Color.Black, 0.35f).copy(alpha = alpha),
                    ),
                    center = pos + Offset(-r * 0.35f, -r * 0.35f),
                    radius = (r * 1.7f).coerceAtLeast(1f),
                ),
                radius = r,
                center = pos,
            )
            if (p.id == selectedId) {
                drawCircle(labelColor.copy(alpha = alpha * 0.8f), radius = r + 3f, center = pos, style = Stroke(width = 1.5f))
            }
            val showLabel = p.id == hoverId ||
                p.id == selectedId ||
                highlighted?.contains(p.id) == true ||
                kind == NodeKind.ACTIVE ||
                projectedNodes.size <= labelBudget
            if (showLabel) {
                val raw = labelById[p.id] ?: continue
                val label = if (raw.length > 28) raw.take(27) + "…" else raw
                val measured = textMeasurer.measure(AnnotatedString(label), labelStyle)
                drawText(
                    textLayoutResult = measured,
                    color = labelColor.copy(alpha = alpha),
                    topLeft = Offset(pos.x - measured.size.width / 2f, pos.y + r + 3f),
                )
            }
        }
    }
}

private fun blendScenes(from: SceneModel, to: SceneModel, t: Float): SceneModel {
    if (t >= 1f || from === to) return to
    val fromById = from.nodes.associateBy { it.id }
    return SceneModel(
        to.nodes.map { n ->
            val f = fromById[n.id] ?: return@map n
            Node3D(n.id, f.x + (n.x - f.x) * t, f.y + (n.y - f.y) * t, f.z + (n.z - f.z) * t)
        },
        to.rings,
    )
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
