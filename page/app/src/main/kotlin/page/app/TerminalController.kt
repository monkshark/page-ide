package page.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.nio.file.Path

class TerminalController(
    private val workspaceRoot: Path,
    private val scope: CoroutineScope,
) {
    private val buffer = TerminalBuffer()
    private var session: TerminalSession? = null
    private var wantsAlive: Boolean = false
    private val restartTimestamps: ArrayDeque<Long> = ArrayDeque()

    val availableShells: List<ShellOption> = TerminalSession.detectShells()

    var shell: ShellOption by mutableStateOf(availableShells.firstOrNull() ?: TerminalSession.defaultShell())
        private set

    var elevated: Boolean by mutableStateOf(false)
        private set

    var lines: List<TerminalLine> by mutableStateOf(buffer.snapshot)
        private set

    var alive: Boolean by mutableStateOf(false)
        private set

    var lastExitCode: Int? by mutableStateOf(null)
        private set

    fun start(cols: Int = 80, rows: Int = 24) {
        if (session != null) return
        wantsAlive = true
        lastExitCode = null
        launchSession(cols, rows)
    }

    fun restartWith(shellOption: ShellOption, elevatedMode: Boolean) {
        shell = shellOption
        elevated = elevatedMode
        stop()
        buffer.clearScreen()
        lines = buffer.snapshot
        start()
    }

    fun selectShell(shellOption: ShellOption) {
        if (shellOption == shell) return
        restartWith(shellOption, elevated)
    }

    fun toggleElevated(value: Boolean) {
        if (value == elevated) return
        restartWith(shell, value)
    }

    private fun launchSession(cols: Int = 80, rows: Int = 24) {
        try {
            session = TerminalSession.start(
                workingDir = workspaceRoot,
                cols = cols,
                rows = rows,
                scope = scope,
                shell = shell,
                elevated = elevated,
                onOutput = { chunk ->
                    buffer.feed(chunk)
                    lines = buffer.snapshot
                },
                onClosed = { code ->
                    alive = false
                    lastExitCode = code
                    session = null
                    if (wantsAlive) tryAutoRestart()
                },
            )
            alive = true
        } catch (e: Throwable) {
            val hint = if (elevated) {
                "관리자 모드는 gsudo 가 필요합니다. 'winget install gsudo' 후 다시 시도해 주세요.\r\n"
            } else ""
            buffer.feed("${e.javaClass.simpleName}: ${e.message}\r\n$hint")
            lines = buffer.snapshot
            alive = false
            wantsAlive = false
        }
    }

    private fun tryAutoRestart() {
        val now = System.currentTimeMillis()
        while (restartTimestamps.isNotEmpty() && now - restartTimestamps.first() > 10_000) {
            restartTimestamps.removeFirst()
        }
        if (restartTimestamps.size >= 3) {
            wantsAlive = false
            return
        }
        restartTimestamps.addLast(now)
        scope.launch {
            delay(500)
            if (wantsAlive && session == null) launchSession()
        }
    }

    fun submit(text: String) {
        val stripped = text.trimEnd('\n', '\r')
        session?.send(stripped + "\r")
    }

    fun sendRaw(chunk: String) {
        session?.send(chunk)
    }

    fun sendInterrupt() {
        session?.send(Char(0x03).toString())
    }

    fun resize(cols: Int, rows: Int) {
        session?.resize(cols, rows)
    }

    fun stop() {
        wantsAlive = false
        session?.close()
        session = null
        alive = false
    }
}
