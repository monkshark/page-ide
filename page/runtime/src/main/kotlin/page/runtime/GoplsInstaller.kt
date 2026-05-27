package page.runtime

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class GoplsInstaller(
    private val osKey: String = LspInstaller.osKey(),
    private val archKey: String = ArchDetect.archKey(),
    private val isWindows: Boolean = LspInstaller.isWindows(),
    private val downloader: (url: String, target: Path, onProgress: (Long, Long) -> Unit) -> Unit = InstallerHttp::download,
    private val tarGzExtractor: (Path, Path, Int) -> Unit = { src, dst, flatten -> ArchiveExtractors.extractTarGz(src, dst, flatten) },
    private val assetsRepo: String = DEFAULT_ASSETS_REPO,
    private val releaseTag: String = DEFAULT_RELEASE_TAG,
    private val defaultGoplsVersion: String = DEFAULT_GOPLS_VERSION,
    private val versionsFetcher: (String, String, String) -> List<String> = { owner, repo, tag ->
        GitHubReleases.listAssetNames(owner, repo, tag)
    },
) : LspInstaller {

    override val languageId: String = "go"
    override val displayName: String = "gopls"
    override val precheck: LspInstaller.Precheck = LspInstaller.Precheck.Ok

    override fun isInstalled(): Boolean = executable() != null

    override fun executable(): Path? {
        val ver = currentInstalledVersion() ?: return null
        return goplsBinary(ver).takeIf { Files.exists(it) }
    }

    override fun defaultVersion(): String? = defaultGoplsVersion
    override fun installedVersion(): String? = currentInstalledVersion()

    override fun installedVersions(): List<String> {
        val base = goplsBase()
        if (!Files.isDirectory(base)) return emptyList()
        return runCatching {
            Files.list(base).use { stream ->
                stream
                    .filter { Files.isDirectory(it) && it.fileName.toString() != "CURRENT" }
                    .map { it.fileName.toString() }
                    .filter { Files.exists(goplsBinary(it)) }
                    .toList()
                    .sortedWith(VERSION_DESC)
            }
        }.getOrDefault(emptyList())
    }

    override fun activeVersion(): String? = currentInstalledVersion()

    override fun applyVersion(version: String): Boolean {
        if (!Files.exists(goplsBinary(version))) return false
        writePointer(version)
        return true
    }

    override fun availableVersions(): List<String> {
        val bundled = discoverBundleVersions()
        val installed = installedVersions()
        return (bundled + defaultGoplsVersion + installed).filter { it.isNotBlank() }.distinct().sortedWith(VERSION_DESC)
    }

    private fun discoverBundleVersions(): List<String> {
        val parts = assetsRepo.split('/')
        if (parts.size != 2) return emptyList()
        val pattern = assetNamePattern() ?: return emptyList()
        return runCatching {
            versionsFetcher(parts[0], parts[1], releaseTag)
                .mapNotNull { pattern.find(it)?.groupValues?.get(1) }
        }.getOrDefault(emptyList())
    }

    private fun assetNamePattern(): Regex? {
        val arch = assetArch()
        return Regex("^page-go-gopls-$osKey-$arch-(.+?)\\.tar\\.gz$")
    }

    override fun install(version: String?, onProgress: (LspInstaller.Progress) -> Unit) {
        try {
            val resolved = version?.takeIf { it.isNotBlank() } ?: defaultGoplsVersion
            val root = goplsRoot(resolved)

            val gopls = goplsBinary(resolved)
            if (Files.exists(gopls)) {
                writePointer(resolved)
                onProgress(LspInstaller.Progress.Done(gopls))
                return
            }
            if (Files.exists(root)) ArchiveExtractors.deleteRecursively(root)
            Files.createDirectories(root)

            val url = downloadUrl(resolved)
            val tmp = Files.createTempFile("page-gopls-", ".tar.gz")
            try {
                onProgress(LspInstaller.Progress.CommandOutput("> GET $url"))
                downloader(url, tmp) { read, total ->
                    onProgress(LspInstaller.Progress.Downloading(read, total))
                }
                onProgress(LspInstaller.Progress.Extracting("Extracting gopls $resolved …"))
                tarGzExtractor(tmp, root, 0)
            } catch (t: Throwable) {
                throw IOException("gopls download failed ($url): ${t.message}", t)
            } finally {
                runCatching { Files.deleteIfExists(tmp) }
            }

            val binary = goplsBinary(resolved)
            if (!Files.exists(binary)) {
                val listing = runCatching {
                    Files.list(root).use { s -> s.limit(20).map { it.fileName.toString() }.toList().joinToString(", ") }
                }.getOrDefault("(empty)")
                throw IOException("gopls binary missing after extraction: $binary\nContents: $listing")
            }
            runCatching { binary.toFile().setExecutable(true, false) }
            writePointer(resolved)
            onProgress(LspInstaller.Progress.Done(binary))
        } catch (t: Throwable) {
            runCatching { ArchiveExtractors.deleteRecursively(goplsRoot(version?.takeIf { it.isNotBlank() } ?: defaultGoplsVersion)) }
            onProgress(LspInstaller.Progress.Failed(t))
        }
    }

    internal fun downloadUrl(version: String): String {
        val arch = assetArch()
        return "https://github.com/$assetsRepo/releases/download/$releaseTag/page-go-gopls-$osKey-$arch-$version.tar.gz"
    }

    private fun assetArch(): String = when (archKey) {
        "arm64" -> "aarch64"
        "amd64" -> "x86_64"
        else -> archKey
    }

    fun goplsBinary(version: String): Path {
        val name = if (isWindows) "gopls.exe" else "gopls"
        return goplsRoot(version).resolve(name)
    }

    fun goplsRoot(version: String): Path = goplsBase().resolve(version)

    private fun goplsBase(): Path = LspInstaller.lspHome().resolve("gopls")

    override fun installDir(version: String?): Path {
        val v = version?.takeIf { it.isNotBlank() } ?: defaultGoplsVersion
        return goplsRoot(v)
    }

    fun currentInstalledVersion(): String? {
        val pointer = goplsBase().resolve("CURRENT")
        return runCatching { Files.readString(pointer).trim().takeIf { it.isNotEmpty() } }.getOrNull()
    }

    private fun writePointer(version: String) {
        val pointer = goplsBase().resolve("CURRENT")
        Files.createDirectories(pointer.parent)
        Files.writeString(pointer, version)
    }

    companion object {
        const val DEFAULT_ASSETS_REPO = "monkshark/page-ide-assets"
        const val DEFAULT_RELEASE_TAG = "gopls-bundle"
        const val DEFAULT_GOPLS_VERSION = "v0.22.0"

        internal val VERSION_DESC: Comparator<String> = Comparator { a, b ->
            val pa = a.removePrefix("v").split('.').mapNotNull { it.toIntOrNull() }
            val pb = b.removePrefix("v").split('.').mapNotNull { it.toIntOrNull() }
            val len = maxOf(pa.size, pb.size)
            for (i in 0 until len) {
                val va = pa.getOrElse(i) { 0 }
                val vb = pb.getOrElse(i) { 0 }
                if (va != vb) return@Comparator vb.compareTo(va)
            }
            0
        }
    }
}
