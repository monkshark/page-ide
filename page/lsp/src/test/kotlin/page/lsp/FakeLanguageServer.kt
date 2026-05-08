package page.lsp

import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue

class FakeLanguageServer : LanguageServer {

    val initializeCalls = ConcurrentLinkedQueue<InitializeParams>()
    val initializedCalls = ConcurrentLinkedQueue<InitializedParams>()
    val didOpenCalls = ConcurrentLinkedQueue<DidOpenTextDocumentParams>()
    val didChangeCalls = ConcurrentLinkedQueue<DidChangeTextDocumentParams>()
    val didCloseCalls = ConcurrentLinkedQueue<DidCloseTextDocumentParams>()
    @Volatile var shutdownCalled = false
    @Volatile var exitCalled = false

    private val textDocService = object : TextDocumentService {
        override fun didOpen(params: DidOpenTextDocumentParams) { didOpenCalls += params }
        override fun didChange(params: DidChangeTextDocumentParams) { didChangeCalls += params }
        override fun didClose(params: DidCloseTextDocumentParams) { didCloseCalls += params }
        override fun didSave(params: DidSaveTextDocumentParams) {}
    }

    private val workspaceService = object : WorkspaceService {
        override fun didChangeConfiguration(params: DidChangeConfigurationParams) {}
        override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {}
    }

    override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> {
        initializeCalls += params
        val capabilities = ServerCapabilities()
        capabilities.setTextDocumentSync(TextDocumentSyncKind.Full)
        return CompletableFuture.completedFuture(InitializeResult(capabilities))
    }

    override fun initialized(params: InitializedParams) {
        initializedCalls += params
    }

    override fun shutdown(): CompletableFuture<Any> {
        shutdownCalled = true
        return CompletableFuture.completedFuture<Any>(null)
    }

    override fun exit() {
        exitCalled = true
    }

    override fun getTextDocumentService(): TextDocumentService = textDocService
    override fun getWorkspaceService(): WorkspaceService = workspaceService
}
