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
import page.editor.SplitPaneState
import page.lsp.CodeActionEntry
import page.lsp.RenameWorkspaceEdit
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

    @Test
    fun `dialog event leaves other slices value-equal`() {
        val s = AppState()
        val next = reduce(s, IdeEvent.Dialog.OpenFindInFiles)
        assertEquals(s.layout, next.layout)
        assertEquals(s.chrome, next.chrome)
        assertEquals(s.tree, next.tree)
        assertEquals(s.editorLayout, next.editorLayout)
    }
}
