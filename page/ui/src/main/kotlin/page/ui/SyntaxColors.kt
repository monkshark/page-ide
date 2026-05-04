package page.ui

import androidx.compose.ui.graphics.Color

data class SyntaxPalette(
    val keyword: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    val annotation: Color,
    val type: Color,
)

val GlassDarkSyntax = SyntaxPalette(
    keyword = Color(0xFFFF7B72),
    string = Color(0xFFA5D6FF),
    number = Color(0xFF79C0FF),
    comment = Color(0xFF8B949E),
    annotation = Color(0xFFD2A8FF),
    type = Color(0xFFFFA657),
)
