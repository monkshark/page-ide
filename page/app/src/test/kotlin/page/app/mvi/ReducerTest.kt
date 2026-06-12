package page.app.mvi

import androidx.compose.ui.unit.dp
import page.app.CreateEntryDialogState
import page.app.CreateEntryKind
import page.app.DeleteEntryDialogState
import page.app.EditorScrollSnapshot
import page.app.PaneSide
import page.app.PendingClose
import page.app.ReferencesQueryState
import page.app.RenameEntryDialogState
import page.atlas.render.AtlasViewTab
import page.editor.SplitPaneState
import page.lsp.CodeActionEntry
import page.lsp.RenameWorkspaceEdit
import page.runtime.RunConfig
import page.runtime.RunConfigsState
import page.ui.GlassPalette
import java.nio.file.Path
import kotlin.test.assertNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ReducerTest {

    @Test
    fun `toggle problems flips open flag`() {
        val s = AppState()
        val opened = reduce(s, IdeEvent.Panel.ToggleProblems)
        assertTrue(opened.layout.problemsOpen)
        val closed = reduce(opened, IdeEvent.Panel.ToggleProblems)
        assertFalse(closed.layout.problemsOpen)
    }

    @Test
    fun `close output forces flag false`() {
        val s = AppState(AppState().layout.copy(outputOpen = true))
        val next = reduce(s, IdeEvent.Panel.CloseOutput)
        assertFalse(next.layout.outputOpen)
    }

    @Test
    fun `resize sidebar clamps to bounds`() {
        val s = AppState()
        val grown = reduce(s, IdeEvent.Panel.ResizeSidebar(10_000.dp))
        assertEquals(600.dp, grown.layout.sidebarWidth)
        val shrunk = reduce(s, IdeEvent.Panel.ResizeSidebar((-10_000).dp))
        assertEquals(160.dp, shrunk.layout.sidebarWidth)
    }

    @Test
    fun `resize terminal clamps to terminal bounds`() {
        val s = AppState()
        val grown = reduce(s, IdeEvent.Panel.ResizeTerminal(10_000.dp))
        assertEquals(600.dp, grown.layout.terminalHeight)
        val shrunk = reduce(s, IdeEvent.Panel.ResizeTerminal((-10_000).dp))
        assertEquals(120.dp, shrunk.layout.terminalHeight)
    }

    @Test
    fun `resize output clamps to wide output bounds`() {
        val s = AppState()
        val grown = reduce(s, IdeEvent.Panel.ResizeOutput(10_000.dp))
        assertEquals(1200.dp, grown.layout.outputHeight)
    }

    @Test
    fun `toggle atlas flips open flag and close forces false`() {
        val s = AppState()
        val opened = reduce(s, IdeEvent.Panel.ToggleAtlas)
        assertTrue(opened.layout.atlasOpen)
        val toggledOff = reduce(opened, IdeEvent.Panel.ToggleAtlas)
        assertFalse(toggledOff.layout.atlasOpen)
        val closed = reduce(opened, IdeEvent.Panel.CloseAtlas)
        assertFalse(closed.layout.atlasOpen)
    }

    @Test
    fun `focus in atlas opens panel on dependency tab`() {
        val s = AppState().copy(
            layout = AppState().layout.copy(atlasOpen = false, atlasViewTab = AtlasViewTab.GRAPH),
        )
        val focused = reduce(s, IdeEvent.Panel.FocusInAtlas)
        assertTrue(focused.layout.atlasOpen)
        assertEquals(AtlasViewTab.DEPENDENCY, focused.layout.atlasViewTab)
    }

    @Test
    fun `show atlas calls opens panel on calls tab`() {
        val s = AppState().copy(
            layout = AppState().layout.copy(atlasOpen = false, atlasViewTab = AtlasViewTab.DEPENDENCY),
        )
        val shown = reduce(s, IdeEvent.Panel.ShowAtlasCalls)
        assertTrue(shown.layout.atlasOpen)
        assertEquals(AtlasViewTab.CALLS, shown.layout.atlasViewTab)
    }

    @Test
    fun `atlas project mode change replaces flag`() {
        val s = AppState()
        val on = reduce(s, IdeEvent.Panel.AtlasProjectModeChanged(true))
        assertTrue(on.layout.atlasProjectMode)
        val off = reduce(on, IdeEvent.Panel.AtlasProjectModeChanged(false))
        assertFalse(off.layout.atlasProjectMode)
    }

    @Test
    fun `atlas vcs overlay defaults on and change replaces flag`() {
        val s = AppState()
        assertTrue(s.layout.atlasVcsOverlay)
        val off = reduce(s, IdeEvent.Panel.AtlasVcsOverlayChanged(false))
        assertFalse(off.layout.atlasVcsOverlay)
        val on = reduce(off, IdeEvent.Panel.AtlasVcsOverlayChanged(true))
        assertTrue(on.layout.atlasVcsOverlay)
    }

    @Test
    fun `atlas follow active defaults off and change replaces flag`() {
        val s = AppState()
        assertFalse(s.layout.atlasFollowActive)
        val on = reduce(s, IdeEvent.Panel.AtlasFollowActiveChanged(true))
        assertTrue(on.layout.atlasFollowActive)
        val off = reduce(on, IdeEvent.Panel.AtlasFollowActiveChanged(false))
        assertFalse(off.layout.atlasFollowActive)
    }

    @Test
    fun `atlas view tab defaults to dependency and change replaces value`() {
        val s = AppState()
        assertEquals(AtlasViewTab.DEPENDENCY, s.layout.atlasViewTab)
        val graph = reduce(s, IdeEvent.Panel.AtlasViewTabChanged(AtlasViewTab.GRAPH))
        assertEquals(AtlasViewTab.GRAPH, graph.layout.atlasViewTab)
        val back = reduce(graph, IdeEvent.Panel.AtlasViewTabChanged(AtlasViewTab.DEPENDENCY))
        assertEquals(AtlasViewTab.DEPENDENCY, back.layout.atlasViewTab)
    }

    @Test
    fun `expand panel to atlas forces atlas open and collapse resets`() {
        val s = AppState()
        val expanded = reduce(s, IdeEvent.Panel.ExpandPanel(ExpandedPanel.ATLAS))
        assertEquals(ExpandedPanel.ATLAS, expanded.layout.expandedPanel)
        assertTrue(expanded.layout.atlasOpen)
        val collapsed = reduce(expanded, IdeEvent.Panel.CollapsePanel)
        assertEquals(ExpandedPanel.NONE, collapsed.layout.expandedPanel)
    }

    @Test
    fun `resize atlas inverts delta sign`() {
        val s = AppState()
        val dragLeft = reduce(s, IdeEvent.Panel.ResizeAtlas((-40).dp))
        assertEquals(400.dp, dragLeft.layout.atlasWidth)
        val dragRight = reduce(s, IdeEvent.Panel.ResizeAtlas(40.dp))
        assertEquals(320.dp, dragRight.layout.atlasWidth)
    }

    @Test
    fun `resize atlas clamps to bounds`() {
        val s = AppState()
        val grown = reduce(s, IdeEvent.Panel.ResizeAtlas((-10_000).dp))
        assertEquals(720.dp, grown.layout.atlasWidth)
        val shrunk = reduce(s, IdeEvent.Panel.ResizeAtlas(10_000.dp))
        assertEquals(240.dp, shrunk.layout.atlasWidth)
    }

    @Test
    fun `collapsed and order changes replace values`() {
        val s = AppState()
        val collapsed = reduce(s, IdeEvent.Panel.ProblemsCollapsedChanged(setOf("a", "b")))
        assertEquals(setOf("a", "b"), collapsed.layout.problemsCollapsed)
        val ordered = reduce(collapsed, IdeEvent.Panel.TodoFileOrderChanged(listOf("x", "y")))
        assertEquals(listOf("x", "y"), ordered.layout.todoFileOrder)
    }

    @Test
    fun `no-op resize keeps slice value-equal`() {
        val s = AppState()
        val next = reduce(s, IdeEvent.Panel.ResizeSidebar(0.dp))
        assertEquals(s.layout, next.layout)
    }

    @Test
    fun `settings toggle and explicit open close`() {
        val s = AppState()
        val toggled = reduce(s, IdeEvent.Chrome.ToggleSettings)
        assertTrue(toggled.chrome.settingsDialogOpen)
        val closed = reduce(toggled, IdeEvent.Chrome.CloseSettings)
        assertFalse(closed.chrome.settingsDialogOpen)
        val opened = reduce(closed, IdeEvent.Chrome.OpenSettings)
        assertTrue(opened.chrome.settingsDialogOpen)
    }

    @Test
    fun `run dialog open and close`() {
        val s = AppState()
        val opened = reduce(s, IdeEvent.Chrome.OpenRunDialog)
        assertTrue(opened.chrome.runDialogOpen)
        val closed = reduce(opened, IdeEvent.Chrome.CloseRunDialog)
        assertFalse(closed.chrome.runDialogOpen)
    }

    @Test
    fun `focus and tree bumps increment counters`() {
        val s = AppState()
        val f1 = reduce(s, IdeEvent.Chrome.BumpEditorFocus)
        val f2 = reduce(f1, IdeEvent.Chrome.BumpEditorFocus)
        assertEquals(2, f2.chrome.editorFocusVersion)
        val t1 = reduce(f2, IdeEvent.Chrome.BumpTreeFocus)
        assertEquals(1, t1.chrome.pendingTreeFocusTick)
    }

    @Test
    fun `palette toast carries deadline`() {
        val s = AppState()
        val next = reduce(s, IdeEvent.Chrome.ShowPaletteToast(1600L))
        assertEquals(1600L, next.chrome.paletteToastUntil)
    }

    @Test
    fun `chrome event leaves layout slice value-equal`() {
        val s = AppState()
        val next = reduce(s, IdeEvent.Chrome.OpenSettings)
        assertEquals(s.layout, next.layout)
    }

    @Test
    fun `tree selection and expansion replace sets`() {
        val s = AppState()
        val a = Path.of("a")
        val b = Path.of("b")
        val selected = reduce(s, IdeEvent.Tree.SelectionChanged(setOf(a, b)))
        assertEquals(setOf(a, b), selected.tree.selection)
        val expanded = reduce(selected, IdeEvent.Tree.ExpandedChanged(setOf(a)))
        assertEquals(setOf(a), expanded.tree.expanded)
        assertEquals(setOf(a, b), expanded.tree.selection)
    }

    @Test
    fun `tree focus change flips flag`() {
        val s = AppState()
        val focused = reduce(s, IdeEvent.Tree.FocusChanged(true))
        assertTrue(focused.tree.focused)
        val blurred = reduce(focused, IdeEvent.Tree.FocusChanged(false))
        assertFalse(blurred.tree.focused)
    }

    @Test
    fun `bump revision increments`() {
        val s = AppState()
        val r1 = reduce(s, IdeEvent.Tree.BumpRevision)
        val r2 = reduce(r1, IdeEvent.Tree.BumpRevision)
        assertEquals(2, r2.tree.revision)
    }

    @Test
    fun `tree event leaves other slices value-equal`() {
        val s = AppState()
        val next = reduce(s, IdeEvent.Tree.BumpRevision)
        assertEquals(s.layout, next.layout)
        assertEquals(s.chrome, next.chrome)
    }

    @Test
    fun `focus pane sets focused side`() {
        val s = AppState()
        val next = reduce(s, IdeEvent.EditorLayout.FocusPane(PaneSide.SECONDARY))
        assertEquals(PaneSide.SECONDARY, next.editorLayout.focusedPane)
    }

    @Test
    fun `disabling split forces focus back to primary`() {
        val split = reduce(AppState(), IdeEvent.EditorLayout.SetSplitEnabled(true))
        val focused = reduce(split, IdeEvent.EditorLayout.FocusPane(PaneSide.SECONDARY))
        assertTrue(focused.editorLayout.splitEnabled)
        assertEquals(PaneSide.SECONDARY, focused.editorLayout.focusedPane)
        val collapsed = reduce(focused, IdeEvent.EditorLayout.SetSplitEnabled(false))
        assertFalse(collapsed.editorLayout.splitEnabled)
        assertEquals(PaneSide.PRIMARY, collapsed.editorLayout.focusedPane)
    }

    @Test
    fun `split state change replaces ratio`() {
        val s = AppState()
        val next = reduce(s, IdeEvent.EditorLayout.SplitStateChanged(SplitPaneState(ratio = 0.3f)))
        assertEquals(0.3f, next.editorLayout.splitState.ratio)
    }

    @Test
    fun `fold change adds and clears entries`() {
        val s = AppState()
        val added = reduce(s, IdeEvent.EditorLayout.FoldChanged("a.kt", setOf(1, 2)))
        assertEquals(setOf(1, 2), added.editorLayout.foldByPath["a.kt"])
        val cleared = reduce(added, IdeEvent.EditorLayout.FoldChanged("a.kt", emptySet()))
        assertFalse("a.kt" in cleared.editorLayout.foldByPath)
    }

    @Test
    fun `editor scroll change records and dedups`() {
        val s = AppState()
        val p = Path.of("a.kt")
        val snap = EditorScrollSnapshot(vertical = 10, horizontal = 0)
        val recorded = reduce(s, IdeEvent.EditorScroll.Changed(p, snap))
        assertEquals(snap, recorded.editorScroll.scrollByPath[p])
        val again = reduce(recorded, IdeEvent.EditorScroll.Changed(p, snap))
        assertSame(recorded.editorScroll.scrollByPath, again.editorScroll.scrollByPath)
    }

    @Test
    fun `editor scroll cleared removes entry`() {
        val p = Path.of("a.kt")
        val recorded = reduce(AppState(), IdeEvent.EditorScroll.Changed(p, EditorScrollSnapshot(5, 5)))
        val cleared = reduce(recorded, IdeEvent.EditorScroll.Cleared(p))
        assertFalse(p in cleared.editorScroll.scrollByPath)
    }

    @Test
    fun `editor scroll change leaves editor layout slice value-equal`() {
        val s = AppState()
        val next = reduce(s, IdeEvent.EditorScroll.Changed(Path.of("a.kt"), EditorScrollSnapshot(1, 1)))
        assertEquals(s.editorLayout, next.editorLayout)
    }

    @Test
    fun `set create dialog stores and clears state`() {
        val s = AppState()
        val state = CreateEntryDialogState(Path.of("dir"), CreateEntryKind.FILE)
        val opened = reduce(s, IdeEvent.Dialog.SetCreate(state))
        assertEquals(state, opened.dialogs.createDialog)
        val cleared = reduce(opened, IdeEvent.Dialog.SetCreate(null))
        assertNull(cleared.dialogs.createDialog)
    }

    @Test
    fun `set rename and delete dialogs are independent`() {
        val s = AppState()
        val rename = reduce(s, IdeEvent.Dialog.SetRename(RenameEntryDialogState(Path.of("a"))))
        val delete = reduce(rename, IdeEvent.Dialog.SetDelete(DeleteEntryDialogState(listOf(Path.of("b")))))
        assertEquals(Path.of("a"), delete.dialogs.renameDialog?.path)
        assertEquals(listOf(Path.of("b")), delete.dialogs.deleteDialog?.paths)
    }

    @Test
    fun `pending close set and cleared`() {
        val s = AppState()
        val pending = reduce(s, IdeEvent.Dialog.SetPendingClose(PendingClose.App))
        assertEquals(PendingClose.App, pending.dialogs.pendingClose)
        val cleared = reduce(pending, IdeEvent.Dialog.SetPendingClose(null))
        assertNull(cleared.dialogs.pendingClose)
    }

    @Test
    fun `find in files open and close`() {
        val s = AppState()
        val opened = reduce(s, IdeEvent.Dialog.OpenFindInFiles)
        assertTrue(opened.dialogs.findInFilesOpen)
        val closed = reduce(opened, IdeEvent.Dialog.CloseFindInFiles)
        assertFalse(closed.dialogs.findInFilesOpen)
    }

    private fun codeAction(title: String) = CodeActionEntry(
        title = title,
        kind = null,
        isPreferred = false,
        edit = RenameWorkspaceEdit.EMPTY,
        command = null,
    )

    @Test
    fun `code actions result populates slice`() {
        val s = AppState()
        val actions = listOf(codeAction("a"), codeAction("b"))
        val next = reduce(
            s,
            IdeEvent.Internal.CodeActionsResult(actions, "file:///x.kt", "text", selected = 1, open = true),
        )
        assertTrue(next.codeAction.open)
        assertEquals(actions, next.codeAction.actions)
        assertEquals(1, next.codeAction.selected)
        assertEquals("file:///x.kt", next.codeAction.uri)
        assertEquals("text", next.codeAction.text)
    }

    @Test
    fun `code action selected change clamps to bounds`() {
        val withActions = reduce(
            AppState(),
            IdeEvent.Internal.CodeActionsResult(listOf(codeAction("a"), codeAction("b")), null, null, 0, true),
        )
        val high = reduce(withActions, IdeEvent.CodeAction.SelectedChange(99))
        assertEquals(1, high.codeAction.selected)
        val low = reduce(withActions, IdeEvent.CodeAction.SelectedChange(-5))
        assertEquals(0, low.codeAction.selected)
    }

    @Test
    fun `code action selected change on empty list stays zero`() {
        val next = reduce(AppState(), IdeEvent.CodeAction.SelectedChange(3))
        assertEquals(0, next.codeAction.selected)
    }

    @Test
    fun `applying code action closes popup and bumps editor focus`() {
        val open = reduce(
            AppState(),
            IdeEvent.Internal.CodeActionsResult(listOf(codeAction("a")), null, null, 0, true),
        )
        val applied = reduce(open, IdeEvent.CodeAction.Apply(codeAction("a")))
        assertFalse(applied.codeAction.open)
        assertEquals(open.chrome.editorFocusVersion + 1, applied.chrome.editorFocusVersion)
    }

    @Test
    fun `dismissing code action closes popup and bumps editor focus`() {
        val open = reduce(
            AppState(),
            IdeEvent.Internal.CodeActionsResult(listOf(codeAction("a")), null, null, 0, true),
        )
        val dismissed = reduce(open, IdeEvent.CodeAction.Dismiss)
        assertFalse(dismissed.codeAction.open)
        assertEquals(open.chrome.editorFocusVersion + 1, dismissed.chrome.editorFocusVersion)
    }

    @Test
    fun `code action result leaves unrelated slices value-equal`() {
        val s = AppState()
        val next = reduce(
            s,
            IdeEvent.Internal.CodeActionsResult(listOf(codeAction("a")), null, null, 0, true),
        )
        assertEquals(s.layout, next.layout)
        assertEquals(s.tree, next.tree)
        assertEquals(s.editorLayout, next.editorLayout)
        assertEquals(s.dialogs, next.dialogs)
    }

    private fun references(symbol: String, loading: Boolean = false) = ReferencesQueryState(
        symbolName = symbol,
        originUri = "file:///x.kt",
        results = emptyList(),
        isLoading = loading,
    )

    @Test
    fun `references result populates and clears query`() {
        val s = AppState()
        val query = references("foo", loading = true)
        val loaded = reduce(s, IdeEvent.Internal.ReferencesResult(query))
        assertEquals(query, loaded.references.query)
        val cleared = reduce(loaded, IdeEvent.Internal.ReferencesResult(null))
        assertNull(cleared.references.query)
    }

    @Test
    fun `references close clears query`() {
        val loaded = reduce(AppState(), IdeEvent.Internal.ReferencesResult(references("foo")))
        val closed = reduce(loaded, IdeEvent.Lsp.ReferencesClose)
        assertNull(closed.references.query)
    }

    @Test
    fun `effect-driven lsp events leave cold state unchanged`() {
        val s = reduce(AppState(), IdeEvent.Internal.ReferencesResult(references("foo")))
        val p = Path.of("a.kt")
        assertSame(s, reduce(s, IdeEvent.Lsp.RequestReferences(p, 1, 2, "foo")))
        assertSame(s, reduce(s, IdeEvent.Lsp.JumpToProblem(p, 1, 2)))
        assertSame(s, reduce(s, IdeEvent.Lsp.ApplyRename(RenameWorkspaceEdit.EMPTY)))
    }

    @Test
    fun `references result leaves unrelated slices value-equal`() {
        val s = AppState()
        val next = reduce(s, IdeEvent.Internal.ReferencesResult(references("foo")))
        assertEquals(s.layout, next.layout)
        assertEquals(s.chrome, next.chrome)
        assertEquals(s.tree, next.tree)
        assertEquals(s.editorLayout, next.editorLayout)
        assertEquals(s.dialogs, next.dialogs)
        assertEquals(s.codeAction, next.codeAction)
    }

    private fun runConfigs(vararg ids: String, active: String? = null) = RunConfigsState(
        configs = ids.map { RunConfig(id = it, name = it, command = "echo $it") },
        activeId = active,
    )

    @Test
    fun `run select config sets active id`() {
        val s = AppState(run = RunState(runConfigs("a", "b")))
        val next = reduce(s, IdeEvent.Run.SelectConfig("b"))
        assertEquals("b", next.run.configs.activeId)
    }

    @Test
    fun `run select unknown config is no-op`() {
        val s = AppState(run = RunState(runConfigs("a", active = "a")))
        val next = reduce(s, IdeEvent.Run.SelectConfig("zzz"))
        assertEquals("a", next.run.configs.activeId)
        assertSame(s.run.configs, next.run.configs)
    }

    @Test
    fun `run save configs replaces slice`() {
        val replacement = runConfigs("x", "y", active = "y")
        val next = reduce(AppState(), IdeEvent.Run.SaveConfigs(replacement))
        assertEquals(replacement, next.run.configs)
    }

    @Test
    fun `internal run configs changed replaces slice`() {
        val loaded = runConfigs("p", active = "p")
        val next = reduce(AppState(), IdeEvent.Internal.RunConfigsChanged(loaded))
        assertEquals(loaded, next.run.configs)
    }

    @Test
    fun `run start stop clear leave cold state unchanged`() {
        val s = AppState(run = RunState(runConfigs("a", active = "a")))
        assertSame(s, reduce(s, IdeEvent.Run.Start))
        assertSame(s, reduce(s, IdeEvent.Run.Stop))
        assertSame(s, reduce(s, IdeEvent.Run.ClearOutput))
    }

    @Test
    fun `run select leaves unrelated slices value-equal`() {
        val s = AppState(run = RunState(runConfigs("a", "b")))
        val next = reduce(s, IdeEvent.Run.SelectConfig("b"))
        assertEquals(s.layout, next.layout)
        assertEquals(s.chrome, next.chrome)
        assertEquals(s.tree, next.tree)
        assertEquals(s.editorLayout, next.editorLayout)
        assertEquals(s.dialogs, next.dialogs)
        assertEquals(s.codeAction, next.codeAction)
        assertEquals(s.references, next.references)
    }

    @Test
    fun `dialog event leaves other slices value-equal`() {
        val s = AppState()
        val next = reduce(s, IdeEvent.Dialog.OpenFindInFiles)
        assertEquals(s.layout, next.layout)
        assertEquals(s.chrome, next.chrome)
        assertEquals(s.tree, next.tree)
        assertEquals(s.editorLayout, next.editorLayout)
    }

    @Test
    fun `palette cycle advances to next value and wraps around`() {
        val all = GlassPalette.values()
        val s = AppState(chrome = ChromeState(palette = all.first()))
        val next = reduce(s, IdeEvent.Palette.Cycle)
        assertEquals(all[1], next.chrome.palette)

        val last = AppState(chrome = ChromeState(palette = all.last()))
        val wrapped = reduce(last, IdeEvent.Palette.Cycle)
        assertEquals(all.first(), wrapped.chrome.palette)
    }

    @Test
    fun `palette cycle is pure and does not set the toast deadline`() {
        val s = AppState()
        val next = reduce(s, IdeEvent.Palette.Cycle)
        assertEquals(s.chrome.paletteToastUntil, next.chrome.paletteToastUntil)
    }

    @Test
    fun `toggle find-in-files closes when open and is a no-op in reduce when closed`() {
        val open = AppState(dialogs = DialogState(findInFilesOpen = true))
        assertFalse(reduce(open, IdeEvent.Palette.ToggleFindInFiles).dialogs.findInFilesOpen)

        val closed = AppState()
        assertSame(closed, reduce(closed, IdeEvent.Palette.ToggleFindInFiles))
    }

    @Test
    fun `internal find-in-files open flips the dialog flag`() {
        val next = reduce(AppState(), IdeEvent.Dialog.OpenFindInFiles)
        assertTrue(next.dialogs.findInFilesOpen)
    }

    @Test
    fun `effect-only palette triggers leave cold state unchanged`() {
        val s = AppState()
        assertSame(s, reduce(s, IdeEvent.Palette.QuickOpen))
        assertSame(s, reduce(s, IdeEvent.Palette.DocumentSymbol))
        assertSame(s, reduce(s, IdeEvent.Palette.WorkspaceSymbol))
        assertSame(s, reduce(s, IdeEvent.Palette.Format))
        assertSame(s, reduce(s, IdeEvent.Palette.CodeActionTrigger))
    }
}
