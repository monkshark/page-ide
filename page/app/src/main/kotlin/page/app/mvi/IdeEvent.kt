package page.app.mvi

import androidx.compose.ui.unit.Dp

internal sealed interface IdeEvent {

    sealed interface Panel : IdeEvent {
        data object ToggleProblems : Panel
        data object CloseProblems : Panel
        data object ToggleTodo : Panel
        data object CloseTodo : Panel
        data object ToggleTerminal : Panel
        data object CloseTerminal : Panel
        data object ToggleOutput : Panel
        data object CloseOutput : Panel
        data class ResizeSidebar(val deltaDp: Dp) : Panel
        data class ResizeProblems(val deltaDp: Dp) : Panel
        data class ResizeTodo(val deltaDp: Dp) : Panel
        data class ResizeTerminal(val deltaDp: Dp) : Panel
        data class ResizeOutput(val deltaDp: Dp) : Panel
        data class ResizeReferences(val deltaDp: Dp) : Panel
        data class ProblemsCollapsedChanged(val keys: Set<String>) : Panel
        data class ProblemsFileOrderChanged(val order: List<String>) : Panel
        data class TodoCollapsedChanged(val keys: Set<String>) : Panel
        data class TodoFileOrderChanged(val order: List<String>) : Panel
    }
}
