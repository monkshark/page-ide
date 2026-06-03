package page.lsp

import org.eclipse.lsp4j.CodeActionContext
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.CompletionContext
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.CompletionTriggerKind
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.FileEvent
import org.eclipse.lsp4j.DocumentFormattingParams
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.FormattingOptions
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.InlayHintParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PrepareRenameParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.ReferenceContext
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.SignatureHelpContext
import org.eclipse.lsp4j.SignatureHelpParams
import org.eclipse.lsp4j.SignatureHelpTriggerKind
import org.eclipse.lsp4j.SymbolKind
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceSymbolParams
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

data class WorkspaceSymbolEntry(
    val name: String,
    val containerName: String?,
    val kind: SymbolKind?,
)

class LspWorkspace(private val client: LspClient) {

    private data class OpenDoc(val languageId: String, var version: Int, var text: String)

    private val openDocs = ConcurrentHashMap<String, OpenDoc>()

    private val resolveRegistry = ConcurrentHashMap<Long, org.eclipse.lsp4j.CompletionItem>()
    private val resolveTokenSeq = AtomicLong(0)

    fun isOpen(uri: String): Boolean = openDocs.containsKey(uri)
    fun textOf(uri: String): String? = openDocs[uri]?.text
    fun versionOf(uri: String): Int? = openDocs[uri]?.version

    fun didOpen(uri: String, languageId: String, text: String) {
        val existing = openDocs[uri]
        if (existing != null) {
            error("document already open: $uri (use didChange instead)")
        }
        val doc = OpenDoc(languageId, version = 1, text = text)
        openDocs[uri] = doc
        val item = TextDocumentItem(uri, languageId, doc.version, text)
        client.server().textDocumentService.didOpen(DidOpenTextDocumentParams(item))
    }

    fun didChange(uri: String, newText: String) {
        val doc = openDocs[uri] ?: error("document not open: $uri")
        doc.version += 1
        doc.text = newText
        val versioned = VersionedTextDocumentIdentifier(uri, doc.version)
        val change = TextDocumentContentChangeEvent(newText)
        client.server().textDocumentService.didChange(
            DidChangeTextDocumentParams(versioned, listOf(change))
        )
    }

    fun didClose(uri: String) {
        openDocs.remove(uri) ?: return
        client.server().textDocumentService.didClose(
            DidCloseTextDocumentParams(TextDocumentIdentifier(uri))
        )
    }

    fun didSave(uri: String) {
        val doc = openDocs[uri] ?: return
        client.server().textDocumentService.didSave(
            DidSaveTextDocumentParams(TextDocumentIdentifier(uri), doc.text)
        )
    }

    fun didChangeWatchedFiles(events: List<Pair<String, FileChangeType>>) {
        if (events.isEmpty()) return
        val lspEvents = events.map { (uri, type) -> FileEvent(uri, type) }
        client.server().workspaceService.didChangeWatchedFiles(DidChangeWatchedFilesParams(lspEvents))
    }

    fun reopen(uri: String, newText: String) {
        val existing = openDocs[uri] ?: return
        val languageId = existing.languageId
        openDocs.remove(uri)
        client.server().textDocumentService.didClose(
            DidCloseTextDocumentParams(TextDocumentIdentifier(uri))
        )
        val doc = OpenDoc(languageId, version = 1, text = newText)
        openDocs[uri] = doc
        val item = TextDocumentItem(uri, languageId, doc.version, newText)
        client.server().textDocumentService.didOpen(DidOpenTextDocumentParams(item))
    }

    fun openUris(): Set<String> = openDocs.keys.toSet()

