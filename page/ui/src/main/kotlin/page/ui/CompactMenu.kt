package page.ui

import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ContextMenuRepresentation
import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

@Composable
fun CompactMenuContainer(
    minWidth: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .width(IntrinsicSize.Max)
                .widthIn(min = minWidth)
                .padding(vertical = 2.dp),
        ) {
            content()
        }
    }
}

@Composable
fun CompactMenuItem(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    trailing: String? = null,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val fg = if (enabled) onSurface else muted.copy(alpha = 0.5f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            .padding(start = 8.dp, end = 16.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelSmall,
                color = muted,
            )
        }
    }
}

@Composable
fun CompactMenuSlot(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            .padding(start = 8.dp, end = 16.dp, top = 2.dp, bottom = 2.dp),
    ) {
        content()
    }
}

object CompactContextMenuRepresentation : ContextMenuRepresentation {
    @Composable
    override fun Representation(state: ContextMenuState, items: () -> List<ContextMenuItem>) {
        val status = state.status
        if (status is ContextMenuState.Status.Open) {
            val positionPx = status.rect.center
            Popup(
                onDismissRequest = { state.status = ContextMenuState.Status.Closed },
                popupPositionProvider = rememberContextMenuPositionProvider(positionPx),
                properties = PopupProperties(focusable = true),
            ) {
                CompactMenuContainer {
                    for (item in items()) {
                        CompactMenuItem(
                            label = item.label,
                            onClick = {
                                state.status = ContextMenuState.Status.Closed
                                item.onClick()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CompactDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    minWidth: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    if (!expanded) return
    val density = LocalDensity.current
    val offsetPx = with(density) { IntOffset(offset.x.roundToPx(), offset.y.roundToPx()) }
    Popup(
        onDismissRequest = onDismissRequest,
        popupPositionProvider = rememberAnchorBelowPositionProvider(offsetPx),
        properties = PopupProperties(focusable = true),
    ) {
        CompactMenuContainer(minWidth = minWidth) { content() }
    }
}

@Composable
private fun rememberAnchorBelowPositionProvider(offsetPx: IntOffset): PopupPositionProvider {
    return remember(offsetPx) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val desiredX = anchorBounds.left + offsetPx.x
                val desiredY = anchorBounds.bottom + offsetPx.y
                val maxX = windowSize.width - popupContentSize.width
                val maxY = windowSize.height - popupContentSize.height
                val clampedX = desiredX.coerceIn(0, maxOf(0, maxX))
                val clampedY = if (desiredY > maxY) {
                    val above = anchorBounds.top - popupContentSize.height + offsetPx.y
                    above.coerceAtLeast(0)
                } else {
                    desiredY.coerceAtLeast(0)
                }
                return IntOffset(clampedX, clampedY)
            }
        }
    }
}

@Composable
private fun rememberContextMenuPositionProvider(positionPx: Offset): PopupPositionProvider {
    return remember(positionPx) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val x = anchorBounds.left + positionPx.x.toInt()
                val y = anchorBounds.top + positionPx.y.toInt()
                val maxX = windowSize.width - popupContentSize.width
                val maxY = windowSize.height - popupContentSize.height
                return IntOffset(
                    x.coerceIn(0, maxOf(0, maxX)),
                    y.coerceIn(0, maxOf(0, maxY)),
                )
            }
        }
    }
}
