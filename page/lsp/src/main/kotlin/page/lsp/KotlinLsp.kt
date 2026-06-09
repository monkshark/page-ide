package page.lsp

import com.google.gson.JsonObject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.isExecutable

object KotlinLsp {

    fun inlayHintsSettings(
        typeHints: Boolean = true,
        parameterHints: Boolean = true,
        chainedHints: Boolean = true,
    ): JsonObject {
        val inlay = JsonObject().apply {
            addProperty("typeHints", typeHints)
            addProperty("parameterHints", parameterHints)
            addProperty("chainedHints", chainedHints)
        }
        val kotlinSection = JsonObject().apply { add("inlayHints", inlay) }
        return JsonObject().apply { add("kotlin", kotlinSection) }
    }

    private const val PATH_OVERRIDE_PROP = "page.lsp.kotlin.path"
    private const val RESOURCES_PROP = "compose.application.resources.dir"
    private const val DISABLE_DEV_PROP = "page.lsp.kotlin.disableDev"
    private const val USER_INSTALL_PROP = "page.lsp.kotlin.userInstall"
    private const val USER_INSTALL_VERSION = "1.3.13-page-1"

    sealed class Resolution {
        data class Found(val executable: Path, val origin: String) : Resolution()
        data class NotFound(val attempted: List<String>) : Resolution()
    }

    fun resolveExecutable(
        env: Map<String, String> = System.getenv(),
        home: Path = Paths.get(System.getProperty("user.home") ?: "."),
    ): Resolution {
        val attempted = mutableListOf<String>()
        val isWindows = System.getProperty("os.name").orEmpty().lowercase().contains("win")
        val binNames = if (isWindows) listOf("kotlin-language-server.bat", "kotlin-language-server")
        else listOf("kotlin-language-server")

        System.getProperty(PATH_OVERRIDE_PROP)?.takeIf { it.isNotBlank() }?.let { override ->
            val p = Paths.get(override)
            attempted += "override=$override"
            if (p.exists()) return Resolution.Found(p, "system property $PATH_OVERRIDE_PROP")
        }

        System.getProperty(RESOURCES_PROP)?.takeIf { it.isNotBlank() }?.let { resDir ->
            val base = Paths.get(resDir, "lsp", "server", "bin")
            attempted += "bundled=$base"
            for (name in binNames) {
                val candidate = base.resolve(name)
                if (candidate.exists()) return Resolution.Found(candidate, "bundled (compose resources)")
            }
        }

        userInstallRoot(home)?.let { root ->
            val base = root.resolve("bin")
            attempted += "user-install=$base"
            for (name in binNames) {
                val candidate = base.resolve(name)
                if (candidate.exists()) return Resolution.Found(candidate, "user install")
            }
        }

        val devDisabled = System.getProperty(DISABLE_DEV_PROP)?.toBooleanStrictOrNull() == true
        if (!devDisabled) {
            val pageRoot = locateProjectRoot()
            if (pageRoot != null) {
                val devBase = pageRoot.resolve("page/app/build/composeResources/common/lsp/server/bin")
                attempted += "dev=$devBase"
                for (name in binNames) {
                    val candidate = devBase.resolve(name)
                    if (candidate.exists()) return Resolution.Found(candidate, "dev build dir")
                }
            }
        }

        env["PATH"]?.let { pathEnv ->
            val sep = System.getProperty("path.separator") ?: ":"
            for (entry in pathEnv.split(sep)) {
                if (entry.isBlank()) continue
                for (name in binNames) {
                    val candidate = Paths.get(entry, name)
                    attempted += "PATH=$candidate"
                    if (candidate.exists() && (isWindows || candidate.isExecutable())) {
                        return Resolution.Found(candidate, "PATH")
                    }
                }
            }
        }

        return Resolution.NotFound(attempted.toList())
    }

