package page.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WordBoundaryTest {

    @Test
    fun `nextBoundary at start jumps to end of first word`() {
        assertEquals(5, WordBoundary.nextBoundary("hello world", 0))
    }

    @Test
    fun `nextBoundary skips leading spaces then word`() {
        assertEquals(11, WordBoundary.nextBoundary("hello world", 5))
    }

    @Test
    fun `nextBoundary skips multiple leading spaces`() {
        assertEquals(8, WordBoundary.nextBoundary("ab    cd", 2))
    }

    @Test
    fun `nextBoundary at end stays at end`() {
        assertEquals(5, WordBoundary.nextBoundary("hello", 5))
    }

    @Test
    fun `nextBoundary across word and punctuation`() {
        assertEquals(3, WordBoundary.nextBoundary("foo()", 0))
        assertEquals(5, WordBoundary.nextBoundary("foo()", 3))
    }

    @Test
    fun `nextBoundary jumps over newline once`() {
        assertEquals(6, WordBoundary.nextBoundary("hello\nworld", 5))
    }

    @Test
    fun `nextBoundary stops before newline when starting in word`() {
        assertEquals(5, WordBoundary.nextBoundary("hello\nworld", 0))
    }

    @Test
    fun `prevBoundary at end goes to start of last word`() {
        assertEquals(6, WordBoundary.prevBoundary("hello world", 11))
    }

    @Test
    fun `prevBoundary skips trailing spaces then word`() {
        assertEquals(0, WordBoundary.prevBoundary("hello world", 6))
    }

    @Test
    fun `prevBoundary at start stays at start`() {
        assertEquals(0, WordBoundary.prevBoundary("hello", 0))
    }

    @Test
    fun `prevBoundary across word and punctuation`() {
        assertEquals(3, WordBoundary.prevBoundary("foo()", 5))
        assertEquals(0, WordBoundary.prevBoundary("foo()", 3))
    }

    @Test
    fun `prevBoundary jumps over newline once`() {
        assertEquals(5, WordBoundary.prevBoundary("hello\nworld", 6))
    }

    @Test
    fun `prevBoundary stops after newline when starting in word`() {
        assertEquals(6, WordBoundary.prevBoundary("hello\nworld", 11))
    }

    @Test
    fun `underscore is part of word`() {
        assertEquals(7, WordBoundary.nextBoundary("foo_bar end", 0))
    }

    @Test
    fun `digits are part of word`() {
        assertEquals(5, WordBoundary.nextBoundary("foo42 end", 0))
    }

    @Test
    fun `deleteWordBackward removes previous word`() {
        val r = WordBoundary.deleteWordBackward(TextEdit("hello world", 11))!!
        assertEquals("hello ", r.text)
        assertEquals(6, r.caret)
    }

    @Test
    fun `deleteWordBackward at start returns null`() {
        assertNull(WordBoundary.deleteWordBackward(TextEdit("hello", 0)))
    }

    @Test
    fun `deleteWordBackward with selection returns null`() {
        assertNull(WordBoundary.deleteWordBackward(TextEdit("hello", 1, 3)))
    }

    @Test
    fun `deleteWordBackward removes trailing spaces then word`() {
        val r = WordBoundary.deleteWordBackward(TextEdit("foo bar  ", 9))!!
        assertEquals("foo ", r.text)
        assertEquals(4, r.caret)
    }

    @Test
    fun `deleteWordForward removes next word`() {
        val r = WordBoundary.deleteWordForward(TextEdit("hello world", 0))!!
        assertEquals(" world", r.text)
        assertEquals(0, r.caret)
    }

    @Test
    fun `deleteWordForward at end returns null`() {
        assertNull(WordBoundary.deleteWordForward(TextEdit("hello", 5)))
    }

    @Test
    fun `deleteWordForward with selection returns null`() {
        assertNull(WordBoundary.deleteWordForward(TextEdit("hello", 1, 3)))
    }

    @Test
    fun `wordRangeAt inside word selects whole word`() {
        assertEquals(0 until 5, WordBoundary.wordRangeAt("hello world", 2))
        assertEquals(6 until 11, WordBoundary.wordRangeAt("hello world", 8))
    }

    @Test
    fun `wordRangeAt at word start selects whole word`() {
        assertEquals(6 until 11, WordBoundary.wordRangeAt("hello world", 6))
    }

    @Test
    fun `wordRangeAt at word end (offset == word end) selects last char's word`() {
        assertEquals(0 until 5, WordBoundary.wordRangeAt("hello world", 4))
    }

    @Test
    fun `wordRangeAt on space returns empty`() {
        assertTrue(WordBoundary.wordRangeAt("hello world", 5).isEmpty())
    }

    @Test
    fun `wordRangeAt on newline returns empty`() {
        assertTrue(WordBoundary.wordRangeAt("a\nb", 1).isEmpty())
    }

    @Test
    fun `wordRangeAt on punctuation run selects run`() {
        assertEquals(3 until 5, WordBoundary.wordRangeAt("foo()", 3))
        assertEquals(3 until 5, WordBoundary.wordRangeAt("foo()", 4))
    }

    @Test
    fun `wordRangeAt with underscore stays one word`() {
        assertEquals(0 until 7, WordBoundary.wordRangeAt("foo_bar end", 3))
    }

    @Test
    fun `wordRangeAt with empty string returns empty`() {
        assertTrue(WordBoundary.wordRangeAt("", 0).isEmpty())
    }

    @Test
    fun `lineRangeAt selects whole line excluding newline`() {
        assertEquals(0 until 5, WordBoundary.lineRangeAt("hello\nworld", 2))
        assertEquals(6 until 11, WordBoundary.lineRangeAt("hello\nworld", 8))
    }

    @Test
    fun `lineRangeAt at newline picks preceding line`() {
        assertEquals(0 until 5, WordBoundary.lineRangeAt("hello\nworld", 5))
    }

    @Test
    fun `lineRangeAt empty line returns empty range`() {
        assertEquals(6 until 6, WordBoundary.lineRangeAt("hello\n\nworld", 6))
    }

    @Test
    fun `lineRangeAt at end of text`() {
        assertEquals(6 until 11, WordBoundary.lineRangeAt("hello\nworld", 11))
    }
}
