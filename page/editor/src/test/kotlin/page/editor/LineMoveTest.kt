package page.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LineMoveTest {
    @Test
    fun `moveUp on first line returns null`() {
        val r = LineMove.moveUp(TextEdit("foo\nbar", 1))
        assertNull(r)
    }

    @Test
    fun `moveUp swaps with line above`() {
        val r = LineMove.moveUp(TextEdit("foo\nbar\nbaz", 5))!!
        assertEquals("bar\nfoo\nbaz", r.text)
        assertEquals(1, r.selectionStart)
        assertEquals(1, r.selectionEnd)
    }

    @Test
    fun `moveUp with multi-line selection moves block up`() {
        val r = LineMove.moveUp(TextEdit("a\nb\nc\nd", 2, 5))!!
        assertEquals("b\nc\na\nd", r.text)
        assertEquals(0, r.selectionStart)
        assertEquals(3, r.selectionEnd)
    }

    @Test
    fun `moveUp on last line without trailing newline`() {
        val r = LineMove.moveUp(TextEdit("foo\nbar", 4))!!
        assertEquals("bar\nfoo", r.text)
        assertEquals(0, r.selectionStart)
    }

    @Test
    fun `moveDown on last line returns null`() {
        val r = LineMove.moveDown(TextEdit("foo\nbar", 5))
        assertNull(r)
    }

    @Test
    fun `moveDown swaps with empty trailing line when file ends with newline`() {
        val r = LineMove.moveDown(TextEdit("foo\nbar\n", 4))!!
        assertEquals("foo\n\nbar", r.text)
        assertEquals(5, r.selectionStart)
    }

    @Test
    fun `moveDown swaps with line below`() {
        val r = LineMove.moveDown(TextEdit("foo\nbar\nbaz", 1))!!
        assertEquals("bar\nfoo\nbaz", r.text)
        assertEquals(5, r.selectionStart)
    }

    @Test
    fun `moveDown with multi-line selection moves block down`() {
        val r = LineMove.moveDown(TextEdit("a\nb\nc\nd", 0, 3))!!
        assertEquals("c\na\nb\nd", r.text)
        assertEquals(2, r.selectionStart)
        assertEquals(5, r.selectionEnd)
    }

    @Test
    fun `duplicateUp inserts copy above and selection stays on copy`() {
        val r = LineMove.duplicateUp(TextEdit("foo\nbar", 1))
        assertEquals("foo\nfoo\nbar", r.text)
        assertEquals(1, r.selectionStart)
    }

    @Test
    fun `duplicateUp on multi-line selection`() {
        val r = LineMove.duplicateUp(TextEdit("a\nb\nc", 0, 3))
        assertEquals("a\nb\na\nb\nc", r.text)
        assertEquals(0, r.selectionStart)
        assertEquals(3, r.selectionEnd)
    }

    @Test
    fun `duplicateDown inserts copy below and selection moves to copy`() {
        val r = LineMove.duplicateDown(TextEdit("foo\nbar", 1))
        assertEquals("foo\nfoo\nbar", r.text)
        assertEquals(5, r.selectionStart)
    }

    @Test
    fun `duplicateDown on last line without trailing newline`() {
        val r = LineMove.duplicateDown(TextEdit("foo\nbar", 5))
        assertEquals("foo\nbar\nbar", r.text)
        assertEquals(9, r.selectionStart)
    }

    @Test
    fun `duplicateDown on multi-line selection`() {
        val r = LineMove.duplicateDown(TextEdit("a\nb\nc", 0, 3))
        assertEquals("a\nb\na\nb\nc", r.text)
        assertEquals(4, r.selectionStart)
        assertEquals(7, r.selectionEnd)
    }

    @Test
    fun `moveUp preserves reverse selection direction`() {
        val r = LineMove.moveUp(TextEdit("foo\nbar", selectionStart = 6, selectionEnd = 4))!!
        assertEquals("bar\nfoo", r.text)
        assertEquals(2, r.selectionStart)
        assertEquals(0, r.selectionEnd)
    }

    @Test
    fun `moveDown preserves reverse selection direction`() {
        val r = LineMove.moveDown(TextEdit("foo\nbar", selectionStart = 2, selectionEnd = 0))!!
        assertEquals("bar\nfoo", r.text)
        assertEquals(6, r.selectionStart)
        assertEquals(4, r.selectionEnd)
    }

    @Test
    fun `moveUp empty caret on second line at column zero`() {
        val r = LineMove.moveUp(TextEdit("foo\nbar", 4))!!
        assertEquals("bar\nfoo", r.text)
        assertEquals(0, r.selectionStart)
    }

    @Test
    fun `moveDown empty caret at end of line`() {
        val r = LineMove.moveDown(TextEdit("foo\nbar\nbaz", 3))!!
        assertEquals("bar\nfoo\nbaz", r.text)
        assertEquals(7, r.selectionStart)
    }

    @Test
    fun `duplicateUp on last line without trailing newline`() {
        val r = LineMove.duplicateUp(TextEdit("foo\nbar", 5))
        assertEquals("foo\nbar\nbar", r.text)
        assertEquals(5, r.selectionStart)
    }

    @Test
    fun `duplicateDown on first line of two`() {
        val r = LineMove.duplicateDown(TextEdit("foo\nbar", 1))
        assertEquals("foo\nfoo\nbar", r.text)
        assertEquals(5, r.selectionStart)
    }
}
