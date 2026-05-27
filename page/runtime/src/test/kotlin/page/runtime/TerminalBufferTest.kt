package page.runtime

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TerminalBufferTest {

    private val esc = Char(0x1B).toString()

    @Test
    fun `plain text appears in snapshot`() {
        val buf = TerminalBuffer()
        buf.feed("hello")
        val snap = buf.snapshot
        assertEquals(1, snap.size)
        assertEquals("hello", snap[0].plain)
    }

    @Test
    fun `newline creates new line`() {
        val buf = TerminalBuffer()
        buf.feed("a\nb")
        val snap = buf.snapshot
        assertEquals(2, snap.size)
        assertEquals("a", snap[0].plain)
        assertEquals("b", snap[1].plain)
    }

    @Test
    fun `carriage return resets cursor and subsequent writes overwrite from start`() {
        val buf = TerminalBuffer()
        buf.feed("abc\rxy")
        val snap = buf.snapshot
        assertEquals(1, snap.size)
        assertEquals("xyc", snap[0].plain)
    }

    @Test
    fun `backspace removes last character`() {
        val buf = TerminalBuffer()
        buf.feed("abc\b")
        val snap = buf.snapshot
        assertEquals("ab", snap[0].plain)
    }

    @Test
    fun `SGR red foreground applies to following text`() {
        val buf = TerminalBuffer()
        buf.feed("${esc}[31mred${esc}[0m plain")
        val spans = buf.snapshot[0].spans
        assertTrue(spans.any { it.text == "red" && it.style.fg != null })
        assertTrue(spans.any { it.text.contains("plain") && it.style.fg == null })
    }

    @Test
    fun `SGR bold sets bold flag`() {
        val buf = TerminalBuffer()
        buf.feed("${esc}[1mB${esc}[22mP")
        val spans = buf.snapshot[0].spans
        assertTrue(spans.any { it.text == "B" && it.style.bold })
        assertTrue(spans.any { it.text == "P" && !it.style.bold })
    }

    @Test
    fun `SGR italic and underline flags`() {
        val buf = TerminalBuffer()
        buf.feed("${esc}[3;4mIU${esc}[0m")
        val spans = buf.snapshot[0].spans
        val styled = spans.first { it.text == "IU" }
        assertTrue(styled.style.italic)
        assertTrue(styled.style.underline)
    }

    @Test
    fun `SGR reset clears style`() {
        val buf = TerminalBuffer()
        buf.feed("${esc}[1;31mX${esc}[0mY")
        val spans = buf.snapshot[0].spans
        val y = spans.first { it.text == "Y" }
        assertNull(y.style.fg)
        assertEquals(false, y.style.bold)
    }

    @Test
    fun `CSI 0K clears from cursor to end of line`() {
        val buf = TerminalBuffer()
        buf.feed("abc\r${esc}[K")
        assertEquals("", buf.snapshot[0].plain)
    }

    @Test
    fun `CSI 0K at end of line leaves text unchanged`() {
        val buf = TerminalBuffer()
        buf.feed("abc${esc}[K")
        assertEquals("abc", buf.snapshot[0].plain)
    }

    @Test
    fun `CSI 2K clears whole line`() {
        val buf = TerminalBuffer()
        buf.feed("abc${esc}[2K")
        assertEquals("", buf.snapshot[0].plain)
    }

    @Test
    fun `CSI 2J clears whole screen`() {
        val buf = TerminalBuffer()
        buf.feed("line1\nline2\nline3${esc}[2J")
        val snap = buf.snapshot
        assertEquals(1, snap.size)
        assertEquals("", snap[0].plain)
    }

    @Test
    fun `OSC sequence is ignored`() {
        val bel = Char(0x07).toString()
        val buf = TerminalBuffer()
        buf.feed("pre${esc}]0;window title${bel}post")
        assertEquals("prepost", buf.snapshot[0].plain)
    }

    @Test
    fun `OSC terminated by ESC backslash`() {
        val buf = TerminalBuffer()
        buf.feed("pre${esc}]1;title${esc}\\post")
        assertEquals("prepost", buf.snapshot[0].plain)
    }

    @Test
    fun `truecolor SGR applies RGB foreground`() {
        val buf = TerminalBuffer()
        buf.feed("${esc}[38;2;10;20;30mX${esc}[0m")
        val span = buf.snapshot[0].spans.first { it.text == "X" }
        assertNotNull(span.style.fg)
        assertEquals(Color(10, 20, 30), span.style.fg)
    }

    @Test
    fun `256-color SGR maps to palette`() {
        val buf = TerminalBuffer()
        buf.feed("${esc}[38;5;196mR${esc}[0m")
        val span = buf.snapshot[0].spans.first { it.text == "R" }
        assertNotNull(span.style.fg)
    }

    @Test
    fun `background color SGR applies`() {
        val buf = TerminalBuffer()
        buf.feed("${esc}[44mBG${esc}[0m")
        val span = buf.snapshot[0].spans.first { it.text == "BG" }
        assertNotNull(span.style.bg)
    }

    @Test
    fun `bright color SGR uses bright palette`() {
        val buf = TerminalBuffer()
        buf.feed("${esc}[92mBright${esc}[0m")
        val span = buf.snapshot[0].spans.first { it.text == "Bright" }
        assertNotNull(span.style.fg)
    }

    @Test
    fun `default foreground 39 resets fg only`() {
        val buf = TerminalBuffer()
        buf.feed("${esc}[31;44mX${esc}[39mY")
        val spans = buf.snapshot[0].spans
        val y = spans.first { it.text == "Y" }
        assertNull(y.style.fg)
        assertNotNull(y.style.bg)
    }

    @Test
    fun `unknown escape sequence does not crash`() {
        val buf = TerminalBuffer()
        buf.feed("a${esc}=b${esc}>c")
        val text = buf.snapshot[0].plain
        assertTrue(text.contains("a") && text.contains("b") && text.contains("c"))
    }

    @Test
    fun `incomplete CSI at end of chunk does not crash`() {
        val buf = TerminalBuffer()
        buf.feed("pre${esc}[")
        val text = buf.snapshot[0].plain
        assertTrue(text.startsWith("pre"))
    }

    @Test
    fun `consecutive same-style writes coalesce into one span`() {
        val buf = TerminalBuffer()
        buf.feed("${esc}[31ma${esc}[31mb")
        val redSpans = buf.snapshot[0].spans.filter { it.style.fg != null }
        assertEquals(1, redSpans.size)
        assertEquals("ab", redSpans[0].text)
    }

    @Test
    fun `BEL outside OSC is silently consumed`() {
        val bel = Char(0x07).toString()
        val buf = TerminalBuffer()
        buf.feed("a${bel}b")
        assertEquals("ab", buf.snapshot[0].plain)
    }

    @Test
    fun `max lines is enforced`() {
        val buf = TerminalBuffer(maxLines = 3)
        buf.feed("a\nb\nc\nd\ne")
        val snap = buf.snapshot
        assertEquals(3, snap.size)
        assertEquals("c", snap[0].plain)
        assertEquals("d", snap[1].plain)
        assertEquals("e", snap[2].plain)
    }
}
