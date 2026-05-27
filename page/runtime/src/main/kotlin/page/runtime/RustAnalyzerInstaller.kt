package page.runtime

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class RustAnalyzerInstaller(
    private val osKey: String = LspInstaller.osKey(),
    private val archKey: String = ArchDetect.archKey(),
    private val isWindows: Boolean = LspInstaller.isWindows(),
    private val downloader: (url: String, target: Path, onProgress: (Long, Long) -> Unit) -> Unit = InstallerHttp::download,
    private val assetsRepo: String = DEFAULT_ASSETS_REPO,
    private val releaseTag: String = DEFAULT_RELEASE_TAG,
    private val defaultVersion: String = DEFAULT_VERSION,
    private val versionsFetcher: (String, String, String) -> List<String> = { owner, repo, tag ->
        GitHubReleases.listAssetNames(owner, repo, tag)
    },
) : LspInstaller {

    override val languageId: String = "rust"
    override val displayName: String = "rust-analyzer"
    override val precheck: LspInstaller.Precheck = LspInstaller.Precheck.Ok

    override fun isInstalled(): Boolean = executable() != null

    override fun executable(): Path? {
        val ver = currentInstalledVersion() ?: return null
        return binaryPath(ver).takeIf { Files.exists(it) }
    }

    override fun defaultVersion(): String = defaultVersion
    override fun installedVersion(): String? = currentInstalledVersion()

    override fun installedVersions(): List<String> {
        val base = raBase()
        if (!Files.isDirectory(base)) return emptyList()
        return runCatching {
            Files.list(base).use { stream ->
                stream
                    .filter { Files.isDirectory(it) && it.fileName.toString() != "CURRENT" }
                    .map { it.fileName.toString() }
                    .filter { Files.exists(binaryPath(it)) }
                    .toList()
                    .sortedDescending()
            }
        }.getOrDefault(emptyList())
    }

    override fun activeVersion(): String? = currentInstalledVersion()

    override fun applyVersion(version: String): Boolean {
        if (!Files.exists(binaryPath(version))) return false
        writePointer(version)
        return true
    }

    override fun availableVersions(): List<String> {
        val bundled = discoverBundleVersions()
        val installed = installedVersions()
        return (bundled + defaultVersion + installed)
            .filter { it.isNotBlank() }
            .distinct()
            .sortedDescending()
    }

    private fun discoverBundleVersions(): List<String> {
        val parts = assetsRepo.split('/')
        if (parts.size != 2) return emptyList()
        val pattern = assetNamePattern()
        return runCatching {
            versionsFetcher(parts[0], parts[1], releaseTag)
                .mapNotNull { pattern.find(it)?.groupValues?.get(1) }
        }.getOrDefault(emptyList())
    }

    private fun assetNamePattern(): Regex {
        val arch = assetArch()
        return Regex("^page-rust-analyzer-$osKey-$arch-(.+?)\\.gz$")
    }

    override fun install(version: String?, onProgress: (LspInstaller.Progress) -> Unit) {
        try {
            val resolved = version?.takeIf { it.isNotBlank() } ?: defaultVersion
            val root = raRoot(resolved)

            val bin = binaryPath(resolved)
            if (Files.exists(bin)) {
                writePointer(resolved)
                onProgress(LspInstaller.Progress.Done(bin))
                return
            }
            if (Files.exists(root)) ArchiveExtractors.deleteRecursively(root)
            Files.createDirectories(root)

            val url = downloadUrl(resolved)
            val tmp = Files.createTempFile("page-ra-", ".gz")
            try {
                onProgress(LspInstaller.Progress.CommandOutput("> GET $url"))
                downloader(url, tmp) { read, total ->
                    onProgress(LspInstaller.Progress.Downloading(read, total))
                }
                onProgress(LspInstaller.Progress.Extracting("Extracting rust-analyzer $resolved …"))
                val exeName = if (isWindows) "rust-analyzer.exe" else "rust-analyzer"
                ArchiveExtractors.extractGzBinary(tmp, root, exeName)
            } catch (t: Throwable) {
                throw IOException("rust-analyzer download failed ($url): ${t.message}", t)
            } finally {
                runCatching { Files.deleteIfExists(tmp) }
            }

            if (!Files.exists(bin)) {
                throw IOException("rust-analyzer binary missing after extraction: $bin")
            }
            runCatching { bin.toFile().setExecutable(true, false) }
            writePointer(resolved)
            onProgress(LspInstaller.Progress.Done(bin))
        } catch (t: Throwable) {
            runCatching { ArchiveExtractors.deleteRecursively(raRoot(version?.takeIf { it.isNotBlank() } ?: defaultVersion)) }
            onProgress(LspInstaller.Progress.Failed(t))
        }
    }

    internal fun downloadUrl(version: String): String {
        val arch = assetArch()
        return "https://github.com/$assetsRepo/releases/download/$releaseTag/page-rust-analyzer-$osKey-$arch-$version.gz"
    }

    private fun assetArch(): String = when (archKey) {
        "arm64" -> "aarch64"
        "amd64" -> "x86_64"
        else -> archKey
    }

    fun binaryPath(version: String): Path {
        val name = if (isWindows) "rust-analyzer.exe" else "rust-analyzer"
        return raRoot(version).resolve(name)
    }

    fun raRoot(version: String): Path = raBase().resolve(version)

    private fun raBase(): Path = LspInstaller.lspHome().resolve("rust")

    override fun installDir(version: String?): Path {
        val v = version?.takeIf { it.isNotBlank() } ?: defaultVersion
        return raRoot(v)
    }

    fun currentInstalledVersion(): String? {
        val pointer = raBase().resolve("CURRENT")
        return runCatching { Files.readString(pointer).trim().takeIf { it.isNotEmpty() } }.getOrNull()
    }

    private fun writePointer(version: String) {
        val pointer = raBase().resolve("CURRENT")
        Files.createDirectories(pointer.parent)
        Files.writeString(pointer, version)
    }

    companion object {
        const val DEFAULT_ASSETS_REPO = "monkshark/page-ide-assets"
        const val DEFAULT_RELEASE_TAG = "rust-analyzer-bundle"
        const val DEFAULT_VERSION = "2025-05-26"
    }
}
