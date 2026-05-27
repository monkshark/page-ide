package page.app

import page.runtime.*

import page.editor.KotlinLexer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class KdocContinuationTest {

    private fun computeAt(text: String, caret: Int): KdocContinuationResult? {
        val tokens = KotlinLexer.tokenize(text)
        return KdocContinuation.compute(text, caret, tokens)
    }

    @Test
    fun `caret inside open kdoc inserts star with single-space indent`() {
        val text = "/**\n */"
        val caret = 3
        val r = computeAt(text, caret)
        assertNotNull(r)
        assertEquals("\n * ", r.insertText)
        assertEquals(r.insertText.length, r.caretOffsetWithinInsert)
    }

    @Test
    fun `caret right after opener with closer on same line yields 3-way split`() {
        val text = "/** */"
        val caret = 3
        val r = computeAt(text, caret)
        assertNotNull(r)
        assertEquals("\n * \n", r.insertText)
        assertEquals("\n * ".length, r.caretOffsetWithinInsert)
        assertEquals(1, r.consumeAfterCaret)
    }

    @Test
    fun `3-way split propagates indent of opener line`() {
        val text = "    /**  */"
        val openerStart = text.indexOf("/**")
        val caret = openerStart + 3
        val r = computeAt(text, caret)
        assertNotNull(r)
        assertEquals("\n     * \n    ", r.insertText)
        assertEquals("\n     * ".length, r.caretOffsetWithinInsert)
        assertEquals(2, r.consumeAfterCaret)
    }

    @Test
    fun `multi-line open kdoc does not consume after caret`() {
        val text = "/**\n */"
        val caret = 3
        val r = computeAt(text, caret)
        assertNotNull(r)
        assertEquals("\n * ", r.insertText)
        assertEquals(0, r.consumeAfterCaret)
    }

    @Test
    fun `indent of opener line propagates to continuation`() {
        val text = "    /**\n     */"
        val openerStart = text.indexOf("/**")
        val caret = openerStart + 3
        val r = computeAt(text, caret)
        assertNotNull(r)
        assertEquals("\n     * ", r.insertText)
    }

    @Test
    fun `regular block comment is ignored`() {
        val text = "/* */"
        val caret = 3
        assertNull(computeAt(text, caret))
    }

    @Test
    fun `line comment is ignored`() {
        val text = "// hello\n"
        assertNull(computeAt(text, 3))
    }

    @Test
    fun `caret inside opener slashes is ignored`() {
        val text = "/** */"
        assertNull(computeAt(text, 1))
        assertNull(computeAt(text, 2))
    }

    @Test
    fun `caret on the closing slash is ignored`() {
        val text = "/** */"
        assertNull(computeAt(text, 5))
    }

    @Test
    fun `non-whitespace before opener disables continuation`() {
        val text = "foo /**\n */"
        val openerStart = text.indexOf("/**")
        val caret = openerStart + 3
        assertNull(computeAt(text, caret))
    }

    @Test
    fun `caret outside any token returns null`() {
        val text = "val x = 1\n"
        assertNull(computeAt(text, 5))
    }
}
