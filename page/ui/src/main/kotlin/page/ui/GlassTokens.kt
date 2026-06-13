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

enum class GlassPalette { Signature, SignatureLight, Graphite, Cool, Warm, Frost, Forest, Midnight, Sand }

@Immutable
data class GlassColors(
    val background: Color,
    val surfaceL1: Color,
    val surfaceL2: Color,
    val surfaceL3: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceOverlay: Color,
    val highlightEdge: Color,
    val separator: Color,
    val outline: Color,
    val primary: Color,
    val primarySoft: Color,
    val onPrimary: Color,
    val accent: Color,
    val text: Color,
    val muted: Color,
    val faint: Color,
    val error: Color,
    val warn: Color,
    val success: Color,
    val danger: Color,
    val syntax: SyntaxPalette,
    val isLight: Boolean,
)

private fun glassColors(
    background: Color,
    surface: Color,
    surfaceRaised: Color,
    outline: Color,
    primary: Color,
    onPrimary: Color,
    accent: Color,
    text: Color,
    muted: Color,
    error: Color,
    warn: Color,
    success: Color,
    syntax: SyntaxPalette,
    isLight: Boolean,
    surfaceL1: Color = surface,
    surfaceL2: Color = surfaceRaised,
    surfaceL3: Color = surfaceRaised,
    surfaceOverlay: Color = surface.copy(alpha = 0.88f),
    highlightEdge: Color = if (isLight) Color(0xE6FFFFFF) else Color(0x12FFFFFF),
    separator: Color = if (isLight) Color(0x14101418) else Color(0x0DFFFFFF),
    primarySoft: Color = primary.copy(alpha = 0.14f),
    faint: Color = muted.copy(alpha = 0.5f),
    danger: Color = error,
): GlassColors = GlassColors(
    background = background,
    surfaceL1 = surfaceL1,
    surfaceL2 = surfaceL2,
    surfaceL3 = surfaceL3,
    surface = surface,
    surfaceRaised = surfaceRaised,
    surfaceOverlay = surfaceOverlay,
    highlightEdge = highlightEdge,
    separator = separator,
    outline = outline,
    primary = primary,
    primarySoft = primarySoft,
    onPrimary = onPrimary,
    accent = accent,
    text = text,
    muted = muted,
    faint = faint,
    error = error,
    warn = warn,
    success = success,
    danger = danger,
    syntax = syntax,
    isLight = isLight,
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
data class GlassRadius(
    val xs: Dp,
    val sm: Dp,
    val md: Dp,
    val lg: Dp,
)

@Immutable
data class GlassShadow(
    val blur: Dp,
    val offsetY: Dp,
    val alpha: Float,
)

@Immutable
data class GlassElevation(
    val flat: GlassShadow,
    val raised: GlassShadow,
    val overlay: GlassShadow,
)

@Immutable
data class GlassTokens(
    val palette: GlassPalette,
    val color: GlassColors,
    val type: GlassType,
    val space: GlassSpace,
    val motion: GlassMotion,
    val radius: GlassRadius,
    val elevation: GlassElevation,
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

private val DefaultRadius = GlassRadius(
    xs = 6.dp,
    sm = 8.dp,
    md = 12.dp,
    lg = 16.dp,
)

private val DefaultElevation = GlassElevation(
    flat = GlassShadow(blur = 0.dp, offsetY = 0.dp, alpha = 0f),
    raised = GlassShadow(blur = 24.dp, offsetY = 8.dp, alpha = 0.35f),
    overlay = GlassShadow(blur = 32.dp, offsetY = 12.dp, alpha = 0.45f),
)

private val CoolSyntax = SyntaxPalette(
    keyword = Color(0xFFFF7B72),
    string = Color(0xFFA5D6FF),
    number = Color(0xFF79C0FF),
    comment = Color(0xFF8B949E),
    docComment = Color(0xFF7CA1A8),
    todoTag = Color(0xFFFF7AC0),
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
    todoTag = Color(0xFFE94F8A),
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
    todoTag = Color(0xFFC7155F),
    annotation = Color(0xFF8250DF),
    type = Color(0xFF953800),
    identifier = Color(0xFF1F2328),
)

private val GraphiteSyntax = SyntaxPalette(
    keyword = Color(0xFFCC7832),
    string = Color(0xFF6A8759),
    number = Color(0xFF6897BB),
    comment = Color(0xFF808080),
    docComment = Color(0xFF629755),
    todoTag = Color(0xFFA8C023),
    annotation = Color(0xFFBBB529),
    type = Color(0xFFFFC66D),
    identifier = Color(0xFFA9B7C6),
)

private val GraphiteColors = glassColors(
    background = Color(0xFF1E1F22),
    surface = Color(0xFF26282B),
    surfaceRaised = Color(0xFF2B2D30),
    outline = Color(0xFF393B40),
    primary = Color(0xFF6897BB),
    onPrimary = Color(0xFF1E1F22),
    accent = Color(0xFF6A8759),
    text = Color(0xFFBCBEC4),
    muted = Color(0xFF787C84),
    error = Color(0xFFDB5C5C),
    warn = Color(0xFFC8A24A),
    success = Color(0xFF5BA85B),
    syntax = GraphiteSyntax,
    isLight = false,
)

private val CoolColors = glassColors(
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
    success = Color(0xFF5BD6A0),
    syntax = CoolSyntax,
    isLight = false,
)

private val WarmColors = glassColors(
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
    success = Color(0xFF94C973),
    syntax = WarmSyntax,
    isLight = false,
)

private val FrostColors = glassColors(
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
    success = Color(0xFF1F9D6B),
    syntax = FrostSyntax,
    isLight = true,
)

private val ForestSyntax = SyntaxPalette(
    keyword = Color(0xFFE5A5A0),
    string = Color(0xFFB5D9B0),
    number = Color(0xFFC5DDA5),
    comment = Color(0xFF7A8E78),
    docComment = Color(0xFF6E9CA8),
    todoTag = Color(0xFFEE5AA8),
    annotation = Color(0xFFD9C28E),
    type = Color(0xFFE8C691),
    identifier = Color(0xFFD8E1C6),
)

private val ForestColors = glassColors(
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
    success = Color(0xFF8CC76E),
    syntax = ForestSyntax,
    isLight = false,
)

private val MidnightSyntax = SyntaxPalette(
    keyword = Color(0xFFFF80B5),
    string = Color(0xFF7DDDD5),
    number = Color(0xFF8DD6FF),
    comment = Color(0xFF5C6577),
    docComment = Color(0xFF788FB5),
    todoTag = Color(0xFFE39FF6),
    annotation = Color(0xFFC8B6FF),
    type = Color(0xFFFFC078),
    identifier = Color(0xFFE6E8F0),
)

private val MidnightColors = glassColors(
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
    success = Color(0xFF4FE3B0),
    syntax = MidnightSyntax,
    isLight = false,
)

private val SandSyntax = SyntaxPalette(
    keyword = Color(0xFFB85C38),
    string = Color(0xFF6B7E3F),
    number = Color(0xFF8C6A1F),
    comment = Color(0xFF94886F),
    docComment = Color(0xFF7A6E8E),
    todoTag = Color(0xFFA82570),
    annotation = Color(0xFF8E5DA8),
    type = Color(0xFF9C6B2D),
    identifier = Color(0xFF3D352A),
)

private val SandColors = glassColors(
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
    success = Color(0xFF1F9D6B),
    syntax = SandSyntax,
    isLight = true,
)

private val SignatureSyntax = SyntaxPalette(
    keyword = Color(0xFF9DA8FF),
    string = Color(0xFF4FD3C7),
    number = Color(0xFFC9B6FF),
    comment = Color(0xFF828DA8),
    docComment = Color(0xFF6E8FA8),
    todoTag = Color(0xFFF08FC8),
    annotation = Color(0xFFB79CFF),
    type = Color(0xFF7FD6E0),
    identifier = Color(0xFFC8D0E6),
)

private val SignatureColors = glassColors(
    background = Color(0xFF0A0D14),
    surface = Color(0xFF131823),
    surfaceRaised = Color(0xFF1F2735),
    outline = Color(0xFF232B3A),
    primary = Color(0xFF6E8BFF),
    onPrimary = Color(0xFF0A0D14),
    accent = Color(0xFF4FD3C7),
    text = Color(0xFFE7EAF3),
    muted = Color(0xFF8A92A6),
    error = Color(0xFFF2727F),
    warn = Color(0xFFE7B45C),
    success = Color(0xFF5BD6A0),
    syntax = SignatureSyntax,
    isLight = false,
    surfaceL1 = Color(0xFF0E121A),
    surfaceL2 = Color(0xFF131823),
    surfaceL3 = Color(0xFF181E2A),
    surfaceOverlay = Color(0xE0131823),
    highlightEdge = Color(0x12FFFFFF),
    separator = Color(0x0DFFFFFF),
    primarySoft = Color(0x246E8BFF),
    faint = Color(0xFF4A5366),
    danger = Color(0xFFF2727F),
)

private val SignatureLightColors = glassColors(
    background = Color(0xFFF4F6FB),
    surface = Color(0xFFF0F3F9),
    surfaceRaised = Color(0xFFFFFFFF),
    outline = Color(0xFFD4DBE8),
    primary = Color(0xFF4360E0),
    onPrimary = Color(0xFFFFFFFF),
    accent = Color(0xFF1FA99B),
    text = Color(0xFF1A2030),
    muted = Color(0xFF5A6478),
    error = Color(0xFFD6485A),
    warn = Color(0xFFB5811F),
    success = Color(0xFF1F9D6B),
    syntax = FrostSyntax,
    isLight = true,
    surfaceL1 = Color(0xFFFFFFFF),
    surfaceL2 = Color(0xFFF0F3F9),
    surfaceL3 = Color(0xFFE7ECF5),
    highlightEdge = Color(0xE6FFFFFF),
    separator = Color(0x1410141E),
    faint = Color(0xFFA8B0C0),
    danger = Color(0xFFD6485A),
)

fun glassTokensFor(palette: GlassPalette): GlassTokens = GlassTokens(
    palette = palette,
    color = when (palette) {
        GlassPalette.Signature -> SignatureColors
        GlassPalette.SignatureLight -> SignatureLightColors
        GlassPalette.Graphite -> GraphiteColors
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
    radius = DefaultRadius,
    elevation = DefaultElevation,
)
