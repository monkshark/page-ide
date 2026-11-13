package page.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import page.app.input.ActionCatalog
import page.app.input.ActionMenus
import page.app.input.ActionSpec
import page.app.state.LayoutUiState
import page.ui.CompactMenuContainer
import page.ui.CompactMenuItem
import page.ui.Glass
import page.ui.Platform

private val COLLAPSE_BELOW = 900.dp

internal const val TITLE_MENU_COMPACT = "__compact"

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun TitleBarMenus(ui: LayoutUiState) {
    if (Platform.isMac) return
    val windowWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }

    if (windowWidth < COLLAPSE_BELOW) {
        MenuButton(
            label = "Menu",
            open = ui.titleMenu != null,
            onPositioned = { x -> if (ui.titleMenu == TITLE_MENU_COMPACT) ui.titleMenuX = x },
            onClick = { x ->
                ui.titleMenuX = x
                ui.titleMenu = if (ui.titleMenu == null) TITLE_MENU_COMPACT else null
            },
            onHover = { x ->
                ui.titleMenuX = x
                ui.titleMenu = TITLE_MENU_COMPACT
            },
            hoverOpensAfter = { if (ui.titleMenu == null) HOVER_OPEN_DELAY_MS else 0L },
        )
        return
    }

    Row(modifier = Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
        for (menu in ActionMenus.all) {
            MenuButton(
                label = menu.title,
                open = ui.titleMenu == menu.title,
                onPositioned = { x -> if (ui.titleMenu == menu.title) ui.titleMenuX = x },
                onClick = { x ->
                    ui.titleMenuX = x
                    ui.titleMenu = if (ui.titleMenu == menu.title) null else menu.title
                },
                onHover = { x ->
                    if (ui.titleMenu != menu.title) {
                        ui.titleMenuX = x
                        ui.titleMenu = menu.title
                    }
                },
                hoverOpensAfter = { if (ui.titleMenu == null) HOVER_OPEN_DELAY_MS else 0L },
            )
        }
    }
}

@Composable
internal fun TitleMenuDropdown(ui: LayoutUiState, onRun: (ActionSpec) -> Unit) {
    val open = ui.titleMenu ?: return
    val density = LocalDensity.current
    val actions = if (open == TITLE_MENU_COMPACT) {
        ActionMenus.all.flatMap { it.actions }
    } else {
        ActionMenus.all.firstOrNull { it.title == open }?.actions.orEmpty()
    }
    if (actions.isEmpty()) return
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .padding(top = TITLE_BAR_HEIGHT)
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { ui.titleMenu = null },
        )
        Box(
            modifier = Modifier.offset(
                x = with(density) { ui.titleMenuX.toDp() },
                y = TITLE_BAR_HEIGHT,
            ),
        ) {
            CompactMenuContainer(minWidth = 220.dp) {
                for (action in actions) {
                    CompactMenuItem(
                        label = action.label,
                        icon = ActionIcons.forId(action.id),
                        trailing = ActionCatalog.label(action),
                        onClick = {
                            ui.titleMenu = null
                            onRun(action)
                        },
                    )
                }
            }
        }
    }
}

private val TITLE_BAR_HEIGHT = 36.dp

private const val HOVER_OPEN_DELAY_MS = 650L

@Composable
private fun MenuButton(
    label: String,
    open: Boolean,
    onPositioned: (Int) -> Unit,
    onClick: (Int) -> Unit,
    onHover: (Int) -> Unit,
    hoverOpensAfter: () -> Long = { 0L },
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val colors = Glass.colors
    var x by remember { androidx.compose.runtime.mutableStateOf(0) }
    LaunchedEffect(hovered) {
        if (!hovered) return@LaunchedEffect
        val wait = hoverOpensAfter()
        if (wait > 0L) kotlinx.coroutines.delay(wait)
        onHover(x)
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = if (open) colors.onPrimary else colors.text,
        modifier = Modifier
            .onGloballyPositioned { coords ->
                x = coords.positionInWindow().x.toInt()
                onPositioned(x)
            }
            .clip(RoundedCornerShape(Glass.radius.xs))
            .background(
                when {
                    open -> colors.primary
                    hovered -> colors.highlightEdge
                    else -> Color.Transparent
                },
            )
            .hoverable(interaction)
            .clickable { onClick(x) }
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
