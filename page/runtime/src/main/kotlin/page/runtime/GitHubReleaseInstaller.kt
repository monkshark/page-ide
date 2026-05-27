package page.runtime

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class GitHubReleaseInstaller(
    val descriptor: GitHubReleaseDescriptor,
    private val manifestFetcher: (String) -> List<String>? = { slug -> LspStaticManifest.fetchReleaseTags(slug) },
    private val apiFetcher: (String, String) -> List<String> = { owner, repo ->
        GitHubReleases.listReleases(owner, repo).map { it.tagName }
    },
) : LspInstaller {

    override val languageId: String = descriptor.languageId
    override val displayName: String = descriptor.displayName
    override val precheck: LspInstaller.Precheck = LspInstaller.Precheck.Ok

    override fun isInstalled(): Boolean = executable() != null

    override fun executable(): Path? {
        val os = descriptor.osBlock() ?: return null
        val root = installRoot()
        if (!Files.isDirectory(root)) return null
        val exe = root.resolve(os.executableRelative)
        return exe.takeIf { Files.exists(it) }
    }

    override fun defaultVersion(): String? = descriptor.defaultVersion

    override fun installedVersion(): String? = currentInstalledVersion()

    override fun availableVersions(): List<String> {
        val manifest = manifestFetcher(descriptor.languageId)
        if (!manifest.isNullOrEmpty()) return manifest
        return runCatching { apiFetcher(descriptor.owner, descriptor.repo) }.getOrDefault(emptyList())
    }

    override fun install(version: String?, onProgress: (LspInstaller.Progress) -> Unit) {
        try {
            val os = descriptor.osBlock()
                ?: throw IOException("no per-OS descriptor for ${LspInstaller.osKey()}")
            val resolvedVersion = version ?: descriptor.defaultVersion
            ?: GitHubReleases.latestTag(descriptor.owner, descriptor.repo)
            ?: throw IOException("cannot resolve release version for ${descriptor.repo}")
            val url = os.url
                .replace("{version}", resolvedVersion)
                .replace("{tag}", resolvedVersion)
                .replace("{versionNoV}", resolvedVersion.removePrefix("v"))
            val target = installRoot(resolvedVersion)
            val tmp = Files.createTempFile("lsp-${descriptor.languageId}-", suffixFor(os.archiveType))
            try {
                InstallerHttp.download(url, tmp) { read, total ->
                    onProgress(LspInstaller.Progress.Downloading(read, total))
                }
                onProgress(LspInstaller.Progress.Extracting())
                materialize(tmp, target, os)
                val exe = target.resolve(os.executableRelative)
                if (!Files.exists(exe)) throw IOException("installed but executable missing: $exe")
                runCatching { exe.toFile().setExecutable(true, false) }
                writePointer(resolvedVersion)
                onProgress(LspInstaller.Progress.Done(exe))
            } finally {
                runCatching { Files.deleteIfExists(tmp) }
            }
        } catch (t: Throwable) {
            runCatching { ArchiveExtractors.deleteRecursively(installDir(version)) }
            onProgress(LspInstaller.Progress.Failed(t))
        }
    }

    fun installRoot(version: String = currentInstalledVersion() ?: descriptor.defaultVersion ?: "latest"): Path =
        LspInstaller.lspHome().resolve(descriptor.languageId).resolve(version)

    private fun currentInstalledVersion(): String? {
        val pointer = LspInstaller.lspHome().resolve(descriptor.languageId).resolve("CURRENT")
        return runCatching { Files.readString(pointer).trim().takeIf { it.isNotEmpty() } }.getOrNull()
    }

    private fun writePointer(version: String) {
        val pointer = LspInstaller.lspHome().resolve(descriptor.languageId).resolve("CURRENT")
        Files.createDirectories(pointer.parent)
        Files.writeString(pointer, version)
    }

    private fun suffixFor(type: ArchiveType): String = when (type) {
        ArchiveType.ZIP -> ".zip"
        ArchiveType.TAR_GZ -> ".tar.gz"
        ArchiveType.GZ_BINARY -> ".gz"
        ArchiveType.RAW_BINARY -> ""
    }

    private fun materialize(source: Path, target: Path, os: OsAsset) {
        when (os.archiveType) {
            ArchiveType.ZIP -> ArchiveExtractors.extractZip(source, target, os.flatten)
            ArchiveType.TAR_GZ -> ArchiveExtractors.extractTarGz(source, target, os.flatten)
            ArchiveType.GZ_BINARY -> ArchiveExtractors.extractGzBinary(source, target, os.executableRelative)
            ArchiveType.RAW_BINARY -> ArchiveExtractors.placeRawBinary(source, target, os.executableRelative)
        }
    }
}

enum class ArchiveType { ZIP, TAR_GZ, GZ_BINARY, RAW_BINARY }

data class OsAsset(
    val url: String,
    val executableRelative: String,
    val archiveType: ArchiveType,
    val flatten: Int = 0,
)

data class GitHubReleaseDescriptor(
    val languageId: String,
    val displayName: String,
    val owner: String,
    val repo: String,
    val perOs: Map<String, OsAsset>,
    val defaultVersion: String? = null,
) {
    fun osBlock(osKey: String = LspInstaller.osKey()): OsAsset? = perOs[osKey]
}
