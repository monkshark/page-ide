package page.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IndentTest {
    @Test
    fun `tab inserts spaces to next tab stop at column 0`() {
        val r = Indent.handleTab(TextEdit("abc", 0))
        assertEquals("    abc", r.text)
        assertEquals(4, r.caret)
    }

    @Test
    fun `tab inserts spaces to next tab stop at column 1`() {
        val r = Indent.handleTab(TextEdit("abc", 1))
        assertEquals("a   bc", r.text)
        assertEquals(4, r.caret)
    }

    @Test
    fun `tab inserts spaces to next tab stop at column 4`() {
        val r = Indent.handleTab(TextEdit("    abc", 4))
        assertEquals("        abc", r.text)
        assertEquals(8, r.caret)
    }

    @Test
    fun `tab on single-line selection indents line start and preserves selection`() {
        val r = Indent.handleTab(TextEdit("foo bar", 4, 7))
        assertEquals("    foo bar", r.text)
        assertEquals(8, r.selectionStart)
        assertEquals(11, r.selectionEnd)
    }

    @Test
    fun `tab on single-line selection at line start keeps selection content`() {
        val r = Indent.handleTab(TextEdit("hello world", 0, 5))
        assertEquals("    hello world", r.text)
        assertEquals(4, r.selectionStart)
        assertEquals(9, r.selectionEnd)
    }

    @Test
    fun `tab on reverse single-line selection preserves direction`() {
        val r = Indent.handleTab(TextEdit("foo bar", 7, 4))
        assertEquals("    foo bar", r.text)
        assertEquals(11, r.selectionStart)
        assertEquals(8, r.selectionEnd)
    }

    @Test
    fun `tab indents both lines of multi-line selection`() {
        val r = Indent.handleTab(TextEdit("abc\ndef", 0, 7))
        assertEquals("    abc\n    def", r.text)
        assertEquals(0, r.selectionStart)
        assertEquals(15, r.selectionEnd)
    }

    @Test
    fun `tab on multi-line selection includes only lines that contain selected chars`() {
        val r = Indent.handleTab(TextEdit("a\nb\nc", 0, 4))
        assertEquals("    a\n    b\nc", r.text)
        assertEquals(0, r.selectionStart)
        assertEquals(12, r.selectionEnd)
    }

    @Test
    fun `tab on selection ending at line start excludes that line`() {
        val r = Indent.handleTab(TextEdit("a\nb\nc", 0, 2))
        assertEquals("    a\nb\nc", r.text)
        assertEquals(0, r.selectionStart)
        assertEquals(6, r.selectionEnd)
    }

    @Test
    fun `shift-tab removes four leading spaces`() {
        val r = Indent.handleShiftTab(TextEdit("    abc", 7))
        assertEquals("abc", r.text)
        assertEquals(3, r.caret)
    }

    @Test
    fun `shift-tab removes only available leading spaces under tab unit`() {
        val r = Indent.handleShiftTab(TextEdit("  abc", 5))
        assertEquals("abc", r.text)
        assertEquals(3, r.caret)
    }

    @Test
    fun `shift-tab clamps caret when caret was inside removed indent`() {
        val r = Indent.handleShiftTab(TextEdit("    abc", 2))
        assertEquals("abc", r.text)
        assertEquals(0, r.caret)
    }

    @Test
    fun `shift-tab is a no-op when line has no leading whitespace`() {
        val r = Indent.handleShiftTab(TextEdit("abc", 1))
        assertEquals("abc", r.text)
        assertEquals(1, r.caret)
    }

    @Test
    fun `shift-tab unindents both lines of multi-line selection`() {
        val r = Indent.handleShiftTab(TextEdit("    abc\n    def", 0, 15))
        assertEquals("abc\ndef", r.text)
        assertEquals(0, r.selectionStart)
        assertEquals(7, r.selectionEnd)
    }

    @Test
    fun `shift-tab treats a single tab character as one indent unit`() {
        val r = Indent.handleShiftTab(TextEdit("\tabc", 4))
        assertEquals("abc", r.text)
        assertEquals(3, r.caret)
    }

    @Test
    fun `enter preserves leading indent of current line`() {
        val r = Indent.handleEnter(TextEdit("    abc", 7))
        assertEquals("    abc\n    ", r.text)
        assertEquals(12, r.caret)
    }

    @Test
    fun `enter with no indent inserts plain newline`() {
        val r = Indent.handleEnter(TextEdit("abc", 3))
        assertEquals("abc\n", r.text)
        assertEquals(4, r.caret)
    }

    @Test
    fun `enter after open brace adds extra indent`() {
        val r = Indent.handleEnter(TextEdit("if {", 4))
        assertEquals("if {\n    ", r.text)
        assertEquals(9, r.caret)
    }

    @Test
    fun `enter after open paren adds extra indent`() {
        val r = Indent.handleEnter(TextEdit("    foo(", 8))
        assertEquals("    foo(\n        ", r.text)
        assertEquals(17, r.caret)
    }

    @Test
    fun `enter after colon adds extra indent`() {
        val r = Indent.handleEnter(TextEdit("def f():", 8))
        assertEquals("def f():\n    ", r.text)
        assertEquals(13, r.caret)
    }

    @Test
    fun `enter between empty brace pair splits with caret on indented middle line`() {
        val r = Indent.handleEnter(TextEdit("{}", 1))
        assertEquals("{\n    \n}", r.text)
        assertEquals(6, r.caret)
    }

    @Test
    fun `enter between empty paren pair preserves outer indent on closing line`() {
        val r = Indent.handleEnter(TextEdit("    foo()", 8))
        assertEquals("    foo(\n        \n    )", r.text)
        assertEquals(17, r.caret)
    }

    @Test
    fun `enter with selection replaces selection then applies indent`() {
        val r = Indent.handleEnter(TextEdit("abc XYZ def", 4, 7))
        assertEquals("abc \n def", r.text)
        assertEquals(5, r.caret)
    }

    @Test
    fun `closing brace on whitespace-only line unindents one level`() {
        val old = TextEdit("if {\n    ", 9)
        val new = TextEdit("if {\n    }", 10)
        val r = Indent.maybeUnindentClosingBrace(old, new)
        assertEquals("if {\n}", r.text)
        assertEquals(6, r.caret)
    }

    @Test
    fun `closing brace at column 8 unindents to column 4`() {
        val old = TextEdit("        ", 8)
        val new = TextEdit("        }", 9)
        val r = Indent.maybeUnindentClosingBrace(old, new)
        assertEquals("    }", r.text)
        assertEquals(5, r.caret)
    }

    @Test
    fun `closing bracket on whitespace-only line unindents`() {
        val old = TextEdit("[\n    ", 6)
        val new = TextEdit("[\n    ]", 7)
        val r = Indent.maybeUnindentClosingBrace(old, new)
        assertEquals("[\n]", r.text)
        assertEquals(3, r.caret)
    }

    @Test
    fun `closing paren on whitespace-only line unindents`() {
        val old = TextEdit("(\n    ", 6)
        val new = TextEdit("(\n    )", 7)
        val r = Indent.maybeUnindentClosingBrace(old, new)
        assertEquals("(\n)", r.text)
        assertEquals(3, r.caret)
    }

    @Test
    fun `closing brace does not unindent when content sits before it`() {
        val old = TextEdit("    abc", 7)
        val new = TextEdit("    abc}", 8)
        val r = Indent.maybeUnindentClosingBrace(old, new)
        assertEquals("    abc}", r.text)
        assertEquals(8, r.caret)
    }

    @Test
    fun `closing brace at column zero is a no-op`() {
        val old = TextEdit("", 0)
        val new = TextEdit("}", 1)
        val r = Indent.maybeUnindentClosingBrace(old, new)
        assertEquals("}", r.text)
        assertEquals(1, r.caret)
    }

    @Test
    fun `non-closer typing is a no-op for unindent helper`() {
        val old = TextEdit("    ", 4)
        val new = TextEdit("    x", 5)
        val r = Indent.maybeUnindentClosingBrace(old, new)
        assertEquals("    x", r.text)
        assertEquals(5, r.caret)
    }

    @Test
    fun `multi-char insert is a no-op for unindent helper`() {
        val old = TextEdit("    ", 4)
        val new = TextEdit("    ab", 6)
        val r = Indent.maybeUnindentClosingBrace(old, new)
        assertEquals("    ab", r.text)
        assertEquals(6, r.caret)
    }

    @Test
    fun `backspace removes full indent unit at column 4`() {
        val r = Indent.handleBackspace(TextEdit("    abc", 4))
        assertEquals("abc", r?.text)
        assertEquals(0, r?.caret)
    }

    @Test
    fun `backspace at column 8 removes back to column 4`() {
        val r = Indent.handleBackspace(TextEdit("        abc", 8))
        assertEquals("    abc", r?.text)
        assertEquals(4, r?.caret)
    }

    @Test
    fun `backspace at column 5 needs only one char so default handles it`() {
        val r = Indent.handleBackspace(TextEdit("     abc", 5))
        assertNull(r)
    }

    @Test
    fun `backspace at column 6 in pure indent removes back to column 4`() {
        val r = Indent.handleBackspace(TextEdit("      abc", 6))
        assertEquals("    abc", r?.text)
        assertEquals(4, r?.caret)
    }

    @Test
    fun `backspace at column 1 returns null for default behavior`() {
        val r = Indent.handleBackspace(TextEdit(" abc", 1))
        assertNull(r)
    }

    @Test
    fun `backspace returns null when caret follows non-whitespace`() {
        val r = Indent.handleBackspace(TextEdit("    abc", 7))
        assertNull(r)
    }

    @Test
    fun `backspace returns null when leading contains tab character`() {
        val r = Indent.handleBackspace(TextEdit("\t   abc", 4))
        assertNull(r)
    }

    @Test
    fun `backspace returns null at column 0`() {
        val r = Indent.handleBackspace(TextEdit("abc", 0))
        assertNull(r)
    }

    @Test
    fun `backspace returns null with non-empty selection`() {
        val r = Indent.handleBackspace(TextEdit("    abc", 0, 3))
        assertNull(r)
    }

    @Test
    fun `enter via diff applies leading indent`() {
        val old = TextEdit("    abc", 7)
        val new = TextEdit("    abc\n", 8)
        val r = Indent.maybeApplyEnter(old, new)
        assertEquals("    abc\n    ", r.text)
        assertEquals(12, r.caret)
    }

    @Test
    fun `enter via diff with selection-replace inserts indent`() {
        val old = TextEdit("    abc XYZ", 7, 11)
        val new = TextEdit("    abc\n", 8)
        val r = Indent.maybeApplyEnter(old, new)
        assertEquals("    abc\n    ", r.text)
        assertEquals(12, r.caret)
    }

    @Test
    fun `enter via diff between brace pair smart-splits`() {
        val old = TextEdit("{}", 1)
        val new = TextEdit("{\n}", 2)
        val r = Indent.maybeApplyEnter(old, new)
        assertEquals("{\n    \n}", r.text)
        assertEquals(6, r.caret)
    }

    @Test
    fun `enter via diff is no-op for non-newline insert`() {
        val old = TextEdit("abc", 3)
        val new = TextEdit("abcd", 4)
        val r = Indent.maybeApplyEnter(old, new)
        assertEquals("abcd", r.text)
        assertEquals(4, r.caret)
    }

    @Test
    fun `enter via diff is no-op when length mismatches`() {
        val old = TextEdit("abc", 3)
        val new = TextEdit("abc\n\n", 5)
        val r = Indent.maybeApplyEnter(old, new)
        assertEquals("abc\n\n", r.text)
        assertEquals(5, r.caret)
    }

    @Test
    fun `enter via diff is no-op when caret position mismatches`() {
        val old = TextEdit("abc", 3)
        val new = TextEdit("abc\n", 3)
        val r = Indent.maybeApplyEnter(old, new)
        assertEquals("abc\n", r.text)
        assertEquals(3, r.caret)
    }

    @Test
    fun `literal tab inserts a tab character at caret`() {
        val r = Indent.handleLiteralTab(TextEdit("abc", 1))
        assertEquals("a\tbc", r.text)
        assertEquals(2, r.caret)
    }

    @Test
    fun `literal tab on single-line selection indents line start and preserves selection`() {
        val r = Indent.handleLiteralTab(TextEdit("foo bar", 4, 7))
        assertEquals("\tfoo bar", r.text)
        assertEquals(5, r.selectionStart)
        assertEquals(8, r.selectionEnd)
    }

    @Test
    fun `literal tab on single-line selection at line start keeps selection content`() {
        val r = Indent.handleLiteralTab(TextEdit("```\nhello world\n```", 4, 9))
        assertEquals("```\n\thello world\n```", r.text)
        assertEquals(5, r.selectionStart)
        assertEquals(10, r.selectionEnd)
    }

    @Test
    fun `literal tab on reverse single-line selection preserves direction`() {
        val r = Indent.handleLiteralTab(TextEdit("foo bar", 7, 4))
        assertEquals("\tfoo bar", r.text)
        assertEquals(8, r.selectionStart)
        assertEquals(5, r.selectionEnd)
    }

    @Test
    fun `literal tab on multi-line selection indents each line with tab`() {
        val r = Indent.handleLiteralTab(TextEdit("abc\ndef", 0, 7))
        assertEquals("\tabc\n\tdef", r.text)
        assertEquals(0, r.selectionStart)
        assertEquals(9, r.selectionEnd)
    }

    @Test
    fun `literal tab on partial multi-line selection indents both spanned lines`() {
        val r = Indent.handleLiteralTab(TextEdit("abc\ndef\nghi", 1, 5))
        assertEquals("\tabc\n\tdef\nghi", r.text)
        assertEquals(2, r.selectionStart)
        assertEquals(7, r.selectionEnd)
    }

    @Test
    fun `literal tab preserves reverse selection direction`() {
        val r = Indent.handleLiteralTab(TextEdit("abc\ndef", 7, 0))
        assertEquals("\tabc\n\tdef", r.text)
        assertEquals(9, r.selectionStart)
        assertEquals(0, r.selectionEnd)
    }
}
