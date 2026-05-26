package page.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class TerminalGridTest {

    private val esc = Char(0x1B).toString()
    private val parser = AnsiParser()

    private fun feed(grid: TerminalGrid, text: String) {
        parser.parse(text, grid)
    }

    @Test
    fun `plain text at cursor position`() {
        val grid = TerminalGrid(cols = 80, rows = 24)
        feed(grid, "hello")
        val snap = grid.snapshot()
        val visible = snap.drop(grid.scrollback.size)
        assertEquals("hello", visible[0].plain)
        assertEquals(0, grid.cursorRow)
        assertEquals(5, grid.cursorCol)
    }

    @Test
    fun `newline moves cursor down`() {
        val grid = TerminalGrid(cols = 80, rows = 24)
        feed(grid, "a\r\nb")
        assertEquals(1, grid.cursorRow)
        assertEquals(1, grid.cursorCol)
        val snap = grid.visibleSnapshot()
        assertEquals("a", snap[0].plain)
        assertEquals("b", snap[1].plain)
    }

    @Test
    fun `carriage return resets column`() {
        val grid = TerminalGrid(cols = 80, rows = 24)
        feed(grid, "abc\rxy")
        assertEquals(0, grid.cursorRow)
        assertEquals(2, grid.cursorCol)
        val snap = grid.visibleSnapshot()
        assertEquals("xyc", snap[0].plain)
    }

    @Test
    fun `backspace moves cursor left`() {
        val grid = TerminalGrid(cols = 80, rows = 24)
        feed(grid, "abc\b")
        assertEquals(2, grid.cursorCol)
    }

    @Test
    fun `tab stops at 8-column boundaries`() {
        val grid = TerminalGrid(cols = 80, rows = 24)
        feed(grid, "a\t")
        assertEquals(8, grid.cursorCol)
    }

    @Test
    fun `cursor movement CSI A B C D`() {
        val grid = TerminalGrid(cols = 80, rows = 24)
        feed(grid, "${esc}[10;10H")
        assertEquals(9, grid.cursorRow)
        assertEquals(9, grid.cursorCol)

        feed(grid, "${esc}[3A")
        assertEquals(6, grid.cursorRow)
        assertEquals(9, grid.cursorCol)

        feed(grid, "${esc}[2B")
        assertEquals(8, grid.cursorRow)

        feed(grid, "${esc}[4C")
        assertEquals(13, grid.cursorCol)

        feed(grid, "${esc}[5D")
        assertEquals(8, grid.cursorCol)
    }

    @Test
    fun `CUP sets position (1-based to 0-based)`() {
        val grid = TerminalGrid(cols = 80, rows = 24)
        feed(grid, "${esc}[5;10H")
        assertEquals(4, grid.cursorRow)
        assertEquals(9, grid.cursorCol)
    }

    @Test
    fun `CUP default is row 1, col 1`() {
        val grid = TerminalGrid(cols = 80, rows = 24)
        feed(grid, "xxx${esc}[H")
        assertEquals(0, grid.cursorRow)
        assertEquals(0, grid.cursorCol)
    }

    @Test
    fun `erase in display mode 0 clears below cursor`() {
        val grid = TerminalGrid(cols = 80, rows = 5)
        feed(grid, "line0\r\nline1\r\nline2\r\nline3\r\nline4")
        feed(grid, "${esc}[2;1H${esc}[0J")
        val snap = grid.visibleSnapshot()
        assertEquals("line0", snap[0].plain)
        assertTrue(snap[1].plain.isBlank())
        assertTrue(snap[2].plain.isBlank())
    }

    @Test
    fun `erase in display mode 2 clears all`() {
        val grid = TerminalGrid(cols = 80, rows = 5)
        feed(grid, "hello\r\nworld")
        feed(grid, "${esc}[2J")
        val snap = grid.visibleSnapshot()
        for (line in snap) {
            assertTrue(line.plain.isBlank())
        }
    }

    @Test
    fun `erase in line mode 0 clears from cursor to end`() {
        val grid = TerminalGrid(cols = 80, rows = 5)
        feed(grid, "hello world")
        feed(grid, "${esc}[1;6H${esc}[0K")
        val snap = grid.visibleSnapshot()
        assertEquals("hello", snap[0].plain)
    }

    @Test
    fun `erase in line mode 2 clears whole line`() {
        val grid = TerminalGrid(cols = 80, rows = 5)
        feed(grid, "hello")
        feed(grid, "${esc}[2K")
        val snap = grid.visibleSnapshot()
        assertTrue(snap[0].plain.isBlank())
    }

    @Test
    fun `line wrapping at column boundary`() {
        val grid = TerminalGrid(cols = 5, rows = 3)
        feed(grid, "abcdefgh")
        assertEquals(1, grid.cursorRow)
        assertEquals(3, grid.cursorCol)
        val snap = grid.visibleSnapshot()
        assertEquals("abcde", snap[0].plain)
        assertEquals("fgh", snap[1].plain)
    }

    @Test
    fun `scrolling when writing past bottom`() {
        val grid = TerminalGrid(cols = 10, rows = 3)
        feed(grid, "line0\r\nline1\r\nline2\r\nline3")
        assertEquals(1, grid.scrollback.size)
        assertEquals(2, grid.cursorRow)
        val snap = grid.visibleSnapshot()
        assertEquals("line1", snap[0].plain)
        assertEquals("line2", snap[1].plain)
        assertEquals("line3", snap[2].plain)
    }

    @Test
    fun `save and restore cursor`() {
        val grid = TerminalGrid(cols = 80, rows = 24)
        feed(grid, "${esc}[5;10H${esc}[s")
        feed(grid, "${esc}[1;1H")
        assertEquals(0, grid.cursorRow)
        assertEquals(0, grid.cursorCol)
        feed(grid, "${esc}[u")
        assertEquals(4, grid.cursorRow)
        assertEquals(9, grid.cursorCol)
    }

    @Test
    fun `cursor horizontal absolute CSI G`() {
        val grid = TerminalGrid(cols = 80, rows = 24)
        feed(grid, "hello${esc}[3G")
        assertEquals(2, grid.cursorCol)
    }

    @Test
    fun `vertical position absolute CSI d`() {
        val grid = TerminalGrid(cols = 80, rows = 24)
        feed(grid, "${esc}[5d")
        assertEquals(4, grid.cursorRow)
    }

    @Test
    fun `cursor visibility CSI 25h and 25l`() {
        val grid = TerminalGrid(cols = 80, rows = 24)
        assertTrue(grid.cursorVisible)
        feed(grid, "${esc}[?25l")
        assertFalse(grid.cursorVisible)
        feed(grid, "${esc}[?25h")
        assertTrue(grid.cursorVisible)
    }

    @Test
    fun `autowrap mode CSI 7h and 7l`() {
        val grid = TerminalGrid(cols = 80, rows = 24)
        assertTrue(grid.autoWrap)
        feed(grid, "${esc}[?7l")
        assertFalse(grid.autoWrap)
        feed(grid, "${esc}[?7h")
        assertTrue(grid.autoWrap)
    }

    @Test
    fun `resize preserves content`() {
        val grid = TerminalGrid(cols = 10, rows = 5)
        feed(grid, "hello")
        grid.resize(20, 10)
        assertEquals(20, grid.cols)
        assertEquals(10, grid.rows)
        val snap = grid.visibleSnapshot()
        assertEquals("hello", snap[0].plain)
    }

    @Test
    fun `scroll region CSI r`() {
        val grid = TerminalGrid(cols = 80, rows = 10)
        feed(grid, "${esc}[3;7r")
        assertEquals(2, grid.scrollTop)
        assertEquals(6, grid.scrollBottom)
    }

    @Test
    fun `SGR colors apply to cells`() {
        val grid = TerminalGrid(cols = 80, rows = 24)
        feed(grid, "${esc}[31mred${esc}[0m plain")
        val snap = grid.visibleSnapshot()
        val spans = snap[0].spans
        assertTrue(spans.any { it.text.contains("red") && it.style.fg != null })
        assertTrue(spans.any { it.text.contains("plain") && it.style.fg == null })
    }

    @Test
    fun `snapshot includes scrollback`() {
        val grid = TerminalGrid(cols = 10, rows = 2)
        feed(grid, "line0\r\nline1\r\nline2\r\nline3")
        val full = grid.snapshot()
        assertEquals(4, full.size)
        assertEquals("line0", full[0].plain)
        assertEquals("line1", full[1].plain)
        assertEquals("line2", full[2].plain)
        assertEquals("line3", full[3].plain)
    }

    @Test
    fun `maxScrollback is enforced`() {
        val grid = TerminalGrid(cols = 10, rows = 2, maxScrollback = 3)
        for (i in 0..10) {
            feed(grid, "L$i\r\n")
        }
        assertTrue(grid.scrollback.size <= 3)
    }

    @Test
    fun `OSC sequence is ignored`() {
        val grid = TerminalGrid(cols = 80, rows = 24)
        val bel = Char(0x07).toString()
        feed(grid, "pre${esc}]0;window title${bel}post")
        val snap = grid.visibleSnapshot()
        assertEquals("prepost", snap[0].plain)
    }

    @Test
    fun `no wrap when autowrap disabled`() {
        val grid = TerminalGrid(cols = 5, rows = 3)
        feed(grid, "${esc}[?7l")
        feed(grid, "abcdefgh")
        assertEquals(0, grid.cursorRow)
        val snap = grid.visibleSnapshot()
        assertEquals("abcde", snap[0].plain)
    }
}
