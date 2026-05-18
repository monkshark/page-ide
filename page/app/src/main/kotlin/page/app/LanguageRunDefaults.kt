package page.app

import java.nio.file.Path

data class LanguageRunTemplate(
    val displayName: String,
    val extensions: Set<String>,
    val command: String,
    val argTemplate: List<String>,
)

object LanguageRunDefaults {

    private const val FILE_TOKEN = "{file}"
    private const val NAME_TOKEN = "{name}"
    private const val DIR_TOKEN = "{dir}"

    val templates: List<LanguageRunTemplate> = listOf(
        LanguageRunTemplate(
            displayName = "Python",
            extensions = setOf("py"),
            command = "python",
            argTemplate = listOf(FILE_TOKEN),
        ),
        LanguageRunTemplate(
            displayName = "Node.js",
            extensions = setOf("js", "mjs", "cjs"),
            command = "node",
            argTemplate = listOf(FILE_TOKEN),
        ),
        LanguageRunTemplate(
            displayName = "TypeScript (ts-node)",
            extensions = setOf("ts"),
            command = "npx",
            argTemplate = listOf("ts-node", FILE_TOKEN),
        ),
        LanguageRunTemplate(
            displayName = "Kotlin (kotlinc)",
            extensions = setOf("kt", "kts"),
            command = "kotlinc",
            argTemplate = listOf("-script", FILE_TOKEN),
        ),
        LanguageRunTemplate(
            displayName = "Java (single-file)",
            extensions = setOf("java"),
            command = "java",
            argTemplate = listOf(FILE_TOKEN),
        ),
        LanguageRunTemplate(
            displayName = "Go (run)",
            extensions = setOf("go"),
            command = "go",
            argTemplate = listOf("run", FILE_TOKEN),
        ),
        LanguageRunTemplate(
            displayName = "Rust (cargo run)",
            extensions = setOf("rs"),
            command = "cargo",
            argTemplate = listOf("run"),
        ),
        LanguageRunTemplate(
            displayName = "Bash",
            extensions = setOf("sh", "bash"),
            command = "bash",
            argTemplate = listOf(FILE_TOKEN),
        ),
    )

    fun forExtension(ext: String): LanguageRunTemplate? {
        val key = ext.lowercase().trimStart('.')
        return templates.firstOrNull { key in it.extensions }
    }

    fun forFile(path: Path): LanguageRunTemplate? {
        val name = path.fileName?.toString() ?: return null
        val dot = name.lastIndexOf('.')
        if (dot < 0 || dot == name.lastIndex) return null
        return forExtension(name.substring(dot + 1))
    }

    fun buildConfig(path: Path, workspaceRoot: Path?): RunConfig? {
        val template = forFile(path) ?: return null
        val fileName = path.fileName?.toString() ?: return null
        val baseName = fileName.substringBeforeLast('.', fileName)
        val resolvedArgs = template.argTemplate.map { token ->
            when (token) {
                FILE_TOKEN -> path.toString()
                NAME_TOKEN -> baseName
                DIR_TOKEN -> path.parent?.toString() ?: ""
                else -> token
            }
        }
        val cwd = workspaceRoot?.toString() ?: path.parent?.toString()
        val id = "auto-${template.command}-${baseName}-${System.nanoTime()}"
        return RunConfig(
            id = id,
            name = "${template.displayName} · $fileName",
            command = template.command,
            args = resolvedArgs,
            workingDir = cwd,
        )
    }
}
