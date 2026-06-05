package page.app.filetree

import page.app.DeleteEntryDialogState
import page.app.DropResultToastState
import page.app.DropResultToastTone
import page.app.state.LayoutUiState
import page.workspace.FileTreeActions
import page.workspace.FileTreeClipboard
import page.workspace.TreeDragController
import java.nio.file.Files
import java.nio.file.Path

internal class FileTreeDropController(
    private val ui: LayoutUiState,
    private val setDropResultToast: (DropResultToastState?) -> Unit,
) {
    fun showDropResultToast(msg: String, tone: DropResultToastTone, undo: (() -> Unit)?) {
        setDropResultToast(
            DropResultToastState(
                message = msg,
                visibleUntilMs = System.currentTimeMillis() + 5000L,
                tone = tone,
                undo = undo,
            ),
        )
    }

    fun onDropPlanReceived(plan: TreeDragController.DropPlan) {
        val mode = when (plan.mode) {
            TreeDragController.Mode.Move -> FileTreeClipboard.Mode.Cut
            TreeDragController.Mode.Copy -> FileTreeClipboard.Mode.Copy
        }
        ui.pasteDialog = PasteEntryDialogState(
            remaining = plan.sources,
            destParent = plan.target,
            mode = mode,
        )
    }

    fun onExternalDropReceived(sources: List<Path>, target: Path) {
        if (sources.isNotEmpty() && Files.isDirectory(target)) {
            ui.pasteDialog = PasteEntryDialogState(
                remaining = sources,
                destParent = target,
                mode = FileTreeClipboard.Mode.Copy,
            )
        }
    }

    fun onDeleteEntry(path: Path) {
        ui.deleteDialog = DeleteEntryDialogState(listOf(path))
    }

    fun onDeleteEntries(paths: Set<Path>) {
        val pruned = FileTreeActions.pruneRedundantDescendants(paths)
        if (pruned.isNotEmpty()) {
            ui.deleteDialog = DeleteEntryDialogState(pruned)
        }
    }
}
