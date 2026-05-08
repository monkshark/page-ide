package page.lsp

import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

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

    fun openUris(): Set<String> = openDocs.keys.toSet()

    fun completion(uri: String, line: Int, character: Int): CompletableFuture<CompletionList> {
        if (!openDocs.containsKey(uri)) {
            return CompletableFuture.completedFuture(CompletionList.EMPTY)
        }
        val params = CompletionParams(TextDocumentIdentifier(uri), Position(line, character))
        return client.server().textDocumentService.completion(params).thenApply { either ->
            when {
                either == null -> CompletionList.EMPTY
                either.isLeft -> CompletionList.fromLspItems(either.left.orEmpty())
                else -> CompletionList.fromLsp(either.right)
            }
        }
    }
}
