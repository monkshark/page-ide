package page.app

import page.runtime.*

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draganddrop.DragAndDropTransferAction
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.DragAndDropTransferable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.nio.file.Path
import kotlin.math.hypot

private const val DRAG_START_THRESHOLD_PX = 4f

data class DragGestureCallbacks(
    val onDragStart: (path: Path, mode: TreeDragController.Mode) -> Unit,
    val onDragEnd: (committed: Boolean) -> Unit,
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
fun Modifier.treeRowDragSource(
    key: Any?,
    path: Path,
    enabled: Boolean,
    selectionProvider: () -> Set<Path>,
    rootProvider: () -> Path?,
    callbacks: DragGestureCallbacks,
): Modifier = composed {
    val cbState = rememberUpdatedState(callbacks)
    val enabledState = rememberUpdatedState(enabled)
    val pathState = rememberUpdatedState(path)
    val selProvider = rememberUpdatedState(selectionProvider)
    val rootProv = rememberUpdatedState(rootProvider)
    val textMeasurer = rememberTextMeasurer()
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
    val accentColor = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val pendingLabel = remember { mutableStateOf("") }
    val pendingMode = remember { mutableStateOf(TreeDragController.Mode.Move) }

    this.dragAndDropSource(
        drawDragDecoration = decoration@ {
            val label = pendingLabel.value
            if (label.isBlank()) return@decoration
            val labelStyle = TextStyle(
                color = onSurfaceColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            val layout = textMeasurer.measure(label, labelStyle)
            val padH = with(density) { 10.dp.toPx() }
            val padV = with(density) { 5.dp.toPx() }
            val badgeGap = with(density) { 6.dp.toPx() }
            val badgeRadius = with(density) { 7.dp.toPx() }
            val showBadge = pendingMode.value == TreeDragController.Mode.Copy
            val labelWidth = layout.size.width.toFloat()
            val labelHeight = layout.size.height.toFloat()
            val bgWidth = labelWidth + padH * 2 +
                if (showBadge) badgeGap + badgeRadius * 2 else 0f
            val bgHeight = labelHeight + padV * 2
            val anchorOffset = with(density) { 18.dp.toPx() }
            val topLeft = Offset(anchorOffset, anchorOffset)
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.18f),
                topLeft = topLeft + Offset(0f, 2f),
                size = Size(bgWidth, bgHeight),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            )
            drawRoundRect(
                color = surfaceColor,
                topLeft = topLeft,
                size = Size(bgWidth, bgHeight),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            )
            drawRoundRect(
                color = outlineColor,
                topLeft = topLeft,
                size = Size(bgWidth, bgHeight),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                style = Stroke(1.dp.toPx()),
            )
            drawText(
                textLayoutResult = layout,
                topLeft = topLeft + Offset(padH, padV),
            )
            if (showBadge) {
                val cx = topLeft.x + padH + labelWidth + badgeGap + badgeRadius
                val cy = topLeft.y + bgHeight / 2f
                drawCircle(color = accentColor, radius = badgeRadius, center = Offset(cx, cy))
                val plusStyle = TextStyle(
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                val plus = textMeasurer.measure("+", plusStyle)
                drawText(
                    textLayoutResult = plus,
                    topLeft = Offset(
                        x = cx - plus.size.width / 2f,
                        y = cy - plus.size.height / 2f,
                    ),
                )
            }
        },
    ) {
        if (!enabledState.value) return@dragAndDropSource
        awaitEachGesture {
            val initial = awaitPointerEvent(PointerEventPass.Initial)
            val down = initial.changes.firstOrNull { it.changedToDown() } ?: return@awaitEachGesture
            if (!initial.buttons.isPrimaryPressed) return@awaitEachGesture
            val startLocal = down.position
            var drag = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.firstOrNull() ?: continue
                if (event.type == PointerEventType.Release || !event.buttons.isPrimaryPressed) {
                    if (drag) cbState.value.onDragEnd(false)
                    return@awaitEachGesture
                }
                if (!drag) {
                    val delta = change.position - startLocal
                    if (hypot(delta.x.toDouble(), delta.y.toDouble()) > DRAG_START_THRESHOLD_PX) {
                        drag = true
                        val mode = if (event.keyboardModifiers.isCtrlPressed)
                            TreeDragController.Mode.Copy else TreeDragController.Mode.Move
                        val grabbed = pathState.value
                        val effective = TreeDragController.effectiveDragPaths(grabbed, selProvider.value())
                            .filter { it != rootProv.value() }
                        if (effective.isEmpty()) {
                            return@awaitEachGesture
                        }
                        val first = effective.first().fileName?.toString() ?: effective.first().toString()
                        pendingLabel.value = if (effective.size > 1) "$first + ${effective.size - 1}" else first
                        pendingMode.value = mode
                        cbState.value.onDragStart(grabbed, mode)
                        val tx = TreeOutboundTransferable.fromPaths(effective, mode)
                        startTransfer(
                            DragAndDropTransferData(
                                transferable = DragAndDropTransferable(tx),
                                supportedActions = listOf(
                                    DragAndDropTransferAction.Move,
                                    DragAndDropTransferAction.Copy,
                                    DragAndDropTransferAction.Link,
                                ),
                                onTransferCompleted = { _ -> cbState.value.onDragEnd(true) },
                            )
                        )
                        change.consume()
                    }
                } else {
                    change.consume()
                }
            }
        }
    }
}
