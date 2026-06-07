package page.app.mvi

import androidx.compose.ui.unit.dp
import page.app.EditorScrollSnapshot
import page.app.PaneSide
import page.editor.SplitPaneState
import java.nio.file.Path
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
}
