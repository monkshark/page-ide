package page.lsp

import java.io.InputStream
import java.io.OutputStream

interface LspTransport : AutoCloseable {
    val input: InputStream
    val output: OutputStream

    val exit: java.util.concurrent.CompletableFuture<Int>? get() = null
}

class StreamTransport(
    override val input: InputStream,
    override val output: OutputStream,
    private val onClose: () -> Unit = {},
) : LspTransport {
    override fun close() {
        try { input.close() } catch (_: Throwable) {}
        try { output.close() } catch (_: Throwable) {}
        onClose()
    }
}

class ProcessTransport(
    private val process: Process,
    private val onStderrLine: (String) -> Unit = { System.err.println("[lsp] $it") },
) : LspTransport {
    private val processJob = try {
        WindowsProcessJob.attach(process)
    } catch (error: Throwable) {
        runCatching { stopProcess() }
        throw error
    }

    override val input: InputStream = process.inputStream
    override val output: OutputStream = process.outputStream
    val errorStream: InputStream = process.errorStream

    override val exit: java.util.concurrent.CompletableFuture<Int> =
        process.onExit().thenApply {
            processJob?.close()
            it.exitValue()
        }

    private val stderrPump: Thread = Thread({
        try {
            errorStream.bufferedReader().useLines { lines ->
                for (line in lines) onStderrLine(line)
            }
        } catch (_: Throwable) {
        }
    }, "lsp-stderr-pump").apply {
        isDaemon = true
        start()
    }

    override fun close() {
        processJob?.close()
        stopProcess()
        runCatching { stderrPump.interrupt() }
        closeStreams()
    }

    private fun closeStreams() {
        val closer = Thread({
            runCatching { input.close() }
            runCatching { output.close() }
            runCatching { errorStream.close() }
        }, "lsp-stream-close").apply {
            isDaemon = true
            start()
        }
        runCatching { closer.join(STREAM_CLOSE_TIMEOUT_MS) }
    }

    private fun stopProcess() {
        val isWindows = System.getProperty("os.name").orEmpty().lowercase().contains("win")
        val pid = runCatching { process.pid() }.getOrNull()
        if (isWindows && pid != null && process.isAlive) {
            val killed = runCatching {
                val tk = ProcessBuilder("taskkill", "/F", "/T", "/PID", pid.toString())
                    .redirectErrorStream(true)
                    .start()
                tk.inputStream.close()
                tk.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
            }.getOrDefault(false)
            if (!killed) {
                process.destroyForcibly()
            }
            runCatching { process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS) }
        } else {
            val descendants = runCatching { process.descendants().toList() }.getOrDefault(emptyList())
            if (process.isAlive) {
                process.destroy()
                if (!process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                }
            }
            for (ph in descendants) {
                runCatching { if (ph.isAlive) ph.destroy() }
            }
            for (ph in descendants) {
                runCatching { ph.onExit().get(2, java.util.concurrent.TimeUnit.SECONDS) }
            }
        }
    }

    private companion object {
        const val STREAM_CLOSE_TIMEOUT_MS = 2_000L
    }
}
