package page.app

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class ShellPackageInstaller(
    val descriptor: ShellPackageDescriptor,
    private val managerFinder: () -> Path? = { findManagerOnPath(descriptor.managerName) },
    private val binaryFinder: () -> Path? = { findManagedBinary(descriptor) },
    private val processRunner: ProcessRunner = DefaultProcessRunner,
) : LspInstaller {

    override val languageId: String = descriptor.languageId
    override val displayName: String = descriptor.displayName
    override val precheck: LspInstaller.Precheck = computePrecheck()
    override val heavyInstall: LspInstaller.HeavyInstallEstimate? = descriptor.heavyInstall

    override fun isInstalled(): Boolean = executable() != null

    override fun executable(): Path? {
        if (markerPresent()) {
            val resolved = binaryFinder() ?: return null
            return resolved.takeIf { Files.exists(it) }
        }
        return null
    }

    override fun defaultVersion(): String? = descriptor.defaultVersion

    override fun installedVersion(): String? = runCatching {
        Files.readString(markerFile()).trim().takeIf { it.isNotEmpty() }
    }.getOrNull()

    override fun install(version: String?, onProgress: (LspInstaller.Progress) -> Unit) {
        try {
            val manager = managerFinder() ?: throw IOException("${descriptor.managerName} not found on PATH")
            val resolvedVersion = version ?: descriptor.defaultVersion ?: "latest"
            val cmd = descriptor.buildInstallCommand(manager.toString(), descriptor.packageName, resolvedVersion)
            val exit = processRunner.runStreaming(cmd) { line ->
                onProgress(LspInstaller.Progress.CommandOutput(line))
            }
            if (exit != 0) throw IOException("${descriptor.managerName} install exit code $exit")
            val exe = binaryFinder() ?: throw IOException("installed but ${descriptor.binaryName} not found")
            runCatching { exe.toFile().setExecutable(true, false) }
            writeMarker(resolvedVersion)
            onProgress(LspInstaller.Progress.Done(exe))
        } catch (t: Throwable) {
            onProgress(LspInstaller.Progress.Failed(t))
        }
    }

    private fun computePrecheck(): LspInstaller.Precheck =
        if (managerFinder() != null) LspInstaller.Precheck.Ok
        else LspInstaller.Precheck.MissingTool(
            tool = descriptor.managerName,
            installUrl = descriptor.managerInstallUrl,
            message = "${descriptor.displayName} requires ${descriptor.managerName}. Install ${descriptor.managerName} then retry.",
        )

    private fun markerFile(): Path =
        LspInstaller.lspHome().resolve(descriptor.languageId).resolve("INSTALLED")

    private fun markerPresent(): Boolean = Files.exists(markerFile())

    private fun writeMarker(version: String) {
        val file = markerFile()
        Files.createDirectories(file.parent)
        Files.writeString(file, version)
    }

    companion object {

        fun findManagerOnPath(managerName: String, osName: String = System.getProperty("os.name") ?: ""): Path? {
            val candidates = if (LspInstaller.isWindows(osName))
                listOf("$managerName.cmd", "$managerName.bat", "$managerName.exe", managerName)
            else listOf(managerName)
            return NpmGlobalInstaller.findOnPath(candidates)
        }

        fun findManagedBinary(descriptor: ShellPackageDescriptor, osName: String = System.getProperty("os.name") ?: ""): Path? {
            val isWindows = LspInstaller.isWindows(osName)
            val exeCandidates = if (isWindows)
                listOf("${descriptor.binaryName}.cmd", "${descriptor.binaryName}.bat", "${descriptor.binaryName}.exe", descriptor.binaryName)
            else listOf(descriptor.binaryName)
            NpmGlobalInstaller.findOnPath(exeCandidates)?.let { return it }
            for (extra in extraSearchDirs(descriptor.managerName)) {
                for (name in exeCandidates) {
                    val candidate = extra.resolve(name)
                    if (Files.exists(candidate)) return candidate
                }
            }
            return null
        }

        private fun extraSearchDirs(managerName: String): List<Path> {
            val home = LspInstaller.userHome()
            return when (managerName) {
                "go" -> {
                    val gopath = System.getenv("GOPATH")?.let { Paths.get(it).resolve("bin") }
                    listOfNotNull(gopath, home.resolve("go").resolve("bin"))
                }
                "dotnet" -> listOf(home.resolve(".dotnet").resolve("tools"))
                "ghcup" -> listOf(home.resolve(".ghcup").resolve("bin"))
                "cargo" -> listOf(home.resolve(".cargo").resolve("bin"))
                "opam" -> {
                    val opamRoot = System.getenv("OPAMROOT")?.let { Paths.get(it) } ?: home.resolve(".opam")
                    listOf(opamRoot.resolve("default").resolve("bin"))
                }
                "gem" -> listOf(home.resolve(".gem").resolve("bin"))
                else -> emptyList()
            }
        }
    }
}

data class ShellPackageDescriptor(
    val languageId: String,
    val displayName: String,
    val managerName: String,
    val managerInstallUrl: String,
    val binaryName: String,
    val packageName: String,
    val defaultVersion: String? = null,
    val heavyInstall: LspInstaller.HeavyInstallEstimate? = null,
    val buildInstallCommand: (manager: String, pkg: String, version: String) -> List<String>,
)

class ToolchainDetectInstaller(
    override val languageId: String,
    override val displayName: String,
    val managerName: String,
    private val managerInstallUrl: String,
    private val binaryFinder: () -> Path? = { ShellPackageInstaller.findManagerOnPath(managerName) },
) : LspInstaller {

    override val precheck: LspInstaller.Precheck =
        if (binaryFinder() != null) LspInstaller.Precheck.Ok
        else LspInstaller.Precheck.MissingTool(
            tool = managerName,
            installUrl = managerInstallUrl,
            message = "$displayName ships with its toolchain. Install the SDK then retry.",
        )

    override fun isInstalled(): Boolean = executable() != null

    override fun executable(): Path? = binaryFinder()

    override fun defaultVersion(): String? = null

    override fun installedVersion(): String? = if (binaryFinder() != null) "system" else null

    override fun install(version: String?, onProgress: (LspInstaller.Progress) -> Unit) {
        val exe = binaryFinder()
        if (exe != null) {
            onProgress(LspInstaller.Progress.Done(exe))
        } else {
            onProgress(LspInstaller.Progress.Failed(
                IOException("$displayName not detected. Install the SDK from $managerInstallUrl and reopen PAGE."),
            ))
        }
    }
}
