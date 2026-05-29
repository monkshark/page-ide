package page.editor

import kotlin.test.Test
import kotlin.test.assertEquals

class AutoCloseTest {
    @Test
    fun `inserts closing paren after typing open paren`() {
        val old = TextEdit("foo", 3)
        val new = TextEdit("foo(", 4)
        val result = AutoClose.apply(old, new)
        assertEquals("foo()", result.text)
        assertEquals(4, result.caret)
    }

    @Test
    fun `inserts closing bracket`() {
        val old = TextEdit("", 0)
        val new = TextEdit("[", 1)
        val result = AutoClose.apply(old, new)
        assertEquals("[]", result.text)
        assertEquals(1, result.caret)
    }

    @Test
    fun `inserts closing brace`() {
        val old = TextEdit("if ", 3)
        val new = TextEdit("if {", 4)
        val result = AutoClose.apply(old, new)
        assertEquals("if {}", result.text)
        assertEquals(4, result.caret)
    }

    @Test
    fun `inserts closing double quote`() {
        val old = TextEdit("x = ", 4)
        val new = TextEdit("x = \"", 5)
        val result = AutoClose.apply(old, new)
        assertEquals("x = \"\"", result.text)
        assertEquals(5, result.caret)
    }

    @Test
    fun `inserts closing single quote`() {
        val old = TextEdit("x = ", 4)
        val new = TextEdit("x = '", 5)
        val result = AutoClose.apply(old, new)
        assertEquals("x = ''", result.text)
        assertEquals(5, result.caret)
    }

    @Test
    fun `skips over closing paren when next char already matches`() {
        val old = TextEdit("()", 1)
        val new = TextEdit("())", 2)
        val result = AutoClose.apply(old, new)
        assertEquals("()", result.text)
        assertEquals(2, result.caret)
    }

    @Test
    fun `skips over closing brace`() {
        val old = TextEdit("{}", 1)
        val new = TextEdit("{}}", 2)
        val result = AutoClose.apply(old, new)
        assertEquals("{}", result.text)
        assertEquals(2, result.caret)
    }

    @Test
    fun `skips over double quote`() {
        val old = TextEdit("\"\"", 1)
        val new = TextEdit("\"\"\"", 2)
        val result = AutoClose.apply(old, new)
        assertEquals("\"\"", result.text)
        assertEquals(2, result.caret)
    }

    @Test
    fun `does not auto-close when next char is letter`() {
        val old = TextEdit("abc", 0)
        val new = TextEdit("(abc", 1)
        val result = AutoClose.apply(old, new)
        assertEquals("(abc", result.text)
        assertEquals(1, result.caret)
    }

    @Test
    fun `does not auto-close when next char is digit`() {
        val old = TextEdit("123", 0)
        val new = TextEdit("(123", 1)
        val result = AutoClose.apply(old, new)
        assertEquals("(123", result.text)
        assertEquals(1, result.caret)
    }

    @Test
    fun `does not auto-close quote inside word`() {
        val old = TextEdit("don", 3)
        val new = TextEdit("don'", 4)
        val result = AutoClose.apply(old, new)
        assertEquals("don'", result.text)
        assertEquals(4, result.caret)
    }

    @Test
    fun `auto-closes paren before space`() {
        val old = TextEdit("foo bar", 3)
        val new = TextEdit("foo( bar", 4)
        val result = AutoClose.apply(old, new)
        assertEquals("foo() bar", result.text)
        assertEquals(4, result.caret)
    }

    @Test
    fun `does not trigger on multi-character insert`() {
        val old = TextEdit("", 0)
        val new = TextEdit("(x", 2)
        val result = AutoClose.apply(old, new)
        assertEquals("(x", result.text)
        assertEquals(2, result.caret)
    }

    @Test
    fun `does not trigger on deletion`() {
        val old = TextEdit("(abc", 4)
        val new = TextEdit("(ab", 3)
        val result = AutoClose.apply(old, new)
        assertEquals("(ab", result.text)
        assertEquals(3, result.caret)
    }

