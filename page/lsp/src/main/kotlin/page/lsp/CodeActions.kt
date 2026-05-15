package page.lsp

import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.Command

data class CodeActionEntry(
    val title: String,
    val kind: String?,
    val isPreferred: Boolean,
    val edit: RenameWorkspaceEdit,
    val command: String?,
) {
    val hasEdit: Boolean get() = !edit.isEmpty
    val isExecutable: Boolean get() = hasEdit

    companion object {
        fun fromLspCodeAction(a: CodeAction?): CodeActionEntry? {
            a ?: return null
            val title = a.title?.takeIf { it.isNotBlank() } ?: return null
            return CodeActionEntry(
                title = title,
                kind = a.kind,
                isPreferred = a.isPreferred == true,
                edit = RenameWorkspaceEdit.fromLsp(a.edit),
                command = a.command?.command,
            )
        }

        fun fromLspCommand(c: Command?): CodeActionEntry? {
            c ?: return null
            val title = c.title?.takeIf { it.isNotBlank() } ?: return null
            return CodeActionEntry(
                title = title,
                kind = null,
                isPreferred = false,
                edit = RenameWorkspaceEdit.EMPTY,
                command = c.command,
            )
        }
    }
}
