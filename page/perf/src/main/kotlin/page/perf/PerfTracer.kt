package page.perf

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

enum class StartupKind { COLD, WARM }

data class PerfMark(
    val phase: String,
    val startMs: Long,
    val endMs: Long,
) {
    val durationMs: Long get() = endMs - startMs
}

class PerfTracer internal constructor(
    val kind: StartupKind,
    val processStartMs: Long,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val open = ConcurrentHashMap<String, Long>()
    private val finished = CopyOnWriteArrayList<PerfMark>()

    fun nowSinceStart(): Long = clock() - processStartMs

    fun begin(phase: String) {
        open[phase] = nowSinceStart()
    }

    fun end(phase: String) {
        val start = open.remove(phase) ?: return
        finished.add(PerfMark(phase, start, nowSinceStart()))
    }

    inline fun <T> trace(phase: String, block: () -> T): T {
        begin(phase)
        try {
            return block()
        } finally {
            end(phase)
        }
    }

    fun snapshot(): List<PerfMark> = finished.toList().sortedBy { it.startMs }

    fun pending(): Set<String> = open.keys.toSet()

    fun summary(): String {
        val marks = snapshot()
        if (marks.isEmpty()) return "[perf:${kind.name.lowercase()}] (no marks)"
        val width = marks.maxOf { it.phase.length }
        val lines = marks.joinToString("\n") { m ->
            val pad = " ".repeat(width - m.phase.length)
            "  ${m.phase}$pad  ${"%6d".format(m.startMs)}ms -> ${"%6d".format(m.endMs)}ms  (delta ${"%5d".format(m.durationMs)}ms)"
        }
        val total = marks.maxOf { it.endMs }
        val pending = pending()
        val pendingLine = if (pending.isEmpty()) "" else "\n  (pending: ${pending.sorted().joinToString(", ")})"
        return "[perf:${kind.name.lowercase()}] total ${total}ms\n$lines$pendingLine"
    }
}

object PerfRegistry {
    @Volatile
    private var current: PerfTracer? = null

    val instance: PerfTracer? get() = current

    @Synchronized
    fun start(kind: StartupKind, processStartMs: Long = System.currentTimeMillis()): PerfTracer {
        val tracer = PerfTracer(kind, processStartMs)
        current = tracer
        return tracer
    }

    @Synchronized
    internal fun reset() {
        current = null
    }
}

object StartupPhases {
    const val COMPOSE_INIT = "startup.compose_init"
    const val WINDOW_SHOWN = "startup.window_shown"
    const val FIRST_FRAME = "startup.first_frame"
    const val WORKSPACE_OPEN = "workspace.open"
    const val WORKSPACE_INDEX_BUILT = "workspace.index_built"
    const val WORKSPACE_FIRST_TAB_VISIBLE = "workspace.first_tab_visible"
    const val LSP_SPAWNED = "lsp.spawned"
    const val LSP_FIRST_DIAGNOSTIC = "lsp.first_diagnostic"
}
