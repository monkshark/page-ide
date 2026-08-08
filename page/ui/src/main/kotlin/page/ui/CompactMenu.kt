package page.ui

import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ContextMenuRepresentation
import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.window.rememberCursorPositionProvider

@Composable
fun CompactMenuContainer(
    minWidth: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    GlassSurface(
        level = GlassSurfaceLevel.Overlay,
        shape = RoundedCornerShape(Glass.radius.sm),
    ) {
        Column(
            modifier = Modifier
                .background(Glass.colors.surfaceRaised)
                .width(IntrinsicSize.Max)
                .widthIn(min = minWidth)
                .padding(vertical = 4.dp),
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
    icon: ImageVector? = null,
    danger: Boolean = false,
) {
    val colors = Glass.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val active = hovered && enabled
    val label色 = when {
        !enabled -> colors.faint
        danger -> colors.danger
        else -> colors.text
    }
    val iconTint = when {
        !enabled -> colors.faint.copy(alpha = 0.6f)
        danger && active -> colors.danger
        active -> colors.primary
        else -> colors.muted
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(Glass.radius.xs))
            .background(
                when {
                    !active -> Color.Transparent
                    danger -> colors.danger.copy(alpha = 0.16f)
                    else -> colors.primarySoft
                },
            )
            .hoverable(interaction, enabled = enabled)
            .clickable(enabled = enabled) { onClick() }
            .padding(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(14.dp),
            )
        } else {
            Spacer(Modifier.size(14.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = label色,
        )
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(20.dp))
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelSmall,
                color = colors.faint,
            )
        }
    }
}

@Composable
fun CompactMenuSeparator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .padding(horizontal = 8.dp)
            .height(1.dp)
            .background(Glass.colors.separator),
    )
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

class IconContextMenuItem(
    label: String,
    val icon: ImageVector? = null,
    val danger: Boolean = false,
    onClick: () -> Unit,
) : ContextMenuItem(label, onClick)

object CompactContextMenuRepresentation : ContextMenuRepresentation {
    @Composable
    override fun Representation(state: ContextMenuState, items: () -> List<ContextMenuItem>) {
        val status = state.status
        if (status is ContextMenuState.Status.Open) {
            Popup(
                onDismissRequest = { state.status = ContextMenuState.Status.Closed },
                popupPositionProvider = rememberCursorPositionProvider(),
                properties = PopupProperties(focusable = true),
            ) {
                CompactMenuContainer {
                    for (item in items()) {
                        val parts = item.label.split('\t', limit = 2)
                        val name = parts[0]
                        val shortcut = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
                        val decorated = item as? IconContextMenuItem
                        CompactMenuItem(
                            label = name,
                            trailing = shortcut,
                            icon = decorated?.icon,
                            danger = decorated?.danger ?: false,
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

