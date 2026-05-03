package page.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TextBufferTest {

    @Test
    fun `empty buffer has one empty line`() {
        val buf = TextBuffer()
        assertEquals(0, buf.length)
        assertEquals(1, buf.lineCount)
        assertEquals("", buf.lineAt(0))
        assertEquals("", buf.text())
    }

    @Test
    fun `single line buffer`() {
        val buf = TextBuffer("hello")
        assertEquals(5, buf.length)
        assertEquals(1, buf.lineCount)
        assertEquals("hello", buf.lineAt(0))
    }

    @Test
    fun `multi-line buffer counts lines correctly`() {
        val buf = TextBuffer("a\nb\nc")
        assertEquals(3, buf.lineCount)
        assertEquals("a", buf.lineAt(0))
        assertEquals("b", buf.lineAt(1))
        assertEquals("c", buf.lineAt(2))
    }

    @Test
    fun `trailing newline produces empty last line`() {
        val buf = TextBuffer("a\n")
        assertEquals(2, buf.lineCount)
        assertEquals("a", buf.lineAt(0))
        assertEquals("", buf.lineAt(1))
    }

    @Test
    fun `insert at offset`() {
        val buf = TextBuffer("hllo")
        buf.insert(1, "e")
        assertEquals("hello", buf.text())
    }

    @Test
    fun `insert at end`() {
        val buf = TextBuffer("hi")
        buf.insert(2, "!")
        assertEquals("hi!", buf.text())
    }

    @Test
    fun `insert newline splits into two lines`() {
        val buf = TextBuffer("ab")
        buf.insert(1, "\n")
        assertEquals("a\nb", buf.text())
        assertEquals(2, buf.lineCount)
        assertEquals("a", buf.lineAt(0))
        assertEquals("b", buf.lineAt(1))
    }

    @Test
    fun `delete range`() {
        val buf = TextBuffer("hello world")
        buf.delete(5, 11)
        assertEquals("hello", buf.text())
    }

    @Test
    fun `delete across lines collapses lines`() {
        val buf = TextBuffer("a\nb\nc")
        buf.delete(1, 4)
        assertEquals("ac", buf.text())
        assertEquals(1, buf.lineCount)
    }

    @Test
    fun `insertAt line and col`() {
        val buf = TextBuffer("foo\nbar")
        buf.insertAt(1, 1, "X")
        assertEquals("foo\nbXar", buf.text())
    }

    @Test
    fun `deleteAt line col range`() {
        val buf = TextBuffer("foo\nbar\nbaz")
        buf.deleteAt(0, 1, 2, 1)
        assertEquals("faz", buf.text())
    }

    @Test
    fun `offsetOf converts line col to offset`() {
        val buf = TextBuffer("abc\ndef\nghi")
        assertEquals(0, buf.offsetOf(0, 0))
        assertEquals(3, buf.offsetOf(0, 3))
        assertEquals(4, buf.offsetOf(1, 0))
        assertEquals(7, buf.offsetOf(1, 3))
        assertEquals(8, buf.offsetOf(2, 0))
        assertEquals(11, buf.offsetOf(2, 3))
    }

    @Test
    fun `lineColOf converts offset to line col`() {
        val buf = TextBuffer("abc\ndef\nghi")
        assertEquals(LineCol(0, 0), buf.lineColOf(0))
        assertEquals(LineCol(0, 3), buf.lineColOf(3))
        assertEquals(LineCol(1, 0), buf.lineColOf(4))
        assertEquals(LineCol(2, 3), buf.lineColOf(11))
    }

    @Test
    fun `offsetOf and lineColOf are inverse`() {
        val buf = TextBuffer("hello\nworld\n!")
        for (offset in 0..buf.length) {
            val lc = buf.lineColOf(offset)
            assertEquals(offset, buf.offsetOf(lc.line, lc.col))
        }
    }

    @Test
    fun `insert at out of bounds offset throws`() {
        val buf = TextBuffer("hi")
        assertFailsWith<IllegalArgumentException> { buf.insert(-1, "x") }
        assertFailsWith<IllegalArgumentException> { buf.insert(3, "x") }
    }

    @Test
    fun `delete with invalid range throws`() {
        val buf = TextBuffer("hello")
        assertFailsWith<IllegalArgumentException> { buf.delete(-1, 3) }
        assertFailsWith<IllegalArgumentException> { buf.delete(3, 1) }
        assertFailsWith<IllegalArgumentException> { buf.delete(0, 99) }
    }

    @Test
    fun `lineAt out of bounds throws`() {
        val buf = TextBuffer("a\nb")
        assertFailsWith<IllegalArgumentException> { buf.lineAt(-1) }
        assertFailsWith<IllegalArgumentException> { buf.lineAt(2) }
    }

    @Test
    fun `inserting multiline text updates line count`() {
        val buf = TextBuffer("ac")
        buf.insert(1, "1\n2\n3\nb")
        assertEquals("a1\n2\n3\nbc", buf.text())
        assertEquals(4, buf.lineCount)
        assertEquals("a1", buf.lineAt(0))
        assertEquals("bc", buf.lineAt(3))
    }
}
