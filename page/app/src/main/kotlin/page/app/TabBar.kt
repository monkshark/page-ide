package page.app

import page.runtime.*

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import page.editor.OpenTab
import page.editor.TabBook
import page.ui.CompactContextMenuRepresentation
import kotlin.math.abs
import kotlin.math.roundToInt

private val TabBarHeight = 32.dp
private val CenterTight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both,
)

enum class CrossPaneSide { LEFT, RIGHT }

data class TabContextActions(
    val onClose: (Int) -> Unit,
    val onCloseOthers: (Int) -> Unit,
    val onCloseToLeft: (Int) -> Unit,
    val onCloseToRight: (Int) -> Unit,
    val onCloseAll: () -> Unit,
    val onCloseUnmodified: () -> Unit,
    val onCopyAbsolutePath: (Int) -> Unit,
    val onCopyRelativePath: (Int) -> Unit,
    val onShowInExplorer: (Int) -> Unit,
    val onTogglePin: (Int) -> Unit,
    val onMoveToOtherPane: ((Int) -> Unit)?,
    val onSplit: ((Int) -> Unit)?,
    val onRename: (Int) -> Unit,
)

@Composable
fun TabBar(
    book: TabBook,
    onActivate: (Int) -> Unit,
    onClose: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onMoveToOtherPane: ((Int) -> Unit)? = null,
    crossPaneSide: CrossPaneSide? = null,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    contextActions: TabContextActions? = null,
    modifier: Modifier = Modifier,
) {
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetPx by remember { mutableStateOf(0f) }
    var draggedNaturalLeft by remember { mutableStateOf(0) }
    var draggedWidth by remember { mutableStateOf(0) }
    var barWidthPx by remember { mutableStateOf(0) }
    val tabBounds = remember { mutableStateMapOf<Int, IntRange>() }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(book.tabs.size) {
        tabBounds.keys.removeAll { it >= book.tabs.size }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TabBarHeight)
            .background(MaterialTheme.colorScheme.surface)
            .onSizeChanged { barWidthPx = it.width },
    ) {
        if (book.tabs.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth())
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type == PointerEventType.Scroll) {
                                    val deltaY = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                    if (deltaY != 0f) {
                                        scope.launch {
                                            scrollState.scrollBy(deltaY * 60f)
                                        }
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            }
                        }
                    }
                    .pointerInput(book.tabs.size) {
                        val touchSlop = viewConfiguration.touchSlop
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val tappedIndex = findTabAt(
                                down.position.x + scrollState.value,
                                tabBounds,
                                book.tabs.size,
                            ) ?: return@awaitEachGesture
                            val tappedBounds = tabBounds[tappedIndex] ?: return@awaitEachGesture
                            onActivate(tappedIndex)
                            draggingIndex = tappedIndex
                            draggedNaturalLeft = tappedBounds.first
                            draggedWidth = tappedBounds.last - tappedBounds.first
                            dragOffsetPx = 0f
                            var crossedSlop = false
                            var preDx = 0f
                            var lastPointerX = down.position.x

                            drag(down.id) { change ->
                                val dx = change.positionChange().x
                                preDx += dx
                                if (!crossedSlop && abs(preDx) > touchSlop) {
                                    crossedSlop = true
                                    onDragStart()
                                }
                                if (crossedSlop) {
                                    dragOffsetPx += dx
                                    lastPointerX = change.position.x
                                    var cur = draggingIndex ?: return@drag
                                    if (dragOffsetPx > 0f) {
                                        while (true) {
                                            val rightW = widthOf(tabBounds[cur + 1]) ?: break
                                            if (dragOffsetPx <= rightW / 2f) break
                                            onMove(cur, cur + 1)
                                            swapBounds(tabBounds, cur, cur + 1)
                                            cur += 1
                                            draggingIndex = cur
                                            draggedNaturalLeft += rightW.toInt()
                                            dragOffsetPx -= rightW
                                        }
                                    } else if (dragOffsetPx < 0f) {
                                        while (true) {
                                            val leftW = widthOf(tabBounds[cur - 1]) ?: break
                                            if (dragOffsetPx >= -leftW / 2f) break
                                            onMove(cur, cur - 1)
                                            swapBounds(tabBounds, cur, cur - 1)
                                            cur -= 1
                                            draggingIndex = cur
                                            draggedNaturalLeft -= leftW.toInt()
                                            dragOffsetPx += leftW
                                        }
                                    }
                                    change.consume()
                                }
                            }
                            val finalIndex = draggingIndex
                            val finalPointerX = lastPointerX
                            val wasDragging = crossedSlop
                            draggingIndex = null
                            dragOffsetPx = 0f
                            if (wasDragging) onDragEnd()
                            if (finalIndex != null && onMoveToOtherPane != null && crossPaneSide != null && barWidthPx > 0) {
                                val overshoot = 30f
                                val crossed = when (crossPaneSide) {
                                    CrossPaneSide.RIGHT -> finalPointerX > barWidthPx + overshoot
                                    CrossPaneSide.LEFT -> finalPointerX < -overshoot
                                }
                                if (crossed) onMoveToOtherPane(finalIndex)
                            }
                        }
                    },
            ) {
                CompositionLocalProvider(LocalContextMenuRepresentation provides CompactContextMenuRepresentation) {
                    Row(
                        modifier = Modifier.horizontalScroll(scrollState),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        book.tabs.forEachIndexed { index, tab ->
                            val isDragged = index == draggingIndex
                            val menuItems = contextActions?.let {
                                buildTabMenuItems(
                                    index = index,
                                    tab = tab,
                                    bookSize = book.tabs.size,
                                    actions = it,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .onGloballyPositioned { coords ->
                                        val left = coords.positionInParent().x.toInt()
                                        val right = left + coords.size.width
                                        tabBounds[index] = left..right
                                    },
                            ) {
                                if (menuItems != null) {
                                    ContextMenuArea(items = { menuItems }) {
                                        TabChip(
                                            tab = tab,
                                            isActive = index == book.activeIndex,
                                            offsetPx = 0,
                                            elevated = false,
                                            alpha = if (isDragged) 0f else 1f,
                                            onClose = { onClose(index) },
                                        )
                                    }
                                } else {
                                    TabChip(
                                        tab = tab,
                                        isActive = index == book.activeIndex,
                                        offsetPx = 0,
                                        elevated = false,
                                        alpha = if (isDragged) 0f else 1f,
                                        onClose = { onClose(index) },
                                    )
                                }
                            }
                        }
                    }
                }
                val di = draggingIndex
                if (di != null && di in book.tabs.indices && draggedWidth > 0) {
                    val viewportLeft = draggedNaturalLeft - scrollState.value + dragOffsetPx.roundToInt()
                    Box(
                        modifier = Modifier
                            .height(TabBarHeight)
                            .offset { IntOffset(viewportLeft, 0) },
                    ) {
                        TabChip(
                            tab = book.tabs[di],
                            isActive = di == book.activeIndex,
                            offsetPx = 0,
                            elevated = true,
                            alpha = 1f,
                            onClose = {},
                        )
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomStart),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    thickness = 1.dp,
                )
            }
        }
    }
}

