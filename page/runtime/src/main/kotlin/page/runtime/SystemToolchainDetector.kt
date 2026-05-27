package page.runtime

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

data class SystemToolchain(
    val vendor: String,
    val version: String,
    val path: Path,
    val compilerPath: Path,
)

object SystemToolchainDetector {

    fun detect(): List<SystemToolchain> {
        val os = LspInstaller.osKey()
        return when (os) {
            "windows" -> detectWindows()
            "macos" -> detectMacos()
            else -> detectLinux()
        }
    }

    private fun detectWindows(): List<SystemToolchain> {
        val results = mutableListOf<SystemToolchain>()
        detectMsvc()?.let(results::add)
        detectSystemClang("windows")?.let(results::add)
        detectSystemGcc("windows")?.let(results::add)
        return results
    }

    private fun detectMacos(): List<SystemToolchain> {
        val results = mutableListOf<SystemToolchain>()
        detectXcodeClang()?.let(results::add)
        detectSystemClang("macos")?.let(results::add)
        detectSystemGcc("macos")?.let(results::add)
        return results
    }

    private fun detectLinux(): List<SystemToolchain> {
        val results = mutableListOf<SystemToolchain>()
        detectSystemClang("linux")?.let(results::add)
        detectSystemGcc("linux")?.let(results::add)
        return results
    }

    private fun detectMsvc(): SystemToolchain? = runCatching {
        val vswhere = listOfNotNull(
            System.getenv("ProgramFiles(x86)")?.let { "$it\\Microsoft Visual Studio\\Installer\\vswhere.exe" },
            System.getenv("ProgramFiles")?.let { "$it\\Microsoft Visual Studio\\Installer\\vswhere.exe" },
        ).firstOrNull { File(it).exists() } ?: return@runCatching null

        val p = ProcessBuilder(vswhere, "-latest", "-property", "installationPath")
            .redirectErrorStream(true).start()
        val installPath = p.inputStream.bufferedReader().use { it.readLine()?.trim() }
        p.waitFor()
        if (installPath.isNullOrBlank()) return@runCatching null

        val vsPath = Paths.get(installPath)
        val cl = findInTree(vsPath.resolve("VC\\Tools\\MSVC"), "cl.exe")
            ?: return@runCatching null

        val verP = ProcessBuilder(vswhere, "-latest", "-property", "installationVersion")
            .redirectErrorStream(true).start()
        val version = verP.inputStream.bufferedReader().use { it.readLine()?.trim() } ?: "unknown"
        verP.waitFor()

        SystemToolchain("msvc", version, vsPath, cl)
    }.getOrNull()

    private fun detectXcodeClang(): SystemToolchain? = runCatching {
        val p = ProcessBuilder("xcrun", "--find", "clang").redirectErrorStream(true).start()
        val clangPath = p.inputStream.bufferedReader().use { it.readLine()?.trim() }
        p.waitFor()
        if (clangPath.isNullOrBlank() || !File(clangPath).exists()) return@runCatching null

        val vp = ProcessBuilder("xcrun", "clang", "--version").redirectErrorStream(true).start()
        val versionLine = vp.inputStream.bufferedReader().use { it.readLine()?.trim() } ?: "unknown"
        vp.waitFor()
        val version = Regex("(\\d+\\.\\d+\\.\\d+)").find(versionLine)?.groupValues?.get(1) ?: "unknown"

        SystemToolchain("apple-clang", version, Paths.get(clangPath).parent.parent, Paths.get(clangPath))
    }.getOrNull()

    private fun detectSystemClang(os: String): SystemToolchain? = runCatching {
        val name = if (os == "windows") "clang.exe" else "clang"
        val path = findOnPath(name) ?: return@runCatching null
        val version = captureVersion(path, "--version")
        SystemToolchain("clang", version ?: "unknown", Path.of(path).parent, Path.of(path))
    }.getOrNull()

    private fun detectSystemGcc(os: String): SystemToolchain? = runCatching {
        val name = if (os == "windows") "gcc.exe" else "gcc"
        val path = findOnPath(name) ?: return@runCatching null
        val version = captureVersion(path, "--version")
        SystemToolchain("gcc", version ?: "unknown", Path.of(path).parent, Path.of(path))
    }.getOrNull()

    private fun captureVersion(executable: String, vararg args: String): String? = runCatching {
        val p = ProcessBuilder(executable, *args).redirectErrorStream(true).start()
        val line = p.inputStream.bufferedReader().use { it.readLine()?.trim() }
        p.waitFor()
        Regex("(\\d+\\.\\d+\\.\\d+)").find(line ?: "")?.groupValues?.get(1)
    }.getOrNull()

    private fun findOnPath(name: String): String? {
        val pathEnv = System.getenv("PATH") ?: return null
        for (dir in pathEnv.split(File.pathSeparator)) {
            val f = File(dir, name)
            if (f.exists()) return f.absolutePath
        }
        return null
    }

    private fun findInTree(root: Path, name: String): Path? {
        if (!Files.isDirectory(root)) return null
        return runCatching {
            Files.walk(root).use { stream ->
                stream.filter { it.fileName.toString().equals(name, ignoreCase = true) && Files.isRegularFile(it) }
                    .findFirst().orElse(null)
            }
        }.getOrNull()
    }
}
