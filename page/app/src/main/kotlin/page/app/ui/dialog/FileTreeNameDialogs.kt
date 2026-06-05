package page.app.ui.dialog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import page.app.CreateEntryKind
import page.app.NameInputDialog
import page.app.filetree.FileTreeActionExecutor
import page.app.state.LayoutUiState
import page.app.state.WorkspaceState
import page.workspace.FileOpHistory
import page.workspace.FileTreeActions
import page.workspace.FileTreeClipboard
import java.nio.file.Path

@Composable
internal fun FileTreeCreateDialog(
    workspace: WorkspaceState,
    ui: LayoutUiState,
    fileOpHistory: FileOpHistory.Stack,
    openInTab: (Path) -> Unit,
    onFileOpHistoryChanged: () -> Unit,
) {
    var rootDir by workspace::rootDir
    var expanded by workspace::expanded
    var treeRevision by workspace::treeRevision
    var createDialog by ui::createDialog

    val activeCreateDialog = createDialog ?: return
    val isFile = activeCreateDialog.kind == CreateEntryKind.FILE
    val rel = FileTreeActions.relativeTo(rootDir, activeCreateDialog.parent)
    val parentLabel = if (rel.isEmpty() || rel == ".") "/" else rel
    NameInputDialog(
        title = if (isFile) "New file" else "New folder",
        label = "$parentLabel  /  name",
        error = activeCreateDialog.error,
        onSubmit = { name ->
            val result = if (isFile) {
                FileTreeActions.createFile(activeCreateDialog.parent, name)
            } else {
                FileTreeActions.createFolder(activeCreateDialog.parent, name)
            }
            when (result) {
                is FileTreeActions.CreateResult.Ok -> {
                    treeRevision++
                    expanded = expanded + activeCreateDialog.parent
                    if (isFile) openInTab(result.path)
                    fileOpHistory.push(FileOpHistory.CreateOp(result.path, isDirectory = !isFile))
                    onFileOpHistoryChanged()
                    createDialog = null
                }
                is FileTreeActions.CreateResult.Err -> {
                    createDialog = activeCreateDialog.copy(error = result.message)
                }
            }
        },
        onDismiss = { createDialog = null },
    )
}

@Composable
internal fun FileTreePasteDialog(
    workspace: WorkspaceState,
    ui: LayoutUiState,
    fileTreeActionExecutor: FileTreeActionExecutor,
) {
    var rootDir by workspace::rootDir
    var pasteDialog by ui::pasteDialog

    val activePasteDialog = pasteDialog
    if (activePasteDialog == null || activePasteDialog.remaining.isEmpty()) return
    val source = activePasteDialog.remaining.first()
    val sourceName = source.fileName?.toString() ?: source.toString()
    val destRel = FileTreeActions.relativeTo(rootDir, activePasteDialog.destParent)
    val destLabel = if (destRel.isEmpty() || destRel == ".") "/" else destRel
    val verb = if (activePasteDialog.mode == FileTreeClipboard.Mode.Cut) "Move" else "Copy"
    val total = activePasteDialog.remaining.size
    val countSuffix = if (total > 1) "  ($total remaining)" else ""
    val skipOne: (() -> Unit)? = if (total > 1) {
        {
            val cur = activePasteDialog
            val rest = cur.remaining.drop(1)
            pasteDialog = if (rest.isEmpty()) null else cur.copy(remaining = rest, error = null)
            if (rest.isEmpty()) fileTreeActionExecutor.finalizePasteHistory(cur)
        }
    } else null
    val skipAll: (() -> Unit)? = if (total > 1) {
        {
            val cur = activePasteDialog
            pasteDialog = null
            fileTreeActionExecutor.finalizePasteHistory(cur)
        }
    } else null
    val performPaste: (String, Boolean) -> Unit = { newName, overwriteOnce ->
        fileTreeActionExecutor.performPaste(newName, overwriteOnce)
    }
    NameInputDialog(
        title = "$verb into $destLabel$countSuffix",
        label = "$sourceName  →  $destLabel  /  name",
        initial = sourceName,
        error = activePasteDialog.error,
        onSkip = skipOne,
        onSkipRemaining = skipAll,
        onOverwrite = { name -> performPaste(name, true) },
        onOverwriteAll = { name ->
            pasteDialog = activePasteDialog.copy(overwriteForAll = true)
            performPaste(name, true)
        },
        onSubmit = { newName -> performPaste(newName, false) },
        onDismiss = { pasteDialog = null },
    )
}
