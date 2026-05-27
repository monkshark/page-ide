package page.runtime

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

class PythonInstaller(
    private val osKey: String = LspInstaller.osKey(),
    private val archKey: String = ArchDetect.archKey(),
    private val isWindows: Boolean = LspInstaller.isWindows(),
    private val downloader: (url: String, target: Path, onProgress: (Long, Long) -> Unit) -> Unit = InstallerHttp::download,
    private val tarGzExtractor: (Path, Path, Int) -> Unit = { src, dst, flatten -> ArchiveExtractors.extractTarGz(src, dst, flatten) },
    private val defaultPythonVersion: String = DEFAULT_PYTHON_VERSION,
    private val assetsRepo: String = DEFAULT_ASSETS_REPO,
    private val releaseTag: String = DEFAULT_RELEASE_TAG,
    private val versionsFetcher: (String, String, String) -> List<String> = { owner, repo, tag ->
        GitHubReleases.listAssetNames(owner, repo, tag)
    },
    private val manifestFetcher: () -> List<String> = { fetchPythonVersions() },
) : LspInstaller {

    override val languageId: String = "python-runtime"
    override val displayName: String = "Python"
    override val precheck: LspInstaller.Precheck = LspInstaller.Precheck.Ok
    override val heavyInstall: LspInstaller.HeavyInstallEstimate = LspInstaller.HeavyInstallEstimate(
        sizeEstimate = "~25 MB to 100 MB",
        durationEstimate = "~1 to 3 min",
        notes = "PAGE downloads a standalone Python build from page-ide-assets and extracts it into an isolated directory.",
    )

    override fun isInstalled(): Boolean = executable() != null

    override fun executable(): Path? {
        val ver = currentInstalledVersion() ?: return null
        return pythonBinary(ver).takeIf { Files.exists(it) }
    }

    override fun defaultVersion(): String? = defaultPythonVersion
    override fun installedVersion(): String? = currentInstalledVersion()

    override fun installedVersions(): List<String> {
        val base = pythonBase()
        if (!Files.isDirectory(base)) return emptyList()
        return runCatching {
            Files.list(base).use { stream ->
                stream
                    .filter { Files.isDirectory(it) && it.fileName.toString() != "CURRENT" }
                    .map { it.fileName.toString() }
                    .filter { Files.exists(pythonBinary(it)) }
                    .toList()
                    .sortedWith(VERSION_DESC)
            }
        }.getOrDefault(emptyList())
    }

    override fun activeVersion(): String? = currentInstalledVersion()

    override fun applyVersion(version: String): Boolean {
        if (!Files.exists(pythonBinary(version))) return false
        writePointer(version)
        return true
    }

    override fun availableVersions(): List<String> {
        val bundled = discoverBundleVersions()
        val installed = installedVersions()
        return (bundled + defaultPythonVersion + installed).filter { it.isNotBlank() }.distinct().sortedWith(VERSION_DESC)
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
        val arch = when (archKey) { "arm64" -> "aarch64"; else -> "x86_64" }
        return Regex("^page-python-cpython-$osKey-$arch-(.+?)\\.tar\\.gz$")
    }

    override fun install(version: String?, onProgress: (LspInstaller.Progress) -> Unit) {
        try {
            val resolved = version?.takeIf { it.isNotBlank() } ?: defaultPythonVersion
            val root = pythonRoot(resolved)

            val precheck = pythonBinary(resolved)
            if (Files.exists(precheck)) {
                writePointer(resolved)
                onProgress(LspInstaller.Progress.Done(precheck))
                return
            }
            if (Files.exists(root)) ArchiveExtractors.deleteRecursively(root)
            Files.createDirectories(root)

            val url = downloadUrl(resolved)
            val tmp = Files.createTempFile("page-python-", ".tar.gz")
            try {
                onProgress(LspInstaller.Progress.CommandOutput("> GET $url"))
                downloader(url, tmp) { read, total ->
                    onProgress(LspInstaller.Progress.Downloading(read, total))
                }
                onProgress(LspInstaller.Progress.Extracting("Extracting Python $resolved …"))
                tarGzExtractor(tmp, root, 0)
                if (isWindows) requestDefenderExclusion(root, onProgress)
            } catch (t: Throwable) {
                throw IOException("Python download failed ($url): ${t.message}", t)
            } finally {
                runCatching { Files.deleteIfExists(tmp) }
            }

            val py = pythonBinary(resolved)
            if (!Files.exists(py)) {
                val topFiles = runCatching {
                    Files.list(root).use { s -> s.limit(30).map { it.fileName.toString() }.toList().joinToString(", ") }
                }.getOrDefault("(empty)")
                throw IOException("python binary missing after extraction: $py\nTop-level contents: $topFiles")
            }
            runCatching { py.toFile().setExecutable(true, false) }
            writePointer(resolved)
            onProgress(LspInstaller.Progress.Done(py))
        } catch (t: Throwable) {
            runCatching { ArchiveExtractors.deleteRecursively(pythonRoot(version?.takeIf { it.isNotBlank() } ?: defaultPythonVersion)) }
            onProgress(LspInstaller.Progress.Failed(t))
        }
    }

    internal fun downloadUrl(version: String): String {
        val arch = when (archKey) { "arm64" -> "aarch64"; else -> "x86_64" }
        return "https://github.com/$assetsRepo/releases/download/$releaseTag/page-python-cpython-$osKey-$arch-$version.tar.gz"
    }

    fun pythonBinary(version: String): Path {
        val name = if (isWindows) "python.exe" else "python3"
        val candidates = listOf(
            pythonRoot(version).resolve("bin").resolve(name),
            pythonRoot(version).resolve(name),
        )
        return candidates.firstOrNull { Files.exists(it) } ?: candidates.first()
    }

    fun pythonRoot(version: String): Path = pythonBase().resolve(version)

    private fun pythonBase(): Path = LspInstaller.lspHome().resolve("python")

    override fun installDir(version: String?): Path {
        val v = version?.takeIf { it.isNotBlank() } ?: defaultPythonVersion
        return pythonRoot(v)
    }

    private fun requestDefenderExclusion(target: Path, onProgress: (LspInstaller.Progress) -> Unit) {
        val pathLiteral = target.toString().replace("'", "''")
        val innerCommand = "try { Add-MpPreference -ExclusionPath ''$pathLiteral''; exit 0 } catch { exit 2 }"
        val outerCommand = "Start-Process powershell -Verb RunAs -WindowStyle Hidden -Wait " +
            "-ArgumentList '-NoProfile -ExecutionPolicy Bypass -Command \"$innerCommand\"'"
        val cmd = listOf("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", outerCommand)
        val exit = try {
            Runtime.getRuntime().exec(cmd.toTypedArray()).waitFor()
        } catch (_: Throwable) { -1 }
        if (exit == 0) onProgress(LspInstaller.Progress.CommandOutput("[info] Defender exclusion registered"))
    }

    fun currentInstalledVersion(): String? {
        val pointer = pythonBase().resolve("CURRENT")
        return runCatching { Files.readString(pointer).trim().takeIf { it.isNotEmpty() } }.getOrNull()
    }

    private fun writePointer(version: String) {
        val pointer = pythonBase().resolve("CURRENT")
        Files.createDirectories(pointer.parent)
        Files.writeString(pointer, version)
    }

    fun pythonHome(): Path? {
        val ver = currentInstalledVersion() ?: return null
        val root = pythonRoot(ver)
        return if (Files.isDirectory(root)) root else null
    }

    companion object {
        const val DEFAULT_PYTHON_VERSION = "3.13.13"
        const val DEFAULT_ASSETS_REPO = "monkshark/page-ide-assets"
        const val DEFAULT_RELEASE_TAG = "python-bundle"

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

        internal fun fetchPythonVersions(): List<String> {
            val url = "https://www.python.org/api/v2/downloads/release/?is_published=true&pre_release=false&page_size=20"
            val conn = URI(url).toURL().openConnection() as HttpURLConnection
            conn.connectTimeout = 5_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "PAGE-IDE/0.1 PythonInstaller")
            try {
                val code = conn.responseCode
                if (code !in 200..299) return emptyList()
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val regex = Regex("\"name\"\\s*:\\s*\"Python (\\d+\\.\\d+\\.\\d+)\"")
                return regex.findAll(body).map { it.groupValues[1] }.distinct().toList().sortedWith(VERSION_DESC).take(20)
            } finally {
                conn.disconnect()
            }
        }
    }
}
