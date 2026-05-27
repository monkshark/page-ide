package page.runtime

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LspInstallerDefaultsTest {

    private class StubInstaller(
        private val current: String?,
    ) : LspInstaller {
        override val languageId: String = "stub"
        override val displayName: String = "stub"
        override val precheck: LspInstaller.Precheck = LspInstaller.Precheck.Ok
        override fun isInstalled(): Boolean = current != null
        override fun executable(): Path? = null
        override fun install(version: String?, onProgress: (LspInstaller.Progress) -> Unit) {}
        override fun installedVersion(): String? = current
    }

    @Test
    fun installedVersionsDefaultsToSingleton() {
        assertEquals(listOf("1.0.0"), StubInstaller(current = "1.0.0").installedVersions())
    }

    @Test
    fun installedVersionsDefaultsToEmptyWhenNotInstalled() {
        assertTrue(StubInstaller(current = null).installedVersions().isEmpty())
    }

    @Test
    fun activeVersionDefaultsToInstalledVersion() {
        assertEquals("2.5.1", StubInstaller(current = "2.5.1").activeVersion())
    }

    @Test
    fun applyVersionDefaultsToUnsupported() {
        assertFalse(StubInstaller(current = "1.0.0").applyVersion("9.9.9"))
    }
}
