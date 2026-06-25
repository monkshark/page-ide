package page.perf

import java.awt.EventQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object UiFreezeWatchdog {

    private val started = AtomicBoolean(false)

    fun start(thresholdMs: Long = 3_000L) {
        if (!started.compareAndSet(false, true)) return
        val thread = Thread {
            while (true) {
                val latch = CountDownLatch(1)
                EventQueue.invokeLater { latch.countDown() }
                var blockedMs = 0L
                while (!latch.await(thresholdMs, TimeUnit.MILLISECONDS)) {
                    blockedMs += thresholdMs
                    println("[watchdog] UI thread blocked ≥ ${blockedMs}ms")
                    dumpUiThreads()
                }
                if (blockedMs > 0) println("[watchdog] UI thread responsive again (blocked ~${blockedMs}ms)")
                runCatching { Thread.sleep(1_000L) }
            }
        }
        thread.isDaemon = true
        thread.name = "page-ui-watchdog"
        thread.priority = Thread.MAX_PRIORITY
        thread.start()
    }

    private fun dumpUiThreads() {
        val traces = Thread.getAllStackTraces()
        val ui = traces.entries.filter { (t, _) ->
            t.name.startsWith("AWT-EventQueue") || t.name.startsWith("AWT-EventThread")
        }
        val targets = ui.ifEmpty { traces.entries.filter { it.key.name == "main" } }
        for ((t, stack) in targets) {
            println("[watchdog] ${t.name} state=${t.state}")
            stack.take(40).forEach { println("[watchdog]   at $it") }
        }
    }
}
