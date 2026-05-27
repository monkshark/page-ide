package page.runtime

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

class NodeInstaller(
    private val osKey: String = LspInstaller.osKey(),
    private val archKey: String = ArchDetect.archKey(),
    private val isWindows: Boolean = LspInstaller.isWindows(),
    private val downloader: (url: String, target: Path, onProgress: (Long, Long) -> Unit) -> Unit = InstallerHttp::download,
    private val tarGzExtractor: (Path, Path, Int) -> Unit = { src, dst, flatten -> ArchiveExtractors.extractTarGz(src, dst, flatten) },
    private val zipExtractor: (Path, Path, Int) -> Unit = { src, dst, flatten -> ArchiveExtractors.extractZip(src, dst, flatten) },
    private val defaultNodeVersion: String = DEFAULT_NODE_VERSION,
    private val manifestFetcher: () -> List<String> = { fetchNodeVersions() },
) : LspInstaller {

    override val languageId: String = "node"
    override val displayName: String = "Node.js"
    override val precheck: LspInstaller.Precheck = LspInstaller.Precheck.Ok
    override val heavyInstall: LspInstaller.HeavyInstallEstimate = LspInstaller.HeavyInstallEstimate(
        sizeEstimate = "~30 MB to 50 MB",
        durationEstimate = "~1 to 2 min",
        notes = "PAGE downloads Node.js from nodejs.org and extracts it into an isolated directory.",
    )

    override fun isInstalled(): Boolean = executable() != null

    override fun executable(): Path? {
        val ver = currentInstalledVersion() ?: return null
        return nodeBinary(ver).takeIf { Files.exists(it) }
    }

    override fun defaultVersion(): String? = defaultNodeVersion

    override fun installedVersion(): String? = currentInstalledVersion()

    override fun installedVersions(): List<String> {
        val base = nodeBase()
        if (!Files.isDirectory(base)) return emptyList()
        return runCatching {
            Files.list(base).use { stream ->
                stream
                    .filter { Files.isDirectory(it) && it.fileName.toString() != "CURRENT" }
                    .map { it.fileName.toString() }
                    .filter { Files.exists(nodeBinary(it)) }
                    .toList()
                    .sortedWith(VERSION_DESC)
            }
        }.getOrDefault(emptyList())
    }

    override fun activeVersion(): String? = currentInstalledVersion()

    override fun applyVersion(version: String): Boolean {
        if (!Files.exists(nodeBinary(version))) return false
        writePointer(version)
        return true
    }

    override fun availableVersions(): List<String> {
        val manifest = runCatching { manifestFetcher() }.getOrDefault(emptyList())
        val installed = installedVersions()
        return (manifest + defaultNodeVersion + installed).filter { it.isNotBlank() }.distinct().sortedWith(VERSION_DESC)
    }

    override fun install(version: String?, onProgress: (LspInstaller.Progress) -> Unit) {
        try {
            val resolved = version?.takeIf { it.isNotBlank() } ?: defaultNodeVersion
            val root = nodeRoot(resolved)

            val node = nodeBinary(resolved)
            if (Files.exists(node)) {
                writePointer(resolved)
                onProgress(LspInstaller.Progress.Done(node))
                return
            }
            if (Files.exists(root)) ArchiveExtractors.deleteRecursively(root)
            Files.createDirectories(root)

            val url = downloadUrl(resolved)
            val ext = if (isWindows) "zip" else "tar.gz"
            val tmp = Files.createTempFile("page-node-", ".$ext")
            try {
                onProgress(LspInstaller.Progress.CommandOutput("> GET $url"))
                downloader(url, tmp) { read, total ->
                    onProgress(LspInstaller.Progress.Downloading(read, total))
                }
                onProgress(LspInstaller.Progress.Extracting("Extracting Node.js $resolved …"))
                if (isWindows) zipExtractor(tmp, root, 1)
                else tarGzExtractor(tmp, root, 1)
            } catch (t: Throwable) {
                throw IOException("Node.js download failed ($url): ${t.message}", t)
            } finally {
                runCatching { Files.deleteIfExists(tmp) }
            }

            if (!Files.exists(node)) {
                throw IOException("node binary missing after extraction: $node")
            }
            runCatching { node.toFile().setExecutable(true, false) }
            writePointer(resolved)
            onProgress(LspInstaller.Progress.Done(node))
        } catch (t: Throwable) {
            runCatching { ArchiveExtractors.deleteRecursively(nodeRoot(version?.takeIf { it.isNotBlank() } ?: defaultNodeVersion)) }
            onProgress(LspInstaller.Progress.Failed(t))
        }
    }

    internal fun downloadUrl(version: String): String {
        val os = when (osKey) {
            "macos" -> "darwin"
            "windows" -> "win"
            else -> osKey
        }
        val arch = when (archKey) {
            "amd64" -> "x64"
            "arm64" -> "arm64"
            else -> "x64"
        }
        val ext = if (isWindows) "zip" else "tar.gz"
        return "https://nodejs.org/dist/v$version/node-v$version-$os-$arch.$ext"
    }

    fun nodeBinary(version: String): Path {
        val name = if (isWindows) "node.exe" else "node"
        val binDir = nodeRoot(version).resolve("bin").resolve(name)
        if (Files.exists(binDir)) return binDir
        val rootDir = nodeRoot(version).resolve(name)
        if (Files.exists(rootDir)) return rootDir
        return if (isWindows) rootDir else binDir
    }

    fun nodeHome(version: String): Path = nodeRoot(version)

    fun nodeRoot(version: String): Path = nodeBase().resolve(version)

    private fun nodeBase(): Path = LspInstaller.lspHome().resolve("node")

    override fun installDir(version: String?): Path {
        val v = version?.takeIf { it.isNotBlank() } ?: defaultNodeVersion
        return nodeRoot(v)
    }

    fun currentInstalledVersion(): String? {
        val pointer = nodeBase().resolve("CURRENT")
        return runCatching { Files.readString(pointer).trim().takeIf { it.isNotEmpty() } }.getOrNull()
    }

    private fun writePointer(version: String) {
        val pointer = nodeBase().resolve("CURRENT")
        Files.createDirectories(pointer.parent)
        Files.writeString(pointer, version)
    }

    fun nodeHome(): Path? {
        val ver = currentInstalledVersion() ?: return null
        val root = nodeRoot(ver)
        return if (Files.isDirectory(root)) root else null
    }

    companion object {
        const val DEFAULT_NODE_VERSION = "22.13.1"
        const val NODE_VERSIONS_URL = "https://nodejs.org/dist/index.json"

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

        private data class NodeVersionEntry(val version: String? = null, val lts: Any? = null)

        internal fun fetchNodeVersions(url: String = NODE_VERSIONS_URL): List<String> {
            val conn = URI(url).toURL().openConnection() as HttpURLConnection
            conn.connectTimeout = 5_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "PAGE-IDE/0.1 NodeInstaller")
            try {
                val code = conn.responseCode
                if (code !in 200..299) return emptyList()
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val entries = gson.fromJson(body, Array<NodeVersionEntry>::class.java) ?: return emptyList()
                return entries
                    .filter { it.lts != null && it.lts != false }
                    .mapNotNull { it.version?.removePrefix("v")?.takeIf(String::isNotBlank) }
                    .take(30)
            } finally {
                conn.disconnect()
            }
        }
    }
}
