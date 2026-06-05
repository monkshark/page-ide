package page.app.run

import page.runtime.RunConfig

internal class RunActionsController(
    private val isRunning: () -> Boolean,
    private val isCurrentFileActive: () -> Boolean,
    private val buildConfigForActiveFile: () -> RunConfig?,
    private val activeRunConfig: () -> RunConfig?,
    private val startRun: (RunConfig) -> Unit,
    private val stopRun: () -> Unit,
    private val autoSaveBeforeRun: () -> Boolean,
    private val saveAllDirty: () -> Unit,
    private val clearOutputOnRun: () -> Boolean,
    private val clearOutput: () -> Unit,
    private val openTerminalOnRun: () -> Boolean,
    private val terminalOpen: () -> Boolean,
    private val setTerminalOpen: (Boolean) -> Unit,
    private val ensureTerminalTab: () -> Unit,
    private val setOutputOpen: (Boolean) -> Unit,
    private val setRunDialogOpen: (Boolean) -> Unit,
) {
    fun toggleTerminal() {
        val next = !terminalOpen()
        setTerminalOpen(next)
        if (next) ensureTerminalTab()
    }

    fun startActiveRun() {
        if (isRunning()) return
        val cfg = if (isCurrentFileActive()) {
            buildConfigForActiveFile() ?: return
        } else {
            activeRunConfig() ?: return
        }
        if (autoSaveBeforeRun()) saveAllDirty()
        if (clearOutputOnRun()) clearOutput()
        if (openTerminalOnRun()) {
            setTerminalOpen(true)
            ensureTerminalTab()
        }
        setOutputOpen(true)
        startRun(cfg)
    }

    fun stopActiveRun() {
        stopRun()
    }

    fun openRunDialog() {
        setRunDialogOpen(true)
    }
}
