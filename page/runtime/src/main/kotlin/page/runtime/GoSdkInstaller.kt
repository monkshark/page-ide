package page.runtime

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

class GoSdkInstaller(
    private val osKey: String = LspInstaller.osKey(),
    private val archKey: String = ArchDetect.archKey(),
    private val isWindows: Boolean = LspInstaller.isWindows(),
    private val downloader: (url: String, target: Path, onProgress: (Long, Long) -> Unit) -> Unit = InstallerHttp::download,
    private val tarGzExtractor: (Path, Path, Int) -> Unit = { src, dst, flatten -> ArchiveExtractors.extractTarGz(src, dst, flatten) },
    private val zipExtractor: (Path, Path, Int) -> Unit = { src, dst, flatten -> ArchiveExtractors.extractZip(src, dst, flatten) },
    private val defaultGoVersion: String = DEFAULT_GO_VERSION,
    private val manifestFetcher: () -> List<String> = { fetchGoVersions() },
) : LspInstaller {

    override val languageId: String = "go-sdk"
    override val displayName: String = "Go SDK"
    override val precheck: LspInstaller.Precheck = LspInstaller.Precheck.Ok
    override val heavyInstall: LspInstaller.HeavyInstallEstimate = LspInstaller.HeavyInstallEstimate(
        sizeEstimate = "~70 MB to 120 MB",
        durationEstimate = "~1 to 3 min",
        notes = "PAGE downloads Go SDK from go.dev and extracts it into an isolated directory.",
    )

    override fun isInstalled(): Boolean = executable() != null

    override fun executable(): Path? {
        val ver = currentInstalledVersion() ?: return null
        return goBinary(ver).takeIf { Files.exists(it) }
    }

    override fun defaultVersion(): String? = defaultGoVersion
    override fun installedVersion(): String? = currentInstalledVersion()

    override fun installedVersions(): List<String> {
        val base = goBase()
        if (!Files.isDirectory(base)) return emptyList()
        return runCatching {
            Files.list(base).use { stream ->
                stream
                    .filter { Files.isDirectory(it) && it.fileName.toString() != "CURRENT" }
                    .map { it.fileName.toString() }
                    .filter { Files.exists(goBinary(it)) }
                    .toList()
                    .sortedWith(VERSION_DESC)
            }
        }.getOrDefault(emptyList())
    }

    override fun activeVersion(): String? = currentInstalledVersion()

    override fun applyVersion(version: String): Boolean {
        if (!Files.exists(goBinary(version))) return false
        writePointer(version)
        return true
    }

    override fun availableVersions(): List<String> {
        val manifest = runCatching { manifestFetcher() }.getOrDefault(emptyList())
        val installed = installedVersions()
        return (manifest + defaultGoVersion + installed).filter { it.isNotBlank() }.distinct().sortedWith(VERSION_DESC)
    }

    override fun install(version: String?, onProgress: (LspInstaller.Progress) -> Unit) {
        try {
            val resolved = version?.takeIf { it.isNotBlank() } ?: defaultGoVersion
            val root = goRoot(resolved)

            val go = goBinary(resolved)
            if (Files.exists(go)) {
                writePointer(resolved)
                onProgress(LspInstaller.Progress.Done(go))
                return
            }
            if (Files.exists(root)) ArchiveExtractors.deleteRecursively(root)
            Files.createDirectories(root)

            val url = downloadUrl(resolved)
            val ext = if (isWindows) "zip" else "tar.gz"
            val tmp = Files.createTempFile("page-go-", ".$ext")
            try {
                onProgress(LspInstaller.Progress.CommandOutput("> GET $url"))
                downloader(url, tmp) { read, total ->
                    onProgress(LspInstaller.Progress.Downloading(read, total))
                }
                onProgress(LspInstaller.Progress.Extracting("Extracting Go SDK $resolved …"))
                if (isWindows) zipExtractor(tmp, root, 1)
                else tarGzExtractor(tmp, root, 1)
            } catch (t: Throwable) {
                throw IOException("Go SDK download failed ($url): ${t.message}", t)
            } finally {
                runCatching { Files.deleteIfExists(tmp) }
            }

            if (!Files.exists(go)) {
                val listing = runCatching {
                    java.nio.file.Files.walk(root).use { s -> s.limit(20).map { root.relativize(it).toString() }.toList().joinToString(", ") }
                }.getOrDefault("(empty)")
                throw IOException("go binary missing after extraction: $go\nExtracted contents: $listing")
            }
            runCatching { go.toFile().setExecutable(true, false) }
            writePointer(resolved)
            onProgress(LspInstaller.Progress.Done(go))
        } catch (t: Throwable) {
            runCatching { ArchiveExtractors.deleteRecursively(goRoot(version?.takeIf { it.isNotBlank() } ?: defaultGoVersion)) }
            onProgress(LspInstaller.Progress.Failed(t))
        }
    }

    internal fun downloadUrl(version: String): String {
        val os = when (osKey) {
            "macos" -> "darwin"
            else -> osKey
        }
        val arch = when (archKey) {
            "amd64" -> "amd64"
            "arm64" -> "arm64"
            else -> "amd64"
        }
        val ext = if (isWindows) "zip" else "tar.gz"
        return "https://go.dev/dl/go$version.$os-$arch.$ext"
    }

    fun goBinary(version: String): Path {
        val name = if (isWindows) "go.exe" else "go"
        return goRoot(version).resolve("bin").resolve(name)
    }

    fun goRoot(version: String): Path = goBase().resolve(version)

    private fun goBase(): Path = LspInstaller.lspHome().resolve("go-runtime")

    override fun installDir(version: String?): Path {
        val v = version?.takeIf { it.isNotBlank() } ?: defaultGoVersion
        return goRoot(v)
    }

    fun currentInstalledVersion(): String? {
        val pointer = goBase().resolve("CURRENT")
        return runCatching { Files.readString(pointer).trim().takeIf { it.isNotEmpty() } }.getOrNull()
    }

    private fun writePointer(version: String) {
        val pointer = goBase().resolve("CURRENT")
        Files.createDirectories(pointer.parent)
        Files.writeString(pointer, version)
    }

    fun goHome(): Path? {
        val ver = currentInstalledVersion() ?: return null
        val root = goRoot(ver)
        return if (Files.isDirectory(root)) root else null
    }

    companion object {
        const val DEFAULT_GO_VERSION = "1.24.3"

        private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

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

        private data class GoRelease(val version: String? = null, val stable: Boolean = false)

        internal fun fetchGoVersions(): List<String> {
            val url = "https://go.dev/dl/?mode=json&include=all"
            val conn = URI(url).toURL().openConnection() as HttpURLConnection
            conn.connectTimeout = 5_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "PAGE-IDE/0.1 GoSdkInstaller")
            try {
                val code = conn.responseCode
                if (code !in 200..299) return emptyList()
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val releases = gson.fromJson(body, Array<GoRelease>::class.java) ?: return emptyList()
                return releases
                    .filter { it.stable }
                    .mapNotNull { it.version?.removePrefix("go")?.takeIf(String::isNotBlank) }
                    .take(20)
            } finally {
                conn.disconnect()
            }
        }
    }
}
