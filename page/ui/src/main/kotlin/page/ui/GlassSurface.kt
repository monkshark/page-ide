package page.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

enum class GlassSurfaceLevel { Flat, Raised, Overlay }

@Composable
fun GlassSurface(
    level: GlassSurfaceLevel = GlassSurfaceLevel.Raised,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Glass.radius.md),
    borderColor: Color? = null,
    content: @Composable () -> Unit,
) {
    val colors = Glass.colors
    val elevation = Glass.elevation
    val shadow = when (level) {
        GlassSurfaceLevel.Flat -> elevation.flat
        GlassSurfaceLevel.Raised -> elevation.raised
        GlassSurfaceLevel.Overlay -> elevation.overlay
    }
    val background = when (level) {
        GlassSurfaceLevel.Flat -> colors.surfaceL2
        GlassSurfaceLevel.Raised -> colors.surfaceRaised
        GlassSurfaceLevel.Overlay -> colors.surfaceOverlay
    }
    val edge = borderColor ?: when (level) {
        GlassSurfaceLevel.Flat -> colors.separator
        else -> colors.highlightEdge
    }
    val shadowed = if (shadow.alpha > 0f) {
        modifier.shadow(
            elevation = shadow.blur,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = shadow.alpha),
            spotColor = Color.Black.copy(alpha = shadow.alpha),
        )
    } else {
        modifier
    }
    Box(
        modifier = shadowed
            .clip(shape)
            .background(background)
            .border(1.dp, edge, shape),
    ) {
        content()
    }
}
