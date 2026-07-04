package page.atlas.render

import androidx.compose.runtime.Composable
import page.ui.Glass
import page.ui.GlassColors

fun atlasRoleColors(colors: GlassColors): AtlasRoleColors = AtlasRoleColors(
    dependency = colors.primary,
    usedBy = colors.accent,
    cycle = colors.warn,
    hub = colors.error,
    path = colors.warn,
    neutral = colors.muted,
)

@Composable
fun atlasRoleColors(): AtlasRoleColors = atlasRoleColors(Glass.colors)
