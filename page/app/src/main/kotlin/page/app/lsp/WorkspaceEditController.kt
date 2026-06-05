package page.app.lsp

import page.language.LspController
import page.lsp.CodeActionEntry
import page.lsp.RenameWorkspaceEdit
import page.workspace.FileOpHistory
import page.workspace.FolderPackageRename
import page.workspace.sync.PackageSyncEngine
import java.nio.file.Path

internal class WorkspaceEditController(
    private val applyRename: (RenameWorkspaceEdit) -> Unit,
    private val codeActionUri: () -> String?,
    private val controllerForUri: (String) -> LspController?,
    private val rootDir: () -> Path?,
    private val readFileText: (Path) -> String?,
    private val applyTextReplace: (Path, String) -> Unit,
) {
    fun applyCodeAction(action: CodeActionEntry) {
        if (action.hasEdit) {
            println("[lsp] codeAction apply ▶ \"${action.title}\" — ${action.edit.changes.sumOf { it.edits.size }} edit(s)")
            applyRename(action.edit)
        }
        if (action.hasCommand) {
            val ctrl = codeActionUri()?.let { controllerForUri(it) }
            val command = action.command
            if (ctrl != null && command != null) {
                println("[lsp] codeAction command ▶ \"${action.title}\" → $command")
                ctrl.executeCommand(command, action.commandArguments)
            }
        }
    }

    fun applyFolderPackageSync(
        newFolder: Path,
        packageMap: Map<String, String>,
    ): List<FileOpHistory.RewriteEntry> {
        val entries = PackageSyncEngine.folderRewrites(newFolder, packageMap, rootDir(), readFileText)
        entries.forEach { applyTextReplace(it.path, it.rewritten) }
        return entries
    }

    fun applySingleFileMoveSync(
        newPath: Path,
        plan: FolderPackageRename.SingleFileMovePlan,
    ): List<FileOpHistory.RewriteEntry> {
        val entries = PackageSyncEngine.singleFileMoveRewrites(newPath, plan, rootDir(), readFileText)
        entries.forEach { applyTextReplace(it.path, it.rewritten) }
        return entries
    }
}
