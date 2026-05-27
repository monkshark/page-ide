package page.app

import page.runtime.*

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.hypot

data class ClickModifiers(val ctrl: Boolean, val shift: Boolean)

private data class DownInfo(
    val primary: Boolean,
    val secondary: Boolean,
    val ctrl: Boolean,
    val shift: Boolean,
    val position: androidx.compose.ui.geometry.Offset,
)

private const val CLICK_DRAG_THRESHOLD_PX = 4f

private suspend fun AwaitPointerEventScope.awaitDownEvent(): DownInfo {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Main)
        val downChange = event.changes.firstOrNull { it.changedToDown() }
        if (downChange != null) {
            return DownInfo(
                primary = event.buttons.isPrimaryPressed,
                secondary = event.buttons.isSecondaryPressed,
                ctrl = event.keyboardModifiers.isCtrlPressed,
                shift = event.keyboardModifiers.isShiftPressed,
                position = downChange.position,
            )
        }
    }
}

fun Modifier.chevronToggleGesture(
    key: Any?,
    onToggle: (recursive: Boolean) -> Unit,
): Modifier = composed {
    val cb = rememberUpdatedState(onToggle)
    pointerInput(key) {
        awaitEachGesture {
            val down = awaitDownEvent()
            if (down.primary) {
                cb.value(down.ctrl)
            }
        }
    }
}

fun Modifier.treeRowGestures(
    key: Any?,
    onPrimaryClick: (ClickModifiers) -> Unit,
    onPrimaryDoubleClick: (ClickModifiers) -> Unit,
    onSecondaryDown: () -> Unit,
): Modifier = composed {
    val primaryClick = rememberUpdatedState(onPrimaryClick)
    val primaryDoubleClick = rememberUpdatedState(onPrimaryDoubleClick)
    val secondaryDown = rememberUpdatedState(onSecondaryDown)
    pointerInput(key) {
        awaitEachGesture {
            val down = awaitDownEvent()
            if (down.secondary) {
                secondaryDown.value()
                return@awaitEachGesture
            }
            if (!down.primary) return@awaitEachGesture

            var moved = false
            var released = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.firstOrNull() ?: continue
                if (event.type == PointerEventType.Release || !event.buttons.isPrimaryPressed) {
                    released = true
                    break
                }
                val delta = change.position - down.position
                if (hypot(delta.x.toDouble(), delta.y.toDouble()) > CLICK_DRAG_THRESHOLD_PX) {
                    moved = true
                    break
                }
            }

            if (!released || moved) return@awaitEachGesture

            primaryClick.value(ClickModifiers(down.ctrl, down.shift))
            val timeout = viewConfiguration.doubleTapTimeoutMillis
            val next = withTimeoutOrNull(timeout) { awaitDownEvent() }
            if (next != null && next.primary) {
                primaryDoubleClick.value(ClickModifiers(next.ctrl, next.shift))
            }
        }
    }
}
