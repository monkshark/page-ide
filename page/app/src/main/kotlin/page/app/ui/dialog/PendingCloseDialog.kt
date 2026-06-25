package page.app.ui.dialog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import page.app.EditorPaneState
import page.workspace.EditorScrollMemory
import page.app.PaneSide
import page.app.PendingClose
import page.app.UnsavedChangesDialog
import page.app.state.EditorWorkspaceState
import page.editor.FileDocument
import page.editor.OpenTab
import java.nio.file.Path

@Composable
internal fun PendingCloseDialog(
    editorWorkspace: EditorWorkspaceState,
    pendingClose: PendingClose?,
    setPendingClose: (PendingClose?) -> Unit,
    isUnsavedText: (OpenTab) -> Boolean,
    closeTabAt: (PaneSide, Int) -> Unit,
    didClose: (Path) -> Unit,
    onExitApplication: () -> Unit,
) {
    var editorScrollByPath by editorWorkspace::editorScrollByPath
    val primaryPane by editorWorkspace::primaryPane
    val secondaryPane by editorWorkspace::secondaryPane
    fun paneOf(side: PaneSide): EditorPaneState = editorWorkspace.paneOf(side)
    fun mutatePane(side: PaneSide, transform: (EditorPaneState) -> EditorPaneState) =
        editorWorkspace.mutatePane(side, transform)

    val current = pendingClose ?: return
    val targets: List<Triple<PaneSide, Int, OpenTab>> = when (current) {
        is PendingClose.Tab -> {
            val tab = paneOf(current.side).book.tabs.getOrNull(current.index)
            if (tab != null) listOf(Triple(current.side, current.index, tab)) else emptyList()
        }
        PendingClose.App -> buildList {
            primaryPane.book.tabs.forEachIndexed { idx, tab ->
                if (isUnsavedText(tab)) add(Triple(PaneSide.PRIMARY, idx, tab))
            }
            secondaryPane.book.tabs.forEachIndexed { idx, tab ->
                if (isUnsavedText(tab)) add(Triple(PaneSide.SECONDARY, idx, tab))
            }
        }
        is PendingClose.Batch -> buildList {
            current.targets.forEach { (side, path) ->
                val idx = paneOf(side).book.tabs.indexOfFirst { it.path == path }
                val tab = paneOf(side).book.tabs.getOrNull(idx)
                if (tab != null && isUnsavedText(tab)) add(Triple(side, idx, tab))
            }
        }
    }
    val targetNames = targets.map { (_, _, t) ->
        t.path.fileName?.toString() ?: t.path.toString()
    }
    val finishBatch: (PendingClose.Batch) -> Unit = { batch ->
        val grouped = batch.targets.groupBy({ it.first }) { it.second }
        grouped.forEach { (side, paths) ->
            val pane = paneOf(side)
            val indices = paths.mapNotNull { p ->
                pane.book.tabs.indexOfFirst { it.path == p }.takeIf { it >= 0 }
            }
            if (indices.isNotEmpty()) {
                val closedPaths = indices.map { pane.book.tabs[it].path }
                mutatePane(side) { it.copy(book = it.book.closeMany(indices)) }
                closedPaths.forEach { p ->
                    val stillOpen = primaryPane.book.tabs.any { it.path == p } ||
                        secondaryPane.book.tabs.any { it.path == p }
                    if (!stillOpen) {
                        editorScrollByPath = EditorScrollMemory.clear(editorScrollByPath, p)
                        didClose(p)
                    }
                }
            }
        }
    }
    UnsavedChangesDialog(
        fileNames = targetNames,
        isAppExit = current is PendingClose.App,
        onSave = {
            targets.forEach { (_, _, t) -> FileDocument.save(t.path, t.text) }
            setPendingClose(null)
            when (current) {
                is PendingClose.Tab -> closeTabAt(current.side, current.index)
                PendingClose.App -> onExitApplication()
                is PendingClose.Batch -> finishBatch(current)
            }
        },
        onDiscard = {
            setPendingClose(null)
            when (current) {
                is PendingClose.Tab -> closeTabAt(current.side, current.index)
                PendingClose.App -> onExitApplication()
                is PendingClose.Batch -> finishBatch(current)
            }
        },
        onCancel = { setPendingClose(null) },
    )
}
