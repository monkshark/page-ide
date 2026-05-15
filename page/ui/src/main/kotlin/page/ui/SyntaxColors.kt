package page.ui

import androidx.compose.ui.graphics.Color

data class SyntaxPalette(
    val keyword: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    val docComment: Color,
    val todoTag: Color,
    val annotation: Color,
    val type: Color,
    val identifier: Color,
)
