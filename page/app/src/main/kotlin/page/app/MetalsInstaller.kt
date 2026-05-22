package page.app

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class MetalsInstaller(
    private val processRunner: ProcessRunner = DefaultProcessRunner,
    private val osKey: String = LspInstaller.osKey(),
    private val archKey: String = ArchDetect.archKey(),
    private val isWindows: Boolean = LspInstaller.isWindows(),
    private val downloader: (url: String, target: Path, onProgress: (Long, Long) -> Unit) -> Unit = InstallerHttp::download,
    private val versionsFetcher: () -> List<String> = {
        LspStaticManifest.fetchReleaseTags("metals")
            ?: GitHubReleases.listReleases("scalameta", "metals").map { it.tagName }
    },
    private val coursierVersion: String = DEFAULT_COURSIER,
    private val defaultMetalsVersion: String = "latest",
) : LspInstaller {

    override val languageId: String = "scala"
    override val displayName: String = "metals"
    override val precheck: LspInstaller.Precheck = LspInstaller.Precheck.Ok

    override fun isInstalled(): Boolean = executable() != null

    override fun executable(): Path? {
        val ver = currentInstalledVersion() ?: return null
        val exe = metalsBinary(ver)
        return exe.takeIf { Files.exists(it) }
    }

    override fun defaultVersion(): String? = defaultMetalsVersion

    override fun installedVersion(): String? = currentInstalledVersion()

    override fun availableVersions(): List<String> = runCatching { versionsFetcher() }.getOrDefault(emptyList())

    override fun install(version: String?, onProgress: (LspInstaller.Progress) -> Unit) {
        try {
            val resolved = version ?: defaultMetalsVersion
            val root = installRoot(resolved)
            Files.createDirectories(root)
            val cs = csBinary(resolved)

            val url = coursierUrl()
            val tmp = Files.createTempFile("page-cs-", if (isWindows) ".zip" else ".gz")
            try {
                downloader(url, tmp) { read, total ->
                    onProgress(LspInstaller.Progress.Downloading(read, total))
                }
                onProgress(LspInstaller.Progress.Extracting("Extracting Coursier…"))
                if (isWindows) {
                    extractCsZipToRoot(tmp, root)
                } else {
                    Files.createDirectories(cs.parent)
                    ArchiveExtractors.extractGzBinary(tmp, root.resolve("cs-staging"), if (isWindows) "cs.exe" else "cs")
                    val staged = root.resolve("cs-staging").resolve(if (isWindows) "cs.exe" else "cs")
                    Files.move(staged, cs, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                    ArchiveExtractors.deleteRecursively(root.resolve("cs-staging"))
                }
            } finally {
                runCatching { Files.deleteIfExists(tmp) }
            }
            if (!Files.exists(cs)) throw IOException("Coursier 다운로드 후 바이너리 누락: $cs")
            runCatching { cs.toFile().setExecutable(true, false) }

            val binDir = root.resolve("bin")
            Files.createDirectories(binDir)
            val metalsSpec = if (resolved == "latest" || resolved.isBlank()) "metals" else "metals:$resolved"
            val installCmd = listOf(cs.toString(), "install", "--install-dir", binDir.toString(), metalsSpec)
            val env = mapOf(
                "COURSIER_CACHE" to root.resolve("cache").toString(),
                "COURSIER_HOME" to root.resolve("home").toString(),
            )
            onProgress(LspInstaller.Progress.CommandOutput("> cs install $metalsSpec"))
            val exit = processRunner.runStreaming(installCmd, env) { line ->
                onProgress(LspInstaller.Progress.CommandOutput(line))
            }
            if (exit != 0) throw IOException("cs install metals 종료 코드 $exit")

            val metals = metalsBinary(resolved)
            if (!Files.exists(metals)) throw IOException("metals 설치 후 launcher 누락: $metals")
            runCatching { metals.toFile().setExecutable(true, false) }

            writePointer(resolved)
            onProgress(LspInstaller.Progress.Done(metals))
        } catch (t: Throwable) {
            onProgress(LspInstaller.Progress.Failed(t))
        }
    }

    private fun extractCsZipToRoot(zip: Path, root: Path) {
        val staging = root.resolve("cs-zip-staging")
        ArchiveExtractors.extractZip(zip, staging, flatten = 0)
        val candidate = listOf(staging.resolve("cs-x86_64-pc-win32.exe"), staging.resolve("cs.exe"))
            .firstOrNull { Files.exists(it) }
            ?: Files.walk(staging).use { stream ->
                stream.filter { it.fileName.toString().lowercase().endsWith(".exe") }.findFirst().orElse(null)
            }
            ?: throw IOException("Coursier zip 안에서 .exe 를 찾지 못함")
        Files.move(candidate, root.resolve("cs.exe"), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        ArchiveExtractors.deleteRecursively(staging)
    }

    internal fun coursierUrl(): String {
        val (osTag, archTag) = when (osKey) {
            "macos" -> "apple-darwin" to (if (archKey == "arm64") "aarch64" else "x86_64")
            "windows" -> "pc-win32" to "x86_64"
            else -> "pc-linux" to (if (archKey == "arm64") "aarch64" else "x86_64")
        }
        val ext = if (isWindows) "zip" else "gz"
        return "https://github.com/coursier/coursier/releases/download/v$coursierVersion/cs-$archTag-$osTag.$ext"
    }

    fun csBinary(metalsVersion: String): Path =
        installRoot(metalsVersion).resolve(if (isWindows) "cs.exe" else "cs")

    fun metalsBinary(metalsVersion: String): Path =
        installRoot(metalsVersion).resolve("bin").resolve(if (isWindows) "metals.bat" else "metals")

    fun installRoot(metalsVersion: String): Path = metalsBase().resolve(sanitize(metalsVersion))

    private fun metalsBase(): Path = LspInstaller.lspHome().resolve("metals")

    fun currentInstalledVersion(): String? {
        val pointer = metalsBase().resolve("CURRENT")
        return runCatching { Files.readString(pointer).trim().takeIf { it.isNotEmpty() } }.getOrNull()
    }

    private fun writePointer(metalsVersion: String) {
        val pointer = metalsBase().resolve("CURRENT")
        Files.createDirectories(pointer.parent)
        Files.writeString(pointer, metalsVersion)
    }

    private fun sanitize(version: String): String = version.replace(Regex("[\\\\/:*?\"<>|]"), "_")

    companion object {
        const val DEFAULT_COURSIER: String = "2.1.24"
    }
}
