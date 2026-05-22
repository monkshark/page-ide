package page.app

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

class DartSdkInstaller(
    private val osKey: String = LspInstaller.osKey(),
    private val archKey: String = ArchDetect.archKey(),
    private val isWindows: Boolean = LspInstaller.isWindows(),
    private val downloader: (url: String, target: Path, onProgress: (Long, Long) -> Unit) -> Unit = InstallerHttp::download,
    private val versionsFetcher: () -> List<String> = { DartSdkInstaller.fetchVersions() },
    private val latestResolver: () -> String? = { DartSdkInstaller.fetchLatestVersion() },
    private val defaultDartVersion: String = "latest",
) : LspInstaller {

    override val languageId: String = "dart"
    override val displayName: String = "Dart SDK (analysis server)"
    override val precheck: LspInstaller.Precheck = LspInstaller.Precheck.Ok

    override fun isInstalled(): Boolean = executable() != null

    override fun executable(): Path? {
        val ver = currentInstalledVersion() ?: return null
        val wrapper = lspWrapper(ver)
        return wrapper.takeIf { Files.exists(it) }
    }

    override fun defaultVersion(): String? = defaultDartVersion

    override fun installedVersion(): String? = currentInstalledVersion()

    override fun availableVersions(): List<String> = runCatching { versionsFetcher() }.getOrDefault(emptyList())

    override fun install(version: String?, onProgress: (LspInstaller.Progress) -> Unit) {
        try {
            val requested = version ?: defaultDartVersion
            val resolved = if (requested == "latest") {
                latestResolver() ?: throw IOException("Dart SDK latest 버전 조회 실패")
            } else requested
            val root = installRoot(resolved)
            Files.createDirectories(root)

            val url = downloadUrl(resolved)
            val tmp = Files.createTempFile("page-dart-sdk-", ".zip")
            try {
                downloader(url, tmp) { read, total ->
                    onProgress(LspInstaller.Progress.Downloading(read, total))
                }
                onProgress(LspInstaller.Progress.Extracting("Extracting Dart SDK…"))
                ArchiveExtractors.extractZip(tmp, root, flatten = 0)
            } finally {
                runCatching { Files.deleteIfExists(tmp) }
            }

            val dart = dartBinary(resolved)
            if (!Files.exists(dart)) throw IOException("Dart SDK 추출 후 dart 누락: $dart")
            runCatching { dart.toFile().setExecutable(true, false) }

            val wrapper = writeWrapper(resolved, dart)
            writePointer(resolved)
            onProgress(LspInstaller.Progress.Done(wrapper))
        } catch (t: Throwable) {
            onProgress(LspInstaller.Progress.Failed(t))
        }
    }

    internal fun downloadUrl(version: String): String {
        val osPart = when (osKey) {
            "macos" -> "macos"
            "windows" -> "windows"
            else -> "linux"
        }
        val archPart = when (archKey) {
            "arm64" -> "arm64"
            else -> "x64"
        }
        return "https://storage.googleapis.com/dart-archive/channels/stable/release/$version/sdk/dartsdk-$osPart-$archPart-release.zip"
    }

    fun dartBinary(version: String): Path {
        val name = if (isWindows) "dart.exe" else "dart"
        return installRoot(version).resolve("dart-sdk").resolve("bin").resolve(name)
    }

    fun lspWrapper(version: String): Path {
        val name = if (isWindows) "dart-language-server.cmd" else "dart-language-server"
        return installRoot(version).resolve(name)
    }

    private fun writeWrapper(version: String, dart: Path): Path {
        val wrapper = lspWrapper(version)
        Files.createDirectories(wrapper.parent)
        val body = if (isWindows) {
            "@echo off\r\n\"${dart}\" language-server %*\r\n"
        } else {
            "#!/usr/bin/env bash\nexec \"${dart}\" language-server \"$@\"\n"
        }
        Files.writeString(wrapper, body)
        runCatching { wrapper.toFile().setExecutable(true, false) }
        return wrapper
    }

    fun installRoot(version: String): Path = installBase().resolve(sanitize(version))

    private fun installBase(): Path = LspInstaller.lspHome().resolve("dart")

    fun currentInstalledVersion(): String? {
        val pointer = installBase().resolve("CURRENT")
        return runCatching { Files.readString(pointer).trim().takeIf { it.isNotEmpty() } }.getOrNull()
    }

    private fun writePointer(version: String) {
        val pointer = installBase().resolve("CURRENT")
        Files.createDirectories(pointer.parent)
        Files.writeString(pointer, version)
    }

    private fun sanitize(version: String): String = version.replace(Regex("[\\\\/:*?\"<>|]"), "_")

    companion object {

        const val LATEST_VERSION_URL: String =
            "https://storage.googleapis.com/dart-archive/channels/stable/release/latest/VERSION"

        private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

        private data class DartVersionDoc(val version: String? = null)

        fun fetchLatestVersion(url: String = LATEST_VERSION_URL): String? = runCatching {
            parseLatestVersion(fetchBody(url))
        }.getOrNull()

        fun fetchVersions(): List<String> = fetchLatestVersion()?.let { listOf(it) } ?: emptyList()

        internal fun parseLatestVersion(body: String): String? {
            val doc = gson.fromJson(body, DartVersionDoc::class.java) ?: return null
            return doc.version?.takeIf { it.isNotBlank() }
        }

        private fun fetchBody(url: String): String {
            val conn = URI(url).toURL().openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "PAGE-IDE/0.1 DartSdkInstaller")
            try {
                val code = conn.responseCode
                if (code !in 200..299) throw IOException("dart-archive HTTP $code")
                return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }
    }
}
