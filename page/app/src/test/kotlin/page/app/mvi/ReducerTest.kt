package page.app.mvi

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReducerTest {

    @Test
    fun `toggle problems flips open flag`() {
        val s = AppState()
        val opened = reduce(s, IdeEvent.Panel.ToggleProblems)
        assertTrue(opened.layout.problemsOpen)
        val closed = reduce(opened, IdeEvent.Panel.ToggleProblems)
        assertFalse(closed.layout.problemsOpen)
    }

    @Test
    fun `close output forces flag false`() {
        val s = AppState(AppState().layout.copy(outputOpen = true))
        val next = reduce(s, IdeEvent.Panel.CloseOutput)
        assertFalse(next.layout.outputOpen)
    }

    @Test
    fun `resize sidebar clamps to bounds`() {
        val s = AppState()
        val grown = reduce(s, IdeEvent.Panel.ResizeSidebar(10_000.dp))
        assertEquals(600.dp, grown.layout.sidebarWidth)
        val shrunk = reduce(s, IdeEvent.Panel.ResizeSidebar((-10_000).dp))
        assertEquals(160.dp, shrunk.layout.sidebarWidth)
    }

    @Test
    fun `resize terminal clamps to terminal bounds`() {
        val s = AppState()
        val grown = reduce(s, IdeEvent.Panel.ResizeTerminal(10_000.dp))
        assertEquals(600.dp, grown.layout.terminalHeight)
        val shrunk = reduce(s, IdeEvent.Panel.ResizeTerminal((-10_000).dp))
        assertEquals(120.dp, shrunk.layout.terminalHeight)
    }

    @Test
    fun `resize output clamps to wide output bounds`() {
        val s = AppState()
        val grown = reduce(s, IdeEvent.Panel.ResizeOutput(10_000.dp))
        assertEquals(1200.dp, grown.layout.outputHeight)
    }

    @Test
    fun `collapsed and order changes replace values`() {
        val s = AppState()
        val collapsed = reduce(s, IdeEvent.Panel.ProblemsCollapsedChanged(setOf("a", "b")))
        assertEquals(setOf("a", "b"), collapsed.layout.problemsCollapsed)
        val ordered = reduce(collapsed, IdeEvent.Panel.TodoFileOrderChanged(listOf("x", "y")))
        assertEquals(listOf("x", "y"), ordered.layout.todoFileOrder)
    }

    @Test
    fun `no-op resize keeps slice value-equal`() {
        val s = AppState()
        val next = reduce(s, IdeEvent.Panel.ResizeSidebar(0.dp))
        assertEquals(s.layout, next.layout)
    }
}
