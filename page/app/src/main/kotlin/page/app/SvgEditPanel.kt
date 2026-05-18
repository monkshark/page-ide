package page.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import org.jetbrains.skia.Data
import org.jetbrains.skia.svg.SVGDOM
import page.editor.SearchState
import page.editor.SplitOrientation
import page.editor.SplitPaneState
import page.editor.SvgFormatter
import page.ui.SplitPane
import java.nio.file.Path
import kotlin.math.min

private const val SVG_MIN_ZOOM = 0.1f
private const val SVG_MAX_ZOOM = 8f
private const val SVG_ZOOM_STEP = 1.25f
private const val SVG_WHEEL_FACTOR = 1.1f
private const val SVG_PARSE_DEBOUNCE_MS = 250L

enum class SvgViewMode { EDITOR_ONLY, BOTH, PREVIEW_ONLY }

@Composable
fun SvgEditPanel(
    path: Path,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    search: SearchState?,
    onQueryChange: (String) -> Unit,
    onReplaceChange: (String) -> Unit,
    onToggleCase: () -> Unit,
    onSearchNext: () -> Unit,
    onSearchPrev: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    onSearchClose: () -> Unit,
    onWindowShortcut: (KeyEvent) -> Boolean,
    modifier: Modifier = Modifier,
) {
    var splitState by remember(path) { mutableStateOf(SplitPaneState(ratio = 0.55f)) }
    var viewMode by remember(path) { mutableStateOf(SvgViewMode.BOTH) }
    var zoom by remember(path) { mutableStateOf(1.0f) }
    var pan by remember(path) { mutableStateOf(Offset.Zero) }

    val editor: @Composable () -> Unit = {
        EditorPanel(
            value = value,
            onValueChange = onValueChange,
            search = search,
            onQueryChange = onQueryChange,
            onReplaceChange = onReplaceChange,
            onToggleCase = onToggleCase,
            onSearchNext = onSearchNext,
            onSearchPrev = onSearchPrev,
            onReplace = onReplace,
            onReplaceAll = onReplaceAll,
            onSearchClose = onSearchClose,
            onWindowShortcut = onWindowShortcut,
            lexer = null,
            activePath = path,
            modifier = Modifier.fillMaxSize(),
        )
    }
    val preview: @Composable () -> Unit = {
        SvgLivePreview(
            xmlText = value.text,
            zoom = zoom,
            onZoom = { zoom = it.coerceIn(SVG_MIN_ZOOM, SVG_MAX_ZOOM) },
            pan = pan,
            onPan = { pan = it },
            modifier = Modifier.fillMaxSize(),
        )
    }

    Column(modifier = modifier) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when (viewMode) {
                SvgViewMode.EDITOR_ONLY -> editor()
                SvgViewMode.PREVIEW_ONLY -> preview()
                SvgViewMode.BOTH -> SplitPane(
                    state = splitState,
                    onStateChange = { splitState = it },
                    orientation = SplitOrientation.HORIZONTAL,
                    modifier = Modifier.fillMaxSize(),
                    first = { editor() },
                    second = { preview() },
                )
            }
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            thickness = 1.dp,
        )
        SvgToolbar(
            viewMode = viewMode,
            onViewModeChange = { viewMode = it },
            zoom = zoom,
            zoomEnabled = viewMode != SvgViewMode.EDITOR_ONLY,
            onZoomIn = { zoom = (zoom * SVG_ZOOM_STEP).coerceAtMost(SVG_MAX_ZOOM) },
            onZoomOut = { zoom = (zoom / SVG_ZOOM_STEP).coerceAtLeast(SVG_MIN_ZOOM) },
            onZoomReset = {
                zoom = 1f
                pan = Offset.Zero
            },
            onFormat = {
                val pretty = SvgFormatter.prettyPrint(value.text)
                if (pretty != null) {
                    onValueChange(TextFieldValue(text = pretty, selection = TextRange(0)))
                }
            },
        )
    }
}

