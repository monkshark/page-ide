package page.runtime

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectRecognizerTest {

    private fun tempDir(): Path = Files.createTempDirectory("project-recognize")

    private fun write(path: Path, content: String = ""): Path {
        Files.createDirectories(path.parent)
        Files.writeString(path, content)
        return path
    }

    @Test
    fun `pubspec with flutter dependency is a flutter project`() {
        val root = tempDir()
        write(root.resolve("pubspec.yaml"), "name: app\ndependencies:\n  flutter:\n    sdk: flutter\n")
        assertEquals(ProjectKind.FLUTTER, ProjectRecognizer.recognize(root).primaryKind)
    }

    @Test
    fun `pubspec without flutter dependency is a dart project`() {
        val root = tempDir()
        write(root.resolve("pubspec.yaml"), "name: cli\ndependencies:\n  args: ^2.0.0\n")
        val profile = ProjectRecognizer.recognize(root)
        assertEquals(ProjectKind.DART, profile.primaryKind)
        assertEquals(setOf("dart"), profile.languages)
    }

    @Test
    fun `cargo workspace collects member crates`() {
        val root = tempDir()
        write(root.resolve("Cargo.toml"), "[workspace]\nmembers = [\"crates/foo\", \"crates/bar\"]\n")
        write(root.resolve("crates/foo/Cargo.toml"), "[package]\nname = \"foo\"\n")
        write(root.resolve("crates/bar/Cargo.toml"), "[package]\nname = \"bar\"\n")

        val profile = ProjectRecognizer.recognize(root)
        assertEquals(ProjectKind.CARGO, profile.primaryKind)
        assertEquals(2, profile.modules.size)
        assertTrue(profile.modules.all { it.kind == ProjectKind.CARGO })
    }

    @Test
    fun `gradle settings with subprojects is a multimodule gradle project`() {
        val root = tempDir()
        write(root.resolve("settings.gradle.kts"), "include(\":app\", \":lib\")\n")
        write(root.resolve("app/build.gradle.kts"), "plugins { }\n")
        write(root.resolve("lib/build.gradle.kts"), "plugins { }\n")

        val profile = ProjectRecognizer.recognize(root)
        assertEquals(ProjectKind.GRADLE, profile.primaryKind)
        assertEquals(2, profile.modules.size)
        assertTrue(profile.modules.all { it.kind == ProjectKind.GRADLE })
    }

    @Test
    fun `pom with modules is a maven project`() {
        val root = tempDir()
        write(root.resolve("pom.xml"), "<project><modules><module>core</module></modules></project>")
        write(root.resolve("core/pom.xml"), "<project></project>")

        val profile = ProjectRecognizer.recognize(root)
        assertEquals(ProjectKind.MAVEN, profile.primaryKind)
        assertEquals(1, profile.modules.size)
        assertEquals(ProjectKind.MAVEN, profile.modules.single().kind)
    }

    @Test
    fun `package json is a node project`() {
        val root = tempDir()
        write(root.resolve("package.json"), "{ \"name\": \"web\" }")
        val profile = ProjectRecognizer.recognize(root)
        assertEquals(ProjectKind.NODE, profile.primaryKind)
        assertEquals(setOf("typescript", "javascript"), profile.languages)
    }

    @Test
    fun `multiple root markers resolve by priority`() {
        val root = tempDir()
        write(root.resolve("pubspec.yaml"), "name: app\nflutter:\n")
        write(root.resolve("package.json"), "{ \"name\": \"web\" }")
        write(root.resolve("pom.xml"), "<project></project>")
        assertEquals(ProjectKind.FLUTTER, ProjectRecognizer.recognize(root).primaryKind)
    }

    @Test
    fun `no markers is unknown`() {
        val root = tempDir()
        Files.createDirectories(root.resolve("src/main"))
        val profile = ProjectRecognizer.recognize(root)
        assertEquals(ProjectKind.UNKNOWN, profile.primaryKind)
        assertTrue(profile.modules.isEmpty())
    }

    @Test
    fun `monorepo without root marker uses most frequent subdir kind`() {
        val root = tempDir()
        write(root.resolve("services/api/go.mod"), "module api\n\ngo 1.22\n")
        write(root.resolve("services/worker/go.mod"), "module worker\n\ngo 1.22\n")
        write(root.resolve("web/package.json"), "{ \"name\": \"web\" }")

        val profile = ProjectRecognizer.recognize(root)
        assertEquals(ProjectKind.GO, profile.primaryKind)
        assertEquals(3, profile.modules.size)
    }

    @Test
    fun `build output and dependency directories are skipped`() {
        val root = tempDir()
        write(root.resolve("app/Cargo.toml"), "[package]\nname = \"app\"\n")
        write(root.resolve("node_modules/dep/Cargo.toml"), "[package]\nname = \"dep\"\n")
        write(root.resolve("target/debug/Cargo.toml"), "[package]\nname = \"art\"\n")

        val profile = ProjectRecognizer.recognize(root)
        assertEquals(1, profile.modules.size)
        assertTrue(profile.modules.single().path.toString().contains("app"))
    }
}
