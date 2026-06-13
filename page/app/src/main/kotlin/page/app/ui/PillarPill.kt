package page.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import page.ui.CompactDropdown
import page.ui.CompactMenuItem
import page.ui.Glass
import page.ui.GlassPalette
import page.ui.GlassSurface
import page.ui.GlassSurfaceLevel
import page.ui.GlassTooltip

@Composable
internal fun PillarPill(
    atlasActive: Boolean,
    onAtlasToggle: () -> Unit,
    currentPalette: GlassPalette,
    onSelectPalette: (GlassPalette) -> Unit,
    onCommandPalette: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Glass.colors
    GlassSurface(
        level = GlassSurfaceLevel.Raised,
        shape = RoundedCornerShape(28.dp),
        modifier = modifier.height(52.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp).height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PillChip(
                tooltip = "Pair — coming soon",
                active = false,
                enabled = false,
                onClick = {},
                icon = { tint -> PairGlyph(tint = tint, size = 18.dp) },
            )
            GlassChip(currentPalette = currentPalette, onSelectPalette = onSelectPalette)
            PillChip(
                tooltip = "Atlas",
                active = atlasActive,
                onClick = onAtlasToggle,
                icon = { tint -> AtlasGlyph(tint = tint, size = 18.dp) },
            )
            PillChip(
                tooltip = "Echo — coming soon",
                active = false,
                enabled = false,
                onClick = {},
                icon = { tint -> EchoGlyph(tint = tint, size = 18.dp) },
            )
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(24.dp)
                    .background(colors.separator),
            )
            Spacer(Modifier.width(4.dp))
            PillChip(
                tooltip = "AI — coming soon",
                active = false,
                enabled = false,
                onClick = {},
                icon = { tint -> AiGlyph(tint = tint, size = 18.dp) },
            )
            PillChip(
                tooltip = "Command Palette",
                active = false,
                onClick = onCommandPalette,
                icon = { tint -> CommandGlyph(tint = tint, size = 18.dp) },
            )
        }
    }
}

@Composable
private fun GlassChip(
    currentPalette: GlassPalette,
    onSelectPalette: (GlassPalette) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        PillChip(
            tooltip = "Appearance",
            active = expanded,
            onClick = { expanded = true },
            icon = { tint -> GlassGlyph(tint = tint, size = 18.dp) },
        )
        CompactDropdown(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            for (palette in GlassPalette.values()) {
                CompactMenuItem(
                    label = if (palette == currentPalette) "● ${palette.name}" else palette.name,
                    onClick = {
                        expanded = false
                        onSelectPalette(palette)
                    },
                )
            }
        }
    }
}

@Composable
private fun PillChip(
    tooltip: String,
    active: Boolean,
    onClick: () -> Unit,
    icon: @Composable (tint: Color) -> Unit,
    enabled: Boolean = true,
) {
    val colors = Glass.colors
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val targetBg = when {
        active -> colors.primarySoft
        enabled && hovered -> colors.primarySoft.copy(alpha = colors.primarySoft.alpha * 0.6f)
        else -> Color.Transparent
    }
    val targetTint = when {
        !enabled -> colors.faint
        active -> colors.primary
        else -> colors.muted
    }
    val bg by animateColorAsState(targetBg, tween(150))
    val tint by animateColorAsState(targetTint, tween(150))
    GlassTooltip(text = tooltip) {
        Box(
            modifier = Modifier
                .padding(horizontal = 3.dp)
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(bg)
                .hoverable(interactionSource)
                .let { if (enabled) it.clickable { onClick() } else it },
            contentAlignment = Alignment.Center,
        ) {
            icon(tint)
        }
    }
}
