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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.nio.file.Path as FilePath
import kotlin.math.hypot
import page.atlas.graph.GraphSlice
import page.ui.Glass

@Composable
fun EgoCanvas(
    slice: GraphSlice,
    focusId: String,
    onNodeClick: (FilePath) -> Unit,
    view: EgoViewState,
    modifier: Modifier = Modifier,
    onRefocus: (String) -> Unit = {},
) {
    LaunchedEffect(focusId) { view.onFocusChanged(focusId) }
    LaunchedEffect(slice, view.pendingFocusId) { view.onSliceChanged(slice) }

    val model = remember(slice, focusId, view.depPage, view.impPage) {
        buildEgoModel(slice, focusId, depPage = view.depPage, impPage = view.impPage)
    }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var hoverPos by remember { mutableStateOf<Offset?>(null) }

    val theme = rememberEgoTheme()
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = remember(theme) { TextStyle(fontSize = 10.sp, color = theme.label) }
    val flowTitleStyle = remember { TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
    val flowSubStyle = remember { TextStyle(fontSize = 9.sp) }

    val columnById = remember(model) { model.nodes.associate { it.id to it.column } }
    val neighbors = remember(model) {
        val m = HashMap<String, MutableSet<String>>()
        for (e in model.edges) {
            m.getOrPut(e.from) { mutableSetOf() }.add(e.to)
            m.getOrPut(e.to) { mutableSetOf() }.add(e.from)
        }
        m
    }

    val importTargets = remember(slice, focusId) { slice.edges.filter { it.from == focusId }.map { it.to }.toSet() }
    val dependentSources = remember(slice, focusId) { slice.edges.filter { it.to == focusId }.map { it.from }.toSet() }
    val dependentCount = dependentSources.size
    val importCount = importTargets.size
    val cycleCount = (importTargets intersect dependentSources).size
    val focusNode = remember(slice, focusId) { slice.nodes.firstOrNull { it.id == focusId } }

    fun transformNow(): EgoTransform =
        egoTransform(model, canvasSize.width.toFloat(), canvasSize.height.toFloat(), view.pan, view.zoom)

    val transform = transformNow()
    val hoverId = hoverPos?.let { egoNodeAt(model, transform, it)?.id }
        ?.takeUnless { it.startsWith(EGO_OVERFLOW_PREFIX) || it.startsWith(EGO_PREV_PREFIX) }
    val activeFocus = view.selectedId ?: hoverId
    val highlighted = activeFocus?.let { neighbors[it].orEmpty() + it }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        view.pan += drag
                    }
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            when (event.type) {
                                PointerEventType.Scroll -> {
                                    val dy = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                    if (dy != 0f) view.zoomBy(if (dy > 0f) 0.9f else 1.1f)
                                }
                                PointerEventType.Move -> hoverPos = event.changes.firstOrNull()?.position
                                PointerEventType.Exit -> hoverPos = null
                                else -> {}
                            }
                        }
                    }
                }
                .pointerInput(model) {
                    detectTapGestures(
                        onTap = { tap ->
                            val hit = egoNodeAt(model, transformNow(), tap)?.id
                            when {
                                hit == null || hit == focusId -> view.selectedId = null
                                hit.startsWith(EGO_OVERFLOW_PREFIX) ->
                                    egoColumnOf(hit)?.let { view.pageColumn(it, 1) }
                                hit.startsWith(EGO_PREV_PREFIX) ->
                                    egoColumnOf(hit)?.let { view.pageColumn(it, -1) }
                                else -> onRefocus(hit)
                            }
                        },
                        onDoubleTap = { tap ->
                            val hit = egoNodeAt(model, transformNow(), tap)
                            if (hit != null) slice.nodes.firstOrNull { it.id == hit.id }?.path?.let(onNodeClick)
                        },
                    )
                },
        ) {
            drawEgoBackground(theme)
            if (model.nodes.isEmpty()) return@Canvas
            val t = transform

            for ((column, ns) in model.nodes.groupBy { it.column }) {
                val cx = ns.map { it.center.x }.average().toFloat()
                val cy = ns.map { it.center.y }.average().toFloat()
                val rad = ns.maxOf { hypot(it.center.x - cx, it.center.y - cy) } + 120f
                drawEgoTerritoryBlob(t.toScreen(Offset(cx, cy)), theme.columnColor(column), rad * t.scale)
            }

            for (edge in model.edges) {
                val dimmed = highlighted != null && (edge.from !in highlighted || edge.to !in highlighted)
                val alpha = if (dimmed) 0.1f else 1f
                val color = theme.edgeColor(edge.toFocus, columnById[edge.to] ?: EgoColumn.IMPORT)
                val path = Path().apply {
                    val st = t.toScreen(edge.start)
                    val c1 = t.toScreen(edge.c1)
                    val c2 = t.toScreen(edge.c2)
                    val en = t.toScreen(edge.end)
                    moveTo(st.x, st.y)
                    cubicTo(c1.x, c1.y, c2.x, c2.y, en.x, en.y)
                }
                drawEgoEdge(path, color, alpha)
                drawEgoArrowHead(t.toScreen(edge.end), color, alpha)
            }

            for (node in model.nodes) {
                val center = t.toScreen(node.center)
                val radius = (node.radius * t.scale).coerceAtLeast(2f)
                val dimmed = highlighted != null && node.id !in highlighted
                val alpha = if (dimmed) 0.2f else 1f
                if (node.column == EgoColumn.FOCUS) drawEgoFocusRing(theme, center, radius, alpha)
                drawEgoDisc(theme, node.column, center, radius, alpha)
                if (node.id == view.selectedId) {
                    drawCircle(
                        color = theme.label.copy(alpha = alpha * 0.9f),
                        radius = radius + 4f,
                        center = center,
                        style = Stroke(width = 1.5f),
                    )
                }
                run {
                    val label = ellipsizeMiddle(node.label, 28)
                    val measured = textMeasurer.measure(AnnotatedString(label), labelStyle)
                    drawText(
                        textLayoutResult = measured,
                        color = theme.label.copy(alpha = alpha),
                        topLeft = Offset(center.x - measured.size.width / 2f, center.y + radius + 3f),
                    )
                }
            }

            drawFlowLabel(theme, textMeasurer, flowTitleStyle, flowSubStyle, model, EgoColumn.DEPENDENT, "USED BY", "files that import this", t)
            drawFlowLabel(theme, textMeasurer, flowTitleStyle, flowSubStyle, model, EgoColumn.IMPORT, "USES", "files this imports", t)

            val area = Rect(size.width - 172f, size.height - 126f, size.width - 16f, size.height - 46f)
            val viewport = Rect(
                (0f - t.offset.x) / t.scale,
                (0f - t.offset.y) / t.scale,
                (size.width - t.offset.x) / t.scale,
                (size.height - t.offset.y) / t.scale,
            )
            drawEgoMinimap(theme, model, area, viewport)
        }

        val hovered = hoverId?.let { id -> model.nodes.firstOrNull { it.id == id } }
        if (hovered != null) {
            val sourceNode = slice.nodes.firstOrNull { it.id == hovered.id }
            val sp = transform.toScreen(hovered.center)
            EgoTooltip(
                label = hovered.label,
                path = sourceNode?.path?.toString(),
                x = (sp.x + 12f).toInt(),
                y = (sp.y - 16f).toInt(),
            )
        }
        EgoHeaderChip(modifier = Modifier.align(Alignment.TopStart).padding(12.dp))
        EgoInfoCard(
            fileName = focusNode?.label ?: "—",
            path = focusNode?.path?.toString() ?: "",
            dependents = dependentCount,
            imports = importCount,
            cycles = cycleCount,
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
        )
        EgoLegend(theme, modifier = Modifier.align(Alignment.BottomStart).padding(12.dp))
        EgoZoomHud(
            zoom = view.zoom,
            onZoomOut = { view.zoomBy(0.9f) },
            onReset = { view.reset() },
            onZoomIn = { view.zoomBy(1.1f) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFlowLabel(
    theme: EgoTheme,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    titleStyle: TextStyle,
    subtitleStyle: TextStyle,
    model: EgoModel,
    column: EgoColumn,
    title: String,
    subtitle: String,
    transform: EgoTransform,
) {
    val ns = model.nodes.filter { it.column == column }
    if (ns.isEmpty()) return
    val x = ns.first().center.x
    val topY = ns.minOf { it.center.y - it.radius } - 48f
    drawEgoFlowLabel(
        textMeasurer, titleStyle, subtitleStyle, title, subtitle,
        theme.columnColor(column), transform.toScreen(Offset(x, topY)),
    )
}

@Composable
private fun EgoHeaderChip(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Glass.colors.surfaceOverlay, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = "Atlas · Focus · dependency map",
            style = TextStyle(fontSize = 11.sp, color = Glass.colors.muted, fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
private fun EgoInfoCard(
    fileName: String,
    path: String,
    dependents: Int,
    imports: Int,
    cycles: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(220.dp)
            .background(Glass.colors.surfaceOverlay, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(fileName, style = TextStyle(fontSize = 13.sp, color = Glass.colors.text, fontWeight = FontWeight.SemiBold))
        if (path.isNotEmpty()) {
            Text(path, style = TextStyle(fontSize = 9.sp, color = Glass.colors.faint), maxLines = 2)
        }
        Box(modifier = Modifier.padding(top = 6.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("$dependents used by · $imports uses", style = TextStyle(fontSize = 12.sp, color = Color(0xFF6E8BFF), fontWeight = FontWeight.Medium))
                Text("$cycles cycles", style = TextStyle(fontSize = 10.sp, color = Glass.colors.faint))
            }
        }
    }
}

@Composable
private fun EgoLegend(theme: EgoTheme, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Glass.colors.surfaceOverlay, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendDot("used by", theme.dependent)
        LegendDot("this file", theme.focus)
        LegendDot("uses", theme.importNode)
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
        Text(label, style = TextStyle(fontSize = 9.sp, color = Glass.colors.muted))
    }
}

@Composable
private fun EgoZoomHud(
    zoom: Float,
    onZoomOut: () -> Unit,
    onReset: () -> Unit,
    onZoomIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(Glass.colors.surfaceOverlay, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HudButton("−", onZoomOut)
        Text(
            "${(zoom * 100).toInt()}%",
            style = TextStyle(fontSize = 10.sp, color = Glass.colors.muted),
            modifier = Modifier.clickable { onReset() }.padding(horizontal = 2.dp),
        )
        HudButton("+", onZoomIn)
    }
}

@Composable
private fun HudButton(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = TextStyle(fontSize = 13.sp, color = Glass.colors.text, fontWeight = FontWeight.SemiBold),
        modifier = Modifier.clickable { onClick() }.padding(horizontal = 4.dp),
    )
}

@Composable
private fun EgoTooltip(label: String, path: String?, x: Int, y: Int) {
    Box(
        modifier = Modifier
            .offset { IntOffset(x, y) }
            .width(260.dp)
            .background(Glass.colors.surfaceOverlay, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = TextStyle(fontSize = 11.sp, color = Glass.colors.text, fontWeight = FontWeight.Medium),
            )
            if (!path.isNullOrEmpty()) {
                Text(path, style = TextStyle(fontSize = 9.sp, color = Glass.colors.faint), maxLines = 2)
            }
        }
    }
}

private fun ellipsizeMiddle(text: String, max: Int): String {
    if (text.length <= max) return text
    val keep = (max - 1).coerceAtLeast(2)
    val head = (keep + 1) / 2
    val tail = keep / 2
    return text.take(head) + "…" + text.takeLast(tail)
}
