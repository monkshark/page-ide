package page.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.sp

private val UI_FONT_RESOURCES = listOf(
    "fonts/Pretendard-Regular.otf" to FontWeight.Normal,
    "fonts/Pretendard-Medium.otf" to FontWeight.Medium,
    "fonts/Pretendard-SemiBold.otf" to FontWeight.SemiBold,
)

val UiFontFamily: FontFamily = run {
    val cl = Thread.currentThread().contextClassLoader ?: object {}.javaClass.classLoader
    val fonts = UI_FONT_RESOURCES.filter { (path, _) -> cl?.getResource(path) != null }
        .map { (path, weight) -> Font(resource = path, weight = weight) }
    if (fonts.isEmpty()) FontFamily.Default else FontFamily(fonts)
}

private fun TextStyle.glass(): TextStyle = copy(fontFamily = UiFontFamily, letterSpacing = 0.sp)

internal val GlassTypography: Typography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.glass(),
        displayMedium = base.displayMedium.glass(),
        displaySmall = base.displaySmall.glass(),
        headlineLarge = base.headlineLarge.glass(),
        headlineMedium = base.headlineMedium.glass(),
        headlineSmall = base.headlineSmall.glass(),
        titleLarge = base.titleLarge.glass(),
        titleMedium = base.titleMedium.glass(),
        titleSmall = base.titleSmall.glass(),
        bodyLarge = base.bodyLarge.glass(),
        bodyMedium = base.bodyMedium.glass(),
        bodySmall = base.bodySmall.glass(),
        labelLarge = base.labelLarge.glass(),
        labelMedium = base.labelMedium.glass(),
        labelSmall = base.labelSmall.glass(),
    )
}
