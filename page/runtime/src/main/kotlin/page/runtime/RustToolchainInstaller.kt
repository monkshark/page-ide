package page.runtime

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class RustToolchainInstaller(
    private val osKey: String = LspInstaller.osKey(),
    private val archKey: String = ArchDetect.archKey(),
    private val isWindows: Boolean = LspInstaller.isWindows(),
    private val downloader: (url: String, target: Path, onProgress: (Long, Long) -> Unit) -> Unit = InstallerHttp::download,
    private val tarGzExtractor: (Path, Path, Int) -> Unit = { src, dst, flatten -> ArchiveExtractors.extractTarGz(src, dst, flatten) },
    private val assetsRepo: String = DEFAULT_ASSETS_REPO,
    private val releaseTag: String = DEFAULT_RELEASE_TAG,
    private val defaultRustVersion: String = DEFAULT_RUST_VERSION,
    private val versionsFetcher: (String, String, String) -> List<String> = { owner, repo, tag ->
        GitHubReleases.listAssetNames(owner, repo, tag)
    },
) : LspInstaller {

    override val languageId: String = "rust-runtime"
    override val displayName: String = "Rust Toolchain"
    override val precheck: LspInstaller.Precheck = LspInstaller.Precheck.Ok
    override val heavyInstall: LspInstaller.HeavyInstallEstimate = LspInstaller.HeavyInstallEstimate(
        sizeEstimate = "~300 MB to 500 MB",
        durationEstimate = "~2 to 5 min",
        notes = "PAGE downloads the Rust standalone toolchain from static.rust-lang.org.",
    )

    override fun isInstalled(): Boolean = executable() != null

    override fun executable(): Path? {
        val ver = currentInstalledVersion() ?: return null
        return cargoBinary(ver).takeIf { Files.exists(it) }
    }

    fun rustcExecutable(): Path? {
        val ver = currentInstalledVersion() ?: return null
        return rustcBinary(ver).takeIf { Files.exists(it) }
    }

    override fun defaultVersion(): String = defaultRustVersion
    override fun installedVersion(): String? = currentInstalledVersion()

    override fun installedVersions(): List<String> {
        val base = rustBase()
        if (!Files.isDirectory(base)) return emptyList()
        return runCatching {
            Files.list(base).use { stream ->
                stream
                    .filter { Files.isDirectory(it) && it.fileName.toString() != "CURRENT" }
                    .map { it.fileName.toString() }
                    .filter { Files.exists(cargoBinary(it)) }
                    .toList()
                    .sortedWith(VERSION_DESC)
            }
        }.getOrDefault(emptyList())
    }

    override fun activeVersion(): String? = currentInstalledVersion()

    override fun applyVersion(version: String): Boolean {
        if (!Files.exists(cargoBinary(version))) return false
        writePointer(version)
        return true
    }

    override fun availableVersions(): List<String> {
        val bundled = discoverBundleVersions()
        val installed = installedVersions()
        return (bundled + defaultRustVersion + installed)
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
        val os = osKey
        val arch = assetArch()
        return Regex("^page-rust-toolchain-$os-$arch-(.+?)\\.tar\\.gz$")
    }

    override fun versionGroups(versions: List<String>): List<LspInstaller.VersionGroup>? = null

    override fun install(version: String?, onProgress: (LspInstaller.Progress) -> Unit) {
        try {
            val resolved = version?.takeIf { it.isNotBlank() } ?: defaultRustVersion
            val root = rustRoot(resolved)

            val cargo = cargoBinary(resolved)
            if (Files.exists(cargo)) {
                writePointer(resolved)
                onProgress(LspInstaller.Progress.Done(cargo))
                return
            }
            if (Files.exists(root)) ArchiveExtractors.deleteRecursively(root)
            Files.createDirectories(root)

            val url = downloadUrl(resolved)
            val tmp = Files.createTempFile("page-rust-", ".tar.gz")
            try {
                onProgress(LspInstaller.Progress.CommandOutput("> GET $url"))
                downloader(url, tmp) { read, total ->
                    onProgress(LspInstaller.Progress.Downloading(read, total))
                }
                onProgress(LspInstaller.Progress.Extracting("Extracting Rust $resolved …"))
                tarGzExtractor(tmp, root, 1)
            } catch (t: Throwable) {
                throw IOException("Rust toolchain download failed ($url): ${t.message}", t)
            } finally {
                runCatching { Files.deleteIfExists(tmp) }
            }

            onProgress(LspInstaller.Progress.Extracting("Reorganizing toolchain layout …"))
            reorganize(root)

            if (!Files.exists(cargo)) {
                val listing = runCatching {
                    Files.walk(root).use { s ->
                        s.limit(30).map { root.relativize(it).toString() }.toList().joinToString(", ")
                    }
                }.getOrDefault("(empty)")
                throw IOException("cargo binary missing after extraction: $cargo\nExtracted contents: $listing")
            }
            runCatching { cargo.toFile().setExecutable(true, false) }
            runCatching { rustcBinary(resolved).toFile().setExecutable(true, false) }
            writePointer(resolved)
            onProgress(LspInstaller.Progress.Done(cargo))
        } catch (t: Throwable) {
            runCatching { ArchiveExtractors.deleteRecursively(rustRoot(version?.takeIf { it.isNotBlank() } ?: defaultRustVersion)) }
            onProgress(LspInstaller.Progress.Failed(t))
        }
    }

    internal fun downloadUrl(version: String): String {
        val arch = assetArch()
        return "https://github.com/$assetsRepo/releases/download/$releaseTag/page-rust-toolchain-$osKey-$arch-$version.tar.gz"
    }

    private fun assetArch(): String = when (archKey) {
        "arm64" -> "aarch64"
        "amd64" -> "x86_64"
        else -> archKey
    }

    private fun reorganize(root: Path) {
        val binDir = root.resolve("bin")
        Files.createDirectories(binDir)

        copyComponentBinaries(root.resolve("rustc").resolve("bin"), binDir)
        copyComponentBinaries(root.resolve("cargo").resolve("bin"), binDir)

        val rustcLib = root.resolve("rustc").resolve("lib")
        if (Files.isDirectory(rustcLib)) {
            copyTree(rustcLib, root.resolve("lib"))
        }

        Files.list(root).use { stream ->
            stream.filter { Files.isDirectory(it) && it.fileName.toString().startsWith("rust-std-") }
                .findFirst().ifPresent { stdDir ->
                    val stdLib = stdDir.resolve("lib")
                    if (Files.isDirectory(stdLib)) copyTree(stdLib, root.resolve("lib"))
                }
        }
    }

    private fun copyComponentBinaries(src: Path, dest: Path) {
        if (!Files.isDirectory(src)) return
        Files.list(src).use { stream ->
            stream.forEach { file ->
                val target = dest.resolve(file.fileName)
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING)
                runCatching { target.toFile().setExecutable(true, false) }
            }
        }
    }

    private fun copyTree(src: Path, dest: Path) {
        if (!Files.isDirectory(src)) return
        Files.walk(src).use { stream ->
            stream.forEach { source ->
                val relative = src.relativize(source)
                val target = dest.resolve(relative)
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target)
                } else {
                    Files.createDirectories(target.parent)
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }

    fun cargoBinary(version: String): Path {
        val name = if (isWindows) "cargo.exe" else "cargo"
        return rustRoot(version).resolve("bin").resolve(name)
    }

    fun rustcBinary(version: String): Path {
        val name = if (isWindows) "rustc.exe" else "rustc"
        return rustRoot(version).resolve("bin").resolve(name)
    }

    fun rustRoot(version: String): Path = rustBase().resolve(version)

    private fun rustBase(): Path = LspInstaller.lspHome().resolve("rust-runtime")

    override fun installDir(version: String?): Path {
        val v = version?.takeIf { it.isNotBlank() } ?: defaultRustVersion
        return rustRoot(v)
    }

    fun currentInstalledVersion(): String? {
        val pointer = rustBase().resolve("CURRENT")
        return runCatching { Files.readString(pointer).trim().takeIf { it.isNotEmpty() } }.getOrNull()
    }

    private fun writePointer(version: String) {
        val pointer = rustBase().resolve("CURRENT")
        Files.createDirectories(pointer.parent)
        Files.writeString(pointer, version)
    }

    fun rustHome(): Path? {
        val ver = currentInstalledVersion() ?: return null
        val root = rustRoot(ver)
        return if (Files.isDirectory(root)) root else null
    }

    companion object {
        const val DEFAULT_ASSETS_REPO = "monkshark/page-ide-assets"
        const val DEFAULT_RELEASE_TAG = "rust-toolchain-bundle"
        const val DEFAULT_RUST_VERSION = "1.95.0"

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
