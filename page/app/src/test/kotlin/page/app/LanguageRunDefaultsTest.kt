package page.app

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LanguageRunDefaultsTest {

    @Test
    fun `forExtension is case insensitive and tolerates leading dot`() {
        val a = LanguageRunDefaults.forExtension("PY")
        val b = LanguageRunDefaults.forExtension(".py")
        assertNotNull(a)
        assertNotNull(b)
        assertEquals("python", a.command)
        assertEquals("python", b.command)
    }

    @Test
    fun `forFile picks last segment after dot`() {
        val t = LanguageRunDefaults.forFile(Path.of("script.tar.gz"))
        assertNull(t)
        val py = LanguageRunDefaults.forFile(Path.of("a/b/main.py"))
        assertNotNull(py)
        assertEquals("python", py.command)
    }

    @Test
    fun `forFile returns null without extension`() {
        assertNull(LanguageRunDefaults.forFile(Path.of("README")))
        assertNull(LanguageRunDefaults.forFile(Path.of("trailing.")))
    }

    @Test
    fun `buildConfig fills file token`() {
        val workspace = Path.of("/tmp/proj")
        val file = Path.of("/tmp/proj/main.py")
        val cfg = LanguageRunDefaults.buildConfig(file, workspace)
        assertNotNull(cfg)
        assertTrue(cfg.command.contains("python"), "command should contain 'python', got: ${cfg.command}")
        assertEquals(listOf(file.toString()), cfg.args)
        assertEquals(workspace.toString(), cfg.workingDir)
    }

    @Test
    fun `buildConfig uses workspace as cwd when provided`() {
        val workspace = Path.of("/proj")
        val cfg = LanguageRunDefaults.buildConfig(
            Path.of("/proj/src/app.ts"),
            workspaceRoot = workspace,
        )
        assertNotNull(cfg)
        assertEquals("npx", cfg.command)
        assertTrue(cfg.args.contains("ts-node"))
        assertEquals(workspace.toString(), cfg.workingDir)
    }

    @Test
    fun `buildConfig uses parent as cwd when workspace null`() {
        val file = Path.of("/standalone/run.sh")
        val cfg = LanguageRunDefaults.buildConfig(file, workspaceRoot = null)
        assertNotNull(cfg)
        assertEquals("bash", cfg.command)
        assertEquals(Path.of("/standalone").toString(), cfg.workingDir)
    }

    @Test
    fun `buildConfig returns null for unknown extension`() {
        assertNull(LanguageRunDefaults.buildConfig(Path.of("/x/y.unknown"), null))
    }

    @Test
    fun `cargo run template has no file argument`() {
        val cfg = LanguageRunDefaults.buildConfig(Path.of("/proj/src/main.rs"), Path.of("/proj"))
        assertNotNull(cfg)
        assertEquals("cargo", cfg.command)
        assertEquals(listOf("run"), cfg.args)
    }
}
