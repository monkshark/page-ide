package page.app

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

class FsAutocompleteInstaller(
    private val processRunner: ProcessRunner = DefaultProcessRunner,
    private val osKey: String = LspInstaller.osKey(),
    private val isWindows: Boolean = LspInstaller.isWindows(),
    private val downloader: (url: String, target: Path, onProgress: (Long, Long) -> Unit) -> Unit = InstallerHttp::download,
    private val versionsFetcher: () -> List<String> = { FsAutocompleteInstaller.fetchFsAutocompleteVersions() },
    private val sdkChannel: String = "LTS",
    private val defaultFsacVersion: String = "latest",
    private val dotnetExeName: String = if (LspInstaller.isWindows()) "dotnet.exe" else "dotnet",
) : LspInstaller {

    override val languageId: String = "fsharp"
    override val displayName: String = "fsautocomplete"
    override val precheck: LspInstaller.Precheck = LspInstaller.Precheck.Ok

    override fun isInstalled(): Boolean = executable() != null

    override fun executable(): Path? {
        val ver = currentInstalledVersion() ?: return null
        val exe = fsacBinary(ver)
        return exe.takeIf { Files.exists(it) }
    }

    override fun defaultVersion(): String? = defaultFsacVersion

    override fun installedVersion(): String? = currentInstalledVersion()

    override fun availableVersions(): List<String> = runCatching { versionsFetcher() }.getOrDefault(emptyList())

    override fun install(version: String?, onProgress: (LspInstaller.Progress) -> Unit) {
        try {
            val resolved = version ?: defaultFsacVersion
            val root = installRoot(resolved)
            Files.createDirectories(root)
            val sdkDir = sdkDir(resolved)
            Files.createDirectories(sdkDir)

            val scriptUrl = dotnetInstallScriptUrl()
            val scriptName = if (isWindows) "dotnet-install.ps1" else "dotnet-install.sh"
            val scriptPath = root.resolve(scriptName)
            downloader(scriptUrl, scriptPath) { read, total ->
                onProgress(LspInstaller.Progress.Downloading(read, total))
            }
            runCatching { scriptPath.toFile().setExecutable(true, false) }

            onProgress(LspInstaller.Progress.Extracting("Installing .NET SDK ($sdkChannel channel)…"))
            val sdkCmd = sdkInstallCommand(scriptPath, sdkDir)
            onProgress(LspInstaller.Progress.CommandOutput("> ${sdkCmd.joinToString(" ")}"))
            val sdkExit = processRunner.runStreaming(sdkCmd) { line ->
                onProgress(LspInstaller.Progress.CommandOutput(line))
            }
            if (sdkExit != 0) throw IOException("dotnet-install 종료 코드 $sdkExit")

            val dotnetExe = sdkDir.resolve(dotnetExeName)
            if (!Files.exists(dotnetExe)) throw IOException(".NET SDK 설치 후 dotnet 누락: $dotnetExe")
            runCatching { dotnetExe.toFile().setExecutable(true, false) }

            val binDir = root.resolve("bin")
            Files.createDirectories(binDir)
            val toolCmd = buildList {
                add(dotnetExe.toString())
                add("tool")
                add("install")
                add("--tool-path")
                add(binDir.toString())
                if (resolved != "latest" && resolved.isNotBlank()) {
                    add("--version"); add(resolved)
                }
                add("fsautocomplete")
            }
            val env = mapOf(
                "DOTNET_ROOT" to sdkDir.toString(),
                "DOTNET_CLI_TELEMETRY_OPTOUT" to "1",
                "DOTNET_NOLOGO" to "1",
            )
            onProgress(LspInstaller.Progress.CommandOutput("> ${toolCmd.joinToString(" ")}"))
            val toolExit = processRunner.runStreaming(toolCmd, env) { line ->
                onProgress(LspInstaller.Progress.CommandOutput(line))
            }
            if (toolExit != 0) throw IOException("dotnet tool install fsautocomplete 종료 코드 $toolExit")

            val fsac = fsacBinary(resolved)
            if (!Files.exists(fsac)) throw IOException("fsautocomplete 설치 후 바이너리 누락: $fsac")
            runCatching { fsac.toFile().setExecutable(true, false) }

            writePointer(resolved)
            onProgress(LspInstaller.Progress.Done(fsac))
        } catch (t: Throwable) {
            onProgress(LspInstaller.Progress.Failed(t))
        }
    }

    private fun dotnetInstallScriptUrl(): String =
        if (isWindows) "https://dot.net/v1/dotnet-install.ps1"
        else "https://dot.net/v1/dotnet-install.sh"

    private fun sdkInstallCommand(script: Path, sdkDir: Path): List<String> =
        if (isWindows) listOf(
            "powershell",
            "-NoProfile",
            "-ExecutionPolicy", "Bypass",
            "-File", script.toString(),
            "-Channel", sdkChannel,
            "-InstallDir", sdkDir.toString(),
            "-NoPath",
        )
        else listOf(
            "bash", script.toString(),
            "--channel", sdkChannel,
            "--install-dir", sdkDir.toString(),
            "--no-path",
        )

    fun sdkDir(fsacVersion: String): Path = installRoot(fsacVersion).resolve("sdk")

    fun fsacBinary(fsacVersion: String): Path {
        val name = if (isWindows) "fsautocomplete.exe" else "fsautocomplete"
        return installRoot(fsacVersion).resolve("bin").resolve(name)
    }

    fun installRoot(fsacVersion: String): Path = fsacBase().resolve(sanitize(fsacVersion))

    private fun fsacBase(): Path = LspInstaller.lspHome().resolve("fsharp")

    fun currentInstalledVersion(): String? {
        val pointer = fsacBase().resolve("CURRENT")
        return runCatching { Files.readString(pointer).trim().takeIf { it.isNotEmpty() } }.getOrNull()
    }

    private fun writePointer(fsacVersion: String) {
        val pointer = fsacBase().resolve("CURRENT")
        Files.createDirectories(pointer.parent)
        Files.writeString(pointer, fsacVersion)
    }

    private fun sanitize(version: String): String = version.replace(Regex("[\\\\/:*?\"<>|]"), "_")

    companion object {

        const val NUGET_VERSIONS_URL: String = "https://api.nuget.org/v3-flatcontainer/fsautocomplete/index.json"

        private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

        private data class NugetIndex(val versions: List<String> = emptyList())

        fun fetchFsAutocompleteVersions(url: String = NUGET_VERSIONS_URL): List<String> = runCatching {
            parseNugetVersions(fetchBody(url))
        }.getOrDefault(emptyList())

        internal fun parseNugetVersions(body: String): List<String> {
            val raw = gson.fromJson(body, NugetIndex::class.java) ?: return emptyList()
            return raw.versions.filter { it.isNotBlank() }.reversed()
        }

        private fun fetchBody(url: String): String {
            val conn = URI(url).toURL().openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "PAGE-IDE/0.1 FsAutocompleteInstaller")
            try {
                val code = conn.responseCode
                if (code !in 200..299) throw IOException("nuget HTTP $code")
                return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }
    }
}

