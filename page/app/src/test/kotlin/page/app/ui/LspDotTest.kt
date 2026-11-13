package page.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class LspDotTest {

    @Test
    fun `the server dot follows the server state`() {
        assertEquals(LspDot.READY, lspDotFor("Kotlin 1.3.13"))
        assertEquals(LspDot.WAITING, lspDotFor("Kotlin 1.3.13 · starting…"))
        assertEquals(LspDot.WAITING, lspDotFor("Go 0.16 · not started"))
        assertEquals(LspDot.PROBLEM, lspDotFor("Kotlin 1.3.13 · failed"))
        assertEquals(LspDot.PROBLEM, lspDotFor("Rust (not installed)"))
        assertEquals(LspDot.PROBLEM, lspDotFor("LSP · kotlin-language-server missing"))
    }
}
