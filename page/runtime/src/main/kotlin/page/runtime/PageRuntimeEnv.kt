package page.runtime

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

object PageRuntimeEnv {

    data class RuntimeEntry(
        val binDir: Path,
        val envVars: Map<String, String> = emptyMap(),
    )

    fun collectRuntimes(): List<RuntimeEntry> {
        val entries = mutableListOf<RuntimeEntry>()
        runCatching { JdkInstaller().javaHome() }.getOrNull()?.let { home ->
            entries += RuntimeEntry(
                binDir = home.resolve("bin"),
                envVars = mapOf("JAVA_HOME" to home.toAbsolutePath().toString()),
            )
        }
        runCatching { NodeInstaller().nodeHome() }.getOrNull()?.let { home ->
            val binDir = if (File(home.resolve("bin").toString()).isDirectory) home.resolve("bin") else home
            entries += RuntimeEntry(binDir = binDir)
        }
        runCatching { PythonInstaller().pythonHome() }.getOrNull()?.let { home ->
            val binDir = if (File(home.resolve("bin").toString()).isDirectory) home.resolve("bin") else home
            entries += RuntimeEntry(binDir = binDir)
        }
        runCatching { GoSdkInstaller().goHome() }.getOrNull()?.let { home ->
            entries += RuntimeEntry(
                binDir = home.resolve("bin"),
                envVars = mapOf("GOROOT" to home.toAbsolutePath().toString()),
            )
        }
        runCatching { CppToolchainInstaller().llvmHome() }.getOrNull()?.let { home ->
            entries += RuntimeEntry(binDir = home.resolve("bin"))
        }
        runCatching { MingwInstaller().includeRoot() }.getOrNull()?.let { include ->
            val root = include.parent ?: return@let
            val sep = System.getProperty("path.separator") ?: ";"
            entries += RuntimeEntry(
                binDir = root.resolve("bin"),
                envVars = mapOf(
                    "C_INCLUDE_PATH" to include.toAbsolutePath().toString(),
                    "CPLUS_INCLUDE_PATH" to (include.toAbsolutePath().toString() + sep + include.resolve("c++").toAbsolutePath().toString()),
                ),
            )
        }
        runCatching { RustToolchainInstaller().rustHome() }.getOrNull()?.let { home ->
            entries += RuntimeEntry(binDir = home.resolve("bin"))
        }
        runCatching { DotnetSdkInstaller().dotnetHome() }.getOrNull()?.let { home ->
            entries += RuntimeEntry(
                binDir = home,
                envVars = mapOf("DOTNET_ROOT" to home.toAbsolutePath().toString()),
            )
        }
        runCatching { SwiftToolchainInstaller().binDir() }.getOrNull()?.let { bin ->
            if (Files.isDirectory(bin)) entries += RuntimeEntry(binDir = bin)
        }
        return entries
    }

    fun applyTo(env: MutableMap<String, String>) {
        val runtimes = collectRuntimes()
        if (runtimes.isEmpty()) return
        for (rt in runtimes) {
            env.putAll(rt.envVars)
        }
        val sep = System.getProperty("path.separator") ?: ";"
        val pathKey = env.keys.firstOrNull { it.equals("PATH", ignoreCase = true) } ?: "PATH"
        val prependBins = runtimes.map { it.binDir.toAbsolutePath().toString() }
        val current = env[pathKey].orEmpty()
        env[pathKey] = (prependBins + current).joinToString(sep)
        applyWindowsSdkEnv(env, sep)
        applySwiftSdkEnv(env)
        runCatching { ensureSwiftCInterop() }
        runCatching { ensureClangdConfigForMingw() }
    }

    fun pinJavaRuntime(env: MutableMap<String, String>, minMajor: Int = 21) {
        val home = runCatching { JdkInstaller().javaHomeAtLeast(minMajor) }.getOrNull()
            ?: JavaRuntimeProbe.systemJavaHomeAtLeast(minMajor)
            ?: return
        pinJavaRuntime(env, home)
    }

    internal fun pinJavaRuntime(env: MutableMap<String, String>, home: Path) {
        val sep = System.getProperty("path.separator") ?: ";"
        env["JAVA_HOME"] = home.toAbsolutePath().toString()
        val pathKey = env.keys.firstOrNull { it.equals("PATH", ignoreCase = true) } ?: "PATH"
        val bin = home.resolve("bin").toAbsolutePath().toString()
        val current = env[pathKey].orEmpty()
        env[pathKey] = if (current.isEmpty()) bin else bin + sep + current
    }

    fun normalizeForLaunch(env: MutableMap<String, String>) {
        if (!LspInstaller.isWindows()) return
        collapseCaseInsensitiveDuplicates(env)
    }

    internal fun collapseCaseInsensitiveDuplicates(env: MutableMap<String, String>) {
        val byLower = env.keys.groupBy { it.lowercase() }
        for ((_, keys) in byLower) {
            if (keys.size <= 1) continue
            val winner = keys.maxByOrNull { (env[it] ?: "").length } ?: continue
            val value = env[winner]
            for (k in keys) if (k != winner) env.remove(k)
            if (value != null) env[winner] = value
        }
    }

    private fun applySwiftSdkEnv(env: MutableMap<String, String>) {
        val sdk = runCatching { SwiftToolchainInstaller().sdkRoot() }.getOrNull() ?: return
        env["SDKROOT"] = sdk.toAbsolutePath().toString()
    }

    private fun ensureSwiftCInterop() {
        val share = runCatching { SwiftToolchainInstaller().sdkShareDir() }.getOrNull() ?: return
        runCatching { WindowsSdkInstaller().ensureSwiftModulemaps(share) }
    }

    private fun applyWindowsSdkEnv(env: MutableMap<String, String>, sep: String) {
        val sdkEnv = runCatching { WindowsSdkInstaller().envVars() }.getOrNull() ?: return
        for ((key, value) in sdkEnv) {
            val existing = env[key]
            env[key] = if (existing.isNullOrBlank()) value else value + sep + existing
        }
    }

    private const val PAGE_MANAGED_MARKER = "# PAGE-managed clangd config (auto-generated)"

    fun ensureClangdConfigForMingw() {
        if (!LspInstaller.isWindows()) return
        val mingw = MingwInstaller()
        val ver = mingw.currentInstalledVersion() ?: return
        val gcc = mingw.gccBinary(ver)
        if (!Files.exists(gcc)) return

        val localAppData = System.getenv("LOCALAPPDATA") ?: return
        val configDir = File(localAppData, "clangd")
        val configFile = File(configDir, "config.yaml")

        val gccYaml = gcc.toAbsolutePath().toString().replace("\\", "/")
        val desired = """
            $PAGE_MANAGED_MARKER
            CompileFlags:
              Compiler: $gccYaml
        """.trimIndent() + "\n"

        if (configFile.exists()) {
            val existing = runCatching { configFile.readText() }.getOrNull() ?: return
            if (!existing.trimStart().startsWith(PAGE_MANAGED_MARKER)) return
            if (existing == desired) return
        }
        runCatching {
            if (!configDir.exists()) configDir.mkdirs()
            configFile.writeText(desired)
        }
    }
}
