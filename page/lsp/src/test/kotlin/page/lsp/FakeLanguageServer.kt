package page.lsp

import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.DocumentFormattingParams
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.PrepareRenameDefaultBehavior
import org.eclipse.lsp4j.PrepareRenameParams
import org.eclipse.lsp4j.PrepareRenameResult
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.SignatureHelp
import org.eclipse.lsp4j.SignatureHelpParams
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.WorkspaceSymbol
import org.eclipse.lsp4j.WorkspaceSymbolParams
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.Either3
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
    val hoverCalls = ConcurrentLinkedQueue<HoverParams>()
    val definitionCalls = ConcurrentLinkedQueue<DefinitionParams>()
    val referencesCalls = ConcurrentLinkedQueue<ReferenceParams>()
    val signatureHelpCalls = ConcurrentLinkedQueue<SignatureHelpParams>()
    val prepareRenameCalls = ConcurrentLinkedQueue<PrepareRenameParams>()
    val renameCalls = ConcurrentLinkedQueue<RenameParams>()
    val documentSymbolCalls = ConcurrentLinkedQueue<DocumentSymbolParams>()
    val workspaceSymbolCalls = ConcurrentLinkedQueue<WorkspaceSymbolParams>()
    val formattingCalls = ConcurrentLinkedQueue<DocumentFormattingParams>()
    val codeActionCalls = ConcurrentLinkedQueue<CodeActionParams>()
    @Volatile var hoverResponse: Hover? = null
    @Volatile var definitionResponse: Either<MutableList<out Location>, MutableList<out LocationLink>>? = null
    @Volatile var referencesResponse: MutableList<out Location>? = null
    @Volatile var signatureHelpResponse: SignatureHelp? = null
    @Volatile var prepareRenameResponse: Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>? = null
    @Volatile var renameResponse: WorkspaceEdit? = null
    @Volatile var documentSymbolResponse: MutableList<Either<SymbolInformation, DocumentSymbol>>? = null
    @Volatile var workspaceSymbolResponse: Either<MutableList<out SymbolInformation>, MutableList<out WorkspaceSymbol>>? = null
    @Volatile var formattingResponse: MutableList<out TextEdit>? = null
    @Volatile var codeActionResponse: MutableList<Either<Command, CodeAction>>? = null
    @Volatile var shutdownCalled = false
    @Volatile var exitCalled = false

    private val textDocService = object : TextDocumentService {
        override fun didOpen(params: DidOpenTextDocumentParams) { didOpenCalls += params }
        override fun didChange(params: DidChangeTextDocumentParams) { didChangeCalls += params }
        override fun didClose(params: DidCloseTextDocumentParams) { didCloseCalls += params }
        override fun didSave(params: DidSaveTextDocumentParams) {}
        override fun hover(params: HoverParams): CompletableFuture<Hover> {
            hoverCalls += params
            return CompletableFuture.completedFuture(hoverResponse)
        }
        override fun definition(
            params: DefinitionParams,
        ): CompletableFuture<Either<MutableList<out Location>, MutableList<out LocationLink>>> {
            definitionCalls += params
            return CompletableFuture.completedFuture(definitionResponse)
        }
        override fun references(params: ReferenceParams): CompletableFuture<MutableList<out Location>> {
            referencesCalls += params
            return CompletableFuture.completedFuture(referencesResponse)
        }
        override fun signatureHelp(params: SignatureHelpParams): CompletableFuture<SignatureHelp> {
            signatureHelpCalls += params
            return CompletableFuture.completedFuture(signatureHelpResponse)
        }
        override fun prepareRename(
            params: PrepareRenameParams,
        ): CompletableFuture<Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>> {
            prepareRenameCalls += params
            return CompletableFuture.completedFuture(prepareRenameResponse)
        }
        override fun rename(params: RenameParams): CompletableFuture<WorkspaceEdit> {
            renameCalls += params
            return CompletableFuture.completedFuture(renameResponse)
        }
        override fun documentSymbol(
            params: DocumentSymbolParams,
        ): CompletableFuture<MutableList<Either<SymbolInformation, DocumentSymbol>>> {
            documentSymbolCalls += params
            return CompletableFuture.completedFuture(documentSymbolResponse)
        }
        override fun formatting(params: DocumentFormattingParams): CompletableFuture<MutableList<out TextEdit>> {
            formattingCalls += params
            return CompletableFuture.completedFuture(formattingResponse)
        }
        override fun codeAction(
            params: CodeActionParams,
        ): CompletableFuture<MutableList<Either<Command, CodeAction>>> {
            codeActionCalls += params
            return CompletableFuture.completedFuture(codeActionResponse)
        }
    }

    private val workspaceService = object : WorkspaceService {
        override fun didChangeConfiguration(params: DidChangeConfigurationParams) {}
        override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {}
        @Suppress("DEPRECATION")
        override fun symbol(
            params: WorkspaceSymbolParams,
        ): CompletableFuture<Either<MutableList<out SymbolInformation>, MutableList<out WorkspaceSymbol>>> {
            workspaceSymbolCalls += params
            return CompletableFuture.completedFuture(workspaceSymbolResponse)
        }
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
