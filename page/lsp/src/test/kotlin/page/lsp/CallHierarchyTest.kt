package page.lsp

import org.eclipse.lsp4j.CallHierarchyIncomingCall
import org.eclipse.lsp4j.CallHierarchyItem
import org.eclipse.lsp4j.CallHierarchyOutgoingCall
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SymbolKind
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CallHierarchyTest {

    private lateinit var harness: LspTestHarness
    private lateinit var workspace: LspWorkspace

    @BeforeTest
    fun setUp() {
        harness = LspTestHarness()
        harness.client.start().get(5, TimeUnit.SECONDS)
        waitUntil { harness.client.state == LspState.INITIALIZED }
        workspace = LspWorkspace(harness.client)
    }

    @AfterTest
    fun tearDown() {
        harness.close()
    }

    private fun lspItem(name: String, uri: String, data: Any? = null): CallHierarchyItem =
        CallHierarchyItem().apply {
            this.name = name
            kind = SymbolKind.Function
            this.uri = uri
            range = Range(Position(1, 0), Position(5, 1))
            selectionRange = Range(Position(1, 4), Position(1, 4 + name.length))
            this.data = data
        }

    @Test
    fun `prepareCallHierarchy maps server items to the model`() {
        val uri = "file:///A.kt"
        workspace.didOpen(uri, "kotlin", "fun a() {}")
        harness.fakeServer.prepareCallHierarchyResponse = mutableListOf(lspItem("a", uri))

        val items = workspace.prepareCallHierarchy(uri, 1, 5).get(5, TimeUnit.SECONDS)

        assertEquals(1, items.size)
        assertEquals("a", items[0].name)
        assertEquals(SymbolKind.Function, items[0].kind)
        assertEquals(uri, items[0].uri)
        assertEquals(SymbolRange(1, 0, 5, 1), items[0].range)
        assertEquals(SymbolRange(1, 4, 1, 5), items[0].selectionRange)
        val sent = harness.fakeServer.prepareCallHierarchyCalls.first()
        assertEquals(uri, sent.textDocument.uri)
        assertEquals(Position(1, 5), sent.position)
    }

    @Test
    fun `prepareCallHierarchy on unopened doc resolves empty without calling the server`() {
        val items = workspace.prepareCallHierarchy("file:///none.kt", 0, 0).get(5, TimeUnit.SECONDS)
        assertTrue(items.isEmpty())
        assertTrue(harness.fakeServer.prepareCallHierarchyCalls.isEmpty())
    }

    @Test
    fun `incomingCalls sends the prepared item back and maps from items with ranges`() {
        val uri = "file:///B.kt"
        workspace.didOpen(uri, "kotlin", "fun b() {}")
        harness.fakeServer.prepareCallHierarchyResponse = mutableListOf(lspItem("b", uri, data = "opaque-token"))
        val prepared = workspace.prepareCallHierarchy(uri, 1, 5).get(5, TimeUnit.SECONDS).single()

        harness.fakeServer.incomingCallsResponse = mutableListOf(
            CallHierarchyIncomingCall(
                lspItem("caller", "file:///C.kt"),
                listOf(Range(Position(3, 2), Position(3, 8))),
            ),
        )
        val calls = workspace.incomingCalls(prepared).get(5, TimeUnit.SECONDS)

        assertEquals(1, calls.size)
        assertEquals("caller", calls[0].item.name)
        assertEquals("file:///C.kt", calls[0].item.uri)
        assertEquals(listOf(SymbolRange(3, 2, 3, 8)), calls[0].fromRanges)
        val sentItem = harness.fakeServer.incomingCallsCalls.first().item
        assertEquals("b", sentItem.name)
        assertEquals(uri, sentItem.uri)
        assertNotNull(sentItem.data)
    }

    @Test
    fun `outgoingCalls maps to items with ranges`() {
        val uri = "file:///D.kt"
        workspace.didOpen(uri, "kotlin", "fun d() {}")
        harness.fakeServer.prepareCallHierarchyResponse = mutableListOf(lspItem("d", uri))
        val prepared = workspace.prepareCallHierarchy(uri, 1, 5).get(5, TimeUnit.SECONDS).single()

        harness.fakeServer.outgoingCallsResponse = mutableListOf(
            CallHierarchyOutgoingCall(
                lspItem("callee", "file:///E.kt"),
                listOf(Range(Position(2, 4), Position(2, 10))),
            ),
        )
        val calls = workspace.outgoingCalls(prepared).get(5, TimeUnit.SECONDS)

        assertEquals(1, calls.size)
        assertEquals("callee", calls[0].item.name)
        assertEquals("file:///E.kt", calls[0].item.uri)
        assertEquals(listOf(SymbolRange(2, 4, 2, 10)), calls[0].fromRanges)
        assertEquals("d", harness.fakeServer.outgoingCallsCalls.first().item.name)
    }

    @Test
    fun `null server response resolves to empty lists`() {
        val uri = "file:///F.kt"
        workspace.didOpen(uri, "kotlin", "fun f() {}")
        harness.fakeServer.prepareCallHierarchyResponse = null
        assertTrue(workspace.prepareCallHierarchy(uri, 0, 0).get(5, TimeUnit.SECONDS).isEmpty())
    }

    private fun waitUntil(timeoutMs: Long = 2000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            Thread.sleep(10)
        }
        throw AssertionError("condition not met within ${timeoutMs}ms")
    }

    @Test
    fun `fromLsp drops items without uri or range`() {
        assertNull(CallHierarchyItemInfo.fromLsp(null))
        assertNull(CallHierarchyItemInfo.fromLsp(CallHierarchyItem().apply { name = "x" }))
        assertNull(
            CallHierarchyItemInfo.fromLsp(
                CallHierarchyItem().apply {
                    name = "x"
                    uri = "file:///X.kt"
                },
            ),
        )
    }
}
