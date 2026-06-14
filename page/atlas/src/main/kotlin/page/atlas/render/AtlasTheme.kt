package page.atlas.render

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import page.ui.Glass
import page.ui.GlassColors

internal data class AtlasColors(
    val surface: Color,
    val surfaceVariant: Color,
    val focus: Color,
    val module: Color,
    val relation: Color,
    val outline: Color,
    val label: Color,
    val text: Color,
    val cycle: Color,
)

internal fun atlasColorsFrom(c: GlassColors): AtlasColors = AtlasColors(
    surface = c.surfaceL2,
    surfaceVariant = c.surfaceL3,
    focus = c.primary,
    module = c.accent,
    relation = c.warn,
    outline = c.outline,
    label = c.muted,
    text = c.text,
    cycle = c.danger,
)

@Composable
@ReadOnlyComposable
internal fun atlasColors(): AtlasColors = atlasColorsFrom(Glass.colors)

val vcsImpactColor = Color(0xFFCC7832)

fun vcsColor(mark: VcsMark): Color = when (mark) {
    VcsMark.MODIFIED -> Color(0xFF6897BB)
    VcsMark.ADDED -> Color(0xFF629755)
    VcsMark.DELETED -> Color(0xFF808080)
}
