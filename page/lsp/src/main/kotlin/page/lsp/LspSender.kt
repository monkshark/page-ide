package page.lsp

import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal class LspSender(private val name: String = "lsp-sender") {

    private val executor = ThreadPoolExecutor(
        0, 1, 10, TimeUnit.SECONDS, LinkedBlockingQueue(),
    ) { r -> Thread(r, name).apply { isDaemon = true } }

    private fun warnBacklog() {
        val depth = executor.queue.size
        if (depth >= 4) println("[lsp] $name backlog: $depth message(s) queued — server may not be draining stdin")
    }

    fun post(block: () -> Unit) {
        warnBacklog()
        executor.execute {
            runCatching(block).onFailure {
                println("[lsp] $name notify failed: ${it.message}")
            }
        }
    }

    fun <T> request(block: () -> CompletableFuture<T>): CompletableFuture<T> {
        warnBacklog()
        val out = CompletableFuture<T>()
        executor.execute {
            if (out.isDone) return@execute
            val inner = try {
                block()
            } catch (t: Throwable) {
                out.completeExceptionally(t)
                return@execute
            }
            out.whenComplete { _, _ -> if (out.isCancelled) inner.cancel(true) }
            inner.whenComplete { value, err ->
                if (err != null) out.completeExceptionally(err) else out.complete(value)
            }
        }
        return out
    }
}
