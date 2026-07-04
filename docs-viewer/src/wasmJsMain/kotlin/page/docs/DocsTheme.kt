package page.docs

import androidx.compose.ui.graphics.Color
import page.shared.syntax.SyntaxPalette

val DocsSyntaxPalette = SyntaxPalette(
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

object DocsTheme {
    val background = Color(0xFF0A0D14)
    val surface = Color(0xFF131823)
    val surfaceRaised = Color(0xFF1F2735)
    val outline = Color(0xFF232B3A)
    val separator = Color(0x0DFFFFFF)
    val primary = Color(0xFF7D8EDB)
    val primarySoft = Color(0x247D8EDB)
    val accent = Color(0xFF67B9BA)
    val text = Color(0xFFE7EAF3)
    val muted = Color(0xFF8A92A6)
    val faint = Color(0xFF4A5366)
    val warn = Color(0xFFE7B45C)
    val warnSoft = Color(0x14E7B45C)
    val danger = Color(0xFFF2727F)
    val success = Color(0xFF5BD6A0)
}
