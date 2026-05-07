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
}
