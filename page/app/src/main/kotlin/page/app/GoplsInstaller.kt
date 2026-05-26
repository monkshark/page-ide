package page.app

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class GoplsInstaller(
    private val processRunner: ProcessRunner = DefaultProcessRunner,
    private val osKey: String = LspInstaller.osKey(),
    private val archKey: String = ArchDetect.archKey(),
    private val isWindows: Boolean = LspInstaller.isWindows(),
    private val versionsFetcher: () -> List<String> = { GoDlManifest.fetchVersions() },
    private val downloader: (url: String, target: Path, onProgress: (Long, Long) -> Unit) -> Unit = InstallerHttp::download,
    private val defaultGoVersion: String = "1.22.5",
    private val goplsSpec: String = "golang.org/x/tools/gopls@latest",
) : LspInstaller {

    override val languageId: String = "go"
    override val displayName: String = "gopls"
    override val precheck: LspInstaller.Precheck = LspInstaller.Precheck.Ok

    override fun isInstalled(): Boolean = executable() != null

    override fun executable(): Path? {
        val ver = currentInstalledVersion() ?: return null
        val exe = goplsBinary(ver)
        return exe.takeIf { Files.exists(it) }
    }

    override fun defaultVersion(): String? = defaultGoVersion

    override fun installedVersion(): String? = currentInstalledVersion()

    override fun availableVersions(): List<String> = runCatching { versionsFetcher() }.getOrDefault(emptyList())

    override fun install(version: String?, onProgress: (LspInstaller.Progress) -> Unit) {
        try {
            val resolved = (version ?: defaultGoVersion).removePrefix("go")
            val versionTag = "go$resolved"
            val url = downloadUrl(versionTag)
            val target = sdkRoot(versionTag)
            Files.createDirectories(target)
            if (isWindows) {
                requestDefenderExclusion(target, onProgress)
                Thread.sleep(3000)
            }
            val tmp = Files.createTempFile("page-go-", if (isWindows) ".zip" else ".tar.gz")
            try {
                downloader(url, tmp) { read, total ->
                    onProgress(LspInstaller.Progress.Downloading(read, total))
                }
                onProgress(LspInstaller.Progress.Extracting("Extracting Go SDK…"))
                if (isWindows) ArchiveExtractors.extractZip(tmp, target, flatten = 1)
                else ArchiveExtractors.extractTarGz(tmp, target, flatten = 1)
            } finally {
                runCatching { Files.deleteIfExists(tmp) }
            }

            val goBin = goBinary(versionTag)
            if (!Files.exists(goBin)) throw IOException("Go SDK 다운로드 후 바이너리 누락: $goBin")
            runCatching { goBin.toFile().setExecutable(true, false) }

            val gopath = gopathFor(versionTag)
            Files.createDirectories(gopath)

            val env = mapOf(
                "GOPATH" to gopath.toString(),
                "GOROOT" to target.toString(),
                "GOTOOLCHAIN" to "local",
                "GO111MODULE" to "on",
            )
            onProgress(LspInstaller.Progress.CommandOutput("> go install $goplsSpec"))
            val exit = processRunner.runStreaming(listOf(goBin.toString(), "install", goplsSpec), env) { line ->
                onProgress(LspInstaller.Progress.CommandOutput(line))
            }
            if (exit != 0) throw IOException("go install gopls 종료 코드 $exit")

            val gopls = goplsBinary(versionTag)
            if (!Files.exists(gopls)) throw IOException("gopls 설치 후 바이너리 누락: $gopls")
            runCatching { gopls.toFile().setExecutable(true, false) }

            writePointer(versionTag)
            onProgress(LspInstaller.Progress.Done(gopls))
        } catch (t: Throwable) {
            val resolved = (version ?: defaultGoVersion).removePrefix("go")
            runCatching { ArchiveExtractors.deleteRecursively(sdkRoot("go$resolved")) }
            onProgress(LspInstaller.Progress.Failed(t))
        }
    }

    internal fun downloadUrl(versionTag: String): String {
        val osDl = when (osKey) {
            "macos" -> "darwin"
            "windows" -> "windows"
            else -> "linux"
        }
        val ext = if (isWindows) "zip" else "tar.gz"
        return "https://go.dev/dl/$versionTag.$osDl-$archKey.$ext"
    }

    fun goBinary(versionTag: String): Path =
        sdkRoot(versionTag).resolve("bin").resolve(if (isWindows) "go.exe" else "go")

    fun goplsBinary(versionTag: String): Path =
        gopathFor(versionTag).resolve("bin").resolve(if (isWindows) "gopls.exe" else "gopls")

    fun sdkRoot(versionTag: String): Path = sdkBase().resolve(versionTag)

    private fun gopathFor(versionTag: String): Path = sdkRoot(versionTag).resolve("gopath")

    private fun sdkBase(): Path = LspInstaller.lspHome().resolve("go-sdk")

    private fun requestDefenderExclusion(target: Path, onProgress: (LspInstaller.Progress) -> Unit) {
        onProgress(LspInstaller.Progress.Extracting("Requesting Windows Defender exclusion (UAC prompt)…"))
        val pathLiteral = target.toString().replace("'", "''")
        val innerCommand = "try { Add-MpPreference -ExclusionPath ''" + pathLiteral +
            "''; exit 0 } catch { exit 2 }"
        val outerCommand = "Start-Process powershell -Verb RunAs -WindowStyle Hidden -Wait " +
            "-ArgumentList '-NoProfile -ExecutionPolicy Bypass -Command \"" + innerCommand + "\"'"
        val cmd = listOf("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", outerCommand)
        val exit = try {
            processRunner.runStreaming(cmd) { line ->
                onProgress(LspInstaller.Progress.CommandOutput(line))
            }
        } catch (t: Throwable) {
            onProgress(LspInstaller.Progress.CommandOutput(
                "[warning] Defender exclusion failed (${t.javaClass.simpleName}) — go install may fail if Defender blocks compile.exe",
            ))
            return
        }
        if (exit != 0) {
            onProgress(LspInstaller.Progress.CommandOutput(
                "[warning] Defender exclusion request failed (exit=$exit) — go install may fail",
            ))
        } else {
            onProgress(LspInstaller.Progress.CommandOutput("[info] Defender exclusion registered"))
        }
    }

    fun currentInstalledVersion(): String? {
        val pointer = sdkBase().resolve("CURRENT")
        return runCatching { Files.readString(pointer).trim().takeIf { it.isNotEmpty() } }.getOrNull()
    }

    private fun writePointer(versionTag: String) {
        val pointer = sdkBase().resolve("CURRENT")
        Files.createDirectories(pointer.parent)
        Files.writeString(pointer, versionTag)
    }
}
