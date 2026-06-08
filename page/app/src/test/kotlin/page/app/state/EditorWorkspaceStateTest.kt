package page.app.state

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import page.app.PaneSide
import page.editor.TabBook
import page.editor.UndoGroupTracker
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class EditorWorkspaceStateTest {
    private fun newState(): EditorWorkspaceState {
        val trackers = mapOf(
            PaneSide.PRIMARY to UndoGroupTracker(),
            PaneSide.SECONDARY to UndoGroupTracker(),
        )
        return EditorWorkspaceState(undoTracker = { trackers.getValue(it) })
    }

    private fun bookWith(count: Int, active: Int): TabBook {
        var book = TabBook()
        for (i in 0 until count) {
            book = book.openOrFocus(Path.of("f$i.txt"), "")
        }
        return book.activate(active)
    }

    private fun EditorWorkspaceState.seed(book: TabBook, focused: PaneSide = PaneSide.PRIMARY) = apply {
        focusedPane = focused
        when (focused) {
            PaneSide.PRIMARY -> primaryPane = primaryPane.copy(book = book)
            PaneSide.SECONDARY -> secondaryPane = secondaryPane.copy(book = book)
        }
    }

    @Test
    fun nextTabAdvancesActiveIndex() {
        val s = newState().seed(bookWith(3, active = 0))
        s.activateAdjacentTab(1)
        assertEquals(1, s.primaryPane.book.activeIndex)
    }

    @Test
    fun prevTabWrapsToLast() {
        val s = newState().seed(bookWith(3, active = 0))
        s.activateAdjacentTab(-1)
        assertEquals(2, s.primaryPane.book.activeIndex)
    }

    @Test
    fun nextTabWrapsToFirst() {
        val s = newState().seed(bookWith(3, active = 2))
        s.activateAdjacentTab(1)
        assertEquals(0, s.primaryPane.book.activeIndex)
    }

    @Test
    fun singleTabIsNoOp() {
        val s = newState().seed(bookWith(1, active = 0))
        s.activateAdjacentTab(1)
        assertEquals(0, s.primaryPane.book.activeIndex)
    }

    @Test
    fun activateAdjacentTabActsOnFocusedPaneOnly() {
        val s = newState().seed(bookWith(3, active = 0), focused = PaneSide.SECONDARY)
        s.activateAdjacentTab(1)
        assertEquals(1, s.secondaryPane.book.activeIndex)
        assertEquals(-1, s.primaryPane.book.activeIndex)
    }

    @Test
    fun handleEditorChangeUpdatesActiveTabAndEditorValue() {
        val s = newState().seed(bookWith(1, active = 0))
        s.handleEditorChange(PaneSide.PRIMARY, TextFieldValue("hello", TextRange(5)))
        assertEquals("hello", s.primaryPane.book.active?.text)
        assertEquals("hello", s.primaryPane.editorValue.text)
        assertEquals(5, s.primaryPane.editorValue.selection.start)
    }

    @Test
    fun activateTabSetsActiveIndex() {
        val s = newState().seed(bookWith(3, active = 0))
        s.activateTab(PaneSide.PRIMARY, 2)
        assertEquals(2, s.primaryPane.book.activeIndex)
    }

    @Test
    fun moveTabReordersTabs() {
        val s = newState().seed(bookWith(3, active = 0))
        s.moveTab(PaneSide.PRIMARY, 0, 2)
        assertEquals("f0.txt", s.primaryPane.book.tabs[2].path.fileName.toString())
    }

    @Test
    fun moveTabAcrossMovesTabToOtherPaneAndFocuses() {
        val s = newState().seed(bookWith(2, active = 0))
        s.splitEnabled = true
        s.moveTabAcross(PaneSide.PRIMARY, 0)
        assertEquals(1, s.primaryPane.book.tabs.size)
        assertEquals(1, s.secondaryPane.book.tabs.size)
        assertEquals(PaneSide.SECONDARY, s.focusedPane)
    }

    @Test
    fun moveTabAcrossIsNoOpWhenNotSplit() {
        val s = newState().seed(bookWith(2, active = 0))
        s.moveTabAcross(PaneSide.PRIMARY, 0)
        assertEquals(2, s.primaryPane.book.tabs.size)
        assertEquals(0, s.secondaryPane.book.tabs.size)
    }

    @Test
    fun mergeDisablesSplitWhenSecondaryEmpties() {
        val s = newState()
        s.primaryPane = s.primaryPane.copy(book = bookWith(2, active = 0))
        s.splitEnabled = true
        s.focusedPane = PaneSide.SECONDARY
        s.mergeSplitIfEmptyPane()
        assertEquals(false, s.splitEnabled)
        assertEquals(2, s.primaryPane.book.tabs.size)
        assertEquals(0, s.secondaryPane.book.tabs.size)
        assertEquals(PaneSide.PRIMARY, s.focusedPane)
    }

    @Test
    fun mergePromotesSecondaryWhenPrimaryEmpties() {
        val s = newState()
        s.secondaryPane = s.secondaryPane.copy(book = bookWith(2, active = 1))
        s.splitEnabled = true
        s.mergeSplitIfEmptyPane()
        assertEquals(false, s.splitEnabled)
        assertEquals(2, s.primaryPane.book.tabs.size)
        assertEquals(0, s.secondaryPane.book.tabs.size)
        assertEquals(PaneSide.PRIMARY, s.focusedPane)
    }

    @Test
    fun mergeIsNoOpWhenBothPanesHaveTabs() {
        val s = newState()
        s.primaryPane = s.primaryPane.copy(book = bookWith(1, active = 0))
        s.secondaryPane = s.secondaryPane.copy(book = bookWith(1, active = 0))
        s.splitEnabled = true
        s.mergeSplitIfEmptyPane()
        assertEquals(true, s.splitEnabled)
    }

    @Test
    fun mergeIsNoOpWhenNotSplit() {
        val s = newState()
        s.mergeSplitIfEmptyPane()
        assertEquals(false, s.splitEnabled)
    }

    @Test
    fun collapseSplitDisablesAndFocusesPrimary() {
        val s = newState()
        s.primaryPane = s.primaryPane.copy(book = bookWith(1, active = 0))
        s.secondaryPane = s.secondaryPane.copy(book = bookWith(1, active = 0))
        s.splitEnabled = true
        s.focusedPane = PaneSide.SECONDARY
        s.collapseSplit()
        assertEquals(false, s.splitEnabled)
        assertEquals(PaneSide.PRIMARY, s.focusedPane)
        assertEquals(1, s.primaryPane.book.tabs.size)
    }
}
