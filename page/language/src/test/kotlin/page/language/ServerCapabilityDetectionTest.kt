package page.language

import org.eclipse.lsp4j.CallHierarchyRegistrationOptions
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
    fun `callHierarchy disabled without provider`() {
        assertFalse(detectCallHierarchySupport(null))
        assertFalse(detectCallHierarchySupport(ServerCapabilities()))
        assertFalse(detectCallHierarchySupport(ServerCapabilities().apply {
            callHierarchyProvider = Either.forLeft(false)
        }))
    }

    @Test
    fun `callHierarchy enabled by boolean true or registration options`() {
        assertTrue(detectCallHierarchySupport(ServerCapabilities().apply {
            callHierarchyProvider = Either.forLeft(true)
        }))
        assertTrue(detectCallHierarchySupport(ServerCapabilities().apply {
            callHierarchyProvider = Either.forRight(CallHierarchyRegistrationOptions())
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

    @Test
    fun `reinject when empty publish arrives for content that had unnecessary`() {
        assertTrue(
            shouldReinjectUnnecessary(
                incomingHasUnnecessary = false,
                cachedForCurrentContent = true,
            ),
        )
    }

    @Test
    fun `do not reinject when incoming already has unnecessary`() {
        assertFalse(
            shouldReinjectUnnecessary(
                incomingHasUnnecessary = true,
                cachedForCurrentContent = true,
            ),
        )
    }

    @Test
    fun `do not reinject when content does not match cache`() {
        assertFalse(
            shouldReinjectUnnecessary(
                incomingHasUnnecessary = false,
                cachedForCurrentContent = false,
            ),
        )
    }

    @Test
    fun `extractProjectUris reads a list of file uri strings`() {
        assertEquals(
            listOf("file:///c:/ws/proj-a", "file:///c:/ws/proj-b"),
            extractProjectUris(listOf("file:///c:/ws/proj-a", "file:///c:/ws/proj-b")),
        )
    }

    @Test
    fun `extractProjectUris strips quotes from json-like elements`() {
        val jsonPrimitiveLike = object {
            override fun toString() = "\"file:///c:/ws/proj-a\""
        }
        assertEquals(
            listOf("file:///c:/ws/proj-a"),
            extractProjectUris(listOf(jsonPrimitiveLike)),
        )
    }

    @Test
    fun `extractProjectUris drops non-file entries and non-iterables`() {
        assertEquals(emptyList<String>(), extractProjectUris(listOf("not-a-uri", 42, null)))
        assertEquals(emptyList<String>(), extractProjectUris(null))
        assertEquals(emptyList<String>(), extractProjectUris("file:///c:/ws/proj-a"))
    }

    @Test
    fun `drainBatch returns every pending entry once and empties the map`() {
        val pending = linkedMapOf<String, List<page.lsp.Diagnostic>>(
            "file:///a" to emptyList(),
            "file:///b" to emptyList(),
            "file:///c" to emptyList(),
        )
        val batch = drainBatch(pending)
        assertEquals(listOf("file:///a", "file:///b", "file:///c"), batch.map { it.first })
        assertTrue(pending.isEmpty())
    }

    @Test
    fun `drainBatch on empty map yields empty list`() {
        assertEquals(emptyList(), drainBatch(mutableMapOf()))
    }
}
