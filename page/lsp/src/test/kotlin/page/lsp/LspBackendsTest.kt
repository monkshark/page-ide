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
        val prevUserInstall = System.getProperty("page.lsp.kotlin.userInstall")
        System.clearProperty("page.lsp.kotlin.path")
        System.clearProperty("compose.application.resources.dir")
        System.setProperty("page.lsp.kotlin.disableDev", "true")
        val emptyUserInstall = java.nio.file.Files.createTempDirectory("kls-empty-")
        System.setProperty("page.lsp.kotlin.userInstall", emptyUserInstall.toString())
        try {
            val notFound = KotlinLanguageBackend.resolveExecutable(mapOf("PATH" to "/definitely/does/not/exist"))
            assertEquals(true, notFound is LanguageBackend.Resolution.NotFound)
        } finally {
            if (prevOverride != null) System.setProperty("page.lsp.kotlin.path", prevOverride)
            if (prevResources != null) System.setProperty("compose.application.resources.dir", prevResources)
            if (prevDisableDev != null) System.setProperty("page.lsp.kotlin.disableDev", prevDisableDev)
            else System.clearProperty("page.lsp.kotlin.disableDev")
            if (prevUserInstall != null) System.setProperty("page.lsp.kotlin.userInstall", prevUserInstall)
            else System.clearProperty("page.lsp.kotlin.userInstall")
            emptyUserInstall.toFile().deleteRecursively()
        }
    }

    @Test
    fun registerIsIdempotentById() {
        val sizeBefore = LspBackends.all().size
        LspBackends.register(KotlinLanguageBackend)
        LspBackends.register(KotlinLanguageBackend)
        assertEquals(sizeBefore, LspBackends.all().size)
    }

    @Test
    fun lspLanguageIdDefaultsToBackendId() {
        assertEquals("kotlin", KotlinLanguageBackend.lspLanguageId)
    }

    @Test
    fun forFileUsesExtensionRoutingWithoutInterceptor() {
        assertSame(KotlinLanguageBackend, LspBackends.forFile(java.nio.file.Path.of("src", "Main.kt"), null))
        assertNull(LspBackends.forFile(java.nio.file.Path.of("src", "Main.zzunknown"), null))
    }

    @Test
    fun forFileInterceptorOverridesExtensionRouting() {
        val base = FakeBackend("fake-base", "fakedart")
        val alt = FakeBackend("fake-alt", "fakedart")
        LspBackends.register(base)
        LspBackends.register(alt)
        try {
            LspBackends.routingInterceptor = { path, _ ->
                if (path.fileName?.toString() == "special.fakedart") LspBackends.byId("fake-alt") else null
            }
            assertSame(alt, LspBackends.forFile(java.nio.file.Path.of("p", "special.fakedart"), null))
            assertSame(base, LspBackends.forFile(java.nio.file.Path.of("p", "plain.fakedart"), null))
        } finally {
            LspBackends.routingInterceptor = null
        }
    }

    @Test
    fun allForExtensionReturnsEveryMatchingBackend() {
        val first = FakeBackend("fake-multi-1", "fakemulti")
        val second = FakeBackend("fake-multi-2", "fakemulti")
        LspBackends.register(first)
        LspBackends.register(second)
        assertEquals(
            listOf("fake-multi-1", "fake-multi-2"),
            LspBackends.allForExtension("fakemulti").map { it.id },
        )
    }

    private class FakeBackend(override val id: String, private val ext: String) : LanguageBackend {
        override val displayName: String = id
        override fun supports(extension: String?): Boolean = ext.equals(extension, ignoreCase = true)
        override fun resolveExecutable(env: Map<String, String>): LanguageBackend.Resolution =
            LanguageBackend.Resolution.NotFound(emptyList())
        override fun spawn(
            executable: java.nio.file.Path,
            workspaceRoot: java.nio.file.Path?,
            onStderrLine: ((String) -> Unit)?,
            env: Map<String, String>,
        ): LspClient = throw UnsupportedOperationException()
    }
}
