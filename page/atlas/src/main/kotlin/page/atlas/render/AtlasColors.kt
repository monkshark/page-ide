package page.atlas.render

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import page.ui.Glass
import page.ui.GlassColors

object AtlasInk {
    val incoming = Color(0xFF6E8BFF)
    val outgoing = Color(0xFF4FD3C7)
    val hub = Color(0xFFE8B341)
    val cycle = Color(0xFFF2727F)
    val canvas = Color(0xFF0A0D14)
    val panelTop = Color(0xFF0E141D)
    val panelBottom = Color(0xFF0A0E14)
    val nodeFill = Color(0xFF161C27)
    val nodeStroke = Color(0x14FFFFFF)
    val edge = Color(0xFF3A4456)
    val focusTop = Color(0xFF1D2532)
    val focusBottom = Color(0xFF121822)
    val focusStroke = Color(0x4778C8DC)
    val hubTop = Color(0xFF1B2230)
    val hubBottom = Color(0xFF141B27)
    val boxFill = Color(0xFF11161F)
    val cycleCardFill = Color(0xFF15110F)
    val hubCardFill = Color(0xFF141014)
    val bright = Color(0xFFF0F5FA)
    val label = Color(0xFFDBE4EE)
    val dim = Color(0xFF8893A3)
    val sub = Color(0xFF5C6776)
}

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
