package page.runtime

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class NpmGlobalInstaller(
    val descriptor: NpmPackageDescriptor,
    private val npmFinder: () -> Path? = ::findNpmOnPath,
    private val processRunner: ProcessRunner = DefaultProcessRunner,
    private val manifestFetcher: (String) -> List<String>? = { slug -> LspStaticManifest.fetchNpmVersions(slug) },
) : LspInstaller {

    override val languageId: String = descriptor.languageId
    override val displayName: String = descriptor.displayName
    override val precheck: LspInstaller.Precheck = computePrecheck()

    override fun isInstalled(): Boolean = executable() != null

    override fun executable(): Path? {
        val root = installRoot()
        if (!Files.isDirectory(root)) return null
        val exe = root.resolve(binaryRelative(descriptor.binaryName))
        return exe.takeIf { Files.exists(it) }
    }

    override fun defaultVersion(): String? = descriptor.defaultVersion

    override fun installedVersion(): String? = currentInstalledVersion()

    override fun availableVersions(): List<String> {
        val manifest = manifestFetcher(descriptor.installKey)
        if (!manifest.isNullOrEmpty()) return manifest
        return runCatching {
            val npm = npmFinder() ?: return@runCatching emptyList<String>()
            val raw = processRunner.captureOutput(listOf(npm.toString(), "view", descriptor.packageName, "versions", "--json"))
            NpmVersionParser.parseVersions(raw)
        }.getOrDefault(emptyList())
    }

    override fun install(version: String?, onProgress: (LspInstaller.Progress) -> Unit) {
        try {
            val npm = npmFinder() ?: throw IOException("npm not found on PATH")
            val resolvedVersion = version ?: descriptor.defaultVersion ?: "latest"
            val target = installRoot(resolvedVersion)
            Files.createDirectories(target)
            val spec = if (resolvedVersion == "latest") descriptor.packageName else "${descriptor.packageName}@$resolvedVersion"
            val cmd = listOf(
                npm.toString(), "install",
                "--prefix", target.toString(),
                "--global",
                "--no-audit",
                "--no-fund",
                spec,
            )
            val exit = processRunner.runStreaming(cmd) { line ->
                onProgress(LspInstaller.Progress.CommandOutput(line))
            }
            if (exit != 0) throw IOException("npm install exit code $exit")
            val exe = target.resolve(binaryRelative(descriptor.binaryName))
            if (!Files.exists(exe)) throw IOException("npm install succeeded but binary missing: $exe")
            runCatching { exe.toFile().setExecutable(true, false) }
            writePointer(resolvedVersion)
            onProgress(LspInstaller.Progress.Done(exe))
        } catch (t: Throwable) {
            onProgress(LspInstaller.Progress.Failed(t))
        }
    }

    fun installRoot(version: String = currentInstalledVersion() ?: descriptor.defaultVersion ?: "latest"): Path =
        LspInstaller.lspHome().resolve(descriptor.installKey).resolve(sanitize(version))

    private fun currentInstalledVersion(): String? {
        val pointer = LspInstaller.lspHome().resolve(descriptor.installKey).resolve("CURRENT")
        return runCatching { Files.readString(pointer).trim().takeIf { it.isNotEmpty() } }.getOrNull()
    }

    private fun writePointer(version: String) {
        val pointer = LspInstaller.lspHome().resolve(descriptor.installKey).resolve("CURRENT")
        Files.createDirectories(pointer.parent)
        Files.writeString(pointer, version)
    }

    private fun computePrecheck(): LspInstaller.Precheck =
        if (npmFinder() != null) LspInstaller.Precheck.Ok
        else LspInstaller.Precheck.MissingTool(
            tool = "npm",
            installUrl = "https://nodejs.org/",
            message = "${descriptor.displayName} requires Node.js / npm to install. Install Node.js then retry.",
        )

    companion object {

        fun binaryRelative(binaryName: String, osName: String = System.getProperty("os.name") ?: ""): String =
            if (LspInstaller.isWindows(osName)) "$binaryName.cmd"
            else "bin/$binaryName"

        fun findNpmOnPath(): Path? = findOnPath(if (LspInstaller.isWindows()) listOf("npm.cmd", "npm.exe", "npm") else listOf("npm"))

        internal fun findOnPath(candidates: List<String>): Path? {
            val envPath = System.getenv("PATH") ?: return null
            for (segment in envPath.split(java.io.File.pathSeparatorChar)) {
                if (segment.isBlank()) continue
                val dir = runCatching { Paths.get(segment) }.getOrNull() ?: continue
                for (name in candidates) {
                    val candidate = dir.resolve(name)
                    if (Files.exists(candidate)) return candidate
                }
            }
            return null
        }

        private fun sanitize(version: String): String =
            version.replace(Regex("[\\\\/:*?\"<>|@]"), "_")
    }
}

data class NpmPackageDescriptor(
    val languageId: String,
    val displayName: String,
    val packageName: String,
    val binaryName: String,
    val defaultVersion: String? = null,
    val installKey: String = defaultInstallKey(packageName),
) {
    companion object {
        fun defaultInstallKey(packageName: String): String =
            packageName.removePrefix("@").replace("/", "-")
    }
}

interface ProcessRunner {
    fun runStreaming(command: List<String>, onLine: (String) -> Unit): Int
    fun runStreaming(command: List<String>, env: Map<String, String>, onLine: (String) -> Unit): Int =
        runStreaming(command, onLine)
    fun captureOutput(command: List<String>): String
}

object DefaultProcessRunner : ProcessRunner {

    override fun runStreaming(command: List<String>, onLine: (String) -> Unit): Int =
        runStreaming(command, emptyMap(), onLine)

    override fun runStreaming(command: List<String>, env: Map<String, String>, onLine: (String) -> Unit): Int {
        val pb = ProcessBuilder(command).redirectErrorStream(true)
        if (env.isNotEmpty()) pb.environment().putAll(env)
        val process = pb.start()
        BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8)).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                onLine(line)
            }
        }
        return process.waitFor()
    }

    override fun captureOutput(command: List<String>): String {
        val pb = ProcessBuilder(command).redirectErrorStream(true)
        val process = pb.start()
        val text = process.inputStream.bufferedReader(Charsets.UTF_8).readText()
        process.waitFor()
        return text
    }
}

object NpmVersionParser {

    fun parseVersions(json: String): List<String> {
        val trimmed = json.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (trimmed.startsWith("\"")) {
            val single = trimmed.removeSurrounding("\"")
            return if (single.isBlank()) emptyList() else listOf(single)
        }
        if (!trimmed.startsWith("[")) return emptyList()
        return trimmed.removeSurrounding("[", "]")
            .split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotBlank() }
            .reversed()
    }
}
