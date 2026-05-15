package page.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class GlassPalette { Cool, Warm, Frost, Forest, Midnight, Sand }

@Immutable
data class GlassColors(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val outline: Color,
    val primary: Color,
    val onPrimary: Color,
    val accent: Color,
    val text: Color,
    val muted: Color,
    val error: Color,
    val warn: Color,
    val syntax: SyntaxPalette,
    val isLight: Boolean,
)

@Immutable
data class GlassType(
    val label: TextUnit,
    val ui: TextUnit,
    val body: TextUnit,
    val code: TextUnit,
    val title: TextUnit,
)

@Immutable
data class GlassSpace(
    val xs: Dp,
    val sm: Dp,
    val md: Dp,
    val lg: Dp,
    val xl: Dp,
)

@Immutable
data class GlassMotion(
    val fast: Int,
    val base: Int,
    val slow: Int,
    val easing: Easing,
)

@Immutable
data class GlassTokens(
    val palette: GlassPalette,
    val color: GlassColors,
    val type: GlassType,
    val space: GlassSpace,
    val motion: GlassMotion,
)

internal val LocalGlassTokens = staticCompositionLocalOf<GlassTokens> {
    error("GlassTokens not provided — wrap UI in GlassTheme { ... }")
}

private val DefaultType = GlassType(
    label = 11.sp,
    ui = 12.sp,
    body = 13.sp,
    code = 13.sp,
    title = 14.sp,
)

private val DefaultSpace = GlassSpace(
    xs = 4.dp,
    sm = 8.dp,
    md = 12.dp,
    lg = 16.dp,
    xl = 24.dp,
)

private val DefaultMotion = GlassMotion(
    fast = 100,
    base = 200,
    slow = 320,
    easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f),
)

private val CoolSyntax = SyntaxPalette(
    keyword = Color(0xFFFF7B72),
    string = Color(0xFFA5D6FF),
    number = Color(0xFF79C0FF),
    comment = Color(0xFF8B949E),
    docComment = Color(0xFF7CA1A8),
    todoTag = Color(0xFFFF9F50),
    annotation = Color(0xFFD2A8FF),
    type = Color(0xFFFFA657),
    identifier = Color(0xFFFFD580),
)

private val WarmSyntax = SyntaxPalette(
    keyword = Color(0xFFE07A5F),
    string = Color(0xFFD4A574),
    number = Color(0xFFC99A6B),
    comment = Color(0xFFA89C8A),
    docComment = Color(0xFFA8B0AC),
    todoTag = Color(0xFFFFAA66),
    annotation = Color(0xFFC8A2C8),
    type = Color(0xFFE8C691),
    identifier = Color(0xFFB8D4A1),
)

private val FrostSyntax = SyntaxPalette(
    keyword = Color(0xFFCF222E),
    string = Color(0xFF0A3069),
    number = Color(0xFF0550AE),
    comment = Color(0xFF6E7781),
    docComment = Color(0xFF5F7C8C),
    todoTag = Color(0xFFCC4E20),
    annotation = Color(0xFF8250DF),
    type = Color(0xFF953800),
    identifier = Color(0xFF1F2328),
)

private val CoolColors = GlassColors(
    background = Color(0xFF0E1418),
    surface = Color(0xFF161D24),
    surfaceRaised = Color(0xFF1C2630),
    outline = Color(0xFF2C3540),
    primary = Color(0xFF6AA9FF),
    onPrimary = Color(0xFF0A1126),
    accent = Color(0xFF79D4B8),
    text = Color(0xFFE6EDF3),
    muted = Color(0xFF8FA0B5),
    error = Color(0xFFFF7B72),
    warn = Color(0xFFE3B341),
    syntax = CoolSyntax,
    isLight = false,
)

private val WarmColors = GlassColors(
    background = Color(0xFF1A1612),
    surface = Color(0xFF221C16),
    surfaceRaised = Color(0xFF2A231C),
    outline = Color(0xFF3A312A),
    primary = Color(0xFFE8C691),
    onPrimary = Color(0xFF2A1F14),
    accent = Color(0xFFC99A6B),
    text = Color(0xFFEDE4D6),
    muted = Color(0xFFA89C8A),
    error = Color(0xFFE07A5F),
    warn = Color(0xFFD4A574),
    syntax = WarmSyntax,
    isLight = false,
)

