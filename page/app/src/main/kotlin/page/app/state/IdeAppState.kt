package page.app.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import page.app.AppSettings
import page.app.DropResultToastState
import page.app.FileOpConfirmState
import page.app.HistoryFile
import page.app.PageSettings
import page.app.PendingClose
import page.app.ReferencesQueryState
import page.app.mvi.IdeStore
import page.editor.IndexedFile
import page.lsp.CodeActionEntry
import page.runtime.RunConfigsState
import page.ui.GlassPalette
import page.workspace.WorkspaceFile

internal class IdeAppState(private val store: IdeStore = IdeStore()) {
    var pageSettings: PageSettings by mutableStateOf(
        PageSettings(
            autoSave = AppSettings.loadAutoSave(),
            editor = AppSettings.loadEditor(),
            lsp = AppSettings.loadLsp(),
            autoInput = AppSettings.loadAutoInput(),
            ui = AppSettings.loadUi(),
            run = AppSettings.loadRun(),
        ),
    )
    var palette: GlassPalette by mutableStateOf(pageSettings.ui.palette)
    var paletteToastUntil: Long
        get() = store.chrome.paletteToastUntil
        set(value) = store.updateChrome { it.copy(paletteToastUntil = value) }
    var settingsDialogOpen: Boolean
        get() = store.chrome.settingsDialogOpen
        set(value) = store.updateChrome { it.copy(settingsDialogOpen = value) }

    var sessionLoaded by mutableStateOf(false)
    var historyFile by mutableStateOf(HistoryFile())
    var historyLoaded by mutableStateOf(false)
    var workspaceFile by mutableStateOf(WorkspaceFile())

    var runState: RunConfigsState by mutableStateOf(RunConfigsState())
    var runDialogOpen: Boolean
        get() = store.chrome.runDialogOpen
        set(value) = store.updateChrome { it.copy(runDialogOpen = value) }

    var findInFiles: Boolean
        get() = store.dialogs.findInFilesOpen
        set(value) = store.updateDialogs { it.copy(findInFilesOpen = value) }
    var findInFilesIndex by mutableStateOf<List<IndexedFile>>(emptyList())
    var referencesState: ReferencesQueryState?
        get() = store.references.query
        set(value) = store.updateReferences { it.copy(query = value) }

    var fileOpHistoryVersion by mutableStateOf(0)
    var fileOpConfirm: FileOpConfirmState?
        get() = store.dialogs.fileOpConfirm
        set(value) = store.updateDialogs { it.copy(fileOpConfirm = value) }
    var pendingTreeFocusTick: Int
        get() = store.chrome.pendingTreeFocusTick
        set(value) = store.updateChrome { it.copy(pendingTreeFocusTick = value) }
    var hadFileDialog by mutableStateOf(false)

    var codeActionOpen: Boolean
        get() = store.codeAction.open
        set(value) = store.updateCodeAction { it.copy(open = value) }
    var codeActionList: List<CodeActionEntry>
        get() = store.codeAction.actions
        set(value) = store.updateCodeAction { it.copy(actions = value) }
    var codeActionUri: String?
        get() = store.codeAction.uri
        set(value) = store.updateCodeAction { it.copy(uri = value) }
    var codeActionText: String?
        get() = store.codeAction.text
        set(value) = store.updateCodeAction { it.copy(text = value) }
    var codeActionSelected: Int
        get() = store.codeAction.selected
        set(value) = store.updateCodeAction { it.copy(selected = value) }

    var editorFocusVersion: Int
        get() = store.chrome.editorFocusVersion
        set(value) = store.updateChrome { it.copy(editorFocusVersion = value) }
    var pendingClose: PendingClose?
        get() = store.dialogs.pendingClose
        set(value) = store.updateDialogs { it.copy(pendingClose = value) }
    var dropResultToast: DropResultToastState? by mutableStateOf(null)
}
