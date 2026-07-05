package page.docs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import page.shared.syntax.SyntaxPalette

private class DocsColors(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val outline: Color,
    val separator: Color,
    val primary: Color,
    val primarySoft: Color,
    val accent: Color,
    val text: Color,
    val muted: Color,
    val faint: Color,
    val warn: Color,
    val warnSoft: Color,
    val danger: Color,
    val success: Color,
    val syntax: SyntaxPalette,
)

private val DarkDocs = DocsColors(
    background = Color(0xFF0A0D14),
    surface = Color(0xFF131823),
    surfaceRaised = Color(0xFF1F2735),
    outline = Color(0xFF232B3A),
    separator = Color(0x0DFFFFFF),
    primary = Color(0xFF7D8EDB),
    primarySoft = Color(0x247D8EDB),
    accent = Color(0xFF67B9BA),
    text = Color(0xFFE7EAF3),
    muted = Color(0xFF8A92A6),
    faint = Color(0xFF4A5366),
    warn = Color(0xFFE7B45C),
    warnSoft = Color(0x14E7B45C),
    danger = Color(0xFFF2727F),
    success = Color(0xFF5BD6A0),
    syntax = SyntaxPalette(
        keyword = Color(0xFF9DA8FF),
        string = Color(0xFF4FD3C7),
        number = Color(0xFFC9B6FF),
        comment = Color(0xFF828DA8),
        docComment = Color(0xFF6E8FA8),
        todoTag = Color(0xFFF08FC8),
        annotation = Color(0xFFB79CFF),
        type = Color(0xFF7FD6E0),
        identifier = Color(0xFFC8D0E6),
    ),
)

private val LightDocs = DocsColors(
    background = Color(0xFFF7F8FB),
    surface = Color(0xFFEEF0F6),
    surfaceRaised = Color(0xFFE4E7F0),
    outline = Color(0xFFD7DCE7),
    separator = Color(0x0D000000),
    primary = Color(0xFF4A5AB8),
    primarySoft = Color(0x1F4A5AB8),
    accent = Color(0xFF2C8F90),
    text = Color(0xFF1A1F2B),
    muted = Color(0xFF5B6478),
    faint = Color(0xFF9AA2B4),
    warn = Color(0xFFB8791F),
    warnSoft = Color(0x14B8791F),
    danger = Color(0xFFC7414E),
    success = Color(0xFF1F9E6E),
    syntax = SyntaxPalette(
        keyword = Color(0xFF5A45C7),
        string = Color(0xFF0A7E77),
        number = Color(0xFF6B4BC4),
        comment = Color(0xFF8A93A6),
        docComment = Color(0xFF5E7C8C),
        todoTag = Color(0xFFC2508F),
        annotation = Color(0xFF7A52C7),
        type = Color(0xFF1C7C86),
        identifier = Color(0xFF2A3242),
    ),
)

object DocsTheme {
    var dark by mutableStateOf(true)

    private val c: DocsColors get() = if (dark) DarkDocs else LightDocs

    val background: Color get() = c.background
    val surface: Color get() = c.surface
    val surfaceRaised: Color get() = c.surfaceRaised
    val outline: Color get() = c.outline
    val separator: Color get() = c.separator
    val primary: Color get() = c.primary
    val primarySoft: Color get() = c.primarySoft
    val accent: Color get() = c.accent
    val text: Color get() = c.text
    val muted: Color get() = c.muted
    val faint: Color get() = c.faint
    val warn: Color get() = c.warn
    val warnSoft: Color get() = c.warnSoft
    val danger: Color get() = c.danger
    val success: Color get() = c.success
    val syntax: SyntaxPalette get() = c.syntax
}
