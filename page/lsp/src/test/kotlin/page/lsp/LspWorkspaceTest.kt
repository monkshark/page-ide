package page.lsp

import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    private fun waitUntil(timeoutMs: Long = 2000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            Thread.sleep(10)
        }
        throw AssertionError("condition not met within ${timeoutMs}ms")
    }
}
