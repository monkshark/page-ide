package page.app.ui.dialog

import androidx.compose.runtime.Composable
import page.app.ConfirmDialog
import page.app.LargeCopyDialog
import page.app.RunConfigDialog
import page.app.PaneSide
import page.app.filetree.FileTreeActionExecutor
import page.app.mvi.IdeEvent
import page.app.state.EditorWorkspaceState
import page.app.state.IdeAppState
import page.app.state.LayoutUiState
import page.app.state.WorkspaceState
import page.editor.OpenTab
import page.language.LspRouter
import page.lsp.RenameWorkspaceEdit
import page.lsp.WorkspaceSymbolLocated
import page.workspace.FileOpHistory
import java.nio.file.Path

@Composable
internal fun AppDialogs(
    appState: IdeAppState,
    layoutUiState: LayoutUiState,
    workspaceState: WorkspaceState,
    editorWorkspace: EditorWorkspaceState,
    fileOpHistory: FileOpHistory.Stack,
    lspRouter: LspRouter,
    fileTreeActionExecutor: FileTreeActionExecutor,
    onEvent: (IdeEvent) -> Unit,
    openInTab: (Path) -> Unit,
    jumpToProblem: (Path, Int, Int) -> Unit,
    workspaceSymbolQuery: suspend (String) -> List<WorkspaceSymbolLocated>,
    requestFrameFocus: () -> Unit,
    applyRename: (RenameWorkspaceEdit) -> Unit,
    remapTabsAfterRename: (Path, Path) -> Unit,
    remapTreeStateAfterRename: (Path, Path) -> Unit,
    applyFolderPackageSync: (Path, Path, Map<String, String>) -> List<FileOpHistory.RewriteEntry>,
    withFileTreeWatcherClosed: (() -> Unit) -> Unit,
    readFileText: (Path) -> String?,
    closeTabsUnderPath: (Path) -> Unit,
    onFileOpHistoryChanged: () -> Unit,
    onUndoFileOp: () -> Boolean,
    onRedoFileOp: () -> Boolean,
    isUnsavedText: (OpenTab) -> Boolean,
    closeTabAt: (PaneSide, Int) -> Unit,
    didClose: (Path) -> Unit,
    onExitApplication: () -> Unit,
) {
    NavigationPickerDialogs(
        ui = layoutUiState,
        openInTab = openInTab,
        jumpToProblem = jumpToProblem,
        workspaceSymbolQuery = workspaceSymbolQuery,
        requestFrameFocus = requestFrameFocus,
        onEditorFocusBump = { onEvent(IdeEvent.Chrome.BumpEditorFocus) },
    )

    if (appState.runDialogOpen) {
        RunConfigDialog(
            state = appState.runState,
            workspaceRoot = workspaceState.rootDir,
            onSave = { saved ->
                onEvent(IdeEvent.Run.SaveConfigs(saved))
                onEvent(IdeEvent.Chrome.CloseRunDialog)
            },
            onDismiss = { onEvent(IdeEvent.Chrome.CloseRunDialog) },
        )
    }

    FileTreeCreateDialog(
        workspace = workspaceState,
        ui = layoutUiState,
        fileOpHistory = fileOpHistory,
        openInTab = openInTab,
        onFileOpHistoryChanged = onFileOpHistoryChanged,
    )

    FileTreeRenameDeleteDialogs(
        workspace = workspaceState,
        ui = layoutUiState,
        lspRouter = lspRouter,
        fileOpHistory = fileOpHistory,
        jumpToProblem = jumpToProblem,
        applyRename = applyRename,
        remapTabsAfterRename = remapTabsAfterRename,
        remapTreeStateAfterRename = remapTreeStateAfterRename,
        applyFolderPackageSync = applyFolderPackageSync,
        withFileTreeWatcherClosed = withFileTreeWatcherClosed,
        readFileText = readFileText,
        closeTabsUnderPath = closeTabsUnderPath,
        onFileOpHistoryChanged = onFileOpHistoryChanged,
    )

    FileTreePasteDialog(
        workspace = workspaceState,
        ui = layoutUiState,
        fileTreeActionExecutor = fileTreeActionExecutor,
    )

    val activeLargeCopy = layoutUiState.largeCopyState
    if (activeLargeCopy != null) {
        LargeCopyDialog(
            sourceName = activeLargeCopy.sourceName,
            destName = activeLargeCopy.destName,
            totalBytes = activeLargeCopy.totalBytes,
            fileCount = activeLargeCopy.fileCount,
            bytesCopied = activeLargeCopy.bytesCopied,
            filesCopied = activeLargeCopy.filesCopied,
            onCancel = { activeLargeCopy.cancelToken.set(true) },
        )
    }

    val activeFileOpConfirm = appState.fileOpConfirm
    if (activeFileOpConfirm != null) {
        val verb = if (activeFileOpConfirm.isRedo) "Redo" else "Undo"
        ConfirmDialog(
            title = "$verb file operation",
            message = "$verb '${activeFileOpConfirm.op.describe()}'?",
            confirmLabel = verb,
            danger = false,
            onConfirm = {
                onEvent(IdeEvent.Dialog.SetFileOpConfirm(null))
                if (activeFileOpConfirm.isRedo) onRedoFileOp() else onUndoFileOp()
            },
            onDismiss = { onEvent(IdeEvent.Dialog.SetFileOpConfirm(null)) },
        )
    }

    PendingCloseDialog(
        editorWorkspace = editorWorkspace,
        pendingClose = appState.pendingClose,
        setPendingClose = { onEvent(IdeEvent.Dialog.SetPendingClose(it)) },
        isUnsavedText = isUnsavedText,
        closeTabAt = closeTabAt,
        didClose = didClose,
        onExitApplication = onExitApplication,
    )
}
