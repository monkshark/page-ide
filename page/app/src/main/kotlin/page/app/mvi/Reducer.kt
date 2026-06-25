package page.app.mvi

import androidx.compose.ui.unit.dp
import page.workspace.EditorScrollMemory
import page.app.PaneSide
import page.atlas.render.AtlasViewTab
import page.ui.GlassPalette

internal fun reduce(state: AppState, event: IdeEvent): AppState = when (event) {
    is IdeEvent.Panel -> state.copy(layout = reduceLayout(state.layout, event))
    is IdeEvent.Chrome -> state.copy(chrome = reduceChrome(state.chrome, event))
    is IdeEvent.Tree -> state.copy(tree = reduceTree(state.tree, event))
    is IdeEvent.EditorLayout -> state.copy(editorLayout = reduceEditorLayout(state.editorLayout, event))
    is IdeEvent.EditorScroll -> state.copy(editorScroll = reduceEditorScroll(state.editorScroll, event))
    is IdeEvent.Dialog -> state.copy(dialogs = reduceDialogs(state.dialogs, event))
    is IdeEvent.CodeAction -> reduceCodeActionEvent(state, event)
    is IdeEvent.Lsp -> reduceLsp(state, event)
    is IdeEvent.Run -> reduceRun(state, event)
    is IdeEvent.Palette -> reducePalette(state, event)
    is IdeEvent.Search -> state
    is IdeEvent.FileTree -> state
    is IdeEvent.Settings -> state
    is IdeEvent.Internal -> reduceInternal(state, event)
}

private fun reducePalette(state: AppState, e: IdeEvent.Palette): AppState = when (e) {
    IdeEvent.Palette.Cycle -> {
        val all = GlassPalette.values()
        val next = all[(all.indexOf(state.chrome.palette) + 1) % all.size]
        state.copy(chrome = state.chrome.copy(palette = next))
    }
    IdeEvent.Palette.ToggleFindInFiles ->
        if (state.dialogs.findInFilesOpen) state.copy(dialogs = state.dialogs.copy(findInFilesOpen = false))
        else state
    IdeEvent.Palette.QuickOpen,
    IdeEvent.Palette.DocumentSymbol,
    IdeEvent.Palette.WorkspaceSymbol,
    IdeEvent.Palette.Format,
    IdeEvent.Palette.CodeActionTrigger -> state
}

private fun reduceRun(state: AppState, e: IdeEvent.Run): AppState = when (e) {
    is IdeEvent.Run.SelectConfig -> state.copy(run = state.run.copy(configs = state.run.configs.select(e.id)))
    is IdeEvent.Run.SaveConfigs -> state.copy(run = state.run.copy(configs = e.state))
    IdeEvent.Run.Start,
    IdeEvent.Run.Stop,
    IdeEvent.Run.ClearOutput -> state
}

private fun reduceLsp(state: AppState, e: IdeEvent.Lsp): AppState = when (e) {
    IdeEvent.Lsp.ReferencesClose -> state.copy(references = state.references.copy(query = null))
    is IdeEvent.Lsp.RequestReferences,
    is IdeEvent.Lsp.JumpToProblem,
    is IdeEvent.Lsp.ApplyRename -> state
}

private fun reduceCodeActionEvent(state: AppState, e: IdeEvent.CodeAction): AppState = when (e) {
    is IdeEvent.CodeAction.SelectedChange -> state.copy(
        codeAction = state.codeAction.copy(
            selected = e.index.coerceIn(0, state.codeAction.actions.lastIndex.coerceAtLeast(0)),
        ),
    )
    is IdeEvent.CodeAction.Apply -> state.copy(
        codeAction = state.codeAction.copy(open = false),
        chrome = state.chrome.copy(editorFocusVersion = state.chrome.editorFocusVersion + 1),
    )
    IdeEvent.CodeAction.Dismiss -> state.copy(
        codeAction = state.codeAction.copy(open = false),
        chrome = state.chrome.copy(editorFocusVersion = state.chrome.editorFocusVersion + 1),
    )
}

private fun reduceInternal(state: AppState, e: IdeEvent.Internal): AppState = when (e) {
    is IdeEvent.Internal.CodeActionsResult -> state.copy(
        codeAction = CodeActionState(
            actions = e.actions,
            uri = e.uri,
            text = e.text,
            selected = e.selected,
            open = e.open,
        ),
    )
    is IdeEvent.Internal.ReferencesResult -> state.copy(
        references = state.references.copy(query = e.state),
    )
    is IdeEvent.Internal.RunConfigsChanged -> state.copy(
        run = state.run.copy(configs = e.state),
    )
}

private fun reduceDialogs(s: DialogState, e: IdeEvent.Dialog): DialogState = when (e) {
    is IdeEvent.Dialog.SetCreate -> s.copy(createDialog = e.state)
    is IdeEvent.Dialog.SetRename -> s.copy(renameDialog = e.state)
    is IdeEvent.Dialog.SetDelete -> s.copy(deleteDialog = e.state)
    is IdeEvent.Dialog.SetPaste -> s.copy(pasteDialog = e.state)
    is IdeEvent.Dialog.SetFileOpConfirm -> s.copy(fileOpConfirm = e.state)
    is IdeEvent.Dialog.SetPendingClose -> s.copy(pendingClose = e.state)
    IdeEvent.Dialog.OpenFindInFiles -> s.copy(findInFilesOpen = true)
    IdeEvent.Dialog.CloseFindInFiles -> s.copy(findInFilesOpen = false)
}

