package page.ui

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font

private const val PRIMARY_FONT_RESOURCE = "fonts/D2Coding.ttf"

val EditorFontFamily: FontFamily = run {
    val cl = Thread.currentThread().contextClassLoader
        ?: object {}.javaClass.classLoader
    if (cl?.getResource(PRIMARY_FONT_RESOURCE) != null) {
        FontFamily(Font(resource = PRIMARY_FONT_RESOURCE))
    } else {
        FontFamily.Monospace
    }
}
