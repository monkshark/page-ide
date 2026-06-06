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

    var findInFiles by mutableStateOf(false)
    var findInFilesIndex by mutableStateOf<List<IndexedFile>>(emptyList())
    var referencesState: ReferencesQueryState? by mutableStateOf(null)

    var fileOpHistoryVersion by mutableStateOf(0)
    var fileOpConfirm: FileOpConfirmState? by mutableStateOf(null)
    var pendingTreeFocusTick: Int
        get() = store.chrome.pendingTreeFocusTick
        set(value) = store.updateChrome { it.copy(pendingTreeFocusTick = value) }
    var hadFileDialog by mutableStateOf(false)

    var codeActionOpen by mutableStateOf(false)
    var codeActionList by mutableStateOf<List<CodeActionEntry>>(emptyList())
    var codeActionUri: String? by mutableStateOf(null)
    var codeActionText: String? by mutableStateOf(null)
    var codeActionSelected by mutableStateOf(0)

    var editorFocusVersion: Int
        get() = store.chrome.editorFocusVersion
        set(value) = store.updateChrome { it.copy(editorFocusVersion = value) }
    var pendingClose: PendingClose? by mutableStateOf(null)
    var dropResultToast: DropResultToastState? by mutableStateOf(null)
}
