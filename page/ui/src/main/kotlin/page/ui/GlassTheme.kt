package page.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember

object Glass {
    val colors: GlassColors
        @Composable @ReadOnlyComposable
        get() = LocalGlassTokens.current.color

    val type: GlassType
        @Composable @ReadOnlyComposable
        get() = LocalGlassTokens.current.type

    val space: GlassSpace
        @Composable @ReadOnlyComposable
        get() = LocalGlassTokens.current.space

    val motion: GlassMotion
        @Composable @ReadOnlyComposable
        get() = LocalGlassTokens.current.motion

    val radius: GlassRadius
        @Composable @ReadOnlyComposable
        get() = LocalGlassTokens.current.radius

    val elevation: GlassElevation
        @Composable @ReadOnlyComposable
        get() = LocalGlassTokens.current.elevation

    val palette: GlassPalette
        @Composable @ReadOnlyComposable
        get() = LocalGlassTokens.current.palette
}

@Composable
fun GlassTheme(palette: GlassPalette = GlassPalette.Signature, content: @Composable () -> Unit) {
    val tokens = remember(palette) { glassTokensFor(palette) }
    val scheme = remember(tokens) {
        val c = tokens.color
        if (c.isLight) lightColorScheme(
            primary = c.primary,
            onPrimary = c.onPrimary,
            secondary = c.accent,
            background = c.background,
            onBackground = c.text,
            surface = c.surfaceL2,
            onSurface = c.text,
            surfaceVariant = c.surfaceL3,
            onSurfaceVariant = c.muted,
            outline = c.outline,
            error = c.danger,
        ) else darkColorScheme(
            primary = c.primary,
            onPrimary = c.onPrimary,
            secondary = c.accent,
            background = c.background,
            onBackground = c.text,
            surface = c.surfaceL2,
            onSurface = c.text,
            surfaceVariant = c.surfaceL3,
            onSurfaceVariant = c.muted,
            outline = c.outline,
            error = c.danger,
        )
    }
    CompositionLocalProvider(LocalGlassTokens provides tokens) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
