package page.lsp

import org.eclipse.lsp4j.jsonrpc.services.ServiceEndpoints
import kotlin.test.Test
import kotlin.test.assertTrue

class JdtlsExtensionNotificationsTest {

    private val supported = ServiceEndpoints.getSupportedMethods(LspClient::class.java).keys

    @Test
    fun `jdtls extension notifications are registered so they are not logged as unsupported`() {
        for (method in listOf(
            "language/status",
            "language/actionableNotification",
            "language/progressReport",
            "language/eventNotification",
        )) {
            assertTrue(method in supported, "expected $method to be a supported client method")
        }
    }

    @Test
    fun `standard client notifications remain registered`() {
        assertTrue("textDocument/publishDiagnostics" in supported)
        assertTrue("window/logMessage" in supported)
    }
}