@Composable
private fun SvgLivePreview(
    xmlText: String,
    zoom: Float,
    onZoom: (Float) -> Unit,
    pan: Offset,
    onPan: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dom: SVGDOM? by remember { mutableStateOf(null) }
    var parseFailed by remember { mutableStateOf(false) }
    LaunchedEffect(xmlText) {
        if (xmlText.isBlank()) {
            dom = null
            parseFailed = false
            return@LaunchedEffect
        }
        delay(SVG_PARSE_DEBOUNCE_MS)
        val parsed = runCatching {
            SVGDOM(Data.makeFromBytes(xmlText.toByteArray(Charsets.UTF_8)))
        }.getOrNull()
        dom = parsed
        parseFailed = parsed == null
    }
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .clipToBounds()
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        val currentDom = dom
        when {
            xmlText.isBlank() -> Text(
                text = "Empty",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            currentDom == null && parseFailed -> Text(
                text = "SVG parse error",
                color = MaterialTheme.colorScheme.error,
            )
            currentDom == null -> Text(
                text = "Parsing…",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> SvgCanvas(
                dom = currentDom,
                zoom = zoom,
                onZoom = onZoom,
                pan = pan,
                onPan = onPan,
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SvgCanvas(
    dom: SVGDOM,
    zoom: Float,
    onZoom: (Float) -> Unit,
    pan: Offset,
    onPan: (Offset) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onPointerEvent(PointerEventType.Scroll, PointerEventPass.Initial) { event ->
                val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                if (delta != 0f) {
                    val factor = if (delta > 0) 1f / SVG_WHEEL_FACTOR else SVG_WHEEL_FACTOR
                    onZoom(zoom * factor)
                    event.changes.forEach { it.consume() }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    onPan(pan + drag)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val panelW = constraints.maxWidth.toFloat()
        val panelH = constraints.maxHeight.toFloat()
        val intrinsic = remember(dom) { computeSvgIntrinsic(dom) }
        val iw = if (intrinsic.width.isFinite() && intrinsic.width > 0f) {
            intrinsic.width
        } else panelW
        val ih = if (intrinsic.height.isFinite() && intrinsic.height > 0f) {
            intrinsic.height
        } else panelH
        val baseScale = if (iw > 0f && ih > 0f) min(panelW / iw, panelH / ih) else 1f
        val scale = baseScale * zoom
        val w = iw * scale
        val h = ih * scale
        val density = LocalDensity.current
        Box(
            modifier = Modifier
                .offset { IntOffset(pan.x.roundToInt(), pan.y.roundToInt()) }
                .requiredSize(
                    width = with(density) { w.toDp() },
                    height = with(density) { h.toDp() },
                )
                .drawBehind {
                    drawIntoCanvas { canvas ->
                        val nc = canvas.nativeCanvas
                        dom.setContainerSize(iw, ih)
                        val cp = nc.save()
                        nc.scale(size.width / iw, size.height / ih)
                        dom.render(nc)
                        nc.restoreToCount(cp)
                    }
                },
        )
    }
}

@Composable
private fun SvgToolbar(
    viewMode: SvgViewMode,
    onViewModeChange: (SvgViewMode) -> Unit,
    zoom: Float,
    zoomEnabled: Boolean,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomReset: () -> Unit,
    onFormat: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(28.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            ViewModeButton(
                label = "Editor",
                selected = viewMode == SvgViewMode.EDITOR_ONLY,
                onClick = { onViewModeChange(SvgViewMode.EDITOR_ONLY) },
            )
            Spacer(Modifier.width(2.dp))
            ViewModeButton(
                label = "Split",
                selected = viewMode == SvgViewMode.BOTH,
                onClick = { onViewModeChange(SvgViewMode.BOTH) },
            )
            Spacer(Modifier.width(2.dp))
            ViewModeButton(
                label = "Preview",
                selected = viewMode == SvgViewMode.PREVIEW_ONLY,
                onClick = { onViewModeChange(SvgViewMode.PREVIEW_ONLY) },
            )
            Spacer(Modifier.width(12.dp))
            ToolbarTextButton(label = "Format", enabled = true, onClick = onFormat)
            Spacer(Modifier.width(12.dp))
            ZoomIconButton(symbol = "−", enabled = zoomEnabled, onClick = onZoomOut)
            Spacer(Modifier.width(4.dp))
            ZoomLabel(zoom = zoom, enabled = zoomEnabled, onClick = onZoomReset)
            Spacer(Modifier.width(4.dp))
            ZoomIconButton(symbol = "+", enabled = zoomEnabled, onClick = onZoomIn)
        }
    }
}

@Composable
private fun ViewModeButton(label: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()
    val bg = when {
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        isHovered -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        else -> Color.Transparent
    }
    val fg = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .background(bg)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun ToolbarTextButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()
    val bg = if (enabled && isHovered) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    } else Color.Transparent
    val fg = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    Box(
        modifier = Modifier
            .background(bg)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun ZoomIconButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()
    val bg = if (enabled && isHovered) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    } else Color.Transparent
    val fg = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    Box(
        modifier = Modifier
            .size(20.dp)
            .background(bg)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            style = LocalTextStyle.current.copy(
                color = fg,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Composable
private fun ZoomLabel(zoom: Float, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()
    val bg = if (enabled && isHovered) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    } else Color.Transparent
    val fg = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    Box(
        modifier = Modifier
            .background(bg)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "${(zoom * 100).toInt()}%",
            color = fg,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}