private fun reduceEditorLayout(s: EditorLayoutState, e: IdeEvent.EditorLayout): EditorLayoutState = when (e) {
    is IdeEvent.EditorLayout.FocusPane -> s.copy(focusedPane = e.side)
    is IdeEvent.EditorLayout.SetSplitEnabled ->
        if (e.enabled) s.copy(splitEnabled = true)
        else s.copy(splitEnabled = false, focusedPane = PaneSide.PRIMARY)
    is IdeEvent.EditorLayout.SplitStateChanged -> s.copy(splitState = e.state)
    is IdeEvent.EditorLayout.FoldChanged ->
        s.copy(foldByPath = if (e.lines.isEmpty()) s.foldByPath - e.key else s.foldByPath + (e.key to e.lines))
}

private fun reduceEditorScroll(s: EditorScrollState, e: IdeEvent.EditorScroll): EditorScrollState = when (e) {
    is IdeEvent.EditorScroll.Changed -> s.copy(scrollByPath = EditorScrollMemory.put(s.scrollByPath, e.path, e.snapshot))
    is IdeEvent.EditorScroll.Cleared -> s.copy(scrollByPath = EditorScrollMemory.clear(s.scrollByPath, e.path))
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
    is IdeEvent.Panel.SelectSideView ->
        s.copy(activeSideView = if (s.activeSideView == e.view) null else e.view)
    IdeEvent.Panel.ToggleProblems -> s.copy(problemsOpen = !s.problemsOpen)
    IdeEvent.Panel.CloseProblems -> s.copy(problemsOpen = false)
    IdeEvent.Panel.ToggleTodo -> s.copy(todoOpen = !s.todoOpen)
    IdeEvent.Panel.CloseTodo -> s.copy(todoOpen = false)
    IdeEvent.Panel.ToggleTerminal -> s.copy(terminalOpen = !s.terminalOpen)
    IdeEvent.Panel.CloseTerminal -> s.copy(terminalOpen = false)
    IdeEvent.Panel.ToggleOutput -> s.copy(outputOpen = !s.outputOpen)
    IdeEvent.Panel.CloseOutput -> s.copy(outputOpen = false)
    IdeEvent.Panel.ToggleAtlas ->
        if (s.atlasOpen || s.expandedPanel == ExpandedPanel.ATLAS) {
            s.copy(atlasOpen = false, expandedPanel = if (s.expandedPanel == ExpandedPanel.ATLAS) ExpandedPanel.NONE else s.expandedPanel)
        } else {
            s.copy(expandedPanel = ExpandedPanel.ATLAS)
        }
    IdeEvent.Panel.DockAtlas -> s.copy(atlasOpen = true, expandedPanel = ExpandedPanel.NONE)
    IdeEvent.Panel.CloseAtlas ->
        s.copy(atlasOpen = false, expandedPanel = if (s.expandedPanel == ExpandedPanel.ATLAS) ExpandedPanel.NONE else s.expandedPanel)
    IdeEvent.Panel.FocusInAtlas -> s.copy(expandedPanel = ExpandedPanel.ATLAS, atlasViewTab = AtlasViewTab.RELATIONS)
    IdeEvent.Panel.ShowAtlasCalls -> s.copy(expandedPanel = ExpandedPanel.ATLAS, atlasViewTab = AtlasViewTab.CALLS)
    is IdeEvent.Panel.AtlasProjectModeChanged -> s.copy(atlasProjectMode = e.enabled)
    is IdeEvent.Panel.AtlasViewTabChanged -> s.copy(atlasViewTab = e.tab)
    is IdeEvent.Panel.AtlasVcsOverlayChanged -> s.copy(atlasVcsOverlay = e.enabled)
    is IdeEvent.Panel.AtlasFollowActiveChanged -> s.copy(atlasFollowActive = e.enabled)
    is IdeEvent.Panel.ExpandPanel ->
        if (e.target == ExpandedPanel.ATLAS) s.copy(expandedPanel = e.target, atlasOpen = false)
        else s.copy(expandedPanel = e.target)
    IdeEvent.Panel.CollapsePanel -> s.copy(expandedPanel = ExpandedPanel.NONE)
    is IdeEvent.Panel.ResizeSidebar -> s.copy(sidebarWidth = (s.sidebarWidth + e.deltaDp).coerceIn(160.dp, 600.dp))
    is IdeEvent.Panel.ResizeProblems -> s.copy(problemsHeight = (s.problemsHeight + e.deltaDp).coerceIn(80.dp, 600.dp))
    is IdeEvent.Panel.ResizeTodo -> s.copy(todoHeight = (s.todoHeight + e.deltaDp).coerceIn(80.dp, 600.dp))
    is IdeEvent.Panel.ResizeTerminal -> s.copy(terminalHeight = (s.terminalHeight + e.deltaDp).coerceIn(120.dp, 600.dp))
    is IdeEvent.Panel.ResizeOutput -> s.copy(outputHeight = (s.outputHeight + e.deltaDp).coerceIn(120.dp, 1200.dp))
    is IdeEvent.Panel.ResizeAtlas -> s.copy(atlasWidth = (s.atlasWidth - e.deltaDp).coerceIn(240.dp, 720.dp))
    is IdeEvent.Panel.ResizeReferences -> s.copy(referencesHeight = (s.referencesHeight + e.deltaDp).coerceIn(80.dp, 600.dp))
    is IdeEvent.Panel.ProblemsCollapsedChanged -> s.copy(problemsCollapsed = e.keys)
    is IdeEvent.Panel.ProblemsFileOrderChanged -> s.copy(problemsFileOrder = e.order)
    is IdeEvent.Panel.TodoCollapsedChanged -> s.copy(todoCollapsed = e.keys)
    is IdeEvent.Panel.TodoFileOrderChanged -> s.copy(todoFileOrder = e.order)
}
