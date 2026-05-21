package page.lsp

import java.nio.file.Path

interface LanguageBackend {
    val id: String
    val displayName: String

    fun supports(extension: String?): Boolean

    fun resolveExecutable(env: Map<String, String> = System.getenv()): Resolution

    fun spawn(
        executable: Path,
        workspaceRoot: Path? = null,
        onStderrLine: ((String) -> Unit)? = null,
    ): LspClient

    sealed class Resolution {
        data class Found(val executable: Path, val origin: String) : Resolution()
        data class NotFound(val attempted: List<String>) : Resolution()
    }
}

object LspBackends {
    private val registry = mutableListOf<LanguageBackend>()

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
    fun byId(id: String): LanguageBackend? = registry.firstOrNull { it.id == id }

    init {
        register(KotlinLanguageBackend)
    }
}
