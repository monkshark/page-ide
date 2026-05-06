package page.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CursorMotionTest {
    @Test
    fun moveLeftStopsAtZero() {
        val c = EditorContent("abc", Selection.at(0))
        val r = CursorMotion.moveLeft(c, extend = false)
        assertEquals(0, r.selection.caret)
    }

    @Test
    fun moveRightStopsAtEnd() {
        val c = EditorContent("abc", Selection.at(3))
        val r = CursorMotion.moveRight(c, extend = false)
        assertEquals(3, r.selection.caret)
    }

    @Test
    fun moveExtendKeepsAnchor() {
        val c = EditorContent("hello", Selection.at(2))
        val r = CursorMotion.moveRight(c, extend = true)
        assertEquals(2, r.selection.anchor)
        assertEquals(3, r.selection.caret)
    }

    @Test
    fun moveCollapsesSelectionWithoutExtend() {
        val c = EditorContent("hello", Selection(1, 4))
        val r = CursorMotion.moveLeft(c, extend = false)
        assertEquals(3, r.selection.anchor)
        assertEquals(3, r.selection.caret)
    }

    @Test
    fun moveLineHomeAndEnd() {
        val c = EditorContent("ab\ncde\nfg", Selection.at(5))
        val home = CursorMotion.moveLineHome(c, extend = false)
        assertEquals(3, home.selection.caret)
        val end = CursorMotion.moveLineEnd(c, extend = false)
        assertEquals(6, end.selection.caret)
    }

    @Test
    fun moveDocStartAndEnd() {
        val c = EditorContent("ab\ncd", Selection.at(2))
        assertEquals(0, CursorMotion.moveDocStart(c, extend = false).selection.caret)
        assertEquals(5, CursorMotion.moveDocEnd(c, extend = false).selection.caret)
    }

    @Test
    fun selectAllSpansEntireText() {
        val c = EditorContent("abc", Selection.at(1))
        val r = CursorMotion.selectAll(c)
        assertEquals(0, r.selection.anchor)
        assertEquals(3, r.selection.caret)
    }

    @Test
    fun deleteBackwardCollapsedRemovesPrevChar() {
        val c = EditorContent("abc", Selection.at(2))
        val r = CursorMotion.deleteBackward(c)
        assertEquals("ac", r.text)
        assertEquals(1, r.selection.caret)
    }

    @Test
    fun deleteBackwardWithSelectionRemovesRange() {
        val c = EditorContent("abcdef", Selection(2, 4))
        val r = CursorMotion.deleteBackward(c)
        assertEquals("abef", r.text)
        assertEquals(2, r.selection.caret)
        assertTrue(r.selection.isCollapsed)
    }

    @Test
    fun deleteBackwardAtZeroNoop() {
        val c = EditorContent("abc", Selection.at(0))
        assertEquals(c, CursorMotion.deleteBackward(c))
    }

    @Test
    fun deleteForwardCollapsedRemovesNextChar() {
        val c = EditorContent("abc", Selection.at(1))
        val r = CursorMotion.deleteForward(c)
        assertEquals("ac", r.text)
        assertEquals(1, r.selection.caret)
    }

    @Test
    fun deleteForwardAtEndNoop() {
        val c = EditorContent("abc", Selection.at(3))
        assertEquals(c, CursorMotion.deleteForward(c))
    }

    @Test
    fun deleteWordBackwardRemovesPriorWord() {
        val c = EditorContent("hello world", Selection.at(11))
        val r = CursorMotion.deleteWordBackward(c)
        assertEquals("hello ", r.text)
        assertEquals(6, r.selection.caret)
    }

    @Test
    fun deleteWordForwardRemovesNextWord() {
        val c = EditorContent("hello world", Selection.at(0))
        val r = CursorMotion.deleteWordForward(c)
        assertEquals(" world", r.text)
        assertEquals(0, r.selection.caret)
    }

    @Test
    fun insertReplacesSelection() {
        val c = EditorContent("hello world", Selection(6, 11))
        val r = CursorMotion.insert(c, "kitty")
        assertEquals("hello kitty", r.text)
        assertEquals(11, r.selection.caret)
    }

    @Test
    fun insertAtCollapsedAddsTextAtCaret() {
        val c = EditorContent("ab", Selection.at(1))
        val r = CursorMotion.insert(c, "X")
        assertEquals("aXb", r.text)
        assertEquals(2, r.selection.caret)
    }

    @Test
    fun moveWordRightSkipsToNextBoundary() {
        val c = EditorContent("foo bar baz", Selection.at(0))
        val r = CursorMotion.moveWordRight(c, extend = false)
        assertTrue(r.selection.caret > 0, "should advance past 'foo'")
    }
}
