package page.app.mvi

import androidx.compose.ui.unit.Dp
import java.nio.file.Path

internal sealed interface IdeEvent {

    sealed interface Chrome : IdeEvent {
        data object OpenSettings : Chrome
        data object CloseSettings : Chrome
        data object ToggleSettings : Chrome
        data object OpenRunDialog : Chrome
        data object CloseRunDialog : Chrome
        data class ShowPaletteToast(val untilMs: Long) : Chrome
        data object BumpEditorFocus : Chrome
        data object BumpTreeFocus : Chrome
    }

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

    sealed interface Tree : IdeEvent {
        data class SelectionChanged(val paths: Set<Path>) : Tree
        data class ExpandedChanged(val paths: Set<Path>) : Tree
        data class FocusChanged(val focused: Boolean) : Tree
        data object BumpRevision : Tree
    }
}
