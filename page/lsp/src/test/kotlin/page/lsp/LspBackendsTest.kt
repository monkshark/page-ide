package page.lsp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class LspBackendsTest {

    @Test
    fun kotlinBackendRegisteredByDefault() {
        val found = LspBackends.byId("kotlin")
        assertNotNull(found, "KotlinLanguageBackend should be registered at module init")
        assertSame(KotlinLanguageBackend, found)
    }

    @Test
    fun routerSelectsKotlinForKtAndKtsExtensions() {
        assertSame(KotlinLanguageBackend, LspBackends.forExtension("kt"))
        assertSame(KotlinLanguageBackend, LspBackends.forExtension("kts"))
        assertSame(KotlinLanguageBackend, LspBackends.forExtension("KT"))
    }

    @Test
    fun routerReturnsNullForUnregisteredExtension() {
        assertNull(LspBackends.forExtension("java"))
        assertNull(LspBackends.forExtension(null))
        assertNull(LspBackends.forExtension(""))
    }

    @Test
    fun kotlinBackendSupportsKtAndKtsOnly() {
        assertEquals(true, KotlinLanguageBackend.supports("kt"))
        assertEquals(true, KotlinLanguageBackend.supports("kts"))
        assertEquals(true, KotlinLanguageBackend.supports("Kt"))
        assertEquals(false, KotlinLanguageBackend.supports("java"))
        assertEquals(false, KotlinLanguageBackend.supports(null))
    }

    @Test
    fun kotlinBackendResolveBridgesKotlinLspResolution() {
        val prevOverride = System.getProperty("page.lsp.kotlin.path")
        val prevResources = System.getProperty("compose.application.resources.dir")
        val prevDisableDev = System.getProperty("page.lsp.kotlin.disableDev")
        System.clearProperty("page.lsp.kotlin.path")
        System.clearProperty("compose.application.resources.dir")
        System.setProperty("page.lsp.kotlin.disableDev", "true")
        try {
            val notFound = KotlinLanguageBackend.resolveExecutable(mapOf("PATH" to "/definitely/does/not/exist"))
            assertEquals(true, notFound is LanguageBackend.Resolution.NotFound)
        } finally {
            if (prevOverride != null) System.setProperty("page.lsp.kotlin.path", prevOverride)
            if (prevResources != null) System.setProperty("compose.application.resources.dir", prevResources)
            if (prevDisableDev != null) System.setProperty("page.lsp.kotlin.disableDev", prevDisableDev)
            else System.clearProperty("page.lsp.kotlin.disableDev")
        }
    }

    @Test
    fun registerIsIdempotentById() {
        val sizeBefore = LspBackends.all().size
        LspBackends.register(KotlinLanguageBackend)
        LspBackends.register(KotlinLanguageBackend)
        assertEquals(sizeBefore, LspBackends.all().size)
    }
}
