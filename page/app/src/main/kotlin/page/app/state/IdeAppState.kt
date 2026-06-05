package page.app.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import page.app.AppSettings
import page.app.HistoryFile
import page.app.PageSettings
import page.ui.GlassPalette
import page.workspace.WorkspaceFile

internal class IdeAppState {
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
    var paletteToastUntil: Long by mutableStateOf(0L)
    var settingsDialogOpen by mutableStateOf(false)

    var sessionLoaded by mutableStateOf(false)
    var historyFile by mutableStateOf(HistoryFile())
    var historyLoaded by mutableStateOf(false)
    var workspaceFile by mutableStateOf(WorkspaceFile())
}
