package page.runtime

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class DotnetSdkInstaller(
    private val osKey: String = LspInstaller.osKey(),
    private val archKey: String = ArchDetect.archKey(),
    private val isWindows: Boolean = LspInstaller.isWindows(),
    private val downloader: (url: String, target: Path, onProgress: (Long, Long) -> Unit) -> Unit = InstallerHttp::download,
    private val tarGzExtractor: (Path, Path, Int) -> Unit = { src, dst, flatten -> ArchiveExtractors.extractTarGz(src, dst, flatten) },
    private val zipExtractor: (Path, Path, Int) -> Unit = { src, dst, flatten -> ArchiveExtractors.extractZip(src, dst, flatten) },
    private val assetsRepo: String = DEFAULT_ASSETS_REPO,
    private val releaseTag: String = DEFAULT_RELEASE_TAG,
    private val defaultDotnetVersion: String = DEFAULT_DOTNET_VERSION,
    private val versionsFetcher: (String, String, String) -> List<String> = { owner, repo, tag ->
        GitHubReleases.listAssetNames(owner, repo, tag)
    },
) : LspInstaller {

    override val languageId: String = "dotnet-runtime"
    override val displayName: String = ".NET SDK"
    override val precheck: LspInstaller.Precheck = LspInstaller.Precheck.Ok
    override val heavyInstall: LspInstaller.HeavyInstallEstimate = LspInstaller.HeavyInstallEstimate(
        sizeEstimate = "~200 MB to 400 MB",
        durationEstimate = "~1 to 3 min",
        notes = "PAGE downloads the .NET SDK from page-ide-assets.",
    )

    override fun isInstalled(): Boolean = executable() != null

    override fun executable(): Path? {
        val ver = currentInstalledVersion() ?: return null
        return dotnetBinary(ver).takeIf { Files.exists(it) }
    }

    override fun defaultVersion(): String = defaultDotnetVersion
    override fun installedVersion(): String? = currentInstalledVersion()

    override fun installedVersions(): List<String> {
        val base = dotnetBase()
        if (!Files.isDirectory(base)) return emptyList()
        return runCatching {
            Files.list(base).use { stream ->
                stream
                    .filter { Files.isDirectory(it) && it.fileName.toString() != "CURRENT" }
                    .map { it.fileName.toString() }
                    .filter { Files.exists(dotnetBinary(it)) }
                    .toList()
                    .sortedWith(VERSION_DESC)
            }
        }.getOrDefault(emptyList())
    }

    override fun activeVersion(): String? = currentInstalledVersion()

    override fun applyVersion(version: String): Boolean {
        if (!Files.exists(dotnetBinary(version))) return false
        writePointer(version)
        return true
    }

    override fun availableVersions(): List<String> {
        val bundled = discoverBundleVersions()
        val installed = installedVersions()
        return (bundled + defaultDotnetVersion + installed)
            .filter { it.isNotBlank() }
            .distinct()
            .sortedWith(VERSION_DESC)
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
        val ext = if (isWindows) "zip" else "tar\\.gz"
        return Regex("^page-dotnet-sdk-$osKey-$arch-(.+?)\\.$ext$")
    }

    override fun install(version: String?, onProgress: (LspInstaller.Progress) -> Unit) {
        try {
            val resolved = version?.takeIf { it.isNotBlank() } ?: defaultDotnetVersion
            val root = dotnetRoot(resolved)

            val bin = dotnetBinary(resolved)
            if (Files.exists(bin)) {
                writePointer(resolved)
                onProgress(LspInstaller.Progress.Done(bin))
                return
            }
            if (Files.exists(root)) ArchiveExtractors.deleteRecursively(root)
            Files.createDirectories(root)

            val url = downloadUrl(resolved)
            val ext = if (isWindows) "zip" else "tar.gz"
            val tmp = Files.createTempFile("page-dotnet-", ".$ext")
            try {
                onProgress(LspInstaller.Progress.CommandOutput("> GET $url"))
                downloader(url, tmp) { read, total ->
                    onProgress(LspInstaller.Progress.Downloading(read, total))
                }
                onProgress(LspInstaller.Progress.Extracting("Extracting .NET SDK $resolved …"))
                if (isWindows) zipExtractor(tmp, root, 0)
                else tarGzExtractor(tmp, root, 0)
            } catch (t: Throwable) {
                throw IOException(".NET SDK download failed ($url): ${t.message}", t)
            } finally {
                runCatching { Files.deleteIfExists(tmp) }
            }

            if (!Files.exists(bin)) {
                val listing = runCatching {
                    Files.walk(root).use { s ->
                        s.limit(20).map { root.relativize(it).toString() }.toList().joinToString(", ")
                    }
                }.getOrDefault("(empty)")
                throw IOException("dotnet binary missing after extraction: $bin\nExtracted contents: $listing")
            }
            runCatching { bin.toFile().setExecutable(true, false) }
            writePointer(resolved)
            onProgress(LspInstaller.Progress.Done(bin))
        } catch (t: Throwable) {
            runCatching { ArchiveExtractors.deleteRecursively(dotnetRoot(version?.takeIf { it.isNotBlank() } ?: defaultDotnetVersion)) }
            onProgress(LspInstaller.Progress.Failed(t))
        }
    }

    internal fun downloadUrl(version: String): String {
        val arch = assetArch()
        val ext = if (isWindows) "zip" else "tar.gz"
        return "https://github.com/$assetsRepo/releases/download/$releaseTag/page-dotnet-sdk-$osKey-$arch-$version.$ext"
    }

    private fun assetArch(): String = when (archKey) {
        "arm64" -> "aarch64"
        "amd64" -> "x86_64"
        else -> archKey
    }

    fun dotnetBinary(version: String): Path {
        val name = if (isWindows) "dotnet.exe" else "dotnet"
        return dotnetRoot(version).resolve(name)
    }

    fun dotnetRoot(version: String): Path = dotnetBase().resolve(version)

    private fun dotnetBase(): Path = LspInstaller.lspHome().resolve("dotnet-runtime")

    override fun installDir(version: String?): Path {
        val v = version?.takeIf { it.isNotBlank() } ?: defaultDotnetVersion
        return dotnetRoot(v)
    }

    fun currentInstalledVersion(): String? {
        val pointer = dotnetBase().resolve("CURRENT")
        return runCatching { Files.readString(pointer).trim().takeIf { it.isNotEmpty() } }.getOrNull()
    }

    private fun writePointer(version: String) {
        val pointer = dotnetBase().resolve("CURRENT")
        Files.createDirectories(pointer.parent)
        Files.writeString(pointer, version)
    }

    fun dotnetHome(): Path? {
        val ver = currentInstalledVersion() ?: return null
        val root = dotnetRoot(ver)
        return if (Files.isDirectory(root)) root else null
    }

    companion object {
        const val DEFAULT_ASSETS_REPO = "monkshark/page-ide-assets"
        const val DEFAULT_RELEASE_TAG = "dotnet-bundle"
        const val DEFAULT_DOTNET_VERSION = "8.0.408"

        internal val VERSION_DESC: Comparator<String> = Comparator { a, b ->
            val pa = versionTokens(a)
            val pb = versionTokens(b)
            val len = maxOf(pa.size, pb.size)
            for (i in 0 until len) {
                val va = pa.getOrElse(i) { 0 }
                val vb = pb.getOrElse(i) { 0 }
                if (va != vb) return@Comparator vb.compareTo(va)
            }
            0
        }

        private fun versionTokens(s: String): IntArray = s.split('.', '-', '_')
            .mapNotNull { it.toIntOrNull() }
            .toIntArray()
            .let { if (it.isEmpty()) intArrayOf(-1) else it }
    }
}
