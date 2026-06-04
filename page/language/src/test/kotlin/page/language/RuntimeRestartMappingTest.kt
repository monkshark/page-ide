package page.language

import page.lsp.LanguageBackend
import page.lsp.LspBackends
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RuntimeRestartMappingTest {

    private class FakeBackend(
        override val id: String,
        private val exts: Set<String>,
    ) : LanguageBackend {
        override val displayName: String = id
        override fun supports(extension: String?): Boolean =
            extension != null && extension.lowercase() in exts
        override fun resolveExecutable(env: Map<String, String>): LanguageBackend.Resolution =
            LanguageBackend.Resolution.NotFound(emptyList())
        override fun spawn(
            executable: Path,
            workspaceRoot: Path?,
            onStderrLine: ((String) -> Unit)?,
            env: Map<String, String>,
        ): page.lsp.LspClient = throw UnsupportedOperationException()
    }

    @Test
    fun `rust runtime extension maps to rust LSP backend id not runtime id`() {
        LspBackends.register(FakeBackend("rust", setOf("rs")))
        val ids = LspRouter.backendIdsForExtensions(listOf("rs"))
        assertTrue("rust" in ids, "rust-runtime의 확장자 rs는 LSP backend id 'rust'로 매핑돼야 한다")
        assertTrue("rust-runtime" !in ids, "runtime installer id를 그대로 controller 키로 쓰면 안 된다")
    }

    @Test
    fun `unregistered extension maps to no backend`() {
        val ids = LspRouter.backendIdsForExtensions(listOf("nope-no-such-ext"))
        assertEquals(emptySet(), ids)
    }

    @Test
    fun `multiple extensions collapse to distinct backend ids`() {
        LspBackends.register(FakeBackend("cpp", setOf("c", "cpp", "h")))
        val ids = LspRouter.backendIdsForExtensions(listOf("c", "cpp", "h"))
        assertEquals(setOf("cpp"), ids)
    }
}
