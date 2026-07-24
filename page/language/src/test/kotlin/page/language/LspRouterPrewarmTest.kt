package page.language

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import page.lsp.LanguageBackend
import page.lsp.LspBackends
import page.lsp.LspClient
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LspRouterPrewarmTest {
    private val supervisor = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Unconfined + supervisor)

    @AfterTest
    fun tearDown() {
        supervisor.cancel()
    }

    private class FakeBackend(override val id: String, private val found: Boolean) : LanguageBackend {
        override val displayName: String = id
        override fun supports(extension: String?): Boolean = false
        override fun resolveExecutable(env: Map<String, String>): LanguageBackend.Resolution =
            if (found) LanguageBackend.Resolution.Found(Path.of("fake-$id"), "test")
            else LanguageBackend.Resolution.NotFound(listOf("test"))

        override fun spawn(
            executable: Path,
            workspaceRoot: Path?,
            onStderrLine: ((String) -> Unit)?,
            env: Map<String, String>,
        ): LspClient = throw IllegalStateException("test backend does not spawn")
    }

    private fun router() = LspRouter(workspaceRoot = null, parentScope = scope)

    @Test
    fun prewarmStartsControllerForInstalledBackend() {
        LspBackends.register(FakeBackend("prewarm-found", found = true))
        val router = router()

        assertTrue(router.prewarm("prewarm-found"))
        assertNotNull(router.controllerById("prewarm-found"))
    }

    @Test
    fun prewarmIsNoOpForMissingBackend() {
        LspBackends.register(FakeBackend("prewarm-missing", found = false))
        val router = router()

        assertFalse(router.prewarm("prewarm-missing"))
        assertNull(router.controllerById("prewarm-missing"))
    }

    @Test
    fun prewarmIsIdempotent() {
        LspBackends.register(FakeBackend("prewarm-twice", found = true))
        val router = router()

        assertTrue(router.prewarm("prewarm-twice"))
        val first = router.controllerById("prewarm-twice")
        assertTrue(router.prewarm("prewarm-twice"))
        val second = router.controllerById("prewarm-twice")

        assertNotNull(first)
        assertSame(first, second)
    }

    @Test
    fun prewarmIsSafeForUnregisteredBackend() {
        val router = router()

        assertFalse(router.prewarm("no-such-backend"))
        assertNull(router.controllerById("no-such-backend"))
    }

    @Test
    fun beginLanguageDeleteRemovesControllerAndBlocksRespawn() {
        LspBackends.register(FakeBackend("delete-guard", found = true))
        val router = router()

        assertTrue(router.prewarm("delete-guard"))
        assertNotNull(router.controllerById("delete-guard"))

        router.beginLanguageDelete("delete-guard")
        assertNull(router.controllerById("delete-guard"))
        assertFalse(router.prewarm("delete-guard"))
        assertNull(router.controllerById("delete-guard"))

        router.endLanguageDelete("delete-guard")
        assertTrue(router.prewarm("delete-guard"))
        assertNotNull(router.controllerById("delete-guard"))
    }

    @Test
    fun beginLanguageDeleteIsSafeWhenAbsent() {
        val router = router()
        router.beginLanguageDelete("never-started")
        assertNull(router.controllerById("never-started"))
        router.endLanguageDelete("never-started")
    }
}
