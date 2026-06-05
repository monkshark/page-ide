package page.app

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppSettingsLspPathTest {

    private lateinit var dir: Path
    private var prevDir: String? = null

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("page-settings-")
        prevDir = System.getProperty("page.settings.dir")
        System.setProperty("page.settings.dir", dir.toString())
    }

    @AfterTest
    fun tearDown() {
        if (prevDir != null) System.setProperty("page.settings.dir", prevDir) else System.clearProperty("page.settings.dir")
        dir.toFile().deleteRecursively()
    }

    @Test
    fun serverPathsRoundTrip() {
        val saved = LspOptions(serverPaths = mapOf("java" to "/opt/jdtls/bin/jdtls", "go" to "/usr/local/bin/gopls"))
        AppSettings.saveLsp(saved)

        val loaded = AppSettings.loadLsp()

        assertEquals("/opt/jdtls/bin/jdtls", loaded.serverPaths["java"])
        assertEquals("/usr/local/bin/gopls", loaded.serverPaths["go"])
        assertEquals(2, loaded.serverPaths.size)
    }

    @Test
    fun blankPathsAreNotPersisted() {
        AppSettings.saveLsp(LspOptions(serverPaths = mapOf("java" to "/x/jdtls", "go" to "   ", "rust" to "")))

        val loaded = AppSettings.loadLsp()

        assertEquals(setOf("java"), loaded.serverPaths.keys)
    }

    @Test
    fun removingAnOverrideClearsStaleKey() {
        AppSettings.saveLsp(LspOptions(serverPaths = mapOf("java" to "/x/jdtls", "go" to "/y/gopls")))
        AppSettings.saveLsp(LspOptions(serverPaths = mapOf("java" to "/x/jdtls")))

        val loaded = AppSettings.loadLsp()

        assertEquals("/x/jdtls", loaded.serverPaths["java"])
        assertNull(loaded.serverPaths["go"])
        assertEquals(1, loaded.serverPaths.size)
    }

    @Test
    fun serverPathsDoNotDisturbOtherLspFields() {
        AppSettings.saveLsp(LspOptions(showInlayHints = false, hoverDelayMs = 250, serverPaths = mapOf("java" to "/x/jdtls")))

        val loaded = AppSettings.loadLsp()

        assertEquals(false, loaded.showInlayHints)
        assertEquals(250, loaded.hoverDelayMs)
        assertEquals("/x/jdtls", loaded.serverPaths["java"])
    }

    @Test
    fun defaultsWhenNoFile() {
        val loaded = AppSettings.loadLsp()
        assertTrue(loaded.serverPaths.isEmpty())
    }
}