    fun completion(
        uri: String,
        line: Int,
        character: Int,
        triggerCharacter: String? = null,
        prefix: String? = null,
    ): CompletableFuture<CompletionList> {
        if (!openDocs.containsKey(uri)) {
            return CompletableFuture.completedFuture(CompletionList.EMPTY)
        }
        val params = CompletionParams(TextDocumentIdentifier(uri), Position(line, character))
        params.context = if (triggerCharacter != null) {
            CompletionContext(CompletionTriggerKind.TriggerCharacter, triggerCharacter)
        } else {
            CompletionContext(CompletionTriggerKind.Invoked)
        }
        resolveRegistry.clear()
        val registerToken: (org.eclipse.lsp4j.CompletionItem) -> Long? = { orig ->
            resolveTokenSeq.incrementAndGet().also { resolveRegistry[it] = orig }
        }
        return client.server().textDocumentService.completion(params).thenApply { either ->
            when {
                either == null -> CompletionList.EMPTY
                either.isLeft -> CompletionList.fromLspItems(either.left.orEmpty(), triggerCharacter, prefix, registerToken)
                else -> CompletionList.fromLsp(either.right, triggerCharacter, prefix, registerToken)
            }
        }
    }

    fun resolveCompletionItem(token: Long): CompletableFuture<ResolvedCompletion?> {
        val original = resolveRegistry[token] ?: return CompletableFuture.completedFuture(null)
        return client.server().textDocumentService.resolveCompletionItem(original)
            .thenApply { resolved -> resolved?.let { ResolvedCompletion.fromLsp(it) } }
            .exceptionally { null }
    }

    fun hover(uri: String, line: Int, character: Int): CompletableFuture<HoverInfo?> {
        if (!openDocs.containsKey(uri)) return CompletableFuture.completedFuture(null)
        val params = HoverParams(TextDocumentIdentifier(uri), Position(line, character))
        return client.server().textDocumentService.hover(params).thenApply { HoverInfo.fromLsp(it) }
    }

    fun definition(uri: String, line: Int, character: Int): CompletableFuture<List<DefinitionTarget>> {
        if (!openDocs.containsKey(uri)) return CompletableFuture.completedFuture(emptyList())
        val params = DefinitionParams(TextDocumentIdentifier(uri), Position(line, character))
        return client.server().textDocumentService.definition(params).thenApply { DefinitionTarget.fromLsp(it) }
    }

    fun references(
        uri: String,
        line: Int,
        character: Int,
        includeDeclaration: Boolean = true,
    ): CompletableFuture<List<ReferenceLocation>> {
        if (!openDocs.containsKey(uri)) return CompletableFuture.completedFuture(emptyList())
        val context = ReferenceContext().apply { isIncludeDeclaration = includeDeclaration }
        val params = ReferenceParams(TextDocumentIdentifier(uri), Position(line, character), context)
        return client.server().textDocumentService.references(params).thenApply { ReferenceLocation.fromLsp(it) }
    }

    fun signatureHelp(
        uri: String,
        line: Int,
        character: Int,
        triggerCharacter: String? = null,
        isRetrigger: Boolean = false,
    ): CompletableFuture<SignatureHelpInfo?> {
        if (!openDocs.containsKey(uri)) return CompletableFuture.completedFuture(null)
        val params = SignatureHelpParams(TextDocumentIdentifier(uri), Position(line, character))
        val triggerKind = when {
            triggerCharacter != null -> SignatureHelpTriggerKind.TriggerCharacter
            isRetrigger -> SignatureHelpTriggerKind.ContentChange
            else -> SignatureHelpTriggerKind.Invoked
        }
        val ctx = SignatureHelpContext(triggerKind, isRetrigger)
        if (triggerCharacter != null) ctx.triggerCharacter = triggerCharacter
        params.context = ctx
        return client.server().textDocumentService.signatureHelp(params).thenApply { SignatureHelpInfo.fromLsp(it) }
    }

    fun prepareRename(uri: String, line: Int, character: Int): CompletableFuture<RenamePrepare?> {
        if (!openDocs.containsKey(uri)) return CompletableFuture.completedFuture(null)
        val params = PrepareRenameParams().apply {
            textDocument = TextDocumentIdentifier(uri)
            position = Position(line, character)
        }
        return client.server().textDocumentService.prepareRename(params).thenApply { RenamePrepare.fromLsp(it) }
    }

