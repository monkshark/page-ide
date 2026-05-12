package page.lsp

import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.MarkupKind
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LspWorkspaceTest {

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

    @Test
    fun `didOpen forwards to server with version 1`() {
        val uri = "file:///A.kt"
        workspace.didOpen(uri, "kotlin", "fun a() {}")

        waitUntil { harness.fakeServer.didOpenCalls.isNotEmpty() }
        val params = harness.fakeServer.didOpenCalls.first()
        assertEquals(uri, params.textDocument.uri)
        assertEquals("kotlin", params.textDocument.languageId)
        assertEquals(1, params.textDocument.version)
        assertEquals("fun a() {}", params.textDocument.text)
        assertTrue(workspace.isOpen(uri))
        assertEquals(1, workspace.versionOf(uri))
    }

    @Test
    fun `didChange increments version and forwards new text`() {
        val uri = "file:///B.kt"
        workspace.didOpen(uri, "kotlin", "v1")
        workspace.didChange(uri, "v2")
        workspace.didChange(uri, "v3")

        waitUntil { harness.fakeServer.didChangeCalls.size >= 2 }
        val changes = harness.fakeServer.didChangeCalls.toList()
        assertEquals(2, changes.first().textDocument.version)
        assertEquals(3, changes.last().textDocument.version)
        assertEquals("v3", workspace.textOf(uri))
        assertEquals(3, workspace.versionOf(uri))
    }

    @Test
    fun `didClose removes document and forwards to server`() {
        val uri = "file:///C.kt"
        workspace.didOpen(uri, "kotlin", "x")
        workspace.didClose(uri)

        waitUntil { harness.fakeServer.didCloseCalls.isNotEmpty() }
        assertFalse(workspace.isOpen(uri))
        assertNull(workspace.textOf(uri))
    }

    @Test
    fun `didChange on unopened doc errors out`() {
        try {
            workspace.didChange("file:///none.kt", "x")
            error("expected IllegalStateException")
        } catch (_: IllegalStateException) {
        }
    }

    @Test
    fun `didClose on unopened doc is no-op`() {
        workspace.didClose("file:///nope.kt")
    }

    @Test
    fun `hover on unopened doc returns null without server call`() {
        val result = workspace.hover("file:///nope.kt", 0, 0).get(2, TimeUnit.SECONDS)
        assertNull(result)
        assertTrue(harness.fakeServer.hoverCalls.isEmpty())
    }

    @Test
    fun `hover forwards params and parses response`() {
        val uri = "file:///H.kt"
        workspace.didOpen(uri, "kotlin", "x")
        harness.fakeServer.hoverResponse = Hover().apply {
            contents = Either.forRight(MarkupContent(MarkupKind.MARKDOWN, "hello"))
            range = Range(Position(0, 0), Position(0, 1))
        }

        val info = workspace.hover(uri, 0, 0).get(2, TimeUnit.SECONDS)
        assertNotNull(info)
        assertEquals("hello", info!!.markdown)

        waitUntil { harness.fakeServer.hoverCalls.isNotEmpty() }
        val sent = harness.fakeServer.hoverCalls.first()
        assertEquals(uri, sent.textDocument.uri)
        assertEquals(0, sent.position.line)
        assertEquals(0, sent.position.character)
    }

    @Test
    fun `definition on unopened doc returns empty`() {
        val result = workspace.definition("file:///nope.kt", 0, 0).get(2, TimeUnit.SECONDS)
        assertTrue(result.isEmpty())
        assertTrue(harness.fakeServer.definitionCalls.isEmpty())
    }

    @Test
    fun `definition returns mapped targets`() {
        val uri = "file:///D.kt"
        workspace.didOpen(uri, "kotlin", "y")
        val loc = Location("file:///Target.kt", Range(Position(5, 2), Position(5, 8)))
        harness.fakeServer.definitionResponse =
            Either.forLeft<MutableList<out Location>, MutableList<out LocationLink>>(mutableListOf(loc))

        val targets = workspace.definition(uri, 3, 4).get(2, TimeUnit.SECONDS)
        assertEquals(1, targets.size)
        assertEquals("file:///Target.kt", targets[0].uri)
        assertEquals(5, targets[0].startLine)
        assertEquals(2, targets[0].startCharacter)
        assertEquals(8, targets[0].endCharacter)
    }

    private fun waitUntil(timeoutMs: Long = 2000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            Thread.sleep(10)
        }
        throw AssertionError("condition not met within ${timeoutMs}ms")
    }
}
