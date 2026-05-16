package page.lsp

import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageClient
import java.io.PipedInputStream
import java.io.PipedOutputStream

class LspTestHarness(initialSettings: Any? = null) {
    val fakeServer = FakeLanguageServer()
    val client: LspClient
    private val transport: LspTransport
    private val serverLauncher: Launcher<LanguageClient>

    init {
        val clientToServer = PipedOutputStream()
        val serverFromClient = PipedInputStream(clientToServer, BUFFER)
        val serverToClient = PipedOutputStream()
        val clientFromServer = PipedInputStream(serverToClient, BUFFER)

        transport = StreamTransport(clientFromServer, clientToServer)
        client = LspClient(transport, initialSettings = initialSettings)

        serverLauncher = LSPLauncher.createServerLauncher(fakeServer, serverFromClient, serverToClient)
        serverLauncher.startListening()
    }

    fun close() {
        try { client.shutdown().get() } catch (_: Throwable) {}
    }

    companion object {
        private const val BUFFER = 1 shl 16
    }
}
