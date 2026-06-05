package page.app.filetree

import page.app.CreateEntryDialogState
import page.app.CreateEntryKind
import page.app.RenameEntryDialogState
import page.app.state.LayoutUiState
import page.workspace.FileTreeActions
import page.workspace.FileTreeClipboard
import java.nio.file.Files
import java.nio.file.Path

internal class FileTreeContextController(
    private val ui: LayoutUiState,
    private val rootDir: () -> Path?,
    private val copyToClipboard: (String) -> Unit,
) {
    fun onCreateFileIn(parent: Path) {
        ui.createDialog = CreateEntryDialogState(parent, CreateEntryKind.FILE)
    }

    fun onCreateFolderIn(parent: Path) {
        ui.createDialog = CreateEntryDialogState(parent, CreateEntryKind.FOLDER)
    }

    fun onRevealInFiles(path: Path) {
        val target = if (Files.isDirectory(path)) path else path.parent
        if (target != null) {
            runCatching { java.awt.Desktop.getDesktop().open(target.toFile()) }
        }
    }

    fun onCopyPath(path: Path) {
        copyToClipboard(path.toAbsolutePath().toString())
    }

    fun onCopyRelativePath(path: Path) {
        copyToClipboard(FileTreeActions.relativeTo(rootDir(), path))
    }

    fun onRenameEntry(path: Path) {
        ui.renameDialog = RenameEntryDialogState(path)
    }

    fun onPasteInto(destParent: Path) {
        val content = FileTreeClipboard.read()
        if (content != null && content.paths.isNotEmpty()) {
            val target = if (Files.isDirectory(destParent)) destParent else destParent.parent
            if (target != null) {
                ui.pasteDialog = PasteEntryDialogState(
                    remaining = content.paths,
                    destParent = target,
                    mode = content.mode,
                )
            }
        }
    }
}
