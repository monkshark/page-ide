package page.app

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull

data class ClickModifiers(val ctrl: Boolean, val shift: Boolean)

private data class DownInfo(
    val primary: Boolean,
    val secondary: Boolean,
    val ctrl: Boolean,
    val shift: Boolean,
)

private suspend fun AwaitPointerEventScope.awaitDownEvent(): DownInfo {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Main)
        val hasDown = event.changes.any { it.changedToDown() }
        if (hasDown) {
            return DownInfo(
                primary = event.buttons.isPrimaryPressed,
                secondary = event.buttons.isSecondaryPressed,
                ctrl = event.keyboardModifiers.isCtrlPressed,
                shift = event.keyboardModifiers.isShiftPressed,
            )
        }
    }
}

fun Modifier.treeRowGestures(
    key: Any?,
    onPrimaryClick: (ClickModifiers) -> Unit,
    onPrimaryDoubleClick: () -> Unit,
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
            primaryClick.value(ClickModifiers(down.ctrl, down.shift))
            val timeout = viewConfiguration.doubleTapTimeoutMillis
            val next = withTimeoutOrNull(timeout) { awaitDownEvent() }
            if (next != null && next.primary) {
                primaryDoubleClick.value()
            }
        }
    }
}
