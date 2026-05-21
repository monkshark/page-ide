package page.lsp

import java.nio.file.Path

object KotlinLanguageBackend : LanguageBackend {
    override val id: String = "kotlin"
    override val displayName: String = "kotlin-language-server"

    override fun supports(extension: String?): Boolean = when (extension?.lowercase()) {
        "kt", "kts" -> true
        else -> false
    }

    override fun resolveExecutable(env: Map<String, String>): LanguageBackend.Resolution {
        return when (val r = KotlinLsp.resolveExecutable(env)) {
            is KotlinLsp.Resolution.Found -> LanguageBackend.Resolution.Found(r.executable, r.origin)
            is KotlinLsp.Resolution.NotFound -> LanguageBackend.Resolution.NotFound(r.attempted)
        }
    }

    override fun spawn(
        executable: Path,
        workspaceRoot: Path?,
        onStderrLine: ((String) -> Unit)?,
    ): LspClient = KotlinLsp.spawn(executable, workspaceRoot, onStderrLine)
}
