package page.app.mvi

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal data class LayoutState(
    val sidebarWidth: Dp = 260.dp,
    val problemsOpen: Boolean = false,
    val problemsHeight: Dp = 220.dp,
    val problemsCollapsed: Set<String> = emptySet(),
    val problemsFileOrder: List<String> = emptyList(),
    val todoOpen: Boolean = false,
    val todoHeight: Dp = 220.dp,
    val todoCollapsed: Set<String> = emptySet(),
    val todoFileOrder: List<String> = emptyList(),
    val terminalOpen: Boolean = false,
    val terminalHeight: Dp = 240.dp,
    val outputOpen: Boolean = false,
    val outputHeight: Dp = defaultOutputHeight(),
    val referencesHeight: Dp = 220.dp,
)

internal data class AppState(
    val layout: LayoutState = LayoutState(),
)

private fun defaultOutputHeight(): Dp {
    if (java.awt.GraphicsEnvironment.isHeadless()) return 480.dp
    return (java.awt.Toolkit.getDefaultToolkit().screenSize.height / 2f)
        .coerceIn(240f, 1200f)
        .dp
}
