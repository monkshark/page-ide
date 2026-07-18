package page.runtime

import java.nio.file.Files
import java.nio.file.Path

object ProjectRecognizer {

    private const val MODULE_LIMIT = 64

    private val skipDirs = setOf(
        "node_modules", "target", "build", "dist", "out",
        ".git", ".dart_tool", ".gradle", ".idea", ".page-ide",
    )

    private val gradleMarkers = listOf("settings.gradle", "settings.gradle.kts", "build.gradle", "build.gradle.kts")
    private val pythonMarkers = listOf("pyproject.toml", "setup.py", "requirements.txt")

    fun recognize(root: Path, maxDepth: Int = 3): ProjectProfile {
        val normRoot = runCatching { root.toAbsolutePath().normalize() }.getOrNull() ?: return ProjectProfile.EMPTY
        if (!Files.isDirectory(normRoot)) return ProjectProfile.EMPTY

        val rootKind = classify(normRoot)
        val modules = mutableListOf<ProjectModule>()
        scanChildren(normRoot, 1, maxDepth, modules)

        val primaryKind = if (rootKind != ProjectKind.UNKNOWN) {
            rootKind
        } else {
            modules.groupingBy { it.kind }.eachCount().maxByOrNull { it.value }?.key ?: ProjectKind.UNKNOWN
        }

        return ProjectProfile(
            root = normRoot,
            primaryKind = primaryKind,
            modules = modules,
            languages = primaryKind.languageIds.toSet(),
            detectedRuntimes = BuildFileVersionDetector.detect(normRoot),
        )
    }

    private fun scanChildren(dir: Path, depth: Int, maxDepth: Int, out: MutableList<ProjectModule>) {
        if (depth > maxDepth || out.size >= MODULE_LIMIT) return
        val children = runCatching {
            Files.list(dir).use { stream ->
                stream.filter { Files.isDirectory(it) }
                    .filter { child ->
                        child.fileName?.toString()?.let { it !in skipDirs && !it.startsWith(".") } ?: false
                    }
                    .toList()
            }
        }.getOrDefault(emptyList())
        for (child in children) {
            if (out.size >= MODULE_LIMIT) return
            val kind = classify(child)
            if (kind != ProjectKind.UNKNOWN) {
                out.add(ProjectModule(child, kind))
            } else {
                scanChildren(child, depth + 1, maxDepth, out)
            }
        }
    }

    private fun classify(dir: Path): ProjectKind {
        val pubspec = dir.resolve("pubspec.yaml")
        if (Files.isRegularFile(pubspec)) {
            return if (FlutterProjectDetector.isFlutterPubspec(read(pubspec))) ProjectKind.FLUTTER else ProjectKind.DART
        }
        if (Files.isRegularFile(dir.resolve("Cargo.toml"))) return ProjectKind.CARGO
        if (Files.isRegularFile(dir.resolve("go.mod"))) return ProjectKind.GO
        if (Files.isRegularFile(dir.resolve("pom.xml"))) return ProjectKind.MAVEN
        if (gradleMarkers.any { Files.isRegularFile(dir.resolve(it)) }) return ProjectKind.GRADLE
        if (Files.isRegularFile(dir.resolve("global.json")) || hasFileWithExt(dir, ".sln") || hasFileWithExt(dir, ".csproj")) {
            return ProjectKind.DOTNET
        }
        if (pythonMarkers.any { Files.isRegularFile(dir.resolve(it)) }) return ProjectKind.PYTHON
        if (Files.isRegularFile(dir.resolve("package.json"))) return ProjectKind.NODE
        if (Files.isRegularFile(dir.resolve("CMakeLists.txt"))) return ProjectKind.CMAKE
        if (Files.isRegularFile(dir.resolve("Makefile"))) return ProjectKind.MAKE
        return ProjectKind.UNKNOWN
    }

    private fun hasFileWithExt(dir: Path, ext: String): Boolean = runCatching {
        Files.list(dir).use { stream ->
            stream.anyMatch { Files.isRegularFile(it) && it.fileName.toString().endsWith(ext, ignoreCase = true) }
        }
    }.getOrDefault(false)

    private fun read(path: Path): String = runCatching {
        if (Files.size(path) < 512_000) Files.readString(path) else ""
    }.getOrDefault("")
}
