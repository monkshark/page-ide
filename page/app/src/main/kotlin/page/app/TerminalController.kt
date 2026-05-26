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
    private val grid = TerminalGrid(cols = 120, rows = 40)
    private val parser = AnsiParser()
    private var session: TerminalSession? = null
    private var wantsAlive: Boolean = false
    private val restartTimestamps: ArrayDeque<Long> = ArrayDeque()

    val availableShells: List<ShellOption> = TerminalSession.detectShells()

    var shell: ShellOption by mutableStateOf(availableShells.firstOrNull() ?: TerminalSession.defaultShell())
        private set

    var elevated: Boolean by mutableStateOf(false)
        private set

    var lines: List<TerminalLine> by mutableStateOf(grid.snapshot())
        private set

    var alive: Boolean by mutableStateOf(false)
        private set

    var lastExitCode: Int? by mutableStateOf(null)
        private set

    var cursorRow: Int by mutableStateOf(0)
        private set

    var cursorCol: Int by mutableStateOf(0)
        private set

    var cursorVisible: Boolean by mutableStateOf(true)
        private set

    var gridRows: Int by mutableStateOf(grid.rows)
        private set

    var gridCols: Int by mutableStateOf(grid.cols)
        private set

    var scrollbackSize: Int by mutableStateOf(0)
        private set

    fun start(cols: Int = 120, rows: Int = 40) {
        if (session != null) return
        wantsAlive = true
        lastExitCode = null
        launchSession(cols, rows)
    }

    fun restartWith(shellOption: ShellOption, elevatedMode: Boolean) {
        shell = shellOption
        elevated = elevatedMode
        stop()
        grid.eraseInDisplay(2)
        syncState()
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

    private fun launchSession(cols: Int = 120, rows: Int = 40) {
        try {
            session = TerminalSession.start(
                workingDir = workspaceRoot,
                cols = cols,
                rows = rows,
                scope = scope,
                shell = shell,
                elevated = elevated,
                onOutput = { chunk ->
                    parser.parse(chunk, grid)
                    syncState()
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
            val msg = "${e.javaClass.simpleName}: ${e.message}\r\n"
            val hint = if (elevated) {
                "Admin mode requires gsudo. Install via 'winget install gsudo' and try again.\r\n"
            } else ""
            parser.parse(msg + hint, grid)
            syncState()
            alive = false
            wantsAlive = false
        }
    }

    private fun syncState() {
        lines = grid.snapshot()
        cursorRow = grid.cursorRow
        cursorCol = grid.cursorCol
        cursorVisible = grid.cursorVisible
        scrollbackSize = grid.scrollback.size
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
        if (cols <= 0 || rows <= 0) return
        grid.resize(cols, rows)
        session?.resize(cols, rows)
        syncState()
    }

    fun stop() {
        wantsAlive = false
        session?.close()
        session = null
        alive = false
    }
}
