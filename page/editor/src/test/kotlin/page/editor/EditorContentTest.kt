package page.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditorContentTest {
    @Test
    fun selectionStartEndOrderedRegardlessOfDirection() {
        val forward = Selection(2, 5)
        val backward = Selection(5, 2)
        assertEquals(2, forward.start)
        assertEquals(5, forward.end)
        assertEquals(2, backward.start)
        assertEquals(5, backward.end)
    }

    @Test
    fun selectionCollapsedDetected() {
        assertTrue(Selection(3, 3).isCollapsed)
        assertFalse(Selection(3, 4).isCollapsed)
    }

    @Test
    fun replaceSelectionInsertsAndCollapsesCaret() {
        val c = EditorContent("hello world", Selection(6, 11))
        val r = c.replaceSelection("there")
        assertEquals("hello there", r.text)
        assertEquals(11, r.selection.caret)
        assertTrue(r.selection.isCollapsed)
    }

    @Test
    fun replaceSelectionEmptyDoesPureInsertAtCaret() {
        val c = EditorContent("ab", Selection.at(1))
        val r = c.replaceSelection("X")
        assertEquals("aXb", r.text)
        assertEquals(2, r.selection.caret)
    }

    @Test
    fun replaceClampsOutOfRangeBounds() {
        val c = EditorContent("abc", Selection.at(0))
        val r = c.replace(-5, 100, "Z")
        assertEquals("Z", r.text)
        assertEquals(1, r.selection.caret)
    }

    @Test
    fun withSelectionClampsOutOfRange() {
        val c = EditorContent("abc", Selection.at(0))
        val r = c.withSelection(Selection(-2, 100))
        assertEquals(0, r.selection.anchor)
        assertEquals(3, r.selection.caret)
    }

    @Test
    fun ofClampsCaret() {
        val c = EditorContent.of("abc", caret = 99)
        assertEquals(3, c.selection.caret)
    }
}
