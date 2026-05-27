package page.app

import page.runtime.*

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.zIndex
import kotlinx.coroutines.withTimeout

@Composable
fun ReorderableFileColumn(
    keys: List<String>,
    onMove: (newKeys: List<String>) -> Unit,
    modifier: Modifier = Modifier,
    onGroupClick: (Int) -> Unit = {},
    headerContent: @Composable (index: Int) -> Unit,
    bodyContent: @Composable (index: Int) -> Unit,
) {
    val groupBounds = remember { mutableStateMapOf<Int, IntRange>() }
    val headerBounds = remember { mutableStateMapOf<Int, IntRange>() }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var draggedNaturalTop by remember { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnGroupClick by rememberUpdatedState(onGroupClick)

    var effectiveKeys by remember { mutableStateOf(keys) }
    LaunchedEffect(keys) {
        if (draggingKey == null) effectiveKeys = keys
    }

    LaunchedEffect(effectiveKeys.size) {
        groupBounds.keys.removeAll { it >= effectiveKeys.size }
        headerBounds.keys.removeAll { it >= effectiveKeys.size }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downY = down.position.y.toInt()
                        val initialIndex = headerBounds.entries
                            .firstOrNull { downY in it.value }
                            ?.key ?: return@awaitEachGesture

                        val longPressTimeout = viewConfiguration.longPressTimeoutMillis * 3 / 5
                        var longPressFired = false
                        var stolen = false
                        var preDragDy = 0f
                        try {
                            withTimeout(longPressTimeout) {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: return@withTimeout
                                    if (change.isConsumed) {
                                        stolen = true
                                        return@withTimeout
                                    }
                                    if (change.changedToUpIgnoreConsumed()) {
                                        return@withTimeout
                                    }
                                    preDragDy += change.positionChange().y
                                }
                            }
                            if (!stolen) currentOnGroupClick(initialIndex)
                            return@awaitEachGesture
                        } catch (_: PointerEventTimeoutCancellationException) {
                            longPressFired = true
                        }

                        if (longPressFired) {
                            val bounds = groupBounds[initialIndex] ?: return@awaitEachGesture
                            val initialKey = effectiveKeys.getOrNull(initialIndex)
                                ?: return@awaitEachGesture
                            val initialBoundsTop = bounds.first
                            val downY = down.position.y
                            draggingKey = initialKey
                            draggingIndex = initialIndex
                            draggedNaturalTop = initialBoundsTop
                            dragOffsetPx = preDragDy

                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: break
                                    if (change.changedToUpIgnoreConsumed()) {
                                        change.consume()
                                        break
                                    }
                                    change.consume()

                                    val cursorDy = change.position.y - downY
                                    var cur = draggingIndex ?: break

                                    while (true) {
                                        val swapShift = (initialBoundsTop - draggedNaturalTop).toFloat()
                                        val effOffset = cursorDy + swapShift
                                        if (effOffset > 0f) {
                                            val nextH = heightOf(groupBounds[cur + 1]) ?: break
                                            if (effOffset <= nextH / 2f) break
                                            effectiveKeys = moveKey(effectiveKeys, cur, cur + 1)
                                            swapBounds(groupBounds, cur, cur + 1)
                                            swapBounds(headerBounds, cur, cur + 1)
                                            cur += 1
                                            draggingIndex = cur
                                            draggedNaturalTop += nextH.toInt()
                                        } else if (effOffset < 0f) {
                                            val prevH = heightOf(groupBounds[cur - 1]) ?: break
                                            if (effOffset >= -prevH / 2f) break
                                            effectiveKeys = moveKey(effectiveKeys, cur, cur - 1)
                                            swapBounds(groupBounds, cur, cur - 1)
                                            swapBounds(headerBounds, cur, cur - 1)
                                            cur -= 1
                                            draggingIndex = cur
                                            draggedNaturalTop -= prevH.toInt()
                                        } else break
                                    }

                                    val swapShift = (initialBoundsTop - draggedNaturalTop).toFloat()
                                    val newOffset = cursorDy + swapShift
                                    if (newOffset != dragOffsetPx) dragOffsetPx = newOffset
                                }
                            } finally {
                                val finalKeys = effectiveKeys
                                draggingKey = null
                                draggingIndex = null
                                dragOffsetPx = 0f
                                if (finalKeys != keys) currentOnMove(finalKeys)
                            }
                        }
                    }
                },
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                effectiveKeys.forEachIndexed { localIdx, key ->
                    val parentIdx = keys.indexOf(key)
                    if (parentIdx < 0) return@forEachIndexed
                    key(key) {
                        val isDragged = key == draggingKey
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { coords ->
                                    val top = coords.positionInParent().y.toInt()
                                    val bottom = top + coords.size.height
                                    groupBounds[localIdx] = top..bottom
                                }
                                .alpha(if (isDragged) 0.25f else 1f),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onGloballyPositioned { coords ->
                                        val groupTop = groupBounds[localIdx]?.first ?: 0
                                        val localTop = coords.positionInParent().y.toInt()
                                        val top = groupTop + localTop
                                        val bottom = top + coords.size.height
                                        headerBounds[localIdx] = top..bottom
                                    },
                            ) {
                                headerContent(parentIdx)
                            }
                            bodyContent(parentIdx)
                        }
                    }
                }
            }

            val dk = draggingKey
            val di = if (dk != null) effectiveKeys.indexOf(dk).takeIf { it >= 0 } else null
            if (di != null) {
                val parentIdx = keys.indexOf(dk)
                if (parentIdx >= 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                translationY = draggedNaturalTop + dragOffsetPx
                                alpha = 0.95f
                            }
                            .zIndex(1f),
                    ) {
                        headerContent(parentIdx)
                    }
                }
            }
        }
    }
}

private fun heightOf(range: IntRange?): Float? =
    range?.let { (it.last - it.first).toFloat() }

private fun swapBounds(bounds: MutableMap<Int, IntRange>, a: Int, b: Int) {
    val ra = bounds[a]
    val rb = bounds[b]
    if (rb != null) bounds[a] = rb else bounds.remove(a)
    if (ra != null) bounds[b] = ra else bounds.remove(b)
}

internal fun moveKey(keys: List<String>, from: Int, to: Int): List<String> {
    if (from !in keys.indices) return keys
    if (to !in keys.indices) return keys
    if (from == to) return keys
    val mutable = keys.toMutableList()
    val moved = mutable.removeAt(from)
    mutable.add(to, moved)
    return mutable
}
