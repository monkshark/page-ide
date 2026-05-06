package page.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.awt.Cursor
import page.editor.SplitOrientation
import page.editor.SplitPaneState

@Composable
fun SplitPane(
    state: SplitPaneState,
    onStateChange: (SplitPaneState) -> Unit,
    orientation: SplitOrientation,
    modifier: Modifier = Modifier,
    dividerThickness: Dp = 4.dp,
    dividerColor: Color = MaterialTheme.colorScheme.outline,
    firstZIndex: Float = 0f,
    secondZIndex: Float = 0f,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val latestState by rememberUpdatedState(state)
    val latestOnChange by rememberUpdatedState(onStateChange)

    BoxWithConstraints(modifier = modifier) {
        val totalPx = with(density) {
            when (orientation) {
                SplitOrientation.HORIZONTAL -> maxWidth.toPx()
                SplitOrientation.VERTICAL -> maxHeight.toPx()
            }
        }
        val dividerPx = with(density) { dividerThickness.toPx() }
        val effectiveTotal = (totalPx - dividerPx).coerceAtLeast(0f)
        val ratio = state.clamped
        val firstWeight = ratio.coerceAtLeast(0.0001f)
        val secondWeight = (1f - ratio).coerceAtLeast(0.0001f)

        val onDrag: (Float) -> Unit = { delta ->
            latestOnChange(latestState.dragBy(delta, effectiveTotal))
        }

        when (orientation) {
            SplitOrientation.HORIZONTAL -> Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(firstWeight).fillMaxHeight().zIndex(firstZIndex)) { first() }
                Divider(
                    orientation = orientation,
                    thickness = dividerThickness,
                    color = dividerColor,
                    onDrag = onDrag,
                )
                Box(modifier = Modifier.weight(secondWeight).fillMaxHeight().zIndex(secondZIndex)) { second() }
            }
            SplitOrientation.VERTICAL -> Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(firstWeight).fillMaxWidth().zIndex(firstZIndex)) { first() }
                Divider(
                    orientation = orientation,
                    thickness = dividerThickness,
                    color = dividerColor,
                    onDrag = onDrag,
                )
                Box(modifier = Modifier.weight(secondWeight).fillMaxWidth().zIndex(secondZIndex)) { second() }
            }
        }
    }
}

@Composable
private fun Divider(
    orientation: SplitOrientation,
    thickness: Dp,
    color: Color,
    onDrag: (Float) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()
    val dragHandleColor = if (isHovered) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else color

    val cursor = when (orientation) {
        SplitOrientation.HORIZONTAL -> Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)
        SplitOrientation.VERTICAL -> Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR)
    }

    val sizeMod = when (orientation) {
        SplitOrientation.HORIZONTAL -> Modifier.width(thickness).fillMaxHeight()
        SplitOrientation.VERTICAL -> Modifier.height(thickness).fillMaxWidth()
    }

    Box(
        modifier = sizeMod
            .pointerHoverIcon(PointerIcon(cursor))
            .hoverable(interaction)
            .pointerInput(orientation) {
                detectDragGestures(onDrag = { _, drag ->
                    val delta = when (orientation) {
                        SplitOrientation.HORIZONTAL -> drag.x
                        SplitOrientation.VERTICAL -> drag.y
                    }
                    onDrag(delta)
                })
            }
            .background(dragHandleColor),
    )
}
