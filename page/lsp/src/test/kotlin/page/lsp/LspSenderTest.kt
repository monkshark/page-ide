package page.lsp

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LspSenderTest {

    @Test
    fun `request returns immediately while the send is still blocked`() {
        val sender = LspSender("test-sender")
        val release = CountDownLatch(1)
        val future = sender.request {
            release.await(5, TimeUnit.SECONDS)
            CompletableFuture.completedFuture("ok")
        }
        assertFalse(future.isDone)
        release.countDown()
        assertEquals("ok", future.get(5, TimeUnit.SECONDS))
    }

    @Test
    fun `post does not block the caller and tasks run in submission order`() {
        val sender = LspSender("test-sender")
        val order = CopyOnWriteArrayList<Int>()
        val gate = CountDownLatch(1)
        val done = CountDownLatch(4)
        sender.post { gate.await(5, TimeUnit.SECONDS); order.add(0); done.countDown() }
        for (i in 1..3) sender.post { order.add(i); done.countDown() }
        gate.countDown()
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals(listOf(0, 1, 2, 3), order)
    }

    @Test
    fun `request keeps ordering relative to posted notifications`() {
        val sender = LspSender("test-sender")
        val order = CopyOnWriteArrayList<String>()
        sender.post { order.add("notify") }
        val future = sender.request {
            order.add("request")
            CompletableFuture.completedFuture(Unit)
        }
        future.get(5, TimeUnit.SECONDS)
        assertEquals(listOf("notify", "request"), order)
    }

    @Test
    fun `throwing send surfaces as an exceptional future`() {
        val sender = LspSender("test-sender")
        val future = sender.request<String> { error("boom") }
        val ex = assertFailsWith<ExecutionException> { future.get(5, TimeUnit.SECONDS) }
        assertTrue(ex.cause is IllegalStateException)
    }

    @Test
    fun `failing inner future propagates its exception`() {
        val sender = LspSender("test-sender")
        val future = sender.request<String> {
            CompletableFuture.failedFuture(RuntimeException("server error"))
        }
        val ex = assertFailsWith<ExecutionException> { future.get(5, TimeUnit.SECONDS) }
        assertEquals("server error", ex.cause?.message)
    }

    @Test
    fun `cancelling the returned future cancels the in-flight request`() {
        val sender = LspSender("test-sender")
        val inner = CompletableFuture<String>()
        val started = CountDownLatch(1)
        val future = sender.request { started.countDown(); inner }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        val innerDone = CountDownLatch(1)
        inner.whenComplete { _, _ -> innerDone.countDown() }
        future.cancel(true)
        assertTrue(innerDone.await(5, TimeUnit.SECONDS))
        assertTrue(inner.isCancelled)
    }

    @Test
    fun `request cancelled before dispatch never runs the send`() {
        val sender = LspSender("test-sender")
        val gate = CountDownLatch(1)
        val ran = CountDownLatch(1)
        sender.post { gate.await(5, TimeUnit.SECONDS) }
        var sent = false
        val future = sender.request {
            sent = true
            CompletableFuture.completedFuture(Unit)
        }
        future.cancel(true)
        sender.post { ran.countDown() }
        gate.countDown()
        assertTrue(ran.await(5, TimeUnit.SECONDS))
        assertFalse(sent)
    }
}
