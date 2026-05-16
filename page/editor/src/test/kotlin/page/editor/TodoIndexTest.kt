package page.editor

import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TodoIndexTest {

    private fun item(uri: String, line: Int, col: Int = 0, keyword: String = "TODO", message: String = "x") =
        TodoItem(uri, line, col, keyword, message, "// $keyword: $message")

    @Test
    fun `setFile stores items and exposes them via forFile and all`() {
        val idx = TodoIndex()
        val a1 = item("file:///A.kt", 1)
        val a2 = item("file:///A.kt", 2)
        idx.setFile("file:///A.kt", listOf(a1, a2))
        assertEquals(listOf(a1, a2), idx.forFile("file:///A.kt"))
        assertEquals(listOf(a1, a2), idx.all())
        assertEquals(1, idx.fileCount())
        assertEquals(2, idx.size())
    }

    @Test
    fun `setFile with empty list removes the entry`() {
        val idx = TodoIndex()
        idx.setFile("file:///A.kt", listOf(item("file:///A.kt", 1)))
        idx.setFile("file:///A.kt", emptyList())
        assertTrue(idx.forFile("file:///A.kt").isEmpty())
        assertEquals(0, idx.fileCount())
    }

    @Test
    fun `setFile with identical items skips listener notification`() {
        val idx = TodoIndex()
        val items = listOf(item("file:///A.kt", 1))
        val calls = AtomicInteger(0)
        idx.addListener { calls.incrementAndGet() }
        idx.setFile("file:///A.kt", items)
        idx.setFile("file:///A.kt", items)
        assertEquals(1, calls.get())
    }

    @Test
    fun `removeFile clears entry and notifies once`() {
        val idx = TodoIndex()
        idx.setFile("file:///A.kt", listOf(item("file:///A.kt", 1)))
        val calls = AtomicInteger(0)
        idx.addListener { calls.incrementAndGet() }
        idx.removeFile("file:///A.kt")
        assertEquals(0, idx.fileCount())
        assertEquals(1, calls.get())
    }

    @Test
    fun `removeFile is a no-op when uri is not present`() {
        val idx = TodoIndex()
        val calls = AtomicInteger(0)
        idx.addListener { calls.incrementAndGet() }
        idx.removeFile("file:///missing.kt")
        assertEquals(0, calls.get())
    }

    @Test
    fun `replaceAll swaps entire snapshot and notifies once`() {
        val idx = TodoIndex()
        idx.setFile("file:///A.kt", listOf(item("file:///A.kt", 1)))
        val calls = AtomicInteger(0)
        idx.addListener { calls.incrementAndGet() }
        val snap = mapOf(
            "file:///B.kt" to listOf(item("file:///B.kt", 3)),
            "file:///C.kt" to listOf(item("file:///C.kt", 5)),
        )
        idx.replaceAll(snap)
        assertTrue(idx.forFile("file:///A.kt").isEmpty())
        assertEquals(2, idx.fileCount())
        assertEquals(1, calls.get())
    }

    @Test
    fun `replaceAll skips empty value entries`() {
        val idx = TodoIndex()
        idx.replaceAll(
            mapOf(
                "file:///A.kt" to emptyList(),
                "file:///B.kt" to listOf(item("file:///B.kt", 0)),
            )
        )
        assertEquals(1, idx.fileCount())
        assertTrue(idx.forFile("file:///A.kt").isEmpty())
    }

    @Test
    fun `all sorts items by uri then line then column`() {
        val idx = TodoIndex()
        val b1 = item("file:///B.kt", 0, 5)
        val a2 = item("file:///A.kt", 1, 0)
        val a1c5 = item("file:///A.kt", 1, 5)
        val a1c0 = item("file:///A.kt", 1, 0, message = "earlier")
        idx.setFile("file:///B.kt", listOf(b1))
        idx.setFile("file:///A.kt", listOf(a2, a1c5, a1c0))
        val ordered = idx.all()
        assertEquals(4, ordered.size)
        assertEquals("file:///A.kt", ordered[0].uri)
        assertEquals("file:///A.kt", ordered[1].uri)
        assertEquals("file:///A.kt", ordered[2].uri)
        assertEquals("file:///B.kt", ordered[3].uri)
        assertTrue(ordered[0].column <= ordered[1].column)
    }

    @Test
    fun `removeListener stops further callbacks`() {
        val idx = TodoIndex()
        val calls = AtomicInteger(0)
        val l: (TodoIndex) -> Unit = { calls.incrementAndGet() }
        idx.addListener(l)
        idx.setFile("file:///A.kt", listOf(item("file:///A.kt", 0)))
        idx.removeListener(l)
        idx.setFile("file:///A.kt", listOf(item("file:///A.kt", 1)))
        assertEquals(1, calls.get())
    }

    @Test
    fun `listener receives same index instance`() {
        val idx = TodoIndex()
        var received: TodoIndex? = null
        idx.addListener { received = it }
        idx.setFile("file:///A.kt", listOf(item("file:///A.kt", 0)))
        assertSame(idx, received)
    }
}
