package page.app

import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import com.pty4j.WinSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

enum class ShellKind(val display: String) {
    POWERSHELL("PowerShell"),
    WINDOWS_POWERSHELL("Windows PowerShell"),
    CMD("Command Prompt"),
    GIT_BASH("Git Bash"),
    WSL("WSL"),
    BASH("Bash"),
    ZSH("Zsh"),
    SH("sh"),
}

data class ShellOption(
    val kind: ShellKind,
    val executable: String,
    val args: List<String>,
)

class TerminalSession internal constructor(
    private val process: PtyProcess,
    private val onOutput: (String) -> Unit,
    private val onClosed: (Int) -> Unit,
    scope: CoroutineScope,
) {
    private val alive = AtomicBoolean(true)
    private val readerJob: Job = scope.launch(Dispatchers.IO) { readLoop() }

    private suspend fun readLoop() {
        val buffer = ByteArray(8 * 1024)
        try {
            while (true) {
                val n = process.inputStream.read(buffer)
                if (n <= 0) break
                val chunk = String(buffer, 0, n, StandardCharsets.UTF_8)
                withContext(Dispatchers.Main) { onOutput(chunk) }
            }
        } catch (_: IOException) {
        } finally {
            val code = runCatching { process.waitFor() }.getOrDefault(-1)
            alive.set(false)
            withContext(Dispatchers.Main) { onClosed(code) }
        }
    }

    fun send(text: String) {
        if (!alive.get()) return
        try {
            val bytes = text.toByteArray(StandardCharsets.UTF_8)
            process.outputStream.write(bytes)
            process.outputStream.flush()
        } catch (_: IOException) {
        }
    }

    fun resize(cols: Int, rows: Int) {
        if (!alive.get()) return
        if (cols <= 0 || rows <= 0) return
        runCatching { process.winSize = WinSize(cols, rows) }
    }

    fun isAlive(): Boolean = alive.get() && process.isAlive

    fun close() {
        if (!alive.compareAndSet(true, false)) return
        runCatching { process.destroy() }
        readerJob.cancel()
    }

    companion object {
        fun start(
            workingDir: Path,
            cols: Int,
            rows: Int,
            scope: CoroutineScope,
            shell: ShellOption,
            elevated: Boolean,
            onOutput: (String) -> Unit,
            onClosed: (Int) -> Unit,
        ): TerminalSession {
            val cmd = buildCommand(shell, elevated)
            val env = HashMap(System.getenv()).apply {
                put("TERM", "xterm-256color")
                put("LANG", get("LANG") ?: "en_US.UTF-8")
                PageRuntimeEnv.applyTo(this)
            }
            val process = PtyProcessBuilder()
                .setCommand(cmd)
                .setDirectory(workingDir.toString())
                .setEnvironment(env)
                .setRedirectErrorStream(true)
                .setInitialColumns(cols.coerceAtLeast(20))
                .setInitialRows(rows.coerceAtLeast(5))
                .setConsole(false)
                .start()
            return TerminalSession(process, onOutput, onClosed, scope)
        }

        internal fun buildCommand(shell: ShellOption, elevated: Boolean): Array<String> {
            val base = arrayOf(shell.executable) + shell.args.toTypedArray()
            if (!elevated) return base
            var gsudo = findGsudo()
            if (gsudo == null) {
                val log = installGsudo()
                gsudo = findGsudo()
                    ?: throw IllegalStateException(
                        "gsudo auto-install failed.\n$log\nInstall manually: winget install gerardog.gsudo\nThen restart PAGE IDE."
                    )
            }
            return arrayOf(gsudo) + base
        }

        private fun findGsudo(): String? {
            findOnPath("gsudo")?.let { return it }
            findOnPath("gsudo.exe")?.let { return it }
            val knownPaths = listOfNotNull(
                System.getenv("ProgramFiles")?.let { "$it\\gsudo\\Current\\gsudo.exe" },
                System.getenv("LOCALAPPDATA")?.let { "$it\\Microsoft\\WinGet\\Links\\gsudo.exe" },
                System.getenv("LOCALAPPDATA")?.let { "$it\\Microsoft\\WinGet\\Packages\\gerardog.gsudo_Microsoft.Winget.Source_8wekyb3d8bbwe\\gsudo.exe" },
            )
            return knownPaths.firstOrNull { java.io.File(it).exists() }
        }

        private fun installGsudo(): String {
            val winget = findOnPath("winget") ?: findOnPath("winget.exe")
                ?: System.getenv("LOCALAPPDATA")?.let { "$it\\Microsoft\\WindowsApps\\winget.exe" }
                ?: return "winget not found"
            return try {
                val p = ProcessBuilder(winget, "install", "--id", "gerardog.gsudo", "--accept-package-agreements", "--accept-source-agreements")
                    .redirectErrorStream(true)
                    .start()
                val output = p.inputStream.bufferedReader().use { it.readText() }
                val exitCode = p.waitFor()
                "winget=$winget exit=$exitCode\n$output\ngsudo search: ${findGsudo() ?: "not found"}"
            } catch (t: Throwable) {
                "winget=$winget error=${t.message}"
            }
        }

        fun detectShells(): List<ShellOption> {
            val os = System.getProperty("os.name").lowercase()
            return when {
                os.contains("win") -> detectWindowsShells()
                os.contains("mac") -> detectUnixShells()
                else -> detectUnixShells()
            }
        }

        fun defaultShell(): ShellOption = detectShells().firstOrNull()
            ?: ShellOption(ShellKind.SH, "/bin/sh", emptyList())

        private fun detectWindowsShells(): List<ShellOption> {
            val list = mutableListOf<ShellOption>()
            val pwsh = listOfNotNull(
                System.getenv("ProgramFiles")?.let { "$it\\PowerShell\\7\\pwsh.exe" },
                System.getenv("LOCALAPPDATA")?.let { "$it\\Microsoft\\PowerShell\\7\\pwsh.exe" },
            ).firstOrNull { File(it).exists() }
            if (pwsh != null) list += ShellOption(ShellKind.POWERSHELL, pwsh, listOf("-NoLogo", "-NoProfile"))
            list += ShellOption(ShellKind.WINDOWS_POWERSHELL, "powershell.exe", listOf("-NoLogo", "-NoProfile"))
            list += ShellOption(ShellKind.CMD, "cmd.exe", listOf("/K"))
            val gitBash = listOfNotNull(
                System.getenv("ProgramFiles")?.let { "$it\\Git\\bin\\bash.exe" },
                System.getenv("ProgramFiles(x86)")?.let { "$it\\Git\\bin\\bash.exe" },
                System.getenv("LOCALAPPDATA")?.let { "$it\\Programs\\Git\\bin\\bash.exe" },
            ).firstOrNull { File(it).exists() }
            if (gitBash != null) list += ShellOption(ShellKind.GIT_BASH, gitBash, listOf("-i", "-l"))
            val wsl = findOnPath("wsl.exe")
            if (wsl != null) list += ShellOption(ShellKind.WSL, wsl, emptyList())
            return list
        }

        private fun detectUnixShells(): List<ShellOption> {
            val list = mutableListOf<ShellOption>()
            val zsh = "/bin/zsh"
            if (File(zsh).exists()) list += ShellOption(ShellKind.ZSH, zsh, listOf("-l"))
            val bash = "/bin/bash"
            if (File(bash).exists()) list += ShellOption(ShellKind.BASH, bash, listOf("-l"))
            val sh = "/bin/sh"
            if (File(sh).exists()) list += ShellOption(ShellKind.SH, sh, emptyList())
            return list
        }

        private fun findOnPath(name: String): String? {
            val pathEnv = System.getenv("PATH") ?: return null
            val pathExt = System.getenv("PATHEXT")?.split(File.pathSeparator) ?: listOf("")
            for (dir in pathEnv.split(File.pathSeparator)) {
                for (ext in pathExt) {
                    val candidate = File(dir, if (name.contains('.')) name else name + ext)
                    if (candidate.exists() && candidate.canExecute()) return candidate.absolutePath
                }
            }
            return null
        }
    }
}
