package page.lsp

import com.google.gson.JsonObject
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LspClientTest {

    @Test
    fun `initialize handshake completes and state becomes INITIALIZED`() {
        val harness = LspTestHarness()
        try {
            assertEquals(LspState.NOT_STARTED, harness.client.state)
            val result = harness.client.start().get(5, TimeUnit.SECONDS)
            assertNotNull(result)
            assertNotNull(result.capabilities)

            waitUntil { harness.fakeServer.initializedCalls.isNotEmpty() }
            assertEquals(LspState.INITIALIZED, harness.client.state)
            assertEquals(1, harness.fakeServer.initializeCalls.size)
            assertEquals(1, harness.fakeServer.initializedCalls.size)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `initialize advertises completionItem resolveSupport so resolve-gated servers enable it`() {
        val harness = LspTestHarness()
        try {
            harness.client.start().get(5, TimeUnit.SECONDS)
            waitUntil { harness.fakeServer.initializeCalls.isNotEmpty() }
            val completionItem = harness.fakeServer.initializeCalls.peek()
                .capabilities.textDocument.completion.completionItem
            assertTrue(completionItem.snippetSupport)
            val props = completionItem.resolveSupport?.properties
            assertNotNull(props)
            assertTrue(props!!.containsAll(listOf("documentation", "detail", "additionalTextEdits")))
        } finally {
            harness.close()
        }
    }

    @Test
    fun `initialize advertises workspace applyEdit so command-based code actions can edit`() {
        val harness = LspTestHarness()
        try {
            harness.client.start().get(5, TimeUnit.SECONDS)
            waitUntil { harness.fakeServer.initializeCalls.isNotEmpty() }
            val workspace = harness.fakeServer.initializeCalls.peek().capabilities.workspace
            assertNotNull(workspace)
            assertTrue(workspace!!.applyEdit)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `applyEdit invokes registered handler and reports applied`() {
        val harness = LspTestHarness()
        try {
            var received: org.eclipse.lsp4j.WorkspaceEdit? = null
            harness.client.onApplyEdit { edit -> received = edit; true }
            val edit = org.eclipse.lsp4j.WorkspaceEdit(
                mapOf(
                    "file:///A.java" to listOf(
                        org.eclipse.lsp4j.TextEdit(
                            org.eclipse.lsp4j.Range(
                                org.eclipse.lsp4j.Position(0, 0),
                                org.eclipse.lsp4j.Position(0, 0),
                            ),
                            "import java.util.List;\n",
                        ),
                    ),
                ),
            )
            val response = harness.client
                .applyEdit(org.eclipse.lsp4j.ApplyWorkspaceEditParams(edit))
                .get(1, TimeUnit.SECONDS)
            assertTrue(response.isApplied)
            assertNotNull(received)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `applyEdit without handler reports not applied`() {
        val harness = LspTestHarness()
        try {
            val edit = org.eclipse.lsp4j.WorkspaceEdit(emptyMap())
            val response = harness.client
                .applyEdit(org.eclipse.lsp4j.ApplyWorkspaceEditParams(edit))
                .get(1, TimeUnit.SECONDS)
            assertTrue(!response.isApplied)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `shutdown transitions to EXITED and calls server shutdown + exit`() {
        val harness = LspTestHarness()
        try {
            harness.client.start().get(5, TimeUnit.SECONDS)
            waitUntil { harness.client.state == LspState.INITIALIZED }

            harness.client.shutdown().get(5, TimeUnit.SECONDS)
            assertEquals(LspState.EXITED, harness.client.state)
            waitUntil { harness.fakeServer.shutdownCalled && harness.fakeServer.exitCalled }
            assertTrue(harness.fakeServer.shutdownCalled)
            assertTrue(harness.fakeServer.exitCalled)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `initialSettings null sends no didChangeConfiguration`() {
        val harness = LspTestHarness()
        try {
            harness.client.start().get(5, TimeUnit.SECONDS)
            waitUntil { harness.fakeServer.initializedCalls.isNotEmpty() }
            assertTrue(harness.fakeServer.didChangeConfigurationCalls.isEmpty())
        } finally {
            harness.close()
        }
    }

    @Test
    fun `initialSettings forwards kotlin inlay hints config after initialized`() {
        val settings = KotlinLsp.inlayHintsSettings()
        val harness = LspTestHarness(initialSettings = settings)
        try {
            harness.client.start().get(5, TimeUnit.SECONDS)
            waitUntil { harness.fakeServer.didChangeConfigurationCalls.isNotEmpty() }
            assertEquals(1, harness.fakeServer.didChangeConfigurationCalls.size)
            val received = harness.fakeServer.didChangeConfigurationCalls.peek().settings as JsonObject
            val inlay = received.getAsJsonObject("kotlin").getAsJsonObject("inlayHints")
            assertTrue(inlay.get("typeHints").asBoolean)
            assertTrue(inlay.get("parameterHints").asBoolean)
            assertTrue(inlay.get("chainedHints").asBoolean)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `inlayHintsSettings respects explicit flags`() {
        val settings = KotlinLsp.inlayHintsSettings(
            typeHints = false,
            parameterHints = true,
            chainedHints = false,
        )
        val inlay = settings.getAsJsonObject("kotlin").getAsJsonObject("inlayHints")
        assertEquals(false, inlay.get("typeHints").asBoolean)
        assertEquals(true, inlay.get("parameterHints").asBoolean)
        assertEquals(false, inlay.get("chainedHints").asBoolean)
        assertNull(settings.getAsJsonObject("kotlin").get("unknown"))
    }

    @Test
    fun `refreshDiagnostics returns a completed future instead of throwing`() {
        val harness = LspTestHarness()
        try {
            val future = harness.client.refreshDiagnostics()
            future.get(1, TimeUnit.SECONDS)
            assertTrue(future.isDone)
            assertTrue(!future.isCompletedExceptionally)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `shutdown is idempotent`() {
        val harness = LspTestHarness()
        try {
            harness.client.start().get(5, TimeUnit.SECONDS)
            harness.client.shutdown().get(5, TimeUnit.SECONDS)
            harness.client.shutdown().get(5, TimeUnit.SECONDS)
            assertEquals(LspState.EXITED, harness.client.state)
        } finally {
            harness.close()
        }
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
