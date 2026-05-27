package page.runtime

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class CppToolchainInstaller(
    private val osKey: String = LspInstaller.osKey(),
    private val archKey: String = ArchDetect.archKey(),
    private val isWindows: Boolean = LspInstaller.isWindows(),
    private val downloader: (url: String, target: Path, onProgress: (Long, Long) -> Unit) -> Unit = InstallerHttp::download,
    private val tarGzExtractor: (Path, Path, Int) -> Unit = { src, dst, flatten -> ArchiveExtractors.extractTarGz(src, dst, flatten) },
    private val assetsRepo: String = DEFAULT_ASSETS_REPO,
    private val releaseTag: String = DEFAULT_RELEASE_TAG,
    private val defaultVersion: String = DEFAULT_LLVM_VERSION,
    private val versionsFetcher: (String, String, String) -> List<String> = { owner, repo, tag ->
        GitHubReleases.listAssetNames(owner, repo, tag)
    },
) : LspInstaller {

    override val languageId: String = "cpp-toolchain"
    override val displayName: String = "LLVM/Clang Toolchain"
    override val precheck: LspInstaller.Precheck = LspInstaller.Precheck.Ok
    override val heavyInstall: LspInstaller.HeavyInstallEstimate = LspInstaller.HeavyInstallEstimate(
        sizeEstimate = "~500 MB to 1.5 GB",
        durationEstimate = "~3 to 10 min",
        notes = "PAGE downloads the LLVM/Clang portable toolchain from page-ide-assets. Includes clang, clang++, clangd, lld.",
    )

    override fun isInstalled(): Boolean = executable() != null

    override fun executable(): Path? {
        val ver = currentInstalledVersion() ?: return null
        return clangBinary(ver).takeIf { Files.exists(it) }
    }

    override fun defaultVersion(): String? = defaultVersion
    override fun installedVersion(): String? = currentInstalledVersion()

    override fun installedVersions(): List<String> {
        val base = llvmBase()
        if (!Files.isDirectory(base)) return emptyList()
        return runCatching {
            Files.list(base).use { stream ->
                stream
                    .filter { Files.isDirectory(it) && it.fileName.toString() != "CURRENT" }
                    .map { it.fileName.toString() }
                    .filter { Files.exists(clangBinary(it)) }
                    .toList()
                    .sortedWith(VERSION_DESC)
            }
        }.getOrDefault(emptyList())
    }

    override fun activeVersion(): String? = currentInstalledVersion()

    override fun applyVersion(version: String): Boolean {
        if (!Files.exists(clangBinary(version))) return false
        writePointer(version)
        return true
    }

    override fun availableVersions(): List<String> {
        val bundled = discoverBundleVersions()
        val installed = installedVersions()
        return (bundled + defaultVersion + installed).filter { it.isNotBlank() }.distinct().sortedWith(VERSION_DESC)
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
        return Regex("^page-cpp-llvm-$osKey-$arch-(.+?)\\.tar\\.gz$")
    }

    override fun install(version: String?, onProgress: (LspInstaller.Progress) -> Unit) {
        try {
            val resolved = version?.takeIf { it.isNotBlank() } ?: defaultVersion
            val root = llvmRoot(resolved)

            val clang = clangBinary(resolved)
            if (Files.exists(clang)) {
                writePointer(resolved)
                onProgress(LspInstaller.Progress.Done(clang))
                return
            }
            if (Files.exists(root)) ArchiveExtractors.deleteRecursively(root)
            Files.createDirectories(root)

            val url = downloadUrl(resolved)
            val tmp = Files.createTempFile("page-llvm-", ".tar.gz")
            try {
                onProgress(LspInstaller.Progress.CommandOutput("> GET $url"))
                downloader(url, tmp) { read, total ->
                    onProgress(LspInstaller.Progress.Downloading(read, total))
                }
                onProgress(LspInstaller.Progress.Extracting("Extracting LLVM/Clang $resolved …"))
                tarGzExtractor(tmp, root, 0)
            } catch (t: Throwable) {
                throw IOException("LLVM download failed ($url): ${t.message}", t)
            } finally {
                runCatching { Files.deleteIfExists(tmp) }
            }

            if (!Files.exists(clang)) {
                val listing = runCatching {
                    Files.list(root).use { s -> s.limit(20).map { it.fileName.toString() }.toList().joinToString(", ") }
                }.getOrDefault("(empty)")
                throw IOException("clang binary missing after extraction: $clang\nContents: $listing")
            }
            runCatching { clang.toFile().setExecutable(true, false) }
            writePointer(resolved)
            onProgress(LspInstaller.Progress.Done(clang))
        } catch (t: Throwable) {
            runCatching { ArchiveExtractors.deleteRecursively(llvmRoot(version?.takeIf { it.isNotBlank() } ?: defaultVersion)) }
            onProgress(LspInstaller.Progress.Failed(t))
        }
    }

    internal fun downloadUrl(version: String): String {
        val arch = assetArch()
        return "https://github.com/$assetsRepo/releases/download/$releaseTag/page-cpp-llvm-$osKey-$arch-$version.tar.gz"
    }

    private fun assetArch(): String = when (archKey) {
        "arm64" -> "aarch64"
        "amd64" -> "x86_64"
        else -> archKey
    }

    fun clangBinary(version: String): Path {
        val name = if (isWindows) "clang.exe" else "clang"
        return llvmRoot(version).resolve("bin").resolve(name)
    }

    fun clangdBinary(version: String): Path {
        val name = if (isWindows) "clangd.exe" else "clangd"
        return llvmRoot(version).resolve("bin").resolve(name)
    }

    fun llvmRoot(version: String): Path = llvmBase().resolve(version)

    private fun llvmBase(): Path = LspInstaller.lspHome().resolve("llvm")

    override fun installDir(version: String?): Path {
        val v = version?.takeIf { it.isNotBlank() } ?: defaultVersion
        return llvmRoot(v)
    }

    fun llvmHome(): Path? {
        val ver = currentInstalledVersion() ?: return null
        val root = llvmRoot(ver)
        return if (Files.isDirectory(root)) root else null
    }

    fun currentInstalledVersion(): String? {
        val pointer = llvmBase().resolve("CURRENT")
        return runCatching { Files.readString(pointer).trim().takeIf { it.isNotEmpty() } }.getOrNull()
    }

    private fun writePointer(version: String) {
        val pointer = llvmBase().resolve("CURRENT")
        Files.createDirectories(pointer.parent)
        Files.writeString(pointer, version)
    }

    companion object {
        const val DEFAULT_ASSETS_REPO = "monkshark/page-ide-assets"
        const val DEFAULT_RELEASE_TAG = "llvm-bundle"
        const val DEFAULT_LLVM_VERSION = "20.1.5"

        internal val VERSION_DESC: Comparator<String> = Comparator { a, b ->
            val pa = a.split('.').mapNotNull { it.toIntOrNull() }
            val pb = b.split('.').mapNotNull { it.toIntOrNull() }
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