    @Test
    fun `does not trigger on selection-only change`() {
        val old = TextEdit("foo", 0)
        val new = TextEdit("foo", 3)
        val result = AutoClose.apply(old, new)
        assertEquals("foo", result.text)
        assertEquals(3, result.caret)
    }

    @Test
    fun `does not trigger on plain letter typing`() {
        val old = TextEdit("foo", 3)
        val new = TextEdit("foox", 4)
        val result = AutoClose.apply(old, new)
        assertEquals("foox", result.text)
        assertEquals(4, result.caret)
    }

    @Test
    fun `wraps selection with parens`() {
        val old = TextEdit("foo bar baz", 4, 7)
        val new = TextEdit("foo ( baz", 5, 5)
        val result = AutoClose.apply(old, new)
        assertEquals("foo (bar) baz", result.text)
        assertEquals(5, result.selectionStart)
        assertEquals(8, result.selectionEnd)
    }

    @Test
    fun `wraps selection with brackets`() {
        val old = TextEdit("xs", 0, 2)
        val new = TextEdit("[", 1, 1)
        val result = AutoClose.apply(old, new)
        assertEquals("[xs]", result.text)
        assertEquals(1, result.selectionStart)
        assertEquals(3, result.selectionEnd)
    }

    @Test
    fun `wraps selection with double quotes`() {
        val old = TextEdit("hello world", 6, 11)
        val new = TextEdit("hello \"", 7, 7)
        val result = AutoClose.apply(old, new)
        assertEquals("hello \"world\"", result.text)
        assertEquals(7, result.selectionStart)
        assertEquals(12, result.selectionEnd)
    }

    @Test
    fun `does not wrap selection when typing non-pair char`() {
        val old = TextEdit("foo bar", 4, 7)
        val new = TextEdit("foo X", 5, 5)
        val result = AutoClose.apply(old, new)
        assertEquals("foo X", result.text)
        assertEquals(5, result.selectionStart)
        assertEquals(5, result.selectionEnd)
    }

    @Test
    fun `removes closer when backspacing empty paren pair`() {
        val old = TextEdit("()", 1)
        val new = TextEdit(")", 0)
        val result = AutoClose.apply(old, new)
        assertEquals("", result.text)
        assertEquals(0, result.caret)
    }

    @Test
    fun `removes closer when backspacing empty bracket pair`() {
        val old = TextEdit("foo[]", 4)
        val new = TextEdit("foo]", 3)
        val result = AutoClose.apply(old, new)
        assertEquals("foo", result.text)
        assertEquals(3, result.caret)
    }

    @Test
    fun `removes closer when backspacing empty quote pair`() {
        val old = TextEdit("\"\"", 1)
        val new = TextEdit("\"", 0)
        val result = AutoClose.apply(old, new)
        assertEquals("", result.text)
        assertEquals(0, result.caret)
    }

    @Test
    fun `does not remove non-pair char on backspace`() {
        val old = TextEdit("(x", 1)
        val new = TextEdit("x", 0)
        val result = AutoClose.apply(old, new)
        assertEquals("x", result.text)
        assertEquals(0, result.caret)
    }

    @Test
    fun `does not remove closer when content sits between pair`() {
        val old = TextEdit("(abc)", 1)
        val new = TextEdit("abc)", 0)
        val result = AutoClose.apply(old, new)
        assertEquals("abc)", result.text)
        assertEquals(0, result.caret)
    }

    @Test
    fun `does not remove on forward delete of closer`() {
        val old = TextEdit("()", 1)
        val new = TextEdit("(", 1)
        val result = AutoClose.apply(old, new)
        assertEquals("(", result.text)
        assertEquals(1, result.caret)
    }

    @Test
    fun `handleBackspacePair removes both parens when caret between empty pair`() {
        val r = AutoClose.handleBackspacePair("foo()", 4)
        assertEquals("foo", r?.text)
        assertEquals(3, r?.caret)
    }

