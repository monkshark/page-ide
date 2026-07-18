package page.runtime

import java.nio.file.Path

enum class ProjectKind(val label: String, val languageIds: List<String>) {
    FLUTTER("Flutter", listOf("dart", "flutter")),
    DART("Dart", listOf("dart")),
    GRADLE("Gradle", listOf("kotlin", "java")),
    MAVEN("Maven", listOf("java")),
    NODE("Node", listOf("typescript", "javascript")),
    CARGO("Rust", listOf("rust")),
    GO("Go", listOf("go")),
    PYTHON("Python", listOf("python")),
    DOTNET(".NET", listOf("csharp")),
    CMAKE("C/C++", listOf("cpp", "c")),
    MAKE("Make", emptyList()),
    UNKNOWN("", emptyList()),
}

data class ProjectModule(val path: Path, val kind: ProjectKind)

data class ProjectProfile(
    val root: Path?,
    val primaryKind: ProjectKind,
    val modules: List<ProjectModule>,
    val languages: Set<String>,
    val detectedRuntimes: List<DetectedVersion>,
) {
    companion object {
        val EMPTY = ProjectProfile(null, ProjectKind.UNKNOWN, emptyList(), emptySet(), emptyList())
    }
}
