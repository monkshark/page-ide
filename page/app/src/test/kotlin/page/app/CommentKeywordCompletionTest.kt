package page.app

import page.runtime.*

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommentKeywordCompletionTest {

    @Test
    fun `items list has dismiss sentinel at index 0 followed by STANDARD_KEYWORDS`() {
        val items = CommentKeywordCompletion.items
        assertEquals(TodoMultiKeyword.STANDARD_KEYWORDS.size + 1, items.size)
        assertTrue(items.first().label.isEmpty())
        assertTrue(items.first().insertText.isEmpty())
        assertEquals(TodoMultiKeyword.STANDARD_KEYWORDS, items.drop(1).map { it.label })
    }

    @Test
    fun `detect right after slash slash with no whitespace flags needsLeadingSpace`() {
        val ctx = CommentKeywordCompletion.detect("//", 2)
        assertNotNull(ctx)
        assertEquals(2, ctx.anchor)
        assertTrue(ctx.needsLeadingSpace)
    }

    @Test
    fun `detect right after slash slash with single space clears needsLeadingSpace`() {
        val text = "// "
        val ctx = CommentKeywordCompletion.detect(text, text.length)
        assertNotNull(ctx)
        assertEquals(3, ctx.anchor)
        assertEquals(false, ctx.needsLeadingSpace)
    }

    @Test
    fun `detect right after slash star pair triggers`() {
        val ctx = CommentKeywordCompletion.detect("/*", 2)
        assertNotNull(ctx)
        assertEquals(2, ctx.anchor)
        assertTrue(ctx.needsLeadingSpace)
    }

    @Test
    fun `detect right after kdoc slash star star triggers and prefers 3-char opener`() {
        val ctx = CommentKeywordCompletion.detect("/**", 3)
        assertNotNull(ctx)
        assertEquals(3, ctx.anchor)
        assertTrue(ctx.needsLeadingSpace)
    }

    @Test
    fun `detect with partial keyword keeps anchor at keyword start`() {
        val text = "//TO"
        val ctx = CommentKeywordCompletion.detect(text, text.length)
        assertNotNull(ctx)
        assertEquals(2, ctx.anchor)
        assertTrue(ctx.needsLeadingSpace)
    }

    @Test
    fun `detect with space and partial keyword anchors after whitespace`() {
        val text = "//   TO"
        val ctx = CommentKeywordCompletion.detect(text, text.length)
        assertNotNull(ctx)
        assertEquals(5, ctx.anchor)
        assertEquals(false, ctx.needsLeadingSpace)
    }

    @Test
    fun `detect returns null when caret is mid-line after non-keyword text`() {
        val text = "// hello world"
        assertNull(CommentKeywordCompletion.detect(text, text.length))
    }

    @Test
    fun `detect returns null when not inside any opener`() {
        assertNull(CommentKeywordCompletion.detect("foo bar", 7))
        assertNull(CommentKeywordCompletion.detect("/", 1))
        assertNull(CommentKeywordCompletion.detect("", 0))
    }

    @Test
    fun `detect returns null when newline separates opener from caret`() {
        val text = "//\n  TO"
        assertNull(CommentKeywordCompletion.detect(text, text.length))
    }

    @Test
    fun `detect treats triple-slash kdoc as slash slash opener`() {
        val ctx = CommentKeywordCompletion.detect("///", 3)
        assertNotNull(ctx)
        assertEquals(3, ctx.anchor)
        assertTrue(ctx.needsLeadingSpace)
    }

    @Test
    fun `detect inside trailing line comment still triggers`() {
        val text = "val x = 1 // "
        val ctx = CommentKeywordCompletion.detect(text, text.length)
        assertNotNull(ctx)
        assertEquals(text.length, ctx.anchor)
        assertEquals(false, ctx.needsLeadingSpace)
    }

    @Test
    fun `detect suppresses when typed prefix exactly matches a standard keyword`() {
        for (kw in TodoMultiKeyword.STANDARD_KEYWORDS) {
            val text = "// $kw"
            assertNull(
                CommentKeywordCompletion.detect(text, text.length),
                "expected suppression after exact match for $kw",
            )
        }
    }

    @Test
    fun `detect re-triggers after exact match if user keeps typing`() {
        val text = "// TODOX"
        val ctx = CommentKeywordCompletion.detect(text, text.length)
        assertNotNull(ctx)
        assertEquals(3, ctx.anchor)
    }

    @Test
    fun `detect re-triggers after exact match if user erases a char`() {
        val text = "// TOD"
        val ctx = CommentKeywordCompletion.detect(text, text.length)
        assertNotNull(ctx)
        assertEquals(3, ctx.anchor)
    }

    @Test
    fun `detect suppresses when caret is mid-word inside a complete keyword`() {
        val text = "// BUG"
        for (caret in 3..text.length) {
            assertNull(
                CommentKeywordCompletion.detect(text, caret),
                "expected suppression at caret=$caret within complete BUG",
            )
        }
    }

    @Test
    fun `detect suppresses when caret is before complete keyword followed by more text`() {
        val text = "// FIXME tail"
        val caret = 3
        assertNull(CommentKeywordCompletion.detect(text, caret))
    }
}
