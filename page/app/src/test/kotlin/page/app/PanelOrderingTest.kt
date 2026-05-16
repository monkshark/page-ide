package page.app

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class PanelOrderingTest {

    private fun p(s: String): Path = Path.of(s)

    @Test
    fun `applyFileOrder returns raw when order is empty`() {
        val raw = listOf(p("a") to 1, p("b") to 2)
        val out = applyFileOrder(raw, emptyList())
        assertEquals(raw, out)
    }

    @Test
    fun `applyFileOrder reorders known keys and appends unknown`() {
        val raw = listOf(p("a") to 1, p("b") to 2, p("c") to 3)
        val order = listOf("b", "a")
        val out = applyFileOrder(raw, order)
        assertEquals(listOf(p("b") to 2, p("a") to 1, p("c") to 3), out)
    }

    @Test
    fun `applyFileOrder ignores order entries that do not exist`() {
        val raw = listOf(p("a") to 1, p("b") to 2)
        val order = listOf("zzz", "b", "ghost")
        val out = applyFileOrder(raw, order)
        assertEquals(listOf(p("b") to 2, p("a") to 1), out)
    }

    @Test
    fun `moveKey shifts an item forward`() {
        val keys = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "a", "c", "d"), moveKey(keys, 0, 1))
        assertEquals(listOf("b", "c", "a", "d"), moveKey(keys, 0, 2))
        assertEquals(listOf("b", "c", "d", "a"), moveKey(keys, 0, 3))
    }

    @Test
    fun `moveKey shifts an item backward`() {
        val keys = listOf("a", "b", "c", "d")
        assertEquals(listOf("a", "c", "b", "d"), moveKey(keys, 2, 1))
        assertEquals(listOf("d", "a", "b", "c"), moveKey(keys, 3, 0))
    }

    @Test
    fun `moveKey returns same list when indices are equal`() {
        val keys = listOf("a", "b", "c")
        assertSame(keys, moveKey(keys, 1, 1))
    }

    @Test
    fun `moveKey returns same list for out-of-range indices`() {
        val keys = listOf("a", "b")
        assertSame(keys, moveKey(keys, -1, 0))
        assertSame(keys, moveKey(keys, 0, 5))
    }
}
