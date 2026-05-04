package page.editor

import kotlin.test.Test
import kotlin.test.assertEquals

class ReplaceTest {
    @Test
    fun `applyCurrent replaces single range`() {
        val r = Replace.applyCurrent("foo bar", 4..6, "baz")
        assertEquals("foo baz", r.text)
        assertEquals(7, r.caret)
        assertEquals(1, r.replacedCount)
    }

    @Test
    fun `applyCurrent with shorter replacement adjusts caret`() {
        val r = Replace.applyCurrent("hello world", 0..4, "hi")
        assertEquals("hi world", r.text)
        assertEquals(2, r.caret)
    }

    @Test
    fun `applyCurrent with longer replacement adjusts caret`() {
        val r = Replace.applyCurrent("a-b", 1..1, "==")
        assertEquals("a==b", r.text)
        assertEquals(3, r.caret)
    }

    @Test
    fun `applyCurrent with empty replacement deletes match`() {
        val r = Replace.applyCurrent("foo bar baz", 4..6, "")
        assertEquals("foo  baz", r.text)
        assertEquals(4, r.caret)
    }

    @Test
    fun `applyAll replaces every range`() {
        val matches = listOf(0..2, 4..6, 8..10)
        val r = Replace.applyAll("foo foo foo", matches, "bar")
        assertEquals("bar bar bar", r.text)
        assertEquals(3, r.replacedCount)
    }

    @Test
    fun `applyAll with longer replacement grows text`() {
        val matches = listOf(0..0, 2..2)
        val r = Replace.applyAll("a a", matches, "abc")
        assertEquals("abc abc", r.text)
        assertEquals(2, r.replacedCount)
    }

    @Test
    fun `applyAll on empty matches is a no-op`() {
        val r = Replace.applyAll("hello", emptyList(), "x")
        assertEquals("hello", r.text)
        assertEquals(0, r.replacedCount)
    }

    @Test
    fun `applyAll with empty replacement removes all matches`() {
        val matches = listOf(0..2, 4..6)
        val r = Replace.applyAll("foo bar", matches, "")
        assertEquals(" ", r.text)
        assertEquals(2, r.replacedCount)
    }
}
