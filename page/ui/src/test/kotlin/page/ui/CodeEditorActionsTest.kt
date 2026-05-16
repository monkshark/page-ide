package page.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CodeEditorActionsTest {

    private fun v(text: String, caret: Int): TextFieldValue =
        TextFieldValue(text = text, selection = TextRange(caret))

    private fun v(text: String, start: Int, end: Int): TextFieldValue =
        TextFieldValue(text = text, selection = TextRange(start, end))

    @Test
    fun `applyTab inserts spaces to next tab stop on collapsed caret`() {
        val r = CodeEditorActions.applyTab(v("ab", 2), shift = false)
        assertEquals("ab  ", r.text)
        assertEquals(4, r.selection.start)
    }

    @Test
    fun `applyTab indents multi-line selection`() {
        val r = CodeEditorActions.applyTab(v("a\nb", 0, 3), shift = false)
        assertEquals("    a\n    b", r.text)
    }

    @Test
    fun `applyShiftTab dedents`() {
        val r = CodeEditorActions.applyTab(v("    a\n    b", 0, 11), shift = true)
        assertEquals("a\nb", r.text)
    }

    @Test
    fun `applyEnter copies leading indent`() {
        val r = CodeEditorActions.applyEnter(v("    foo", 7))
        assertEquals("    foo\n    ", r.text)
        assertEquals(12, r.selection.start)
    }

    @Test
    fun `applyEnter adds extra indent after open brace`() {
        val r = CodeEditorActions.applyEnter(v("if (x) {", 8))
        assertEquals("if (x) {\n    ", r.text)
    }

    @Test
    fun `applyBackspace deletes single char on collapsed caret without indent`() {
        val r = CodeEditorActions.applyBackspace(v("abc", 3))!!
        assertEquals("ab", r.text)
        assertEquals(2, r.selection.start)
    }

    @Test
    fun `applyBackspace dedents one tab unit when caret is in pure-indent line`() {
        val r = CodeEditorActions.applyBackspace(v("        x", 8))!!
        assertEquals("    x", r.text)
        assertEquals(4, r.selection.start)
    }

    @Test
    fun `applyBackspace returns null at start of empty document`() {
        assertNull(CodeEditorActions.applyBackspace(v("", 0)))
    }

    @Test
    fun `applyBackspace removes selection`() {
        val r = CodeEditorActions.applyBackspace(v("abcdef", 1, 4))!!
        assertEquals("aef", r.text)
        assertEquals(1, r.selection.start)
    }

    @Test
    fun `applyDelete removes following char`() {
        val r = CodeEditorActions.applyDelete(v("abc", 0))!!
        assertEquals("bc", r.text)
        assertEquals(0, r.selection.start)
    }

    @Test
    fun `applyWordLeft jumps to previous boundary`() {
        val r = CodeEditorActions.applyWordLeft(v("foo bar baz", 11), shift = false)
        assertEquals(8, r.selection.start)
    }

    @Test
    fun `applyWordLeft with shift extends selection`() {
        val r = CodeEditorActions.applyWordLeft(v("foo bar", 7), shift = true)
        assertEquals(7, r.selection.start)
        assertEquals(4, r.selection.end)
    }

    @Test
    fun `applyWordRight jumps to next boundary`() {
        val r = CodeEditorActions.applyWordRight(v("foo bar", 0), shift = false)
        assertEquals(3, r.selection.start)
    }

    @Test
    fun `applyWordBackspace deletes previous word`() {
        val r = CodeEditorActions.applyWordBackspace(v("foo bar", 7))!!
        assertEquals("foo ", r.text)
    }

    @Test
    fun `applyWordBackspace stops at current line start without crossing newline`() {
        val r = CodeEditorActions.applyWordBackspace(v("foo\nbar baz", 11))!!
        assertEquals("foo\nbar ", r.text)
        assertEquals(8, r.selection.start)
    }

    @Test
    fun `applyWordBackspace at line start deletes only the preceding newline`() {
        val r = CodeEditorActions.applyWordBackspace(v("foo\nbar", 4))!!
        assertEquals("foobar", r.text)
        assertEquals(3, r.selection.start)
    }

    @Test
    fun `applyWordBackspace with only whitespace before caret clips to line start`() {
        val r = CodeEditorActions.applyWordBackspace(v("foo\n    bar", 8))!!
        assertEquals("foo\nbar", r.text)
        assertEquals(4, r.selection.start)
    }

    @Test
    fun `applyLineMove down swaps two lines`() {
        val r = CodeEditorActions.applyLineMove(v("a\nb", 0), down = true, duplicate = false)!!
        assertEquals("b\na", r.text)
    }

    @Test
    fun `applyLineMove up at first line returns null`() {
        assertNull(CodeEditorActions.applyLineMove(v("a\nb", 0), down = false, duplicate = false))
    }

    @Test
    fun `applyLineMove duplicate down adds copy`() {
        val r = CodeEditorActions.applyLineMove(v("a\nb", 0), down = true, duplicate = true)!!
        assertEquals("a\na\nb", r.text)
    }

    @Test
    fun `applyCharInsert inserts plain char`() {
        val r = CodeEditorActions.applyCharInsert(v("ab", 2), "c")
        assertEquals("abc", r.text)
        assertEquals(3, r.selection.start)
    }

    @Test
    fun `applyCharInsert auto-closes parenthesis`() {
        val r = CodeEditorActions.applyCharInsert(v("foo", 3), "(")
        assertEquals("foo()", r.text)
        assertEquals(4, r.selection.start)
    }

    @Test
    fun `applyCharInsert wraps selection with quotes`() {
        val r = CodeEditorActions.applyCharInsert(v("hello", 0, 5), "\"")
        assertEquals("\"hello\"", r.text)
    }

    @Test
    fun `selectWordAt selects identifier under offset`() {
        val r = CodeEditorActions.selectWordAt(v("foo bar baz", 0), 5)
        assertEquals(4, r.selection.start)
        assertEquals(7, r.selection.end)
    }

    @Test
    fun `selectLineAt selects entire line`() {
        val r = CodeEditorActions.selectLineAt(v("a\nbar\nc", 0), 3)
        assertEquals(2, r.selection.start)
        assertEquals(5, r.selection.end)
    }

    @Test
    fun `applyDragMove returns null for collapsed selection`() {
        assertNull(CodeEditorActions.applyDragMove(v("hello", 2), dropOffset = 4, copy = false))
    }

    @Test
    fun `applyDragMove returns null when drop is inside selection`() {
        assertNull(CodeEditorActions.applyDragMove(v("hello", 1, 4), dropOffset = 2, copy = false))
    }

    @Test
    fun `applyDragMove forward moves selection past tail and selects moved text`() {
        val r = CodeEditorActions.applyDragMove(v("hello world", 0, 5), dropOffset = 11, copy = false)!!
        assertEquals(" worldhello", r.text)
        assertEquals(6, r.selection.start)
        assertEquals(11, r.selection.end)
    }

    @Test
    fun `applyDragMove backward moves selection before head and selects moved text`() {
        val r = CodeEditorActions.applyDragMove(v("hello world", 6, 11), dropOffset = 0, copy = false)!!
        assertEquals("worldhello ", r.text)
        assertEquals(0, r.selection.start)
        assertEquals(5, r.selection.end)
    }

    @Test
    fun `applyDragMove copy keeps original and inserts at drop`() {
        val r = CodeEditorActions.applyDragMove(v("ab cd", 0, 2), dropOffset = 5, copy = true)!!
        assertEquals("ab cdab", r.text)
        assertEquals(5, r.selection.start)
        assertEquals(7, r.selection.end)
    }

    @Test
    fun `applyDragMove copy backward leaves original intact`() {
        val r = CodeEditorActions.applyDragMove(v("ab cd", 3, 5), dropOffset = 0, copy = true)!!
        assertEquals("cdab cd", r.text)
        assertEquals(0, r.selection.start)
        assertEquals(2, r.selection.end)
    }

    @Test
    fun `applyDragMove drop equal to selection min counts as inside`() {
        assertNull(CodeEditorActions.applyDragMove(v("hello", 1, 4), dropOffset = 1, copy = false))
        assertNull(CodeEditorActions.applyDragMove(v("hello", 1, 4), dropOffset = 4, copy = false))
    }
}
