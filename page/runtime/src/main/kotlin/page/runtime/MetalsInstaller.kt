package page.runtime

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class MetalsInstaller(
    private val downloader: (url: String, target: Path, onProgress: (Long, Long) -> Unit) -> Unit = InstallerHttp::download,
    private val osKey: String = LspInstaller.osKey(),
    private val archKey: String = ArchDetect.archKey(),
    private val isWindows: Boolean = LspInstaller.isWindows(),
    private val assetsRepo: String = DEFAULT_ASSETS_REPO,
    private val releaseTag: String = DEFAULT_RELEASE_TAG,
    private val staticManifestFetcher: (tag: String) -> List<String>? = { tag ->
        LspStaticManifest.fetchAssetNames("scala-$tag")
    },
    private val assetsFetcher: (owner: String, repo: String, tag: String) -> List<String> = { owner, repo, tag ->
        GitHubReleases.listAssetNames(owner, repo, tag)
    },
    private val defaultVersion: String = "latest",
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

    override fun defaultVersion(): String? = defaultVersion

    override fun installedVersion(): String? = currentInstalledVersion()

    override fun installedVersions(): List<String> {
        val root = LspInstaller.lspHome().resolve("metals")
        if (!Files.isDirectory(root)) return emptyList()
        return runCatching {
            Files.list(root).use { stream ->
                stream
                    .filter { Files.isDirectory(it) && it.fileName.toString() != "CURRENT" }
                    .map { it.fileName.toString() }
                    .filter { Files.exists(metalsBinary(it)) }
                    .toList()
                    .sortedWith(VERSION_DESC)
            }
        }.getOrDefault(emptyList())
    }

    override fun activeVersion(): String? = currentInstalledVersion()

    override fun applyVersion(version: String): Boolean {
        if (!Files.exists(metalsBinary(version))) return false
        writePointer(version)
        return true
    }

    override fun availableVersions(): List<String> {
        val installed = installedVersions()
        val discovered = discoverVersions()
        return (discovered + installed).distinct().sortedWith(VERSION_DESC)
    }

    private fun discoverVersions(): List<String> {
        val pageOs = pageOsKey()
        val pageArch = pageArchKey()

        val staticVersions = runCatching {
            staticManifestFetcher(releaseTag)
                ?.let { versionsFor(it, pageOs, pageArch) }
                ?.takeIf { it.isNotEmpty() }
        }.getOrNull()
        if (staticVersions != null) return staticVersions

        val (owner, repo) = parseRepo(assetsRepo) ?: return emptyList()
        return runCatching {
            versionsFor(assetsFetcher(owner, repo, releaseTag), pageOs, pageArch)
        }.getOrDefault(emptyList())
    }

    private fun versionsFor(assets: List<String>, pageOs: String, pageArch: String): List<String> =
        assets
            .mapNotNull { name ->
                val m = ASSET_NAME_REGEX.find(name) ?: return@mapNotNull null
                if (m.groupValues[1] == pageOs && m.groupValues[2] == pageArch) m.groupValues[3] else null
            }
            .distinct()
            .sortedWith(VERSION_DESC)

    override fun install(version: String?, onProgress: (LspInstaller.Progress) -> Unit) {
        try {
            val requested = version ?: defaultVersion
            val resolved = if (requested == "latest" || requested.isBlank()) {
                discoverVersions().firstOrNull()
                    ?: throw IOException("page-ide-assets 의 metals-bundle 태그에 사용 가능한 Metals 번들이 없습니다.")
            } else requested

            val url = bundleUrl(resolved)
            val target = installRoot(resolved)
            if (Files.isDirectory(target)) {
                ArchiveExtractors.deleteRecursively(target)
            }
            Files.createDirectories(target)

            val ext = if (isWindows) ".zip" else ".tar.gz"
            val tmp = Files.createTempFile("metals-", ext)
            try {
                downloader(url, tmp) { read, total ->
                    onProgress(LspInstaller.Progress.Downloading(read, total))
                }
                onProgress(LspInstaller.Progress.Extracting("Extracting Metals…"))
                if (isWindows) {
                    ArchiveExtractors.extractZip(tmp, target, flatten = 0)
                } else {
                    ArchiveExtractors.extractTarGz(tmp, target, flatten = 0)
                }
            } finally {
                runCatching { Files.deleteIfExists(tmp) }
            }

            writePointer(resolved)
            val exe = metalsBinary(resolved)
            if (!Files.exists(exe)) throw IOException("metals launcher missing at $exe")
            runCatching { exe.toFile().setExecutable(true, false) }
            val javaExe = target.resolve("jre").resolve("bin").resolve(if (isWindows) "java.exe" else "java")
            runCatching { javaExe.toFile().setExecutable(true, false) }
            onProgress(LspInstaller.Progress.Done(exe))
        } catch (t: Throwable) {
            runCatching { ArchiveExtractors.deleteRecursively(installRoot(version?.takeIf { it.isNotBlank() } ?: defaultVersion)) }
            onProgress(LspInstaller.Progress.Failed(t))
        }
    }

    internal fun bundleUrl(version: String): String {
        val pageOs = pageOsKey()
        val pageArch = pageArchKey()
        val ext = if (isWindows) "zip" else "tar.gz"
        return "https://github.com/$assetsRepo/releases/download/$releaseTag/page-scala-metals-$pageOs-$pageArch-$version.$ext"
    }

    fun metalsBinary(metalsVersion: String): Path =
        installRoot(metalsVersion).resolve("bin").resolve(if (isWindows) "metals.bat" else "metals")

    fun installRoot(metalsVersion: String): Path =
        LspInstaller.lspHome().resolve("metals").resolve(sanitize(metalsVersion))

    override fun installDir(version: String?): Path =
        installRoot(version ?: currentInstalledVersion() ?: defaultVersion)

    fun currentInstalledVersion(): String? {
        val pointer = LspInstaller.lspHome().resolve("metals").resolve("CURRENT")
        return runCatching { Files.readString(pointer).trim().takeIf { it.isNotEmpty() } }.getOrNull()
    }

    private fun writePointer(version: String) {
        val pointer = LspInstaller.lspHome().resolve("metals").resolve("CURRENT")
        Files.createDirectories(pointer.parent)
        Files.writeString(pointer, version)
    }

    private fun pageOsKey(): String = when (osKey) {
        "windows" -> "windows"
        "macos" -> "macos"
        else -> "linux"
    }

    private fun pageArchKey(): String = if (archKey == "arm64") "aarch64" else "x86_64"

    private fun parseRepo(slashSlug: String): Pair<String, String>? {
        val parts = slashSlug.split('/')
        if (parts.size != 2 || parts.any { it.isBlank() }) return null
        return parts[0] to parts[1]
    }

    private fun sanitize(version: String): String =
        version.replace(Regex("[\\\\/:*?\"<>|]"), "_")

    companion object {

        const val DEFAULT_ASSETS_REPO: String = "monkshark/page-ide-assets"
        const val DEFAULT_RELEASE_TAG: String = "metals-bundle"

        internal val ASSET_NAME_REGEX =
            Regex("^page-scala-metals-(linux|macos|windows)-(x86_64|aarch64)-(\\d+(?:\\.\\d+){1,3})\\.(?:tar\\.gz|zip)$")

        internal val VERSION_DESC: Comparator<String> = Comparator { a, b ->
            val aParts = a.split('.').map { it.toIntOrNull() ?: 0 }
            val bParts = b.split('.').map { it.toIntOrNull() ?: 0 }
            val n = maxOf(aParts.size, bParts.size)
            for (i in 0 until n) {
                val ai = aParts.getOrNull(i) ?: 0
                val bi = bParts.getOrNull(i) ?: 0
                if (ai != bi) return@Comparator bi - ai
            }
            0
        }
    }
}
