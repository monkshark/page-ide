package page.app

import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileTreeSelectionTest {

    private fun p(s: String): Path = Paths.get(s)

    @Test
    fun `single replaces selection with one path`() {
        val target = p("/r/a.kt")
        assertEquals(setOf(target), FileTreeSelection.single(target))
    }

    @Test
    fun `toggle adds when absent and removes when present`() {
        val a = p("/r/a.kt")
        val b = p("/r/b.kt")
        val after = FileTreeSelection.toggle(setOf(a), b)
        assertEquals(setOf(a, b), after)
        val again = FileTreeSelection.toggle(after, a)
        assertEquals(setOf(b), again)
    }

    @Test
    fun `range from anchor to target inclusive both directions`() {
        val order = listOf("/r/a", "/r/b", "/r/c", "/r/d", "/r/e").map(::p)
        val forward = FileTreeSelection.range(p("/r/b"), p("/r/d"), order)
        assertEquals(setOf(p("/r/b"), p("/r/c"), p("/r/d")), forward)
        val backward = FileTreeSelection.range(p("/r/d"), p("/r/b"), order)
        assertEquals(setOf(p("/r/b"), p("/r/c"), p("/r/d")), backward)
    }

    @Test
    fun `range with null anchor falls back to single target`() {
        val order = listOf("/r/a", "/r/b", "/r/c").map(::p)
        assertEquals(setOf(p("/r/b")), FileTreeSelection.range(null, p("/r/b"), order))
    }

    @Test
    fun `range with anchor outside visible order falls back to single target`() {
        val order = listOf("/r/a", "/r/b", "/r/c").map(::p)
        assertEquals(setOf(p("/r/c")), FileTreeSelection.range(p("/r/hidden"), p("/r/c"), order))
    }

    @Test
    fun `range with target outside visible order falls back to single target`() {
        val order = listOf("/r/a", "/r/b").map(::p)
        assertEquals(setOf(p("/r/missing")), FileTreeSelection.range(p("/r/a"), p("/r/missing"), order))
    }

    @Test
    fun `range with same anchor and target returns single`() {
        val order = listOf("/r/a", "/r/b", "/r/c").map(::p)
        assertEquals(setOf(p("/r/b")), FileTreeSelection.range(p("/r/b"), p("/r/b"), order))
    }

    @Test
    fun `pruneToDescendantsOf drops paths outside root`() {
        val root = p("/workspace")
        val inside1 = p("/workspace/a.kt")
        val inside2 = p("/workspace/sub/b.kt")
        val outside = p("/elsewhere/c.kt")
        val pruned = FileTreeSelection.pruneToDescendantsOf(setOf(inside1, inside2, outside), root)
        assertEquals(setOf(inside1, inside2), pruned)
    }

    @Test
    fun `pruneToDescendantsOf keeps root itself`() {
        val root = p("/workspace")
        assertTrue(root in FileTreeSelection.pruneToDescendantsOf(setOf(root), root))
    }
}
