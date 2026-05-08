package page.lsp

import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.CompletionCapabilities
import org.eclipse.lsp4j.CompletionItemCapabilities
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.TextDocumentClientCapabilities
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageServer
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicReference

class LspClient(
    private val transport: LspTransport,
    private val workspaceRoot: Path? = null,
    private val clientName: String = "PAGE",
    private val clientVersion: String = "0.1.0",
) : LanguageClient {

    private val stateRef = AtomicReference(LspState.NOT_STARTED)
    val state: LspState get() = stateRef.get()

    private var server: LanguageServer? = null
    private var listening: Future<Void>? = null

    private val diagnosticsListeners = ConcurrentLinkedQueue<(PublishDiagnosticsParams) -> Unit>()
    private val logListeners = ConcurrentLinkedQueue<(MessageParams) -> Unit>()
    private val showMessageListeners = ConcurrentLinkedQueue<(MessageParams) -> Unit>()

    fun onDiagnostics(listener: (PublishDiagnosticsParams) -> Unit) {
        diagnosticsListeners += listener
    }

    fun onLogMessage(listener: (MessageParams) -> Unit) {
        logListeners += listener
    }

    fun onShowMessage(listener: (MessageParams) -> Unit) {
        showMessageListeners += listener
    }

    fun server(): LanguageServer = server ?: error("LSP not started yet (state=$state)")

    fun start(): CompletableFuture<InitializeResult> {
        if (!stateRef.compareAndSet(LspState.NOT_STARTED, LspState.STARTING)) {
            error("LspClient already started (state=$state)")
        }
        return try {
            val launcher = createLauncher(transport.input, transport.output)
            server = launcher.remoteProxy
            listening = launcher.startListening()
            server!!.initialize(buildInitializeParams())
                .whenComplete { _, throwable ->
                    if (throwable != null) {
                        stateRef.set(LspState.FAILED)
                    } else {
                        try {
                            server!!.initialized(org.eclipse.lsp4j.InitializedParams())
                            stateRef.set(LspState.INITIALIZED)
                        } catch (t: Throwable) {
                            stateRef.set(LspState.FAILED)
                            throw t
                        }
                    }
                }
        } catch (t: Throwable) {
            stateRef.set(LspState.FAILED)
            throw t
        }
    }

    fun shutdown(): CompletableFuture<Unit> {
        val current = stateRef.get()
        if (current == LspState.EXITED || current == LspState.NOT_STARTED) {
            return CompletableFuture.completedFuture(Unit)
        }
        stateRef.set(LspState.SHUTTING_DOWN)
        val srv = server ?: run {
            stateRef.set(LspState.EXITED)
            return CompletableFuture.completedFuture(Unit)
        }
        return srv.shutdown().handle { _, _ ->
            try { srv.exit() } catch (_: Throwable) {}
            try { listening?.cancel(true) } catch (_: Throwable) {}
            try { transport.close() } catch (_: Throwable) {}
            stateRef.set(LspState.EXITED)
            Unit
        }
    }

    private fun createLauncher(input: InputStream, output: OutputStream): Launcher<LanguageServer> =
        LSPLauncher.createClientLauncher(this, input, output)

    private fun buildInitializeParams(): InitializeParams {
        val params = InitializeParams()
        params.processId = ProcessHandle.current().pid().toInt()
        params.clientInfo = org.eclipse.lsp4j.ClientInfo(clientName, clientVersion)
        params.capabilities = ClientCapabilities().apply {
            textDocument = TextDocumentClientCapabilities().apply {
                completion = CompletionCapabilities().apply {
                    completionItem = CompletionItemCapabilities().apply {
                        snippetSupport = false
                    }
                }
            }
        }
        if (workspaceRoot != null) {
            val uri = workspaceRoot.toUri().toString()
            params.workspaceFolders = listOf(WorkspaceFolder(uri, workspaceRoot.fileName?.toString() ?: clientName))
            @Suppress("DEPRECATION")
            params.rootUri = uri
        }
        return params
    }

    override fun telemetryEvent(`object`: Any?) {}

    override fun publishDiagnostics(diagnostics: PublishDiagnosticsParams) {
        diagnosticsListeners.forEach { it(diagnostics) }
    }

    override fun showMessage(messageParams: MessageParams) {
        showMessageListeners.forEach { it(messageParams) }
    }

    override fun showMessageRequest(requestParams: ShowMessageRequestParams): CompletableFuture<MessageActionItem> =
        CompletableFuture.completedFuture(null)

    override fun logMessage(message: MessageParams) {
        logListeners.forEach { it(message) }
    }

    companion object {
        fun documentUri(path: Path): String = path.toUri().toString()
        fun documentUri(uri: URI): String = uri.toString()
    }
}
