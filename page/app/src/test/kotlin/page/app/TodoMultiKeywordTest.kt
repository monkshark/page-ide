package page.app

import page.editor.KotlinLexer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TodoMultiKeywordTest {

    private val lexer = KotlinLexer

    @Test
    fun `single-keyword comment is surfaced with full standard options`() {
        val text = "// TODO: just one\n"
        val tokens = lexer.tokenize(text)
        val result = TodoMultiKeyword.analyze(text, tokens)
        assertEquals(1, result.size)
        assertEquals(TodoMultiKeyword.STANDARD_KEYWORDS, result.first().keywords)
        assertEquals(listOf("TODO"), result.first().detectedKeywords)
        assertEquals("TODO", result.first().effectiveKeyword)
    }

    @Test
    fun `two distinct keywords in same comment uses first detected as default`() {
        val text = "// TODO FIXME: hybrid\n"
        val tokens = lexer.tokenize(text)
        val result = TodoMultiKeyword.analyze(text, tokens)
        assertEquals(1, result.size)
        val c = result.first()
        assertEquals(listOf("TODO", "FIXME"), c.detectedKeywords)
        assertEquals(TodoMultiKeyword.STANDARD_KEYWORDS, c.keywords)
        assertEquals("TODO", c.effectiveKeyword)
        assertEquals(0, c.line)
    }

    @Test
    fun `multi-line block comment with two keywords surfaces`() {
        val text = "/* TODO\n   FIXME: stacked */\n"
        val tokens = lexer.tokenize(text)
        val result = TodoMultiKeyword.analyze(text, tokens)
        assertEquals(1, result.size)
        assertTrue("TODO" in result.first().detectedKeywords)
        assertTrue("FIXME" in result.first().detectedKeywords)
    }

    @Test
    fun `text without TODO tokens yields empty result`() {
        val text = "// plain comment\nval x = 1\n"
        val tokens = lexer.tokenize(text)
        assertTrue(TodoMultiKeyword.analyze(text, tokens).isEmpty())
    }

    @Test
    fun `same keyword repeated still surfaces with single detected entry`() {
        val text = "// TODO TODO: dupe\n"
        val tokens = lexer.tokenize(text)
        val result = TodoMultiKeyword.analyze(text, tokens)
        assertEquals(1, result.size)
        assertEquals(listOf("TODO"), result.first().detectedKeywords)
    }

    @Test
    fun `STANDARD_KEYWORDS contains 8 entries ending with EXCEPTION`() {
        assertEquals(8, TodoMultiKeyword.STANDARD_KEYWORDS.size)
        assertEquals(
            listOf("TODO", "FIXME", "HACK", "XXX", "NOTE", "BUG", "REVIEW", "EXCEPTION"),
            TodoMultiKeyword.STANDARD_KEYWORDS,
        )
    }

    @Test
    fun `EXCEPTION keyword is detected by lexer and surfaced`() {
        val text = "// EXCEPTION: rethrow upstream\n"
        val tokens = lexer.tokenize(text)
        val result = TodoMultiKeyword.analyze(text, tokens)
        assertEquals(1, result.size)
        assertEquals("EXCEPTION", result.first().effectiveKeyword)
    }

    @Test
    fun `BUG keyword is detected by lexer and surfaced`() {
        val text = "// BUG: known regression\n"
        val tokens = lexer.tokenize(text)
        val result = TodoMultiKeyword.analyze(text, tokens)
        assertEquals(1, result.size)
        assertEquals("BUG", result.first().effectiveKeyword)
    }

    @Test
    fun `REVIEW keyword is detected by lexer and surfaced`() {
        val text = "// REVIEW: please double-check\n"
        val tokens = lexer.tokenize(text)
        val result = TodoMultiKeyword.analyze(text, tokens)
        assertEquals(1, result.size)
        assertEquals("REVIEW", result.first().effectiveKeyword)
    }

    @Test
    fun `multiple comments each evaluated independently`() {
        val text = "// TODO FIXME: a\nval x = 1\n// HACK NOTE: b\n"
        val tokens = lexer.tokenize(text)
        val result = TodoMultiKeyword.analyze(text, tokens)
        assertEquals(2, result.size)
        val byLine = result.associateBy { it.line }
        assertEquals("TODO", byLine[0]?.effectiveKeyword)
        assertEquals("HACK", byLine[2]?.effectiveKeyword)
    }

    @Test
    fun `non-TODO_TAG tokens without enclosing comment yield no result`() {
        val text = "TODO FIXME\n"
        val tokens = lexer.tokenize(text)
        val result = TodoMultiKeyword.analyze(text, tokens)
        assertNull(result.firstOrNull())
    }

    @Test
    fun `analyze returns empty on no tokens`() {
        assertTrue(TodoMultiKeyword.analyze("abc", emptyList()).isEmpty())
    }

    @Test
    fun `rewriteFirstKeyword replaces matching keyword inside range`() {
        val text = "// TODO: foo\nval x = 1\n"
        val tokens = lexer.tokenize(text)
        val c = TodoMultiKeyword.analyze(text, tokens).first()
        val result = rewriteFirstKeyword(text, c.commentRange, "TODO", "FIXME")
        assertNotNull(result)
        assertEquals("// FIXME: foo\nval x = 1\n", result)
    }

    @Test
    fun `rewriteFirstKeyword only touches first occurrence within range`() {
        val text = "// TODO TODO: dupe\n"
        val tokens = lexer.tokenize(text)
        val c = TodoMultiKeyword.analyze(text, tokens).first()
        val result = rewriteFirstKeyword(text, c.commentRange, "TODO", "FIXME")
        assertEquals("// FIXME TODO: dupe\n", result)
    }

    @Test
    fun `rewriteFirstKeyword returns null when keyword not found in range`() {
        val text = "// TODO: foo\n"
        val tokens = lexer.tokenize(text)
        val c = TodoMultiKeyword.analyze(text, tokens).first()
        val result = rewriteFirstKeyword(text, c.commentRange, "FIXME", "BUG")
        assertNull(result)
    }
}
