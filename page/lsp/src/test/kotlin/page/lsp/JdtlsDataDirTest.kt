package page.lsp

import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class JdtlsDataDirTest {

    private val jdtls = Paths.get("C:", "Users", "x", ".page-ide", "lsp", "jdtls", "1.58.0", "jdtls.bat")

    @Test
    fun `data dir lives under the jdtls install in a ws-data folder`() {
        val dir = jdtlsDataDir(jdtls, Paths.get("C:", "Users", "x", "Desktop", "IDE-test-samples"))
        assertEquals(jdtls.toAbsolutePath().parent.resolve("ws-data"), dir.parent)
        assertTrue(dir.fileName.toString().startsWith("IDE-test-samples-"))
    }

    @Test
    fun `distinct workspaces get distinct data dirs`() {
        val a = jdtlsDataDir(jdtls, Paths.get("C:", "ws", "unused-gray-demo"))
        val b = jdtlsDataDir(jdtls, Paths.get("C:", "ws", "IDE-test-samples"))
        assertNotEquals(a, b)
    }

    @Test
    fun `same workspace is stable across calls`() {
        val root = Paths.get("C:", "ws", "proj")
        assertEquals(jdtlsDataDir(jdtls, root), jdtlsDataDir(jdtls, root))
    }
}