    fun rename(uri: String, line: Int, character: Int, newName: String): CompletableFuture<RenameWorkspaceEdit> {
        if (!openDocs.containsKey(uri)) return CompletableFuture.completedFuture(RenameWorkspaceEdit.EMPTY)
        val params = RenameParams(TextDocumentIdentifier(uri), Position(line, character), newName)
        return client.server().textDocumentService.rename(params).thenApply { RenameWorkspaceEdit.fromLsp(it) }
    }

    @Suppress("DEPRECATION")
    fun workspaceSymbols(query: String): CompletableFuture<List<WorkspaceSymbolEntry>> {
        val params = WorkspaceSymbolParams(query)
        return client.server().workspaceService.symbol(params).thenApply { either ->
            when {
                either == null -> emptyList()
                either.isLeft -> either.left.orEmpty().map {
                    WorkspaceSymbolEntry(it.name ?: "", it.containerName, it.kind)
                }
                else -> either.right.orEmpty().map {
                    WorkspaceSymbolEntry(it.name ?: "", it.containerName, it.kind)
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    fun workspaceSymbolsLocated(query: String): CompletableFuture<List<WorkspaceSymbolLocated>> {
        val params = WorkspaceSymbolParams(query)
        return client.server().workspaceService.symbol(params).thenApply { either ->
            when {
                either == null -> emptyList()
                either.isLeft -> either.left.orEmpty().map { si ->
                    val loc = si.location
                    WorkspaceSymbolLocated(
                        name = si.name ?: "",
                        containerName = si.containerName,
                        kind = si.kind,
                        location = if (loc != null && loc.uri != null) {
                            SymbolLocation(loc.uri, loc.range.toSymbolRange())
                        } else null,
                    )
                }
                else -> either.right.orEmpty().map { ws ->
                    val locEither = ws.location
                    val loc: SymbolLocation? = when {
                        locEither == null -> null
                        locEither.isLeft -> locEither.left?.let { SymbolLocation(it.uri, it.range.toSymbolRange()) }
                        else -> locEither.right?.uri?.let { SymbolLocation(it, SymbolRange(0, 0, 0, 0)) }
                    }
                    WorkspaceSymbolLocated(
                        name = ws.name ?: "",
                        containerName = ws.containerName,
                        kind = ws.kind,
                        location = loc,
                    )
                }
            }
        }
    }

    fun documentSymbols(uri: String): CompletableFuture<List<DocumentSymbolEntry>> {
        if (!openDocs.containsKey(uri)) return CompletableFuture.completedFuture(emptyList())
        val params = DocumentSymbolParams(TextDocumentIdentifier(uri))
        return client.server().textDocumentService.documentSymbol(params).thenApply { results ->
            results.orEmpty().mapNotNull { either ->
                when {
                    either == null -> null
                    either.isLeft -> DocumentSymbolEntry.fromLspSymbolInformation(either.left)
                    either.isRight -> DocumentSymbolEntry.fromLspDocumentSymbol(either.right)
                    else -> null
                }
            }
        }
    }

    fun formatting(
        uri: String,
        tabSize: Int = 4,
        insertSpaces: Boolean = true,
    ): CompletableFuture<List<RenameEdit>> {
        if (!openDocs.containsKey(uri)) return CompletableFuture.completedFuture(emptyList())
        val params = DocumentFormattingParams(
            TextDocumentIdentifier(uri),
            FormattingOptions(tabSize, insertSpaces),
        )
        return client.server().textDocumentService.formatting(params).thenApply { edits ->
            edits.orEmpty().mapNotNull { te ->
                val r = te.range ?: return@mapNotNull null
                RenameEdit(
                    startLine = r.start.line,
                    startCharacter = r.start.character,
                    endLine = r.end.line,
                    endCharacter = r.end.character,
                    newText = te.newText.orEmpty(),
                )
            }
        }
    }

    fun inlayHints(
        uri: String,
        startLine: Int,
        startCharacter: Int,
        endLine: Int,
        endCharacter: Int,
    ): CompletableFuture<List<InlayHintItem>> {
        if (!openDocs.containsKey(uri)) return CompletableFuture.completedFuture(emptyList())
        val range = Range(Position(startLine, startCharacter), Position(endLine, endCharacter))
        val params = InlayHintParams(TextDocumentIdentifier(uri), range)
        return client.server().textDocumentService.inlayHint(params).thenApply { list ->
            InlayHintItem.fromLspList(list)
        }
    }

    fun codeAction(
        uri: String,
        startLine: Int,
        startCharacter: Int,
        endLine: Int,
        endCharacter: Int,
        diagnostics: List<org.eclipse.lsp4j.Diagnostic> = emptyList(),
        only: List<String>? = null,
    ): CompletableFuture<List<CodeActionEntry>> {
        if (!openDocs.containsKey(uri)) return CompletableFuture.completedFuture(emptyList())
        val range = Range(Position(startLine, startCharacter), Position(endLine, endCharacter))
        val context = CodeActionContext(diagnostics).apply { if (only != null) this.only = only }
        val params = CodeActionParams(TextDocumentIdentifier(uri), range, context)
        val svc = client.server().textDocumentService
        return svc.codeAction(params).thenCompose { list ->
            val items = list.orEmpty()
            val entries = items.map { either ->
                when {
                    either == null -> CompletableFuture.completedFuture<CodeActionEntry?>(null)
                    either.isLeft -> CompletableFuture.completedFuture(CodeActionEntry.fromLspCommand(either.left))
                    either.isRight -> resolveIfNeeded(svc, either.right)
                    else -> CompletableFuture.completedFuture<CodeActionEntry?>(null)
                }
            }
            CompletableFuture.allOf(*entries.toTypedArray()).thenApply {
                entries.mapNotNull { it.join() }
            }
        }
    }

    private fun resolveIfNeeded(
        svc: org.eclipse.lsp4j.services.TextDocumentService,
        action: org.eclipse.lsp4j.CodeAction,
    ): CompletableFuture<CodeActionEntry?> {
        val initial = CodeActionEntry.fromLspCodeAction(action)
            ?: return CompletableFuture.completedFuture(null)
        if (initial.hasEdit) {
            return CompletableFuture.completedFuture(initial)
        }
        println("[lsp] codeAction resolve → \"${action.title}\" (empty edit — requesting resolve)")
        return runCatching { svc.resolveCodeAction(action) }
            .getOrNull()
            ?.handle<CodeActionEntry?> { resolved, err ->
                if (err != null) {
                    println("[lsp] codeAction resolve ✗ \"${action.title}\": ${err.message}")
                    initial
                } else {
                    val out = CodeActionEntry.fromLspCodeAction(resolved) ?: initial
                    println("[lsp] codeAction resolve ✓ \"${action.title}\" — hasEdit=${out.hasEdit}")
                    out
                }
            }
            ?: CompletableFuture.completedFuture(initial)
    }

    fun executeCommand(command: String, arguments: List<Any?>): CompletableFuture<Boolean> {
        val params = ExecuteCommandParams(command, arguments)
        return client.server().workspaceService.executeCommand(params)
            .handle { _, err ->
                if (err != null) {
                    println("[lsp] executeCommand ✗ \"$command\": ${err.message}")
                    false
                } else {
                    true
                }
            }
    }

    fun executeCommandForResult(command: String, arguments: List<Any?>): CompletableFuture<Any?> {
        val params = ExecuteCommandParams(command, arguments)
        return client.server().workspaceService.executeCommand(params)
            .handle { result, err ->
                if (err != null) {
                    println("[lsp] executeCommand ✗ \"$command\": ${err.message}")
                    null
                } else {
                    result
                }
            }
    }
}