private fun buildTabMenuItems(
    index: Int,
    tab: OpenTab,
    bookSize: Int,
    actions: TabContextActions,
): List<ContextMenuItem> {
    val items = mutableListOf<ContextMenuItem>()
    items += ContextMenuItem("Close\tCtrl+W") { actions.onClose(index) }
    if (bookSize > 1) {
        items += ContextMenuItem("Close Others") { actions.onCloseOthers(index) }
        if (index > 0) items += ContextMenuItem("Close Tabs to the Left") {
            actions.onCloseToLeft(index)
        }
        if (index < bookSize - 1) items += ContextMenuItem("Close Tabs to the Right") {
            actions.onCloseToRight(index)
        }
        items += ContextMenuItem("Close All") { actions.onCloseAll() }
        items += ContextMenuItem("Close Unmodified") { actions.onCloseUnmodified() }
    }
    items += ContextMenuItem(if (tab.isPinned) "Unpin Tab" else "Pin Tab") {
        actions.onTogglePin(index)
    }
    items += ContextMenuItem("Copy Path") { actions.onCopyAbsolutePath(index) }
    items += ContextMenuItem("Copy Relative Path") { actions.onCopyRelativePath(index) }
    items += ContextMenuItem("Show in Explorer") { actions.onShowInExplorer(index) }
    actions.onMoveToOtherPane?.let { fn ->
        items += ContextMenuItem("Move to Other Pane") { fn(index) }
    }
    actions.onSplit?.let { fn ->
        items += ContextMenuItem("Split with This Tab") { fn(index) }
    }
    items += ContextMenuItem("Rename File…") { actions.onRename(index) }
    return items
}

private fun widthOf(range: IntRange?): Float? =
    range?.let { (it.last - it.first).toFloat() }

private fun swapBounds(bounds: MutableMap<Int, IntRange>, a: Int, b: Int) {
    val ra = bounds[a]
    val rb = bounds[b]
    if (rb != null) bounds[a] = rb else bounds.remove(a)
    if (ra != null) bounds[b] = ra else bounds.remove(b)
}

private fun findTabAt(x: Float, bounds: Map<Int, IntRange>, count: Int): Int? {
    val px = x.toInt()
    for (i in 0 until count) {
        val range = bounds[i] ?: continue
        if (px in range) return i
    }
    return null
}

@Composable
private fun TabChip(
    tab: OpenTab,
    isActive: Boolean,
    offsetPx: Int,
    elevated: Boolean,
    alpha: Float,
    onClose: () -> Unit,
) {
    val name = tab.path.fileName?.toString() ?: tab.path.toString()
    val bg = if (isActive) MaterialTheme.colorScheme.background else Color.Transparent
    val nameColor =
        if (isActive) MaterialTheme.colorScheme.onBackground
        else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxHeight()
            .zIndex(if (elevated) 1f else 0f)
            .offset { IntOffset(offsetPx, 0) }
            .alpha(alpha),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .background(bg)
                .padding(start = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (tab.isPinned) {
                PinIndicator()
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = name,
                style = LocalTextStyle.current.copy(
                    color = nameColor,
                    fontSize = 12.sp,
                    fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                    lineHeight = 12.sp,
                    lineHeightStyle = CenterTight,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            CloseButton(dirty = tab.dirty, onClick = onClose)
        }
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        )
    }
}

@Composable
private fun PinIndicator() {
    val color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
    Box(
        modifier = Modifier
            .width(6.dp)
            .height(6.dp)
            .background(color = color, shape = androidx.compose.foundation.shape.CircleShape),
    )
}

@Composable
private fun CloseButton(dirty: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()
    val showDot = dirty && !isHovered
    Box(
        modifier = Modifier
            .width(16.dp)
            .height(16.dp)
            .background(
                color = if (isHovered)
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                else Color.Transparent,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (showDot) "●" else "×",
            style = LocalTextStyle.current.copy(
                color = if (showDot)
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                fontSize = if (showDot) 10.sp else 12.sp,
                lineHeight = 12.sp,
                lineHeightStyle = CenterTight,
            ),
        )
    }
}
