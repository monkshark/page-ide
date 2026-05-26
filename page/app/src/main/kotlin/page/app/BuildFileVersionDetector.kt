package page.app

import java.nio.file.Files
import java.nio.file.Path

data class DetectedVersion(
    val runtime: String,
    val version: String,
    val source: String,
)

object BuildFileVersionDetector {

    fun detect(projectRoot: Path): List<DetectedVersion> {
        val results = mutableListOf<DetectedVersion>()
        detectJava(projectRoot)?.let(results::add)
        detectNode(projectRoot)?.let(results::add)
        detectPython(projectRoot)?.let(results::add)
        detectGo(projectRoot)?.let(results::add)
        detectRust(projectRoot)?.let(results::add)
        detectDotnet(projectRoot)?.let(results::add)
        return results
    }

    fun detectForRuntime(projectRoot: Path, runtime: String): DetectedVersion? = when (runtime) {
        "java", "jdk" -> detectJava(projectRoot)
        "js", "node" -> detectNode(projectRoot)
        "py", "python", "python-runtime" -> detectPython(projectRoot)
        "go", "go-sdk" -> detectGo(projectRoot)
        else -> null
    }

    private fun detectJava(root: Path): DetectedVersion? {
        readFile(root.resolve(".java-version"))?.trim()?.takeIf { it.isNotBlank() }?.let {
            return DetectedVersion("jdk", it, ".java-version")
        }
        readFile(root.resolve("pom.xml"))?.let { content ->
            val version = extractXmlProperty(content, "maven.compiler.source")
                ?: extractXmlProperty(content, "java.version")
                ?: extractXmlTag(content, "source")
            if (version != null) return DetectedVersion("jdk", version, "pom.xml")
        }
        readFile(root.resolve("build.gradle.kts"))?.let { content ->
            extractGradleVersion(content)?.let {
                return DetectedVersion("jdk", it, "build.gradle.kts")
            }
        }
        readFile(root.resolve("build.gradle"))?.let { content ->
            extractGradleVersion(content)?.let {
                return DetectedVersion("jdk", it, "build.gradle")
            }
        }
        return null
    }

    private fun detectNode(root: Path): DetectedVersion? {
        readFile(root.resolve(".node-version"))?.trim()?.takeIf { it.isNotBlank() }?.let {
            return DetectedVersion("node", it.removePrefix("v"), ".node-version")
        }
        readFile(root.resolve(".nvmrc"))?.trim()?.takeIf { it.isNotBlank() }?.let {
            return DetectedVersion("node", it.removePrefix("v"), ".nvmrc")
        }
        readFile(root.resolve("package.json"))?.let { content ->
            val match = Regex("\"node\"\\s*:\\s*\"[>=<^~]*\\s*(\\d+[\\d.]*)\"").find(content)
            if (match != null) return DetectedVersion("node", match.groupValues[1], "package.json")
        }
        return null
    }

    private fun detectPython(root: Path): DetectedVersion? {
        readFile(root.resolve(".python-version"))?.trim()?.takeIf { it.isNotBlank() }?.let {
            return DetectedVersion("python-runtime", it, ".python-version")
        }
        readFile(root.resolve("pyproject.toml"))?.let { content ->
            val match = Regex("requires-python\\s*=\\s*\"[>=<]*\\s*(\\d+\\.\\d+[\\d.]*)\"").find(content)
            if (match != null) return DetectedVersion("python-runtime", match.groupValues[1], "pyproject.toml")
        }
        readFile(root.resolve("runtime.txt"))?.trim()?.let { line ->
            val match = Regex("python-(\\d+\\.\\d+[\\d.]*)").find(line)
            if (match != null) return DetectedVersion("python-runtime", match.groupValues[1], "runtime.txt")
        }
        return null
    }

    private fun detectGo(root: Path): DetectedVersion? {
        readFile(root.resolve("go.mod"))?.let { content ->
            val match = Regex("(?m)^go\\s+(\\d+\\.\\d+[\\d.]*)").find(content)
            if (match != null) return DetectedVersion("go-sdk", match.groupValues[1], "go.mod")
        }
        return null
    }

    private fun detectRust(root: Path): DetectedVersion? {
        readFile(root.resolve("rust-toolchain.toml"))?.let { content ->
            val match = Regex("channel\\s*=\\s*\"(\\d+\\.\\d+[\\d.]*)\"").find(content)
            if (match != null) return DetectedVersion("rust", match.groupValues[1], "rust-toolchain.toml")
        }
        readFile(root.resolve("Cargo.toml"))?.let { content ->
            val match = Regex("rust-version\\s*=\\s*\"(\\d+\\.\\d+[\\d.]*)\"").find(content)
            if (match != null) return DetectedVersion("rust", match.groupValues[1], "Cargo.toml")
        }
        return null
    }

    private fun detectDotnet(root: Path): DetectedVersion? {
        readFile(root.resolve("global.json"))?.let { content ->
            val match = Regex("\"version\"\\s*:\\s*\"(\\d+\\.\\d+[\\d.]*)\"").find(content)
            if (match != null) return DetectedVersion("dotnet", match.groupValues[1], "global.json")
        }
        return null
    }

    private fun readFile(path: Path): String? = runCatching {
        if (Files.isRegularFile(path) && Files.size(path) < 512_000) Files.readString(path) else null
    }.getOrNull()

    private fun extractXmlProperty(xml: String, property: String): String? {
        val regex = Regex("<$property>\\s*(\\d+[\\d.]*)\\s*</$property>")
        return regex.find(xml)?.groupValues?.get(1)
    }

    private fun extractXmlTag(xml: String, tag: String): String? {
        val regex = Regex("<$tag>\\s*(\\d+[\\d.]*)\\s*</$tag>")
        return regex.find(xml)?.groupValues?.get(1)
    }

    private fun extractGradleVersion(content: String): String? {
        val patterns = listOf(
            Regex("jvmToolchain\\s*\\(?\\s*(\\d+)"),
            Regex("sourceCompatibility\\s*=\\s*['\"]?(\\d+[\\d.]*)['\"]?"),
            Regex("JavaVersion\\.VERSION_(\\d+)"),
            Regex("targetCompatibility\\s*=\\s*['\"]?(\\d+[\\d.]*)['\"]?"),
        )
        for (p in patterns) {
            p.find(content)?.groupValues?.get(1)?.let { return it }
        }
        return null
    }
}