    @Test
    fun `handleBackspacePair removes both braces when caret between empty pair`() {
        val r = AutoClose.handleBackspacePair("a{}b", 2)
        assertEquals("ab", r?.text)
        assertEquals(1, r?.caret)
    }

    @Test
    fun `handleBackspacePair removes empty bracket pair`() {
        val r = AutoClose.handleBackspacePair("[]", 1)
        assertEquals("", r?.text)
        assertEquals(0, r?.caret)
    }

    @Test
    fun `handleBackspacePair removes empty double-quote pair`() {
        val r = AutoClose.handleBackspacePair("x = \"\"", 5)
        assertEquals("x = ", r?.text)
        assertEquals(4, r?.caret)
    }

    @Test
    fun `handleBackspacePair returns null when paren has content`() {
        val r = AutoClose.handleBackspacePair("(x)", 1)
        assertEquals(null, r)
    }

    @Test
    fun `handleBackspacePair returns null when next char does not match closer`() {
        val r = AutoClose.handleBackspacePair("(]", 1)
        assertEquals(null, r)
    }

    @Test
    fun `handleBackspacePair returns null when prev is not an opener`() {
        val r = AutoClose.handleBackspacePair("ab)", 2)
        assertEquals(null, r)
    }

    @Test
    fun `handleBackspacePair returns null at start of text`() {
        val r = AutoClose.handleBackspacePair("", 0)
        assertEquals(null, r)
    }

    @Test
    fun `handleHtmlTagClose inserts matching closer for simple tag`() {
        val r = AutoClose.handleHtmlTagClose("<div>", 5)
        assertEquals("<div></div>", r?.text)
        assertEquals(5, r?.caret)
    }

    @Test
    fun `handleHtmlTagClose preserves trailing content after caret`() {
        val r = AutoClose.handleHtmlTagClose("<section>tail", 9)
        assertEquals("<section></section>tail", r?.text)
        assertEquals(9, r?.caret)
    }

    @Test
    fun `handleHtmlTagClose preserves indent across line for nested tag`() {
        val r = AutoClose.handleHtmlTagClose("<body>\n    <p>", 14)
        assertEquals("<body>\n    <p></p>", r?.text)
        assertEquals(14, r?.caret)
    }

    @Test
    fun `handleHtmlTagClose strips attribute list when deriving tag name`() {
        val r = AutoClose.handleHtmlTagClose("<a href=\"https://x\">", 20)
        assertEquals("<a href=\"https://x\"></a>", r?.text)
        assertEquals(20, r?.caret)
    }

    @Test
    fun `handleHtmlTagClose skips void elements like br`() {
        assertEquals(null, AutoClose.handleHtmlTagClose("<br>", 4))
        assertEquals(null, AutoClose.handleHtmlTagClose("<img src=\"x\">", 13))
        assertEquals(null, AutoClose.handleHtmlTagClose("<input>", 7))
    }

    @Test
    fun `handleHtmlTagClose skips self-closing tag`() {
        val r = AutoClose.handleHtmlTagClose("<custom />", 10)
        assertEquals(null, r)
    }

    @Test
    fun `handleHtmlTagClose skips closing tag, comment, doctype, processing instruction`() {
        assertEquals(null, AutoClose.handleHtmlTagClose("</div>", 6))
        assertEquals(null, AutoClose.handleHtmlTagClose("<!--x-->", 8))
        assertEquals(null, AutoClose.handleHtmlTagClose("<!DOCTYPE html>", 15))
        assertEquals(null, AutoClose.handleHtmlTagClose("<?xml version=\"1.0\"?>", 21))
    }

    @Test
    fun `handleHtmlTagClose ignores text without preceding open angle`() {
        val r = AutoClose.handleHtmlTagClose("plain text>", 11)
        assertEquals(null, r)
    }

    @Test
    fun `handleHtmlTagClose keeps custom tag names with dash`() {
        val r = AutoClose.handleHtmlTagClose("<my-element>", 12)
        assertEquals("<my-element></my-element>", r?.text)
        assertEquals(12, r?.caret)
    }
}
