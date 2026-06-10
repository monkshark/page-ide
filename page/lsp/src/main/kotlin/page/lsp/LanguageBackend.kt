package page.lsp

import java.nio.file.Path

interface LanguageBackend {
    val id: String
    val displayName: String
    val lspLanguageId: String get() = id

    fun supports(extension: String?): Boolean

    fun resolveExecutable(env: Map<String, String> = System.getenv()): Resolution

    fun spawn(
        executable: Path,
        workspaceRoot: Path? = null,
        onStderrLine: ((String) -> Unit)? = null,
        env: Map<String, String> = System.getenv(),
    ): LspClient

    sealed class Resolution {
        data class Found(val executable: Path, val origin: String) : Resolution()
        data class NotFound(val attempted: List<String>) : Resolution()
    }
}

object LspBackends {
    private val registry = mutableListOf<LanguageBackend>()

    @Volatile
    var routingInterceptor: ((path: Path, workspaceRoot: Path?) -> LanguageBackend?)? = null

    @Synchronized
    fun register(backend: LanguageBackend) {
        if (registry.none { it.id == backend.id }) registry += backend
    }

    @Synchronized
    fun all(): List<LanguageBackend> = registry.toList()

    @Synchronized
    fun forExtension(extension: String?): LanguageBackend? =
        registry.firstOrNull { it.supports(extension) }

    @Synchronized
    fun allForExtension(extension: String?): List<LanguageBackend> =
        registry.filter { it.supports(extension) }

    fun forFile(path: Path, workspaceRoot: Path?): LanguageBackend? {
        routingInterceptor?.invoke(path, workspaceRoot)?.let { return it }
        return forExtension(extensionOf(path))
    }

    @Synchronized
    fun byId(id: String): LanguageBackend? = registry.firstOrNull { it.id == id }

    private fun extensionOf(path: Path): String? {
        val name = path.fileName?.toString() ?: return null
        val dot = name.lastIndexOf('.')
        return if (dot >= 0 && dot < name.length - 1) name.substring(dot + 1) else null
    }

    init {
        register(KotlinLanguageBackend)
    }
}
