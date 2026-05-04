package page.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import page.editor.OpenTab
import page.editor.TabBook
import kotlin.math.abs
import kotlin.math.roundToInt

private val TabBarHeight = 32.dp
private val CenterTight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both,
)

@Composable
fun TabBar(
    book: TabBook,
    onActivate: (Int) -> Unit,
    onClose: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetPx by remember { mutableStateOf(0f) }
    val tabBounds = remember { mutableStateMapOf<Int, IntRange>() }

    LaunchedEffect(book.tabs.size) {
        tabBounds.keys.removeAll { it >= book.tabs.size }
    }

    Surface(
        modifier = modifier.fillMaxWidth().height(TabBarHeight),
        color = MaterialTheme.colorScheme.surface,
    ) {
        if (book.tabs.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth())
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(book.tabs.size) {
                        val touchSlop = viewConfiguration.touchSlop
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = true)
                            val tappedIndex = findTabAt(down.position.x, tabBounds, book.tabs.size)
                                ?: return@awaitEachGesture
                            onActivate(tappedIndex)
                            draggingIndex = tappedIndex
                            dragOffsetPx = 0f
                            var crossedSlop = false
                            var preDx = 0f

                            drag(down.id) { change ->
                                val dx = change.positionChange().x
                                preDx += dx
                                if (!crossedSlop && abs(preDx) > touchSlop) {
                                    crossedSlop = true
                                }
                                if (crossedSlop) {
                                    dragOffsetPx += dx
                                    var cur = draggingIndex ?: return@drag
                                    if (dragOffsetPx > 0f) {
                                        while (true) {
                                            val rightW = widthOf(tabBounds[cur + 1]) ?: break
                                            if (dragOffsetPx <= rightW / 2f) break
                                            onMove(cur, cur + 1)
                                            swapBounds(tabBounds, cur, cur + 1)
                                            cur += 1
                                            draggingIndex = cur
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
                                            dragOffsetPx += leftW
                                        }
                                    }
                                    change.consume()
                                }
                            }
                            draggingIndex = null
                            dragOffsetPx = 0f
                        }
                    },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    book.tabs.forEachIndexed { index, tab ->
                        TabChip(
                            tab = tab,
                            isActive = index == book.activeIndex,
                            offsetPx = if (index == draggingIndex) dragOffsetPx.roundToInt() else 0,
                            onClose = { onClose(index) },
                            onBoundsChanged = { left, right ->
                                tabBounds[index] = left..right
                            },
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
    onClose: () -> Unit,
    onBoundsChanged: (Int, Int) -> Unit,
) {
    val name = tab.path.fileName?.toString() ?: tab.path.toString()
    val bg = if (isActive) MaterialTheme.colorScheme.background else Color.Transparent
    val nameColor =
        if (isActive) MaterialTheme.colorScheme.onBackground
        else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxHeight()
            .offset { IntOffset(offsetPx, 0) }
            .onGloballyPositioned { coords ->
                val left = coords.positionInParent().x.toInt()
                val right = left + coords.size.width
                onBoundsChanged(left, right)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .background(bg)
                .padding(start = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
