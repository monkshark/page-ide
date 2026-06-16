package page.atlas.render

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import page.ui.Glass
import page.ui.GlassColors

@Immutable
data class AtlasRoleColors(
    val dependency: Color,
    val usedBy: Color,
    val cycle: Color,
    val hub: Color,
    val path: Color,
    val neutral: Color,
)

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
