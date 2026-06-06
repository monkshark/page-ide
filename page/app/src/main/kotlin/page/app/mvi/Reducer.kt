package page.app.mvi

import androidx.compose.ui.unit.dp

internal fun reduce(state: AppState, event: IdeEvent): AppState = when (event) {
    is IdeEvent.Panel -> state.copy(layout = reduceLayout(state.layout, event))
    is IdeEvent.Chrome -> state.copy(chrome = reduceChrome(state.chrome, event))
    is IdeEvent.Tree -> state.copy(tree = reduceTree(state.tree, event))
}

private fun reduceTree(s: TreeState, e: IdeEvent.Tree): TreeState = when (e) {
    is IdeEvent.Tree.SelectionChanged -> s.copy(selection = e.paths)
    is IdeEvent.Tree.ExpandedChanged -> s.copy(expanded = e.paths)
    is IdeEvent.Tree.FocusChanged -> s.copy(focused = e.focused)
    IdeEvent.Tree.BumpRevision -> s.copy(revision = s.revision + 1)
}

private fun reduceChrome(s: ChromeState, e: IdeEvent.Chrome): ChromeState = when (e) {
    IdeEvent.Chrome.OpenSettings -> s.copy(settingsDialogOpen = true)
    IdeEvent.Chrome.CloseSettings -> s.copy(settingsDialogOpen = false)
    IdeEvent.Chrome.ToggleSettings -> s.copy(settingsDialogOpen = !s.settingsDialogOpen)
    IdeEvent.Chrome.OpenRunDialog -> s.copy(runDialogOpen = true)
    IdeEvent.Chrome.CloseRunDialog -> s.copy(runDialogOpen = false)
    is IdeEvent.Chrome.ShowPaletteToast -> s.copy(paletteToastUntil = e.untilMs)
    IdeEvent.Chrome.BumpEditorFocus -> s.copy(editorFocusVersion = s.editorFocusVersion + 1)
    IdeEvent.Chrome.BumpTreeFocus -> s.copy(pendingTreeFocusTick = s.pendingTreeFocusTick + 1)
}

private fun reduceLayout(s: LayoutState, e: IdeEvent.Panel): LayoutState = when (e) {
    IdeEvent.Panel.ToggleProblems -> s.copy(problemsOpen = !s.problemsOpen)
    IdeEvent.Panel.CloseProblems -> s.copy(problemsOpen = false)
    IdeEvent.Panel.ToggleTodo -> s.copy(todoOpen = !s.todoOpen)
    IdeEvent.Panel.CloseTodo -> s.copy(todoOpen = false)
    IdeEvent.Panel.ToggleTerminal -> s.copy(terminalOpen = !s.terminalOpen)
    IdeEvent.Panel.CloseTerminal -> s.copy(terminalOpen = false)
    IdeEvent.Panel.ToggleOutput -> s.copy(outputOpen = !s.outputOpen)
    IdeEvent.Panel.CloseOutput -> s.copy(outputOpen = false)
    is IdeEvent.Panel.ResizeSidebar -> s.copy(sidebarWidth = (s.sidebarWidth + e.deltaDp).coerceIn(160.dp, 600.dp))
    is IdeEvent.Panel.ResizeProblems -> s.copy(problemsHeight = (s.problemsHeight + e.deltaDp).coerceIn(80.dp, 600.dp))
    is IdeEvent.Panel.ResizeTodo -> s.copy(todoHeight = (s.todoHeight + e.deltaDp).coerceIn(80.dp, 600.dp))
    is IdeEvent.Panel.ResizeTerminal -> s.copy(terminalHeight = (s.terminalHeight + e.deltaDp).coerceIn(120.dp, 600.dp))
    is IdeEvent.Panel.ResizeOutput -> s.copy(outputHeight = (s.outputHeight + e.deltaDp).coerceIn(120.dp, 1200.dp))
    is IdeEvent.Panel.ResizeReferences -> s.copy(referencesHeight = (s.referencesHeight + e.deltaDp).coerceIn(80.dp, 600.dp))
    is IdeEvent.Panel.ProblemsCollapsedChanged -> s.copy(problemsCollapsed = e.keys)
    is IdeEvent.Panel.ProblemsFileOrderChanged -> s.copy(problemsFileOrder = e.order)
    is IdeEvent.Panel.TodoCollapsedChanged -> s.copy(todoCollapsed = e.keys)
    is IdeEvent.Panel.TodoFileOrderChanged -> s.copy(todoFileOrder = e.order)
}
