package page.app.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import page.app.CreateEntryDialogState
import page.app.DeleteEntryDialogState
import page.app.RenameEntryDialogState
import page.app.filetree.LargeCopyDialogState
import page.app.filetree.PasteEntryDialogState

internal class LayoutUiState {
    var sidebarWidth: Dp by mutableStateOf(260.dp)

    var problemsOpen by mutableStateOf(false)
    var problemsHeight: Dp by mutableStateOf(220.dp)
    var problemsCollapsed by mutableStateOf(emptySet<String>())
    var problemsFileOrder by mutableStateOf(emptyList<String>())

    var todoOpen by mutableStateOf(false)
    var todoHeight: Dp by mutableStateOf(220.dp)
    var todoCollapsed by mutableStateOf(emptySet<String>())
    var todoFileOrder by mutableStateOf(emptyList<String>())

    var terminalOpen by mutableStateOf(false)
    var terminalHeight: Dp by mutableStateOf(240.dp)

    var outputOpen by mutableStateOf(false)
    var outputHeight: Dp by mutableStateOf(
        (java.awt.Toolkit.getDefaultToolkit().screenSize.height / 2f)
            .coerceIn(240f, 1200f)
            .dp,
    )

    var referencesHeight: Dp by mutableStateOf(220.dp)

    var createDialog: CreateEntryDialogState? by mutableStateOf(null)
    var renameDialog: RenameEntryDialogState? by mutableStateOf(null)
    var deleteDialog: DeleteEntryDialogState? by mutableStateOf(null)
    var pasteDialog: PasteEntryDialogState? by mutableStateOf(null)
    var largeCopyState: LargeCopyDialogState? by mutableStateOf(null)
}
