package page.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OutputPanelStateTest {

    @Test
    fun `started sets running and clears previous result`() {
        val s = OutputPanelState()
        s.onEvent(RunEvent.Started("python", listOf("a.py"), "/proj"))
        assertTrue(s.running)
        assertEquals("python a.py", s.commandLabel)
        assertEquals("/proj", s.workingDir)
        assertNull(s.lastExitCode)
        assertNull(s.lastError)
    }

    @Test
    fun `stdout and stderr append lines without flipping running`() {
        val s = OutputPanelState()
        s.onEvent(RunEvent.Started("ls", emptyList(), null))
        val before = s.lines.size
        s.onEvent(RunEvent.Stdout("hello\n"))
        s.onEvent(RunEvent.Stderr("oops\n"))
        assertTrue(s.lines.size > before)
        assertTrue(s.running)
    }

    @Test
    fun `exited sets exit code and duration and clears running`() {
        val s = OutputPanelState()
        s.onEvent(RunEvent.Started("ls", emptyList(), null))
        s.onEvent(RunEvent.Exited(0, 123))
        assertFalse(s.running)
        assertEquals(0, s.lastExitCode)
        assertEquals(123L, s.lastDurationMs)
    }

    @Test
    fun `failed marks lastError and clears running`() {
        val s = OutputPanelState()
        s.onEvent(RunEvent.Failed("not found"))
        assertFalse(s.running)
        assertNotNull(s.lastError)
        assertEquals("not found", s.lastError)
    }

    @Test
    fun `clear resets exit fields`() {
        val s = OutputPanelState()
        s.onEvent(RunEvent.Started("ls", emptyList(), null))
        s.onEvent(RunEvent.Exited(0, 50))
        s.clear()
        assertNull(s.lastExitCode)
        assertNull(s.lastError)
        assertNull(s.lastDurationMs)
    }

    @Test
    fun `restart clears previous run output`() {
        val s = OutputPanelState()
        s.onEvent(RunEvent.Started("ls", emptyList(), null))
        s.onEvent(RunEvent.Stdout("first run line 1\n"))
        s.onEvent(RunEvent.Stdout("first run line 2\n"))
        s.onEvent(RunEvent.Exited(0, 10))
        val firstRunTextBefore = s.lines.joinToString("\n") { it.plain }
        assertTrue(firstRunTextBefore.contains("first run line 1"))

        s.onEvent(RunEvent.Started("ls", listOf("-a"), null))
        val afterRestartText = s.lines.joinToString("\n") { it.plain }
        assertFalse(afterRestartText.contains("first run line 1"))
        assertFalse(afterRestartText.contains("first run line 2"))
        assertTrue(s.running)
        assertNull(s.lastExitCode)
    }
}
