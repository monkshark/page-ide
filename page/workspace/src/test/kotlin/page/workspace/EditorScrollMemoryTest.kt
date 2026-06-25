package page.workspace

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.nio.file.Paths

class EditorScrollMemoryTest {

    private val a = Paths.get("a.kt")
    private val b = Paths.get("b.kt")
    private val snap1 = EditorScrollSnapshot(vertical = 120, horizontal = 0)
    private val snap2 = EditorScrollSnapshot(vertical = 480, horizontal = 32)

    @Test
    fun `get returns null for unknown path`() {
        assertNull(EditorScrollMemory.get(emptyMap(), a))
    }

    @Test
    fun `get returns null when path is null`() {
        val store = mapOf(a to snap1)
        assertNull(EditorScrollMemory.get(store, null))
    }

    @Test
    fun `put stores snapshot for path`() {
        val updated = EditorScrollMemory.put(emptyMap(), a, snap1)
        assertEquals(snap1, EditorScrollMemory.get(updated, a))
    }

    @Test
    fun `put with null path returns store unchanged`() {
        val store = mapOf(a to snap1)
        assertSame(store, EditorScrollMemory.put(store, null, snap2))
    }

    @Test
    fun `put with negative offsets returns store unchanged`() {
        val store = mapOf(a to snap1)
        val negV = EditorScrollSnapshot(vertical = -1, horizontal = 0)
        val negH = EditorScrollSnapshot(vertical = 0, horizontal = -1)
        assertSame(store, EditorScrollMemory.put(store, a, negV))
        assertSame(store, EditorScrollMemory.put(store, a, negH))
    }

    @Test
    fun `put returns same instance when snapshot matches existing`() {
        val store = mapOf(a to snap1)
        assertSame(store, EditorScrollMemory.put(store, a, snap1))
    }

    @Test
    fun `put overwrites existing snapshot`() {
        val store = mapOf(a to snap1)
        val updated = EditorScrollMemory.put(store, a, snap2)
        assertEquals(snap2, EditorScrollMemory.get(updated, a))
    }

    @Test
    fun `clear removes path from store`() {
        val store = mapOf(a to snap1, b to snap2)
        val updated = EditorScrollMemory.clear(store, a)
        assertNull(EditorScrollMemory.get(updated, a))
        assertEquals(snap2, EditorScrollMemory.get(updated, b))
    }

    @Test
    fun `clear is a no-op when path not present`() {
        val store = mapOf(a to snap1)
        assertSame(store, EditorScrollMemory.clear(store, b))
    }

    @Test
    fun `clearAll removes multiple paths`() {
        val c = Paths.get("c.kt")
        val store = mapOf(a to snap1, b to snap2, c to snap1)
        val updated = EditorScrollMemory.clearAll(store, listOf(a, c))
        assertNull(EditorScrollMemory.get(updated, a))
        assertNull(EditorScrollMemory.get(updated, c))
        assertEquals(snap2, EditorScrollMemory.get(updated, b))
    }

    @Test
    fun `clearAll is a no-op when none of the paths are in store`() {
        val c = Paths.get("c.kt")
        val store = mapOf(a to snap1)
        assertSame(store, EditorScrollMemory.clearAll(store, listOf(b, c)))
    }

    @Test
    fun `clearAll is a no-op for empty input`() {
        val store = mapOf(a to snap1)
        assertSame(store, EditorScrollMemory.clearAll(store, emptyList()))
    }
}
