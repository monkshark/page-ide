package page.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import page.app.mvi.SideView
import page.ui.Glass
import page.ui.GlassTooltip

@Composable
internal fun ActivityRail(
    activeSideView: SideView?,
    onSelectSideView: (SideView) -> Unit,
    problemsOpen: Boolean,
    problemsCount: Int,
    onProblemsToggle: () -> Unit,
    terminalOpen: Boolean,
    onTerminalToggle: () -> Unit,
    outputOpen: Boolean,
    onOutputToggle: () -> Unit,
    settingsOpen: Boolean,
    onSettingsToggle: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(48.dp)
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RailItem(
            tooltip = "Files",
            selected = activeSideView == SideView.FILES,
            onClick = { onSelectSideView(SideView.FILES) },
            icon = { tint -> FilesGlyph(tint = tint, size = 16.dp) },
        )
        RailItem(
            tooltip = "Search",
            selected = activeSideView == SideView.SEARCH,
            onClick = { onSelectSideView(SideView.SEARCH) },
            icon = { tint -> SearchGlyph(tint = tint, size = 16.dp) },
        )
        RailItem(
            tooltip = "Source Control",
            selected = activeSideView == SideView.SOURCE_CONTROL,
            onClick = { onSelectSideView(SideView.SOURCE_CONTROL) },
            icon = { tint -> SourceControlGlyph(tint = tint, size = 16.dp) },
        )
        Spacer(Modifier.height(8.dp))
        RailItem(
            tooltip = "Problems",
            selected = problemsOpen,
            onClick = onProblemsToggle,
            badge = problemsCount,
            icon = { tint -> ProblemsGlyph(tint = tint, size = 16.dp) },
        )
        RailItem(
            tooltip = "Terminal",
            selected = terminalOpen,
            onClick = onTerminalToggle,
            icon = { tint -> TerminalGlyph(tint = tint, size = 16.dp) },
        )
        RailItem(
            tooltip = "Output",
            selected = outputOpen,
            onClick = onOutputToggle,
            icon = { tint -> OutputGlyph(tint = tint, size = 16.dp) },
        )
        Spacer(Modifier.weight(1f))
        RailItem(
            tooltip = "Settings",
            selected = settingsOpen,
            onClick = onSettingsToggle,
            icon = { tint -> SettingsGlyph(tint = tint, size = 16.dp) },
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
internal fun SideViewPlaceholder(title: String, modifier: Modifier = Modifier) {
    val colors = Glass.colors
    Box(
        modifier = modifier.background(colors.surfaceL1),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$title — coming soon",
            color = colors.muted,
            fontSize = Glass.type.ui,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun RailItem(
    tooltip: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable (tint: Color) -> Unit,
    badge: Int = 0,
) {
    val colors = Glass.colors
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val targetBg = when {
        selected -> colors.primarySoft
        hovered -> colors.primarySoft.copy(alpha = colors.primarySoft.alpha * 0.6f)
        else -> Color.Transparent
    }
    val bg by animateColorAsState(targetBg, tween(150))
    val tint by animateColorAsState(if (selected) colors.primary else colors.muted, tween(150))
    val barHeight by animateDpAsState(if (selected) 20.dp else 0.dp, tween(180))
    GlassTooltip(text = tooltip) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .hoverable(interactionSource)
                .clickable(interactionSource = interactionSource, indication = null) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(2.dp)
                    .height(barHeight)
                    .background(colors.accent),
            )
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(Glass.radius.xs))
                    .background(bg),
                contentAlignment = Alignment.Center,
            ) {
                icon(tint)
            }
            if (badge > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 9.dp, end = 9.dp)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(colors.danger),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (badge > 9) "9+" else badge.toString(),
                        color = colors.onPrimary,
                        fontSize = Glass.type.label,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
