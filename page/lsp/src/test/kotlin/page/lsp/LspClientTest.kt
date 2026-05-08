package page.lsp

import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
