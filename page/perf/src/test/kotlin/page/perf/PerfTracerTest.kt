package page.perf

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PerfTracerTest {

    @AfterTest
    fun teardown() {
        PerfRegistry.reset()
    }

    private fun fakeClock(values: LongArray): () -> Long {
        var idx = 0
        return {
            val v = values[idx.coerceAtMost(values.lastIndex)]
            idx++
            v
        }
    }

    @Test
    fun `begin then end records a mark`() {
        val tracer = PerfTracer(StartupKind.COLD, processStartMs = 100, clock = fakeClock(longArrayOf(110, 150)))
        tracer.begin("startup.compose_init")
        tracer.end("startup.compose_init")
        val marks = tracer.snapshot()
        assertEquals(1, marks.size)
        assertEquals("startup.compose_init", marks[0].phase)
        assertEquals(10, marks[0].startMs)
        assertEquals(50, marks[0].endMs)
        assertEquals(40, marks[0].durationMs)
    }

    @Test
    fun `end without begin is a no-op`() {
        val tracer = PerfTracer(StartupKind.COLD, processStartMs = 0, clock = fakeClock(longArrayOf(10)))
        tracer.end("missing")
        assertTrue(tracer.snapshot().isEmpty())
    }

    @Test
    fun `trace block records start and end automatically`() {
        val tracer = PerfTracer(StartupKind.COLD, processStartMs = 0, clock = fakeClock(longArrayOf(5, 25)))
        val result = tracer.trace("workspace.open") { "value" }
        assertEquals("value", result)
        val marks = tracer.snapshot()
        assertEquals(1, marks.size)
        assertEquals(20, marks[0].durationMs)
    }

    @Test
    fun `trace records mark even when block throws`() {
        val tracer = PerfTracer(StartupKind.COLD, processStartMs = 0, clock = fakeClock(longArrayOf(5, 25)))
        try {
            tracer.trace<Unit>("workspace.open") { throw IllegalStateException("boom") }
        } catch (_: IllegalStateException) {
        }
        val marks = tracer.snapshot()
        assertEquals(1, marks.size)
        assertEquals(20, marks[0].durationMs)
    }

    @Test
    fun `snapshot is ordered by start time`() {
        val tracer = PerfTracer(StartupKind.COLD, processStartMs = 0, clock = fakeClock(longArrayOf(0, 5, 1, 9)))
        tracer.begin("first")
        tracer.begin("second")
        tracer.end("first")
        tracer.end("second")
        val marks = tracer.snapshot()
        assertEquals(listOf("first", "second"), marks.map { it.phase })
    }

    @Test
    fun `pending reports open phases that have not ended`() {
        val tracer = PerfTracer(StartupKind.COLD, processStartMs = 0, clock = fakeClock(longArrayOf(0, 5)))
        tracer.begin("first")
        tracer.begin("second")
        tracer.end("first")
        assertEquals(setOf("second"), tracer.pending())
    }

    @Test
    fun `summary on empty tracer mentions no marks`() {
        val tracer = PerfTracer(StartupKind.WARM, processStartMs = 0)
        assertTrue(tracer.summary().contains("(no marks)"))
        assertTrue(tracer.summary().contains("warm"))
    }

    @Test
    fun `summary includes total duration and each phase`() {
        val tracer = PerfTracer(StartupKind.COLD, processStartMs = 0, clock = fakeClock(longArrayOf(0, 10, 12, 30)))
        tracer.begin("alpha")
        tracer.end("alpha")
        tracer.begin("beta")
        tracer.end("beta")
        val s = tracer.summary()
        assertTrue(s.contains("cold"), s)
        assertTrue(s.contains("alpha"), s)
        assertTrue(s.contains("beta"), s)
        assertTrue(s.contains("total 30ms"), s)
    }

    @Test
    fun `registry start replaces previous instance`() {
        assertNull(PerfRegistry.instance)
        val a = PerfRegistry.start(StartupKind.COLD)
        assertNotNull(PerfRegistry.instance)
        val b = PerfRegistry.start(StartupKind.WARM)
        assertEquals(b, PerfRegistry.instance)
        assertEquals(StartupKind.WARM, PerfRegistry.instance!!.kind)
    }
}
