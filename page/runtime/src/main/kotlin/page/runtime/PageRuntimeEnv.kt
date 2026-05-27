package page.runtime

import java.io.File
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
        runCatching { RustToolchainInstaller().rustHome() }.getOrNull()?.let { home ->
            entries += RuntimeEntry(binDir = home.resolve("bin"))
        }
        runCatching { DotnetSdkInstaller().dotnetHome() }.getOrNull()?.let { home ->
            entries += RuntimeEntry(
                binDir = home,
                envVars = mapOf("DOTNET_ROOT" to home.toAbsolutePath().toString()),
            )
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
    }
}
