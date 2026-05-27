package page.runtime

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BuildFileVersionDetectorTest {

    private lateinit var tempDir: Path

    private fun createProject(): Path {
        tempDir = Files.createTempDirectory("page-build-detect-")
        return tempDir
    }

    @AfterTest
    fun cleanup() {
        if (::tempDir.isInitialized) {
            runCatching { Files.walk(tempDir).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
        }
    }

    @Test
    fun detectJavaFromDotFile() {
        val root = createProject()
        Files.writeString(root.resolve(".java-version"), "21")
        val result = BuildFileVersionDetector.detectForRuntime(root, "jdk")
        assertNotNull(result)
        assertEquals("21", result.version)
        assertEquals(".java-version", result.source)
    }

    @Test
    fun detectJavaFromPomXml() {
        val root = createProject()
        Files.writeString(root.resolve("pom.xml"), """
            <project>
                <properties>
                    <maven.compiler.source>17</maven.compiler.source>
                </properties>
            </project>
        """.trimIndent())
        val result = BuildFileVersionDetector.detectForRuntime(root, "jdk")
        assertNotNull(result)
        assertEquals("17", result.version)
        assertEquals("pom.xml", result.source)
    }

    @Test
    fun detectJavaFromGradleKts() {
        val root = createProject()
        Files.writeString(root.resolve("build.gradle.kts"), """
            kotlin { jvmToolchain(21) }
        """.trimIndent())
        val result = BuildFileVersionDetector.detectForRuntime(root, "jdk")
        assertNotNull(result)
        assertEquals("21", result.version)
        assertEquals("build.gradle.kts", result.source)
    }

    @Test
    fun detectNodeFromNvmrc() {
        val root = createProject()
        Files.writeString(root.resolve(".nvmrc"), "v22.13.1")
        val result = BuildFileVersionDetector.detectForRuntime(root, "node")
        assertNotNull(result)
        assertEquals("22.13.1", result.version)
        assertEquals(".nvmrc", result.source)
    }

    @Test
    fun detectNodeFromPackageJson() {
        val root = createProject()
        Files.writeString(root.resolve("package.json"), """{"engines":{"node":">=18.0.0"}}""")
        val result = BuildFileVersionDetector.detectForRuntime(root, "node")
        assertNotNull(result)
        assertEquals("18.0.0", result.version)
        assertEquals("package.json", result.source)
    }

    @Test
    fun detectPythonFromDotFile() {
        val root = createProject()
        Files.writeString(root.resolve(".python-version"), "3.13.13")
        val result = BuildFileVersionDetector.detectForRuntime(root, "python-runtime")
        assertNotNull(result)
        assertEquals("3.13.13", result.version)
    }

    @Test
    fun detectPythonFromPyprojectToml() {
        val root = createProject()
        Files.writeString(root.resolve("pyproject.toml"), """requires-python = ">=3.12.0" """)
        val result = BuildFileVersionDetector.detectForRuntime(root, "python-runtime")
        assertNotNull(result)
        assertEquals("3.12.0", result.version)
        assertEquals("pyproject.toml", result.source)
    }

    @Test
    fun detectGoFromGoMod() {
        val root = createProject()
        Files.writeString(root.resolve("go.mod"), "module example.com/app\n\ngo 1.24\n")
        val result = BuildFileVersionDetector.detectForRuntime(root, "go-sdk")
        assertNotNull(result)
        assertEquals("1.24", result.version)
        assertEquals("go.mod", result.source)
    }

    @Test
    fun detectAllReturnsMultiple() {
        val root = createProject()
        Files.writeString(root.resolve("pom.xml"), "<project><properties><java.version>21</java.version></properties></project>")
        Files.writeString(root.resolve("go.mod"), "module x\ngo 1.24\n")
        val results = BuildFileVersionDetector.detect(root)
        assertEquals(2, results.size)
    }

    @Test
    fun noFilesReturnsEmpty() {
        val root = createProject()
        val results = BuildFileVersionDetector.detect(root)
        assertEquals(0, results.size)
    }

    @Test
    fun dotFileHasPriorityOverBuildFile() {
        val root = createProject()
        Files.writeString(root.resolve(".java-version"), "17")
        Files.writeString(root.resolve("pom.xml"), "<project><properties><java.version>21</java.version></properties></project>")
        val result = BuildFileVersionDetector.detectForRuntime(root, "jdk")
        assertNotNull(result)
        assertEquals("17", result.version)
        assertEquals(".java-version", result.source)
    }
}
