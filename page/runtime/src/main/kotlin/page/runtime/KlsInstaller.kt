package page.runtime

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream

object KlsInstaller {
    const val VERSION: String = "1.3.13-page-3"
    const val DOWNLOAD_URL: String = "https://github.com/Monkshark/kotlin-language-server/releases/download/$VERSION/server.zip"

    sealed class Progress {
        data class Downloading(val bytesRead: Long, val total: Long) : Progress()
        object Extracting : Progress()
        data class Done(val executable: Path) : Progress()
        data class Failed(val error: Throwable) : Progress()
    }

    fun userHome(): Path = Paths.get(System.getProperty("user.home") ?: ".")

    fun installRoot(home: Path = userHome()): Path =
        home.resolve(".page-ide").resolve("lsp").resolve("kotlin-language-server-$VERSION")

    fun multiInstallBase(home: Path = userHome()): Path =
        home.resolve(".page-ide").resolve("lsp").resolve("kotlin-language-server")

    fun sanitizeLabel(label: String): String =
        label.replace(Regex("[\\\\/:*?\"<>|()\\s]+"), "_").trim('_').ifEmpty { "unnamed" }

    fun installRootFor(label: String, home: Path = userHome()): Path =
        multiInstallBase(home).resolve(sanitizeLabel(label))

    fun currentPointer(home: Path = userHome()): Path =
        multiInstallBase(home).resolve("CURRENT")

    fun activeLabel(home: Path = userHome()): String? {
        val pointer = currentPointer(home)
        return runCatching { Files.readString(pointer).trim().takeIf { it.isNotEmpty() } }.getOrNull()
    }

    fun setActiveLabel(label: String, home: Path = userHome()) {
        val pointer = currentPointer(home)
        Files.createDirectories(pointer.parent)
        Files.writeString(pointer, label)
    }

    fun readInstalledLabel(installDir: Path): String? {
        val labelFile = installDir.resolve("LABEL")
        return runCatching { Files.readString(labelFile).trim().takeIf { it.isNotEmpty() } }.getOrNull()
    }

    fun installedLabels(home: Path = userHome()): List<String> {
        val base = multiInstallBase(home)
        if (!Files.isDirectory(base)) return emptyList()
        val out = mutableListOf<String>()
        Files.list(base).use { stream ->
            for (path in stream) {
                if (!Files.isDirectory(path)) continue
                if (!isInstalled(path)) continue
                val label = readInstalledLabel(path) ?: continue
                out.add(label)
            }
        }
        return out
    }

    fun installRootForLabel(label: String, home: Path = userHome()): Path? {
        val base = multiInstallBase(home)
        if (!Files.isDirectory(base)) return null
        Files.list(base).use { stream ->
            for (path in stream) {
                if (!Files.isDirectory(path)) continue
                if (readInstalledLabel(path) == label) return path
            }
        }
        return null
    }

    fun uninstallLabel(label: String, home: Path = userHome()) {
        val wasActive = activeLabel(home) == label
        val root = installRootForLabel(label, home)
        if (root != null) {
            deleteRecursively(root)
        } else if (isInstalled(installRoot(home))) {
            deleteRecursively(installRoot(home))
        }
        if (wasActive) runCatching { Files.deleteIfExists(currentPointer(home)) }
    }

    fun isWindows(osName: String = System.getProperty("os.name") ?: ""): Boolean =
        osName.lowercase().contains("win")

    fun executableName(windows: Boolean = isWindows()): String =
        if (windows) "kotlin-language-server.bat" else "kotlin-language-server"

    fun isInstalled(root: Path = installRoot()): Boolean {
        if (!Files.isDirectory(root)) return false
        val bin = root.resolve("bin")
        if (!Files.isDirectory(bin)) return false
        return Files.exists(bin.resolve("kotlin-language-server.bat")) ||
            Files.exists(bin.resolve("kotlin-language-server"))
    }

    fun executable(root: Path = installRoot()): Path? {
        if (!isInstalled(root)) return null
        val name = executableName()
        val candidate = root.resolve("bin").resolve(name)
        if (Files.exists(candidate)) return candidate
        val fallback = if (isWindows()) root.resolve("bin").resolve("kotlin-language-server")
        else root.resolve("bin").resolve("kotlin-language-server.bat")
        return fallback.takeIf { Files.exists(it) }
    }

