package page.atlas.analyzer

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModuleLinksTest {

    private fun write(root: Path, rel: String, text: String) {
        val p = root.resolve(rel)
        Files.createDirectories(p.parent)
        Files.writeString(p, text)
    }

    @Test
    fun `settings lists the modules in either dsl`() {
        assertEquals(listOf(":app", ":page:core"), ModuleLinks.includedProjects("include(\":app\")\ninclude(\":page:core\")\n"))
        assertEquals(listOf(":app", ":lib"), ModuleLinks.includedProjects("include ':app', ':lib'\n"))
        assertEquals(listOf(":tools"), ModuleLinks.includedProjects("include(\"tools\")"))
    }

    @Test
    fun `commented includes are ignored`() {
        assertEquals(listOf(":app"), ModuleLinks.includedProjects("include(\":app\")\n// include(\":old\")\n"))
    }

    @Test
    fun `a build file names the modules it depends on`() {
        val build = """
            dependencies {
                implementation(project(":page:core"))
                api(project(":shared-core"))
                testImplementation(project(path = ":page:testkit"))
                implementation(libs.gson)
            }
        """.trimIndent()
        assertEquals(listOf(":page:core", ":shared-core", ":page:testkit"), ModuleLinks.referencedProjects(build))
    }

    @Test
    fun `module links point from the using module to the used one`() {
        val root = Files.createTempDirectory("gradle-")
        write(root, "settings.gradle.kts", "include(\":app\")\ninclude(\":page:core\")\n")
        write(root, "app/build.gradle.kts", "dependencies { implementation(project(\":page:core\")) }")
        write(root, "page/core/build.gradle.kts", "dependencies { }")
        val links = ModuleLinks.read(root)
        assertEquals(listOf(root.resolve("app").normalize() to root.resolve("page/core").normalize()), links)
    }

    @Test
    fun `a project without gradle has no module links`() {
        assertTrue(ModuleLinks.read(Files.createTempDirectory("plain-")).isEmpty())
    }
}