    fun spawn(
        executable: Path,
        workspaceRoot: Path? = null,
        onStderrLine: ((String) -> Unit)? = null,
        env: Map<String, String> = System.getenv(),
    ): LspClient {
        val builder = ProcessBuilder(executable.toAbsolutePath().toString())
        if (workspaceRoot != null && Files.isDirectory(workspaceRoot)) {
            builder.directory(workspaceRoot.toFile())
        }
        builder.environment().putAll(env)
        val javaHome = System.getProperty("java.home")
        if (!javaHome.isNullOrBlank()) {
            builder.environment()["JAVA_HOME"] = javaHome
        }
        prependGradleToPath(builder)
        builder.redirectErrorStream(false)
        val process = builder.start()
        val transport = if (onStderrLine != null) ProcessTransport(process, onStderrLine)
        else ProcessTransport(process)
        return LspClient(transport, workspaceRoot, initialSettings = inlayHintsSettings())
    }

    private fun prependGradleToPath(builder: ProcessBuilder) {
        val gradleBin = locateGradleBin() ?: return
        val env = builder.environment()
        val sep = System.getProperty("path.separator") ?: ";"
        val pathKey = env.keys.firstOrNull { it.equals("PATH", ignoreCase = true) } ?: "PATH"
        val current = env[pathKey].orEmpty()
        env[pathKey] = if (current.isBlank()) gradleBin.toString() else "$gradleBin$sep$current"
    }

    private fun locateGradleBin(): Path? {
        System.getProperty("page.lsp.gradle.bin")?.takeIf { it.isNotBlank() }?.let {
            val p = Paths.get(it)
            if (p.exists()) return p
        }
        val home = System.getProperty("user.home") ?: return null
        val dists = Paths.get(home, ".gradle", "wrapper", "dists")
        if (!dists.exists()) return null
        val candidates = mutableListOf<Path>()
        Files.walk(dists, 4).use { stream ->
            stream.forEach { p ->
                val name = p.fileName?.toString()
                if (name != null && name == "bin" && p.parent != null) {
                    val gradleBat = p.resolve("gradle.bat")
                    val gradleSh = p.resolve("gradle")
                    if (gradleBat.exists() || gradleSh.exists()) {
                        candidates.add(p)
                    }
                }
            }
        }
        return candidates.maxByOrNull { Files.getLastModifiedTime(it).toMillis() }
    }

    fun userInstallRoot(home: Path = Paths.get(System.getProperty("user.home") ?: ".")): Path? {
        System.getProperty(USER_INSTALL_PROP)?.takeIf { it.isNotBlank() }?.let {
            val p = Paths.get(it)
            return if (p.exists()) p else null
        }
        val multiBase = home.resolve(".page-ide").resolve("lsp").resolve("kotlin-language-server")
        val pointer = multiBase.resolve("CURRENT")
        if (pointer.exists()) {
            val label = runCatching { Files.readString(pointer).trim() }.getOrNull()
            if (!label.isNullOrEmpty() && Files.isDirectory(multiBase)) {
                val matched = runCatching {
                    Files.list(multiBase).use { stream ->
                        stream.filter { Files.isDirectory(it) }
                            .filter { dir ->
                                val labelFile = dir.resolve("LABEL")
                                Files.exists(labelFile) &&
                                    runCatching { Files.readString(labelFile).trim() == label }.getOrDefault(false)
                            }
                            .findFirst()
                            .orElse(null)
                    }
                }.getOrNull()
                if (matched != null) return matched
            }
        }
        val legacy = home.resolve(".page-ide").resolve("lsp").resolve("kotlin-language-server-$USER_INSTALL_VERSION")
        return if (legacy.exists()) legacy else null
    }

    private fun locateProjectRoot(): Path? {
        val cwd = Paths.get("").toAbsolutePath()
        var dir: Path? = cwd
        repeat(6) {
            val current = dir ?: return null
            if (current.resolve("settings.gradle.kts").exists()) return current
            dir = current.parent
        }
        return null
    }
}
