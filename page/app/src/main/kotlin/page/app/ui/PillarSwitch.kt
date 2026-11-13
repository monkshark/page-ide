package page.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import page.ui.CompactDropdown
import page.ui.CompactMenuItem
import page.ui.Glass
import page.ui.GlassPalette

internal fun paletteLabel(palette: GlassPalette): String =
    palette.name.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ").lowercase().replaceFirstChar { it.uppercase() }

@Composable
internal fun PillarSwitch(
    atlasActive: Boolean,
    onAtlasToggle: () -> Unit,
    currentPalette: GlassPalette,
    onSelectPalette: (GlassPalette) -> Unit,
    onCommandPalette: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Glass.colors
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = modifier
            .height(28.dp)
            .clip(shape)
            .background(colors.surfaceL2)
            .border(1.dp, colors.separator, shape)
            .padding(horizontal = 2.dp)
            .testTag("pillar-switch"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        var paletteOpen by remember { mutableStateOf(false) }
        Box {
            SwitchSegment("Glass", active = paletteOpen, onClick = { paletteOpen = true }) { tint -> GlassGlyph(tint = tint, size = 14.dp) }
            CompactDropdown(expanded = paletteOpen, onDismissRequest = { paletteOpen = false }) {
                for (palette in GlassPalette.values()) {
                    CompactMenuItem(
                        label = if (palette == currentPalette) "● ${paletteLabel(palette)}" else paletteLabel(palette),
                        onClick = {
                            paletteOpen = false
                            onSelectPalette(palette)
                        },
                    )
                }
            }
        }
        SwitchSegment("Atlas", active = atlasActive, onClick = onAtlasToggle) { tint -> AtlasGlyph(tint = tint, size = 14.dp) }
        Box(Modifier.width(1.dp).height(16.dp).background(colors.separator))
        SwitchSegment("Commands", active = false, onClick = onCommandPalette) { tint -> CommandGlyph(tint = tint, size = 14.dp) }
    }
}

@Composable
private fun SwitchSegment(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    icon: @Composable (tint: Color) -> Unit,
) {
    val colors = Glass.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg by animateColorAsState(
        when {
            active -> colors.surfaceRaised
            hovered -> colors.highlightEdge
            else -> Color.Transparent
        },
        tween(150),
    )
    val tint = if (active || hovered) colors.text else colors.muted
    Row(
        modifier = Modifier
            .height(24.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp)
            .testTag("pillar-$label"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon(tint)
        Text(label, color = tint, fontSize = 12.sp)
    }
}
