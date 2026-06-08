package page.app.mvi

import androidx.compose.ui.unit.Dp
import page.app.CreateEntryDialogState
import page.app.DeleteEntryDialogState
import page.app.EditorScrollSnapshot
import page.app.FileOpConfirmState
import page.app.PaneSide
import page.app.PendingClose
import page.app.ReferencesQueryState
import page.app.RenameEntryDialogState
import page.app.filetree.PasteEntryDialogState
import page.editor.SplitPaneState
import page.lsp.CodeActionEntry
import page.lsp.RenameWorkspaceEdit
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

    sealed interface EditorLayout : IdeEvent {
        data class FocusPane(val side: PaneSide) : EditorLayout
        data class SetSplitEnabled(val enabled: Boolean) : EditorLayout
        data class SplitStateChanged(val state: SplitPaneState) : EditorLayout
        data class FoldChanged(val key: String, val lines: Set<Int>) : EditorLayout
    }

    sealed interface EditorScroll : IdeEvent {
        data class Changed(val path: Path, val snapshot: EditorScrollSnapshot) : EditorScroll
        data class Cleared(val path: Path) : EditorScroll
    }

    sealed interface Dialog : IdeEvent {
        data class SetCreate(val state: CreateEntryDialogState?) : Dialog
        data class SetRename(val state: RenameEntryDialogState?) : Dialog
        data class SetDelete(val state: DeleteEntryDialogState?) : Dialog
        data class SetPaste(val state: PasteEntryDialogState?) : Dialog
        data class SetFileOpConfirm(val state: FileOpConfirmState?) : Dialog
        data class SetPendingClose(val state: PendingClose?) : Dialog
        data object OpenFindInFiles : Dialog
        data object CloseFindInFiles : Dialog
    }

    sealed interface CodeAction : IdeEvent {
        data class SelectedChange(val index: Int) : CodeAction
        data class Apply(val action: CodeActionEntry) : CodeAction
        data object Dismiss : CodeAction
    }

    sealed interface Lsp : IdeEvent {
        data class RequestReferences(
            val path: Path,
            val line: Int,
            val character: Int,
            val symbol: String,
        ) : Lsp
        data class JumpToProblem(val path: Path, val line: Int, val character: Int) : Lsp
        data class ApplyRename(val edit: RenameWorkspaceEdit) : Lsp
        data object ReferencesClose : Lsp
    }

    sealed interface Internal : IdeEvent {
        data class CodeActionsResult(
            val actions: List<CodeActionEntry>,
            val uri: String?,
            val text: String?,
            val selected: Int,
            val open: Boolean,
        ) : Internal

        data class ReferencesResult(val state: ReferencesQueryState?) : Internal
    }
}
