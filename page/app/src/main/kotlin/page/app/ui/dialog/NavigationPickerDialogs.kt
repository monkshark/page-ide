package page.app.ui.dialog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import page.app.DocumentSymbolDialog
import page.app.WorkspaceSymbolDialog
import page.app.state.LayoutUiState
import page.lsp.WorkspaceSymbolLocated
import page.workspace.QuickOpenDialog
import java.nio.file.Path

@Composable
internal fun NavigationPickerDialogs(
    ui: LayoutUiState,
    openInTab: (Path) -> Unit,
    jumpToProblem: (Path, Int, Int) -> Unit,
    workspaceSymbolQuery: suspend (String) -> List<WorkspaceSymbolLocated>,
    requestFrameFocus: () -> Unit,
    onEditorFocusBump: () -> Unit,
) {
    var quickOpen by ui::quickOpen
    val quickOpenIndex by ui::quickOpenIndex
    var documentSymbolOpen by ui::documentSymbolOpen
    val documentSymbolList by ui::documentSymbolList
    val documentSymbolUri by ui::documentSymbolUri
    var workspaceSymbolOpen by ui::workspaceSymbolOpen

    if (quickOpen) {
        QuickOpenDialog(
            files = quickOpenIndex,
            onPick = { f ->
                quickOpen = false
                openInTab(f.path)
            },
            onDismiss = { quickOpen = false },
        )
    }

    if (documentSymbolOpen) {
        DocumentSymbolDialog(
            uri = documentSymbolUri,
            symbols = documentSymbolList,
            onPick = { pick ->
                documentSymbolOpen = false
                val pickedPath = runCatching { Path.of(java.net.URI(pick.uri)) }.getOrNull()
                if (pickedPath != null) {
                    jumpToProblem(pickedPath, pick.startLine, pick.startCharacter)
                }
                requestFrameFocus()
                onEditorFocusBump()
            },
            onDismiss = {
                documentSymbolOpen = false
                requestFrameFocus()
            },
        )
    }

    if (workspaceSymbolOpen) {
        WorkspaceSymbolDialog(
            queryFor = workspaceSymbolQuery,
            onPick = { pick ->
                workspaceSymbolOpen = false
                val pickedPath = runCatching { Path.of(java.net.URI(pick.uri)) }.getOrNull()
                if (pickedPath != null) {
                    jumpToProblem(pickedPath, pick.startLine, pick.startCharacter)
                }
                requestFrameFocus()
                onEditorFocusBump()
            },
            onDismiss = {
                workspaceSymbolOpen = false
                requestFrameFocus()
            },
        )
    }
}
