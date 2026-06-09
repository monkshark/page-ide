package page.lsp

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.isExecutable

class GenericLanguageBackend(
    private val definition: LanguageDefinition,
    private val executableFinder: () -> Path? = { null },
    private val userOverrideFinder: () -> Path? = { null },
    private val envSetup: ((MutableMap<String, String>) -> Unit)? = null,
    private val initializationOptionsProvider: ((Path?) -> Any?)? = null,
) : LanguageBackend {

    override val id: String = definition.id
    override val displayName: String = definition.displayName

    override fun supports(extension: String?): Boolean = definition.supports(extension)

    override fun resolveExecutable(env: Map<String, String>): LanguageBackend.Resolution {
        val attempted = mutableListOf<String>()
        val isWindows = System.getProperty("os.name").orEmpty().lowercase().contains("win")
        val binNames = if (isWindows) definition.lspWindowsBinaries.ifEmpty { definition.lspBinaries }
        else definition.lspBinaries

        val override = userOverrideFinder()
        if (override != null) {
            attempted += "override=$override"
            if (override.exists()) return LanguageBackend.Resolution.Found(override, "user override")
        }

        val installed = executableFinder()
        if (installed != null) {
            attempted += "installer=$installed"
            if (installed.exists()) return LanguageBackend.Resolution.Found(installed, "PAGE installer")
        }

        env["PATH"]?.let { pathEnv ->
            val sep = System.getProperty("path.separator") ?: ":"
            for (entry in pathEnv.split(sep)) {
                if (entry.isBlank()) continue
                for (name in binNames) {
                    val candidate = Paths.get(entry, name)
                    attempted += "PATH=$candidate"
                    if (candidate.exists() && (isWindows || candidate.isExecutable())) {
                        return LanguageBackend.Resolution.Found(candidate, "PATH")
                    }
                }
            }
        }

        return LanguageBackend.Resolution.NotFound(attempted.toList())
    }

    override fun spawn(
        executable: Path,
        workspaceRoot: Path?,
        onStderrLine: ((String) -> Unit)?,
        env: Map<String, String>,
    ): LspClient {
        val command = mutableListOf(executable.toAbsolutePath().toString())
        command += definition.launchArgs
        if (definition.id == "java" && workspaceRoot != null) {
            val dataDir = jdtlsDataDir(executable, workspaceRoot)
            Files.createDirectories(dataDir)
            command += listOf("-data", dataDir.toAbsolutePath().toString())
        }
        val builder = ProcessBuilder(command)
        if (workspaceRoot != null && Files.isDirectory(workspaceRoot)) {
            builder.directory(workspaceRoot.toFile())
        }
        builder.redirectErrorStream(false)
        builder.environment().putAll(env)
        envSetup?.invoke(builder.environment())
        val process = builder.start()
        val transport = if (onStderrLine != null) ProcessTransport(process, onStderrLine)
        else ProcessTransport(process)
        return LspClient(
            transport = transport,
            workspaceRoot = workspaceRoot,
            initializationOptions = initializationOptionsProvider?.invoke(workspaceRoot),
        )
    }
}

internal fun jdtlsDataDir(executable: Path, workspaceRoot: Path): Path {
    val root = workspaceRoot.toAbsolutePath().normalize()
    val hash = Integer.toHexString(root.toString().hashCode())
    val name = root.fileName?.toString()?.take(40)?.ifBlank { null } ?: "ws"
    return executable.toAbsolutePath().parent.resolve("ws-data").resolve("$name-$hash")
}
