package page.app.mvi

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import page.app.CreateEntryDialogState
import page.app.DeleteEntryDialogState
import page.app.EditorScrollSnapshot
import page.app.FileOpConfirmState
import page.app.PaneSide
import page.app.PendingClose
import page.app.RenameEntryDialogState
import page.app.filetree.PasteEntryDialogState
import page.editor.SplitOrientation
import page.editor.SplitPaneState
import page.lsp.CodeActionEntry
import java.nio.file.Path

internal data class LayoutState(
    val sidebarWidth: Dp = 260.dp,
    val problemsOpen: Boolean = false,
    val problemsHeight: Dp = 220.dp,
    val problemsCollapsed: Set<String> = emptySet(),
    val problemsFileOrder: List<String> = emptyList(),
    val todoOpen: Boolean = false,
    val todoHeight: Dp = 220.dp,
    val todoCollapsed: Set<String> = emptySet(),
    val todoFileOrder: List<String> = emptyList(),
    val terminalOpen: Boolean = false,
    val terminalHeight: Dp = 240.dp,
    val outputOpen: Boolean = false,
    val outputHeight: Dp = defaultOutputHeight(),
    val referencesHeight: Dp = 220.dp,
)

internal data class ChromeState(
    val settingsDialogOpen: Boolean = false,
    val runDialogOpen: Boolean = false,
    val paletteToastUntil: Long = 0L,
    val editorFocusVersion: Int = 0,
    val pendingTreeFocusTick: Int = 0,
)

internal data class TreeState(
    val expanded: Set<Path> = emptySet(),
    val selection: Set<Path> = emptySet(),
    val revision: Int = 0,
    val focused: Boolean = false,
)

internal data class EditorLayoutState(
    val focusedPane: PaneSide = PaneSide.PRIMARY,
    val splitEnabled: Boolean = false,
    val splitOrientation: SplitOrientation = SplitOrientation.HORIZONTAL,
    val splitState: SplitPaneState = SplitPaneState(ratio = 0.5f),
    val foldByPath: Map<String, Set<Int>> = emptyMap(),
)

internal data class EditorScrollState(
    val scrollByPath: Map<Path, EditorScrollSnapshot> = emptyMap(),
)

internal data class CodeActionState(
    val open: Boolean = false,
    val actions: List<CodeActionEntry> = emptyList(),
    val uri: String? = null,
    val text: String? = null,
    val selected: Int = 0,
)

internal data class DialogState(
    val createDialog: CreateEntryDialogState? = null,
    val renameDialog: RenameEntryDialogState? = null,
    val deleteDialog: DeleteEntryDialogState? = null,
    val pasteDialog: PasteEntryDialogState? = null,
    val fileOpConfirm: FileOpConfirmState? = null,
    val pendingClose: PendingClose? = null,
    val findInFilesOpen: Boolean = false,
)

internal data class AppState(
    val layout: LayoutState = LayoutState(),
    val chrome: ChromeState = ChromeState(),
    val tree: TreeState = TreeState(),
    val editorLayout: EditorLayoutState = EditorLayoutState(),
    val editorScroll: EditorScrollState = EditorScrollState(),
    val dialogs: DialogState = DialogState(),
    val codeAction: CodeActionState = CodeActionState(),
)

private fun defaultOutputHeight(): Dp {
    if (java.awt.GraphicsEnvironment.isHeadless()) return 480.dp
    return (java.awt.Toolkit.getDefaultToolkit().screenSize.height / 2f)
        .coerceIn(240f, 1200f)
        .dp
}
