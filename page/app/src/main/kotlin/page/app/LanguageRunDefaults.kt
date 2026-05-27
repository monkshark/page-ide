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
            displayName = "C (clang)",
            extensions = setOf("c"),
            command = "clang",
            argTemplate = listOf(FILE_TOKEN, "-o", NAME_TOKEN, "&&", NAME_TOKEN),
        ),
        LanguageRunTemplate(
            displayName = "C++ (clang++)",
            extensions = setOf("cpp", "cc", "cxx"),
            command = "clang++",
            argTemplate = listOf(FILE_TOKEN, "-o", NAME_TOKEN, "&&", NAME_TOKEN),
        ),
        LanguageRunTemplate(
            displayName = "Rust (cargo run)",
            extensions = setOf("rs"),
            command = "cargo",
            argTemplate = listOf("run"),
        ),
        LanguageRunTemplate(
            displayName = "C# (dotnet run)",
            extensions = setOf("cs"),
            command = "dotnet",
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

    private fun resolveJdkEnv(template: LanguageRunTemplate): Pair<String, Map<String, String>>? {
        if ("java" in template.extensions) {
            val jdk = runCatching { JdkInstaller() }.getOrNull() ?: return null
            val home = jdk.javaHome() ?: return null
            val javaBin = home.resolve("bin").resolve(if (LspInstaller.isWindows()) "java.exe" else "java")
            if (!java.nio.file.Files.exists(javaBin)) return null
            return javaBin.toAbsolutePath().toString() to mapOf("JAVA_HOME" to home.toAbsolutePath().toString())
        }
        if ("js" in template.extensions || "mjs" in template.extensions) {
            val node = runCatching { NodeInstaller() }.getOrNull() ?: return null
            val home = node.nodeHome() ?: return null
            val bin = node.executable() ?: return null
            return bin.toAbsolutePath().toString() to mapOf("NODE_HOME" to home.toAbsolutePath().toString())
        }
        if ("py" in template.extensions) {
            val py = runCatching { PythonInstaller() }.getOrNull() ?: return null
            val bin = py.executable() ?: return null
            return bin.toAbsolutePath().toString() to emptyMap()
        }
        if ("go" in template.extensions) {
            val go = runCatching { GoSdkInstaller() }.getOrNull() ?: return null
            val home = go.goHome() ?: return null
            val bin = go.executable() ?: return null
            return bin.toAbsolutePath().toString() to mapOf("GOROOT" to home.toAbsolutePath().toString())
        }
        if ("c" in template.extensions || "cpp" in template.extensions) {
            val llvm = runCatching { CppToolchainInstaller() }.getOrNull() ?: return null
            val home = llvm.llvmHome() ?: return null
            val name = if ("cpp" in template.extensions || "cc" in template.extensions || "cxx" in template.extensions) {
                if (LspInstaller.isWindows()) "clang++.exe" else "clang++"
            } else {
                if (LspInstaller.isWindows()) "clang.exe" else "clang"
            }
            val bin = home.resolve("bin").resolve(name)
            if (!java.nio.file.Files.exists(bin)) return null
            return bin.toAbsolutePath().toString() to emptyMap()
        }
        if ("rs" in template.extensions) {
            val rust = runCatching { RustToolchainInstaller() }.getOrNull() ?: return null
            val home = rust.rustHome() ?: return null
            val bin = rust.executable() ?: return null
            return bin.toAbsolutePath().toString() to mapOf("PATH" to home.resolve("bin").toAbsolutePath().toString() + java.io.File.pathSeparator + (System.getenv("PATH") ?: ""))
        }
        if ("cs" in template.extensions) {
            val dotnet = runCatching { DotnetSdkInstaller() }.getOrNull() ?: return null
            val home = dotnet.dotnetHome() ?: return null
            val bin = dotnet.executable() ?: return null
            return bin.toAbsolutePath().toString() to mapOf("DOTNET_ROOT" to home.toAbsolutePath().toString())
        }
        return null
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
        val jdkEnv = resolveJdkEnv(template)
        return RunConfig(
            id = id,
            name = "${template.displayName} · $fileName",
            command = jdkEnv?.first ?: template.command,
            args = resolvedArgs,
            workingDir = cwd,
            env = jdkEnv?.second ?: emptyMap(),
        )
    }
}
