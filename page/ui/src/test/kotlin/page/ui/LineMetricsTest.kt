package page.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class LineMetricsTest {

    @Test
    fun `empty text is one empty line`() {
        val m = LineMetrics.of("")
        assertEquals(1, m.lineCount)
        assertEquals(0, m.lineStart(0))
        assertEquals(0, m.lineEnd(0))
        assertEquals(0, m.lineLength(0))
        assertEquals(0, m.lineForOffset(0))
    }

    @Test
    fun `single line without newline`() {
        val m = LineMetrics.of("abc")
        assertEquals(1, m.lineCount)
        assertEquals(0, m.lineStart(0))
        assertEquals(3, m.lineEnd(0))
        assertEquals(0, m.lineForOffset(0))
        assertEquals(0, m.lineForOffset(3))
    }

    @Test
    fun `multi line offsets and boundaries`() {
        val m = LineMetrics.of("a\nbb\nccc")
        assertEquals(3, m.lineCount)
        assertEquals(0, m.lineStart(0))
        assertEquals(1, m.lineEnd(0))
        assertEquals(2, m.lineStart(1))
        assertEquals(4, m.lineEnd(1))
        assertEquals(5, m.lineStart(2))
        assertEquals(8, m.lineEnd(2))

        assertEquals(0, m.lineForOffset(0))
        assertEquals(0, m.lineForOffset(1))
        assertEquals(1, m.lineForOffset(2))
        assertEquals(1, m.lineForOffset(4))
        assertEquals(2, m.lineForOffset(5))
        assertEquals(2, m.lineForOffset(8))
    }

    @Test
    fun `newline boundary maps end of line not start of next`() {
        val m = LineMetrics.of("ab\ncd")
        assertEquals(0, m.lineForOffset(2))
        assertEquals(1, m.lineForOffset(3))
    }

    @Test
    fun `trailing newline yields trailing empty line`() {
        val m = LineMetrics.of("a\n")
        assertEquals(2, m.lineCount)
        assertEquals(0, m.lineStart(0))
        assertEquals(1, m.lineEnd(0))
        assertEquals(2, m.lineStart(1))
        assertEquals(2, m.lineEnd(1))
        assertEquals(0, m.lineLength(1))
        assertEquals(1, m.lineForOffset(2))
    }

    @Test
    fun `lone newline yields two empty lines`() {
        val m = LineMetrics.of("\n")
        assertEquals(2, m.lineCount)
        assertEquals(0, m.lineForOffset(0))
        assertEquals(1, m.lineForOffset(1))
    }

    @Test
    fun `column within line clamps to line bounds`() {
        val m = LineMetrics.of("a\nbb\nccc")
        assertEquals(0, m.columnIn(1, 2))
        assertEquals(1, m.columnIn(1, 3))
        assertEquals(2, m.columnIn(1, 4))
        assertEquals(2, m.columnIn(1, 99))
        assertEquals(0, m.columnIn(1, -5))
    }

    @Test
    fun `out of range offset clamps to valid line`() {
        val m = LineMetrics.of("a\nbb")
        assertEquals(0, m.lineForOffset(-3))
        assertEquals(1, m.lineForOffset(999))
    }
}