    fun install(
        target: Path = installRoot(),
        downloadUrl: String = DOWNLOAD_URL,
        onProgress: (Progress) -> Unit = {},
    ) {
        try {
            val tmpZip = Files.createTempFile("kls-", ".zip")
            try {
                download(downloadUrl, tmpZip, onProgress)
                onProgress(Progress.Extracting)
                installFromZip(tmpZip, target)
                val exe = executable(target)
                    ?: throw IOException("installed but no executable found under $target/bin/")
                onProgress(Progress.Done(exe))
            } finally {
                runCatching { Files.deleteIfExists(tmpZip) }
            }
        } catch (t: Throwable) {
            onProgress(Progress.Failed(t))
        }
    }

    fun installLabeled(
        label: String,
        downloadUrl: String,
        home: Path = userHome(),
        onProgress: (Progress) -> Unit = {},
    ) {
        try {
            val target = installRootFor(label, home)
            val tmpZip = Files.createTempFile("kls-", ".zip")
            try {
                download(downloadUrl, tmpZip, onProgress)
                onProgress(Progress.Extracting)
                installFromZip(tmpZip, target)
                Files.writeString(target.resolve("LABEL"), label)
                setActiveLabel(label, home)
                val exe = executable(target)
                    ?: throw IOException("installed but no executable found under $target/bin/")
                onProgress(Progress.Done(exe))
            } finally {
                runCatching { Files.deleteIfExists(tmpZip) }
            }
        } catch (t: Throwable) {
            onProgress(Progress.Failed(t))
        }
    }

    fun installFromZip(zipFile: Path, target: Path) {
        val stagingParent = target.parent ?: throw IOException("target has no parent: $target")
        Files.createDirectories(stagingParent)
        val staging = Files.createTempDirectory(stagingParent, "kls-staging-")
        try {
            ZipInputStream(Files.newInputStream(zipFile)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val segments = entry.name.split('/', '\\').filter { it.isNotEmpty() }
                    val flattened = segments.drop(1)
                    if (flattened.isEmpty()) {
                        zip.closeEntry()
                        continue
                    }
                    val outPath = flattened.fold(staging) { acc, s -> acc.resolve(s) }
                    val normalizedOut = outPath.normalize()
                    val normalizedStaging = staging.normalize()
                    if (!normalizedOut.startsWith(normalizedStaging) || normalizedOut == normalizedStaging) {
                        throw IOException("zip slip detected: ${entry.name}")
                    }
                    if (entry.isDirectory) {
                        Files.createDirectories(outPath)
                    } else {
                        Files.createDirectories(outPath.parent)
                        Files.newOutputStream(outPath).use { out -> zip.copyTo(out) }
                        runCatching { outPath.toFile().setExecutable(true, false) }
                    }
                    zip.closeEntry()
                }
            }
            if (Files.exists(target)) {
                deleteRecursively(target)
            }
            Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (t: Throwable) {
            runCatching { deleteRecursively(staging) }
            throw t
        }
    }

    private fun download(url: String, target: Path, onProgress: (Progress) -> Unit) {
        val conn = openConnection(url)
        try {
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: -1L
            conn.inputStream.use { input ->
                Files.newOutputStream(target).use { output ->
                    copyWithProgress(input, output, total, onProgress)
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun openConnection(url: String): HttpURLConnection {
        var current = url
        for (hop in 0..5) {
            val conn = URI(current).toURL().openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", "PAGE-IDE/0.1 KlsInstaller")
            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location") ?: throw IOException("redirect without Location: $code")
                conn.disconnect()
                current = if (loc.startsWith("http")) loc else URI(current).resolve(loc).toString()
                continue
            }
            if (code !in 200..299) {
                conn.disconnect()
                throw IOException("download failed: HTTP $code for $current")
            }
            return conn
        }
        throw IOException("too many redirects starting at $url")
    }

    private fun copyWithProgress(
        input: InputStream,
        output: java.io.OutputStream,
        total: Long,
        onProgress: (Progress) -> Unit,
    ) {
        val buf = ByteArray(64 * 1024)
        var read = 0L
        var lastReport = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            output.write(buf, 0, n)
            read += n
            if (read - lastReport >= 256 * 1024L || (total > 0 && read == total)) {
                onProgress(Progress.Downloading(read, total))
                lastReport = read
            }
        }
        if (lastReport != read) onProgress(Progress.Downloading(read, total))
    }

    private fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { p ->
                runCatching { Files.delete(p) }
            }
        }
    }
}
