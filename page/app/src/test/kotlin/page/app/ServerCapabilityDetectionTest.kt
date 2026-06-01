package page.app

import org.eclipse.lsp4j.CompletionOptions
import org.eclipse.lsp4j.ExecuteCommandOptions
import org.eclipse.lsp4j.InlayHintRegistrationOptions
import org.eclipse.lsp4j.RenameOptions
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.jsonrpc.messages.Either
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerCapabilityDetectionTest {

    @Test
    fun `canonicalUri lowercases windows drive letter`() {
        assertEquals(
            "file:///c:/Users/x/main.rs",
            canonicalUri("file:///C:/Users/x/main.rs"),
        )
    }

    @Test
    fun `canonicalUri leaves already-lowercase drive unchanged`() {
        val uri = "file:///c:/Users/x/main.rs"
        assertEquals(uri, canonicalUri(uri))
    }

    @Test
    fun `canonicalUri leaves non-windows file uris unchanged`() {
        val uri = "file:///home/user/main.rs"
        assertEquals(uri, canonicalUri(uri))
    }

    @Test
    fun `canonicalUri rust-analyzer and editor uris collapse to same key`() {
        assertEquals(
            canonicalUri("file:///c:/Users/x/main.rs"),
            canonicalUri("file:///C:/Users/x/main.rs"),
        )
    }

    @Test
    fun `null capabilities disable inlay hints`() {
        assertFalse(detectInlayHintSupport(null))
        assertFalse(detectInlayHintSupport(ServerCapabilities()))
    }

    @Test
    fun `boolean true enables inlay hints`() {
        val caps = ServerCapabilities().apply { inlayHintProvider = Either.forLeft(true) }
        assertTrue(detectInlayHintSupport(caps))
    }

    @Test
    fun `boolean false disables inlay hints`() {
        val caps = ServerCapabilities().apply { inlayHintProvider = Either.forLeft(false) }
        assertFalse(detectInlayHintSupport(caps))
    }

    @Test
    fun `registration options enable inlay hints`() {
        val caps = ServerCapabilities().apply {
            inlayHintProvider = Either.forRight(InlayHintRegistrationOptions())
        }
        assertTrue(detectInlayHintSupport(caps))
    }

    @Test
    fun `prepareRename requires options with prepareProvider`() {
        assertFalse(detectPrepareRenameSupport(null))
        assertFalse(detectPrepareRenameSupport(ServerCapabilities().apply {
            renameProvider = Either.forLeft(true)
        }))
        assertTrue(detectPrepareRenameSupport(ServerCapabilities().apply {
            renameProvider = Either.forRight(RenameOptions().apply { prepareProvider = true })
        }))
    }

    @Test
    fun `completion resolve requires resolveProvider true`() {
        assertFalse(detectCompletionResolveSupport(null))
        assertFalse(detectCompletionResolveSupport(ServerCapabilities()))
        assertFalse(detectCompletionResolveSupport(ServerCapabilities().apply {
            completionProvider = CompletionOptions().apply { resolveProvider = false }
        }))
        assertTrue(detectCompletionResolveSupport(ServerCapabilities().apply {
            completionProvider = CompletionOptions().apply { resolveProvider = true }
        }))
    }

    @Test
    fun `executeCommand requires executeCommandProvider`() {
        assertFalse(detectExecuteCommandSupport(null))
        assertFalse(detectExecuteCommandSupport(ServerCapabilities()))
        assertTrue(detectExecuteCommandSupport(ServerCapabilities().apply {
            executeCommandProvider = ExecuteCommandOptions(listOf("java.apply.organizeImports"))
        }))
    }
}
