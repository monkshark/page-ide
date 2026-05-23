package page.app

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class HaskellHlsInstaller(
    private val processRunner: ProcessRunner = DefaultProcessRunner,
    private val osKey: String = LspInstaller.osKey(),
    private val archKey: String = ArchDetect.archKey(),
    private val isWindows: Boolean = LspInstaller.isWindows(),
    private val downloader: (url: String, target: Path, onProgress: (Long, Long) -> Unit) -> Unit = InstallerHttp::download,
    private val versionsFetcher: () -> List<String> = {
        LspStaticManifest.fetchReleaseTags("haskell") ?: emptyList()
    },
    private val ghcupVersion: String = DEFAULT_GHCUP_VERSION,
    private val defaultHlsVersion: String = "latest",
) : LspInstaller {

    override val languageId: String = "haskell"
    override val displayName: String = "haskell-language-server"
    override val precheck: LspInstaller.Precheck = LspInstaller.Precheck.Ok

    override fun isInstalled(): Boolean = executable() != null

    override fun executable(): Path? {
        val ver = currentInstalledVersion() ?: return null
        val wrapper = wrapperBinary(ver)
        return wrapper.takeIf { Files.exists(it) }
    }

    override fun defaultVersion(): String? = defaultHlsVersion

    override fun installedVersion(): String? = currentInstalledVersion()

    override fun availableVersions(): List<String> = runCatching { versionsFetcher() }.getOrDefault(emptyList())

    override fun install(version: String?, onProgress: (LspInstaller.Progress) -> Unit) {
        try {
            val resolved = version ?: defaultHlsVersion
            val root = installRoot(resolved)
            Files.createDirectories(root)

            val ghcupBin = root.resolve(if (isWindows) "ghcup.exe" else "ghcup")
            if (!Files.exists(ghcupBin)) {
                onProgress(LspInstaller.Progress.Extracting("Downloading ghcup $ghcupVersion…"))
                downloader(ghcupUrl(), ghcupBin) { read, total ->
                    onProgress(LspInstaller.Progress.Downloading(read, total))
                }
                runCatching { ghcupBin.toFile().setExecutable(true, false) }
            }

            val env = buildEnv(root)
            val installArgs = buildList {
                add(ghcupBin.toString())
                add("--no-verbose")
                add("install")
                add("hls")
                if (resolved != "latest" && resolved.isNotBlank()) add(resolved)
                add("--set")
                add("--force")
            }
            onProgress(LspInstaller.Progress.CommandOutput("> ${installArgs.joinToString(" ")}"))
            val exit = processRunner.runStreaming(installArgs, env) { line ->
                onProgress(LspInstaller.Progress.CommandOutput(line))
            }
            if (exit != 0) throw IOException("ghcup install hls 종료 코드 $exit")

            val wrapper = wrapperBinary(resolved)
            if (!Files.exists(wrapper)) throw IOException("haskell-language-server-wrapper 누락: $wrapper")
            runCatching { wrapper.toFile().setExecutable(true, false) }

            writePointer(resolved)
            onProgress(LspInstaller.Progress.Done(wrapper))
        } catch (t: Throwable) {
            onProgress(LspInstaller.Progress.Failed(t))
        }
    }

    internal fun ghcupUrl(): String {
        val arch = when (archKey) {
            "arm64" -> "aarch64"
            "386" -> "i386"
            else -> "x86_64"
        }
        val asset = when (osKey) {
            "macos" -> "$arch-apple-darwin-ghcup-$ghcupVersion"
            "windows" -> "x86_64-mingw64-ghcup-$ghcupVersion.exe"
            else -> "$arch-linux-ghcup-$ghcupVersion"
        }
        return "https://github.com/haskell/ghcup-hs/releases/download/v$ghcupVersion/$asset"
    }

    private fun buildEnv(root: Path): Map<String, String> = mapOf(
        "GHCUP_INSTALL_BASE_PREFIX" to root.toString(),
        "GHCUP_TUI" to "0",
        "BOOTSTRAP_HASKELL_NONINTERACTIVE" to "1",
    )

    fun wrapperBinary(hlsVersion: String): Path {
        val name = if (isWindows) "haskell-language-server-wrapper.exe" else "haskell-language-server-wrapper"
        return installRoot(hlsVersion).resolve(".ghcup").resolve("bin").resolve(name)
    }

    fun installRoot(hlsVersion: String): Path = installBase().resolve(sanitize(hlsVersion))

    private fun installBase(): Path = LspInstaller.lspHome().resolve("haskell")

    fun currentInstalledVersion(): String? {
        val pointer = installBase().resolve("CURRENT")
        return runCatching { Files.readString(pointer).trim().takeIf { it.isNotEmpty() } }.getOrNull()
    }

    private fun writePointer(hlsVersion: String) {
        val pointer = installBase().resolve("CURRENT")
        Files.createDirectories(pointer.parent)
        Files.writeString(pointer, hlsVersion)
    }

    private fun sanitize(version: String): String = version.replace(Regex("[\\\\/:*?\"<>|]"), "_")

    companion object {
        const val DEFAULT_GHCUP_VERSION: String = "0.1.40.0"
    }
}
