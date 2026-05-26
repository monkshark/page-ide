package page.app

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

class JdkInstaller(
    private val osKey: String = LspInstaller.osKey(),
    private val archKey: String = ArchDetect.archKey(),
    private val isWindows: Boolean = LspInstaller.isWindows(),
    private val downloader: (url: String, target: Path, onProgress: (Long, Long) -> Unit) -> Unit = InstallerHttp::download,
    private val tarGzExtractor: (Path, Path, Int) -> Unit = { src, dst, flatten -> ArchiveExtractors.extractTarGz(src, dst, flatten) },
    private val zipExtractor: (Path, Path, Int) -> Unit = { src, dst, flatten -> ArchiveExtractors.extractZip(src, dst, flatten) },
    private val assetsRepo: String = DEFAULT_ASSETS_REPO,
    private val releaseTag: String = DEFAULT_RELEASE_TAG,
    private val defaultJdkVersion: String = DEFAULT_JDK_VERSION,
    private val versionsFetcher: (String, String, String) -> List<String> = { owner, repo, tag ->
        GitHubReleases.listAssetNames(owner, repo, tag)
    },
    private val manifestFetcher: () -> List<String> = { fetchManifestVersions() },
) : LspInstaller {

    override val languageId: String = "jdk"
    override val displayName: String = "Eclipse Temurin JDK"
    override val precheck: LspInstaller.Precheck = LspInstaller.Precheck.Ok
    override val heavyInstall: LspInstaller.HeavyInstallEstimate = LspInstaller.HeavyInstallEstimate(
        sizeEstimate = "~180 MB to 240 MB (Temurin JDK runtime + headers)",
        durationEstimate = "~1 to 3 min",
        notes = "PAGE downloads the Eclipse Temurin JDK from page-ide-assets and extracts it into a project-isolated directory. " +
            "No system JDK is required.",
    )

    override fun isInstalled(): Boolean = executable() != null

    override fun executable(): Path? {
        val ver = currentInstalledVersion() ?: return null
        return javaBinary(ver).takeIf { Files.exists(it) }
    }

    override fun defaultVersion(): String? = defaultJdkVersion

    override fun installedVersion(): String? = currentInstalledVersion()

    override fun installedVersions(): List<String> {
        val base = jdkBase()
        if (!Files.isDirectory(base)) return emptyList()
        return runCatching {
            Files.list(base).use { stream ->
                stream
                    .filter { Files.isDirectory(it) && it.fileName.toString() != "CURRENT" }
                    .map { it.fileName.toString() }
                    .filter { Files.exists(javaBinary(it)) }
                    .toList()
                    .sortedWith(VERSION_DESC)
            }
        }.getOrDefault(emptyList())
    }

    override fun activeVersion(): String? = currentInstalledVersion()

    override fun applyVersion(version: String): Boolean {
        val sanitized = sanitize(version)
        if (!Files.exists(javaBinary(sanitized))) return false
        writePointer(sanitized)
        return true
    }

    override fun availableVersions(): List<String> {
        val bundled = discoverBundleVersions()
        val manifest = runCatching { manifestFetcher() }.getOrDefault(emptyList())
        val installed = installedVersions()
        val combined = (manifest + bundled + sanitize(defaultJdkVersion) + installed).filter { it.isNotBlank() }
        return combined.map(::sanitize).distinct().sortedWith(VERSION_DESC)
    }

    override fun versionGroups(versions: List<String>): List<LspInstaller.VersionGroup> {
        val grouped = versions.groupBy { majorOf(it) }.toSortedMap(compareByDescending { it })
        return grouped.map { (major, patches) ->
            val sorted = patches.sortedWith(VERSION_DESC)
            LspInstaller.VersionGroup(
                label = "JDK $major",
                recommended = sorted.first(),
                versions = sorted,
            )
        }
    }

    private fun majorOf(version: String): Int =
        version.split('.', '-', '_', '+').firstOrNull()?.toIntOrNull() ?: 0

    private fun discoverBundleVersions(): List<String> {
        val (owner, repo) = parseRepo(assetsRepo) ?: return emptyList()
        val pattern = assetNamePattern() ?: return emptyList()
        return runCatching {
            versionsFetcher(owner, repo, releaseTag)
                .mapNotNull { pattern.find(it)?.groupValues?.get(1) }
        }.getOrDefault(emptyList())
    }

    private fun parseRepo(slashSlug: String): Pair<String, String>? {
        val parts = slashSlug.split('/')
        if (parts.size != 2 || parts.any { it.isBlank() }) return null
        return parts[0] to parts[1]
    }

    override fun install(version: String?, onProgress: (LspInstaller.Progress) -> Unit) {
        try {
            val resolved = version?.takeIf { it.isNotBlank() } ?: defaultJdkVersion
            val sanitized = sanitize(resolved)
            val root = jdkRoot(sanitized)

            val java = javaBinary(sanitized)
            if (Files.exists(java)) {
                writePointer(sanitized)
                onProgress(LspInstaller.Progress.Done(java))
                return
            }
            if (Files.exists(root)) ArchiveExtractors.deleteRecursively(root)
            Files.createDirectories(root)

            val url = downloadUrl(resolved)
            val ext = bundleExt()
            val tmp = Files.createTempFile("page-jdk-", ".$ext")
            try {
                onProgress(LspInstaller.Progress.CommandOutput("> GET $url"))
                downloader(url, tmp) { read, total ->
                    onProgress(LspInstaller.Progress.Downloading(read, total))
                }
                onProgress(LspInstaller.Progress.Extracting("Extracting Temurin JDK $resolved …"))
                if (isWindows) zipExtractor(tmp, root, 0)
                else tarGzExtractor(tmp, root, 0)
            } catch (t: Throwable) {
                throw IOException(buildDownloadDiagnostic(url, t), t)
            } finally {
                runCatching { Files.deleteIfExists(tmp) }
            }

            if (!Files.exists(java)) {
                throw IOException(
                    "java binary missing after bundle extraction: $java — page-ide-assets jdk-bundle layout differs from expected " +
                        "(bundle root must contain bin/java${if (isWindows) ".exe" else ""}).",
                )
            }
            runCatching { java.toFile().setExecutable(true, false) }
            writePointer(sanitized)
            onProgress(LspInstaller.Progress.Done(java))
        } catch (t: Throwable) {
            runCatching { ArchiveExtractors.deleteRecursively(jdkRoot(sanitize(version?.takeIf { it.isNotBlank() } ?: defaultJdkVersion))) }
            onProgress(LspInstaller.Progress.Failed(t))
        }
    }

    internal fun downloadUrl(version: String): String {
        val ext = bundleExt()
        val safe = sanitize(version)
        return "https://github.com/$assetsRepo/releases/download/$releaseTag/page-java-temurin-$osKey-${assetArch()}-$safe.$ext"
    }

    private fun bundleExt(): String = if (isWindows) "zip" else "tar.gz"

    private fun assetArch(): String = when (archKey) {
        "arm64" -> "aarch64"
        "amd64" -> "x86_64"
        else -> archKey
    }

    private fun assetNamePattern(): Regex? {
        val ext = bundleExt().replace(".", "\\.")
        val arch = assetArch()
        return Regex("^page-java-temurin-$osKey-$arch-(.+?)\\.$ext$")
    }

    fun javaHome(): Path? {
        val ver = currentInstalledVersion() ?: return null
        val root = jdkRoot(ver)
        val macHome = root.resolve("Contents").resolve("Home")
        if (Files.isDirectory(macHome.resolve("bin"))) return macHome
        if (Files.isDirectory(root.resolve("bin"))) return root
        return null
    }

    private fun buildDownloadDiagnostic(url: String, cause: Throwable): String =
        "Temurin JDK bundle download failed ($url): ${cause.javaClass.simpleName}: ${cause.message}\n" +
            "Recovery steps:\n" +
            "  1. Check network / corporate proxy, then retry install in PAGE\n" +
            "  2. Verify that asset $url exists on the '$releaseTag' release in https://github.com/$assetsRepo\n" +
            "  3. If the OS/arch is not published (e.g. JDK 8 ARM64 macOS/Windows), pick a different JDK major in PAGE"

    fun jdkRoot(sanitizedVersion: String): Path = jdkBase().resolve(sanitizedVersion)

    fun javaBinary(sanitizedVersion: String): Path {
        val bin = jdkRoot(sanitizedVersion).resolve("bin").resolve(if (isWindows) "java.exe" else "java")
        if (Files.exists(bin) || osKey != "macos") return bin
        val macBin = jdkRoot(sanitizedVersion).resolve("Contents").resolve("Home").resolve("bin").resolve("java")
        return if (Files.exists(macBin)) macBin else bin
    }

    private fun jdkBase(): Path = LspInstaller.lspHome().resolve("jdk")

    override fun installDir(version: String?): Path {
        val v = sanitize(version?.takeIf { it.isNotBlank() } ?: defaultJdkVersion)
        return jdkRoot(v)
    }

    fun currentInstalledVersion(): String? {
        val pointer = jdkBase().resolve("CURRENT")
        return runCatching { Files.readString(pointer).trim().takeIf { it.isNotEmpty() } }.getOrNull()
    }

    private fun writePointer(sanitizedVersion: String) {
        val pointer = jdkBase().resolve("CURRENT")
        Files.createDirectories(pointer.parent)
        Files.writeString(pointer, sanitizedVersion)
    }

    companion object {
        const val DEFAULT_ASSETS_REPO = "monkshark/page-ide-assets"
        const val DEFAULT_RELEASE_TAG = "jdk-bundle"
        const val DEFAULT_JDK_VERSION = "21.0.5+11"

        const val MANIFEST_URL = "https://monkshark.github.io/page-ide/jdk/versions.json"

        private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

        internal fun sanitize(version: String): String =
            version.replace(Regex("\\.0\\.LTS$"), "").replace(Regex("\\.LTS$"), "").replace(Regex("-LTS$"), "")
                .replace('+', '-').replace(Regex("[\\\\/:*?\"<>|]"), "_")

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

        private fun versionTokens(s: String): IntArray = s.split('.', '_', '-', '+')
            .mapNotNull { it.toIntOrNull() }
            .toIntArray()
            .let { if (it.isEmpty()) intArrayOf(-1) else it }

        private data class JdkManifest(val versions: List<JdkManifestEntry>? = null)
        private data class JdkManifestEntry(val semver: String? = null)

        internal fun fetchManifestVersions(url: String = MANIFEST_URL): List<String> {
            val conn = URI(url).toURL().openConnection() as HttpURLConnection
            conn.connectTimeout = 5_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "PAGE-IDE/0.1 JdkInstaller")
            try {
                val code = conn.responseCode
                if (code !in 200..299) return emptyList()
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val doc = gson.fromJson(body, JdkManifest::class.java) ?: return emptyList()
                return doc.versions?.mapNotNull { it.semver?.takeIf(String::isNotBlank) } ?: emptyList()
            } finally {
                conn.disconnect()
            }
        }
    }
}
