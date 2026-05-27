package page.runtime

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

class FlutterSdkInstaller(
    private val osKey: String = LspInstaller.osKey(),
    private val archKey: String = ArchDetect.archKey(),
    private val isWindows: Boolean = LspInstaller.isWindows(),
    private val downloader: (url: String, target: Path, onProgress: (Long, Long) -> Unit) -> Unit = InstallerHttp::download,
    private val releasesFetcher: (osKey: String) -> ReleasesDoc? = { FlutterSdkInstaller.fetchReleases(it) },
    private val processRunner: ProcessRunner = DefaultProcessRunner,
    private val defaultFlutterVersion: String = "latest",
) : LspInstaller {

    override val languageId: String = "flutter"
    override val displayName: String = "Flutter SDK (Dart analysis server)"
    override val precheck: LspInstaller.Precheck = LspInstaller.Precheck.Ok

    override fun isInstalled(): Boolean = executable() != null

    override fun executable(): Path? {
        val ver = currentInstalledVersion() ?: return null
        val wrapper = lspWrapper(ver)
        return wrapper.takeIf { Files.exists(it) }
    }

    override fun defaultVersion(): String? = defaultFlutterVersion

    override fun installedVersion(): String? = currentInstalledVersion()

    override fun installedVersions(): List<String> {
        val base = installBase()
        if (!Files.isDirectory(base)) return emptyList()
        return runCatching {
            Files.list(base).use { stream ->
                stream
                    .filter { Files.isDirectory(it) && it.fileName.toString() != "CURRENT" }
                    .filter { Files.exists(lspWrapper(it.fileName.toString())) }
                    .map { it.fileName.toString() }
                    .toList()
                    .sortedWith(VERSION_DESC)
            }
        }.getOrDefault(emptyList())
    }

    override fun activeVersion(): String? = currentInstalledVersion()

    override fun applyVersion(version: String): Boolean {
        if (!Files.exists(lspWrapper(version))) return false
        writePointer(version)
        return true
    }

    override fun availableVersions(): List<String> {
        val discovered = runCatching {
            val doc = releasesFetcher(osKey) ?: return@runCatching emptyList()
            stableVersionsFor(doc, archKey).map { it.version }
        }.getOrDefault(emptyList())
        val installed = installedVersions()
        return (discovered + installed).distinct().sortedWith(VERSION_DESC)
    }

    override fun install(version: String?, onProgress: (LspInstaller.Progress) -> Unit) {
        try {
            val doc = releasesFetcher(osKey) ?: throw IOException("Flutter release manifest fetch 실패")
            val stable = stableVersionsFor(doc, archKey)
            val requested = version ?: defaultFlutterVersion
            val release = if (requested == "latest" || requested.isBlank()) {
                stable.firstOrNull() ?: throw IOException("Flutter stable release 없음")
            } else {
                stable.firstOrNull { it.version == requested }
                    ?: throw IOException("Flutter $requested 버전 없음 ($osKey/$archKey)")
            }

            val baseUrl = doc.base_url?.trimEnd('/')
                ?: "https://storage.googleapis.com/flutter_infra_release/releases"
            val url = "$baseUrl/${release.archive}"

            val target = installRoot(release.version)
            val ext = if (release.archive.endsWith(".tar.xz")) ".tar.xz" else ".zip"
            val tmp = Files.createTempFile("page-flutter-", ext)
            try {
                downloader(url, tmp) { read, total ->
                    onProgress(LspInstaller.Progress.Downloading(read, total))
                }
                onProgress(LspInstaller.Progress.Extracting("Extracting Flutter SDK…"))
                if (release.archive.endsWith(".tar.xz")) ArchiveExtractors.extractTarXz(tmp, target, flatten = 0)
                else ArchiveExtractors.extractZip(tmp, target, flatten = 0)
            } finally {
                runCatching { Files.deleteIfExists(tmp) }
            }

            val flutterBin = flutterCommand(release.version)
            if (!Files.exists(flutterBin)) throw IOException("Flutter SDK 추출 후 flutter 누락: $flutterBin")
            runCatching { flutterBin.toFile().setExecutable(true, false) }

            onProgress(LspInstaller.Progress.CommandOutput("> flutter --version (dart-sdk 캐시 준비)"))
            val exit = runCatching {
                processRunner.runStreaming(listOf(flutterBin.toString(), "--version")) { line ->
                    onProgress(LspInstaller.Progress.CommandOutput(line))
                }
            }.getOrDefault(0)
            if (exit != 0) onProgress(LspInstaller.Progress.CommandOutput("flutter --version 종료 코드 $exit (계속)"))

            val dart = dartBinary(release.version)
            if (!Files.exists(dart)) throw IOException("flutter/bin/cache/dart-sdk 누락: $dart")
            runCatching { dart.toFile().setExecutable(true, false) }

            val wrapper = writeWrapper(release.version, dart)
            writePointer(release.version)
            onProgress(LspInstaller.Progress.Done(wrapper))
        } catch (t: Throwable) {
            runCatching { ArchiveExtractors.deleteRecursively(installRoot(version?.takeIf { it.isNotBlank() } ?: defaultFlutterVersion)) }
            onProgress(LspInstaller.Progress.Failed(t))
        }
    }

    fun flutterCommand(version: String): Path {
        val name = if (isWindows) "flutter.bat" else "flutter"
        return installRoot(version).resolve("flutter").resolve("bin").resolve(name)
    }

    fun dartBinary(version: String): Path {
        val name = if (isWindows) "dart.exe" else "dart"
        return installRoot(version).resolve("flutter").resolve("bin").resolve("cache").resolve("dart-sdk").resolve("bin").resolve(name)
    }

    fun lspWrapper(version: String): Path {
        val name = if (isWindows) "flutter-language-server.cmd" else "flutter-language-server"
        return installRoot(version).resolve(name)
    }

    fun installRoot(version: String): Path = installBase().resolve(sanitize(version))

    private fun installBase(): Path = LspInstaller.lspHome().resolve("flutter")

    fun currentInstalledVersion(): String? {
        val pointer = installBase().resolve("CURRENT")
        return runCatching { Files.readString(pointer).trim().takeIf { it.isNotEmpty() } }.getOrNull()
    }

    private fun writePointer(version: String) {
        val pointer = installBase().resolve("CURRENT")
        Files.createDirectories(pointer.parent)
        Files.writeString(pointer, version)
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

    private fun sanitize(version: String): String = version.replace(Regex("[\\\\/:*?\"<>|]"), "_")

    data class Release(
        val hash: String? = null,
        val channel: String? = null,
        val version: String = "",
        val dart_sdk_arch: String? = null,
        val archive: String = "",
        val sha256: String? = null,
    )

    data class ReleasesDoc(
        val base_url: String? = null,
        val current_release: Map<String, String>? = null,
        val releases: List<Release>? = null,
    )

    companion object {

        private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

        internal val VERSION_DESC: Comparator<String> = Comparator { a, b ->
            val aParts = a.split('.', '-', '+')
            val bParts = b.split('.', '-', '+')
            val n = maxOf(aParts.size, bParts.size)
            for (i in 0 until n) {
                val ai = aParts.getOrNull(i)?.toIntOrNull() ?: 0
                val bi = bParts.getOrNull(i)?.toIntOrNull() ?: 0
                if (ai != bi) return@Comparator bi - ai
            }
            0
        }

        fun manifestUrl(osKey: String): String {
            val os = when (osKey) {
                "macos" -> "macos"
                "windows" -> "windows"
                else -> "linux"
            }
            return "https://storage.googleapis.com/flutter_infra_release/releases/releases_$os.json"
        }

        fun fetchReleases(osKey: String): ReleasesDoc? = runCatching {
            parseReleases(fetchBody(manifestUrl(osKey)))
        }.getOrNull()

        internal fun parseReleases(body: String): ReleasesDoc? =
            gson.fromJson(body, ReleasesDoc::class.java)

        internal fun stableVersionsFor(doc: ReleasesDoc, archKey: String): List<Release> {
            val releases = doc.releases ?: return emptyList()
            val wantArm64 = archKey == "arm64"
            return releases
                .asSequence()
                .filter { it.channel == "stable" }
                .filter { it.archive.isNotBlank() && it.version.isNotBlank() }
                .filter { release ->
                    val arch = release.dart_sdk_arch?.lowercase()
                    val archiveHasArm = release.archive.contains("arm64", ignoreCase = true)
                    if (wantArm64) arch == "arm64" || archiveHasArm
                    else arch == null || arch == "x64" || (!archiveHasArm && (arch == "ia32" || arch == "x86_64" || arch == "x64"))
                }
                .distinctBy { it.version }
                .toList()
        }

        private fun fetchBody(url: String): String {
            val conn = URI(url).toURL().openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "PAGE-IDE/0.1 FlutterSdkInstaller")
            try {
                val code = conn.responseCode
                if (code !in 200..299) throw IOException("flutter_infra_release HTTP $code")
                return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }
    }
}
