package page.app.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import page.app.CreateEntryDialogState
import page.app.DeleteEntryDialogState
import page.app.RenameEntryDialogState
import page.app.filetree.LargeCopyDialogState
import page.app.filetree.PasteEntryDialogState
import page.app.mvi.ExpandedPanel
import page.app.mvi.IdeStore
import page.atlas.render.AtlasViewTab
import page.editor.IndexedFile
import page.lsp.DocumentSymbolEntry

internal class LayoutUiState(private val store: IdeStore = IdeStore()) {
    var sidebarWidth: Dp
        get() = store.layout.sidebarWidth
        set(value) = store.updateLayout { it.copy(sidebarWidth = value) }

    var problemsOpen: Boolean
        get() = store.layout.problemsOpen
        set(value) = store.updateLayout { it.copy(problemsOpen = value) }
    var problemsHeight: Dp
        get() = store.layout.problemsHeight
        set(value) = store.updateLayout { it.copy(problemsHeight = value) }
    var problemsCollapsed: Set<String>
        get() = store.layout.problemsCollapsed
        set(value) = store.updateLayout { it.copy(problemsCollapsed = value) }
    var problemsFileOrder: List<String>
        get() = store.layout.problemsFileOrder
        set(value) = store.updateLayout { it.copy(problemsFileOrder = value) }

    var todoOpen: Boolean
        get() = store.layout.todoOpen
        set(value) = store.updateLayout { it.copy(todoOpen = value) }
    var todoHeight: Dp
        get() = store.layout.todoHeight
        set(value) = store.updateLayout { it.copy(todoHeight = value) }
    var todoCollapsed: Set<String>
        get() = store.layout.todoCollapsed
        set(value) = store.updateLayout { it.copy(todoCollapsed = value) }
    var todoFileOrder: List<String>
        get() = store.layout.todoFileOrder
        set(value) = store.updateLayout { it.copy(todoFileOrder = value) }

    var terminalOpen: Boolean
        get() = store.layout.terminalOpen
        set(value) = store.updateLayout { it.copy(terminalOpen = value) }
    var terminalHeight: Dp
        get() = store.layout.terminalHeight
        set(value) = store.updateLayout { it.copy(terminalHeight = value) }

    var outputOpen: Boolean
        get() = store.layout.outputOpen
        set(value) = store.updateLayout { it.copy(outputOpen = value) }
    var outputHeight: Dp
        get() = store.layout.outputHeight
        set(value) = store.updateLayout { it.copy(outputHeight = value) }

    var referencesHeight: Dp
        get() = store.layout.referencesHeight
        set(value) = store.updateLayout { it.copy(referencesHeight = value) }

    var atlasOpen: Boolean
        get() = store.layout.atlasOpen
        set(value) = store.updateLayout { it.copy(atlasOpen = value) }
    var atlasWidth: Dp
        get() = store.layout.atlasWidth
        set(value) = store.updateLayout { it.copy(atlasWidth = value) }
    var atlasProjectMode: Boolean
        get() = store.layout.atlasProjectMode
        set(value) = store.updateLayout { it.copy(atlasProjectMode = value) }
    var atlasViewTab: AtlasViewTab
        get() = store.layout.atlasViewTab
        set(value) = store.updateLayout { it.copy(atlasViewTab = value) }
    var atlasVcsOverlay: Boolean
        get() = store.layout.atlasVcsOverlay
        set(value) = store.updateLayout { it.copy(atlasVcsOverlay = value) }
    var expandedPanel: ExpandedPanel
        get() = store.layout.expandedPanel
        set(value) = store.updateLayout { it.copy(expandedPanel = value) }

    var createDialog: CreateEntryDialogState?
        get() = store.dialogs.createDialog
        set(value) = store.updateDialogs { it.copy(createDialog = value) }
    var renameDialog: RenameEntryDialogState?
        get() = store.dialogs.renameDialog
        set(value) = store.updateDialogs { it.copy(renameDialog = value) }
    var deleteDialog: DeleteEntryDialogState?
        get() = store.dialogs.deleteDialog
        set(value) = store.updateDialogs { it.copy(deleteDialog = value) }
    var pasteDialog: PasteEntryDialogState?
        get() = store.dialogs.pasteDialog
        set(value) = store.updateDialogs { it.copy(pasteDialog = value) }
    var largeCopyState: LargeCopyDialogState? by mutableStateOf(null)

    var quickOpen by mutableStateOf(false)
    var quickOpenIndex by mutableStateOf<List<IndexedFile>>(emptyList())
    var documentSymbolOpen by mutableStateOf(false)
    var documentSymbolList by mutableStateOf<List<DocumentSymbolEntry>>(emptyList())
    var documentSymbolUri by mutableStateOf("")
    var workspaceSymbolOpen by mutableStateOf(false)
}