private val FrostColors = GlassColors(
    background = Color(0xFFF2F4F8),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFFAFBFD),
    outline = Color(0xFFD7DCE3),
    primary = Color(0xFF2D5BFF),
    onPrimary = Color(0xFFFFFFFF),
    accent = Color(0xFF00A88E),
    text = Color(0xFF1A2330),
    muted = Color(0xFF5C6878),
    error = Color(0xFFD63B3B),
    warn = Color(0xFFC68A00),
    syntax = FrostSyntax,
    isLight = true,
)

private val ForestSyntax = SyntaxPalette(
    keyword = Color(0xFFE5A5A0),
    string = Color(0xFFB5D9B0),
    number = Color(0xFFC5DDA5),
    comment = Color(0xFF7A8E78),
    docComment = Color(0xFF6E9CA8),
    todoTag = Color(0xFFE08560),
    annotation = Color(0xFFD9C28E),
    type = Color(0xFFE8C691),
    identifier = Color(0xFFD8E1C6),
)

private val ForestColors = GlassColors(
    background = Color(0xFF101714),
    surface = Color(0xFF17211D),
    surfaceRaised = Color(0xFF1E2A24),
    outline = Color(0xFF2C3A33),
    primary = Color(0xFF7CC4A1),
    onPrimary = Color(0xFF0E1A14),
    accent = Color(0xFFC8D58A),
    text = Color(0xFFE2EBDF),
    muted = Color(0xFF8FA396),
    error = Color(0xFFE5786E),
    warn = Color(0xFFD9C28E),
    syntax = ForestSyntax,
    isLight = false,
)

private val MidnightSyntax = SyntaxPalette(
    keyword = Color(0xFFFF80B5),
    string = Color(0xFF7DDDD5),
    number = Color(0xFF8DD6FF),
    comment = Color(0xFF5C6577),
    docComment = Color(0xFF788FB5),
    todoTag = Color(0xFFFFAA66),
    annotation = Color(0xFFC8B6FF),
    type = Color(0xFFFFC078),
    identifier = Color(0xFFE6E8F0),
)

private val MidnightColors = GlassColors(
    background = Color(0xFF05070C),
    surface = Color(0xFF0B0F18),
    surfaceRaised = Color(0xFF121826),
    outline = Color(0xFF1F2A3F),
    primary = Color(0xFF42E2D6),
    onPrimary = Color(0xFF002624),
    accent = Color(0xFFB388FF),
    text = Color(0xFFE6E8F0),
    muted = Color(0xFF7B86A0),
    error = Color(0xFFFF6E83),
    warn = Color(0xFFFFC078),
    syntax = MidnightSyntax,
    isLight = false,
)

private val SandSyntax = SyntaxPalette(
    keyword = Color(0xFFB85C38),
    string = Color(0xFF6B7E3F),
    number = Color(0xFF8C6A1F),
    comment = Color(0xFF94886F),
    docComment = Color(0xFF7A6E8E),
    todoTag = Color(0xFFB85C38),
    annotation = Color(0xFF8E5DA8),
    type = Color(0xFF9C6B2D),
    identifier = Color(0xFF3D352A),
)

private val SandColors = GlassColors(
    background = Color(0xFFF5EEDF),
    surface = Color(0xFFFAF4E6),
    surfaceRaised = Color(0xFFFFF9EC),
    outline = Color(0xFFE0D3B8),
    primary = Color(0xFFB85C38),
    onPrimary = Color(0xFFFFF6E8),
    accent = Color(0xFF6B7E3F),
    text = Color(0xFF3D352A),
    muted = Color(0xFF7A6E58),
    error = Color(0xFFC23B2C),
    warn = Color(0xFFA8761A),
    syntax = SandSyntax,
    isLight = true,
)

fun glassTokensFor(palette: GlassPalette): GlassTokens = GlassTokens(
    palette = palette,
    color = when (palette) {
        GlassPalette.Cool -> CoolColors
        GlassPalette.Warm -> WarmColors
        GlassPalette.Frost -> FrostColors
        GlassPalette.Forest -> ForestColors
        GlassPalette.Midnight -> MidnightColors
        GlassPalette.Sand -> SandColors
    },
    type = DefaultType,
    space = DefaultSpace,
    motion = DefaultMotion,
)
