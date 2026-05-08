package page.lsp

import java.io.InputStream
import java.io.OutputStream

interface LspTransport : AutoCloseable {
    val input: InputStream
    val output: OutputStream
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

class ProcessTransport(private val process: Process) : LspTransport {
    override val input: InputStream = process.inputStream
    override val output: OutputStream = process.outputStream
    val errorStream: InputStream = process.errorStream

    override fun close() {
        try { input.close() } catch (_: Throwable) {}
        try { output.close() } catch (_: Throwable) {}
        try { errorStream.close() } catch (_: Throwable) {}
        if (process.isAlive) {
            process.destroy()
            if (!process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly()
            }
        }
    }
}
