package page.runtime

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CargoWorkspaceDetectorTest {

    private fun tempDir(): Path = Files.createTempDirectory("cargo-detect")

    private fun touchManifest(dir: Path) {
        Files.createDirectories(dir)
        Files.writeString(dir.resolve("Cargo.toml"), "[package]\nname = \"x\"\n")
    }

    @Test
    fun `root manifest returns null so rust-analyzer uses its own discovery`() {
        val root = tempDir()
        touchManifest(root)
        assertNull(CargoWorkspaceDetector.linkedProjects(root))
    }

    @Test
    fun `nested manifests are discovered as linked projects`() {
        val root = tempDir()
        touchManifest(root.resolve("crate-a"))
        touchManifest(root.resolve("crate-b"))

        val options = CargoWorkspaceDetector.linkedProjects(root)!!
        @Suppress("UNCHECKED_CAST")
        val linked = options["linkedProjects"] as List<String>
        assertEquals(2, linked.size)
        assertTrue(linked.all { it.endsWith("Cargo.toml") })
    }

    @Test
    fun `does not descend into a directory that already has a manifest`() {
        val root = tempDir()
        touchManifest(root.resolve("crate-a"))
        touchManifest(root.resolve("crate-a").resolve("inner"))

        val projects = CargoWorkspaceDetector.findCargoProjects(root)
        assertEquals(1, projects.size)
    }

    @Test
    fun `skips target and hidden directories`() {
        val root = tempDir()
        touchManifest(root.resolve("target").resolve("debug"))
        touchManifest(root.resolve(".git").resolve("hooks"))
        touchManifest(root.resolve("real"))

        val projects = CargoWorkspaceDetector.findCargoProjects(root)
        assertEquals(1, projects.size)
        assertTrue(projects.single().toString().contains("real"))
    }

    @Test
    fun `no manifests anywhere returns null`() {
        val root = tempDir()
        Files.createDirectories(root.resolve("src"))
        assertNull(CargoWorkspaceDetector.linkedProjects(root))
    }

    @Test
    fun `null workspace root returns null`() {
        assertNull(CargoWorkspaceDetector.linkedProjects(null))
    }
}
