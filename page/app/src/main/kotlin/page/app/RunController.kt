package page.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

sealed interface RunEvent {
    data class Started(val command: String, val args: List<String>, val workingDir: String?) : RunEvent
    data class Stdout(val text: String) : RunEvent
    data class Stderr(val text: String) : RunEvent
    data class Exited(val code: Int, val durationMs: Long) : RunEvent
    data class Failed(val message: String) : RunEvent
}

class RunController(
    private val scope: CoroutineScope,
    private val onEvent: (RunEvent) -> Unit,
) {
    private var process: Process? = null
    private var stdoutJob: Job? = null
    private var stderrJob: Job? = null
    private var waitJob: Job? = null
    private val alive = AtomicBoolean(false)
    private var startedAt: Long = 0L

    val isRunning: Boolean get() = alive.get()

    fun start(config: RunConfig) {
        if (alive.get()) return
        if (!config.isRunnable()) {
            emit(RunEvent.Failed("Run command is empty"))
            return
        }
        val command = listOf(config.command) + config.args
        val builder = ProcessBuilder(command)
        val cwd = config.workingDir?.takeIf { it.isNotBlank() }?.let { File(it) }
        if (cwd != null && cwd.isDirectory) builder.directory(cwd)
        PageRuntimeEnv.applyTo(builder.environment())
        builder.environment().putAll(config.env)
        val started = try {
            builder.start()
        } catch (e: IOException) {
            emit(RunEvent.Failed(e.message ?: "Failed to start process"))
            return
        } catch (e: SecurityException) {
            emit(RunEvent.Failed(e.message ?: "Permission denied"))
            return
        }
        process = started
        alive.set(true)
        startedAt = System.currentTimeMillis()
        emit(RunEvent.Started(config.command, config.args, config.workingDir))
        stdoutJob = scope.launch(Dispatchers.IO) {
            streamLoop(started.inputStream) { chunk -> emitOnMain(RunEvent.Stdout(chunk)) }
        }
        stderrJob = scope.launch(Dispatchers.IO) {
            streamLoop(started.errorStream) { chunk -> emitOnMain(RunEvent.Stderr(chunk)) }
        }
        waitJob = scope.launch(Dispatchers.IO) {
            val code = runCatching { started.waitFor() }.getOrElse { -1 }
            stdoutJob?.join()
            stderrJob?.join()
            val duration = System.currentTimeMillis() - startedAt
            alive.set(false)
            process = null
            withContext(Dispatchers.Main) { onEvent(RunEvent.Exited(code, duration)) }
        }
    }

    fun stop() {
        val p = process ?: return
        runCatching { p.descendants().forEach { it.destroyForcibly() } }
        runCatching { p.destroyForcibly() }
    }

    private suspend fun streamLoop(stream: java.io.InputStream, onChunk: suspend (String) -> Unit) {
        val buf = ByteArray(8 * 1024)
        try {
            while (true) {
                val n = stream.read(buf)
                if (n <= 0) break
                onChunk(String(buf, 0, n, StandardCharsets.UTF_8))
            }
        } catch (_: IOException) {
        }
    }

    private suspend fun emitOnMain(event: RunEvent) {
        withContext(Dispatchers.Main) { onEvent(event) }
    }

    private fun emit(event: RunEvent) {
        onEvent(event)
    }
}
