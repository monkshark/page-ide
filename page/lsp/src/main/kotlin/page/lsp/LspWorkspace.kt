package page.lsp

import org.eclipse.lsp4j.CompletionContext
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.CompletionTriggerKind
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PrepareRenameParams
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

data class WorkspaceSymbolEntry(
    val name: String,
    val containerName: String?,
    val kind: SymbolKind?,
)

class LspWorkspace(private val client: LspClient) {

    private data class OpenDoc(val languageId: String, var version: Int, var text: String)

    private val openDocs = ConcurrentHashMap<String, OpenDoc>()

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
        return client.server().textDocumentService.completion(params).thenApply { either ->
            when {
                either == null -> CompletionList.EMPTY
                either.isLeft -> CompletionList.fromLspItems(either.left.orEmpty(), triggerCharacter, prefix)
                else -> CompletionList.fromLsp(either.right, triggerCharacter, prefix)
            }
        }
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
}
