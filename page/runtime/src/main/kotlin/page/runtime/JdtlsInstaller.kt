package page.runtime

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

class JdtlsInstaller(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val versionsFetcher: (baseUrl: String) -> List<String> = JdtlsInstaller::fetchMilestoneVersions,
    private val snapshotFileFetcher: (baseUrl: String) -> String? = JdtlsInstaller::fetchSnapshotFileName,
    private val milestoneFileFetcher: (baseUrl: String, version: String) -> String? = JdtlsInstaller::fetchMilestoneFileName,
    private val downloader: (url: String, target: Path, onProgress: (Long, Long) -> Unit) -> Unit = InstallerHttp::download,
    private val defaultVersion: String = "snapshot-latest",
) : LspInstaller {

    override val languageId: String = "jdtls"
    override val displayName: String = "Eclipse JDT Language Server"
    override val precheck: LspInstaller.Precheck =
        if (hasJavaOnPath()) LspInstaller.Precheck.Ok
        else LspInstaller.Precheck.MissingTool(
            tool = "java",
            installUrl = "https://adoptium.net/",
            message = "JDT Language Server requires Java 21+ to run. Install a JDK then retry.",
        )

    override fun isInstalled(): Boolean = executable() != null

    override fun executable(): Path? {
        val tag = currentInstalledVersion() ?: return null
        val root = installRoot(tag)
        if (!Files.isDirectory(root)) return null
        val launcher = root.resolve(launcherName())
        return launcher.takeIf { Files.exists(it) }
    }

    override fun defaultVersion(): String? = defaultVersion

    override fun installedVersion(): String? = currentInstalledVersion()

    override fun installedVersions(): List<String> {
        val root = LspInstaller.lspHome().resolve(languageId)
        if (!Files.isDirectory(root)) return emptyList()
        return runCatching {
            Files.list(root).use { stream ->
                stream
                    .filter { Files.isDirectory(it) && it.fileName.toString() != "CURRENT" }
                    .filter { Files.exists(it.resolve(launcherName())) }
                    .map { it.fileName.toString() }
                    .toList()
                    .sortedWith(installedVersionComparator())
            }
        }.getOrDefault(emptyList())
    }

    override fun activeVersion(): String? = currentInstalledVersion()

    override fun applyVersion(version: String): Boolean {
        val launcher = installRoot(version).resolve(launcherName())
        if (!Files.exists(launcher)) return false
        writePointer(version)
        return true
    }

    override fun availableVersions(): List<String> {
        val discovered = runCatching {
            val milestones = versionsFetcher(baseUrl)
            listOf("snapshot-latest") + milestones
        }.getOrDefault(listOf("snapshot-latest"))
        val installed = installedVersions()
        val merged = mutableListOf<String>()
        merged.addAll(discovered)
        for (v in installed) if (v !in merged) merged.add(v)
        return merged
    }

    private fun installedVersionComparator(): Comparator<String> = Comparator { a, b ->
        when {
            a == b -> 0
            a == "snapshot-latest" -> -1
            b == "snapshot-latest" -> 1
            else -> VERSION_DESC.compare(a, b)
        }
    }

    override fun install(version: String?, onProgress: (LspInstaller.Progress) -> Unit) {
        try {
            val requested = version ?: defaultVersion
            val (label, fileName) = resolveAsset(requested)
                ?: throw IOException("JDT-LS $requested 다운로드 파일명 조회 실패")

            val downloadUrl = if (label == "snapshot-latest") {
                "${baseUrl.trimEnd('/')}/snapshots/$fileName"
            } else {
                "${baseUrl.trimEnd('/')}/milestones/$label/$fileName"
            }

            val target = installRoot(label)
            val tmp = Files.createTempFile("jdtls-", ".tar.gz")
            try {
                downloader(downloadUrl, tmp) { read, total ->
                    onProgress(LspInstaller.Progress.Downloading(read, total))
                }
                onProgress(LspInstaller.Progress.Extracting("Extracting JDT-LS…"))
                ArchiveExtractors.extractTarGz(tmp, target, flatten = 0)
                writeLauncher(target)
                writePointer(label)
                val exe = target.resolve(launcherName())
                if (!Files.exists(exe)) throw IOException("launcher missing at $exe")
                runCatching { exe.toFile().setExecutable(true, false) }
                onProgress(LspInstaller.Progress.Done(exe))
            } finally {
                runCatching { Files.deleteIfExists(tmp) }
            }
        } catch (t: Throwable) {
            runCatching { ArchiveExtractors.deleteRecursively(installRoot(version?.takeIf { it.isNotBlank() } ?: defaultVersion)) }
            onProgress(LspInstaller.Progress.Failed(t))
        }
    }

    private fun resolveAsset(version: String): Pair<String, String>? {
        if (version == "snapshot-latest" || version == "snapshot" || version == "latest") {
            val file = snapshotFileFetcher(baseUrl) ?: return null
            return "snapshot-latest" to file
        }
        val file = milestoneFileFetcher(baseUrl, version) ?: return null
        return version to file
    }

    fun installRoot(version: String = currentInstalledVersion() ?: defaultVersion): Path =
        LspInstaller.lspHome().resolve(languageId).resolve(sanitize(version))

    private fun currentInstalledVersion(): String? {
        val pointer = LspInstaller.lspHome().resolve(languageId).resolve("CURRENT")
        return runCatching { Files.readString(pointer).trim().takeIf { it.isNotEmpty() } }.getOrNull()
    }

    private fun writePointer(version: String) {
        val pointer = LspInstaller.lspHome().resolve(languageId).resolve("CURRENT")
        Files.createDirectories(pointer.parent)
        Files.writeString(pointer, version)
    }

    private fun launcherName(): String = if (LspInstaller.isWindows()) "jdtls.bat" else "jdtls.sh"

    private fun writeLauncher(target: Path) {
        val configDir = configDirFor(target)
            ?: throw IOException("config dir not found under $target")
        val launcherJar = findLauncherJar(target)
            ?: throw IOException("equinox launcher jar not found under $target/plugins")
        val launcher = target.resolve(launcherName())
        val data = target.resolve("workspace")
        Files.createDirectories(data)
        val text = if (LspInstaller.isWindows()) {
            buildString {
                append("@echo off\r\n")
                append("setlocal\r\n")
                append("set JDTLS_HOME=%~dp0\r\n")
                append("if defined JAVA_HOME (\r\n")
                append("  set JAVA_CMD=%JAVA_HOME%\\bin\\java.exe\r\n")
                append(") else (\r\n")
                append("  set JAVA_CMD=java\r\n")
                append(")\r\n")
                append("\"%JAVA_CMD%\" ^\r\n")
                append("  -Declipse.application=org.eclipse.jdt.ls.core.id1 ^\r\n")
                append("  -Dosgi.bundles.defaultStartLevel=4 ^\r\n")
                append("  -Declipse.product=org.eclipse.jdt.ls.core.product ^\r\n")
                append("  -Dlog.level=ALL ^\r\n")
                append("  -Xms1g ^\r\n")
                append("  --add-modules=ALL-SYSTEM ^\r\n")
                append("  --add-opens java.base/java.util=ALL-UNNAMED ^\r\n")
                append("  --add-opens java.base/java.lang=ALL-UNNAMED ^\r\n")
                append("  -jar \"%JDTLS_HOME%${target.relativize(launcherJar)}\" ^\r\n")
                append("  -configuration \"%JDTLS_HOME%${target.relativize(configDir)}\" ^\r\n")
                append("  -data \"%JDTLS_HOME%workspace\" %*\r\n")
            }
        } else {
            buildString {
                append("#!/usr/bin/env bash\n")
                append("set -e\n")
                append("HERE=\"$(cd \"$(dirname \"\${BASH_SOURCE[0]}\")\" && pwd)\"\n")
                append("if [ -n \"\$JAVA_HOME\" ]; then\n")
                append("  JAVA_CMD=\"\$JAVA_HOME/bin/java\"\n")
                append("else\n")
                append("  JAVA_CMD=java\n")
                append("fi\n")
                append("exec \"\$JAVA_CMD\" \\\n")
                append("  -Declipse.application=org.eclipse.jdt.ls.core.id1 \\\n")
                append("  -Dosgi.bundles.defaultStartLevel=4 \\\n")
                append("  -Declipse.product=org.eclipse.jdt.ls.core.product \\\n")
                append("  -Dlog.level=ALL \\\n")
                append("  -Xms1g \\\n")
                append("  --add-modules=ALL-SYSTEM \\\n")
                append("  --add-opens java.base/java.util=ALL-UNNAMED \\\n")
                append("  --add-opens java.base/java.lang=ALL-UNNAMED \\\n")
                append("  -jar \"\$HERE/${target.relativize(launcherJar).toString().replace('\\', '/')}\" \\\n")
                append("  -configuration \"\$HERE/${target.relativize(configDir).toString().replace('\\', '/')}\" \\\n")
                append("  -data \"\$HERE/workspace\" \"\$@\"\n")
            }
        }
        Files.writeString(launcher, text)
        runCatching { launcher.toFile().setExecutable(true, false) }
    }

    private fun configDirFor(target: Path): Path? {
        val candidate = when (LspInstaller.osKey()) {
            "macos" -> target.resolve("config_mac")
            "windows" -> target.resolve("config_win")
            else -> target.resolve("config_linux")
        }
        return candidate.takeIf { Files.isDirectory(it) }
    }

    private fun findLauncherJar(target: Path): Path? {
        val plugins = target.resolve("plugins")
        if (!Files.isDirectory(plugins)) return null
        Files.list(plugins).use { stream ->
            return stream
                .filter { it.fileName.toString().startsWith("org.eclipse.equinox.launcher_") }
                .filter { it.fileName.toString().endsWith(".jar") }
                .findFirst()
                .orElse(null)
        }
    }

    private fun sanitize(version: String): String =
        version.replace(Regex("[\\\\/:*?\"<>|]"), "_")

    private fun hasJavaOnPath(): Boolean {
        val envPath = System.getenv("PATH") ?: return false
        val candidates = if (LspInstaller.isWindows()) listOf("java.exe") else listOf("java")
        for (segment in envPath.split(java.io.File.pathSeparatorChar)) {
            if (segment.isBlank()) continue
            val dir = runCatching { java.nio.file.Paths.get(segment) }.getOrNull() ?: continue
            for (name in candidates) {
                if (Files.exists(dir.resolve(name))) return true
            }
        }
        return false
    }

    companion object {

        const val DEFAULT_BASE_URL: String = "https://download.eclipse.org/jdtls"
        private val MILESTONE_DIR_REGEX = Regex("""href="([0-9]+\.[0-9]+\.[0-9]+)/?"""")
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

        fun fetchMilestoneVersions(baseUrl: String = DEFAULT_BASE_URL): List<String> = runCatching {
            val html = fetchText("${baseUrl.trimEnd('/')}/milestones/")
            parseMilestoneVersions(html)
        }.getOrDefault(emptyList())

        internal fun parseMilestoneVersions(html: String): List<String> {
            return MILESTONE_DIR_REGEX.findAll(html)
                .map { it.groupValues[1] }
                .toSet()
                .sortedWith(VERSION_DESC)
        }

        fun fetchSnapshotFileName(baseUrl: String = DEFAULT_BASE_URL): String? = runCatching {
            fetchText("${baseUrl.trimEnd('/')}/snapshots/latest.txt").trim().takeIf { it.isNotBlank() }
        }.getOrNull()

        fun fetchMilestoneFileName(baseUrl: String = DEFAULT_BASE_URL, version: String): String? = runCatching {
            fetchText("${baseUrl.trimEnd('/')}/milestones/$version/latest.txt").trim().takeIf { it.isNotBlank() }
        }.getOrNull()

        private fun fetchText(url: String): String {
            val conn = URI(url).toURL().openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", "PAGE-IDE/0.1 JdtlsInstaller")
            try {
                val code = conn.responseCode
                if (code !in 200..299) throw IOException("download.eclipse.org HTTP $code")
                return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }
    }
}
