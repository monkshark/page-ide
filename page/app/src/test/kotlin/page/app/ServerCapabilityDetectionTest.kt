package page.app

import org.eclipse.lsp4j.InlayHintRegistrationOptions
import org.eclipse.lsp4j.RenameOptions
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.jsonrpc.messages.Either
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerCapabilityDetectionTest {

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
}
