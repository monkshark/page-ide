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

    val palette: GlassPalette
        @Composable @ReadOnlyComposable
        get() = LocalGlassTokens.current.palette
}

@Composable
fun GlassTheme(palette: GlassPalette = GlassPalette.Cool, content: @Composable () -> Unit) {
    val tokens = remember(palette) { glassTokensFor(palette) }
    val scheme = remember(tokens) {
        val c = tokens.color
        if (c.isLight) lightColorScheme(
            primary = c.primary,
            onPrimary = c.onPrimary,
            secondary = c.accent,
            background = c.background,
            onBackground = c.text,
            surface = c.surface,
            onSurface = c.text,
            surfaceVariant = c.surfaceRaised,
            onSurfaceVariant = c.muted,
            outline = c.outline,
            error = c.error,
        ) else darkColorScheme(
            primary = c.primary,
            onPrimary = c.onPrimary,
            secondary = c.accent,
            background = c.background,
            onBackground = c.text,
            surface = c.surface,
            onSurface = c.text,
            surfaceVariant = c.surfaceRaised,
            onSurfaceVariant = c.muted,
            outline = c.outline,
            error = c.error,
        )
    }
    CompositionLocalProvider(LocalGlassTokens provides tokens) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
